package com.churchdrive.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** A reading at a time (epoch ms). */
data class Reading(val time: Long, val value: Double)

/** The numeric readings in a history list, in time order. Non-numbers (unavailable, unknown) are skipped. */
fun numericSeries(history: List<Pair<Long, String>>?): List<Reading> =
    history.orEmpty().mapNotNull { (t, s) -> s.toDoubleOrNull()?.let { Reading(t, it) } }.sortedBy { it.time }

/** The value at [time]: the last reading at or before it (a sensor holds its value until it changes), or null before the first. */
fun valueAt(series: List<Reading>, time: Long): Double? = series.lastOrNull { it.time <= time }?.value

fun clockOf(ms: Long): String =
    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))

/** What happened around a time on a zone's strip, as words ("Motion · Door open"), or "Quiet". [window] is how far either side counts. */
fun happenedAt(tracks: List<ZoneTrack>, time: Long, window: Long): String {
    val words = tracks.filter { t -> t.kind != ZoneKind.Light && t.spans.any { (a, b) -> a <= time + window && b >= time - window } }
        .map { zoneKindWord(it.kind) }.distinct()
    val light = tracks.any { t -> t.kind == ZoneKind.Light && t.spans.any { (a, b) -> a <= time && b >= time } }
    val all = words + if (light) listOf("Light on") else emptyList()
    return if (all.isEmpty()) "Quiet" else all.joinToString(" · ")
}

fun zoneKindWord(kind: ZoneKind): String = when (kind) {
    ZoneKind.Motion -> "Motion"
    ZoneKind.Door -> "Door open"
    ZoneKind.Ring -> "Doorbell"
    ZoneKind.Tamper -> "Tamper"
    ZoneKind.Light -> "Light on"
}

/**
 * A thermostat's temperature and humidity over the last day from `history/history_during_period` with attributes
 * (the room's readings are attributes of the climate entity). Humidity falls back to its own sensor's states.
 */
fun climateSeries(result: Any?, climate: String, humiditySensor: String?): Pair<List<Reading>, List<Reading>> {
    val o = result as? org.json.JSONObject ?: return emptyList<Reading>() to emptyList()
    fun time(p: org.json.JSONObject): Long? = when {
        p.has("lu") -> (p.optDouble("lu") * 1000).toLong()
        p.has("lc") -> (p.optDouble("lc") * 1000).toLong()
        else -> null
    }
    val temps = mutableListOf<Reading>()
    val hums = mutableListOf<Reading>()
    val a = o.optJSONArray(climate)
    var attrs = org.json.JSONObject()
    for (i in 0 until (a?.length() ?: 0)) {
        val p = a?.optJSONObject(i) ?: continue
        val t = time(p) ?: continue
        // Attributes are sent when they change: carry the last ones forward.
        attrs = p.optJSONObject("a") ?: attrs
        if (attrs.has("current_temperature") && !attrs.isNull("current_temperature")) temps += Reading(t, attrs.optDouble("current_temperature"))
        if (attrs.has("current_humidity") && !attrs.isNull("current_humidity")) hums += Reading(t, attrs.optDouble("current_humidity"))
    }
    if (hums.isEmpty() && humiditySensor != null) {
        val h = o.optJSONArray(humiditySensor)
        for (i in 0 until (h?.length() ?: 0)) {
            val p = h?.optJSONObject(i) ?: continue
            val v = p.optString("s").toDoubleOrNull() ?: continue
            hums += Reading(time(p) ?: continue, v)
        }
    }
    return temps.sortedBy { it.time } to hums.sortedBy { it.time }
}

/** The room's temperature (orange) and/or humidity (blue) over the last day, as on the dashboard's thermostat card. */
@Composable
fun ClimateGraph(climate: String, humiditySensor: String?, showTemperature: Boolean, showHumidity: Boolean, ink: Color) {
    val api = LocalHaApi.current ?: return
    var data by remember { mutableStateOf<Pair<List<Reading>, List<Reading>>?>(null) }
    LaunchedEffect(climate, humiditySensor) {
        while (true) {
            val ids = org.json.JSONArray().put(climate).also { if (humiditySensor != null) it.put(humiditySensor) }
            val params = org.json.JSONObject()
                .put("start_time", Instant.now().minusSeconds(24 * 3600L).toString())
                .put("entity_ids", ids)
                .put("minimal_response", false)
                .put("no_attributes", false)
                .put("significant_changes_only", false)
            api.request("history/history_during_period", params) { data = climateSeries(it, climate, humiditySensor) }
            delay(5 * 60_000L)
        }
    }
    val d = data ?: return
    val series = listOf(
        if (showTemperature) d.first else emptyList(),
        if (showHumidity) d.second else emptyList(),
    )
    val units = listOf("°", "%")
    val colours = listOf(Color(0xFFFF7043), Color(0xFF42A5F5))
    val used = series.indices.filter { series[it].size >= 2 }
    if (used.isEmpty()) return
    HistoryLines(used.map { series[it] }, used.map { units[it] }, used.map { colours[it] }, 24, ink)
}

/** A graph of one sensor's numeric history (such as the air purifier's PM2.5). */
@Composable
fun SensorGraph(id: String, hours: Int, unit: String, colour: Color, ink: Color) {
    val load = LocalHistory.current
    var history by remember { mutableStateOf<Map<String, List<Pair<Long, String>>>>(emptyMap()) }
    LaunchedEffect(id, hours) {
        while (true) {
            load(listOf(id), hours) { history = it }
            delay(5 * 60_000L)
        }
    }
    val series = numericSeries(history[id])
    if (series.size >= 2) HistoryLines(listOf(series), listOf(unit), listOf(colour), hours, ink)
}

/** Lines over the last hours, each on its own scale. Drag along the graph to read every value at that time. */
@Composable
fun HistoryLines(series: List<List<Reading>>, units: List<String>, colours: List<Color>, hours: Int, ink: Color, modifier: Modifier = Modifier) {
    val now = remember(series) { System.currentTimeMillis() }
    val from = now - hours * 3_600_000L
    var scrub by remember { mutableStateOf<Float?>(null) }
    Column(modifier) {
        Canvas(
            Modifier.fillMaxWidth().height(80.dp).pointerInput(series.size) {
                detectHorizontalDragGestures(
                    onDragStart = { scrub = (it.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = { scrub = null },
                    onDragCancel = { scrub = null },
                    onHorizontalDrag = { change, _ -> scrub = (change.position.x / size.width).coerceIn(0f, 1f) },
                )
            },
        ) {
            fun x(t: Long) = ((t - from).toFloat() / (now - from)) * size.width
            series.forEachIndexed { i, s ->
                val lo = s.minOf { it.value }
                val hi = s.maxOf { it.value }.let { if (it - lo < 1.0) lo + 1.0 else it }
                fun y(v: Double) = size.height - ((v - lo) / (hi - lo)).toFloat() * (size.height - 8f) - 4f
                val path = Path()
                s.filter { it.time >= from }.forEachIndexed { n, r ->
                    if (n == 0) path.moveTo(x(r.time), y(r.value)) else path.lineTo(x(r.time), y(r.value))
                }
                s.lastOrNull()?.let { path.lineTo(size.width, y(it.value)) }
                drawPath(path, colours[i % colours.size], style = Stroke(width = 3.dp.toPx()))
            }
            scrub?.let { f -> drawLine(ink.copy(alpha = 0.6f), Offset(f * size.width, 0f), Offset(f * size.width, size.height), 2f) }
        }
        val f = scrub
        val text = if (f != null) {
            val t = from + ((now - from) * f).toLong()
            clockOf(t) + " · " + series.indices.joinToString(" · ") { i -> valueAt(series[i], t)?.let { "%.1f".format(it) + units[i] } ?: "–" }
        } else "Last $hours hours · drag to read"
        Text(text, style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.7f))
    }
}
