package com.churchdrive.app.ui

import org.json.JSONObject

/**
 * One monthly alarm test (`church_drive/alarm_tests`): the house alarm (its indoor siren sounds briefly), the smoke and heat
 * alarms, or the CO alarm (each one's Device Test). It runs on [day] of each month at [time]; everyone home is told 15 and
 * 5 minutes before. The house runs them (alarm_tests.py); the app only sets them.
 */
data class AlarmTest(
    val kind: String,
    val name: String,
    val enabled: Boolean,
    val day: Int,
    val time: String,
    val summary: String,
    val next: String?,
    val targets: List<String>,
    val lastText: String?,
)

/** The three tests by kind ("alarm", "smoke", "co"); null if the house didn't answer with them (an older house). */
fun parseAlarmTests(result: Any?): Map<String, AlarmTest>? {
    val tests = (result as? JSONObject)?.optJSONObject("tests") ?: return null
    return tests.keys().asSequence().mapNotNull { kind ->
        val o = tests.optJSONObject(kind) ?: return@mapNotNull null
        val targets = o.optJSONArray("targets")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty()
        val last = o.optJSONObject("last")?.let { l ->
            val tested = l.optInt("tested"); val of = l.optInt("of")
            val at = shortDayOf(l.optString("at"))
            when {
                of == 0 -> "Last test found nothing to test"
                tested == of -> "Last tested ${at ?: ""}".trim()
                else -> "Last test: $tested of $of answered"
            }
        }
        kind to AlarmTest(
            kind = kind,
            name = o.optString("name").ifBlank { kind },
            enabled = o.optBoolean("enabled"),
            day = o.optInt("day", 1).coerceIn(1, 31),
            time = o.optString("time").ifBlank { "11:00" },
            summary = o.optString("summary"),
            next = o.optJSONObject("next")?.optString("text")?.takeIf { it.isNotBlank() },
            targets = targets,
            lastText = last,
        )
    }.toMap()
}

/** 1 as "1st", 22 as "22nd", 13 as "13th". */
fun ordinalDay(day: Int): String {
    val suffix = if (day % 100 in 11..13) "th" else when (day % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }
    return "$day$suffix"
}

/** The `church_drive/alarm_tests/set` message body for one test. */
fun alarmTestSetBody(kind: String, enabled: Boolean, day: Int, time: String): JSONObject =
    JSONObject().put("kind", kind).put("enabled", enabled).put("day", day.coerceIn(1, 31)).put("time", time)
