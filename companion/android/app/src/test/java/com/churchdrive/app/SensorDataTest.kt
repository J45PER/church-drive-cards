package com.churchdrive.app

import com.churchdrive.app.ui.SENSOR_DELAYS
import com.churchdrive.app.ui.SensorSlot
import com.churchdrive.app.ui.addSlot
import com.churchdrive.app.ui.moveSlotBoundary
import com.churchdrive.app.ui.removeSlot
import com.churchdrive.app.ui.slotsRoundTheClock
import com.churchdrive.app.ui.newSensorSlot
import com.churchdrive.app.ui.slotLookText
import com.churchdrive.app.ui.SensorAction
import com.churchdrive.app.ui.delayText
import com.churchdrive.app.ui.parseSensorActions
import com.churchdrive.app.ui.sensorBadge
import com.churchdrive.app.ui.sensorBody
import com.churchdrive.app.ui.sensorProblem
import com.churchdrive.app.ui.sensorSubtitle
import com.churchdrive.app.ui.stepDelay
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SensorDataTest {
    private val answer = JSONObject(
        """{"actions":[{"id":"a1","sensor":"binary_sensor.cloakroom_presence","name":"Cloakroom","lights":["light.cloakroom"],"on_action":"on","scene":"",
          "off_action":"off","off_delay":60,"dim_pct":20,"only_dark":false,"dark_lux":30,"lux_sensor":"sensor.fp300_lux","window":null,"enabled":true,
          "summary":"Light follows presence · off after 1 min"}],
         "sensors":[
          {"entity_id":"binary_sensor.cloakroom_presence","name":"Cloakroom Presence Sensor","model":"Presence Multi-Sensor FP300","platform":"matter","area":"cloakroom","state":"off",
           "readings":{"battery":{"entity_id":"sensor.fp300_battery","state":"100"},"illuminance":{"entity_id":"sensor.fp300_lux","state":"353"}},
           "settings":[{"entity_id":"number.fp300_hold_time","kind":"number","name":"Hold time","state":"10","min":1,"max":300,"step":1,"unit":"s"},
                       {"entity_id":"select.fp300_sensitivity","kind":"select","name":"Sensitivity","state":"medium","options":["low","medium","high"]}],
           "action":"a1"},
          {"entity_id":"binary_sensor.lower_landing_sensor_motion","name":"Lower Landing Sensor","model":"Hue motion sensor","platform":"hue","state":"off","readings":{},"settings":[],"action":null}]}"""
    )

    @Test fun theHousesAnswerIsRead() {
        val data = parseSensorActions(answer)!!
        assertEquals(1, data.actions.size)
        assertEquals(2, data.sensors.size)
        val cloak = data.sensors[0]
        assertEquals("sensor.fp300_battery", cloak.batteryEntity)
        assertEquals("sensor.fp300_lux", cloak.luxEntity)
        assertEquals(listOf("number", "select"), cloak.settings.map { it.kind })
        assertEquals(listOf("low", "medium", "high"), cloak.settings[1].options)
        assertEquals(300.0, cloak.settings[0].max!!, 0.0)
        assertNotNull(data.actionFor(cloak))
        assertNull(data.actionFor(data.sensors[1]))
        assertTrue(data.sensors[1].isHue)
    }

    @Test fun nothingIsReadFromAnAnswerThatIsntOne() {
        assertNull(parseSensorActions(null))
        assertNull(parseSensorActions("no"))
    }

    @Test fun theBadgeSaysWhoRunsIt() {
        val data = parseSensorActions(answer)!!
        val action: SensorAction = data.actions[0]
        assertEquals("Church Drive", sensorBadge(data.sensors[0], action))
        assertEquals("Hue app", sensorBadge(data.sensors[1], null))
        assertEquals("Off", sensorBadge(data.sensors[0], action.copy(enabled = false)))
        assertEquals("Set in the Hue app", sensorSubtitle(data.sensors[1], null))
        assertEquals("Light follows presence · off after 1 min", sensorSubtitle(data.sensors[0], action))
    }

    @Test fun theDelayStepsThroughSensibleValues() {
        assertEquals(90, stepDelay(60, 1))
        assertEquals(45, stepDelay(60, -1))
        assertEquals(10, stepDelay(10, -1))
        assertEquals(3600, stepDelay(3600, 1))
        assertEquals(60, stepDelay(50, 0))
        assertTrue(SENSOR_DELAYS == SENSOR_DELAYS.sorted())
        assertEquals("45 s", delayText(45))
        assertEquals("1 min", delayText(60))
        assertEquals("1 min 30 s", delayText(90))
    }

    @Test fun aHueSensorCantBeSavedUntilItsOwnAutomationIsOff() {
        assertEquals("Choose a light first.", sensorProblem(emptyList(), "", "on", false, false))
        assertEquals("Choose which scene to play.", sensorProblem(listOf("light.a"), "", "scene", false, false))
        assertNotNull(sensorProblem(listOf("light.a"), "", "on", needsHueOff = true, hueOff = false))
        assertNull(sensorProblem(listOf("light.a"), "", "on", needsHueOff = true, hueOff = true))
        assertNull(sensorProblem(listOf("light.a"), "", "on", needsHueOff = false, hueOff = false))
    }

    @Test fun theBodyNamesTheSensorOnlyForANewOne() {
        val made = sensorBody(null, "binary_sensor.x", "X", listOf("light.a"), "on", "", "off", 60, 20, true, 30, "", "22:00", "06:00", true)
        assertEquals("binary_sensor.x", made.getString("sensor"))
        assertTrue(!made.has("action_id"))
        assertEquals("22:00", made.getJSONObject("window").getString("start"))
        val changed = sensorBody("a1", "binary_sensor.x", "X", listOf("light.a"), "on", "", "off", 60, 20, false, 30, "", null, null, true)
        assertEquals("a1", changed.getString("action_id"))
        assertTrue(!changed.has("sensor"))
        assertTrue(changed.isNull("window"))
    }

    @Test fun timeSlotsAreReadAndSent() {
        val o = JSONObject(
            """{"actions":[{"id":"a1","sensor":"binary_sensor.x","name":"X","lights":["light.a"],"on_action":"on","pct":40,"slots":[
              {"start":"18:00","end":"23:00","on_action":"scene","scene":"relax","pct":0},{"start":"23:00","end":"07:00","on_action":"on","pct":10}]}],"sensors":[]}"""
        )
        val action = parseSensorActions(o)!!.actions.single()
        assertEquals(40, action.pct)
        assertEquals(listOf("18:00", "23:00"), action.slots.map { it.start })
        assertEquals("relax", action.slots[0].scene)
        assertEquals(10, action.slots[1].pct)
        val body = sensorBody("a1", "binary_sensor.x", "X", listOf("light.a"), "on", "", "off", 60, 20, false, 30, "", null, null, true, 40, action.slots)
        assertEquals(40, body.getInt("pct"))
        assertEquals(2, body.getJSONArray("slots").length())
        assertEquals("relax", body.getJSONArray("slots").getJSONObject(0).getString("scene"))
    }

    @Test fun aSlotIsDescribedByWhatItDoes() {
        val name = { k: String -> k.replaceFirstChar { it.uppercase() } }
        assertEquals("Scene · Relax", slotLookText(SensorSlot("18:00", "23:00", "scene", "relax", 0), name))
        assertEquals("Turn on · 10%", slotLookText(SensorSlot("23:00", "07:00", "on", "", 10), name))
        assertEquals("Turn on", slotLookText(SensorSlot("07:00", "18:00", "on", "", 0), name))
        assertEquals("Nothing", slotLookText(SensorSlot("09:00", "17:00", "none", "", 0), name))
        assertEquals("scene", newSensorSlot().onAction)
    }

    @Test fun aThemeSlotAndTheOffIfOnSettingAreKept() {
        val name = { k: String -> "Late glow" }
        assertEquals("Theme · Late glow", slotLookText(SensorSlot("23:00", "02:00", "hybrid", "h1", 0), name))
        val body = sensorBody(
            "a1", "binary_sensor.x", "X", listOf("light.a"), "on", "", "off", 20, 20, false, 30, "", null, null, true, 0,
            listOf(SensorSlot("23:00", "02:00", "hybrid", "h1", 0)), true,
        )
        assertEquals(true, body.getBoolean("off_if_on"))
        assertEquals("hybrid", body.getJSONArray("slots").getJSONObject(0).getString("on_action"))
        assertNotNull(sensorProblem(listOf("light.a"), "", "hybrid", false, false))
        assertNull(sensorProblem(listOf("light.a"), "h1", "hybrid", false, false))
        val parsed = parseSensorActions(JSONObject("""{"actions":[{"id":"a","sensor":"binary_sensor.x","off_if_on":true}],"sensors":[]}"""))!!
        assertTrue(parsed.actions.single().offIfOn)
        assertTrue(parsed.actions.single().dimWarning)
        assertEquals(true, body.getBoolean("dim_warning"))
        assertEquals(false, parseSensorActions(JSONObject("""{"actions":[{"id":"a","sensor":"binary_sensor.x","dim_warning":false}],"sensors":[]}"""))!!.actions.single().dimWarning)
    }

    @Test fun sensorsInAGroupShareOneCardAndOneAction() {
        val o = JSONObject(
            """{"actions":[{"id":"g1","sensor":"binary_sensor.lower","sensors":["binary_sensor.lower","binary_sensor.upper"],"lux_sensors":["sensor.l","sensor.u"],"name":"Landings"}],
              "sensors":[{"entity_id":"binary_sensor.lower","name":"Lower Landing","platform":"hue"},{"entity_id":"binary_sensor.upper","name":"Upper Landing","platform":"hue"},
                         {"entity_id":"binary_sensor.other","name":"Other","platform":"hue"}]}""",
        )
        val data = parseSensorActions(o)!!
        val action = data.actions.single()
        assertEquals(listOf("sensor.l", "sensor.u"), action.luxSensors)
        assertEquals(action, data.actionFor(data.sensors[1]))
        assertNull(data.actionFor(data.sensors[2]))
        assertEquals(listOf("Lower Landing", "Upper Landing"), data.groupOf(action).map { it.name })
        assertEquals(listOf("Lower Landing", "Other"), data.cards().map { it.name })
        val body = sensorBody("g1", "binary_sensor.lower", "Landings", listOf("light.a"), "on", "", "off", 20, 20, true, 30, "sensor.l", null, null, true, 0, emptyList(), false, action.sensors, action.luxSensors)
        assertEquals(2, body.getJSONArray("sensors").length())
        assertEquals(2, body.getJSONArray("lux_sensors").length())
    }

    @Test fun slotsAlwaysGoRoundTheClock() {
        val day = SensorSlot("06:00", "18:00", "on", "", 0)
        val night = SensorSlot("18:00", "06:00", "scene", "relax", 0)
        val three = addSlot(listOf(day, night))
        assertEquals(3, three.size)
        assertTrue(slotsRoundTheClock(three))
        assertEquals(three[0].end, three[1].start)
        assertEquals(three.last().end, three[0].start)
        // Changing when one ends changes when the next begins.
        val moved = moveSlotBoundary(listOf(day, night), 1, "20:00")!!
        assertEquals("20:00", moved[0].end)
        assertEquals("20:00", moved[1].start)
        assertEquals("06:00", moved[1].end)
        // The first slot's start is the last one's end.
        val wrapped = moveSlotBoundary(listOf(day, night), 0, "07:00")!!
        assertEquals("07:00", wrapped[1].end)
        assertEquals("07:00", wrapped[0].start)
        // Past another slot's time is refused.
        assertNull(moveSlotBoundary(three, 1, "05:00"))
        // Removing one carries the one before on; fewer than two slots is none.
        val removed = removeSlot(three, 1)
        assertEquals(2, removed.size)
        assertTrue(slotsRoundTheClock(removed))
        assertEquals(emptyList<SensorSlot>(), removeSlot(listOf(day, night), 0))
        assertEquals(2, addSlot(emptyList()).size)
    }
}
