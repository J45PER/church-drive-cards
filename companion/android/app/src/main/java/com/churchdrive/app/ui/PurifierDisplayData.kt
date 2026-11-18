package com.churchdrive.app.ui

import com.churchdrive.app.ha.EntityState
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The air purifier's display: its brightness light, as a few notches between Off and On.
 *
 * A Philips purifier's display ("Display backlight") is a Home Assistant light, but the device only has a few real steps: off, dim and bright
 * (anything from 1 to 254 comes back as 128, 255 is bright). So the card shows Off and a button for each step, not a free
 * brightness slider. The notches are percentages of full brightness: dim (50%) and bright (100%) unless the card lists others in
 * `display_levels`. The dashboard card has the same rules (`src/purifier-display-logic.js`): change both together.
 */
val DEFAULT_DISPLAY_LEVELS = listOf(50, 100)

/** The notches, as whole percentages from low to high; a list that gives none (or nonsense) is the default. */
fun displayLevels(config: JSONObject): List<Int> {
    val list = config.optJSONArray("display_levels")?.let { a -> (0 until a.length()).mapNotNull { i -> a.optDouble(i).takeIf { !it.isNaN() }?.roundToInt() } }.orEmpty()
        .filter { it in 1..100 }.distinct().sorted()
    return list.ifEmpty { DEFAULT_DISPLAY_LEVELS }
}

/**
 * The display light that belongs to a purifier fan: the one the card names (`display_entity`; an empty string means the card has none), else the
 * light that starts with the fan's name and has "display" in it (light.<name>_display_backlight), or null.
 */
fun displayEntityFor(config: JSONObject, fanId: String, entities: Map<String, EntityState>): String? {
    if (config.has("display_entity")) {
        val named = config.optString("display_entity")
        return if (named.isBlank()) null else named.takeIf { it in entities }
    }
    val base = fanId.substringAfter('.', "").takeIf { it.isNotEmpty() } ?: return null
    val ids = entities.keys.filter { it.startsWith("light.${base}_") && "display" in it }
    return (ids.firstOrNull { "backlight" in it } ?: ids.firstOrNull())
}

/** Which notch the display is at (0 for the lowest), or null when it is off, unavailable or has no notches; the nearest notch, the higher one on a tie. */
fun displayLevelIndex(state: String?, brightness: Double?, levels: List<Int>): Int? {
    if (state != "on" || levels.isEmpty()) return null
    if (brightness == null) return levels.size - 1
    val pct = brightness.coerceIn(0.0, 255.0) / 255.0 * 100.0
    var best = 0
    levels.forEachIndexed { i, l -> if (abs(l - pct) <= abs(levels[best] - pct)) best = i }
    return best
}

/** What a notch is called: Dim and Bright for two, Dim, Medium and Bright for three, else its percentage; "Off" for no notch. */
fun displayLevelWord(index: Int?, levels: List<Int>): String = when {
    index == null || index < 0 || index >= levels.size -> "Off"
    levels.size == 1 -> "On"
    levels.size == 2 -> listOf("Dim", "Bright")[index]
    levels.size == 3 -> listOf("Dim", "Medium", "Bright")[index]
    else -> "${levels[index]}%"
}

/** The brightness (0 to 255) a notch asks the light for. */
fun displayLevelBrightness(index: Int, levels: List<Int>): Int = (levels[index.coerceIn(0, levels.size - 1)] / 100.0 * 255.0).roundToInt()
