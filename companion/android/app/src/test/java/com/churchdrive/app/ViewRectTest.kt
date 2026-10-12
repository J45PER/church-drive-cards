package com.churchdrive.app

import com.churchdrive.app.ui.viewRect
import org.junit.Assert.assertEquals
import org.junit.Test

class ViewRectTest {
    @Test
    fun aWideCameraInATallBoxShowsTheMiddle() {
        // A 16:9 picture covering a 900 x 1600 box is 2844 x 1600; centred, the box sees the middle third of its width.
        val coverW = 1600f * 16f / 9f
        val x = (900f - coverW) / 2f
        val r = viewRect(x, 0f, 1f, 900f, 1600f, coverW, 1600f)
        assertEquals(0.5f - 450f / coverW, r.u0, 0.001f)
        assertEquals(900f / coverW, r.du, 0.001f)
        assertEquals(0f, r.v0, 0.001f)
        assertEquals(1f, r.dv, 0.001f)
    }

    @Test
    fun aTallPictureInAWideBoxShowsAPartOfItsHeight() {
        // A square doorbell in a 900 x 506 box is 900 x 900: the box sees 56% of the height, here from the top.
        val r = viewRect(0f, 0f, 1f, 900f, 506f, 900f, 900f)
        assertEquals(0f, r.v0, 0.001f)
        assertEquals(506f / 900f, r.dv, 0.001f)
        assertEquals(1f, r.du, 0.001f)
    }

    @Test
    fun zoomingInShowsLess() {
        val r = viewRect(-450f, -253f, 2f, 900f, 506f, 900f, 506f)
        assertEquals(0.25f, r.u0, 0.001f)
        assertEquals(0.5f, r.du, 0.001f)
        assertEquals(0.25f, r.v0, 0.001f)
        assertEquals(0.5f, r.dv, 0.001f)
    }
}
