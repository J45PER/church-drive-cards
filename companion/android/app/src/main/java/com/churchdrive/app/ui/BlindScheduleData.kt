package com.churchdrive.app.ui

import org.json.JSONArray
import org.json.JSONObject

/**
 * A blind's schedules (`church_drive/blind_schedule`): each is some days and up to two times, shown as
 * "Mon-Fri Open 07:30 · Close 20:00". The house runs them; the app only sets them. Days are 0 = Monday to 6 = Sunday.
 * A time that is left out is null. The rules match blind_schedule_logic.py.
 */
data class BlindSchedule(val id: String, val days: List<Int>, val open: String?, val close: String?)

data class BlindScheduleState(val enabled: Boolean, val schedules: List<BlindSchedule>, val next: String?, val lastFailed: String?)

const val MAX_BLIND_SCHEDULES = 12

/** Reads the house's answer; null if it isn't one (the house is older and doesn't have blind schedules). */
fun parseBlindSchedule(result: Any?): BlindScheduleState? {
    val o = result as? JSONObject ?: return null
    val list = o.optJSONArray("schedules") ?: return null
    val schedules = (0 until list.length()).mapNotNull { list.optJSONObject(it) }.mapNotNull { s ->
        val days = s.optJSONArray("days")?.let { a -> (0 until a.length()).map { a.optInt(it, -1) } }?.filter { it in 0..6 }?.distinct()?.sorted().orEmpty()
        val open = s.optString("open").takeIf { validBlindTime(it) }
        val close = s.optString("close").takeIf { validBlindTime(it) }
        if (days.isEmpty() || (open == null && close == null)) null
        else BlindSchedule(s.optString("id").ifBlank { "s" }, days, open, close)
    }
    val last = o.optJSONObject("last")
    val failed = last?.takeIf { !it.optBoolean("ok", true) && it.optString("skipped").isEmpty() }?.optString("action")?.takeIf { it.isNotBlank() }
    return BlindScheduleState(o.optBoolean("enabled", true), schedules, o.optJSONObject("next")?.optString("text")?.takeIf { it.isNotBlank() }, failed)
}

fun validBlindTime(t: String) = Regex("^([01]\\d|2[0-3]):[0-5]\\d$").matches(t)

/** "Mon-Fri Open 07:30 · Close 20:00" (a time that is not set is left out). */
fun blindSummary(s: BlindSchedule): String {
    val times = listOfNotNull(s.open?.let { "Open $it" }, s.close?.let { "Close $it" }).joinToString(" · ")
    return if (times.isEmpty()) purifierDaysText(s.days) else "${purifierDaysText(s.days)} $times"
}

/** A day switched on or off in a schedule; the days stay in order. */
fun blindToggleDay(s: BlindSchedule, day: Int): BlindSchedule =
    s.copy(days = if (day in s.days) s.days - day else (s.days + day).sorted())

/** A new schedule for the days no schedule covers yet (weekdays first): open at 07:30, close at 20:00. */
fun newBlindSchedule(existing: List<BlindSchedule>): BlindSchedule {
    val covered = existing.flatMap { it.days }.toSet()
    val free = (0..6).filter { it !in covered }
    val weekdays = free.filter { it < 5 }
    val days = weekdays.ifEmpty { free }.ifEmpty { listOf(0, 1, 2, 3, 4) }
    var n = 1
    while (existing.any { it.id == "s$n" }) n++
    return BlindSchedule("s$n", days, "07:30", "20:00")
}

/** What stops a draft being saved, as a sentence; null when it can be. */
fun blindProblem(schedules: List<BlindSchedule>): String? = when {
    schedules.size > MAX_BLIND_SCHEDULES -> "At most $MAX_BLIND_SCHEDULES schedules."
    schedules.any { it.days.isEmpty() } -> "Pick at least one day for each schedule."
    schedules.any { it.open == null && it.close == null } -> "Set a time to open or to close for each schedule."
    else -> null
}

/** The days more than one schedule covers ("Tue, Wed"), or null. */
fun blindOverlaps(schedules: List<BlindSchedule>): String? {
    val twice = (0..6).filter { d -> schedules.count { d in it.days } > 1 }
    return twice.takeIf { it.isNotEmpty() }?.joinToString(", ") { PURIFIER_DAY_NAMES[it] }
}

/** The `church_drive/blind_schedule/set` message body for a draft. */
fun blindSetBody(entity: String, enabled: Boolean, schedules: List<BlindSchedule>): JSONObject = JSONObject()
    .put("entity", entity)
    .put("enabled", enabled)
    .put(
        "schedules",
        JSONArray().also { arr ->
            schedules.forEach { s ->
                val o = JSONObject().put("id", s.id).put("days", JSONArray(s.days))
                s.open?.let { o.put("open", it) }
                s.close?.let { o.put("close", it) }
                arr.put(o)
            }
        },
    )
