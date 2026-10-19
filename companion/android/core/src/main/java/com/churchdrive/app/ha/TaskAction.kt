package com.churchdrive.app.ha

/** The snooze lengths on offer, in minutes. The house accepts only these. */
val SNOOZE_MINUTES = listOf(5, 10, 20, 30, 60)

fun snoozeLabel(minutes: Int): String = if (minutes == 60) "1 hour" else "$minutes minutes"

/**
 * What a task reminder's button carries: a kind (done or snooze), the to-do list and item the reminder is about, the first
 * name of the person it was sent to and, for a reminder at a place, that place's zone (empty for a time reminder).
 * The house builds these (`reminder_logic.py`); this reads them.
 */
data class TaskAction(val kind: String, val todo: String, val uid: String, val who: String, val zone: String)

fun parseTaskAction(id: String?): TaskAction? {
    val p = id?.split("|") ?: return null
    if (p.size != 6 || p[0] != "CD" || p[1] !in setOf("done", "snooze") || !p[2].startsWith("todo.")) return null
    return TaskAction(p[1], p[2], p[3], p[4], p[5])
}
