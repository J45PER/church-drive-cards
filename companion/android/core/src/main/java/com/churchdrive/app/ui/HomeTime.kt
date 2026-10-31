package com.churchdrive.app.ui

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The home's time zone (Home Assistant's own, read when the app connects) and what to show when the device is somewhere else: the time on the device,
 * and the home's time after it in brackets, "14:05 (12:05 home)". Nothing changes while the zones agree, or while the home's zone isn't known yet.
 */
object HomeTime {
    /** Home Assistant's time zone, such as "Europe/London", or null until it has been read. */
    @Volatile var zoneId: String? = null

    /** A clock time written as hours and minutes in some text. */
    private val CLOCK = Regex("""(?<![\d:])([01]?\d|2[0-3]):([0-5]\d)(?![\d:])""")

    private fun home(): ZoneId? = zoneId?.let { runCatching { ZoneId.of(it) }.getOrNull() }

    /** Whether the device's clock differs from the home's at [ms]. */
    fun away(ms: Long, device: ZoneId = ZoneId.systemDefault()): Boolean {
        val h = home() ?: return false
        val at = Instant.ofEpochMilli(ms)
        return h.rules.getOffset(at) != device.rules.getOffset(at)
    }

    /** [ms] as [pattern] in the device's zone, with the same in the home's zone after it when the two differ: "14:05 (12:05 home)". */
    fun format(ms: Long, pattern: String, device: ZoneId = ZoneId.systemDefault()): String {
        val at = Instant.ofEpochMilli(ms)
        val local = DateTimeFormatter.ofPattern(pattern, Locale.UK).withZone(device).format(at)
        val h = home()
        if (h == null || !away(ms, device)) return local
        return "$local (${DateTimeFormatter.ofPattern(pattern, Locale.UK).withZone(h).format(at)} home)"
    }

    /** A clock time "HH:MM" given in the home's zone, as the device's "HH:MM" for the same moment today; null when it is the same clock or can't be told. */
    fun toDevice(clock: String, nowMs: Long = System.currentTimeMillis(), device: ZoneId = ZoneId.systemDefault()): String? {
        val h = home() ?: return null
        val m = CLOCK.matchEntire(clock.trim()) ?: return null
        val day = Instant.ofEpochMilli(nowMs).atZone(h).toLocalDate()
        val at = ZonedDateTime.of(day, LocalTime.of(m.groupValues[1].toInt(), m.groupValues[2].toInt()), h).toInstant()
        if (!away(at.toEpochMilli(), device)) return null
        return DateTimeFormatter.ofPattern("HH:mm", Locale.UK).withZone(device).format(at)
    }

    /** [text] written in the home's time (what the house's own cards say), with each clock time shown as the device's and then the home's: "14:05 (12:05 home)". */
    fun textBoth(text: String, nowMs: Long = System.currentTimeMillis(), device: ZoneId = ZoneId.systemDefault()): String {
        if (home() == null) return text
        return CLOCK.replace(text) { m ->
            val local = toDevice(m.value, nowMs, device)
            if (local == null) m.value else "$local (${"%02d:%02d".format(m.groupValues[1].toInt(), m.groupValues[2].toInt())} home)"
        }
    }
}
