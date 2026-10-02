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
enum class Tone { Green, Blue, Red, Purple, Amber, Orange, Grey }

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
    Tone.Grey to light(0xFFF1F3F4, 0xFF3C4043, 0xFF5F6368),
)

private val darkTones = mapOf(
    Tone.Green to dark(0xFF1B3A28, 0xFF81C995, 0xFF81C995),
    Tone.Blue to dark(0xFF1C3358, 0xFF8AB4F8, 0xFF8AB4F8),
    Tone.Red to dark(0xFF4D1F1C, 0xFFF28B82, 0xFFF28B82),
    Tone.Purple to dark(0xFF3A2552, 0xFFC58AF9, 0xFFC58AF9),
    Tone.Amber to dark(0xFF4A3A12, 0xFFFDD663, 0xFFFDD663),
    Tone.Orange to dark(0xFF4A2A14, 0xFFFFAB70, 0xFFFFAB70),
    Tone.Grey to dark(0xFF303134, 0xFFBDC1C6, 0xFF9AA0A6),
)

@Composable
fun toneColors(tone: Tone): ToneColors =
    (if (isSystemInDarkTheme()) darkTones else lightTones).getValue(tone)

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
