package com.churchdrive.app

import com.churchdrive.app.ui.ActivityDay
import com.churchdrive.app.ui.ActivityFilters
import com.churchdrive.app.ui.ActivityLine
import com.churchdrive.app.ui.ActivityTime
import com.churchdrive.app.ui.activityDays
import com.churchdrive.app.ui.activityKindLabel
import com.churchdrive.app.ui.activityNote
import com.churchdrive.app.ui.activityQuery
import com.churchdrive.app.ui.activitySummary
import com.churchdrive.app.ui.activityWho
import com.churchdrive.app.ui.parseActivity
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ActivityDataTest {
    private val london = ZoneId.of("Europe/London")
    private fun at(y: Int, m: Int, d: Int, h: Int = 0, min: Int = 0) = ZonedDateTime.of(y, m, d, h, min, 0, 0, london)
    private val now = at(2026, 10, 26, 15, 30).toInstant().toEpochMilli() // Monday 26 October, after the clocks went back

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
        val month = activityQuery(ActivityFilters(day = ActivityDay.Month), now, london)
        assertEquals(at(2026, 9, 27).toEpochSecond(), month.getLong("since"))
    }

    @Test
    fun theQuestionCarriesOnlyWhatWasChosen() {
        val plain = activityQuery(ActivityFilters(), now, london)
        assertFalse(plain.has("start") || plain.has("end") || plain.has("kinds") || plain.has("who") || plain.has("offset"))
        assertEquals(100, plain.getInt("limit"))
        val f = ActivityFilters(time = ActivityTime.Night, who = "Hayley").toggleKind("clive_auto").toggleKind("clive")
        val q = activityQuery(f, now, london, limit = 50, offset = 100)
        assertEquals("22:00", q.getString("start"))
        assertEquals("06:00", q.getString("end"))
        assertEquals("Hayley", q.getString("who"))
        assertEquals(listOf("clive", "clive_auto"), (0 until q.getJSONArray("kinds").length()).map { q.getJSONArray("kinds").getString(it) })
        assertEquals(50, q.getInt("limit"))
        assertEquals(100, q.getInt("offset"))
    }

    @Test
    fun kindsAndPeopleToggleOnAndOff() {
        val f = ActivityFilters()
        assertEquals(setOf("clive"), f.toggleKind("clive").kinds)
        assertEquals(emptySet<String>(), f.toggleKind("clive").toggleKind("clive").kinds)
        assertEquals("Ian", f.toggleWho("Ian").who)
        assertNull(f.toggleWho("Ian").toggleWho("Ian").who)
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
    fun chipsNameTheAssistant() {
        assertEquals(listOf("People", "Gregg asked", "Gregg automatic", "Coming and going"), listOf("user", "clive", "clive_auto", "presence").map { activityKindLabel(it, "Gregg") })
    }

    @Test
    fun linesGroupUnderTheirDay() {
        val lines = listOf(
            line(at(2026, 10, 26, 12).toInstant().toEpochMilli()),
            line(at(2026, 10, 26, 9).toInstant().toEpochMilli()),
            line(at(2026, 10, 25, 22).toInstant().toEpochMilli()),
        )
        val days = activityDays(lines, now, london)
        assertEquals(listOf("Today", "Yesterday"), days.map { it.first })
        assertEquals(listOf(2, 1), days.map { it.second.size })
    }

    @Test
    fun theSummaryCountsLines() {
        assertEquals("1 line", activitySummary(ActivityFilters(), 1))
        assertEquals("3 lines", activitySummary(ActivityFilters(), 3))
        assertEquals("3 lines match", activitySummary(ActivityFilters(who = "Ian"), 3))
        assertEquals("3 lines match", activitySummary(ActivityFilters(time = ActivityTime.Night), 3))
    }
}
