package com.churchdrive.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

/** The colour maths the light pages and the Admin app's saved colours share. */

/** A hue (0-360) and saturation (0-100) to [r, g, b]. */
fun hsToRgb(hue: Double, sat: Double): IntArray {
    val s = (sat / 100.0).coerceIn(0.0, 1.0)
    val h = ((hue % 360 + 360) % 360) / 60.0
    val c = s
    val x = c * (1 - Math.abs(h % 2 - 1))
    val (r, g, b) = when (h.toInt()) {
        0 -> Triple(c, x, 0.0); 1 -> Triple(x, c, 0.0); 2 -> Triple(0.0, c, x)
        3 -> Triple(0.0, x, c); 4 -> Triple(x, 0.0, c); else -> Triple(c, 0.0, x)
    }
    val m = 1 - c
    return intArrayOf(((r + m) * 255).roundToInt(), ((g + m) * 255).roundToInt(), ((b + m) * 255).roundToInt())
}

/** A touch at ([x], [y]) from the wheel's centre on a wheel of [radius]: its hue and saturation (the edge is fully saturated). */
fun wheelHueSat(x: Double, y: Double, radius: Double): Pair<Double, Double> {
    val hue = (Math.toDegrees(atan2(y, x)) + 360) % 360
    val sat = (hypot(x, y) / radius * 100).coerceIn(0.0, 100.0)
    return hue to sat
}

/** Where a hue and saturation sit on the wheel, as an offset from its centre in units of the radius: the inverse of [wheelHueSat]. */
fun wheelPoint(hue: Double, sat: Double): Pair<Double, Double> {
    val a = Math.toRadians(hue)
    val d = sat / 100.0
    return Math.cos(a) * d to Math.sin(a) * d
}

/** Most colours the house keeps. */
const val MAX_SAVED_COLOURS = 8

/** Whether two hue and saturation pairs are the same colour to the eye (a few degrees of hue, a few points of saturation). */
fun sameColour(a: Pair<Double, Double>, b: Pair<Double, Double>): Boolean {
    val dh = Math.abs(a.first - b.first).let { if (it > 180) 360 - it else it }
    return dh < 3.0 && Math.abs(a.second - b.second) < 3.0
}

/** [colour] added to the saved ones (up to [MAX_SAVED_COLOURS]), or taken out when it is already saved. */
fun toggleSavedColour(saved: List<Pair<Double, Double>>, colour: Pair<Double, Double>): List<Pair<Double, Double>> =
    if (saved.any { sameColour(it, colour) }) saved.filterNot { sameColour(it, colour) }
    else if (saved.size >= MAX_SAVED_COLOURS) saved
    else saved + colour

/** The colours the house keeps for lights as hue and saturation pairs, with how to change them. Shared by every light's settings and the Admin app. */
class SavedColoursState(val colours: List<Pair<Double, Double>>, val set: (List<Pair<Double, Double>>) -> Unit)

val LocalSavedColours = compositionLocalOf { SavedColoursState(emptyList()) {} }

/** The house's answer to `church_drive/saved_colours`, or null if it didn't answer. */
fun parseSavedColours(result: Any?): List<Pair<Double, Double>>? {
    val a = (result as? JSONObject)?.optJSONArray("colours") ?: return null
    return (0 until a.length()).mapNotNull { i ->
        val pair = a.optJSONArray(i) ?: return@mapNotNull null
        if (pair.length() != 2) null else pair.optDouble(0) to pair.optDouble(1)
    }.take(MAX_SAVED_COLOURS)
}

/** The body of `church_drive/saved_colours/set` for [colours]. */
fun savedColoursBody(colours: List<Pair<Double, Double>>): JSONObject =
    JSONObject().put("colours", JSONArray(colours.map { JSONArray(listOf(it.first, it.second)) }))

/**
 * The saved colours, asked for once and again whenever they change in the house. [legacy] is what this phone kept on its own before they lived in
 * the house: the first time the house has none, they are sent up and [onMigrated] says the phone can forget them.
 */
@Composable
fun rememberSavedColoursState(api: HaApi?, legacy: List<Pair<Double, Double>> = emptyList(), onMigrated: () -> Unit = {}): SavedColoursState {
    val state = remember { mutableStateOf<List<Pair<Double, Double>>>(emptyList()) }
    var changed by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(api, changed) {
        val got = parseSavedColours(api?.ask("church_drive/saved_colours", JSONObject())) ?: return@LaunchedEffect
        if (got.isEmpty() && legacy.isNotEmpty()) {
            state.value = legacy
            api?.ask("church_drive/saved_colours/set", savedColoursBody(legacy))
            onMigrated()
        } else {
            state.value = got
        }
    }
    DisposableEffect(api) {
        val sub = api?.subscribe("subscribe_events", JSONObject().put("event_type", "church_drive_saved_colours_changed")) { changed++ } ?: -1
        onDispose { if (sub >= 0) api?.close(sub) }
    }
    return SavedColoursState(state.value) { next ->
        state.value = next
        scope.launch { api?.ask("church_drive/saved_colours/set", savedColoursBody(next)) }
    }
}
