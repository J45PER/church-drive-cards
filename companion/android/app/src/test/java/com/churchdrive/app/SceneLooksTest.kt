package com.churchdrive.app

import com.churchdrive.app.ui.SceneLooks
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SceneLooksTest {
    private fun argb(hex: Long) = (0xFF000000 or hex).toInt()

    private val library = JSONObject(
        """
        {"scenes":[
          {"key":"ruby_glow","name":"Ruby glow","kind":"colour","custom":false,"hex":["#c8324b","#ff95ab"],"dynamic":true},
          {"key":"bright","name":"Bright","kind":"white","color_temp_kelvin":2700,"brightness":255},
          {"key":"nightlight","name":"Nightlight","kind":"white","xy_color":[0.561,0.4042],"brightness":1},
          {"key":"my_scene","name":"My scene","kind":"colour","custom":true,"hex":["#00ff00"],"icon":"mdi:star"},
          {"key":"soho","name":"Soho","kind":"colour","hex":["#111111","#222222"]}
        ]}
        """,
    )
    private val dashboard = JSONObject(
        """
        {"views":[{"path":"presets","cards":[{"type":"custom:scene-styles-card","styles":[
          {"scene":"Soho 2","colour_1":"#ff0000","colour_2":[0,0,255],"icon":"phu:soho-icon"},
          {"scene":"Bright","icon":"mdi:white-balance-sunny"}
        ]}]}]}
        """,
    )

    @Test
    fun aNewColourSceneGetsItsOwnColours() {
        val looks = SceneLooks.parse(library, null)
        assertEquals(listOf(argb(0xC8324B), argb(0xFF95AB)), looks.colours("ruby_glow", "Ruby glow"))
    }

    @Test
    fun aSingleColourIsDoubledIntoAGradient() {
        val looks = SceneLooks.parse(library, null)
        assertEquals(listOf(argb(0x00FF00), argb(0x00FF00)), looks.colours("my_scene", "My scene"))
        assertEquals("mdi:star", looks.icon("my_scene", "My scene"))
    }

    @Test
    fun theLookSetInSceneStylesWins() {
        val looks = SceneLooks.parse(library, dashboard)
        // "Soho 2" is matched to "Soho" (the trailing number is ignored, as in the cards).
        assertEquals(listOf(argb(0xFF0000), argb(0x0000FF)), looks.colours("soho", "Soho"))
        assertEquals("phu:soho-icon", looks.icon("soho", "Soho"))
        // A style with an icon but no colours leaves the colours to the library.
        assertEquals("mdi:white-balance-sunny", looks.icon("bright", "Bright"))
        assertNull(looks.colours("bright", "Bright"))
    }

    @Test
    fun whiteScenesGetAColourFromTheirTemperatureOrColour() {
        val looks = SceneLooks.parse(library, null)
        val warm = looks.whiteColours("bright", "Bright")!!
        assertEquals(argb(0xFFA757), warm[0])
        val night = looks.whiteColours("nightlight", "Nightlight")!!
        assertEquals(argb(0xFFA026), night[0])
        assertNull(looks.whiteColours("unknown", "Unknown"))
    }

    @Test
    fun xyColoursMatchTheIntegration() {
        // The same values the integration's xy_to_hex gives.
        assertEquals(argb(0xFF95AB), SceneLooks.xyToRgb(0.4557, 0.2951))
        assertEquals(argb(0xF5FEFF), SceneLooks.xyToRgb(0.3127, 0.329))
    }

    @Test
    fun readsColoursFromHexOrArrays() {
        assertEquals(argb(0x112233), SceneLooks.parseColour("#112233"))
        assertEquals(argb(0xAABBCC), SceneLooks.parseColour("#abc"))
        assertEquals(argb(0x010203), SceneLooks.parseColour(org.json.JSONArray("[1,2,3]")))
        assertNull(SceneLooks.parseColour("red"))
    }

    @Test
    fun emptyLooksKnowNothing() {
        assertNull(SceneLooks.Empty.colours("bright", "Bright"))
        assertNull(SceneLooks.Empty.icon("bright", "Bright"))
    }

    @Test
    fun picksDarkOrWhiteTextByContrast() {
        // Bright or pastel tiles take dark text; deep ones keep white.
        assertEquals(true, SceneLooks.darkInkOn(listOf(argb(0xFFF1D6), argb(0xFFD9A0))))   // Bright
        assertEquals(true, SceneLooks.darkInkOn(listOf(argb(0xFF95AB), argb(0xFFA06B))))   // pink and orange, like Soho/Ruby glow
        assertEquals(true, SceneLooks.darkInkOn(listOf(argb(0xFFB36B), argb(0xE07A3A))))   // Relax
        assertEquals(false, SceneLooks.darkInkOn(listOf(argb(0xA8793A), argb(0x5C3D1A))))  // Dimmed
        assertEquals(false, SceneLooks.darkInkOn(listOf(argb(0x7A2E10), argb(0x3A1408))))  // Nightlight
        assertEquals(false, SceneLooks.darkInkOn(listOf(argb(0x3A2552), argb(0x1C3358))))  // deep purple and navy
    }
}
