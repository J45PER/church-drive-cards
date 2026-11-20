package com.churchdrive.app

import com.churchdrive.app.ui.EVERY_DAY
import com.churchdrive.app.ui.Tone
import com.churchdrive.app.ui.UpcomingDates
import com.churchdrive.app.ui.UpcomingShow
import com.churchdrive.app.ui.WEEKDAYS
import com.churchdrive.app.ui.WEEKEND
import com.churchdrive.app.ui.isLightRoutine
import com.churchdrive.app.ui.oneOffBody
import com.churchdrive.app.ui.parseRoutines
import com.churchdrive.app.ui.parseUpcoming
import com.churchdrive.app.ui.repeatBody
import com.churchdrive.app.ui.routineActions
import com.churchdrive.app.ui.routineDaysText
import com.churchdrive.app.ui.routineProblem
import com.churchdrive.app.ui.upcomingDays
import com.churchdrive.app.ui.upcomingDetail
import com.churchdrive.app.ui.upcomingFilter
import com.churchdrive.app.ui.upcomingHeading
import com.churchdrive.app.ui.upcomingPeople
import com.churchdrive.app.ui.upcomingTone
import com.churchdrive.app.ui.upcomingWho
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class UpcomingDataTest {
    private val today = LocalDate.of(2026, 11, 20)
    private val sample = JSONObject("""{"items":[
        {"at":"2026-11-20T21:00","date":"2026-11-20","time":"21:00","title":"Close Hayley's Bedroom Blind","kind":"blind","src":"device","by":"","card":"Blind card","tap":{"to":"device","entity":"cover.roller_blind"}},
        {"at":"2026-11-21T08:15","date":"2026-11-21","time":"08:15","title":"Open Hayley's bedroom blind","kind":"blind","src":"clive","by":"Jamie","card":"","tap":{"to":"oneoff","id":"c25df71e"}},
        {"at":"2026-11-23T18:00","date":"2026-11-23","time":"18:00","title":"Put the bins out","kind":"task","src":"device","by":"","card":"To-do","tap":{"to":"page","page":"todo"}},
        {"at":"2026-11-21T22:00","date":"2026-11-21","time":"22:00","title":"Cloakroom light off","kind":"light","src":"user","by":"Diane","card":"","tap":{"to":"routine","id":"r9"}}
    ]}""")

    @Test fun readsTheList() {
        val items = parseUpcoming(sample)!!
        assertEquals(4, items.size)
        assertEquals("oneoff", items[1].tapTo)
        assertEquals("c25df71e", items[1].tapId)
        assertEquals("cover.roller_blind", items[0].tapEntity)
        assertEquals("todo", items[2].tapPage)
        assertNull(parseUpcoming(null))
    }

    @Test fun headingsAndDays() {
        assertEquals("Today · Friday 20 November", upcomingHeading("2026-11-20", today))
        assertEquals("Tomorrow · Saturday 21 November", upcomingHeading("2026-11-21", today))
        assertEquals("Monday 23 November", upcomingHeading("2026-11-23", today))
        val days = upcomingDays(parseUpcoming(sample)!!, today)
        assertEquals(listOf("Today · Friday 20 November", "Tomorrow · Saturday 21 November", "Monday 23 November"), days.map { it.first })
        assertEquals(listOf("08:15", "22:00"), days[1].second.map { it.time })
    }

    @Test fun filters() {
        val items = parseUpcoming(sample)!!
        assertEquals(1, upcomingFilter(items, UpcomingShow.Reminders, UpcomingDates.Week, null, today).size)
        assertEquals(3, upcomingFilter(items, UpcomingShow.Actions, UpcomingDates.Week, null, today).size)
        assertEquals(listOf("Open Hayley's bedroom blind", "Cloakroom light off"), upcomingFilter(items, UpcomingShow.All, UpcomingDates.Tomorrow, null, today).map { it.title })
        assertEquals(1, upcomingFilter(items, UpcomingShow.All, UpcomingDates.Week, "Clive", today).size)
        assertEquals(1, upcomingFilter(items, UpcomingShow.All, UpcomingDates.Week, "Diane", today).size)
        assertEquals(listOf("Clive", "Diane"), upcomingPeople(items))
    }

    @Test fun whoTonesAndDetail() {
        val items = parseUpcoming(sample)!!
        assertEquals(listOf("Blind card", "Clive", "To-do", "Diane"), items.map { upcomingWho(it) })
        assertEquals("08:15 · Jamie asked", upcomingDetail(items[1]))
        assertEquals("21:00", upcomingDetail(items[0]))
        assertEquals(Tone.Blinds, upcomingTone("blind"))
        assertEquals(Tone.Lights, upcomingTone("light"))
        assertEquals(Tone.Grey, upcomingTone("nothing"))
    }

    @Test fun readsRoutines() {
        val r = parseRoutines(JSONObject("""{"routines":[
            {"id":"a","name":"Landing light off","entities":["light.landing"],"action":"off","when":{"kind":"repeat","time":"23:00","days":[0,1,2,3,4,5,6]},"enabled":true,"by":"Hayley","src":"clive","summary":"x","next":"2026-11-20T23:00+00:00"},
            {"id":"b","name":"Open blind","entities":["cover.roller_blind"],"action":"open","when":{"kind":"once","at":"2026-11-21T08:15"},"enabled":true,"by":"Jamie","src":"clive","summary":"y","next":null}]}"""))!!
        assertEquals(2, r.size)
        assertTrue(isLightRoutine(r[0]))
        assertTrue(!isLightRoutine(r[1]))
        assertEquals("2026-11-21T08:15", r[1].onceAt)
        assertNull(r[1].next)
    }

    @Test fun daysAndBodies() {
        assertEquals("Every day", routineDaysText(EVERY_DAY))
        assertEquals("Mon-Fri", routineDaysText(WEEKDAYS))
        assertEquals("Weekends", routineDaysText(WEEKEND))
        assertEquals("Mon, Thu", routineDaysText(listOf(3, 0)))
        assertEquals(listOf("open" to "Open", "close" to "Close"), routineActions("cover.b"))
        assertEquals(listOf("on" to "On", "off" to "Off"), routineActions("light.l"))
        val once = oneOffBody("abc", "cover.b", "open", "2026-11-21", "08:15")
        assertEquals("abc", once.getString("routine_id"))
        assertEquals("2026-11-21T08:15", once.getJSONObject("when").getString("at"))
        assertTrue(!oneOffBody(null, "cover.b", "open", "2026-11-21", "08:15").has("routine_id"))
        val rep = repeatBody(null, listOf("light.a"), "off", "23:00", listOf(4, 0))
        assertEquals("[0,4]", rep.getJSONObject("when").getJSONArray("days").toString())
        assertEquals("Choose a light first.", routineProblem(emptyList(), EVERY_DAY))
        assertEquals("Choose at least one day.", routineProblem(listOf("light.a"), emptyList()))
        assertNull(routineProblem(listOf("light.a"), WEEKDAYS))
    }
}
