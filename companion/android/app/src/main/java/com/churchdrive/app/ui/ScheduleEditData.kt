package com.churchdrive.app.ui

/**
 * One schedule as the full-page editor sees it, whatever it belongs to: some days (0 = Monday to 6 = Sunday) and two times. For a
 * blind they are Open and Close (either can be left out, so null); for the purifier, Auto and Sleep (both always set).
 */
data class SlotSchedule(val id: String, val days: List<Int>, val first: String?, val second: String?)

/** The one-letter day buttons, Monday first. */
val SCHEDULE_DAY_LETTERS = listOf("M", "T", "W", "T", "F", "S", "S")

/** The quick picks above the day buttons, and the days each sets. */
val SCHEDULE_DAY_PRESETS = listOf("Weekdays" to listOf(0, 1, 2, 3, 4), "Weekend" to listOf(5, 6), "Every day" to (0..6).toList())

/** A day switched on or off; the days stay in order. */
fun slotToggleDay(s: SlotSchedule, day: Int): SlotSchedule =
    s.copy(days = if (day in s.days) s.days - day else (s.days + day).sorted())

/** "Open 07:30 · Close 20:00", leaving out a time that is not set; "No times set" when neither is. */
fun slotTimesText(s: SlotSchedule, firstWord: String, secondWord: String): String =
    listOfNotNull(s.first?.let { "$firstWord $it" }, s.second?.let { "$secondWord $it" }).joinToString(" · ").ifEmpty { "No times set" }

fun BlindSchedule.toSlot() = SlotSchedule(id, days, open, close)
fun SlotSchedule.toBlind() = BlindSchedule(id, days, first, second)

fun PurifierSchedule.toSlot() = SlotSchedule(id, days, auto, sleep)

/** The purifier's two times are never left out, so a missing one (which the editor never makes) takes the usual time. */
fun SlotSchedule.toPurifier() = PurifierSchedule(id, days, first ?: "07:00", second ?: "22:30")
