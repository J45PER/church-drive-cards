package com.churchdrive.app.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** "14:05", from an ISO 8601 time, in [zone] (the phone's by default). Empty if it can't be read. */
fun clockTime(iso: String?, zone: ZoneId = ZoneId.systemDefault()): String = runCatching {
    DateTimeFormatter.ofPattern("HH:mm", Locale.UK).withZone(zone).format(Instant.parse(iso))
}.getOrDefault("")

/** "2 Oct", from an ISO 8601 time. Empty if it can't be read. */
fun shortDay(iso: String?, zone: ZoneId = ZoneId.systemDefault()): String = runCatching {
    DateTimeFormatter.ofPattern("d MMM", Locale.UK).withZone(zone).format(Instant.parse(iso))
}.getOrDefault("")
