package com.churchdrive.app

import com.churchdrive.app.ui.Adaptive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveTest {
    @Test fun aPhoneUprightIsOneColumnWithTheBarAtTheBottom() {
        // Typical phone widths, less the page's 16dp each side.
        for (w in listOf(320, 360, 392, 412, 450)) {
            val room = (w - 32).toFloat()
            assertEquals("width $w", 1, Adaptive.columns(room))
            assertFalse("width $w", Adaptive.rail(w))
        }
    }

    @Test fun aPhoneOnItsSideHasTheRailAndTwoColumns() {
        // 915dp wide, less an 80dp rail and the page's padding.
        val room = 915f - 80f - 32f
        assertTrue(Adaptive.rail(915))
        assertEquals(2, Adaptive.columns(room))
        // Each column is about a phone's width, as when upright.
        val each = Adaptive.columnWidth(room, 2)
        assertTrue("$each", each in 360f..420f)
        assertTrue(Adaptive.short(412))
    }

    @Test fun tabletsGetMoreColumnsTheWiderTheyAre() {
        // Upright tablet 800 wide, on its side 1280 wide, a large one 1920 wide (rail and padding taken off).
        assertEquals(2, Adaptive.columns(800f - 80f - 32f))
        assertEquals(3, Adaptive.columns(1280f - 80f - 32f))
        assertEquals(4, Adaptive.columns(1920f - 80f - 32f))
        assertFalse(Adaptive.short(1280))
        assertFalse(Adaptive.short(800))
    }

    @Test fun columnsNeverStretchPastTheirMaximumOrShrinkUnderTheirMinimum() {
        for (room in 200..3000 step 7) {
            val n = Adaptive.columns(room.toFloat())
            assertTrue("$room: $n", n in 1..Adaptive.MAX_COLUMNS)
            val each = Adaptive.columnWidth(room.toFloat(), n)
            assertTrue("$room: $each", each <= maxOf(Adaptive.MAX_COLUMN_DP.toFloat(), room.toFloat()))
            if (n > 1) assertTrue("$room x $n: $each", each >= Adaptive.MIN_COLUMN_DP.toFloat())
        }
    }

    @Test fun oneColumnTakesTheWholeWidth() {
        assertEquals(340f, Adaptive.columnWidth(340f, 1), 0.001f)
    }
}
