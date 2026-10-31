package com.churchdrive.app

import com.churchdrive.app.ui.HomeTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class HomeTimeTest {
    private val athens = ZoneId.of("Europe/Athens")
    private val london = ZoneId.of("Europe/London")
    // 12:05 UTC on 1 July: London is 13:05, Athens 15:05.
    private val ms = Instant.parse("2026-07-01T12:05:00Z").toEpochMilli()

    @After fun reset() { HomeTime.zoneId = null }

    @Test fun unknownHomeChangesNothing() {
        assertEquals("15:05", HomeTime.format(ms, "HH:mm", athens))
        assertEquals("at 13:05", HomeTime.textBoth("at 13:05", ms, athens))
    }

    @Test fun sameZoneShowsOneTime() {
        HomeTime.zoneId = "Europe/London"
        assertFalse(HomeTime.away(ms, london))
        assertEquals("13:05", HomeTime.format(ms, "HH:mm", london))
        assertEquals("Back at 13:05", HomeTime.textBoth("Back at 13:05", ms, london))
    }

    @Test fun otherZoneShowsBoth() {
        HomeTime.zoneId = "Europe/London"
        assertTrue(HomeTime.away(ms, athens))
        assertEquals("15:05 (13:05 home)", HomeTime.format(ms, "HH:mm", athens))
        assertEquals("Back at 15:05 (13:05 home)", HomeTime.textBoth("Back at 13:05", ms, athens))
        assertEquals("15:05", HomeTime.toDevice("13:05", ms, athens))
    }
}
