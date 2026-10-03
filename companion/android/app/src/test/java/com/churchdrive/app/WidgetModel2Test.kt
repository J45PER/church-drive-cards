package com.churchdrive.app

import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ui.Tone
import com.churchdrive.app.ui.TodoItem
import com.churchdrive.app.widget.Gauges
import com.churchdrive.app.widget.WidgetPages
import com.churchdrive.app.widget.alarmInDelay
import com.churchdrive.app.widget.alarmStatus
import com.churchdrive.app.widget.fansFor
import com.churchdrive.app.widget.coverCard
import com.churchdrive.app.widget.doorsStatus
import com.churchdrive.app.widget.fanCard
import com.churchdrive.app.widget.lastActivity
import com.churchdrive.app.widget.peopleOf
import com.churchdrive.app.widget.todoRows
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class WidgetModel2Test {
    private fun e(id: String, state: String, vararg a: Pair<String, Any>) =
        EntityState(id, state, JSONObject().apply { a.forEach { put(it.first, it.second) } })

    private fun map(vararg es: EntityState) = es.associateBy { it.entityId }

    @Test
    fun peopleSayWhereEachIs() {
        val people = peopleOf(map(
            e("person.ann", "home", "friendly_name" to "Ann Smith"),
            e("person.bo", "not_home", "friendly_name" to "Bo"),
            e("person.cy", "work", "friendly_name" to "Cy"),
        ))
        assertEquals(listOf("Ann", "Bo", "Cy"), people.map { it.name })
        assertEquals(listOf("Home", "Out", "Work"), people.map { it.where })
        assertEquals(listOf(true, false, false), people.map { it.home })
        assertEquals("A", people.first().initial)
    }

    @Test
    fun doorsReportOpenAndTamper() {
        val closed = doorsStatus(map(e("binary_sensor.front", "off", "device_class" to "door")))
        assertEquals("All closed", closed.line)
        assertEquals(Tone.Green, closed.tone)
        val open = doorsStatus(map(e("binary_sensor.front", "on", "device_class" to "door", "friendly_name" to "Front door")))
        assertEquals("Front door open", open.line)
        val two = doorsStatus(map(
            e("binary_sensor.a", "on", "device_class" to "door"),
            e("binary_sensor.b", "on", "device_class" to "window"),
        ))
        assertEquals("2 open", two.line)
        val tamper = doorsStatus(map(e("binary_sensor.t", "on", "device_class" to "tamper")))
        assertEquals(Tone.Red, tamper.tone)
    }

    @Test
    fun lastActivityPicksTheLaterOfDoorbellAndMovement() {
        val now = java.time.Instant.parse("2026-10-03T18:00:00Z").toEpochMilli()
        val entities = map(
            e("event.front_door_ding", "2026-10-03T17:41:00+00:00"),
            e("event.front_door_motion", "2026-10-03T17:10:00+00:00"),
        )
        val (text, tone) = lastActivity("front_door", entities, now, ZoneOffset.UTC)
        assertEquals("Doorbell · 17:41", text)
        assertEquals(Tone.Green, tone)
        assertEquals("Nothing yet", lastActivity("back", entities, now, ZoneOffset.UTC).first)
    }

    @Test
    fun todoRowsListOpenTasksSoonestFirst() {
        val rows = todoRows(listOf(
            TodoItem("1", "Later", false, "2026-12-01", null),
            TodoItem("2", "Done", true, null, null),
            TodoItem("3", "No date", false, null, null),
            TodoItem("4", "Soon", false, "2026-10-04", null),
        ), 5)
        assertEquals(listOf("Soon", "Later", "No date"), rows.map { it.summary })
    }

    @Test
    fun gaugesReadTheHouse() {
        val entities = map(
            e("climate.downstairs", "heat", "current_temperature" to 19.0, "temperature" to 21.0, "min_temp" to 7.0, "max_temp" to 25.0, "current_humidity" to 50),
            e("sensor.home_climate_quality", "90"),
            e("sensor.phone_battery", "15", "device_class" to "battery", "unit_of_measurement" to "%", "friendly_name" to "Phone"),
        )
        val heating = Gauges.compute("heating", entities)!!
        assertEquals("19.0°", heating.value)
        assertTrue(heating.marker!! > heating.fraction)
        assertEquals("target 21.0°", heating.sub)
        assertEquals("50%", Gauges.compute("humidity", entities)!!.value)
        assertEquals("90", Gauges.compute("air", entities)!!.value)
        val battery = Gauges.compute("entity:sensor.phone_battery", entities)!!
        assertEquals(Tone.Red, battery.tone)
        assertEquals("15%", battery.value)
        assertNull(Gauges.compute("entity:sensor.missing", entities))
        assertEquals(3, Gauges.cluster(emptyList(), entities).size)
    }

    @Test
    fun coverAndFanCardsOfferTheirControls() {
        val cover = coverCard(e("cover.lounge", "open", "current_position" to 80, "friendly_name" to "Lounge"), "cover.lounge")
        assertEquals(listOf("Open", "Stop", "Close"), cover.tiles.map { it.label })
        assertEquals("Open · 80%", cover.sub)
        val fan = fanCard(e("fan.f", "on", "percentage" to 50, "percentage_step" to 25.0, "oscillating" to false), "fan.f")
        assertEquals("Off", fan.tiles.first().label)
        assertTrue(fan.tiles.any { it.label == "Swing" })
    }

    @Test
    fun alarmCountdownShowsTheModeAndSeconds() {
        val arming = e("alarm_control_panel.a", "arming", "targetState" to "armed_home", "exitSecondsLeft" to 23)
        assertEquals("Arming Home · 23s", alarmStatus(arming))
        assertTrue(alarmInDelay(arming))
        assertEquals("Entry delay · 9s", alarmStatus(e("alarm_control_panel.a", "pending", "entrySecondsLeft" to 9)))
        assertEquals("Armed Home", alarmStatus(e("alarm_control_panel.a", "armed_home")))
        assertEquals(false, alarmInDelay(e("alarm_control_panel.a", "armed_home")))
    }

    @Test
    fun fansAndPurifiersAreOfferedApart() {
        val entities = map(
            e("fan.air_purifier", "on", "friendly_name" to "Air Purifier"),
            e("fan.desk", "off", "friendly_name" to "Desk fan"),
        )
        assertEquals(listOf("fan.desk"), fansFor(false, entities).map { it.entityId })
        assertEquals(listOf("fan.air_purifier"), fansFor(true, entities).map { it.entityId })
    }

    @Test
    fun aClusterKeepsAnUnavailableReadingInPlace() {
        val entities = map(e("climate.downstairs", "heat", "current_temperature" to 20.0, "current_humidity" to 50))
        val readings = Gauges.cluster(listOf("inside", "air", "humidity", "charger"), entities)
        assertEquals(listOf("20.0°", "–", "50%", "–"), readings.map { it.value })
    }

    @Test
    fun eachWidgetOpensItsPageOfTheApp() {
        assertEquals("Security", WidgetPages.of("com.churchdrive.app.widget.AlarmWidgetReceiver"))
        assertEquals("Todo", WidgetPages.of("com.churchdrive.app.widget.JobsWidgetReceiver"))
        assertEquals("Lighting", WidgetPages.of("com.churchdrive.app.widget.ScenesWidgetReceiver"))
        assertEquals("Climate", WidgetPages.of("com.churchdrive.app.widget.GaugeWidgetReceiver"))
        assertNull(WidgetPages.of(null))
    }
}
