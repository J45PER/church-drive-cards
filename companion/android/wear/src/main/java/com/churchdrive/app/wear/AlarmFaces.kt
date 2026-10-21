package com.churchdrive.app.wear

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.wear.compose.material.Text
import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ui.alarmTone
import com.churchdrive.app.ui.toneColorsFor
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TRIGGERED_INK = Color(0xFF4D1F1C)

private val STATE_LABELS = mapOf(
    "disarmed" to "Disarmed", "armed_home" to "Armed Home", "armed_away" to "Armed Away", "armed_night" to "Armed Night",
    "arming" to "Arming", "pending" to "Entry Delay", "triggered" to "Triggered!",
)
private val MODE_NAMES = mapOf("armed_home" to "Home", "armed_away" to "Away", "armed_night" to "Night")

private val WHEN = DateTimeFormatter.ofPattern("dd MMM, HH:mm", Locale.UK).withZone(ZoneId.systemDefault())

private fun whenText(iso: String?): String? = runCatching { WHEN.format(Instant.parse(iso)) }.getOrNull()

private fun millisOf(iso: String?): Long? =
    runCatching { Instant.parse(iso).toEpochMilli() }.getOrNull() ?: runCatching { OffsetDateTime.parse(iso).toInstant().toEpochMilli() }.getOrNull()

/**
 * Who changed the alarm last, and when: the person Home Assistant recorded (`sensor.church_drive_alarm_actor`) when it is for the alarm's
 * current state and Ring's own time is not much newer, else Ring's own name (the phone's rule, so the watch names the same person).
 */
internal fun alarmWho(alarm: EntityState, actor: EntityState?, ringBy: String?, ringAt: String?): Pair<String, String>? {
    val a = actor?.attributes
    if (actor != null && a != null && actor.available && a.optString("alarm") == alarm.entityId && a.optString("alarm_state") == alarm.state) {
        val mine = millisOf(a.optString("at"))
        val ring = ringAt?.let { millisOf(it) }
        if (mine != null && (ring == null || ring <= mine + 120_000L)) return actor.state to a.optString("at")
    }
    return if (!ringBy.isNullOrBlank() && !ringAt.isNullOrBlank()) ringBy to ringAt else null
}

/** The two lines under the buttons: what to do during a delay, otherwise who armed or disarmed it, and when. */
internal fun alarmLines(alarm: EntityState, actor: EntityState?): Pair<String, String> {
    fun by(who: String?, at: String?) = alarmWho(alarm, actor, who, at)?.let { w -> whenText(w.second)?.let { t -> w.first to t } }
    return when (alarm.state) {
        "arming" -> "Leave now" to "until armed"
        "pending" -> "Disarm now" to "until the alarm sounds"
        "triggered" -> "Alarm sounding" to "Disarm to stop it"
        "disarmed" -> by(alarm.str("lastDisarmedBy"), alarm.str("lastDisarmedTime"))?.let { "Disarmed by ${it.first}" to it.second }
        else -> by(alarm.str("lastArmedBy"), alarm.str("lastArmedTime"))?.let { "Armed by ${it.first}" to it.second }
    } ?: ("" to "")
}

private class AlarmMode(val key: String, val label: String, val icon: String, val service: String)

/** The alarm face: its state in the phone's colours, the modes across the middle, and who and when; the ring is the entry or exit countdown. */
@Composable
fun alarmFace(entities: Map<String, EntityState>, config: WatchConfig): Face {
    val alarm = entities[ALARM]
    val actor = entities[ALARM_ACTOR]
    val state = alarm?.state
    val inDelay = state == "arming" || state == "pending"
    val triggered = state == "triggered"
    val colours = toneColorsFor(alarmTone(state), true)
    val bg = if (triggered) colours.accent else colours.container
    val accent = if (triggered) TRIGGERED_INK else colours.accent
    val ink = if (triggered) TRIGGERED_INK else Ink

    val secsLeft = when (state) {
        "pending" -> alarm?.num("entrySecondsLeft")?.toInt() ?: 0
        "arming" -> alarm?.num("exitSecondsLeft")?.toInt() ?: 0
        else -> 0
    }
    // Count down here between the house's updates; the ring is full at the start of a delay.
    var remaining by remember(state, secsLeft) { mutableIntStateOf(secsLeft) }
    val total = remember(state) { secsLeft.coerceAtLeast(1) }
    LaunchedEffect(state, secsLeft) {
        remaining = secsLeft
        while (remaining > 0) {
            delay(1000)
            remaining -= 1
        }
    }
    val fraction = if (inDelay) (remaining.toFloat() / total).coerceIn(0f, 1f) else 1f

    val target = alarm?.str("targetState")
    val title = when {
        alarm == null -> "Loading…"
        state == "arming" && MODE_NAMES[target] != null -> "Arming ${MODE_NAMES[target]}"
        else -> STATE_LABELS[state] ?: state.orEmpty().replaceFirstChar { it.uppercase() }
    }
    val features = alarm?.num("supported_features")?.toInt() ?: 0
    val modes = buildList {
        add(AlarmMode("disarmed", "Disarm", "shield-off-outline", "alarm_disarm"))
        if (features and 1 != 0 && !config.hides(WatchPage.Alarm, "armed_home")) add(AlarmMode("armed_home", "Home", "shield-home", "alarm_arm_home"))
        if (features and 2 != 0 && !config.hides(WatchPage.Alarm, "armed_away")) add(AlarmMode("armed_away", "Away", "shield-lock", "alarm_arm_away"))
        if (features and 4 != 0 && !config.hides(WatchPage.Alarm, "armed_night")) add(AlarmMode("armed_night", "Night", "shield-moon", "alarm_arm_night"))
    }
    val active = if (inDelay || triggered) target else state
    val (line1, line2) = alarm?.let { alarmLines(it, actor) } ?: ("" to "")
    val spec = RingSpec(bg = bg, a0 = -90f, sweep = 360f, trackColor = accent, trackAlpha = 0.2f, fill = RingFill(fraction, accent))

    return Face("alarm", spec) {
        val ui = LocalUi.current
        Box(Modifier.fillMaxSize()) {
            Text(
                title, color = if (triggered) ink else accent, fontSize = ui.sp(19f), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.at(ui, 30f, 44f, 220f),
            )
            ButtonRow(30f, 98f, 64f) {
                for (m in modes) {
                    val lit = active == m.key
                    ModeButton(
                        m.icon, m.label, lit, if (triggered) TRIGGERED_INK else accent, ink, Slot(), enabled = alarm != null,
                        onClick = { WatchData.call("alarm_control_panel", m.service, ALARM) },
                    )
                }
            }
            Column(Modifier.at(ui, 36f, 178f, 208f), horizontalAlignment = Alignment.CenterHorizontally) {
                if (inDelay) Text("${remaining}s", color = if (triggered) ink else accent, fontSize = ui.sp(34f), fontWeight = FontWeight.Bold)
                if (line1.isNotEmpty()) Text(line1, color = ink, fontSize = ui.sp(14f), fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (line2.isNotEmpty()) Text(line2, color = ink.copy(alpha = 0.8f), fontSize = ui.sp(12f), textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
