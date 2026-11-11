package com.churchdrive.app.ha

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * When the apps look again at what they show. The line to Home Assistant delivers changes as they happen, but an event can be missed (the phone
 * slept, a message was lost) and then something stays old until the next change. So the house is read again in full:
 * - coming back to the app after a minute or more away, with the Updating pill so it can be seen;
 * - every five minutes while the app is open, quietly: the pill only shows if it found something that had gone stale.
 */
object Freshness {
    /** Coming back to an app after this long away reads the house again. */
    const val AWAY_MS = 60_000L
    /** While an app is open it reads the whole house again this often. */
    const val PERIODIC_MS = 300_000L
    /** How often the watcher looks at the clock. */
    const val TICK_MS = 30_000L

    fun dueOnReturn(now: Long, awaySince: Long): Boolean = awaySince > 0L && now - awaySince >= AWAY_MS
    fun duePeriodic(now: Long, lastSync: Long): Boolean = lastSync > 0L && now - lastSync >= PERIODIC_MS
}

/** Whether a fresh read differs from what the live events had left: a different count, a state that is not the same, or one that changed at another time. */
fun statesDiffer(old: Map<String, EntityState>, fresh: Map<String, EntityState>): Boolean {
    if (old.size != fresh.size) return true
    for ((id, s) in fresh) {
        val o = old[id] ?: return true
        if (o.state != s.state || o.lastChanged != s.lastChanged) return true
    }
    return false
}

/**
 * Keeps an app's view of the house from going stale (see [Freshness]). Call [onForeground] and [onBackground] as the app comes to the front and
 * goes away. [onChecked] runs after a read that worked (so an app can reload what is not carried by the live events); [onDead] when nothing
 * answered, so the app should connect afresh.
 */
class FreshnessWatcher(
    scope: CoroutineScope,
    private val client: HaClient,
    private val onChecked: (visible: Boolean, changed: Boolean) -> Unit = { _, _ -> },
    private val onDead: () -> Unit,
) {
    private var foreground = false
    private var awaySince = 0L

    init {
        scope.launch {
            while (true) {
                delay(Freshness.TICK_MS)
                if (foreground && client.statesLoaded.value && Freshness.duePeriodic(System.currentTimeMillis(), client.lastSyncAt)) check(visible = false)
            }
        }
    }

    /** The app came to the front. True when this started a read (so the caller need not check the line itself). */
    fun onForeground(): Boolean {
        foreground = true
        val away = awaySince
        awaySince = 0L
        return Freshness.dueOnReturn(System.currentTimeMillis(), away) && check(visible = true)
    }

    fun onBackground() {
        foreground = false
        awaySince = System.currentTimeMillis()
    }

    private fun check(visible: Boolean): Boolean {
        if (client.connection.value != ConnectionState.Connected || client.refreshing.value) return false
        client.refreshStates(quiet = !visible) { alive ->
            if (!alive) onDead() else onChecked(visible, client.lastRefreshChanged)
        }
        return true
    }
}
