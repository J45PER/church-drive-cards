package com.churchdrive.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import kotlin.math.roundToInt
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
 * Smooths jumpy readings the way the dashboard's graphs do: the time-weighted average of each of [slots] slices
 * of the period, then a gentle blend of neighbours. Fewer than three readings are left as they are.
 */
fun smoothSeries(points: List<Reading>, from: Long, now: Long, slots: Int = 96): List<Reading> {
    val sorted = points.sortedBy { it.time }
    if (sorted.size < 3) return sorted
    val step = (now - from).toDouble() / slots
    var j = 0
    var v: Double? = null
    while (j < sorted.size && sorted[j].time <= from) v = sorted[j++].value
    val avg = ArrayList<Reading>()
    for (k in 0 until slots) {
        val a = from + k * step
        val b = a + step
        var sum = 0.0
        var dur = 0.0
        var t = a
        while (j < sorted.size && sorted[j].time < b) {
            val tp = sorted[j].time.toDouble()
            val cur = v
            if (cur != null) {
                sum += cur * (tp - t)
                dur += tp - t
            }
            t = tp
            v = sorted[j++].value
        }
        val cur = v
        if (cur != null) {
            sum += cur * (b - t)
            dur += b - t
        }
        if (dur > 0) avg += Reading((a + step / 2).toLong(), sum / dur)
    }
    val weights = doubleArrayOf(1.0, 2.0, 3.0, 4.0, 3.0, 2.0, 1.0)
    val out = avg.mapIndexed { i, p ->
        var s = 0.0
        var n = 0.0
        weights.forEachIndexed { k, w ->
            avg.getOrNull(i + k - 3)?.let { s += it.value * w; n += w }
        }
        Reading(p.time, s / n)
    }
    return if (out.isEmpty()) out else out + Reading(now, out.last().value)
}

/** The room's history from `history/history_during_period` with attributes: temperature, humidity and target. */
data class ClimateHistory(val temps: List<Reading>, val hums: List<Reading>, val targets: List<Reading>)

/**
 * The readings of a thermostat are attributes of the climate entity (attributes arrive when they change, so the last
 * ones carry forward). Humidity falls back to its own sensor's states. A target is only there while it's heating or cooling.
 */
fun climateHistory(result: Any?, climate: String, humiditySensor: String?): ClimateHistory {
    val o = result as? org.json.JSONObject ?: return ClimateHistory(emptyList(), emptyList(), emptyList())
    fun time(p: org.json.JSONObject): Long? = when {
        p.has("lu") -> (p.optDouble("lu") * 1000).toLong()
        p.has("lc") -> (p.optDouble("lc") * 1000).toLong()
        else -> null
    }
    val temps = mutableListOf<Reading>()
    val hums = mutableListOf<Reading>()
    val targets = mutableListOf<Reading>()
    val a = o.optJSONArray(climate)
    var attrs = org.json.JSONObject()
    for (i in 0 until (a?.length() ?: 0)) {
        val p = a?.optJSONObject(i) ?: continue
        val t = time(p) ?: continue
        attrs = p.optJSONObject("a") ?: attrs
        fun num(key: String): Double? = if (attrs.has(key) && !attrs.isNull(key)) attrs.optDouble(key).takeIf { !it.isNaN() } else null
        num("current_temperature")?.let { temps += Reading(t, it) }
        num("current_humidity")?.let { hums += Reading(t, it) }
        if (p.optString("s") != "off") num("temperature")?.let { targets += Reading(t, it) }
    }
    if (hums.isEmpty() && humiditySensor != null) {
        val h = o.optJSONArray(humiditySensor)
        for (i in 0 until (h?.length() ?: 0)) {
            val p = h?.optJSONObject(i) ?: continue
            val v = p.optString("s").toDoubleOrNull() ?: continue
            hums += Reading(time(p) ?: continue, v)
        }
    }
    return ClimateHistory(temps.sortedBy { it.time }, hums.sortedBy { it.time }, targets.sortedBy { it.time })
}

/** One line of a graph, on its own scale ([lo] to [hi]), with what to draw around it. */
class GraphLayer(
    val points: List<Reading>,
    val colour: Color,
    val lo: Double,
    val hi: Double,
    val format: (Double) -> String,
    val fill: Boolean = false,
    /** A comfortable range: shaded, with dashed edges. */
    val band: Pair<Double, Double>? = null,
    val bandColour: Color = Color.Unspecified,
    /** Dotted lines (humidity limits). */
    val guides: List<Double> = emptyList(),
    /** The target, as a dashed line across. */
    val target: Double? = null,
)

/** A line through [xy], curved (the same Catmull-Rom curve the dashboard uses) when [curve]. */
private fun curvePath(xy: List<Offset>, curve: Boolean): Path {
    val path = Path()
    if (xy.isEmpty()) return path
    path.moveTo(xy[0].x, xy[0].y)
    if (!curve || xy.size < 3) {
        for (i in 1 until xy.size) path.lineTo(xy[i].x, xy[i].y)
        return path
    }
    for (i in 0 until xy.size - 1) {
        val p0 = xy.getOrElse(i - 1) { xy[i] }
        val p1 = xy[i]
        val p2 = xy[i + 1]
        val p3 = xy.getOrElse(i + 2) { p2 }
        path.cubicTo(
            p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f,
            p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f,
            p2.x, p2.y,
        )
    }
    return path
}

/**
 * The dashboard's history graph: each layer a (smoothed) line on its own scale, the temperature filled, the comfortable
 * range shaded, humidity limits dotted and the target dashed. Drag along it to read every value at that time.
 */
@Composable
fun LineGraph(layers: List<GraphLayer>, from: Long, now: Long, smooth: Boolean, ink: Color, legend: List<Pair<String, Color>>) {
    val drawn = layers.filter { it.points.size >= 2 }
    if (drawn.isEmpty()) return
    var scrub by remember { mutableStateOf<Float?>(null) }
    Column {
        Canvas(
            Modifier.fillMaxWidth().height(64.dp).pointerInput(drawn.size) {
                detectHorizontalDragGestures(
                    onDragStart = { scrub = (it.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = { scrub = null },
                    onDragCancel = { scrub = null },
                    onHorizontalDrag = { change, _ -> scrub = (change.position.x / size.width).coerceIn(0f, 1f) },
                )
            },
        ) {
            val w = size.width
            val h = size.height
            val pad = 3.dp.toPx()
            fun x(t: Long) = ((t.coerceAtLeast(from) - from).toFloat() / (now - from)) * w
            for (layer in drawn) {
                fun y(v: Double) = h - pad - ((v - layer.lo) / (layer.hi - layer.lo).let { if (it == 0.0) 1.0 else it }).toFloat() * (h - 2 * pad)
                layer.band?.let { (low, high) ->
                    drawRect(layer.bandColour.copy(alpha = 0.12f), Offset(0f, y(high)), Size(w, y(low) - y(high)))
                    for (v in listOf(low, high)) {
                        drawLine(layer.bandColour.copy(alpha = 0.55f), Offset(0f, y(v)), Offset(w, y(v)), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())))
                    }
                }
                for (v in layer.guides) {
                    drawLine(layer.colour.copy(alpha = 0.6f), Offset(0f, y(v)), Offset(w, y(v)), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.5.dp.toPx(), 3.dp.toPx())))
                }
                val xy = layer.points.map { Offset(x(it.time), y(it.value)) }
                val line = curvePath(xy, smooth)
                if (layer.fill) {
                    val area = Path().apply {
                        addPath(line)
                        lineTo(xy.last().x, h)
                        lineTo(xy.first().x, h)
                        close()
                    }
                    drawPath(area, layer.colour.copy(alpha = 0.16f))
                }
                drawPath(line, layer.colour, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                layer.target?.let { t ->
                    drawLine(layer.colour, Offset(0f, y(t)), Offset(w, y(t)), 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())))
                }
            }
            scrub?.let { f -> drawLine(ink.copy(alpha = 0.6f), Offset(f * w, 0f), Offset(f * w, h), 2f) }
        }
        val f = scrub
        if (f != null) {
            val t = from + ((now - from) * f).toLong()
            val readings = drawn.mapNotNull { l -> valueAt(l.points, t)?.let { l.format(it) } }
            Text(clockOf(t) + " · " + readings.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = ink)
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                legend.forEach { (text, colour) -> Text(text, style = MaterialTheme.typography.bodySmall, color = colour) }
            }
        }
    }
}

private const val GRAPH_HOURS = 24

/** 19.0 as "19", 19.5 as "19.5". */
private fun plain(d: Double) = if (d % 1.0 == 0.0) d.toInt().toString() else d.toString()

private fun List<Reading>.upTo(now: Long, current: Double?): List<Reading> =
    if (current == null) this else this + Reading(now, current)

/** The room's temperature and/or humidity over the last day, as on the dashboard's thermostat card (range, limits, target, smoothing). */
@Composable
fun ClimateGraph(
    climate: com.churchdrive.app.ha.EntityState?,
    id: String,
    humidity: com.churchdrive.app.ha.EntityState?,
    config: org.json.JSONObject,
    ink: Color,
    lineColour: Color,
) {
    val api = LocalHaApi.current ?: return
    val showT = config.optBoolean("show_temperature_history")
    val showH = config.optBoolean("show_humidity_history")
    val humiditySensor = humidity?.entityId
    var history by remember { mutableStateOf<ClimateHistory?>(null) }
    LaunchedEffect(id, humiditySensor) {
        while (true) {
            val ids = org.json.JSONArray().put(id).also { if (humiditySensor != null) it.put(humiditySensor) }
            val params = org.json.JSONObject()
                .put("start_time", Instant.now().minusSeconds(GRAPH_HOURS * 3600L).toString())
                .put("entity_ids", ids)
                .put("minimal_response", false)
                .put("no_attributes", false)
                .put("significant_changes_only", false)
            api.request("history/history_during_period", params) { history = climateHistory(it, id, humiditySensor) }
            delay(5 * 60_000L)
        }
    }
    val h = history ?: return
    val now = remember(history) { System.currentTimeMillis() }
    val from = now - GRAPH_HOURS * 3_600_000L
    val smooth = if (config.has("smooth_graphs")) config.optBoolean("smooth_graphs", true) else true
    val limits = if (config.has("show_limits")) config.optBoolean("show_limits", true) else true
    fun cfg(key: String, default: Double) = if (config.has(key) && !config.isNull(key) && config.optString(key).isNotBlank()) config.optDouble(key) else default
    val type = ROOM_TYPES[config.optString("room_type")] ?: ROOM_TYPES.getValue("living")
    val cLow = cfg("comfort_low", type.low)
    val cHigh = maxOf(cfg("comfort_high", type.high), cLow + 0.5)
    val hLow = cfg("humidity_low", 40.0)
    val hHigh = cfg("humidity_high", 60.0)
    val currentHumidity = humidity?.state?.toDoubleOrNull() ?: climate?.num("current_humidity")
    val purple = toneColors(Tone.Purple).accent
    val green = toneColors(Tone.Green).accent

    val layers = mutableListOf<GraphLayer>()
    val legend = mutableListOf<Pair<String, Color>>()
    val tRaw = if (showT) h.temps.upTo(now, climate?.num("current_temperature")) else emptyList()
    val hRaw = if (showH) h.hums.upTo(now, currentHumidity) else emptyList()
    val hums = if (smooth) smoothSeries(hRaw, from, now) else hRaw
    val temps = if (smooth) smoothSeries(tRaw, from, now) else tRaw
    val target = climate?.takeIf { it.state != "off" }?.num("temperature")
    if (hums.size > 1) {
        val vals = hums.map { it.value }
        val lo = minOf(vals.min(), if (limits) hLow - 5 else vals.min()) - 3
        val hi = maxOf(vals.max(), if (limits) hHigh + 5 else vals.max()) + 3
        layers += GraphLayer(hums, purple, lo, hi, { "${it.roundToInt()}% humidity" }, fill = temps.isEmpty(), guides = if (limits) listOf(hLow, hHigh) else emptyList())
        legend += if (limits) "┄ ${hLow.roundToInt()}–${hHigh.roundToInt()}%" to purple
        else "● Humidity ${vals.min().roundToInt()}–${vals.max().roundToInt()}%" to purple
    }
    if (temps.size > 1) {
        val vals = temps.map { it.value }
        val targets = h.targets.map { it.value } + listOfNotNull(target)
        val band = if (limits) listOf(cLow - 1, cHigh + 1) else emptyList()
        val lo = (vals + targets + band).min() - 0.5
        val hi = (vals + targets + band).max() + 0.5
        // The temperature is drawn first (under humidity), as on the dashboard.
        layers.add(
            0,
            GraphLayer(
                temps, lineColour, lo, hi, { "%.1f° room".format(it) }, fill = true,
                band = if (limits) cLow to cHigh else null, bandColour = green, target = target,
            ),
        )
        legend.add(
            0,
            if (limits) "▭ ${type.name} ${plain(cLow)}–${plain(cHigh)}°" to green
            else "● Temperature ${"%.1f".format(vals.min())}–${"%.1f".format(vals.max())}°" to lineColour,
        )
    }
    if (legend.size == 1) legend += "last 24 h" to ink.copy(alpha = 0.7f)
    if (layers.isNotEmpty()) LineGraph(layers, from, now, smooth, ink, legend)
}

/** A graph of one sensor's numeric history, smoothed (such as the air purifier's PM2.5, in the colour of its reading). */
@Composable
fun SensorGraph(id: String, hours: Int, unit: String, colour: Color, ink: Color, smooth: Boolean = true) {
    val load = LocalHistory.current
    var history by remember { mutableStateOf<Map<String, List<Pair<Long, String>>>>(emptyMap()) }
    LaunchedEffect(id, hours) {
        while (true) {
            load(listOf(id), hours) { history = it }
            delay(5 * 60_000L)
        }
    }
    val raw = numericSeries(history[id])
    if (raw.size < 2) return
    val now = remember(history) { System.currentTimeMillis() }
    val from = now - hours * 3_600_000L
    val series = if (smooth) smoothSeries(raw, from, now) else raw
    val lo = series.minOf { it.value } - 1
    val hi = series.maxOf { it.value } + 1
    LineGraph(
        listOf(GraphLayer(series, colour, lo, hi, { "${it.roundToInt()}$unit" }, fill = true)),
        from, now, smooth, ink, listOf("last $hours h" to ink.copy(alpha = 0.7f)),
    )
}
