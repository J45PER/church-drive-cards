package com.churchdrive.app

import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ui.ClimateStep
import com.churchdrive.app.ui.QuickSetting
import com.churchdrive.app.ui.onMode
import com.churchdrive.app.ui.quickSteps
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
}
