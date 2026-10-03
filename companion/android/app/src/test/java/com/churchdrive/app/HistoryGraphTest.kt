package com.churchdrive.app

import com.churchdrive.app.ui.Reading
import com.churchdrive.app.ui.ZoneKind
import com.churchdrive.app.ui.ZoneTrack
import com.churchdrive.app.ui.climateSeries
import com.churchdrive.app.ui.happenedAt
import com.churchdrive.app.ui.numericSeries
import com.churchdrive.app.ui.valueAt
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HistoryGraphTest {
    @Test
    fun numericSeriesSkipsUnavailableAndSorts() {
        val s = numericSeries(listOf(30L to "20.5", 10L to "unavailable", 20L to "19"))
        assertEquals(listOf(Reading(20, 19.0), Reading(30, 20.5)), s)
    }

    @Test
    fun valueAtHoldsTheLastReading() {
        val s = listOf(Reading(10, 1.0), Reading(20, 2.0))
        assertNull(valueAt(s, 5))
        assertEquals(1.0, valueAt(s, 15)!!, 0.0)
        assertEquals(2.0, valueAt(s, 99)!!, 0.0)
    }

    @Test
    fun scrubbingNamesWhatHappened() {
        val tracks = listOf(
            ZoneTrack(ZoneKind.Motion, listOf(1000L to 2000L)),
            ZoneTrack(ZoneKind.Light, listOf(0L to 5000L)),
        )
        assertEquals("Motion · Light on", happenedAt(tracks, 1500, 100))
        assertEquals("Light on", happenedAt(tracks, 3000, 100))
        assertEquals("Quiet", happenedAt(tracks, 9000, 100))
    }

    @Test
    fun climateSeriesCarriesAttributesForward() {
        val result = JSONObject(
            """{"climate.hall":[{"s":"heat","a":{"current_temperature":19.5,"current_humidity":48},"lu":100.0},
                {"s":"heat","lu":200.0},{"s":"heat","a":{"current_temperature":20.0},"lu":300.0}]}""",
        )
        val (t, h) = climateSeries(result, "climate.hall", null)
        assertEquals(listOf(19.5, 19.5, 20.0), t.map { it.value })
        assertEquals(2, h.size)
    }
}
