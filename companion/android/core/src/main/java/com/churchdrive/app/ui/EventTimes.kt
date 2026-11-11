package com.churchdrive.app.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// The day and the clock time of an event, as the camera events and the activity log both show them (in core: Clive Admin has the log too).

/** "Today", "Yesterday" or "Monday 21 Sep". */
fun eventDay(ms: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val day = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    return when (day) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> DateTimeFormatter.ofPattern("EEEE d MMM", Locale.UK).format(day)
    }
}

/** "21:40". */
fun eventClock(ms: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    HomeTime.format(ms, "HH:mm", zone)
