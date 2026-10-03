package com.churchdrive.app

import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.widget.WidgetColours
import com.churchdrive.app.widget.WidgetLooks
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetLooksTest {
    private fun e(id: String, state: String, vararg a: Pair<String, Any>) =
        EntityState(id, state, JSONObject().apply { a.forEach { put(it.first, it.second) } })

    private fun map(vararg s: EntityState) = s.associateBy { it.entityId }

    @Test
    fun alarmFollowsItsState() {
        assertEquals(WidgetColours.GREEN, WidgetLooks.alarm(map(e("alarm_control_panel.church_drive_alarm", "disarmed"))).colour)
        assertEquals(WidgetColours.RED, WidgetLooks.alarm(map(e("alarm_control_panel.church_drive_alarm", "armed_away"))).colour)
        assertEquals(WidgetColours.GREY, WidgetLooks.alarm(null).colour)
    }

    @Test
    fun lightsCountWhatIsOn() {
        val s = map(e("light.a", "on"), e("light.b", "off"), e("light.c", "unavailable"), e("switch.x", "on"))
        val look = WidgetLooks.lights(s)
        assertEquals("1 on", look.value)
        assertEquals("of 2 lights", look.sub)
        assertEquals("All off", WidgetLooks.lights(map(e("light.a", "off"))).value)
    }

    @Test
    fun climateShowsTheScoreAndTemperature() {
        val s = map(e("sensor.home_climate_quality", "88"), e("climate.downstairs", "heat", "current_temperature" to 20.5))
        val look = WidgetLooks.climate(s)
        assertEquals("88/100", look.value)
        assertEquals("Excellent · 20.5 °C", look.sub)
        assertEquals(WidgetColours.GREEN, look.colour)
        assertEquals("21.0 °C", WidgetLooks.climate(map(e("climate.downstairs", "heat", "current_temperature" to 21.0))).value)
    }
}
