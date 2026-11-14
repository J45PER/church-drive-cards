package com.churchdrive.admin

import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ha.Registry
import com.churchdrive.app.ui.DeviceHealthItem
import com.churchdrive.app.ui.HealthLevel
import com.churchdrive.app.ui.HomeItem
import com.churchdrive.app.ui.Rate
import com.churchdrive.app.ui.Tone
import com.churchdrive.app.ui.cheapWindow
import com.churchdrive.app.ui.cliveDevicesSummary
import com.churchdrive.app.ui.editsWaiting
import com.churchdrive.app.ui.healthSummary
import com.churchdrive.app.ui.securitySummary
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdminHomeDataTest {
    private fun state(id: String, state: String, vararg attrs: Pair<String, Any>) =
        EntityState(id, state, JSONObject().also { o -> attrs.forEach { (k, v) -> o.put(k, v) } })

    private fun map(vararg e: EntityState) = e.associateBy { it.entityId }

    private fun device(name: String, battery: Int? = null, filter: Int? = null, offline: Boolean = false) =
        DeviceHealthItem("d-$name", name, null, emptyList(), battery, false, filter, offline, null, null)

    private val hour = 3_600_000L

    @Test fun cheapWindowIsTheRunThatIsOnOrNext() {
        // Two days of half hours: cheap from midnight to 05:30 and from 23:30; dear in between.
        val day = 24 * hour
        val half = hour / 2
        val rates = (0 until 96).map { n ->
            val start = n * half
            val inDay = start % day
            Rate(start, start + half, if (inDay < 11 * half || inDay >= 47 * half) 7.9 else 36.8)
        }
        // At noon the next cheap run is tonight's, and it runs on into tomorrow morning.
        val w = cheapWindow(rates, 12 * hour)!!
        assertEquals(47 * half, w.first)
        assertEquals(day + 11 * half, w.second)
        // At 02:00 it is on now: its start is before now, at the start of the day's rates.
        val now = cheapWindow(rates, 2 * hour)!!
        assertEquals(0L, now.first)
        assertEquals(11 * half, now.second)
    }

    @Test fun healthCardTakesTheWorstDevicesColourAndListsProblemsFirst() {
        val fine = healthSummary(listOf(device("Filter", filter = 59), device("Doorbell", battery = 90)))
        assertEquals(Tone.Green, fine.tone)
        assertEquals("Nothing needs you", fine.line2)
        // What is next: the lowest first.
        assertEquals("Filter", (fine.items.first() as HomeItem.Row).name)

        val bad = healthSummary(listOf(device("Filter", filter = 59), device("Doorbell", battery = 5), device("Lamp", offline = true)))
        assertEquals(Tone.Red, bad.tone)
        assertEquals(HealthLevel.Bad, (bad.items.first() as HomeItem.Row).level)
        assertTrue(bad.line1.contains("2 of 3"))
    }

    @Test fun securityCardSaysWhatIsOpenAndLeavesCarsOut() {
        val entities = map(
            state("alarm_control_panel.church_drive_alarm", "disarmed"),
            state("binary_sensor.back_door", "on", "device_class" to "door", "friendly_name" to "Back Door"),
            state("binary_sensor.front_door", "off", "device_class" to "door", "friendly_name" to "Front Door"),
            state("binary_sensor.smoke", "off", "device_class" to "smoke", "friendly_name" to "Hall Smoke Alarm Alarm Status"),
        )
        val s = securitySummary(entities, Registry.Empty, 0L)
        assertEquals("Disarmed", s.line1)
        assertEquals("Back Door open", s.line2)
        assertEquals(Tone.AlarmOff, s.tone)
        assertTrue(s.items.any { it is HomeItem.Row && it.level == HealthLevel.Warn && it.name == "Back Door" })
    }

    @Test fun aSoundingSmokeAlarmMakesTheCardRed() {
        val entities = map(
            state("alarm_control_panel.church_drive_alarm", "disarmed"),
            state("binary_sensor.smoke", "on", "device_class" to "smoke", "friendly_name" to "Hall Smoke Alarm Alarm Status"),
        )
        assertEquals(Tone.Red, securitySummary(entities, Registry.Empty, 0L).tone)
    }

    @Test fun cliveDevicesCountsWhatIsNotAnswering() {
        val data = JSONObject()
            .put("phones", JSONArray().put(JSONObject().put("who", "Jamie").put("name", "Pixel").put("answering", true).put("version", "0.152.0")))
            .put("satellites", JSONArray().put(JSONObject().put("name", "Living room").put("answering", false).put("state", "Not answering")))
        val s = cliveDevicesSummary(data, 0L)
        assertEquals("1 of 2 not answering", s.line1)
        assertEquals(Tone.Amber, s.tone)
        assertEquals("Data not available", cliveDevicesSummary(null, 0L).line1)
    }

    @Test fun editsWaitingAreOneLineEach() {
        val pending = JSONArray().put(JSONObject().put("kind", "palette").put("by", "Jamie").put("status", "pending"))
            .put(JSONObject().put("kind", "icons").put("by", "Hayley").put("status", "applied"))
        val entities = map(state("sensor.church_drive_proposals", "1", "pending" to pending))
        assertEquals(listOf("Colours edit waiting · Jamie"), editsWaiting(entities))
    }
}
