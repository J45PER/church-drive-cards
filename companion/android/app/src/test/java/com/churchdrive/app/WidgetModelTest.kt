package com.churchdrive.app

import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ui.LightRoom
import com.churchdrive.app.ui.QuickSetting
import com.churchdrive.app.widget.WidgetModel
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetModelTest {
    private fun e(id: String, state: String, vararg a: Pair<String, Any>) =
        EntityState(id, state, JSONObject().apply { a.forEach { put(it.first, it.second) } })

    @Test
    fun alarmButtonsFollowTheSupportedModes() {
        val all = WidgetModel.alarmTiles(e("alarm_control_panel.a", "armed_home", "supported_features" to 7), "alarm_control_panel.a")
        assertEquals(listOf("Disarm", "Home", "Away", "Night"), all.map { it.label })
        assertEquals(listOf(false, true, false, false), all.map { it.selected })
        val homeAway = WidgetModel.alarmTiles(e("alarm_control_panel.a", "disarmed", "supported_features" to 3), "alarm_control_panel.a")
        assertEquals(listOf("Disarm", "Home", "Away"), homeAway.map { it.label })
        assertEquals("alarm_arm_away", homeAway.last().service)
    }

    @Test
    fun roomTilesNameAndLightEachRoom() {
        val rooms = listOf(
            LightRoom("Kitchen", null, "light.kitchen", emptyList(), emptyList()),
            LightRoom(null, "hall", "light.hall", emptyList(), emptyList()),
            LightRoom("Gone", null, "light.gone", emptyList(), emptyList()),
        )
        val states = listOf(e("light.kitchen", "on"), e("light.hall", "off"), e("light.gone", "unavailable")).associateBy { it.entityId }
        val tiles = WidgetModel.roomTiles(rooms, mapOf("hall" to "Hall"), states)
        assertEquals(listOf("Kitchen", "Hall"), tiles.map { it.label })
        assertEquals(listOf(true, false), tiles.map { it.selected })
        assertEquals("toggle", tiles.first().service)
    }

    @Test
    fun climateButtonsMarkTheCurrentSetting() {
        val quick = listOf(QuickSetting("Off", "off", null), QuickSetting("Heat", "heat", null), QuickSetting("Eco", null, "eco"))
        val eco = WidgetModel.climateTiles(e("climate.c", "heat", "preset_mode" to "eco"), "climate.c", quick)
        assertEquals(listOf(false, false, true), eco.map { it.selected })
        assertEquals("set_preset_mode", eco.last().service)
        val heat = WidgetModel.climateTiles(e("climate.c", "heat", "preset_mode" to "none"), "climate.c", quick)
        assertEquals(listOf(false, true, false), heat.map { it.selected })
    }

    @Test
    fun targetStepsStayInsideTheLimits() {
        val c = e("climate.c", "heat", "temperature" to 21.0, "target_temp_step" to 0.5, "min_temp" to 7.0, "max_temp" to 21.0)
        assertEquals(20.5, WidgetModel.nextTarget(c, -1)!!, 0.0)
        assertEquals(21.0, WidgetModel.nextTarget(c, 1)!!, 0.0)
        assertNull(WidgetModel.nextTarget(null, 1))
    }
}

class WidgetModelMoreTest {
    private fun e(id: String, state: String, vararg a: Pair<String, Any>) =
        EntityState(id, state, JSONObject().apply { a.forEach { put(it.first, it.second) } })

    @Test
    fun sizesPickTheLayout() {
        assertEquals(com.churchdrive.app.widget.SizeClass.Square, com.churchdrive.app.widget.sizeClass(110f, 110f))
        assertEquals(com.churchdrive.app.widget.SizeClass.Strip, com.churchdrive.app.widget.sizeClass(250f, 70f))
        assertEquals(com.churchdrive.app.widget.SizeClass.Wide, com.churchdrive.app.widget.sizeClass(250f, 140f))
        assertEquals(com.churchdrive.app.widget.SizeClass.Tall, com.churchdrive.app.widget.sizeClass(250f, 230f))
    }

    @Test
    fun statsAreWorkedOutFromTheHouse() {
        val states = listOf(
            e("climate.downstairs", "heat", "current_temperature" to 20.5, "current_humidity" to 48),
            e("sensor.home_climate_quality", "88"),
            e("light.a", "on"), e("light.b", "off"),
            e("person.i", "home"), e("person.d", "not_home"),
            e("binary_sensor.front_door", "on", "device_class" to "door"),
        ).associateBy { it.entityId }
        assertEquals("20.5°", com.churchdrive.app.widget.Stats.compute("inside", states)!!.value)
        assertEquals("48%", com.churchdrive.app.widget.Stats.compute("humidity", states)!!.value)
        assertEquals("88", com.churchdrive.app.widget.Stats.compute("air", states)!!.value)
        assertEquals("1 on", com.churchdrive.app.widget.Stats.compute("lights", states)!!.value)
        assertEquals("1 in", com.churchdrive.app.widget.Stats.compute("people", states)!!.value)
        assertEquals("1 open", com.churchdrive.app.widget.Stats.compute("doors", states)!!.value)
        assertNull(com.churchdrive.app.widget.Stats.compute("nonsense", states))
        assertEquals(4, com.churchdrive.app.widget.Stats.chosen(emptyList(), states).size)
        assertEquals(2, com.churchdrive.app.widget.Stats.chosen(listOf("inside", "air"), states).size)
    }

    @Test
    fun shortcutsComeFromTheCatalogue() {
        val states = listOf(e("scene.movie", "scening", "friendly_name" to "Movie night")).associateBy { it.entityId }
        val layout = com.churchdrive.app.ui.LightLayout.Fallback
        val catalogue = com.churchdrive.app.widget.Shortcuts.catalogue(states, layout)
        assertEquals(true, catalogue.any { it.id == "scene:scene.movie" && it.label == "Movie night" })
        assertEquals(true, catalogue.any { it.id == "alarm:home" })
        val chosen = com.churchdrive.app.widget.Shortcuts.chosen(listOf("alarm:away", "gone:missing", "lights:off"), states, layout)
        assertEquals(listOf("Arm away", "All off"), chosen.map { it.label })
        assertEquals(4, com.churchdrive.app.widget.Shortcuts.chosen(emptyList(), states, layout).size)
    }

    @Test
    fun roomsAreTheChosenOnesOrTheHomePanels() {
        val layout = com.churchdrive.app.ui.LightLayout.Fallback
        assertEquals(layout.home.map { it.head }, com.churchdrive.app.widget.chosenRooms(layout, emptyList()).map { it.head })
        val one = com.churchdrive.app.widget.chosenRooms(layout, listOf("light.kitchen", "light.nowhere"))
        assertEquals(listOf("light.kitchen"), one.map { it.head })
    }

    @Test
    fun ecoWhileOffCarriesTheHeatStepFirst() {
        val climate = e("climate.c", "off", "hvac_modes" to JSONArray(listOf("off", "heat")), "preset_modes" to JSONArray(listOf("none", "eco")))
        val quick = listOf(QuickSetting("Eco", null, "eco"))
        val tile = WidgetModel.climateTiles(climate, "climate.c", quick).single()
        assertEquals("set_hvac_mode", tile.service)
        assertEquals(true, tile.data.contains("__then") && tile.data.contains("eco"))
    }
}
