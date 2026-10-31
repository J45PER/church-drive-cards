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

    @Test fun readsStatusVolumeButtonsAndTheUpdate() {
        val s = findSatellites(house(
            state("number.clive_round_screen_volume", "65"),
            state("sensor.clive_round_screen_uptime", "93784.0"),
            state("sensor.clive_round_screen_ip_address", "192.168.1.50"),
            state("sensor.clive_round_screen_wi_fi_network", "Church"),
            state("sensor.clive_round_screen_esphome_version", "2026.10.1"),
            state("button.clive_round_screen_restart", "unknown"),
            state("button.clive_round_screen_identify", "unknown"),
            state("assist_satellite.clive_round_screen", "idle"),
            state("update.clive_round_screen_firmware", "on", "installed_version" to "2026.10.1", "latest_version" to "2026.11.0", "in_progress" to false),
        )).single()
        assertEquals(65f, s.volume!!, 0f)
        assertEquals(93784L, s.uptimeSeconds)
        assertEquals("192.168.1.50", s.ip)
        assertEquals("Church", s.network)
        assertEquals("2026.10.1", s.firmware)
        assertEquals(setOf("restart", "identify"), s.buttons)
        assertTrue(s.canSpeak)
        val u = s.update!!
        assertEquals("update.clive_round_screen_firmware", u.entity)
        assertEquals("2026.11.0", u.latest)
        assertTrue(u.available)
    }

    @Test fun olderFirmwareHasNoExtras() {
        val s = findSatellites(house()).single()
        assertNull(s.volume)
        assertNull(s.update)
        assertTrue(s.buttons.isEmpty())
        assertEquals(false, s.canSpeak)
    }

    @Test fun formatsUptimeAndSignal() {
        assertEquals("1 d 2 h", formatUptime(93784))
        assertEquals("3 h 5 min", formatUptime(11_100))
        assertEquals("8 min", formatUptime(500))
        assertEquals("Excellent", signalWords(-48))
        assertEquals("Good", signalWords(-60))
        assertEquals("Fair", signalWords(-70))
        assertEquals("Weak", signalWords(-85))
    }
}
