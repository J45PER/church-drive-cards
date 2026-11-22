package com.churchdrive.app

import com.churchdrive.app.ui.MAX_SAVED_COLOURS
import com.churchdrive.app.ui.WHITE_SCENES
import com.churchdrive.app.ui.decodeSavedColours
import com.churchdrive.app.ui.encodeSavedColours
import com.churchdrive.app.ui.sameColour
import com.churchdrive.app.ui.parseSavedColours
import com.churchdrive.app.ui.savedColoursBody
import com.churchdrive.app.ui.snapBrightness
import com.churchdrive.app.ui.toggleSavedColour
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LightSettingsSavedColoursTest {
    @Test fun brightnessMovesInFivePercentSteps() {
        assertEquals(1, snapBrightness(0f))
        assertEquals(1, snapBrightness(0.02f))
        assertEquals(5, snapBrightness(0.04f))
        assertEquals(50, snapBrightness(0.5f))
        assertEquals(55, snapBrightness(0.53f))
        assertEquals(100, snapBrightness(1f))
        assertEquals(100, snapBrightness(1.4f))
    }

    @Test fun savedColoursSurviveBeingKept() {
        val colours = listOf(28.0 to 100.0, 195.5 to 80.0)
        assertEquals(colours, decodeSavedColours(encodeSavedColours(colours)))
    }

    @Test fun unreadableSavedColoursAreLeftOut() {
        assertEquals(listOf(10.0 to 20.0), decodeSavedColours("10,20;oops;1,2,3;,5"))
        assertEquals(emptyList<Pair<Double, Double>>(), decodeSavedColours(null))
        assertEquals(emptyList<Pair<Double, Double>>(), decodeSavedColours(""))
    }

    @Test fun aColourIsSavedOnceAndTakenOutAgain() {
        val saved = toggleSavedColour(emptyList(), 120.0 to 70.0)
        assertEquals(listOf(120.0 to 70.0), saved)
        assertEquals(emptyList<Pair<Double, Double>>(), toggleSavedColour(saved, 121.0 to 71.0))
    }

    @Test fun noMoreThanTheMostAreKept() {
        var saved = emptyList<Pair<Double, Double>>()
        for (i in 0 until MAX_SAVED_COLOURS + 3) saved = toggleSavedColour(saved, (i * 30.0) to 80.0)
        assertEquals(MAX_SAVED_COLOURS, saved.size)
    }

    @Test fun hueWrapsRoundTheWheel() {
        assertTrue(sameColour(359.0 to 50.0, 1.0 to 50.0))
        assertFalse(sameColour(10.0 to 50.0, 40.0 to 50.0))
    }

    @Test fun theWhiteScenesAreTheLibrarysNine() {
        assertEquals(9, WHITE_SCENES.size)
        assertEquals(WHITE_SCENES.size, WHITE_SCENES.toSet().size)
    }

    @Test fun theHousesSavedColoursAreRead() {
        val answer = JSONObject("""{"colours":[[28.0,100.0],[195.5,80.0],[1]]}""")
        assertEquals(listOf(28.0 to 100.0, 195.5 to 80.0), parseSavedColours(answer))
        assertNull(parseSavedColours(null))
        assertEquals(emptyList<Pair<Double, Double>>(), parseSavedColours(JSONObject("""{"colours":[]}""")))
    }

    @Test fun theSavedColoursAreSentAsPairs() {
        val body = savedColoursBody(listOf(28.0 to 100.0, 120.0 to 50.5))
        val a = body.getJSONArray("colours")
        assertEquals(2, a.length())
        assertEquals(120.0, a.getJSONArray(1).getDouble(0), 0.001)
        assertEquals(50.5, a.getJSONArray(1).getDouble(1), 0.001)
    }
}
