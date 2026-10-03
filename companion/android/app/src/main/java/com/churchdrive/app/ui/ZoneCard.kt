package com.churchdrive.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.churchdrive.app.ha.CallService
import com.churchdrive.app.ha.EntityState
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.time.ZoneId

/**
 * Loads the history of some entities over the last hours: (entity ids, hours, done with each one's
 * (time in ms, state) list). Set in MainActivity from Home Assistant's history.
 */
val LocalHistory = compositionLocalOf<(List<String>, Int, (Map<String, List<Pair<Long, String>>>) -> Unit) -> Unit> { { _, _, done -> done(emptyMap()) } }

private fun kindColour(kind: ZoneKind): Color = when (kind) {
    ZoneKind.Motion -> Color(0xFF7986CB)
    ZoneKind.Door -> Color(0xFFFFA726)
    ZoneKind.Ring -> Color(0xFFF06292)
    ZoneKind.Tamper -> Color(0xFFE53935)
    ZoneKind.Light -> Color(0xFFFFD54F)
}

private fun levelTone(level: ZoneLevel): Tone = when (level) {
    ZoneLevel.Tamper -> Tone.Red
    ZoneLevel.Open -> Tone.Amber
    ZoneLevel.Off -> Tone.Grey
    else -> Tone.Indigo
}

/**
 * One outside or entry zone (Front Garden, Entrance, Driveway...): its name with its state ("Motion just now",
 * "Closed", "Open 4 min"), a strip of the last hours (motion, door open, doorbell, light on), the last events, each
 * battery on its own row, and the zone's light. Indigo normally, amber while a door is open, red on a tamper or a
 * door opened while the alarm is set.
 */
@Composable
fun ZoneCard(config: JSONObject, entities: Map<String, EntityState>, call: CallService) {
    val cfg = remember(config.toString()) { zoneConfig(config) }
    val loadHistory = LocalHistory.current
    var history by remember { mutableStateOf<Map<String, List<Pair<Long, String>>>>(emptyMap()) }
    val ids = cfg.historyIds()
    LaunchedEffect(ids, cfg.hours) {
        while (true) {
            loadHistory(ids, cfg.hours) { history = it }
            delay(5 * 60_000L)
        }
    }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }

    val view = zoneView(cfg, entities, history, now, ZoneId.systemDefault())
    val tone = toneColors(levelTone(view.level))
    val neutral = toneColors(Tone.Grey)
    // Open and tamper tint the whole card; otherwise it's the neutral card with an indigo title.
    val tinted = view.level == ZoneLevel.Open || view.level == ZoneLevel.Tamper
    val container = if (tinted) tone.container else neutral.container
    val ink = if (tinted) tone.onContainer else neutral.onContainer

    // Tapping the card opens that camera's events, when the zone has a camera.
    val eventsBase = cameraBaseName(entities, cfg.doorbell, cfg.motion.firstOrNull(), cfg.door, cfg.name)
    var showEvents by remember { mutableStateOf(false) }
    if (showEvents && eventsBase != null) CameraEventsViewer(cfg.name, eventsBase) { showEvents = false }
    var scrub by remember { mutableStateOf<Float?>(null) }
    EntityCard(
        container, ink,
        if (eventsBase != null) Modifier.clickable { showEvents = true } else Modifier,
    ) {
        if (view.warn.isNotEmpty()) {
            val red = toneColors(Tone.Red)
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(red.accent).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HaIcon("mdi:alert", Icons.Filled.Info, red.onAccent, 20.dp)
                Text(view.warn, color = red.onAccent, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(cfg.name, style = MaterialTheme.typography.titleLarge, maxLines = 1)
            Text(view.word, style = MaterialTheme.typography.titleMedium, color = tone.accent, maxLines = 1, modifier = Modifier.weight(1f))
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Strip(view.tracks, now, cfg.hours, cfg.ticks, ink, Modifier.weight(1f), scrub) { scrub = it }
                ZoneChip(view)
            }
            val from = now - cfg.hours * 3_600_000L
            val span = cfg.hours * 3_600_000L
            Row(modifier = Modifier.fillMaxWidth().padding(end = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf(0.0, 0.25, 0.5, 0.75).forEach { f ->
                    Text(clockMs(from + (f * span).toLong()), fontSize = 10.sp, color = ink.copy(alpha = 0.6f))
                }
                Text("now", fontSize = 10.sp, color = ink.copy(alpha = 0.6f))
            }
            // Dragging along the strip says what happened at that moment.
            scrub?.let { f ->
                val t = from + (span * f).toLong()
                Text(clockMs(t) + " · " + happenedAt(view.tracks, t, span / 48), style = MaterialTheme.typography.bodySmall, color = ink)
            }
        }
        if (cfg.showLast && view.last.isNotEmpty()) {
            Text(view.last, style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.75f))
        }
        if (view.bats.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { view.bats.forEach { BatteryRow(it, ink) } }
        }
        cfg.light?.let { id ->
            LightPill(entities[id], id, view.light?.first, toneColors(if (view.light?.second == true) Tone.Amber else Tone.Grey), call)
        }
    }
}

@Composable
private fun ZoneChip(view: ZoneView) {
    val tone = toneColors(levelTone(view.level))
    val green = toneColors(Tone.Green)
    val (bg, fg) = when {
        view.level == ZoneLevel.Motion ->
            (if (view.chip == "Doorbell") kindColour(ZoneKind.Ring) else toneColors(Tone.Indigo).accent) to Color.White
        view.level == ZoneLevel.Ok && view.chip == "Closed" -> green.accent.copy(alpha = 0.22f) to green.accent
        view.level == ZoneLevel.Ok || view.level == ZoneLevel.Off -> Color.Gray.copy(alpha = 0.22f) to Color.Gray
        else -> tone.accent.copy(alpha = 0.22f) to tone.accent
    }
    Box(modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(bg).padding(horizontal = 10.dp, vertical = 3.dp)) {
        Text(view.chip, color = fg, fontSize = 11.sp, maxLines = 1)
    }
}

/** The strip: the light as a faint band behind, then bars (busy periods) or ticks (each event). */
@Composable
private fun Strip(tracks: List<ZoneTrack>, now: Long, hours: Int, ticks: Boolean, ink: Color, modifier: Modifier, scrub: Float?, onScrub: (Float?) -> Unit) {
    val span = hours * 3_600_000L
    val from = now - span
    Canvas(
        modifier = modifier.height(22.dp).clip(RoundedCornerShape(6.dp)).background(ink.copy(alpha = 0.10f)).pointerInput(Unit) {
            detectHorizontalDragGestures(
                onDragStart = { onScrub((it.x / size.width).coerceIn(0f, 1f)) },
                onDragEnd = { onScrub(null) },
                onDragCancel = { onScrub(null) },
                onHorizontalDrag = { change, _ -> onScrub((change.position.x / size.width).coerceIn(0f, 1f)) },
            )
        },
    ) {
        val w = size.width
        val h = size.height
        fun x(t: Long) = ((t - from).toFloat() / span) * w
        for (g in 1..3) drawRect(ink.copy(alpha = 0.15f), Offset(w * g / 4f, 0f), Size(1f, h))
        tracks.filter { it.kind == ZoneKind.Light }.forEach { track ->
            track.spans.forEach { (a, b) ->
                val l = x(a).coerceAtLeast(0f)
                drawRect(kindColour(ZoneKind.Light).copy(alpha = 0.35f), Offset(l, 0f), Size(maxOf(w * 0.004f, x(b) - l), h))
            }
        }
        if (ticks) {
            tracks.filter { it.kind != ZoneKind.Light }.forEach { track ->
                track.spans.forEach { (a, b) ->
                    val l = x(a).coerceAtLeast(0f)
                    drawRoundRect(kindColour(track.kind), Offset(l, 3.dp.toPx()), Size(maxOf(2.dp.toPx(), x(b) - l), h - 6.dp.toPx()), CornerRadius(2.dp.toPx()))
                }
            }
        } else {
            val slots = 24
            stripBars(tracks, from, span, slots).forEach { bar ->
                val barW = w / slots - 0.8f / 100f * w
                val barH = h * bar.height
                drawRoundRect(
                    kindColour(bar.kind),
                    Offset(bar.slot * w / slots + 0.4f / 100f * w, h - barH),
                    Size(barW, barH),
                    CornerRadius(2.dp.toPx()),
                )
            }
        }
        scrub?.let { f -> drawRect(ink.copy(alpha = 0.8f), Offset(f * w, 0f), Size(2f, h)) }
    }
}

@Composable
private fun BatteryRow(b: ZoneBattery, ink: Color) {
    val low = b.level != null && b.level < LOW_BATTERY
    val colour = if (low) Color(0xFFFFA726) else ink.copy(alpha = 0.8f)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        HaIcon(
            if (b.level == null) "mdi:battery-outline" else if (low) "mdi:battery-alert" else b.icon,
            Icons.Filled.Info, colour, 17.dp,
        )
        Text(b.name, style = MaterialTheme.typography.bodyMedium, color = colour, maxLines = 1, modifier = Modifier.weight(1f))
        Box(modifier = Modifier.width(56.dp).height(5.dp).clip(RoundedCornerShape(99.dp)).background(ink.copy(alpha = 0.2f))) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(((b.level ?: 0.0) / 100.0).coerceIn(0.0, 1.0).toFloat())
                    .height(5.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(if (low) Color(0xFFFFA726) else Color(0xFF4CAF50)),
            )
        }
        Text(
            b.level?.let { "${it.toInt()}%" } ?: "–",
            style = MaterialTheme.typography.bodyMedium,
            color = if (low) colour else ink,
            modifier = Modifier.width(40.dp),
        )
    }
}
