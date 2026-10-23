package com.churchdrive.app

import androidx.compose.ui.graphics.Color
import com.churchdrive.app.ui.partColour
import com.churchdrive.app.ui.partLifeLeft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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

    @Test fun aPartIsBlueWhileItHasLifeAndRedAsItEnds() {
        assertEquals(blue, partColour(1f, blue, red))
        assertEquals(blue, partColour(0.5f, blue, red))
        assertEquals(red, partColour(0.1f, blue, red))
        assertEquals(red, partColour(0f, blue, red))
        // In between it is neither one.
        val mid = partColour(0.3f, blue, red)
        assertNotEquals(blue, mid)
        assertNotEquals(red, mid)
    }
}
