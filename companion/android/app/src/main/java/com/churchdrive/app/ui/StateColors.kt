package com.churchdrive.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Meaning-carrying colours for entities, matching what the Home Assistant cards use
 * (disarmed green, armed home blue, armed away red, heating orange and so on) but in
 * Google's own palette, with a light and a dark variant. They stay fixed rather than
 * following the wallpaper, so green always means "fine" and red always means "look".
 */
enum class Tone { Green, Blue, Red, Purple, Amber, Orange, Teal, Indigo, Grey }

data class ToneColors(
    /** Card background. */
    val container: Color,
    /** Text and icons on the card background. */
    val onContainer: Color,
    /** The strong colour: selected buttons, gauges, a fully tinted card. */
    val accent: Color,
    /** Text and icons on the accent. */
    val onAccent: Color,
)

private fun light(container: Long, onContainer: Long, accent: Long) =
    ToneColors(Color(container), Color(onContainer), Color(accent), Color.White)

private fun dark(container: Long, onContainer: Long, accent: Long) =
    ToneColors(Color(container), Color(onContainer), Color(accent), Color(0xFF202124))

private val lightTones = mapOf(
    Tone.Green to light(0xFFE6F4EA, 0xFF0D652D, 0xFF1E8E3E),
    Tone.Blue to light(0xFFE8F0FE, 0xFF174EA6, 0xFF1A73E8),
    Tone.Red to light(0xFFFCE8E6, 0xFFA50E0E, 0xFFD93025),
    Tone.Purple to light(0xFFF3E8FD, 0xFF681DA8, 0xFF9334E6),
    Tone.Amber to light(0xFFFEF7E0, 0xFF7A4F01, 0xFFE37400),
    Tone.Orange to light(0xFFFEEFE3, 0xFF8C3A00, 0xFFE8590C),
    Tone.Teal to light(0xFFE0F7FA, 0xFF006064, 0xFF00838F),
    Tone.Indigo to light(0xFFE8EAF6, 0xFF283593, 0xFF5C6BC0),
    Tone.Grey to light(0xFFF1F3F4, 0xFF3C4043, 0xFF5F6368),
)

private val darkTones = mapOf(
    Tone.Green to dark(0xFF1B3A28, 0xFF81C995, 0xFF81C995),
    Tone.Blue to dark(0xFF1C3358, 0xFF8AB4F8, 0xFF8AB4F8),
    Tone.Red to dark(0xFF4D1F1C, 0xFFF28B82, 0xFFF28B82),
    Tone.Purple to dark(0xFF3A2552, 0xFFC58AF9, 0xFFC58AF9),
    Tone.Amber to dark(0xFF4A3A12, 0xFFFDD663, 0xFFFDD663),
    Tone.Orange to dark(0xFF4A2A14, 0xFFFFAB70, 0xFFFFAB70),
    Tone.Teal to dark(0xFF0F3A40, 0xFF4DD0E1, 0xFF4DD0E1),
    Tone.Indigo to dark(0xFF262B4A, 0xFF9FA8DA, 0xFF9FA8DA),
    Tone.Grey to dark(0xFF303134, 0xFFBDC1C6, 0xFF9AA0A6),
)

@Composable
fun toneColors(tone: Tone): ToneColors = toneColorsFor(tone, isSystemInDarkTheme())

/** The same colours without Compose, for places that draw themselves (the home-screen widgets). */
fun toneColorsFor(tone: Tone, dark: Boolean): ToneColors = (if (dark) darkTones else lightTones).getValue(tone)

/** The alarm card's colours, as on the dashboard. */
fun alarmTone(state: String?): Tone = when (state) {
    "disarmed" -> Tone.Green
    "armed_home" -> Tone.Blue
    "armed_away", "triggered" -> Tone.Red
    "armed_night" -> Tone.Purple
    "arming" -> Tone.Amber
    "pending" -> Tone.Orange
    else -> Tone.Grey
}

/**
 * A tone for a Home Assistant colour: a colour name as the dashboards use it ("green", "deep-orange"), or a
 * hex colour (matched by its hue). Null when it isn't a colour, so the caller can use its own.
 */
fun toneFromColour(colour: String?): Tone? {
    val v = colour?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
    when (v) {
        "green", "light-green", "lime" -> return Tone.Green
        "blue", "light-blue" -> return Tone.Blue
        "red" -> return Tone.Red
        "orange", "amber", "yellow" -> return Tone.Amber
        "deep-orange" -> return Tone.Orange
        "purple", "deep-purple", "pink" -> return Tone.Purple
        "indigo" -> return Tone.Indigo
        "teal", "cyan" -> return Tone.Teal
        "grey", "gray", "blue-grey", "brown", "disabled" -> return Tone.Grey
    }
    val hex = v.removePrefix("#")
    val full = if (hex.length == 3) hex.map { "$it$it" }.joinToString("") else hex
    if (full.length != 6) return null
    val rgb = full.toIntOrNull(16) ?: return null
    val r = (rgb shr 16 and 0xFF) / 255f
    val g = (rgb shr 8 and 0xFF) / 255f
    val b = (rgb and 0xFF) / 255f
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min
    if (max == 0f || delta / max < 0.15f) return Tone.Grey
    var hue = when (max) {
        r -> 60f * (((g - b) / delta) % 6)
        g -> 60f * ((b - r) / delta + 2)
        else -> 60f * ((r - g) / delta + 4)
    }
    if (hue < 0) hue += 360f
    return when {
        hue < 12f || hue >= 330f -> Tone.Red
        hue < 32f -> Tone.Orange
        hue < 62f -> Tone.Amber
        hue < 170f -> Tone.Green
        hue < 205f -> Tone.Teal
        hue < 228f -> Tone.Blue
        hue < 256f -> Tone.Indigo
        else -> Tone.Purple
    }
}
