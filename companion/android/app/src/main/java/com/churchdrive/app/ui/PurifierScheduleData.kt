package com.churchdrive.app.ui

import org.json.JSONArray
import org.json.JSONObject

/**
 * The air purifier's schedules (`church_drive/purifier_schedule`): each is some days and two times, shown as
 * "Mon-Fri Auto: 07:00-23:30" (Auto from 07:00, Sleep from 23:30). The house runs them; the app only sets them.
 * Days are 0 = Monday to 6 = Sunday. The rules match purifier_schedule_logic.py and src/purifier-schedule-logic.js.
 */
data class PurifierSchedule(val id: String, val days: List<Int>, val auto: String, val sleep: String)

data class PurifierScheduleState(val enabled: Boolean, val schedules: List<PurifierSchedule>, val next: String?, val lastFailed: String?)

const val MAX_PURIFIER_SCHEDULES = 12
val PURIFIER_DAY_NAMES = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

/** Reads the house's answer; null if it isn't one (the house is older and doesn't have the schedule). */
fun parsePurifierSchedule(result: Any?): PurifierScheduleState? {
    val o = result as? JSONObject ?: return null
    val list = o.optJSONArray("schedules") ?: return null
    val schedules = (0 until list.length()).mapNotNull { list.optJSONObject(it) }.mapNotNull { s ->
        val days = s.optJSONArray("days")?.let { a -> (0 until a.length()).map { a.optInt(it, -1) } }?.filter { it in 0..6 }?.distinct()?.sorted().orEmpty()
        val auto = s.optString("auto")
        val sleep = s.optString("sleep")
        if (days.isEmpty() || !validPurifierTime(auto) || !validPurifierTime(sleep)) null
        else PurifierSchedule(s.optString("id").ifBlank { "s" }, days, auto, sleep)
    }
    val last = o.optJSONObject("last")
    val failed = last?.takeIf { !it.optBoolean("ok", true) && it.optString("skipped").isEmpty() }?.optString("mode")?.takeIf { it.isNotBlank() }
    return PurifierScheduleState(o.optBoolean("enabled", true), schedules, o.optJSONObject("next")?.optString("text")?.takeIf { it.isNotBlank() }, failed)
}

fun validPurifierTime(t: String) = Regex("^([01]\\d|2[0-3]):[0-5]\\d$").matches(t)

/** "Every day", "Mon-Fri", "Sat & Sun", "Mon, Thu & Sat": runs of three or more days are ranges. */
fun purifierDaysText(days: List<Int>): String {
    val ds = days.filter { it in 0..6 }.distinct().sorted()
    if (ds.isEmpty()) return "No days"
    if (ds.size == 7) return "Every day"
    val runs = mutableListOf<MutableList<Int>>()
    for (d in ds) if (runs.isNotEmpty() && d == runs.last().last() + 1) runs.last().add(d) else runs.add(mutableListOf(d))
    val parts = runs.flatMap { run ->
        if (run.size >= 3) listOf("${PURIFIER_DAY_NAMES[run.first()]}-${PURIFIER_DAY_NAMES[run.last()]}") else run.map { PURIFIER_DAY_NAMES[it] }
    }
    return if (parts.size == 1) parts[0] else parts.dropLast(1).joinToString(", ") + " & " + parts.last()
}

/** "Mon-Fri Auto: 07:00-23:30". */
fun purifierSummary(s: PurifierSchedule) = "${purifierDaysText(s.days)} Auto: ${s.auto}-${s.sleep}"

/** A day switched on or off in a schedule; the days stay in order. */
fun purifierToggleDay(s: PurifierSchedule, day: Int): PurifierSchedule =
    s.copy(days = if (day in s.days) s.days - day else (s.days + day).sorted())

/** A new schedule for the days no schedule covers yet (weekdays first), 07:00 to 22:30. */
fun newPurifierSchedule(existing: List<PurifierSchedule>): PurifierSchedule {
    val covered = existing.flatMap { it.days }.toSet()
    val free = (0..6).filter { it !in covered }
    val weekdays = free.filter { it < 5 }
    val days = weekdays.ifEmpty { free }.ifEmpty { listOf(0, 1, 2, 3, 4) }
    var n = 1
    while (existing.any { it.id == "s$n" }) n++
    return PurifierSchedule("s$n", days, "07:00", "22:30")
}

/** What stops a draft being saved, as a sentence; null when it can be. */
fun purifierProblem(schedules: List<PurifierSchedule>): String? = when {
    schedules.size > MAX_PURIFIER_SCHEDULES -> "At most $MAX_PURIFIER_SCHEDULES schedules."
    schedules.any { it.days.isEmpty() } -> "Pick at least one day for each schedule."
    schedules.any { !validPurifierTime(it.auto) || !validPurifierTime(it.sleep) } -> "Set both times for each schedule."
    else -> null
}

/** The days more than one schedule covers ("Tue, Wed"), or null. */
fun purifierOverlaps(schedules: List<PurifierSchedule>): String? {
    val twice = (0..6).filter { d -> schedules.count { d in it.days } > 1 }
    return twice.takeIf { it.isNotEmpty() }?.joinToString(", ") { PURIFIER_DAY_NAMES[it] }
}

/** The `church_drive/purifier_schedule/set` message body for a draft. */
fun purifierSetBody(enabled: Boolean, schedules: List<PurifierSchedule>): JSONObject = JSONObject()
    .put("enabled", enabled)
    .put(
        "schedules",
        JSONArray().also { arr ->
            schedules.forEach { s -> arr.put(JSONObject().put("id", s.id).put("days", JSONArray(s.days)).put("auto", s.auto).put("sleep", s.sleep)) }
        },
    )
