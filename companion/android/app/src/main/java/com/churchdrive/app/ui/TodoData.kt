package com.churchdrive.app.ui

import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** One item of a to-do list. [due] is a date or a date and time, as Home Assistant sends it. */
data class TodoItem(val uid: String, val summary: String, val done: Boolean, val due: String?, val description: String?)

/** The items from `todo/item/list`. */
fun parseTodoItems(result: Any?): List<TodoItem> {
    val list = (result as? JSONObject)?.optJSONArray("items") ?: return emptyList()
    return (0 until list.length()).mapNotNull { i ->
        val o = list.optJSONObject(i) ?: return@mapNotNull null
        val uid = o.optString("uid").takeIf { it.isNotBlank() } ?: return@mapNotNull null
        TodoItem(
            uid,
            o.optString("summary"),
            o.optString("status") == "completed",
            if (o.isNull("due")) null else o.optString("due").takeIf { it.isNotBlank() },
            if (o.isNull("description")) null else o.optString("description").takeIf { it.isNotBlank() },
        )
    }
}

/** The person's own list, `todo.priorities_<first name>`, or null if their name isn't known. */
fun myTodoList(name: String?): String? {
    val first = name?.trim()?.substringBefore(' ').orEmpty()
    val slug = first.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
    return if (slug.isEmpty()) null else "todo.priorities_$slug"
}

/** An icon for a cleaning job from its name. */
fun choreIcon(name: String): String {
    val n = name.lowercase()
    return when {
        Regex("hoover|vacuum").containsMatchIn(n) -> "mdi:vacuum-outline"
        Regex("bath|shower|en-?suite").containsMatchIn(n) -> "mdi:shower"
        Regex("toilet|loo").containsMatchIn(n) -> "mdi:toilet"
        Regex("sheet|bed").containsMatchIn(n) -> "mdi:bed-outline"
        Regex("mop|floor").containsMatchIn(n) -> "mdi:spray-bottle"
        Regex("dust|polish").containsMatchIn(n) -> "mdi:feather"
        Regex("kitchen|oven|hob|fridge").containsMatchIn(n) -> "mdi:countertop-outline"
        Regex("window|glass|mirror").containsMatchIn(n) -> "mdi:window-closed-variant"
        Regex("bin|rubbish|recycl").containsMatchIn(n) -> "mdi:trash-can-outline"
        Regex("wash|laundry|towel").containsMatchIn(n) -> "mdi:washing-machine"
        Regex("garden|lawn|mow|weed").containsMatchIn(n) -> "mdi:flower-outline"
        else -> "mdi:broom"
    }
}

private fun dueTime(due: String, zone: ZoneId): LocalDateTime? {
    if ('T' !in due) return runCatching { LocalDate.parse(due).atTime(23, 59, 59) }.getOrNull()
    return runCatching { OffsetDateTime.parse(due).atZoneSameInstant(zone).toLocalDateTime() }.getOrNull()
        ?: runCatching { LocalDateTime.parse(due) }.getOrNull()
}

/** When a task is due, in words: "2 days overdue", "today 09:00", "tomorrow", "Friday", "20 Oct". Empty if there's no date. */
fun whenText(due: String?, now: LocalDateTime = LocalDateTime.now(), zone: ZoneId = ZoneId.systemDefault()): String {
    if (due.isNullOrBlank()) return ""
    val d = dueTime(due, zone) ?: return ""
    val days = ChronoUnit.DAYS.between(now.toLocalDate(), d.toLocalDate())
    val time = if ('T' in due) " " + d.format(DateTimeFormatter.ofPattern("HH:mm")) else ""
    if (d.isBefore(now)) return if (days < 0) "${-days} day${if (days == -1L) "" else "s"} overdue" else "due now"
    return when {
        days == 0L -> "today$time"
        days == 1L -> "tomorrow$time"
        days < 7 -> d.format(DateTimeFormatter.ofPattern("EEEE", Locale.UK)) + time
        else -> d.format(DateTimeFormatter.ofPattern("d MMM", Locale.UK))
    }
}

/** The line under a task: when it's due, then its note (a repeat such as "Every 2 weeks: Mon 09:00 · for Hayley"). */
fun taskLine(item: TodoItem, now: LocalDateTime = LocalDateTime.now()): String =
    listOf(whenText(item.due, now), item.description.orEmpty()).filter { it.isNotBlank() }.joinToString(" · ")

/** A job the house spotted: its kind, detail, who it's for and an icon, from the item's description. */
data class HouseTask(val kind: String, val detail: String, val who: String, val icon: String)

fun houseTask(item: TodoItem): HouseTask {
    val parts = item.description.orEmpty().split(" · ")
    val last = parts.lastOrNull().orEmpty()
    val forWho = last.startsWith("for ")
    val who = if (forWho) last.removePrefix("for ").trim() else "Everyone"
    val rest = parts.drop(1).let { if (forWho) it.dropLast(1) else it }
    val kind = rest.firstOrNull().orEmpty()
    val icon = when {
        Regex("batter", RegexOption.IGNORE_CASE).containsMatchIn(kind) -> "mdi:battery-alert-variant-outline"
        Regex("filter", RegexOption.IGNORE_CASE).containsMatchIn(kind) -> "mdi:air-filter"
        Regex("vacuum", RegexOption.IGNORE_CASE).containsMatchIn(kind) -> "mdi:robot-vacuum"
        Regex("respond|device", RegexOption.IGNORE_CASE).containsMatchIn(kind) -> "mdi:heart-pulse"
        Regex("safety|smoke|alarm", RegexOption.IGNORE_CASE).containsMatchIn(kind) -> "mdi:smoke-detector-alert"
        Regex("update", RegexOption.IGNORE_CASE).containsMatchIn(kind) -> "mdi:update"
        else -> "mdi:home-alert-outline"
    }
    return HouseTask(kind, rest.drop(1).joinToString(" · "), who, icon)
}

/** Whether a house task is for this person (their own, or everyone's). */
fun houseTaskFor(task: HouseTask, first: String?): Boolean {
    val names = task.who.lowercase().split(',').map { it.trim() }
    return "everyone" in names || (!first.isNullOrBlank() && first.lowercase() in names)
}
