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

    @Test fun aSatelliteWithoutAScreenIsFoundAsAnAssistSatellite() {
        val found = findSatellites(house(
            state("assist_satellite.kitchen_box", "idle", "friendly_name" to "Kitchen box"),
            state("switch.kitchen_box_follow_up_listening", "on"),
            state("number.kitchen_box_volume", "50"),
            // An Assist satellite that is not ours (no follow-up switch) is not listed.
            state("assist_satellite.phone", "idle", "friendly_name" to "Phone"),
        ))
        assertEquals(listOf("clive_round_screen", "kitchen_box"), found.map { it.slug })
        val box = found.last()
        assertEquals("Kitchen box", box.name)
        assertEquals(false, box.hasScreen)
        assertNull(box.design)
        assertEquals(50f, box.volume!!, 0f)
        assertTrue(box.online)
    }

    @Test fun readsTheVoiceSettings() {
        val s = findSatellites(house(
            state("select.clive_round_screen_assistant", "Clive", "options" to JSONArray(listOf("Preferred", "Clive", "Plain"))),
            state("select.clive_round_screen_finished_speaking_detection", "default", "options" to JSONArray(listOf("aggressive", "default", "relaxed"))),
        )).single()
        assertEquals(listOf("Assistant", "Finished speaking"), s.voice.map { it.label })
        assertEquals(listOf("Preferred", "Clive", "Plain"), s.voice[0].options)
    }

    @Test fun warnsAboutAWeakSignalAndLongOffline() {
        val weak = findSatellites(house(state("sensor.clive_round_screen_wi_fi_signal", "-80"))).single()
        assertEquals("Weak Wi-Fi", weak.warning())
        assertNull(findSatellites(house()).single().warning())
        val offline = findSatellites(house(
            EntityState("select.clive_round_screen_design", "unavailable", JSONObject().put("options", designOptions).put("friendly_name", "Clive (round screen) Design"), "2026-10-31T10:00:00+00:00"),
        )).single()
        val now = java.time.OffsetDateTime.parse("2026-10-31T10:30:00+00:00").toInstant().toEpochMilli()
        assertEquals("Offline for 30 min", offline.warning(now))
        assertEquals("Offline", offline.warning(java.time.OffsetDateTime.parse("2026-10-31T10:05:00+00:00").toInstant().toEpochMilli()))
    }

    @Test fun settingsSurviveJson() {
        val a = SatSettings(SatDesign.Ring, listOf("Alarm", "Heating", "Forecast"), 80f, 10f, 21, 7, 40f, true, false)
        assertEquals(a, SatSettings.fromJson(a.toJson()))
        val b = SatSettings(volume = 30f)
        assertEquals(b, SatSettings.fromJson(b.toJson()))
        assertNull(SatSettings.fromJson(b.toJson()).design)
    }

    @Test fun aSatelliteMakesASettingsSnapshot() {
        val s = findSatellites(house(state("switch.clive_round_screen_spoken_answers", "on"))).single()
        val snap = SatSettings.of(s)
        assertEquals(SatDesign.Weather, snap.design)
        assertEquals(listOf("Who's home", "Electricity", "Forecast"), snap.glances)
        assertEquals(true, snap.spokenAnswers)
        assertEquals(22, snap.nightStart)
    }
}
