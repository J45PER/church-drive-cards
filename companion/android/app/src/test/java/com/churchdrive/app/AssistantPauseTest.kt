package com.churchdrive.app

import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ha.NoticeButton
import com.churchdrive.app.ha.parseNoticeButton
import com.churchdrive.app.house.isAssistantNotice
import com.churchdrive.app.ui.AssistantPause
import com.churchdrive.app.ui.assistantPauses
import com.churchdrive.app.ui.pauseLeftText
import com.churchdrive.app.ui.pauseLine
import com.churchdrive.app.ui.resumeAssistantData
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class AssistantPauseTest {
    private val now = 1_792_738_841_000L // 2026-10-23 07:00:41 UTC
    private fun resume(zone: String, atMs: Long, state: String = "2026-10-23 10:08:00") =
        "input_datetime.clive_climate_${zone}_resume" to EntityState(
            "input_datetime.clive_climate_${zone}_resume", state, JSONObject().put("timestamp", atMs / 1000.0), "2026-10-23T08:00:00+01:00",
        )

    @Test fun aZoneWithATimeInTheFutureIsPaused() {
        val pauses = assistantPauses(mapOf(resume("upstairs", now + 3_600_000), resume("downstairs", now + 600_000)), now)
        assertEquals(listOf("Downstairs", "Upstairs"), pauses.map { it.zone })
    }

    @Test fun noPauseMeansNothingToShow() {
        // 2000-01-01 is "no pause", and a time that has passed means he is back in charge.
        assertTrue(assistantPauses(mapOf(resume("downstairs", 946_684_800_000L, "2000-01-01 00:00:00"), resume("upstairs", now - 1)), now).isEmpty())
        assertTrue(assistantPauses(emptyMap(), now).isEmpty())
        val gone = "input_datetime.clive_climate_upstairs_resume" to EntityState("input_datetime.clive_climate_upstairs_resume", "unavailable", JSONObject(), null)
        assertTrue(assistantPauses(mapOf(gone), now).isEmpty())
    }

    @Test fun otherHelpersAreNotZones() {
        val other = "input_datetime.clive_climate_upstairs_when" to EntityState("input_datetime.clive_climate_upstairs_when", "x", JSONObject().put("timestamp", (now + 5_000_000) / 1000.0), null)
        assertTrue(assistantPauses(mapOf(other), now).isEmpty())
    }

    @Test fun theTimeLeftIsRoundedUpAndNeverZero() {
        assertEquals("1 min", pauseLeftText(now + 5_000, now))
        assertEquals("35 min", pauseLeftText(now + 35 * 60_000L, now))
        assertEquals("1 h 20 min", pauseLeftText(now + 80 * 60_000L, now))
        assertEquals("2 h", pauseLeftText(now + 120 * 60_000L, now))
    }

    @Test fun theLineSaysWhenHeTakesOverAgain() {
        val london = TimeZone.getTimeZone("Europe/London")
        assertEquals("Downstairs: takes over again at 10:00 (in 2 h)", pauseLine(AssistantPause("Downstairs", now + 2 * 3_600_000L), now, london))
    }

    @Test fun resumeIsAScriptCallTheHouseUnderstandsAndAButtonTheAppDoes() {
        assertEquals("resume", resumeAssistantData().getJSONObject("variables").getString("command"))
        assertEquals(NoticeButton.House("clive_resume"), parseNoticeButton("CD_clive_resume", null))
    }

    @Test fun onlyTheAssistantsOwnNoticesCarryHisMark() {
        assertTrue(isAssistantNotice("clive_actions"))
        assertFalse(isAssistantNotice("door_away"))
        assertFalse(isAssistantNotice(null))
    }
}
