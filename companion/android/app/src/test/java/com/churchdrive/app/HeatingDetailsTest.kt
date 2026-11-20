package com.churchdrive.app

import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ui.heatingStatusWord
import com.churchdrive.app.ui.heatingThermostats
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class HeatingDetailsTest {
    @Test fun statusWords() {
        assertEquals("Heating", heatingStatusWord("heat", "heating", "none"))
        assertEquals("Idle", heatingStatusWord("heat", "idle", "none"))
        assertEquals("Eco", heatingStatusWord("heat", "idle", "eco"))
        assertEquals("Off", heatingStatusWord("off", "off", "none"))
        assertEquals("Heat_cool", heatingStatusWord("heat_cool", "", ""))
        assertEquals("Unknown", heatingStatusWord("", "", ""))
    }

    @Test fun onlyAvailableThermostatsByName() {
        fun e(id: String, state: String, name: String) = EntityState(id, state, JSONObject().put("friendly_name", name))
        val list = heatingThermostats(
            mapOf(
                "climate.upstairs" to e("climate.upstairs", "heat", "Upstairs"),
                "climate.downstairs" to e("climate.downstairs", "heat", "Downstairs"),
                "climate.broken" to e("climate.broken", "unavailable", "Broken"),
                "light.hall" to e("light.hall", "on", "Hall"),
            ),
        )
        assertEquals(listOf("Downstairs", "Upstairs"), list.map { it.friendlyName })
    }
}
