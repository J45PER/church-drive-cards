package com.churchdrive.app.ui

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.churchdrive.app.ha.EntityState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import org.json.JSONObject

/**
 * What a presence sensor says about a light it looks after: whether someone is there and, once the room has emptied, how long until the light goes off
 * ([offIn] seconds when the house answered, at [receivedAt], a [SystemClock.elapsedRealtime] reading).
 */
data class Presence(val present: Boolean, val offIn: Int?, val delay: Int, val sensor: String, val receivedAt: Long = 0L, val motion: Boolean = false) {
    /** Seconds left until the light goes off at [now], or null when nobody has left. */
    fun remaining(now: Long): Int? = offIn?.let { (it - (now - receivedAt) / 1000).toInt().coerceAtLeast(0) }
}

/** The house's answer to `church_drive/presence`, light by light, or null if it didn't answer. */
fun parsePresence(result: Any?, receivedAt: Long): Map<String, Presence>? {
    val lights = (result as? JSONObject)?.optJSONObject("lights") ?: return null
    return lights.keys().asSequence().mapNotNull { k ->
        val o = lights.optJSONObject(k) ?: return@mapNotNull null
        k to Presence(
            o.optBoolean("present"), if (o.isNull("off_in")) null else o.optInt("off_in"), o.optInt("delay"), o.optString("sensor"), receivedAt, o.optBoolean("motion"),
        )
    }.toMap()
}

/** The line for a light's card: "Someone's in", "Off in 12 s" or "Nobody in", and for a motion detector (which only sees movement) "Motion detected" and "No motion". */
fun presenceLine(p: Presence, remaining: Int?): String = when {
    p.present -> if (p.motion) "Motion detected" else "Someone's in"
    remaining != null -> "Off in $remaining s"
    else -> if (p.motion) "No motion" else "Nobody in"
}

/** How full the countdown bar is: full while someone is in, draining over the delay once they have gone, empty otherwise. */
fun presenceFraction(p: Presence, remaining: Int?): Float = when {
    p.present -> 1f
    remaining != null && p.delay > 0 -> (remaining.toFloat() / p.delay).coerceIn(0f, 1f)
    else -> 0f
}

/**
 * What the presence sensors say about a room's card or page: the sensors' action looks after a light, which may be the room's own light, its device light
 * or a zone or light inside the room (the landings' Main zone, say), so a presence light that is one of the room's lights, or a group made only of them, counts.
 */
fun presenceFor(light: EntityState?, device: EntityState?, entities: Map<String, EntityState>, all: Map<String, Presence>): Presence? {
    if (all.isEmpty()) return null
    all[light?.entityId]?.let { return it }
    all[device?.entityId]?.let { return it }
    // A card for a room's single light (the Front Garden's front light) takes the presence of the group made only of that light (the Hue room "Front").
    val own = light?.entityId
    if (own != null) {
        all.entries.firstOrNull { (id, _) -> entities[id]?.lightMembers()?.let { m -> m.isNotEmpty() && m.all { it == own } } == true }?.let { return it.value }
    }
    val members = light?.lightMembers()?.toSet().orEmpty()
    if (members.isEmpty()) return null
    return all.entries.firstOrNull { (id, _) ->
        id in members || entities[id]?.lightMembers()?.let { inner -> inner.isNotEmpty() && members.containsAll(inner) } == true
    }?.value
}

/** The lights a presence sensor looks after, kept up to date while the app is open. */
val LocalPresence = compositionLocalOf<Map<String, Presence>> { emptyMap() }

/** Asks the house for the presence of its lights every few seconds while the app is on screen. */
@Composable
fun rememberPresence(api: HaApi?): Map<String, Presence> {
    var state by remember { mutableStateOf<Map<String, Presence>>(emptyMap()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(api, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                parsePresence(api?.ask("church_drive/presence", JSONObject()), SystemClock.elapsedRealtime())?.let { state = it }
                kotlinx.coroutines.delay(3000)
            }
        }
    }
    return state
}

/** Counts [p] down on screen, once a second, and gives the seconds left (null when nobody has left). */
@Composable
fun rememberRemaining(p: Presence?): Int? {
    var now by remember { mutableStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(p) {
        while (p?.offIn != null) {
            now = SystemClock.elapsedRealtime()
            kotlinx.coroutines.delay(1000)
        }
    }
    return p?.remaining(maxOf(now, p.receivedAt))
}

/** The countdown bar: a thick track that is always there (so the card never changes size), filled by [fraction], with [text] inside it. */
@Composable
fun PresenceBar(fraction: Float, text: String, colour: Color, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colour.copy(alpha = 0.12f)), contentAlignment = Alignment.CenterStart) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().background(colour.copy(alpha = 0.32f)))
        Text(text, Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.labelLarge, color = colour, maxLines = 1)
    }
}
