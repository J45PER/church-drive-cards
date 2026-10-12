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
    /** Wi-Fi signal (`sensor.<x>_wireless` in dBm, or the Ring integration's `_wifi_signal_strength` / `_category`). */
    val signal: String? = null,
    /** `binary_sensor.<x>_ding`: on while the doorbell is ringing. */
    val ringSensor: String? = null,
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
        signal = has("sensor.${base}_wireless") ?: has("sensor.${base}_wifi_signal_strength") ?: has("sensor.${base}_wifi_signal_category"),
        ringSensor = has("binary_sensor.${base}_ding"),
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

/** Wi-Fi signal as 0-4 bars (from dBm, or Ring's category), or -1 when unknown. */
fun signalBars(state: String?, category: String? = null): Int {
    val n = state?.toDoubleOrNull()
    if (n != null && n < 0) return when {
        n >= -55 -> 4
        n >= -65 -> 3
        n >= -75 -> 2
        else -> 1
    }
    return when ((category ?: state).orEmpty().lowercase()) {
        "excellent" -> 4
        "good" -> 3
        "fair" -> 2
        "poor", "bad" -> 1
        else -> -1
    }
}

fun signalIconName(bars: Int): String = if (bars < 0) "mdi:wifi-strength-off-outline" else listOf("mdi:wifi-strength-outline", "mdi:wifi-strength-1", "mdi:wifi-strength-2", "mdi:wifi-strength-3", "mdi:wifi-strength-4")[bars.coerceIn(0, 4)]

/** mdi:battery-10 ... mdi:battery, in steps of 10. */
fun batteryIconName(pct: Double?): String {
    if (pct == null) return "mdi:battery-unknown"
    val p = pct.toInt().coerceIn(0, 100)
    return when {
        p >= 95 -> "mdi:battery"
        p < 5 -> "mdi:battery-outline"
        else -> "mdi:battery-${p / 10 * 10}"
    }
}

/**
 * Is the doorbell ringing? Its binary sensor is on, or its ding event fired in the last [withinMs]. [answeredAt] (ms) ends it
 * once someone has answered from here.
 */
fun isRinging(dingBinary: String?, dingEvent: String?, now: Long, answeredAt: Long = 0L, withinMs: Long = 45_000L): Boolean {
    // Still ringing unless answered from here, and then only again for a newer ring.
    if (dingBinary == "on") return answeredAt == 0L || (parseMillis(dingEvent) ?: 0L) > answeredAt
    val ms = parseMillis(dingEvent) ?: return false
    return now - ms < withinMs && ms > answeredAt
}

/** The tile's shape (width / height) from the picture's own, kept between 3:4 and 16:9 so a tall doorbell still fits. */
fun tileAspect(width: Int, height: Int): Float =
    if (width <= 0 || height <= 0) 16f / 9f else (width.toFloat() / height).coerceIn(3f / 4f, 16f / 9f)

/** A notification's link to a camera: `church-drive-camera:camera.front_door_live_view`, with `?answer` to open it answering. */
data class CameraLink(val entityId: String, val answer: Boolean)

fun cameraFromLink(url: String?): CameraLink? {
    val link = url?.trim() ?: return null
    val prefix = "church-drive-camera:"
    if (!link.startsWith(prefix)) return null
    val rest = link.removePrefix(prefix)
    val id = rest.substringBefore('?')
    if (!Regex("^camera\\.[a-z0-9_]+$").matches(id)) return null
    return CameraLink(id, "answer" in rest.substringAfter('?', ""))
}
