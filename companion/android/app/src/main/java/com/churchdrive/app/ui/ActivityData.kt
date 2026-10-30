package com.churchdrive.app.ui

import com.churchdrive.app.ha.text
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId

/** The days the activity log can be asked for (whole local days, today counting as the last of the 7 or 30). */
enum class ActivityDay(val label: String) { Today("Today"), Yesterday("Yesterday"), Week("7 days"), Month("30 days") }

/** The parts of the day it can be asked for; Night runs over midnight (22:00 to 06:00). The house applies the window to every day in the range. */
enum class ActivityTime(val label: String, val start: String?, val end: String?) {
    All("All day", null, null),
    Morning("Morning", "06:00", "12:00"),
    Afternoon("Afternoon", "12:00", "18:00"),
    Evening("Evening", "18:00", "22:00"),
    Night("Night", "22:00", "06:00"),
}

/** The kinds of line, in the order of the chips: `user` (a person), `clive` (the assistant, because someone asked), `clive_auto` (the assistant, run by an automation), `presence`. */
val ACTIVITY_KINDS = listOf("user", "clive", "clive_auto", "presence")

/** What is being asked for. No kinds chosen means all of them; [who] is a first name. */
data class ActivityFilters(
    val day: ActivityDay = ActivityDay.Today,
    val time: ActivityTime = ActivityTime.All,
    val kinds: Set<String> = emptySet(),
    val who: String? = null,
) {
    fun toggleKind(kind: String): ActivityFilters = copy(kinds = if (kind in kinds) kinds - kind else kinds + kind)
    fun toggleWho(name: String): ActivityFilters = copy(who = if (who == name) null else name)
}

/** The parameters of `church_drive/activity` for [filters] at [nowMs]: the day as [since, until) in the phone's local days, the time-of-day window, kinds and person. */
fun activityQuery(filters: ActivityFilters, nowMs: Long, zone: ZoneId = ZoneId.systemDefault(), limit: Int = 100, offset: Int = 0): JSONObject {
    val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
    val (first, last) = when (filters.day) {
        ActivityDay.Today -> today to today
        ActivityDay.Yesterday -> today.minusDays(1) to today.minusDays(1)
        ActivityDay.Week -> today.minusDays(6) to today
        ActivityDay.Month -> today.minusDays(29) to today
    }
    val q = JSONObject()
        .put("since", first.atStartOfDay(zone).toEpochSecond())
        .put("until", last.plusDays(1).atStartOfDay(zone).toEpochSecond())
        .put("limit", limit)
    filters.time.start?.let { q.put("start", it) }
    filters.time.end?.let { q.put("end", it) }
    if (filters.kinds.isNotEmpty()) q.put("kinds", JSONArray(ACTIVITY_KINDS.filter { it in filters.kinds }))
    filters.who?.let { q.put("who", it) }
    if (offset > 0) q.put("offset", offset)
    return q
}

/** One line of the log. [by] is who he did it for (kind `clive`); [rule] is the automation that ran him (kind `clive_auto`). */
data class ActivityLine(val ts: Long, val kind: String, val who: String, val text: String, val icon: String?, val by: String?, val rule: String?)

/** An answer from the house: the lines (newest first), how many matched in all, who is named in the log, and what the assistant is called. */
data class ActivityResult(val lines: List<ActivityLine>, val total: Int, val people: List<String>, val assistant: String)

fun parseActivity(result: Any?): ActivityResult? {
    val o = result as? JSONObject ?: return null
    val list = o.optJSONArray("entries") ?: return null
    val lines = (0 until list.length()).mapNotNull { i ->
        val e = list.optJSONObject(i) ?: return@mapNotNull null
        val text = e.text("text") ?: return@mapNotNull null
        ActivityLine((e.optDouble("ts", 0.0) * 1000).toLong(), e.text("kind") ?: "user", e.optString("who", ""), text, e.text("icon"), e.text("by"), e.text("rule"))
    }
    val people = o.optJSONArray("people")?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() } } ?: emptyList()
    return ActivityResult(lines, o.optInt("total", lines.size), people, o.text("assistant") ?: "Clive")
}

fun activityIsAssistant(kind: String): Boolean = kind == "clive" || kind == "clive_auto"

/** Who did it: the person, or the assistant by his name. */
fun activityWho(line: ActivityLine, assistant: String): String = if (activityIsAssistant(line.kind)) assistant else line.who.ifBlank { "Someone" }

/** Whether someone asked him or an automation ran him, and who or which; null for people and comings and goings. */
fun activityNote(line: ActivityLine): String? = when (line.kind) {
    "clive" -> line.by?.let { "asked by $it" } ?: "asked"
    "clive_auto" -> line.rule?.let { "automatic: $it" } ?: "automatic"
    else -> null
}

/** The chip's words for a kind; the assistant's kinds are named after him. */
fun activityKindLabel(kind: String, assistant: String): String = when (kind) {
    "user" -> "People"
    "clive" -> "$assistant asked"
    "clive_auto" -> "$assistant automatic"
    else -> "Coming and going"
}

/** The lines under their day ("Today", "Yesterday", "Monday 21 Sep"), keeping the newest-first order. */
fun activityDays(lines: List<ActivityLine>, now: Long, zone: ZoneId = ZoneId.systemDefault()): List<Pair<String, List<ActivityLine>>> =
    lines.groupBy { eventDay(it.ts, now, zone) }.toList()

/** "1 line", "3 lines match". */
fun activitySummary(filters: ActivityFilters, total: Int): String {
    val n = "$total ${if (total == 1) "line" else "lines"}"
    return if (filters.kinds.isNotEmpty() || filters.who != null || filters.time != ActivityTime.All) "$n match" else n
}
