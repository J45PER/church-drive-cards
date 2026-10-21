package com.churchdrive.app.wear

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ui.Tone
import com.churchdrive.app.ui.ToneColors
import com.churchdrive.app.ui.toneColorsFor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.roundToInt

/** The phone app's colours for a tone, in its dark variant (the watch is always dark). */
fun tone(t: Tone): ToneColors = toneColorsFor(t, true)

/** A face's text colour. */
val Ink = Color.White

/**
 * A value that follows the finger at once while the house is told once it stops: [value] is what to show (the finger's, until the
 * house has had time to agree), [dragging] is true until the call has gone.
 */
class Pending(private val scope: CoroutineScope) {
    var value by mutableStateOf<Float?>(null)
        private set
    var dragging by mutableStateOf(false)
        private set
    private var job: Job? = null

    /** Shows [v] now and runs [send] with the last value [quietMs] after the last change. */
    fun set(v: Float, quietMs: Long = 450, send: suspend (Float) -> Unit) {
        value = v
        dragging = true
        job?.cancel()
        job = scope.launch {
            delay(quietMs)
            dragging = false
            send(v)
            // The house takes a moment to say the new value; until then keep showing it.
            delay(2_500)
            value = null
        }
    }
}

@Composable
fun rememberPending(key: Any?): Pending {
    val scope = rememberCoroutineScope()
    return remember(key) { Pending(scope) }
}

/** [v] moved onto a grid of [step] starting at [min], and kept between [min] and [max] (a thermostat's own steps: 18, 18.5, 19). */
fun snapTo(v: Float, step: Float, min: Float, max: Float): Float {
    val s = if (step > 0f) step else 0.5f
    val snapped = min + ((v - min) / s).roundToInt() * s
    return (snapped.coerceIn(min, max) * 100f).roundToInt() / 100f
}

/** "19.5" or "19": no trailing zero. */
fun temp(v: Float?): String = if (v == null) "–" else ((v * 10f).roundToInt() / 10f).let { if (it == it.toInt().toFloat()) it.toInt().toString() else it.toString() }

fun EntityState.strings(attr: String): List<String> = attributes.optJSONArray(attr).let { a: JSONArray? -> if (a == null) emptyList() else (0 until a.length()).map { a.optString(it) } }

fun presetLabel(value: String): String = value.replace('_', ' ').trim().replaceFirstChar { it.uppercase() }

/** The mode that means "on" for a thermostat that is off: Heat, else Auto, else the first mode that isn't Off. */
fun onMode(modes: List<String>): String? = listOf("heat", "auto", "heat_cool").firstOrNull { it in modes } ?: modes.firstOrNull { it != "off" }

// ---------------------------------------------------------------------------------------------------- fans

/** One speed of a fan: its number, and the preset or the percentage that sets it. */
data class FanSpeed(val n: Int, val preset: String?, val percentage: Int?)

private val SPEED_NAME = Regex("^speed[ _-]?(\\d+)$", RegexOption.IGNORE_CASE)

fun speedNumber(preset: String): Int? = SPEED_NAME.find(preset)?.groupValues?.get(1)?.toIntOrNull()

/** The speeds a fan offers, as the dashboard's fan card works them out: `speed_N` presets, else steps of its percentage. */
fun fanSpeeds(fan: EntityState?): List<FanSpeed> {
    if (fan == null) return emptyList()
    val presets = fan.strings("preset_modes").filter { speedNumber(it) != null }.sortedBy { speedNumber(it) }
    if (presets.isNotEmpty()) return presets.map { FanSpeed(speedNumber(it) ?: 0, it, null) }
    val step = fan.num("percentage_step") ?: 0.0
    val count = if (step > 0) Math.round(100 / step).toInt() else 0
    if (count < 2 || count > 6) return emptyList()
    return (0 until count).map { FanSpeed(it + 1, null, Math.round(step * (it + 1)).toInt()) }
}

/** Which speed a running fan is at (its index in [speeds]), or -1 for a preset that isn't a speed, or when it is off. */
fun currentSpeed(fan: EntityState, speeds: List<FanSpeed>): Int {
    if (fan.state != "on") return -1
    val preset = fan.str("preset_mode")
    val percentage = fan.num("percentage") ?: 0.0
    return speeds.indexOfFirst { sp -> if (sp.preset != null) sp.preset == preset else abs(percentage - (sp.percentage ?: 0)) < 5 }
}

fun speedIcon(i: Int, count: Int): String {
    val gauges = listOf("speedometer-slow", "speedometer-medium", "speedometer")
    val slot = minOf(2, Math.round(i.toDouble() / maxOf(1, count - 1) * 2).toInt())
    return gauges[slot]
}

fun fanPresetIcon(preset: String): String = when (preset.lowercase()) {
    "natural", "nature", "breeze" -> "weather-windy"
    "sleep", "night" -> "power-sleep"
    "auto", "smart" -> "fan-auto"
    "turbo", "boost" -> "rocket-launch"
    "eco" -> "leaf"
    else -> "fan"
}

fun purifierModeIcon(mode: String): String = when (mode.lowercase()) {
    "auto", "auto (general)" -> "autorenew"
    "allergen" -> "flower"
    "medium" -> "fan"
    "turbo" -> "rocket-launch"
    "sleep", "night" -> "power-sleep"
    "low" -> "fan-speed-1"
    "high" -> "fan-speed-3"
    else -> speedNumber(mode)?.let { "fan-speed-${it.coerceIn(1, 3)}" } ?: "fan"
}

/** A short list as rows: up to [perRow] in the first, the rest in the second (five is 3 + 2, as the phone shares tiles). */
fun <T> rowsOf(items: List<T>, perRow: Int = 4): List<List<T>> {
    if (items.isEmpty()) return emptyList()
    val rows = (items.size + perRow - 1) / perRow
    val base = items.size / rows
    val extra = items.size % rows
    var at = 0
    return List(rows) { r -> val n = if (r < extra) base + 1 else base; items.subList(at, at + n).also { at += n } }
}

fun json(vararg pairs: Pair<String, Any>): JSONObject = JSONObject().apply { pairs.forEach { put(it.first, it.second) } }
