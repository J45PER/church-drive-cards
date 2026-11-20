package com.churchdrive.app.ui

import org.json.JSONObject
import java.time.OffsetDateTime

/**
 * One thing the house has scheduled, as `church_drive/schedules` lists it: something Clive was asked to do ([source] "clive"), or the schedule
 * set from a blind, fan or humidifier's device page ("device") or the air purifier's ("purifier"). [lines] say what happens and when.
 */
data class ScheduleItem(
    val source: String,
    val id: String,
    val title: String,
    val lines: List<String>,
    val enabled: Boolean,
    /** When it next runs (ISO), or null: off, done, or nothing coming up. */
    val next: String?,
    /** A short "Close Fri 20:00" for a device schedule's next change. */
    val nextText: String?,
    val entity: String,
    /** Who asked Clive for it. */
    val by: String,
    /** A one-off that has already run. */
    val done: Boolean,
)

fun parseSchedules(result: Any?): List<ScheduleItem>? {
    val list = (result as? JSONObject)?.optJSONArray("schedules") ?: return null
    return (0 until list.length()).mapNotNull { i ->
        val o = list.optJSONObject(i) ?: return@mapNotNull null
        val lines = o.optJSONArray("lines")?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() } }.orEmpty()
        ScheduleItem(
            source = o.optString("source"),
            id = o.optString("id"),
            title = o.optString("title"),
            lines = lines,
            enabled = o.optBoolean("enabled", true),
            next = if (o.isNull("next")) null else o.optString("next").takeIf { it.isNotBlank() },
            nextText = if (o.isNull("next_text")) null else o.optString("next_text").takeIf { it.isNotBlank() },
            entity = o.optString("entity"),
            by = o.optString("by"),
            done = o.optBoolean("done", false),
        )
    }
}

/** The groups the page shows, in order: coming up (soonest first), then off, then done. */
enum class ScheduleGroup(val title: String) { Coming("Coming up"), Off("Switched off"), Done("Done") }

fun scheduleGroup(item: ScheduleItem): ScheduleGroup = when {
    item.done -> ScheduleGroup.Done
    !item.enabled -> ScheduleGroup.Off
    else -> ScheduleGroup.Coming
}

/** The items by group, empty groups left out, each group's items soonest first (those with no time last, then by title). */
fun groupSchedules(items: List<ScheduleItem>): List<Pair<ScheduleGroup, List<ScheduleItem>>> =
    ScheduleGroup.entries.mapNotNull { g ->
        items.filter { scheduleGroup(it) == g }
            .sortedWith(compareBy<ScheduleItem> { it.next == null }.thenBy { it.next?.let { n -> runCatching { OffsetDateTime.parse(n).toInstant().toEpochMilli() }.getOrNull() ?: Long.MAX_VALUE } ?: Long.MAX_VALUE }.thenBy { it.title })
            .takeIf { it.isNotEmpty() }?.let { g to it }
    }

/** Where a schedule came from, in words. */
fun scheduleSourceWord(item: ScheduleItem): String = when (item.source) {
    "clive" -> if (item.by.isNotBlank()) "Set by Clive for ${item.by}" else "Set by Clive"
    "purifier" -> "Air purifier schedule"
    else -> "Set from the device's card"
}

/** The icon for a schedule: the device's kind from its entity, Clive's calendar for his own. */
fun scheduleIcon(item: ScheduleItem): String = when (item.entity.substringBefore('.')) {
    "cover" -> "mdi:blinds"
    "fan" -> "mdi:fan"
    "humidifier" -> "mdi:air-humidifier"
    "light" -> "mdi:lightbulb-outline"
    "switch", "input_boolean" -> "mdi:toggle-switch-outline"
    "climate" -> "mdi:thermostat"
    "scene" -> "mdi:palette"
    else -> "mdi:calendar-clock"
}

/** The message that switches a schedule on or off in the house, as (type, body), or null if it can't be (nothing to say it with). */
fun scheduleToggle(item: ScheduleItem, enabled: Boolean): Pair<String, JSONObject>? = when (item.source) {
    "clive" -> "church_drive/routines/set" to JSONObject().put("routine_id", item.id).put("enabled", enabled)
    "device" -> "church_drive/blind_schedule/set" to JSONObject().put("entity", item.entity).put("enabled", enabled)
    "purifier" -> "church_drive/purifier_schedule/set" to JSONObject().put("enabled", enabled)
    else -> null
}
