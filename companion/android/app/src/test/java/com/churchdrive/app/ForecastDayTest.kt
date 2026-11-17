package com.churchdrive.app

import com.churchdrive.app.ui.forecastDayName
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
}
