package com.churchdrive.app

import androidx.compose.ui.graphics.Color
import com.churchdrive.app.ui.partColour
import com.churchdrive.app.ui.partLifeLeft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CleaningPageTest {
    private val blue = Color(0xFF2196F3)
    private val red = Color(0xFFF44336)

    @Test fun lifeLeftIsAFractionOfTheLifeAndNeverMoreThanAll() {
        assertEquals(0.5f, partLifeLeft(180.0, 360.0), 0.001f)
        assertEquals(1f, partLifeLeft(400.0, 360.0), 0.001f)
        assertEquals(0f, partLifeLeft(-3.0, 360.0), 0.001f)
        assertEquals(0f, partLifeLeft(null, 360.0), 0.001f)
        assertEquals(0f, partLifeLeft(10.0, 0.0), 0.001f)
    }

    @Test fun aPartFadesFromBlueThroughGreenAndYellowToRed() {
        val green = Color(0xFF4CAF50)
        val yellow = Color(0xFFFFC107)
        assertEquals(blue, partColour(1f, blue, green, yellow, red))
        assertEquals(blue, partColour(0.8f, blue, green, yellow, red))
        assertEquals(green, partColour(0.6f, blue, green, yellow, red))
        assertEquals(yellow, partColour(0.35f, blue, green, yellow, red))
        assertEquals(red, partColour(0.1f, blue, green, yellow, red))
        assertEquals(red, partColour(0f, blue, green, yellow, red))
        // Between two stops it is a blend of the pair, not either one.
        for (f in listOf(0.7f, 0.5f, 0.2f)) {
            val c = partColour(f, blue, green, yellow, red)
            for (stop in listOf(blue, green, yellow, red)) assertNotEquals("$f", stop, c)
        }
        // Running out only ever moves it along the fade: red grows as the life falls.
        assertTrue(partColour(0.2f, blue, green, yellow, red).red > partColour(0.5f, blue, green, yellow, red).red)
    }
}
