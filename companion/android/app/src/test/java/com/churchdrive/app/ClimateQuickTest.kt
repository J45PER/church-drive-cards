package com.churchdrive.app

import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ui.ClimateStep
import com.churchdrive.app.ui.QuickSetting
import com.churchdrive.app.ui.onMode
import com.churchdrive.app.ui.quickSteps
import com.churchdrive.app.ui.snapToStep
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClimateQuickTest {
    private fun climate(state: String, preset: String = "none") = EntityState(
        "climate.c", state,
        JSONObject().put("hvac_modes", JSONArray(listOf("off", "heat"))).put("preset_modes", JSONArray(listOf("none", "eco"))).put("preset_mode", preset),
    )

    private val eco = QuickSetting("Eco", null, "eco")
    private val heat = QuickSetting("Heat", "heat", null)
    private val off = QuickSetting("Off", "off", null)

    @Test
    fun ecoOnAThermostatThatIsOffSwitchesItOnFirst() {
        assertEquals(
            listOf(ClimateStep("set_hvac_mode", "hvac_mode", "heat"), ClimateStep("set_preset_mode", "preset_mode", "eco")),
            quickSteps(climate("off"), eco),
        )
    }

    @Test
    fun ecoWhileHeatingIsJustThePreset() {
        assertEquals(listOf(ClimateStep("set_preset_mode", "preset_mode", "eco")), quickSteps(climate("heat"), eco))
    }

    @Test
    fun heatLeavesEcoAndOffSendsNoPreset() {
        assertEquals(
            listOf(ClimateStep("set_hvac_mode", "hvac_mode", "heat"), ClimateStep("set_preset_mode", "preset_mode", "none")),
            quickSteps(climate("heat", "eco"), heat),
        )
        assertEquals(listOf(ClimateStep("set_hvac_mode", "hvac_mode", "off")), quickSteps(climate("heat", "eco"), off))
    }

    @Test
    fun theOnModeIsHeatThenAutoThenAnythingButOff() {
        assertEquals("heat", onMode(listOf("off", "auto", "heat")))
        assertEquals("auto", onMode(listOf("off", "cool", "auto")))
        assertEquals("cool", onMode(listOf("off", "cool")))
        assertNull(onMode(listOf("off")))
    }

    @Test
    fun targetsSnapOntoTheStepGridInsteadOfDriftingFromTheEcoSetpoint() {
        // Eco reports 12.1; one step up used to give 12.6, 13.1. On a 0.5 grid from 10 it is 12, 12.5, 13.
        assertEquals(12.5, snapToStep(12.1 + 0.5, 0.5, 10.0, 32.0), 0.0001)
        assertEquals(12.0, snapToStep(12.1, 0.5, 10.0, 32.0), 0.0001)
        assertEquals(32.0, snapToStep(40.0, 0.5, 10.0, 32.0), 0.0001)
        assertEquals(10.0, snapToStep(3.0, 0.5, 10.0, 32.0), 0.0001)
    }
}
