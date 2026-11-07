package com.churchdrive.app

import androidx.compose.ui.graphics.Color
import com.churchdrive.app.ui.blendScale
import org.junit.Assert.assertEquals
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
        val mid = blendScale(70.0, scale)
        assertEquals(1f, mid.red, 0.01f)
        assertEquals(0.5f, mid.green, 0.01f)
    }
}
