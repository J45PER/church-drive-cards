package com.churchdrive.app

import com.churchdrive.app.ui.batteryIconName
import com.churchdrive.app.ui.isRinging
import com.churchdrive.app.ui.signalBars
import com.churchdrive.app.ui.signalIconName
import com.churchdrive.app.ui.tileAspect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraStatusTest {
    @Test
    fun signalBarsFromDbmAndCategory() {
        assertEquals(4, signalBars("-50"))
        assertEquals(3, signalBars("-60"))
        assertEquals(2, signalBars("-70"))
        assertEquals(1, signalBars("-85"))
        assertEquals(3, signalBars("unavailable", "good"))
        assertEquals(-1, signalBars("unknown"))
        assertEquals("mdi:wifi-strength-off-outline", signalIconName(-1))
        assertEquals("mdi:wifi-strength-3", signalIconName(3))
    }

    @Test
    fun batteryIconSteps() {
        assertEquals("mdi:battery", batteryIconName(100.0))
        assertEquals("mdi:battery-50", batteryIconName(53.0))
        assertEquals("mdi:battery-outline", batteryIconName(3.0))
        assertEquals("mdi:battery-unknown", batteryIconName(null))
    }

    @Test
    fun ringing() {
        val now = 1_790_000_030_000L
        assertTrue(isRinging("on", null, now))
        val ding = "2026-10-11T12:00:00+00:00"
        val at = 1791720000000L
        assertTrue(isRinging("off", ding, at + 30_000))
        assertFalse(isRinging("off", ding, at + 60_000))
        assertFalse(isRinging("off", ding, at + 30_000, answeredAt = at + 10_000))
        assertFalse(isRinging("off", "unknown", now))
        assertFalse(isRinging("on", null, now, answeredAt = now - 1_000))
    }

    @Test
    fun tileShapeIsClamped() {
        assertEquals(16f / 9f, tileAspect(0, 0), 0.001f)
        assertEquals(16f / 9f, tileAspect(1920, 1080), 0.001f)
        assertEquals(1f, tileAspect(1000, 1000), 0.001f)
        assertEquals(0.75f, tileAspect(500, 1500), 0.001f)
    }
}
