package com.churchdrive.app

import com.churchdrive.app.ui.LibraryScene
import com.churchdrive.app.ui.MAX_SCENE_COLOURS
import com.churchdrive.app.ui.draftOf
import com.churchdrive.app.ui.newSceneDraft
import com.churchdrive.app.ui.sceneProblem
import com.churchdrive.app.ui.sceneSaveBody
import com.churchdrive.app.ui.stepBrightness
import com.churchdrive.app.ui.withAdded
import com.churchdrive.app.ui.withColour
import com.churchdrive.app.ui.withoutColour
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneEditorDataTest {
    @Test fun aNewSceneNeedsAName() {
        assertEquals("Give the scene a name.", sceneProblem(newSceneDraft()))
        assertNull(sceneProblem(newSceneDraft().copy(name = "Movie night")))
    }

    @Test fun coloursAreCappedAndNeverEmpty() {
        var d = newSceneDraft()
        repeat(10) { d = d.withAdded("#ffd84a") }
        assertEquals(MAX_SCENE_COLOURS, d.colours.size)
        repeat(10) { d = d.withoutColour(0) }
        assertEquals(1, d.colours.size)
        assertEquals(listOf("#123456"), d.withColour(0, "#123456").colours)
    }

    @Test fun brightnessMovesInTensWithinTenToAHundred() {
        assertEquals(70, newSceneDraft().stepBrightness(1).brightness)
        assertEquals(5, newSceneDraft().copy(brightness = 10).stepBrightness(-1).brightness)
        assertEquals(100, newSceneDraft().copy(brightness = 100).stepBrightness(1).brightness)
    }

    @Test fun theBodyIsAColourSceneForTheHouse() {
        val body = sceneSaveBody(newSceneDraft().copy(name = " Movie night ", animated = true)).getJSONObject("scene")
        assertEquals("Movie night", body.getString("name"))
        assertEquals("colour", body.getString("kind"))
        assertEquals(60, body.getInt("brightness"))
        assertEquals("#5b8cfd", body.getJSONArray("colors").getString(0))
        assertTrue(body.getBoolean("dynamic"))
        assertTrue(!body.has("key"))
    }

    @Test fun changingASceneKeepsItsKey() {
        val scene = LibraryScene("movie", "Movie", listOf(0xFF5B8CFD.toInt(), 0xFF1D1D6B.toInt()), null, null, null, false, custom = true, brightnessPct = 80)
        val draft = draftOf(scene)
        assertEquals(listOf("#5b8cfd", "#1d1d6b"), draft.colours)
        assertEquals(80, draft.brightness)
        assertEquals("movie", sceneSaveBody(draft).getJSONObject("scene").getString("key"))
    }
}
