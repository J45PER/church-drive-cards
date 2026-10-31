package com.churchdrive.satellites

import com.churchdrive.app.ha.EntityState
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SatelliteDataTest {
    private fun state(id: String, value: String, vararg attrs: Pair<String, Any>) =
        EntityState(id, value, JSONObject().apply { attrs.forEach { put(it.first, it.second) } })

    private val designOptions = JSONArray(listOf("Glance", "Clock", "Weather", "Ring"))

    private fun house(vararg extra: EntityState): Map<String, EntityState> =
        (listOf(
            state("select.clive_round_screen_design", "Weather", "options" to designOptions, "friendly_name" to "Clive (round screen) Design"),
            state("select.clive_round_screen_glance_1", "Who's home"),
            state("select.clive_round_screen_glance_2", "Electricity"),
            state("select.clive_round_screen_glance_3", "Forecast"),
            state("number.clive_round_screen_backlight_day", "90"),
            state("number.clive_round_screen_backlight_night", "12"),
            state("number.clive_round_screen_night_starts_hour", "22"),
            state("number.clive_round_screen_night_ends_hour", "6"),
            state("switch.clive_round_screen_microphone", "on"),
            state("switch.clive_round_screen_night_mode", "off"),
            state("sensor.clive_round_screen_wi_fi_signal", "-48"),
            // Not satellites: a select with other options, and one that is not a design.
            state("select.fan_design", "Glance", "options" to JSONArray(listOf("Glance", "Other"))),
            state("select.heating_mode", "Eco", "options" to JSONArray(listOf("Eco", "Comfort"))),
        ) + extra).associateBy { it.entityId }

    @Test fun findsASatelliteByItsDesignSetting() {
        val found = findSatellites(house())
        assertEquals(1, found.size)
        val s = found[0]
        assertEquals("clive_round_screen", s.slug)
        assertEquals("Clive (round screen)", s.name)
        assertEquals(SatDesign.Weather, s.design)
        assertEquals(listOf("Who's home", "Electricity", "Forecast"), s.glances)
        assertEquals(90f, s.backlightDay!!, 0f)
        assertEquals(12f, s.backlightNight!!, 0f)
        assertEquals(22, s.nightStart)
        assertEquals(6, s.nightEnd)
        assertEquals(true, s.microphone)
        assertEquals(false, s.nightMode)
        assertNull(s.spokenAnswers)
        assertEquals("-48", s.signal)
        assertTrue(s.online)
    }

    @Test fun anUnavailableDesignMeansOffline() {
        val offline = house(state("select.clive_round_screen_design", "unavailable", "options" to designOptions, "friendly_name" to "Clive (round screen) Design"))
        val s = findSatellites(offline).single()
        assertEquals(false, s.online)
        assertNull(s.design)
    }

    @Test fun entityIdsAreBuiltFromTheSlug() {
        val s = findSatellites(house()).single()
        assertEquals("select.clive_round_screen_glance_2", s.select("glance_2"))
        assertEquals("number.clive_round_screen_backlight_day", s.number("backlight_day"))
        assertEquals("switch.clive_round_screen_microphone", s.switch("microphone"))
    }

    @Test fun noSatellitesWhenNoneHaveTheFourDesigns() {
        assertEquals(0, findSatellites(house().filterKeys { it != "select.clive_round_screen_design" }).size)
    }
}
