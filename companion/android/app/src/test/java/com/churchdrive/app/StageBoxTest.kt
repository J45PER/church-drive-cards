package com.churchdrive.app

import com.churchdrive.app.ui.coverSize
import com.churchdrive.app.ui.fitSize
import com.churchdrive.app.ui.panZoom
import org.junit.Assert.assertEquals
import org.junit.Test

class StageBoxTest {
    @Test
    fun aSquareClipFillsTheWidthOfAWideBox() {
        val (w, h) = coverSize(1f, 160f, 90f)
        assertEquals(160f, w, 0.001f)
        assertEquals(160f, h, 0.001f)
    }

    @Test
    fun aWideClipFillsTheHeightAndAPortraitOneTheWidth() {
        val (w, h) = coverSize(2f, 160f, 90f)
        assertEquals(180f, w, 0.001f)
        assertEquals(90f, h, 0.001f)
        val (pw, ph) = coverSize(0.5f, 160f, 90f)
        assertEquals(160f, pw, 0.001f)
        assertEquals(320f, ph, 0.001f)
    }

    @Test
    fun draggingStaysWithinTheClip() {
        // A square clip in a 160 x 90 box starts centred (y = -35): dragging far down stops at the top edge, far up at the bottom.
        val down = panZoom(1f, 0f, -35f, 80f, 45f, 0f, 500f, 1f, 160f, 90f, 160f, 160f)
        assertEquals(0f, down.third, 0.001f)
        val up = panZoom(1f, 0f, -35f, 80f, 45f, 0f, -500f, 1f, 160f, 90f, 160f, 160f)
        assertEquals(-70f, up.third, 0.001f)
    }

    @Test
    fun zoomIsLimitedAndKeepsCovering() {
        val (z, x, y) = panZoom(1f, 0f, 0f, 0f, 0f, 0f, 0f, 10f, 160f, 90f, 160f, 90f)
        assertEquals(4f, z, 0.001f)
        assertEquals(0f, x, 0.001f)
        assertEquals(0f, y, 0.001f)
        assertEquals(1f, panZoom(2f, -80f, -45f, 0f, 0f, 0f, 0f, 0.1f, 160f, 90f, 160f, 90f).first, 0.001f)
    }

    @Test
    fun aLiveViewIsShownWholeAndCentredThenZoomsToFillThePortraitScreen() {
        // A square picture on a portrait phone screen (100 x 200): it fits at full width, centred in the height.
        val (w, h) = fitSize(1f, 100f, 200f)
        assertEquals(100f, w, 0.001f)
        assertEquals(100f, h, 0.001f)
        // A wide picture is as wide as the box; a tall one as tall.
        val wide = fitSize(16f / 9f, 100f, 200f)
        assertEquals(100f, wide.first, 0.001f)
        assertEquals(56.25f, wide.second, 0.01f)
        assertEquals(50f, fitSize(0.5f, 100f, 100f).first, 0.001f)
        // Unzoomed it stays centred whatever the drag; zoomed past the box it can be dragged but not off its edges.
        val still = panZoom(1f, 0f, 50f, 50f, 100f, 0f, 400f, 1f, 100f, 200f, 100f, 100f, 6f)
        assertEquals(50f, still.third, 0.001f)
        val zoomed = panZoom(1f, 0f, 50f, 50f, 100f, 0f, 0f, 3f, 100f, 200f, 100f, 100f, 6f)
        assertEquals(3f, zoomed.first, 0.001f)
        val far = panZoom(zoomed.first, zoomed.second, zoomed.third, 0f, 0f, -1000f, -1000f, 1f, 100f, 200f, 100f, 100f, 6f)
        assertEquals(-200f, far.second, 0.001f)
        assertEquals(-100f, far.third, 0.001f)
    }
}
