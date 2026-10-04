package com.churchdrive.app.ui

import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * Editing a to-do item. A repeat, who gets reminded and the notes are kept as plain words in the item's description
 * ("Every 2 weeks: Mon 09:00 · for Hayley · bring bins in"), the same words the dashboard's repeat editor writes and
 * the Church Drive automation reads.
 */
val REPEAT_CHOICES = listOf("None", "Once", "Every day", "Every week", "Every 2 weeks", "Monthly")
val WHO_CHOICES = listOf("Everyone", "Just me", "No reminders")
val DUE_CHOICES = listOf("No date", "Today", "Tomorrow", "Next week")

/** An item's description taken apart: the repeat words, who, the notes. Words that aren't a repeat are all notes. */
data class TaskWords(val repeat: String?, val who: String?, val notes: String)

private val REPEAT_START = Regex(
    "^(once|every day .*|every \\d+ days .*|every \\d+ weeks: .*|(mon|tue|wed|thu|fri|sat|sun)[a-z]* \\d{1,2}:\\d{2}.*|" +
        "monthly on .*|every \\d+ months on .*|yearly on .*|every (\\d+ )?(day|week|month)s? after done .*)$",
    RegexOption.IGNORE_CASE,
)

fun parseTaskWords(description: String?): TaskWords {
    val parts = description.orEmpty().split(" · ").map { it.trim() }.filter { it.isNotEmpty() }
    if (parts.isEmpty() || !REPEAT_START.matches(parts[0])) return TaskWords(null, null, parts.joinToString(" · "))
    val whoAt = parts.drop(1).firstOrNull { it.startsWith("for ", true) || it.equals("no reminders", true) }
    val notes = parts.drop(1).filter { it != whoAt }.joinToString(" · ")
    return TaskWords(parts[0], whoAt, notes)
}

/** Which repeat choice the words are. Returns the choice, or the words themselves when the app has no choice for them (kept as they are). */
fun repeatChoiceOf(repeat: String?): String {
    val r = repeat?.trim().orEmpty()
    return when {
        r.isEmpty() -> "None"
        r.equals("once", true) -> "Once"
        Regex("^every day \\d.*", RegexOption.IGNORE_CASE).matches(r) -> "Every day"
        Regex("^every 2 weeks: [^,]*$", RegexOption.IGNORE_CASE).matches(r) -> "Every 2 weeks"
        Regex("^(mon|tue|wed|thu|fri|sat|sun)[a-z]* \\d{1,2}:\\d{2}$", RegexOption.IGNORE_CASE).matches(r) -> "Every week"
        Regex("^monthly on the \\d+(st|nd|rd|th) \\d.*", RegexOption.IGNORE_CASE).matches(r) -> "Monthly"
        else -> r
    }
}

fun timeOf(repeat: String?): String? = repeat?.let { Regex("(\\d{1,2}):(\\d{2})").find(it)?.value?.padStart(5, '0') }

fun whoChoiceOf(who: String?, first: String?): String = when {
    who == null -> "Everyone"
    who.equals("for everyone", true) -> "Everyone"
    who.equals("no reminders", true) -> "No reminders"
    !first.isNullOrBlank() && who.equals("for $first", true) -> "Just me"
    else -> who
}

private fun ordinal(n: Int): String = n.toString() + when {
    n % 100 in 11..13 -> "th"
    n % 10 == 1 -> "st"
    n % 10 == 2 -> "nd"
    n % 10 == 3 -> "rd"
    else -> "th"
}

/** The repeat words for a choice (null for None); weekly and monthly follow the due date's day. Custom words pass through. */
fun repeatWords(choice: String, time: String, day: LocalDate): String? {
    val dow = day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.UK).take(3)
    return when (choice) {
        "None" -> null
        "Once" -> "Once"
        "Every day" -> "Every day $time"
        "Every week" -> "$dow $time"
        "Every 2 weeks" -> "Every 2 weeks: $dow $time"
        "Monthly" -> "Monthly on the ${ordinal(day.dayOfMonth)} $time"
        else -> choice
    }
}

fun whoWords(choice: String, first: String?): String = when (choice) {
    "Everyone" -> "for everyone"
    "Just me" -> if (first.isNullOrBlank()) "for everyone" else "for $first"
    "No reminders" -> "no reminders"
    else -> choice
}

/** The description to save: repeat · who · notes, or just the notes when there's no repeat. Blank when empty. */
fun buildDescription(repeat: String?, who: String, notes: String): String =
    if (repeat == null) notes.trim() else listOf(repeat, who, notes.trim()).filter { it.isNotBlank() }.joinToString(" · ")

fun dueDateOf(choice: String, today: LocalDate): LocalDate? = when (choice) {
    "Today" -> today
    "Tomorrow" -> today.plusDays(1)
    "Next week" -> today.plusDays(7)
    else -> null
}

/** Which due choice an item's due date is, or "Keep" when it's some other day (left as it is unless changed). */
fun dueChoiceOf(due: String?, today: LocalDate): String {
    if (due.isNullOrBlank()) return "No date"
    val day = runCatching { LocalDate.parse(due.take(10)) }.getOrNull() ?: return "Keep"
    return DUE_CHOICES.drop(1).firstOrNull { dueDateOf(it, today) == day } ?: "Keep"
}

/** A valid 24-hour time ("9:30" or "09:30"), as "09:30", or null. */
fun cleanTime(text: String): String? {
    val m = Regex("^(\\d{1,2}):(\\d{2})$").matchEntire(text.trim()) ?: return null
    val h = m.groupValues[1].toInt()
    val min = m.groupValues[2].toInt()
    return if (h in 0..23 && min in 0..59) "%02d:%02d".format(h, min) else null
}

/**
 * What to send `todo.update_item`: the new name, the due date (and time) unless it's to be kept, and the description.
 * Values of null are sent as JSON null, which clears them.
 */
fun updateFields(
    uid: String,
    name: String,
    dueChoice: String,
    time: String?,
    today: LocalDate,
    description: String,
): Map<String, Any?> {
    val out = linkedMapOf<String, Any?>("item" to uid, "rename" to name.trim())
    val day = dueDateOf(dueChoice, today)
    if (dueChoice != "Keep") {
        if (day == null) out["due_date"] = null
        else if (time != null) out["due_datetime"] = "$day $time:00"
        else out["due_date"] = day.toString()
    }
    out["description"] = description.ifBlank { null }
    return out
}
