package com.churchdrive.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/**
 * The house's own colours for entities (Clive's palette: five colours per kind of thing, from design/clive/cards.json through
 * CliveColours.kt), with a light and a dark variant. Nothing follows the phone's wallpaper or Google's palette: green always
 * means "fine", amber "needs attention" and red "wrong", and each kind of thing keeps its own colour everywhere (lights yellow,
 * heating orange, Octopus pink and violet for electricity and gas, and so on).
 */
/** The colour of the dashboard panel a card sits in: a card with no colour of its own takes it, as on the dashboard. */
val LocalPanelTone = androidx.compose.runtime.compositionLocalOf<Tone?> { null }

/** The colours for a card that has none of its own: its panel's, else grey. */
@androidx.compose.runtime.Composable
fun panelNeutral(): ToneColors = toneColors(LocalPanelTone.current ?: Tone.Grey)

/**
 * A kind of colour. The first ten are the general ones the screens have always asked for; each now takes the palette set that
 * means the same ([toneSet]). The rest are the palette's own kinds of thing, for cards that are about that thing.
 */
enum class Tone {
    Green, Blue, Red, Purple, Amber, Orange, Teal, Indigo, Grey, Coral,
    Lights, Blinds, Plugs, Gregg, Heating, Cooling, Air, Energy, Gas, Car, Alarm, Doors,
    AlarmHome, AlarmAway, AlarmOff, ArmingHome, ArmingAway,
    Media, People, Tasks, Shopping, Calendar, Sunny, Night, Rain, Snow, Storm, Sport,
    TodoShared, TodoHouse, PersonJamie, PersonHayley, PersonIan, PersonDiane,
    Eco, Humidity, QualityGood, QualityFair, QualityPoor, PartlyCloudy, Cloudy, Fog, Wind, Hail, Unavailable,
}

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

/** The palette set a tone uses; null for Grey, which is the neutral colours. */
fun toneSet(tone: Tone): EntityColours? = when (tone) {
    Tone.Green -> CliveColours.Ok
    Tone.Red -> CliveColours.Bad
    Tone.Amber -> CliveColours.Warn
    Tone.Blue -> CliveColours.AlarmHome
    Tone.Purple -> CliveColours.Doors
    Tone.Orange, Tone.Heating -> CliveColours.Heating
    Tone.Teal, Tone.Air -> CliveColours.Air
    Tone.Indigo, Tone.Tasks -> CliveColours.Tasks
    Tone.Coral, Tone.Shopping -> CliveColours.Shopping
    Tone.Grey -> null
    Tone.Lights -> CliveColours.Lights
    Tone.Blinds -> CliveColours.Blinds
    Tone.Plugs -> CliveColours.Plugs
    Tone.Gregg -> CliveColours.Gregg
    Tone.Cooling -> CliveColours.Cooling
    Tone.Energy -> CliveColours.Energy
    Tone.Gas -> CliveColours.Gas
    Tone.Car -> CliveColours.Car
    Tone.Alarm -> CliveColours.Alarm
    Tone.Doors -> CliveColours.Doors
    Tone.AlarmHome -> CliveColours.AlarmHome
    Tone.AlarmAway -> CliveColours.AlarmAway
    Tone.AlarmOff -> CliveColours.AlarmOff
    Tone.ArmingHome -> CliveColours.ArmingHome
    Tone.ArmingAway -> CliveColours.ArmingAway
    Tone.Media -> CliveColours.Media
    Tone.People -> CliveColours.People
    Tone.Calendar -> CliveColours.Calendar
    Tone.Sunny -> CliveColours.Sunny
    Tone.Night -> CliveColours.Night
    Tone.Rain -> CliveColours.Rain
    Tone.Snow -> CliveColours.Snow
    Tone.Storm -> CliveColours.Storm
    Tone.Sport -> CliveColours.Sport
    Tone.TodoShared -> CliveColours.TodoShared
    Tone.TodoHouse -> CliveColours.TodoHouse
    Tone.PersonJamie -> CliveColours.PersonJamie
    Tone.PersonHayley -> CliveColours.PersonHayley
    Tone.PersonIan -> CliveColours.PersonIan
    Tone.PersonDiane -> CliveColours.PersonDiane
    Tone.Eco -> CliveColours.Eco
    Tone.Humidity -> CliveColours.Humidity
    Tone.QualityGood -> CliveColours.QualityGood
    Tone.QualityFair -> CliveColours.QualityFair
    Tone.QualityPoor -> CliveColours.QualityPoor
    Tone.PartlyCloudy -> CliveColours.PartlyCloudy
    Tone.Cloudy -> CliveColours.Cloudy
    Tone.Fog -> CliveColours.Fog
    Tone.Wind -> CliveColours.Wind
    Tone.Hail -> CliveColours.Hail
    Tone.Unavailable -> CliveColours.Unavailable
}

/** WCAG contrast ratio of two colours (1 to 21). */
fun contrastRatio(a: Color, b: Color): Float {
    val l1 = a.luminance()
    val l2 = b.luminance()
    return (maxOf(l1, l2) + 0.05f) / (minOf(l1, l2) + 0.05f)
}

/**
 * [c] as it is when it already reads on [on] (at least [min]:1), else moved towards [toward] just far enough to read. Palette colours
 * keep their own look wherever they are legible; only the too-dark ones are lifted.
 */
fun readableOn(c: Color, toward: Color, on: Color, min: Float = 4.5f): Color {
    if (contrastRatio(c, on) >= min) return c
    for (i in 1..20) {
        val m = lerp(c, toward, i / 20f)
        if (contrastRatio(m, on) >= min) return m
    }
    return toward
}

/**
 * Dark: the set's Dark ground with white (the neutral text colour) for text and icons, and its Colour for the strong colour (lifted towards its Light where it would be too dim to read). Light: a soft tint of its Light over white, with its
 * Deep for text and its Colour as the strong one; text on the strong colour is the set's Dark either way.
 */
private fun colorsOf(set: EntityColours, dark: Boolean): ToneColors =
    // Text and icons are white on the dark ground (not the card's colour); only status pills, selected buttons, gauges and lines take the colour, so every card reads the same; the colour is for the strong parts (selected tiles, gauges, lines).
    if (dark) readableOn(set.colour, set.light, set.dark).let { strong -> ToneColors(set.dark, CliveColours.NeutralDark.text, strong, set.dark) }
    else ToneColors(lerp(Color.White, set.light, 0.38f), set.deep, set.colour, set.dark)

private fun neutralColors(dark: Boolean): ToneColors =
    if (dark) CliveColours.NeutralDark.let { ToneColors(it.surface2, it.text, it.soft, it.background) }
    else CliveColours.NeutralLight.let { ToneColors(it.surface2, it.text, it.soft, it.surface) }

@Composable
fun toneColors(tone: Tone): ToneColors = toneColorsFor(tone, isSystemInDarkTheme())

/** The same colours without Compose, for places that draw themselves (the home-screen widgets). */
fun toneColorsFor(tone: Tone, dark: Boolean): ToneColors = toneSet(tone)?.let { colorsOf(LiveColours.resolve(it), dark) } ?: neutralColors(dark)

/** The alarm card's colours, as on the dashboard. */
fun alarmTone(state: String?): Tone = when (state) {
    "disarmed" -> Tone.AlarmOff
    "armed_home" -> Tone.AlarmHome
    "armed_away" -> Tone.AlarmAway
    "triggered" -> Tone.Red
    "armed_night" -> Tone.Alarm
    "arming" -> Tone.ArmingHome
    "pending" -> Tone.ArmingAway
    else -> Tone.Grey
}

/** The tone for a name from the palette (design/clive/cards.json), as a dashboard card's `color` can give it; null for any other name. */
fun toneForKey(key: String): Tone? = when (key) {
    "ok" -> Tone.Green
    "warn" -> Tone.Amber
    "bad" -> Tone.Red
    "lights" -> Tone.Lights
    "blinds" -> Tone.Blinds
    "plugs" -> Tone.Plugs
    "gregg" -> Tone.Gregg
    "heating" -> Tone.Heating
    "cooling" -> Tone.Cooling
    "air" -> Tone.Air
    "energy" -> Tone.Energy
    "gas" -> Tone.Gas
    "car" -> Tone.Car
    "alarm" -> Tone.Alarm
    "doors" -> Tone.Doors
    "alarm_home" -> Tone.AlarmHome
    "alarm_away" -> Tone.AlarmAway
    "alarm_off" -> Tone.AlarmOff
    "arming_home" -> Tone.ArmingHome
    "arming_away" -> Tone.ArmingAway
    "media" -> Tone.Media
    "people" -> Tone.People
    "tasks" -> Tone.Tasks
    "shopping" -> Tone.Shopping
    "calendar" -> Tone.Calendar
    "sunny" -> Tone.Sunny
    "night" -> Tone.Night
    "rain" -> Tone.Rain
    "snow" -> Tone.Snow
    "storm" -> Tone.Storm
    "sport" -> Tone.Sport
    "neutral" -> Tone.Grey
    "todo_shared" -> Tone.TodoShared
    "todo_house" -> Tone.TodoHouse
    "person_jamie" -> Tone.PersonJamie
    "person_hayley" -> Tone.PersonHayley
    "person_ian" -> Tone.PersonIan
    "person_diane" -> Tone.PersonDiane
    "eco" -> Tone.Eco
    "humidity" -> Tone.Humidity
    "quality_good" -> Tone.QualityGood
    "quality_fair" -> Tone.QualityFair
    "quality_poor" -> Tone.QualityPoor
    "partly_cloudy" -> Tone.PartlyCloudy
    "cloudy" -> Tone.Cloudy
    "fog" -> Tone.Fog
    "wind" -> Tone.Wind
    "hail" -> Tone.Hail
    "unavailable" -> Tone.Unavailable
    else -> null
}

/**
 * A tone for a Home Assistant colour: a colour name as the dashboards use it ("green", "deep-orange"), or a
 * hex colour (matched by its hue). Null when it isn't a colour, so the caller can use its own.
 */
fun toneFromColour(colour: String?): Tone? {
    val v = colour?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
    // A palette name (`lights`, `gregg`, `alarm_home`, `ok`…): the house's own colour for that kind of thing.
    toneForKey(v)?.let { return it }
    // A Home Assistant colour name ("green", "deep-orange"): the palette name it stands for (CliveColours.aliases, docs/CLIVE-COLOURS.md).
    CliveColours.aliases[v]?.let { toneForKey(it) }?.let { return it }
    // The house tasks' own colour on the dashboard (its hue is too near red to tell apart by hue).
    if (v == "#f2705c") return Tone.Coral
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
