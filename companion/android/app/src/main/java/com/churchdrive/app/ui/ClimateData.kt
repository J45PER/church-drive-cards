package com.churchdrive.app.ui

import com.churchdrive.app.ha.EntityState
import org.json.JSONObject
import java.time.OffsetDateTime

/** A list attribute such as `preset_modes`, as text. */
fun EntityState.list(key: String): List<String> {
    val a = attributes.optJSONArray(key) ?: return emptyList()
    return (0 until a.length()).filter { !a.isNull(it) }.map { a.optString(it) }.filter { it.isNotBlank() }
}

/** "speed_1" as "Speed 1". */
fun presetLabel(value: String): String = value.replace('_', ' ').trim().replaceFirstChar { it.uppercase() }

/** The comfortable range of a room by its type, in °C, as on the dashboard's Climate panel. */

/** A room type with its comfortable range in °C (the dashboard's Climate Zone card types). */
data class RoomType(val name: String, val low: Double, val high: Double)

val ROOM_TYPES: Map<String, RoomType> = mapOf(
    "living" to RoomType("Living room", 19.0, 22.0),
    "bedroom" to RoomType("Bedroom", 16.0, 20.0),
    "office" to RoomType("Office / study", 19.0, 22.0),
    "hall" to RoomType("Hall / landing", 16.0, 21.0),
    "bathroom" to RoomType("Bathroom", 20.0, 24.0),
    "kitchen" to RoomType("Kitchen", 17.0, 21.0),
    "outside" to RoomType("Outside", 3.0, 25.0),
)

private val COMFORT = ROOM_TYPES.mapValues { it.value.low to it.value.high }


/** Green in the comfortable range; cyan or blue when it's cold, amber or red when it's hot, by how far out. */
fun comfortTone(temperature: Double?, type: String?): Tone {
    if (temperature == null) return Tone.Grey
    val (lo, hi) = COMFORT[type.orEmpty()] ?: (16.0 to 21.0)
    return when {
        temperature < lo -> if (lo - temperature > 3) Tone.Blue else Tone.Teal
        temperature > hi -> if (temperature - hi > 2.5) Tone.Red else Tone.Amber
        else -> Tone.Green
    }
}

fun pmWord(pm: Double?): String = when {
    pm == null -> ""
    pm <= 35 -> "Good"
    pm <= 75 -> "Fair"
    pm <= 115 -> "Poor"
    else -> "Very poor"
}

fun pmTone(pm: Double?): Tone = when {
    pm == null || pm <= 35 -> Tone.Green
    pm <= 75 -> Tone.Amber
    pm <= 115 -> Tone.Orange
    else -> Tone.Red
}

/** The mdi icon for a Home Assistant weather condition. */
fun weatherIcon(condition: String?): String = when (condition) {
    "clear-night" -> "mdi:weather-night"
    "cloudy" -> "mdi:weather-cloudy"
    "exceptional" -> "mdi:weather-sunny-alert"
    "fog" -> "mdi:weather-fog"
    "hail" -> "mdi:weather-hail"
    "lightning" -> "mdi:weather-lightning"
    "lightning-rainy" -> "mdi:weather-lightning-rainy"
    "partlycloudy" -> "mdi:weather-partly-cloudy"
    "pouring" -> "mdi:weather-pouring"
    "rainy" -> "mdi:weather-rainy"
    "snowy" -> "mdi:weather-snowy"
    "snowy-rainy" -> "mdi:weather-snowy-rainy"
    "sunny" -> "mdi:weather-sunny"
    "windy" -> "mdi:weather-windy"
    "windy-variant" -> "mdi:weather-windy-variant"
    else -> "mdi:weather-partly-cloudy"
}

/** One step of a forecast. */
data class ForecastPoint(val timeMs: Long, val temperature: Double?, val condition: String)

/** The forecast from a `weather/subscribe_forecast` event. */
fun parseForecast(event: JSONObject): List<ForecastPoint> {
    val a = event.optJSONArray("forecast") ?: return emptyList()
    return (0 until a.length()).mapNotNull { i ->
        val o = a.optJSONObject(i) ?: return@mapNotNull null
        val time = runCatching { OffsetDateTime.parse(o.optString("datetime")).toInstant().toEpochMilli() }.getOrNull()
            ?: return@mapNotNull null
        val t = if (o.has("temperature") && !o.isNull("temperature")) o.optDouble("temperature") else null
        ForecastPoint(time, t, o.optString("condition"))
    }
}

/** "19 Jul" from a time with any offset (such as "2026-07-19T09:00:00+00:00"), or null if it can't be read. */
fun shortDayOf(iso: String?): String? {
    val ms = parseMillis(iso) ?: return null
    return java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("d MMM", java.util.Locale.UK))
}

/** The sensor holding the home's climate quality score (0 to 100), made in Home Assistant from the rooms' temperatures, humidity and air. */
const val CLIMATE_QUALITY_ENTITY = "sensor.home_climate_quality"

/** The score in a word, with the same bands as the dashboard: Excellent from 85, Good 70, Fair 50, Poor 30, else Bad. */
fun qualityWord(score: Int): String = when {
    score >= 85 -> "Excellent"
    score >= 70 -> "Good"
    score >= 50 -> "Fair"
    score >= 30 -> "Poor"
    else -> "Bad"
}

/** The score's colour, as on the dashboard: green, amber, orange, red; grey when there's no score yet. */
fun qualityTone(score: Int?): Tone = when {
    score == null -> Tone.Grey
    score >= 70 -> Tone.Green
    score >= 50 -> Tone.Amber
    score >= 30 -> Tone.Orange
    else -> Tone.Red
}
