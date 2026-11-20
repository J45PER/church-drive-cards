package com.churchdrive.app.ui

import org.json.JSONArray
import org.json.JSONObject

/**
 * One of the house's scheduled actions (`church_drive/routines`): [action] is on, off, open, close or set; [onceAt] is the one-off's local time
 * ("2026-11-21T08:15"), or [days] (0 Monday to 6 Sunday) and [time] for one that repeats.
 */
data class RoutineInfo(
    val id: String,
    val name: String,
    val entities: List<String>,
    val action: String,
    val repeats: Boolean,
    val onceAt: String,
    val time: String,
    val days: List<Int>,
    val enabled: Boolean,
    val by: String,
    val src: String,
    val summary: String,
    val next: String?,
)

fun parseRoutines(result: Any?): List<RoutineInfo>? {
    val list = (result as? JSONObject)?.optJSONArray("routines") ?: return null
    return (0 until list.length()).mapNotNull { i ->
        val o = list.optJSONObject(i) ?: return@mapNotNull null
        val w = o.optJSONObject("when") ?: return@mapNotNull null
        val ents = o.optJSONArray("entities")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty()
        val days = w.optJSONArray("days")?.let { a -> (0 until a.length()).map { a.optInt(it) } }.orEmpty()
        RoutineInfo(
            id = o.optString("id"), name = o.optString("name"), entities = ents, action = o.optString("action"),
            repeats = w.optString("kind") == "repeat", onceAt = w.optString("at"), time = w.optString("time"), days = days,
            enabled = o.optBoolean("enabled", true), by = o.optString("by"), src = o.optString("src"), summary = o.optString("summary"),
            next = if (o.isNull("next")) null else o.optString("next").takeIf { it.isNotBlank() },
        )
    }
}

/** A routine for a light that repeats, which is what the Lights page's Routines card manages. */
fun isLightRoutine(r: RoutineInfo): Boolean = r.repeats && r.entities.firstOrNull()?.startsWith("light.") == true

val ROUTINE_DAY_LETTERS = listOf("M", "T", "W", "T", "F", "S", "S")
val ROUTINE_DAY_NAMES = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
val WEEKDAYS = listOf(0, 1, 2, 3, 4)
val WEEKEND = listOf(5, 6)
val EVERY_DAY = listOf(0, 1, 2, 3, 4, 5, 6)

/** "Every day", "Mon-Fri", "Weekends", else the day names. */
fun routineDaysText(days: List<Int>): String = when (days.sorted()) {
    EVERY_DAY -> "Every day"
    WEEKDAYS -> "Mon-Fri"
    WEEKEND -> "Weekends"
    else -> days.sorted().joinToString(", ") { ROUTINE_DAY_NAMES[it] }
}

/** The action words for a device: a blind opens and closes, everything else goes on and off. */
fun routineActions(entity: String): List<Pair<String, String>> =
    if (entity.startsWith("cover.")) listOf("open" to "Open", "close" to "Close") else listOf("on" to "On", "off" to "Off")

/** The body of `church_drive/routines/set` for a one-off ([routineId] null makes a new one). The name is left to the house, which words it from the device. */
fun oneOffBody(routineId: String?, entity: String, action: String, date: String, time: String): JSONObject = JSONObject()
    .also { if (routineId != null) it.put("routine_id", routineId) }
    .put("entities", JSONArray().put(entity))
    .put("action", action)
    .put("name", "")
    .put("when", JSONObject().put("kind", "once").put("at", "${date}T$time"))

/** The body of `church_drive/routines/set` for a routine that repeats. */
fun repeatBody(routineId: String?, entities: List<String>, action: String, time: String, days: List<Int>): JSONObject = JSONObject()
    .also { if (routineId != null) it.put("routine_id", routineId) }
    .put("entities", JSONArray(entities))
    .put("action", action)
    .put("name", "")
    .put("when", JSONObject().put("kind", "repeat").put("time", time).put("days", JSONArray(days.sorted())))

/** Why a routine can't be saved yet, or null. */
fun routineProblem(entities: List<String>, days: List<Int>): String? = when {
    entities.isEmpty() -> "Choose a light first."
    days.isEmpty() -> "Choose at least one day."
    else -> null
}

/** The lights that can be scheduled, by name: every available light (a room's group is a light too). */
fun schedulableLights(entities: Map<String, com.churchdrive.app.ha.EntityState>): List<Pair<String, String>> =
    entities.values.filter { it.entityId.startsWith("light.") && it.available }.map { it.entityId to it.friendlyName }.sortedBy { it.second.lowercase() }

/** Everything a one-off can be done to: blinds, lights, fans, humidifiers and switches. */
fun schedulableDevices(entities: Map<String, com.churchdrive.app.ha.EntityState>): List<Pair<String, String>> =
    entities.values.filter { it.available && it.entityId.substringBefore('.') in setOf("cover", "light", "fan", "humidifier", "switch") && !it.entityId.endsWith("_display_backlight") }
        .map { it.entityId to it.friendlyName }.sortedBy { it.second.lowercase() }
