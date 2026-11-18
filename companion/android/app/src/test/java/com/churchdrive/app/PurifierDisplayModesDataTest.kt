package com.churchdrive.app

import com.churchdrive.app.ui.DISPLAY_MODE_CHOICES
import com.churchdrive.app.ui.parsePurifierDisplayModes
import com.churchdrive.app.ui.purifierDisplayLine
import com.churchdrive.app.ui.purifierDisplaySetBody
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PurifierDisplayModesDataTest {
    private val answer = """{"enabled":true,"entity":"fan.p","display":"light.p_display_backlight","modes":{"sleep":"off","auto":"dim"},
        "preset_modes":["Auto","Sleep","Turbo"],"choices":["keep","off","dim","bright"],"summary":"Sleep: Off · Auto: Dim"}"""

    @Test fun readsTheHousesAnswer() {
        val s = parsePurifierDisplayModes(JSONObject(answer))!!
        assertEquals("off", s.choiceFor("Sleep"))
        assertEquals("dim", s.choiceFor(" AUTO "))
        assertEquals("keep", s.choiceFor("Turbo"))
        assertEquals(listOf("Auto", "Sleep", "Turbo"), s.presetModes)
        assertEquals("light.p_display_backlight", s.display)
        assertEquals("Sleep: Off · Auto: Dim", purifierDisplayLine(s))
    }

    @Test fun anOlderHouseOrNothingGivesNull() {
        assertNull(parsePurifierDisplayModes(null))
        assertNull(parsePurifierDisplayModes(JSONObject("""{"schedules":[]}""")))
    }

    @Test fun noDisplayLightIsSaid() {
        val s = parsePurifierDisplayModes(JSONObject("""{"enabled":true,"entity":"fan.p","display":null,"modes":{},"preset_modes":[]}"""))!!
        assertNull(s.display)
        assertEquals("No display found", purifierDisplayLine(s))
    }

    @Test fun theBodyNamesOneModeInLowerCase() {
        assertEquals("""{"modes":{"sleep":"bright"}}""", purifierDisplaySetBody(" Sleep ", "bright").toString())
        assertEquals(listOf("keep", "off", "dim", "bright"), DISPLAY_MODE_CHOICES.map { it.first })
    }
}
