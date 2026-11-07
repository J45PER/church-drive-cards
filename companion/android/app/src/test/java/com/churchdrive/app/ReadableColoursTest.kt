package com.churchdrive.app

import androidx.compose.ui.graphics.Color
import com.churchdrive.app.ui.Tone
import com.churchdrive.app.ui.contrastRatio
import com.churchdrive.app.ui.readableOn
import com.churchdrive.app.ui.toneColorsFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadableColoursTest {
    @Test
    fun everyToneReadsOnItsOwnGroundInTheDark() {
        for (tone in Tone.entries) {
            val c = toneColorsFor(tone, true)
            assertTrue("$tone text on its ground is ${contrastRatio(c.onContainer, c.container)}:1", contrastRatio(c.onContainer, c.container) >= 4.5f)
            assertTrue("$tone strong colour on its ground is ${contrastRatio(c.accent, c.container)}:1", contrastRatio(c.accent, c.container) >= 4.5f)
        }
    }

    @Test
    fun aColourThatAlreadyReadsIsLeftAlone() {
        val ground = Color(0xFF202733)
        val bright = Color(0xFFFFC857)
        assertEquals(bright, readableOn(bright, Color.White, ground))
    }

    @Test
    fun aTooDimColourIsLiftedJustEnough() {
        val ground = Color(0xFF0B0E13)
        val dim = Color(0xFF3A3F8F)
        val lifted = readableOn(dim, Color.White, ground)
        assertTrue(contrastRatio(lifted, ground) >= 4.5f)
        assertTrue(lifted != dim)
    }
}
