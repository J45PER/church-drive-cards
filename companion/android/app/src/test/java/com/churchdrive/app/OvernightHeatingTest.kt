package com.churchdrive.app

import com.churchdrive.app.ui.CardSpec
import com.churchdrive.app.ui.describeOvernight
import com.churchdrive.app.ui.hmOf
import com.churchdrive.app.ui.inOvernightWindow
import com.churchdrive.app.ui.overnightModeOf
import com.churchdrive.app.ui.stepTime
import com.churchdrive.app.ui.visibleCards
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OvernightHeatingTest {
    @Test
    fun timesAreReadAsHoursAndMinutes() {
        assertEquals("00:00", hmOf("00:00:00"))
        assertEquals("06:05", hmOf("6:5"))
        assertEquals("23:59", hmOf("23:59:59"))
        assertNull(hmOf("24:00"))
        assertNull(hmOf("unknown"))
        assertNull(hmOf(null))
    }

    @Test
    fun theWindowStartsAtTheStartAndEndsBeforeTheEndAlsoAcrossMidnight() {
        assertTrue(inOvernightWindow("00:00", "06:00", "00:00"))
        assertTrue(inOvernightWindow("00:00", "06:00", "05:59"))
        assertEquals(false, inOvernightWindow("00:00", "06:00", "06:00"))
        assertEquals(false, inOvernightWindow("00:00", "06:00", "12:00"))
        assertTrue(inOvernightWindow("22:00", "06:00", "23:30"))
        assertTrue(inOvernightWindow("22:00", "06:00", "03:00"))
        assertEquals(false, inOvernightWindow("22:00", "06:00", "12:00"))
    }

    @Test
    fun equalOrUnreadableTimesMeanNoWindow() {
        assertEquals(false, inOvernightWindow("06:00", "06:00", "06:00"))
        assertEquals(false, inOvernightWindow("unknown", "06:00", "03:00"))
    }

    @Test
    fun aTimeMovesByHalfAnHourAndWrapsRoundTheClock() {
        assertEquals("00:30", stepTime("00:00", 1))
        assertEquals("23:30", stepTime("00:00", -1))
        assertEquals("00:00", stepTime("23:30", 1))
        assertEquals("06:30", stepTime("06:00:00", 1))
        assertEquals("06:15", stepTime("06:00", 1, 15))
        assertNull(stepTime("nope", 1))
    }

    @Test
    fun eachModeIsDescribedWithItsTimesAndAMissingModeCountsAsSmart() {
        assertTrue(describeOvernight("Eco", "00:00:00", "06:00:00", "Clive").startsWith("From 00:00 to 06:00 the heating is put on Eco"))
        assertTrue(describeOvernight("Off", "23:00", "05:30", "Clive").contains("turned off"))
        assertTrue(describeOvernight("Smart", "00:00", "06:00", "Clive").contains("all night"))
        assertEquals("Eco", overnightModeOf("Eco"))
        assertEquals("Smart", overnightModeOf("unavailable"))
        assertEquals("Smart", overnightModeOf(null))
    }

    @Test
    fun anAdminOnlyCardIsShownOnlyToAnAdministrator() {
        val plain = CardSpec("custom:fan-card", JSONObject())
        val mine = CardSpec("custom:overnight-heating-card", JSONObject().put("admin_only", true))
        assertEquals(listOf(plain), visibleCards(listOf(plain, mine), isAdmin = false))
        assertEquals(listOf(plain, mine), visibleCards(listOf(plain, mine), isAdmin = true))
        assertEquals(emptyList<CardSpec>(), visibleCards(listOf(mine), isAdmin = false))
    }
}
