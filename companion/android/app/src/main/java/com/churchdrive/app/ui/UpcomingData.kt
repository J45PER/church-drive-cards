package com.churchdrive.app.ui

import org.json.JSONObject
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * One thing the house will do at a set time, as `church_drive/upcoming` lists it. [kind] gives its colour and icon (blind, light, fan, plug, heat,
 * task, alarm); [src] says who set it (clive, user, or device for a schedule set from a device's own card) and [by] who asked; [tapTo] where a tap
 * goes (oneoff, routine, device or page) with [tapId], [tapEntity] or [tapPage] to say which.
 */
data class UpcomingItem(
    val at: String,
    val date: String,
    val time: String,
    val title: String,
    val kind: String,
    val src: String,
    val by: String,
    val card: String,
    val tapTo: String,
    val tapId: String,
    val tapEntity: String,
    val tapPage: String,
)

fun parseUpcoming(result: Any?): List<UpcomingItem>? {
    val list = (result as? JSONObject)?.optJSONArray("items") ?: return null
    return (0 until list.length()).mapNotNull { i ->
        val o = list.optJSONObject(i) ?: return@mapNotNull null
        val tap = o.optJSONObject("tap")
        UpcomingItem(
            at = o.optString("at"), date = o.optString("date"), time = o.optString("time"), title = o.optString("title"),
            kind = o.optString("kind"), src = o.optString("src"), by = o.optString("by"), card = o.optString("card"),
            tapTo = tap?.optString("to").orEmpty(), tapId = tap?.optString("id").orEmpty(),
            tapEntity = tap?.optString("entity").orEmpty(), tapPage = tap?.optString("page").orEmpty(),
        )
    }
}

/** What the Show choice lists: everything, or one kind of thing. */
enum class UpcomingShow(val label: String, val kinds: Set<String>?) {
    All("All", null),
    Actions("Actions", setOf("blind", "light", "fan", "plug")),
    Reminders("Reminders", setOf("task")),
    Alarms("Alarms", setOf("alarm")),
    Heating("Heating", setOf("heat")),
}

/** How far ahead to look: [days] is what the house is asked for, [only] keeps one day (0 today, 1 tomorrow). */
enum class UpcomingDates(val label: String, val days: Int, val only: Int?) {
    Week("Next 7 days", 7, null),
    Today("Today", 1, 0),
    Tomorrow("Tomorrow", 2, 1),
    Month("Next 30 days", 30, null),
}

/** The people (and Clive) who set something in [items], for the Who choice. */
fun upcomingPeople(items: List<UpcomingItem>): List<String> =
    (items.filter { it.src == "clive" }.map { "Clive" } + items.mapNotNull { it.by.takeIf { b -> b.isNotBlank() && it.src != "clive" } }).distinct().sorted()

fun upcomingFilter(items: List<UpcomingItem>, show: UpcomingShow, dates: UpcomingDates, who: String?, today: LocalDate): List<UpcomingItem> =
    items.filter { item ->
        (show.kinds == null || item.kind in show.kinds) &&
            (dates.only == null || item.date == today.plusDays(dates.only.toLong()).toString()) &&
            (who == null || (who == "Clive" && item.src == "clive") || (item.by == who && item.src != "clive"))
    }

/** The day's heading: "Today · Friday 20 November", "Tomorrow · Saturday 21 November", then "Monday 23 November". */
fun upcomingHeading(date: String, today: LocalDate): String {
    val d = runCatching { LocalDate.parse(date) }.getOrNull() ?: return date
    val text = d.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.UK) + " " + d.dayOfMonth + " " + d.month.getDisplayName(TextStyle.FULL, Locale.UK)
    return when (d) {
        today -> "Today · $text"
        today.plusDays(1) -> "Tomorrow · $text"
        else -> text
    }
}

/** The items under their day's heading, soonest first (the house already sends them in order). */
fun upcomingDays(items: List<UpcomingItem>, today: LocalDate): List<Pair<String, List<UpcomingItem>>> =
    items.groupBy { it.date }.toSortedMap().map { (date, list) -> upcomingHeading(date, today) to list.sortedBy { it.at } }

/** The colour of a kind of thing: blinds, lights, air, plugs, heating, to-do and the alarm each have their own. */
fun upcomingTone(kind: String): Tone = when (kind) {
    "blind" -> Tone.Blinds
    "light" -> Tone.Lights
    "fan" -> Tone.Air
    "plug" -> Tone.Plugs
    "heat" -> Tone.Heating
    "task" -> Tone.Tasks
    "alarm" -> Tone.Alarm
    else -> Tone.Grey
}

fun upcomingIcon(kind: String): String = when (kind) {
    "blind" -> "cd:blind-open"
    "light" -> "cd:bulb-lit"
    "fan" -> "cd:fan"
    "plug" -> "cd:plug"
    "heat" -> "cd:thermostat"
    "task" -> "cd:checklist"
    "alarm" -> "cd:shield-home"
    else -> "cd:calendar-clock"
}

/** The pill's name: Clive, the person who set it, or the card it was set on. */
fun upcomingWho(item: UpcomingItem): String = when (item.src) {
    "clive" -> "Clive"
    "user" -> item.by.ifBlank { "Someone" }
    else -> item.card.ifBlank { "Device" }
}

/** The line under the title: the time, then what kind of thing it is and who asked Clive. */
fun upcomingDetail(item: UpcomingItem): String =
    item.time + (if (item.src == "clive" && item.by.isNotBlank()) " · ${item.by} asked" else "")
