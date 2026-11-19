package com.churchdrive.app

import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ui.climateAction
import com.churchdrive.app.ui.climateBusy
import com.churchdrive.app.ui.climateStatusWord
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThermostatStatusTest {
    private fun thermostat(state: String, action: String? = null, preset: String? = null) =
        EntityState("climate.downstairs", state, JSONObject().apply { action?.let { put("hvac_action", it) }; preset?.let { put("preset_mode", it) } })

    @Test fun saysHeatingOrIdle() {
        assertEquals("Heating", climateStatusWord(thermostat("heat", "heating")))
        assertEquals("Heating", climateStatusWord(thermostat("heat", "preheating")))
        assertEquals("Idle", climateStatusWord(thermostat("heat", "idle")))
        assertEquals("Idle", climateStatusWord(thermostat("heat")))
        assertEquals("Cooling", climateStatusWord(thermostat("cool", "cooling")))
    }

    @Test fun aPresetGoesInFrontOfWhatItIsDoing() {
        assertEquals("Eco · Idle", climateStatusWord(thermostat("heat", "idle", "eco")))
        assertEquals("Eco · Heating", climateStatusWord(thermostat("heat", "heating", "eco")))
        assertEquals("Idle", climateStatusWord(thermostat("heat", "idle", "none")))
        assertEquals("Boost · Heating", climateStatusWord(thermostat("heat", "heating", "boost")))
    }

    @Test fun offAndUnavailable() {
        assertEquals("Off", climateStatusWord(thermostat("off", "off", "eco")))
        assertEquals("Unavailable", climateStatusWord(thermostat("unavailable")))
        assertEquals("Loading…", climateStatusWord(null))
    }

    @Test fun onlyHeatingAndCoolingAreBusy() {
        assertTrue(climateBusy(thermostat("heat", "heating")))
        assertTrue(climateBusy(thermostat("heat", "preheating")))
        assertTrue(climateBusy(thermostat("cool", "cooling")))
        assertFalse(climateBusy(thermostat("heat", "idle")))
        assertFalse(climateBusy(thermostat("off", "off")))
        assertFalse(climateBusy(null))
        assertNull(climateAction(thermostat("heat", "idle")))
    }
}
