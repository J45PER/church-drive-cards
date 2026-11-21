package com.churchdrive.app

import com.churchdrive.app.ui.Hybrid
import com.churchdrive.app.ui.HybridDraft
import com.churchdrive.app.ui.HybridLook
import com.churchdrive.app.ui.HYBRID_ICONS
import com.churchdrive.app.ui.LightKind
import com.churchdrive.app.ui.SceneLooks
import com.churchdrive.app.ui.hexToArgb
import com.churchdrive.app.ui.hybridKindsFor
import com.churchdrive.app.ui.hybridLookText
import com.churchdrive.app.ui.hybridProblem
import com.churchdrive.app.ui.hybridSaveBody
import com.churchdrive.app.ui.hybridTileColours
import com.churchdrive.app.ui.parseHybrids
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HybridDataTest {
    private val answer = JSONObject(
        """
        {"hybrids":[{"id":"h1","name":"Movie","icon":"cd:television","target":"light.living_room","looks":{
          "light.ceiling":{"t":"off"},"light.strip":{"t":"dynamic","v":"soho"},"light.lamp":{"t":"colour","v":"#ff9a3c"},
          "light.shelf":{"t":"gradient","v":["#3ec6ff","#5b8cfd","#b36bff"]},"light.wall":{"t":"white","v":"relax"},"light.hall":{"t":"leave"},
          "light.broken":{"t":"sparkle"}}}],
         "active":{"light.living_room":"h1"}}
        """.trimIndent(),
    )

    @Test fun theHousesAnswerIsRead() {
        val list = parseHybrids(answer)!!
        val movie = list.of("light.living_room").single()
        assertEquals("Movie", movie.name)
        assertEquals("cd:television", movie.icon)
        assertEquals(6, movie.looks.size)
        assertEquals(listOf("#3ec6ff", "#5b8cfd", "#b36bff"), movie.looks["light.shelf"]!!.colours)
        assertEquals("h1", list.active["light.living_room"])
        assertTrue(list.of("light.hall").isEmpty())
    }

    @Test fun nothingBackMeansNoList() {
        assertNull(parseHybrids(null))
        assertEquals(0, parseHybrids(JSONObject("{}"))!!.hybrids.size)
    }

    @Test fun anUnknownIconBecomesThePalette() {
        val o = JSONObject("""{"hybrids":[{"id":"a","name":"A","icon":"mdi:skull","target":"light.x","looks":{"light.y":{"t":"off"}}}]}""")
        assertEquals("cd:palette", parseHybrids(o)!!.hybrids.single().icon)
        assertTrue("cd:palette" in HYBRID_ICONS)
    }

    @Test fun eachLightOnlyOffersWhatItCanDo() {
        assertEquals(listOf("leave", "off"), hybridKindsFor(LightKind.Switch))
        assertEquals(listOf("leave", "off", "white"), hybridKindsFor(LightKind.White))
        assertTrue("gradient" in hybridKindsFor(LightKind.Colour))
        assertTrue("gradient" !in hybridKindsFor(LightKind.Dim))
    }

    @Test fun aGradientIsOnlyOfferedToALightThatCanDoOne() {
        val plain = hybridKindsFor(LightKind.Colour, canGradient = false, canDynamic = false)
        assertTrue("gradient" !in plain)
        assertTrue("dynamic" !in plain)
        assertTrue("colour" in plain)
        assertTrue("dynamic" in hybridKindsFor(LightKind.Colour, canGradient = false, canDynamic = true))
        assertTrue("gradient" !in hybridKindsFor(LightKind.Colour, canGradient = false, canDynamic = true))
    }

    @Test fun theHouseSaysWhichLightsCanDoWhat() {
        val o = JSONObject("""{"hybrids":[],"can":{"hue":["light.strip","light.lamp"],"gradient":["light.strip"]}}""")
        val got = parseHybrids(o)!!
        assertEquals(setOf("light.strip", "light.lamp"), got.hueLights)
        assertEquals(setOf("light.strip"), got.gradientLights)
        assertTrue(parseHybrids(JSONObject("""{"hybrids":[]}"""))!!.gradientLights.isEmpty())
    }

    @Test fun theSaveBodyCarriesTheLooks() {
        val draft = HybridDraft(null, "  Cosy ", "cd:flower", mapOf("light.a" to HybridLook("white", "relax"), "light.b" to HybridLook("gradient", colours = listOf("#ffffff", "#000000")), "light.c" to HybridLook("off")))
        val h = hybridSaveBody(draft, "light.room").getJSONObject("hybrid")
        assertEquals("Cosy", h.getString("name"))
        assertEquals("light.room", h.getString("target"))
        assertTrue(!h.has("id"))
        assertEquals("relax", h.getJSONObject("looks").getJSONObject("light.a").getString("v"))
        assertEquals(2, h.getJSONObject("looks").getJSONObject("light.b").getJSONArray("v").length())
        assertEquals("h9", hybridSaveBody(draft.copy(id = "h9"), "light.room").getJSONObject("hybrid").getString("id"))
    }

    @Test fun aDraftNeedsANameAndSomethingToDo() {
        assertNotNull(hybridProblem(HybridDraft(null, " ", "cd:palette", mapOf("light.a" to HybridLook("off")))))
        assertNotNull(hybridProblem(HybridDraft(null, "A", "cd:palette", mapOf("light.a" to HybridLook("leave")))))
        assertNull(hybridProblem(HybridDraft(null, "A", "cd:palette", mapOf("light.a" to HybridLook("off")))))
    }

    @Test fun hexColoursAreRead() {
        assertEquals(0xFFFF9A3C.toInt(), hexToArgb("#ff9a3c"))
        assertNull(hexToArgb("orange"))
        assertNull(hexToArgb("#12345"))
    }

    @Test fun aTileTakesTwoOfItsColours() {
        val h = Hybrid("h", "H", "cd:palette", "light.r", mapOf("light.a" to HybridLook("off"), "light.b" to HybridLook("colour", "#ff0000"), "light.c" to HybridLook("colour", "#0000ff")))
        assertEquals(listOf(0xFFFF0000.toInt(), 0xFF0000FF.toInt()), hybridTileColours(h, SceneLooks.Empty))
        val one = Hybrid("h", "H", "cd:palette", "light.r", mapOf("light.b" to HybridLook("colour", "#ff0000")))
        assertEquals(listOf(0xFFFF0000.toInt(), 0xFFFF0000.toInt()), hybridTileColours(one, SceneLooks.Empty))
    }

    @Test fun aLookReadsAsWords() {
        assertEquals("White scene · Relax", hybridLookText(HybridLook("white", "relax")) { it.replaceFirstChar { c -> c.uppercase() } })
        assertEquals("Off", hybridLookText(HybridLook("off")) { it })
    }
}
