package com.churchdrive.app

import com.churchdrive.app.ui.PurifierSchedule
import com.churchdrive.app.ui.newPurifierSchedule
import com.churchdrive.app.ui.parsePurifierSchedule
import com.churchdrive.app.ui.purifierDaysText
import com.churchdrive.app.ui.purifierOverlaps
import com.churchdrive.app.ui.purifierProblem
import com.churchdrive.app.ui.purifierSetBody
import com.churchdrive.app.ui.purifierSummary
import com.churchdrive.app.ui.purifierToggleDay
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PurifierScheduleDataTest {
    private val weekdays = PurifierSchedule("s1", listOf(0, 1, 2, 3, 4), "07:00", "23:30")

    @Test fun daysReadLikeAPersonWouldSayThem() {
        assertEquals("Mon-Fri", purifierDaysText(listOf(0, 1, 2, 3, 4)))
        assertEquals("Sat & Sun", purifierDaysText(listOf(5, 6)))
        assertEquals("Mon, Thu & Sat", purifierDaysText(listOf(0, 3, 5)))
        assertEquals("Every day", purifierDaysText((0..6).toList()))
        assertEquals("Mon-Wed & Fri", purifierDaysText(listOf(0, 1, 2, 4)))
        assertEquals("Wed", purifierDaysText(listOf(2)))
        assertEquals("No days", purifierDaysText(emptyList()))
    }

    @Test fun theSummaryMatchesTheDashboard() {
        assertEquals("Mon-Fri Auto: 07:00-23:30", purifierSummary(weekdays))
        assertEquals("Mon, Thu & Sat Auto: 08:00-22:00", purifierSummary(PurifierSchedule("x", listOf(0, 3, 5), "08:00", "22:00")))
    }

    @Test fun readsTheHousesAnswer() {
        val answer = JSONObject(
            """{"enabled":true,"schedules":[{"id":"s1","days":[0,1,2,3,4],"auto":"07:00","sleep":"23:30"},
              {"id":"bad","days":[],"auto":"07:00","sleep":"23:30"},{"id":"bad2","days":[5],"auto":"7am","sleep":"23:30"}],
              "next":{"mode":"sleep","text":"Sleep Mon 23:30"},"last":{"mode":"auto","ok":false,"tries":5}}""",
        )
        val state = parsePurifierSchedule(answer)
        assertNotNull(state)
        assertEquals(listOf(weekdays), state!!.schedules)
        assertEquals("Sleep Mon 23:30", state.next)
        assertEquals("auto", state.lastFailed)
    }

    @Test fun aSwitchedOffPurifierIsNotAFailure() {
        val state = parsePurifierSchedule(JSONObject("""{"schedules":[],"last":{"mode":"sleep","ok":false,"skipped":"switched off"}}"""))
        assertNull(state!!.lastFailed)
    }

    @Test fun anOlderHouseGivesNothing() {
        assertNull(parsePurifierSchedule(null))
        assertNull(parsePurifierSchedule(JSONObject("""{"unrelated":1}""")))
    }

    @Test fun togglingADayKeepsThemInOrder() {
        assertEquals(listOf(0, 2, 4), purifierToggleDay(PurifierSchedule("a", listOf(0, 4), "07:00", "22:00"), 2).days)
        assertEquals(listOf(0), purifierToggleDay(PurifierSchedule("a", listOf(0, 4), "07:00", "22:00"), 4).days)
    }

    @Test fun aNewScheduleTakesTheDaysNobodyHas() {
        assertEquals(listOf(0, 1, 2, 3, 4), newPurifierSchedule(emptyList()).days)
        assertEquals(listOf(5, 6), newPurifierSchedule(listOf(weekdays)).days)
        val all = PurifierSchedule("s1", (0..6).toList(), "07:00", "22:00")
        assertEquals("s2", newPurifierSchedule(listOf(all)).id)
    }

    @Test fun problemsStopASave() {
        assertNull(purifierProblem(listOf(weekdays)))
        assertNull(purifierProblem(emptyList()))
        assertEquals("Pick at least one day for each schedule.", purifierProblem(listOf(weekdays.copy(days = emptyList()))))
        assertEquals("Set both times for each schedule.", purifierProblem(listOf(weekdays.copy(sleep = ""))))
    }

    @Test fun overlappingDaysAreNamed() {
        assertEquals("Tue", purifierOverlaps(listOf(PurifierSchedule("a", listOf(0, 1), "07:00", "22:00"), PurifierSchedule("b", listOf(1, 2), "07:00", "22:00"))))
        assertNull(purifierOverlaps(listOf(weekdays)))
    }

    @Test fun theSaveMessageCarriesTheDraft() {
        val body = purifierSetBody(false, listOf(weekdays))
        assertEquals(false, body.getBoolean("enabled"))
        val s = body.getJSONArray("schedules").getJSONObject(0)
        assertEquals("23:30", s.getString("sleep"))
        assertEquals(5, s.getJSONArray("days").length())
    }
}
