package com.churchdrive.app

import androidx.compose.ui.graphics.Color
import com.churchdrive.app.ui.CliveColours
import com.churchdrive.app.ui.EntityColours
import com.churchdrive.app.ui.LiveColours
import com.churchdrive.app.ui.Tone
import com.churchdrive.app.ui.toneColorsFor
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

class LiveColoursTest {
    private fun hex(c: Color) = "#%02X%02X%02X".format((c.red * 255).roundToInt(), (c.green * 255).roundToInt(), (c.blue * 255).roundToInt())
    private fun five(first: String) = """["$first","#223344","#334455","#445566","#556677"]"""
    private fun builtin(e: EntityColours) = "[" + listOf(e.colour, e.dark, e.accent, e.light, e.deep).joinToString(",") { "\"${hex(it)}\"" } + "]"

    @Test
    fun onlyChangedKnownPalettesWithFiveGoodColoursAreUsed() {
        val json = JSONObject("""{
            "lights": {"colours": ${five("#112233")}},
            "gregg": {"colours": ${builtin(CliveColours.Gregg)}},
            "heating": {"colours": ["#12"]},
            "air": {"colours": ["#zzzzzz","#223344","#334455","#445566","#556677"]},
            "my_new_one": {"colours": ${five("#AABBCC")}}
        }""")
        val out = LiveColours.parse(json)
        assertEquals(setOf("lights"), out.keys)
        assertEquals(Color(0xFF112233), out.getValue("lights").colour)
    }

    @Test
    fun nothingSentIsNoChange() {
        assertTrue(LiveColours.parse(JSONObject("{}")).isEmpty())
        assertTrue(LiveColours.parse(null).isEmpty())
    }

    @Test
    fun appliedPalettesChangeTheTonesAtOnceAndGoingBackRestoresTheBuiltInOnes() {
        val before = toneColorsFor(Tone.Lights, true).accent
        LiveColours.apply(JSONObject("""{"lights": {"colours": ${five("#FFFFFF")}}}"""))
        assertTrue("the strong colour followed Home Assistant", toneColorsFor(Tone.Lights, true).accent != before)
        LiveColours.apply(JSONObject("{}"))
        assertEquals(before, toneColorsFor(Tone.Lights, true).accent)
    }
}
