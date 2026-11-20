package com.churchdrive.app

import com.churchdrive.app.ui.ForecastDay
import com.churchdrive.app.ui.ForecastHour
import com.churchdrive.app.ui.dayDescription
import com.churchdrive.app.ui.rainWord
import com.churchdrive.app.ui.uvWord
import com.churchdrive.app.ui.windWord
import com.churchdrive.app.ui.compassPoint
import com.churchdrive.app.ui.dayParts
import com.churchdrive.app.ui.forecastDayName
import com.churchdrive.app.ui.parseHourlyForecast
import com.churchdrive.app.ui.parseDailyForecast
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ForecastDayTest {
    private val zone = ZoneId.of("Europe/London")

    @Test fun readsTheDailyForecast() {
        val days = parseDailyForecast(JSONObject("""{"forecast":[
            {"datetime":"2026-11-17T12:00:00+00:00","condition":"rainy","temperature":9.4,"templow":4.1,"precipitation_probability":80,"wind_speed":24.5},
            {"datetime":"2026-11-18T12:00:00+00:00","condition":"sunny","temperature":7.0,"templow":null},
            {"datetime":"not a date","condition":"sunny"}
        ]}"""))
        assertEquals(2, days.size)
        assertEquals(9.4, days[0].high!!, 0.0)
        assertEquals(4.1, days[0].low!!, 0.0)
        assertEquals(80, days[0].rainChance)
        assertEquals("sunny", days[1].condition)
        assertNull(days[1].low)
        assertNull(days[1].rainChance)
    }

    @Test fun namesTodayTomorrowThenTheWeekday() {
        val now = ZonedDateTime.of(2026, 11, 17, 9, 0, 0, 0, zone).toInstant().toEpochMilli()
        fun at(d: Int) = ZonedDateTime.of(2026, 11, d, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals("Today", forecastDayName(at(17), now, zone))
        assertEquals("Tomorrow", forecastDayName(at(18), now, zone))
        assertEquals("Thursday", forecastDayName(at(19), now, zone))
    }

    @Test fun readsTheDailyExtrasAndTheHours() {
        val day = parseDailyForecast(JSONObject("""{"forecast":[{"datetime":"2026-11-20T12:00:00+00:00","condition":"rainy","temperature":7,"precipitation":2.5,"wind_bearing":45,"humidity":88,"uv_index":1}]}""")).single()
        assertEquals(2.5, day.rain!!, 0.0)
        assertEquals(45.0, day.bearing!!, 0.0)
        assertEquals(88.0, day.humidity!!, 0.0)
        val hours = parseHourlyForecast(JSONObject("""{"forecast":[{"datetime":"2026-11-20T07:00:00+00:00","condition":"cloudy","temperature":6.2,"precipitation":0,"wind_speed":14,"wind_bearing":12}]}"""))
        assertEquals(6.2, hours.single().temperature!!, 0.0)
    }

    @Test fun groupsADaysHoursIntoParts() {
        fun h(day: Int, hour: Int, t: Double, cond: String, rain: Double = 0.0, wind: Double = 10.0) =
            ForecastHour(ZonedDateTime.of(2026, 11, day, hour, 0, 0, 0, zone).toInstant().toEpochMilli(), t, cond, rain, wind, 90.0)
        val hours = listOf(
            h(20, 7, 5.0, "cloudy"), h(20, 9, 6.0, "cloudy"), h(20, 11, 7.0, "sunny"),
            h(20, 13, 8.0, "rainy", rain = 0.5, wind = 20.0), h(20, 15, 7.0, "rainy", rain = 1.0),
            h(20, 22, 3.0, "clear-night"), h(21, 2, 1.0, "clear-night"),
            h(21, 9, 4.0, "sunny"),
        )
        val parts = dayParts(hours, ZonedDateTime.of(2026, 11, 20, 12, 0, 0, 0, zone).toInstant().toEpochMilli(), zone)
        assertEquals(listOf("Morning", "Afternoon", "Evening", "Night"), parts.map { it.name })
        assertEquals("cloudy", parts[0].condition)
        assertEquals(5.0, parts[0].low!!, 0.0)
        assertEquals(7.0, parts[0].high!!, 0.0)
        assertEquals(1.5, parts[1].rain, 0.0001)
        assertEquals(20.0, parts[1].wind!!, 0.0)
        assertEquals(1.0, parts[3].low!!, 0.0)
    }

    @Test fun aPartWithNoHoursIsLeftOut() {
        val evening = ForecastHour(ZonedDateTime.of(2026, 11, 20, 20, 0, 0, 0, zone).toInstant().toEpochMilli(), 4.0, "rainy", 0.2, 15.0, 0.0)
        assertEquals(listOf("Evening"), dayParts(listOf(evening), evening.timeMs, zone).map { it.name })
    }

    @Test fun windDirectionsAsCompassPoints() {
        assertEquals(listOf("N", "NE", "E", "S", "W", "NW", "N"), listOf(0.0, 45.0, 100.0, 180.0, 260.0, 320.0, 359.0).map { compassPoint(it) })
    }

    @Test fun wordsForUvRainAndWind() {
        assertEquals(listOf("Low", "Moderate", "High", "Very high"), listOf(1.0, 4.0, 7.0, 9.0).map { uvWord(it) })
        assertEquals(listOf("Dry", "Light rain", "Some rain", "Heavy rain"), listOf(null, 0.5, 5.0, 12.0).map { rainWord(it) })
    }

    @Test fun aDayBeyondTheHoursIsDescribedInASentence() {
        val day = ForecastDay(0L, 10.0, 4.0, "partlycloudy", null, 12.0, rain = 0.0, bearing = 90.0)
        val text = dayDescription(day, "km/h")
        assert(text.startsWith("Partlycloudy. Dry.")) { text }
        assert(text.endsWith("from the E.")) { text }
        assert(windWord(12.0).isNotBlank())
    }
}
