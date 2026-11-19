package com.churchdrive.app.ui

import com.churchdrive.app.ha.EntityState
import org.json.JSONObject
import java.time.OffsetDateTime

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


/**
 * A room's comfortable range: its own numbers where it has them ([low] and [high], from number helpers such as the assistant's range for
 * a thermostat), else its room type's, else 16 to 21.
 */
fun comfortRange(type: String?, low: Double? = null, high: Double? = null): Pair<Double, Double> {
    val (typeLow, typeHigh) = COMFORT[type.orEmpty()] ?: (16.0 to 21.0)
    val lo = low ?: typeLow
    return lo to maxOf(high ?: typeHigh, lo + 0.5)
}

/**
 * The card's config with the live comfortable range in it (`comfort_low` and `comfort_high`): from the number helpers the card names
 * (`comfort_low_entity`, `comfort_high_entity`), else the assistant's range for this thermostat, else left as the card has it.
 */
fun withLiveRange(config: JSONObject, entities: Map<String, EntityState>, zone: ZoneControl?): JSONObject {
    fun live(key: String) = config.optString(key).takeIf { it.isNotBlank() }?.let { entities[it]?.state?.toDoubleOrNull() }
    val lo = live("comfort_low_entity") ?: zone?.lowValue
    val hi = live("comfort_high_entity") ?: zone?.highValue
    if (lo == null && hi == null) return config
    return JSONObject(config.toString()).also { c ->
        lo?.let { c.put("comfort_low", it) }
        hi?.let { c.put("comfort_high", it) }
    }
}

/** Green in the comfortable range; cyan or blue when it's cold, amber or red when it's hot, by how far out. */
fun comfortTone(temperature: Double?, type: String?, low: Double? = null, high: Double? = null): Tone {
    if (temperature == null) return Tone.Grey
    val (lo, hi) = comfortRange(type, low, high)
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
    pm == null || pm <= 35 -> Tone.QualityGood
    pm <= 75 -> Tone.QualityFair
    else -> Tone.QualityPoor
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

/** The palette for a Home Assistant weather condition: the icon takes it, the card ground stays neutral. */
fun weatherTone(condition: String?): Tone = when (condition) {
    "sunny" -> Tone.Sunny
    "clear-night" -> Tone.Night
    "partlycloudy" -> Tone.PartlyCloudy
    "cloudy" -> Tone.Cloudy
    "fog" -> Tone.Fog
    "hail" -> Tone.Hail
    "rainy", "pouring" -> Tone.Rain
    "lightning", "lightning-rainy", "exceptional" -> Tone.Storm
    "snowy", "snowy-rainy" -> Tone.Snow
    "windy", "windy-variant" -> Tone.Wind
    else -> Tone.Cloudy
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

/** One day of the week's forecast: its high and low, what it'll be like, the chance of rain (%) and the wind (in the house's unit). */
data class ForecastDay(
    val timeMs: Long,
    val high: Double?,
    val low: Double?,
    val condition: String,
    val rainChance: Int?,
    val wind: Double?,
    /** Rain in mm, wind direction in degrees, humidity (%) and UV index, where the forecast has them. */
    val rain: Double? = null,
    val bearing: Double? = null,
    val humidity: Double? = null,
    val uv: Double? = null,
)

/** One hour of the forecast (forecast_type "hourly"; Met Office/met.no give about the next 48 hours). */
data class ForecastHour(val timeMs: Long, val temperature: Double?, val condition: String, val rain: Double?, val wind: Double?, val bearing: Double?)

private fun JSONObject.forecastNum(key: String) = if (has(key) && !isNull(key)) optDouble(key).takeIf { !it.isNaN() } else null

private fun JSONObject.forecastTime() = runCatching { OffsetDateTime.parse(optString("datetime")).toInstant().toEpochMilli() }.getOrNull()

/** The daily forecast from a `weather/subscribe_forecast` event (forecast_type "daily"). */
fun parseDailyForecast(event: JSONObject): List<ForecastDay> {
    val a = event.optJSONArray("forecast") ?: return emptyList()
    return (0 until a.length()).mapNotNull { i ->
        val o = a.optJSONObject(i) ?: return@mapNotNull null
        val time = o.forecastTime() ?: return@mapNotNull null
        ForecastDay(
            time, o.forecastNum("temperature"), o.forecastNum("templow"), o.optString("condition"), o.forecastNum("precipitation_probability")?.toInt(),
            o.forecastNum("wind_speed"), o.forecastNum("precipitation"), o.forecastNum("wind_bearing"), o.forecastNum("humidity"), o.forecastNum("uv_index"),
        )
    }
}

/** The hourly forecast from a `weather/subscribe_forecast` event (forecast_type "hourly"). */
fun parseHourlyForecast(event: JSONObject): List<ForecastHour> {
    val a = event.optJSONArray("forecast") ?: return emptyList()
    return (0 until a.length()).mapNotNull { i ->
        val o = a.optJSONObject(i) ?: return@mapNotNull null
        val time = o.forecastTime() ?: return@mapNotNull null
        ForecastHour(time, o.forecastNum("temperature"), o.optString("condition"), o.forecastNum("precipitation"), o.forecastNum("wind_speed"), o.forecastNum("wind_bearing"))
    }
}

/** A part of a day in an opened forecast day: its weather (the most common in those hours), temperatures, rain and strongest wind. */
data class DayPart(val name: String, val condition: String, val low: Double?, val high: Double?, val rain: Double, val wind: Double?, val bearing: Double?)

/**
 * A day's hours as Morning (06-12), Afternoon (12-18), Evening (18-24) and Night (that night, 00-06 the next day), leaving out a
 * part with no hours (one already past today, or beyond the hourly forecast). [dayMs] is any time on that day.
 */
fun dayParts(hours: List<ForecastHour>, dayMs: Long, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): List<DayPart> {
    val date = java.time.Instant.ofEpochMilli(dayMs).atZone(zone).toLocalDate()
    fun at(d: java.time.LocalDate, h: Int) = d.atStartOfDay(zone).plusHours(h.toLong()).toInstant().toEpochMilli()
    val parts = listOf(
        Triple("Morning", at(date, 6), at(date, 12)),
        Triple("Afternoon", at(date, 12), at(date, 18)),
        Triple("Evening", at(date, 18), at(date.plusDays(1), 0)),
        Triple("Night", at(date.plusDays(1), 0), at(date.plusDays(1), 6)),
    )
    return parts.mapNotNull { (name, from, to) ->
        val inPart = hours.filter { it.timeMs in from until to }
        if (inPart.isEmpty()) return@mapNotNull null
        val temps = inPart.mapNotNull { it.temperature }
        val windiest = inPart.filter { it.wind != null }.maxByOrNull { it.wind!! }
        val condition = inPart.groupingBy { it.condition }.eachCount().maxByOrNull { it.value }!!.key
        DayPart(name, condition, temps.minOrNull(), temps.maxOrNull(), inPart.sumOf { it.rain ?: 0.0 }, windiest?.wind, windiest?.bearing)
    }
}

/** A wind direction in degrees as a compass point ("N", "NE"... "NW"): where the wind comes from. */
fun compassPoint(bearing: Double): String = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")[(((bearing % 360) + 360) % 360 / 45.0 + 0.5).toInt() % 8]

/** "Today", "Tomorrow", then "Thursday" for a day [days] after today. */
fun forecastDayName(timeMs: Long, nowMs: Long = System.currentTimeMillis(), zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): String {
    val day = java.time.Instant.ofEpochMilli(timeMs).atZone(zone).toLocalDate()
    val today = java.time.Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
    return when (java.time.temporal.ChronoUnit.DAYS.between(today, day)) {
        0L -> "Today"
        1L -> "Tomorrow"
        else -> day.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.UK)
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

/** One service call on a thermostat: the service and its data. */
data class ClimateStep(val service: String, val key: String, val value: String)

/** The mode that means "on" for a thermostat that is off: Heat, else Auto, else the first mode that isn't Off. */
fun onMode(modes: List<String>): String? =
    listOf("heat", "auto", "heat_cool").firstOrNull { it in modes } ?: modes.firstOrNull { it != "off" }

/**
 * What a quick setting does, as the calls to make in order (the same as the dashboard's thermostat card). A preset such as
 * Eco only takes hold while the thermostat is on, so Eco pressed on a thermostat that is off switches it on first:
 * one tap, not Heat and then Eco. A mode (Heat) also leaves any preset (Eco).
 */
fun quickSteps(climate: EntityState?, q: QuickSetting): List<ClimateStep> {
    val steps = mutableListOf<ClimateStep>()
    val preset = q.presetMode?.takeIf { it.isNotBlank() }
    if (q.hvacMode != null) {
        steps += ClimateStep("set_hvac_mode", "hvac_mode", q.hvacMode)
        val active = climate?.str("preset_mode")
        if (q.hvacMode != "off" && !active.isNullOrBlank() && active != "none" && "none" in (climate?.list("preset_modes").orEmpty())) {
            steps += ClimateStep("set_preset_mode", "preset_mode", "none")
        }
    } else if (preset != null) {
        if (preset != "none" && climate?.state == "off") {
            onMode(climate?.list("hvac_modes").orEmpty())?.let { steps += ClimateStep("set_hvac_mode", "hvac_mode", it) }
        }
        steps += ClimateStep("set_preset_mode", "preset_mode", preset)
    }
    return steps
}
