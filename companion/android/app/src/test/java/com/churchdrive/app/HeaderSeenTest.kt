package com.churchdrive.app

import com.churchdrive.app.ui.HeaderSeen
import org.junit.Assert.assertEquals
import org.junit.Test

class HeaderSeenTest {
    @Test
    fun seenLinesStayAwayUntilTheAppUpdates() {
        val stored = setOf("a", "b")
        assertEquals(stored, HeaderSeen.seenFor("0.33.2.405", "0.33.2.405", stored))
        assertEquals(emptySet<String>(), HeaderSeen.seenFor("0.33.2.405", "0.33.3.406", stored))
        assertEquals(emptySet<String>(), HeaderSeen.seenFor(null, "0.33.3.406", stored))
    }
}
