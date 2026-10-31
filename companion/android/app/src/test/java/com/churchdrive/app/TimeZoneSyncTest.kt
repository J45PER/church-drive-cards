package com.churchdrive.app

import com.churchdrive.app.ui.TimeZoneSync
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.util.TimeZone

class TimeZoneSyncTest {
    @Test
    fun theHelperIsNamedAfterTheFirstName() {
        assertEquals("input_text.clive_tz_jamie", TimeZoneSync.entityFor("Jamie"))
        assertEquals("input_text.clive_tz_jamie", TimeZoneSync.entityFor("  Jamie Smith "))
        assertEquals("input_text.clive_tz_diane", TimeZoneSync.entityFor("Diane"))
        assertNull(TimeZoneSync.entityFor(null))
        assertNull(TimeZoneSync.entityFor("  "))
    }

    @Test
    fun theValueIsTheZoneAndItsOffsetInMinutes() {
        val winter = Instant.parse("2026-10-31T00:00:00Z").toEpochMilli()
        val summer = Instant.parse("2026-07-01T12:00:00Z").toEpochMilli()
        assertEquals("Europe/Athens|120", TimeZoneSync.valueFor(TimeZone.getTimeZone("Europe/Athens"), winter))
        assertEquals("Europe/Athens|180", TimeZoneSync.valueFor(TimeZone.getTimeZone("Europe/Athens"), summer))
        assertEquals("Europe/London|0", TimeZoneSync.valueFor(TimeZone.getTimeZone("Europe/London"), winter))
        assertEquals("Europe/London|60", TimeZoneSync.valueFor(TimeZone.getTimeZone("Europe/London"), summer))
        assertEquals("Asia/Kolkata|330", TimeZoneSync.valueFor(TimeZone.getTimeZone("Asia/Kolkata"), winter))
    }
}
