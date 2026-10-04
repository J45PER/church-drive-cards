package com.churchdrive.app.ui

import com.churchdrive.app.ha.EntityState
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The entities that go with a camera, found from its name, as the dashboard's Camera card does. */
data class CameraEntities(
    val live: String?,
    val snap: String?,
    val button: String?,
    val battery: String?,
    val ding: String?,
    val motion: String?,
)

/** The newest picture of a camera: where to fetch it, when it was taken (ms) and why. */
data class CameraPicture(val id: String, val url: String, val ms: Long, val kind: String)

/**
 * camera.front_door_live_view finds camera.front_door_snapshot, button.front_door_take_snapshot,
 * sensor.front_door_battery and event.front_door_ding / _motion. [snapshot] and [button] override the guesses.
 */
fun cameraFind(entities: Map<String, EntityState>, entity: String, snapshot: String? = null, button: String? = null): CameraEntities {
    fun has(id: String?) = id?.takeIf { entities.containsKey(it) }
    val base = entity.removePrefix("camera.").replace(Regex("_(live_view|snapshot|last_recording)$"), "")
    return CameraEntities(
        live = has(entity),
        snap = has(snapshot) ?: has("camera.${base}_snapshot"),
        button = has(button) ?: has("button.${base}_take_snapshot"),
        battery = has("sensor.${base}_battery"),
        ding = has("event.${base}_ding"),
        motion = has("event.${base}_motion"),
    )
}

/** An ISO 8601 time (with `Z` or an offset like `+00:00`) as epoch milliseconds, or null. */
fun parseMillis(iso: String?): Long? {
    if (iso.isNullOrBlank()) return null
    return runCatching { Instant.parse(iso).toEpochMilli() }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(iso).toInstant().toEpochMilli() }.getOrNull()
}

/**
 * The newer of the camera's two pictures: ring-mqtt's snapshot (taken on motion, a ring or on request) and the
 * Ring integration's frame from the last recording. Null if the camera has neither.
 */
fun cameraPicture(entities: Map<String, EntityState>, found: CameraEntities): CameraPicture? {
    val pictures = mutableListOf<CameraPicture>()
    found.snap?.let { id ->
        val e = entities[id] ?: return@let
        val url = e.str("entity_picture") ?: return@let
        val ms = e.num("timestamp")?.let { (it * 1000).toLong() } ?: parseMillis(e.lastChanged) ?: 0L
        pictures += CameraPicture(id, url, ms, e.str("type") ?: "interval")
    }
    found.live?.let { id ->
        val e = entities[id] ?: return@let
        val url = e.str("entity_picture") ?: return@let
        val base = id.removePrefix("camera.").removeSuffix("_live_view")
        val activity = entities["sensor.${base}_last_activity"]
        val ms = parseMillis(activity?.state) ?: 0L
        pictures += CameraPicture(id, url, ms, if (activity?.str("category") == "ding") "ding" else "motion")
    }
    return pictures.maxByOrNull { it.ms }
}

/** "just now", "4 min", "3 h", or "Mon 21:40" for older than a day. */
fun cameraAge(ms: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val s = maxOf(0L, (now - ms) / 1000)
    return when {
        s < 60 -> "just now"
        s < 3600 -> "${s / 60} min"
        s < 86400 -> "${s / 3600} h"
        else -> DateTimeFormatter.ofPattern("EEE HH:mm", Locale.UK).withZone(zone).format(Instant.ofEpochMilli(ms))
    }
}

/** Why a picture was taken, as the chip on the tile says it. */
fun cameraKindLabel(kind: String): String = when (kind) {
    "ding" -> "Doorbell"
    "motion" -> "Motion"
    "linked" -> "Linked"
    "live" -> "Live view"
    else -> "Snapshot"
}
