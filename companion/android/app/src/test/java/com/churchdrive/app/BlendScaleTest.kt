package com.churchdrive.app

import androidx.compose.ui.graphics.Color
import com.churchdrive.app.ui.blendScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlendScaleTest {
    private val scale = listOf(10.0 to Color.Green, 50.0 to Color.Yellow, 90.0 to Color.Red)

    @Test
    fun belowAndAboveTheScaleTakeTheEndColours() {
        assertEquals(Color.Green, blendScale(0.0, scale))
        assertEquals(Color.Red, blendScale(500.0, scale))
    }

    @Test
    fun onAnAnchorIsThatColourAndBetweenIsBlended() {
        assertEquals(Color.Yellow, blendScale(50.0, scale))
        // Compose blends in a perceptual colour space, so the middle is between the two, not exactly half way in RGB.
        val mid = blendScale(70.0, scale)
        assertTrue(mid.green > 0.1f && mid.green < 0.9f)
        assertTrue(mid.red > 0.8f)
    }
}
