package com.churchdrive.app.ui

import com.churchdrive.app.ha.EntityState
import org.json.JSONObject
import java.time.Instant
import java.time.OffsetDateTime

// The devices Church Drive watches and how long ago each was heard from: Device Health, the Manager's Device Health card and the lists of what
// needs attention all read these. In core so the phone app and Clive Admin share them.

/** An ISO 8601 time (with `Z` or an offset like `+00:00`) as epoch milliseconds, or null. */
fun parseMillis(iso: String?): Long? {
    if (iso.isNullOrBlank()) return null
    return runCatching { Instant.parse(iso).toEpochMilli() }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(iso).toInstant().toEpochMilli() }.getOrNull()
}

/** A watched device for the Device Health card. */
data class WatchedDevice(val id: String, val name: String, val ok: Boolean, val reason: String, val lastHeard: String, val usual: String, val fixes: List<String>)

fun heardAgo(iso: String?, now: Long = System.currentTimeMillis()): String {
    val at = parseMillis(iso) ?: return "not heard yet"
    val minutes = Math.round((now - at) / 60_000.0)
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        else -> Math.round(minutes / 60.0).let { h -> if (h < 48) "$h h ago" else "${Math.round(h / 24.0)} days ago" }
    }
}

/** The devices Church Drive watches (`sensor.church_drive_device_health`), those needing attention first. */
fun watchedDevices(entities: Map<String, EntityState>, now: Long = System.currentTimeMillis()): List<WatchedDevice> {
    val devices = entities["sensor.church_drive_device_health"]?.attributes?.optJSONObject("devices") ?: return emptyList()
    return devices.keys().asSequence().map { id ->
        val d = devices.optJSONObject(id) ?: JSONObject()
        val fixes = d.optJSONArray("fixes")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty()
        WatchedDevice(id, d.optString("name").ifBlank { id }, d.optString("status") == "ok", d.optString("reason"), heardAgo(d.optString("last_heard").takeIf { it.isNotBlank() }, now), everyText(d.optDouble("usual_gap").takeIf { !it.isNaN() }), fixes)
    }.sortedWith(compareBy({ it.ok }, { it.name })).toList()
}

fun everyText(seconds: Double?): String = when {
    seconds == null || seconds <= 0 -> ""
    seconds < 90 -> "every ${Math.round(seconds)} s"
    seconds < 5400 -> "every ${Math.round(seconds / 60)} min"
    else -> "every ${Math.round(seconds / 3600)} h"
}
