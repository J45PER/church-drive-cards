package com.churchdrive.app.ui

import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ha.Registry
import com.churchdrive.app.ha.text
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One saved doorbell press, motion or snapshot, from the Church Drive integration. [picture] and [clip] are signed links. */
data class CameraEvent(val id: String, val ts: Long, val kind: String, val picture: String?, val clip: String?)

/** The events from `church_drive/camera/events`, newest first. */
fun parseCameraEvents(result: Any?): List<CameraEvent> {
    val list = (result as? JSONObject)?.optJSONArray("events") ?: return emptyList()
    return (0 until list.length()).mapNotNull { i ->
        val o = list.optJSONObject(i) ?: return@mapNotNull null
        val id = o.text("id") ?: return@mapNotNull null
        CameraEvent(id, (o.optDouble("ts", 0.0) * 1000).toLong(), o.text("kind") ?: "interval", o.text("picture"), o.text("clip"))
    }.sortedByDescending { it.ts }
}

/**
 * The camera a card is about, by its base name (`front_door`): from a camera or event entity, or a name
 * such as a zone's. Only a base that has a `camera.<base>_live_view` counts.
 */
fun cameraBaseName(entities: Map<String, EntityState>, vararg hints: String?): String? {
    val pattern = Regex("^(?:camera|event)\\.([a-z0-9_]+?)(?:_live_view|_snapshot|_last_recording|_ding|_motion)?$")
    for (hint in hints) {
        if (hint.isNullOrBlank()) continue
        val base = pattern.find(hint)?.groupValues?.get(1)
            ?: hint.lowercase().trim().replace(Regex("[^a-z0-9]+"), "_").trim('_')
        if (base.isNotEmpty() && "camera.${base}_live_view" in entities) return base
    }
    return null
}

/**
 * The light by a camera: the one named in its card (`none` for no light), else the only light in the camera's area.
 */
fun cameraLight(entities: Map<String, EntityState>, registry: Registry, cameraId: String, configured: String?): String? {
    if (configured == "none") return null
    if (!configured.isNullOrBlank() && configured in entities) return configured
    val area = registry.areaOf(cameraId) ?: return null
    return entities.keys.filter { it.startsWith("light.") && registry.areaOf(it) == area }.singleOrNull()
}

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
    DateTimeFormatter.ofPattern("HH:mm", Locale.UK).withZone(zone).format(Instant.ofEpochMilli(ms))

/** The kinds of event there are, as the labels shown on the filter chips (Doorbell and Motion first). */
fun eventFilters(events: List<CameraEvent>): List<String> {
    val order = listOf("Doorbell", "Motion", "Linked", "Live view", "Snapshot")
    return events.map { cameraKindLabel(it.kind) }.distinct().sortedBy { order.indexOf(it).let { i -> if (i < 0) order.size else i } }
}

/** [events] of one kind (by its label), or all of them for null. */
fun filterEvents(events: List<CameraEvent>, label: String?): List<CameraEvent> =
    if (label == null) events else events.filter { cameraKindLabel(it.kind) == label }

/** The events grouped by day, in order. */
fun groupByDay(events: List<CameraEvent>, now: Long, zone: ZoneId = ZoneId.systemDefault()): List<Pair<String, List<CameraEvent>>> =
    events.groupBy { eventDay(it.ts, now, zone) }.toList()
