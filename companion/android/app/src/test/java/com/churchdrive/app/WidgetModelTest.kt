package com.churchdrive.app

import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ui.LightRoom
import com.churchdrive.app.ui.QuickSetting
import com.churchdrive.app.widget.WidgetModel
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
