package com.churchdrive.app

import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ui.DEFAULT_DISPLAY_LEVELS
import com.churchdrive.app.ui.displayEntityFor
import com.churchdrive.app.ui.displayLevelBrightness
import com.churchdrive.app.ui.displayLevelIndex
import com.churchdrive.app.ui.displayLevelWord
import com.churchdrive.app.ui.displayLevels
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PurifierDisplayDataTest {
    private fun states(vararg ids: String) = ids.associateWith { EntityState(it, "on", JSONObject()) }

    @Test
    fun theDefaultNotchesAreDimAndBrightAndACardCanListItsOwn() {
        assertEquals(DEFAULT_DISPLAY_LEVELS, displayLevels(JSONObject()))
        assertEquals(listOf(25, 50, 100), displayLevels(JSONObject("""{"display_levels":[100,25,50,50]}""")))
        assertEquals(DEFAULT_DISPLAY_LEVELS, displayLevels(JSONObject("""{"display_levels":[0,101,-5]}""")))
        assertEquals(DEFAULT_DISPLAY_LEVELS, displayLevels(JSONObject("""{"display_levels":[]}""")))
    }

    @Test
    fun theDisplayLightIsTheOneNextToThePurifierUnlessTheCardSaysOtherwise() {
        val e = states(
            "fan.hayleys_bedroom_air_purifier", "light.hayleys_bedroom_air_purifier_display_backlight",
            "light.hayleys_bedroom_air_purifier", "light.other_purifier_display_backlight", "light.somewhere_else",
        )
        assertEquals("light.hayleys_bedroom_air_purifier_display_backlight", displayEntityFor(JSONObject(), "fan.hayleys_bedroom_air_purifier", e))
        assertEquals("light.somewhere_else", displayEntityFor(JSONObject("""{"display_entity":"light.somewhere_else"}"""), "fan.hayleys_bedroom_air_purifier", e))
        // The card clears it: no display control.
        assertNull(displayEntityFor(JSONObject("""{"display_entity":""}"""), "fan.hayleys_bedroom_air_purifier", e))
        // A named light the house does not have is not shown.
        assertNull(displayEntityFor(JSONObject("""{"display_entity":"light.gone"}"""), "fan.hayleys_bedroom_air_purifier", e))
        assertNull(displayEntityFor(JSONObject(), "fan.no_light_here", e))
        assertNull(displayEntityFor(JSONObject(), "nonsense", e))
    }

    @Test
    fun theNotchFollowsTheLight() {
        val l = listOf(50, 100)
        assertNull(displayLevelIndex("off", null, l))
        assertNull(displayLevelIndex("unavailable", 255.0, l))
        assertEquals(0, displayLevelIndex("on", 128.0, l))
        assertEquals(1, displayLevelIndex("on", 255.0, l))
        assertEquals(0, displayLevelIndex("on", 1.0, l))
        assertEquals(1, displayLevelIndex("on", 200.0, l))
        // On with no brightness given: the brightest.
        assertEquals(1, displayLevelIndex("on", null, l))
        assertEquals(0, displayLevelIndex("on", 64.0, listOf(25, 50, 100)))
        // 153 is exactly 60%, halfway between 40% and 80%: the higher notch.
        assertEquals(1, displayLevelIndex("on", 153.0, listOf(40, 80)))
    }

    @Test
    fun notchesAreNamedForPeopleAndAskTheLightForTheRightBrightness() {
        assertEquals("Off", displayLevelWord(null, listOf(50, 100)))
        assertEquals("Dim", displayLevelWord(0, listOf(50, 100)))
        assertEquals("Bright", displayLevelWord(1, listOf(50, 100)))
        assertEquals("Medium", displayLevelWord(1, listOf(25, 50, 100)))
        assertEquals("60%", displayLevelWord(2, listOf(20, 40, 60, 80)))
        assertEquals("On", displayLevelWord(0, listOf(100)))
        assertEquals(128, displayLevelBrightness(0, listOf(50, 100)))
        assertEquals(255, displayLevelBrightness(1, listOf(50, 100)))
        assertEquals(255, displayLevelBrightness(9, listOf(50, 100)))
        assertEquals(128, displayLevelBrightness(-1, listOf(50, 100)))
    }
}
