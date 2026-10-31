package com.churchdrive.app

import com.churchdrive.app.ui.ActivityAction
import com.churchdrive.app.ui.ActivityDay
import com.churchdrive.app.ui.ActivityFilters
import com.churchdrive.app.ui.ActivityLine
import com.churchdrive.app.ui.ActivityTime
import com.churchdrive.app.ui.activityActionLabel
import com.churchdrive.app.ui.activityDays
import com.churchdrive.app.ui.activityNote
import com.churchdrive.app.ui.activityProblem
import com.churchdrive.app.ui.activityQuery
import com.churchdrive.app.ui.activityStamp
import com.churchdrive.app.ui.activitySummary
import com.churchdrive.app.ui.activityWho
import com.churchdrive.app.ui.parseActivity
import com.churchdrive.app.ui.usesTimeOfDay
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ActivityDataTest {
    private val london = ZoneId.of("Europe/London")
    private fun at(y: Int, m: Int, d: Int, h: Int = 0, min: Int = 0) = ZonedDateTime.of(y, m, d, h, min, 0, 0, london)
    private fun ms(z: ZonedDateTime) = z.toInstant().toEpochMilli()
    private val now = ms(at(2026, 10, 26, 15, 30)) // Monday 26 October, after the clocks went back

    private fun line(ts: Long, kind: String = "user", who: String = "Jamie", by: String? = null, rule: String? = null) =
        ActivityLine(ts, kind, who, "turned on Kitchen", null, by, rule)

    @Test
    fun todayAndYesterdayAreWholeLocalDays() {
        val today = activityQuery(ActivityFilters(), now, london)
        assertEquals(at(2026, 10, 26).toEpochSecond(), today.getLong("since"))
        assertEquals(at(2026, 10, 27).toEpochSecond(), today.getLong("until"))
        val yesterday = activityQuery(ActivityFilters(day = ActivityDay.Yesterday), now, london)
        assertEquals(at(2026, 10, 25).toEpochSecond(), yesterday.getLong("since"))
        assertEquals(at(2026, 10, 26).toEpochSecond(), yesterday.getLong("until"))
        // The day the clocks went back has 25 hours.
        assertEquals(25L * 3600, yesterday.getLong("until") - yesterday.getLong("since"))
    }

    @Test
    fun sevenAndThirtyDaysIncludeToday() {
        val week = activityQuery(ActivityFilters(day = ActivityDay.Week), now, london)
        assertEquals(at(2026, 10, 20).toEpochSecond(), week.getLong("since"))
        assertEquals(at(2026, 10, 27).toEpochSecond(), week.getLong("until"))
        assertEquals(at(2026, 9, 27).toEpochSecond(), activityQuery(ActivityFilters(day = ActivityDay.Month), now, london).getLong("since"))
    }

    @Test
    fun allDatesHasNoLimits() {
        val q = activityQuery(ActivityFilters(day = ActivityDay.All), now, london)
        assertFalse(q.has("since") || q.has("until"))
    }

    @Test
    fun anExactRangeRunsToTheEndOfTheMinuteChosen() {
        val from = ms(at(2026, 10, 24, 8, 15))
        val to = ms(at(2026, 10, 25, 17, 45))
        val q = activityQuery(ActivityFilters(day = ActivityDay.Custom, from = from, to = to), now, london)
        assertEquals(from / 1000, q.getLong("since"))
        assertEquals(to / 1000 + 60, q.getLong("until"))
    }

    @Test
    fun aReversedRangeIsPutRightAndAnOpenEndStaysOpen() {
        val a = ms(at(2026, 10, 24, 8, 15))
        val b = ms(at(2026, 10, 25, 17, 45))
        val q = activityQuery(ActivityFilters(day = ActivityDay.Custom, from = b, to = a), now, london)
        assertEquals(a / 1000, q.getLong("since"))
        assertEquals(b / 1000 + 60, q.getLong("until"))
        val open = activityQuery(ActivityFilters(day = ActivityDay.Custom, from = a, to = null), now, london)
        assertEquals(a / 1000, open.getLong("since"))
        assertFalse(open.has("until"))
    }

    @Test
    fun choosingARangeStartsFromMidnightTodayToNow() {
        val f = ActivityFilters().withDay(ActivityDay.Custom, now, london)
        assertEquals(ms(at(2026, 10, 26)), f.from)
        assertEquals(now, f.to)
        // Changing the ends, then picking another option and back, keeps what was chosen.
        val kept = f.copy(from = 5L).withDay(ActivityDay.Week, now, london).withDay(ActivityDay.Custom, now, london)
        assertEquals(5L, kept.from)
    }

    @Test
    fun aRangeHasItsOwnTimesSoTheTimeOfDayIsLeftOut() {
        val range = ActivityFilters(day = ActivityDay.Custom, time = ActivityTime.Night, from = 1000, to = 2000)
        assertFalse(range.usesTimeOfDay())
        val q = activityQuery(range, now, london)
        assertFalse(q.has("start") || q.has("end"))
        assertTrue(ActivityFilters(time = ActivityTime.Night).usesTimeOfDay())
    }

    @Test
    fun theQuestionCarriesOnlyWhatWasChosen() {
        val plain = activityQuery(ActivityFilters(), now, london)
        assertFalse(plain.has("start") || plain.has("end") || plain.has("kinds") || plain.has("who") || plain.has("offset"))
        assertEquals(100, plain.getInt("limit"))
        val q = activityQuery(ActivityFilters(time = ActivityTime.Night, action = ActivityAction.Assistant, who = "Hayley"), now, london, limit = 50, offset = 100)
        assertEquals("22:00", q.getString("start"))
        assertEquals("06:00", q.getString("end"))
        assertEquals("Hayley", q.getString("who"))
        assertEquals(listOf("clive", "clive_auto"), (0 until q.getJSONArray("kinds").length()).map { q.getJSONArray("kinds").getString(it) })
        assertEquals(50, q.getInt("limit"))
        assertEquals(100, q.getInt("offset"))
    }

    @Test
    fun everyActionOptionAsksForTheRightKinds() {
        fun kinds(a: ActivityAction): List<String> = activityQuery(ActivityFilters(action = a), now, london).optJSONArray("kinds")?.let { k -> (0 until k.length()).map { k.getString(it) } } ?: emptyList()
        assertEquals(emptyList<String>(), kinds(ActivityAction.All))
        assertEquals(listOf("user"), kinds(ActivityAction.People))
        assertEquals(listOf("clive", "clive_auto"), kinds(ActivityAction.Assistant))
        assertEquals(listOf("clive"), kinds(ActivityAction.Asked))
        assertEquals(listOf("clive_auto"), kinds(ActivityAction.Automatic))
        assertEquals(listOf("presence"), kinds(ActivityAction.Presence))
    }

    @Test
    fun readsAnswersAndSkipsBadLines() {
        val answer = JSONObject()
            .put("total", 7)
            .put("assistant", "Gregg")
            .put("people", JSONArray(listOf("Hayley", "", "Jamie")))
            .put("entries", JSONArray()
                .put(JSONObject().put("ts", 1000.5).put("kind", "clive").put("who", "").put("text", "turned off Downstairs").put("by", "Hayley").put("icon", "mdi:thermostat"))
                .put(JSONObject().put("ts", 900).put("kind", "clive_auto").put("who", "").put("text", "turned on Downstairs").put("rule", "keep the climate comfortable"))
                .put(JSONObject().put("ts", 800).put("kind", "user").put("who", "Jamie"))) // no text: dropped
        val r = parseActivity(answer)!!
        assertEquals(7, r.total)
        assertEquals("Gregg", r.assistant)
        assertEquals(listOf("Hayley", "Jamie"), r.people)
        assertEquals(2, r.lines.size)
        assertEquals(1_000_500L, r.lines[0].ts)
        assertEquals("Hayley", r.lines[0].by)
        assertEquals("keep the climate comfortable", r.lines[1].rule)
        assertNull(parseActivity(null))
        assertNull(parseActivity(JSONObject()))
        assertEquals("Clive", parseActivity(JSONObject().put("entries", JSONArray()))!!.assistant)
    }

    @Test
    fun aLineSaysWhoDidItAndWhetherSomeoneAskedOrAnAutomationRan() {
        assertEquals("Gregg", activityWho(line(1, "clive"), "Gregg"))
        assertEquals("Gregg", activityWho(line(1, "clive_auto", who = ""), "Gregg"))
        assertEquals("Jamie", activityWho(line(1), "Gregg"))
        assertEquals("Someone", activityWho(line(1, who = ""), "Gregg"))
        assertEquals("asked by Hayley", activityNote(line(1, "clive", by = "Hayley")))
        assertEquals("asked", activityNote(line(1, "clive")))
        assertEquals("automatic: keep the climate comfortable", activityNote(line(1, "clive_auto", rule = "keep the climate comfortable")))
        assertEquals("automatic", activityNote(line(1, "clive_auto")))
        assertNull(activityNote(line(1)))
        assertNull(activityNote(line(1, "presence")))
    }

    @Test
    fun theActionListNamesTheAssistant() {
        assertEquals(
            listOf("All actions", "People", "Gregg (all)", "Gregg: asked", "Gregg: automatic", "Coming and going"),
            ActivityAction.entries.map { activityActionLabel(it, "Gregg") },
        )
    }

    @Test
    fun linesGroupUnderTheirDay() {
        val lines = listOf(line(ms(at(2026, 10, 26, 12))), line(ms(at(2026, 10, 26, 9))), line(ms(at(2026, 10, 25, 22))))
        val days = activityDays(lines, now, london)
        assertEquals(listOf("Today", "Yesterday"), days.map { it.first })
        assertEquals(listOf(2, 1), days.map { it.second.size })
    }

    @Test
    fun theSummaryCountsLines() {
        assertEquals("1 line", activitySummary(ActivityFilters(), 1))
        assertEquals("3 lines", activitySummary(ActivityFilters(day = ActivityDay.Week), 3))
        assertEquals("3 lines match", activitySummary(ActivityFilters(who = "Ian"), 3))
        assertEquals("3 lines match", activitySummary(ActivityFilters(action = ActivityAction.People), 3))
        assertEquals("3 lines match", activitySummary(ActivityFilters(time = ActivityTime.Night), 3))
        // A range ignores the time-of-day choice, so it doesn't count as narrowing.
        assertEquals("3 lines", activitySummary(ActivityFilters(day = ActivityDay.Custom, time = ActivityTime.Night), 3))
    }

    @Test
    fun theRangeEndsAreReadable() {
        assertEquals("24 Oct 2026, 08:15", activityStamp(ms(at(2026, 10, 24, 8, 15)), london))
    }

    @Test
    fun aRefusedAskIsExplained() {
        assertTrue(activityProblem("unknown_command").contains("0.71.0"))
        assertTrue(activityProblem("unauthorized").contains("administrators"))
        assertTrue(activityProblem(null).contains("couldn't be read"))
    }
}
