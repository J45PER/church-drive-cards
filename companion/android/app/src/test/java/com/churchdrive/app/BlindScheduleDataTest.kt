package com.churchdrive.app

import com.churchdrive.app.ui.BlindSchedule
import com.churchdrive.app.ui.blindOverlaps
import com.churchdrive.app.ui.blindProblem
import com.churchdrive.app.ui.blindSetBody
import com.churchdrive.app.ui.blindSummary
import com.churchdrive.app.ui.blindToggleDay
import com.churchdrive.app.ui.newBlindSchedule
import com.churchdrive.app.ui.parseBlindSchedule
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BlindScheduleDataTest {
    private val weekdays = BlindSchedule("s1", listOf(0, 1, 2, 3, 4), "07:30", "20:00")

    @Test fun theSummaryLeavesOutATimeThatIsNotSet() {
        assertEquals("Mon-Fri Open 07:30 · Close 20:00", blindSummary(weekdays))
        assertEquals("Sat & Sun Open 09:00", blindSummary(BlindSchedule("x", listOf(5, 6), "09:00", null)))
        assertEquals("Every day Close 21:00", blindSummary(BlindSchedule("x", (0..6).toList(), null, "21:00")))
    }

    @Test fun readsTheHousesAnswer() {
        val answer = JSONObject(
            """{"entity":"cover.roller_blind","enabled":true,"schedules":[{"id":"s1","days":[0,1,2,3,4],"open":"07:30","close":"20:00"},
              {"id":"only","days":[5],"close":"21:00"},{"id":"bad","days":[],"open":"07:00"},{"id":"bad2","days":[6],"open":"7am"}],
              "next":{"action":"close","text":"Close Mon 20:00"},"last":{"action":"open","ok":false,"tries":3}}""",
        )
        val state = parseBlindSchedule(answer)
        assertNotNull(state)
        assertEquals(listOf(weekdays, BlindSchedule("only", listOf(5), null, "21:00")), state!!.schedules)
        assertEquals("Close Mon 20:00", state.next)
        assertEquals("open", state.lastFailed)
        assertNull(parseBlindSchedule(JSONObject("""{"nothing":1}""")))
        assertNull(parseBlindSchedule(null))
    }

    @Test fun aSkippedChangeIsNotAFailure() {
        val state = parseBlindSchedule(JSONObject("""{"enabled":true,"schedules":[],"last":{"action":"open","ok":false,"skipped":"not found"}}"""))
        assertNull(state!!.lastFailed)
    }

    @Test fun togglingADayKeepsThemInOrder() {
        assertEquals(listOf(0, 1, 2, 3), blindToggleDay(weekdays, 4).days)
        assertEquals(listOf(0, 1, 2, 3, 4, 5), blindToggleDay(weekdays, 5).days)
    }

    @Test fun aNewScheduleTakesTheDaysNotCoveredYet() {
        assertEquals(listOf(0, 1, 2, 3, 4), newBlindSchedule(emptyList()).days)
        val n = newBlindSchedule(listOf(weekdays))
        assertEquals(listOf(5, 6), n.days)
        assertEquals("s2", n.id)
        assertEquals("07:30", n.open)
        assertEquals("20:00", n.close)
    }

    @Test fun aDraftNeedsDaysAndATime() {
        assertNull(blindProblem(listOf(weekdays)))
        assertEquals("Pick at least one day for each schedule.", blindProblem(listOf(weekdays.copy(days = emptyList()))))
        assertEquals("Set a time to open or to close for each schedule.", blindProblem(listOf(weekdays.copy(open = null, close = null))))
        assertNull(blindProblem(listOf(weekdays.copy(open = null))))
    }

    @Test fun overlapsNameTheDays() {
        assertNull(blindOverlaps(listOf(weekdays, BlindSchedule("s2", listOf(5, 6), "09:00", null))))
        assertEquals("Tue, Wed", blindOverlaps(listOf(weekdays.copy(days = listOf(0, 1, 2)), BlindSchedule("s2", listOf(1, 2, 3), "09:00", null))))
    }

    @Test fun theSetBodyLeavesOutATimeThatIsNotSet() {
        val body = blindSetBody("cover.roller_blind", false, listOf(BlindSchedule("s1", listOf(5), null, "21:00")))
        assertEquals("cover.roller_blind", body.getString("entity"))
        assertEquals(false, body.getBoolean("enabled"))
        val s = body.getJSONArray("schedules").getJSONObject(0)
        assertEquals("21:00", s.getString("close"))
        assertEquals(false, s.has("open"))
        assertEquals(5, s.getJSONArray("days").getInt(0))
    }
}
