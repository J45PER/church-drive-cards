package com.churchdrive.app.ui

import com.churchdrive.app.ha.text
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The dates the activity log can be asked for: whole local days, everything the house kept, or an exact range ([ActivityFilters.from] to [ActivityFilters.to]). */
enum class ActivityDay(val label: String) {
    Today("Today"), Yesterday("Yesterday"), Week("Last 7 days"), Month("Last 30 days"), All("All dates"), Custom("Pick a range"),
}

/** The parts of the day it can be asked for; Night runs over midnight (22:00 to 06:00). The house applies the window to every day in the range. */
enum class ActivityTime(val label: String, val start: String?, val end: String?) {
    All("All day", null, null),
    Morning("Morning (06:00 to 12:00)", "06:00", "12:00"),
    Afternoon("Afternoon (12:00 to 18:00)", "12:00", "18:00"),
    Evening("Evening (18:00 to 22:00)", "18:00", "22:00"),
    Night("Night (22:00 to 06:00)", "22:00", "06:00"),
}

/**
 * Which kinds of line: `user` (a person), `clive` (the assistant, because someone asked), `clive_auto` (the assistant, run by an automation),
 * `presence` (arrived or left), `overruled` (something the assistant set was changed from outside the app: the thermostat's own dial or app, a
 * switch or a remote). [kinds] empty is everything.
 */
enum class ActivityAction(val kinds: List<String>) {
    All(emptyList()), People(listOf("user")), Assistant(listOf("clive", "clive_auto")), Asked(listOf("clive")), Automatic(listOf("clive_auto")), Presence(listOf("presence")),
    Overruled(listOf("overruled")),
}

/** What is being asked for. [who] is a first name (null = everyone). [from] and [to] (milliseconds) are the range for [ActivityDay.Custom]. */
data class ActivityFilters(
    val day: ActivityDay = ActivityDay.Today,
    val time: ActivityTime = ActivityTime.All,
    val action: ActivityAction = ActivityAction.All,
    val who: String? = null,
    val from: Long? = null,
    val to: Long? = null,
) {
    /** Chooses a date option; a custom range starts as midnight today to now so it can be adjusted from there. */
    fun withDay(day: ActivityDay, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): ActivityFilters =
        if (day == ActivityDay.Custom && (from == null || to == null)) {
            copy(day = day, from = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli(), to = nowMs)
        } else copy(day = day)
}

/** Whether the daily time-of-day choice applies: not with an exact range, which has its own times. */
fun ActivityFilters.usesTimeOfDay(): Boolean = day != ActivityDay.Custom

/** The parameters of `church_drive/activity` for [filters] at [nowMs]: the dates as [since, until) in the phone's local days (or the exact range), the time-of-day window, kinds and person. */
fun activityQuery(filters: ActivityFilters, nowMs: Long, zone: ZoneId = ZoneId.systemDefault(), limit: Int = 100, offset: Int = 0): JSONObject {
    val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
    val q = JSONObject().put("limit", limit)
    fun days(first: java.time.LocalDate, last: java.time.LocalDate) {
        q.put("since", first.atStartOfDay(zone).toEpochSecond()).put("until", last.plusDays(1).atStartOfDay(zone).toEpochSecond())
    }
    when (filters.day) {
        ActivityDay.Today -> days(today, today)
        ActivityDay.Yesterday -> days(today.minusDays(1), today.minusDays(1))
        ActivityDay.Week -> days(today.minusDays(6), today)
        ActivityDay.Month -> days(today.minusDays(29), today)
        ActivityDay.All -> Unit
        // The range runs to the end of the minute chosen (the pickers have no seconds), and a reversed range is put the right way round.
        ActivityDay.Custom -> {
            val a = filters.from
            val b = filters.to
            if (a != null && b != null) q.put("since", minOf(a, b) / 1000).put("until", maxOf(a, b) / 1000 + 60)
            else if (a != null) q.put("since", a / 1000)
            else if (b != null) q.put("until", b / 1000 + 60)
        }
    }
    if (filters.usesTimeOfDay()) {
        filters.time.start?.let { q.put("start", it) }
        filters.time.end?.let { q.put("end", it) }
    }
    if (filters.action.kinds.isNotEmpty()) q.put("kinds", org.json.JSONArray(filters.action.kinds))
    filters.who?.let { q.put("who", it) }
    if (offset > 0) q.put("offset", offset)
    return q
}

/** "31 Oct 2026, 08:00", for the range's ends. */
fun activityStamp(ms: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.UK).withZone(zone).format(Instant.ofEpochMilli(ms))

/**
 * One line of the log. [by] is who he did it for (kind `clive`); [rule] is the automation that ran him (kind `clive_auto`); for kind `overruled`,
 * [who] is the device and [clive] is when he set it (milliseconds).
 */
data class ActivityLine(val ts: Long, val kind: String, val who: String, val text: String, val icon: String?, val by: String?, val rule: String?, val clive: Long? = null)

/** An answer from the house: the lines (newest first), how many matched in all, who is named in the log, and what the assistant is called. */
data class ActivityResult(val lines: List<ActivityLine>, val total: Int, val people: List<String>, val assistant: String)

fun parseActivity(result: Any?): ActivityResult? {
    val o = result as? JSONObject ?: return null
    val list = o.optJSONArray("entries") ?: return null
    val lines = (0 until list.length()).mapNotNull { i ->
        val e = list.optJSONObject(i) ?: return@mapNotNull null
        val text = e.text("text") ?: return@mapNotNull null
        ActivityLine((e.optDouble("ts", 0.0) * 1000).toLong(), e.text("kind") ?: "user", e.optString("who", ""), text, e.text("icon"), e.text("by"), e.text("rule"),
            e.optDouble("clive_ts", 0.0).takeIf { it > 0 }?.let { (it * 1000).toLong() })
    }
    val people = o.optJSONArray("people")?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() } } ?: emptyList()
    return ActivityResult(lines, o.optInt("total", lines.size), people, o.text("assistant") ?: "Clive")
}

fun activityIsAssistant(kind: String): Boolean = kind == "clive" || kind == "clive_auto"

/** Who did it: the person, the assistant by his name, or (for an override) the device that was changed. */
fun activityWho(line: ActivityLine, assistant: String): String = if (activityIsAssistant(line.kind)) assistant else line.who.ifBlank { "Someone" }

/** Whether someone asked him or an automation ran him (and who or which), or that a device overruled him; null for people and comings and goings. */
fun activityNote(line: ActivityLine, assistant: String = "the assistant"): String? = when (line.kind) {
    "clive" -> line.by?.let { "asked by $it" } ?: "asked"
    "clive_auto" -> line.rule?.let { "automatic: $it" } ?: "automatic"
    "overruled" -> "overruled $assistant from outside the app" + (line.clive?.let { " (set at ${eventClock(it)})" } ?: "")
    else -> null
}

/** The words of an option in the Actions list; the assistant's are named after him. */
fun activityActionLabel(action: ActivityAction, assistant: String): String = when (action) {
    ActivityAction.All -> "All actions"
    ActivityAction.People -> "People"
    ActivityAction.Assistant -> "$assistant (all)"
    ActivityAction.Asked -> "$assistant: asked"
    ActivityAction.Automatic -> "$assistant: automatic"
    ActivityAction.Presence -> "Coming and going"
    ActivityAction.Overruled -> "Overruled $assistant"
}

/** The lines under their day ("Today", "Yesterday", "Monday 21 Sep"), keeping the newest-first order. */
fun activityDays(lines: List<ActivityLine>, now: Long, zone: ZoneId = ZoneId.systemDefault()): List<Pair<String, List<ActivityLine>>> =
    lines.groupBy { eventDay(it.ts, now, zone) }.toList()

/** "1 line", "3 lines match" (when something narrows it down). */
fun activitySummary(filters: ActivityFilters, total: Int): String {
    val n = "$total ${if (total == 1) "line" else "lines"}"
    val narrowed = filters.action != ActivityAction.All || filters.who != null || (filters.usesTimeOfDay() && filters.time != ActivityTime.All)
    return if (narrowed) "$n match" else n
}

/** What to say when the house could not answer: an older Church Drive (no activity log yet), not an administrator, or anything else. */
fun activityProblem(errorCode: String?): String = when (errorCode) {
    "unknown_command" -> "Home Assistant is running an older Church Drive that doesn't have the activity log yet. Update Church Drive to 0.71.0 or later in HACS, then restart Home Assistant."
    "unauthorized" -> "Only administrators can see the activity log."
    else -> "The activity log couldn't be read from the house. Check the connection, or that Church Drive is running in Home Assistant."
}
