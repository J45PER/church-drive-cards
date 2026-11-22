package com.churchdrive.app.ui

import com.churchdrive.app.ha.EntityState
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

/** What a light can do, from least to most. A group can do the most of any of its lights. */
enum class LightKind { Switch, Dim, White, Colour }

private val COLOUR_MODES = setOf("hs", "xy", "rgb", "rgbw", "rgbww")

/** The kind of light for its declared colour modes; [hasBrightness] covers a light that declares no modes but reports a level. */
fun lightKindOf(modes: List<String>, hasBrightness: Boolean = false): LightKind = when {
    modes.any { it in COLOUR_MODES } -> LightKind.Colour
    "color_temp" in modes -> LightKind.White
    modes.any { it != "onoff" } || (modes.isEmpty() && hasBrightness) -> LightKind.Dim
    else -> LightKind.Switch
}

fun EntityState.lightModes(): List<String> =
    attributes.optJSONArray("supported_color_modes")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()

fun EntityState.lightKind(): LightKind = lightKindOf(lightModes(), num("brightness") != null)

/** Whether a colour light can also make plain white (so it gets a White tab beside Colour). */
fun EntityState.hasWhiteMode(): Boolean = "color_temp" in lightModes()

/** The lights a group, room or zone is made of (Hue and light groups list them in `entity_id`); empty for a single light. */
fun EntityState.lightMembers(): List<String> =
    attributes.optJSONArray("entity_id")?.let { a -> (0 until a.length()).map { a.getString(it) }.filter { it.startsWith("light.") } } ?: emptyList()

/** The most a set of lights can do. */
fun groupKind(lights: List<EntityState>): LightKind = lights.map { it.lightKind() }.maxByOrNull { it.ordinal } ?: LightKind.Switch

/** Whether holding this light on its card should open its settings: it can do more than switch, or it is a group. */
fun opensSettings(light: EntityState?): Boolean =
    light != null && light.available && (light.lightKind() != LightKind.Switch || light.lightMembers().size > 1)

/** Warm to cool: the colour a white of [kelvin] looks like, as [r, g, b] (a standard curve, good enough for a slider). */
fun kelvinRgb(kelvin: Int): IntArray {
    val t = kelvin.coerceIn(1000, 10000) / 100.0
    val r = if (t <= 66) 255.0 else 329.698727446 * Math.pow(t - 60, -0.1332047592)
    val g = if (t <= 66) 99.4708025861 * Math.log(t) - 161.1195681661 else 288.1221695283 * Math.pow(t - 60, -0.0755148492)
    val b = if (t >= 66) 255.0 else if (t <= 19) 0.0 else 138.5177312231 * Math.log(t - 10) - 305.0447927307
    return intArrayOf(r.coerceIn(0.0, 255.0).roundToInt(), g.coerceIn(0.0, 255.0).roundToInt(), b.coerceIn(0.0, 255.0).roundToInt())
}

/** The colour temperature presets this light can reach. */
fun whitePresets(min: Int, max: Int): List<Pair<String, Int>> =
    listOf("Candle" to 2200, "Warm" to 2700, "Neutral" to 4000, "Daylight" to 5000, "Cool" to 6500)
        .filter { it.second in min..max }

fun kelvinWord(k: Int): String = when {
    k < 2400 -> "Candle"; k < 3200 -> "Warm"; k < 4500 -> "Neutral"; k < 5600 -> "Daylight"; else -> "Cool"
}

/** The white scenes in Church Drive's library, in the order a light's White section shows them. */
val WHITE_SCENES = listOf("bright", "cool_bright", "dimmed", "read", "concentrate", "energise", "relax", "rest", "nightlight")

/** A brightness bar moves to 1%, then in 5% steps from 5% to 100%: the nearest step to [fraction] (0 to 1), as a percentage. */
fun snapBrightness(fraction: Float): Int = if (fraction * 100f < 3f) 1 else ((fraction * 100f / 5f).roundToInt() * 5).coerceIn(5, 100)

/** Saved colours as hue and saturation pairs, kept as "hue,sat;hue,sat". Anything unreadable is left out. */
fun decodeSavedColours(text: String?): List<Pair<Double, Double>> =
    (text ?: "").split(';').mapNotNull { part ->
        val bits = part.split(',')
        val h = bits.getOrNull(0)?.toDoubleOrNull()
        val s = bits.getOrNull(1)?.toDoubleOrNull()
        if (bits.size == 2 && h != null && s != null) h to s else null
    }.take(MAX_SAVED_COLOURS)

fun encodeSavedColours(colours: List<Pair<Double, Double>>): String =
    colours.joinToString(";") { "${"%.1f".format(java.util.Locale.ROOT, it.first)},${"%.1f".format(java.util.Locale.ROOT, it.second)}" }
