package com.churchdrive.app.ui

import com.churchdrive.app.ha.EntityState
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// The Security Zone card (an outside or entry zone: Front Garden, Entrance, Driveway...), without any drawing, so
// it can be tested. It follows the dashboard card's rules (security-zone-card.js): the state word, the strip of
// the last hours, the "last events" line, and the batteries.

enum class ZoneKind { Light, Motion, Door, Tamper, Ring }

/** How things look for the zone: normal, something moving, a door open, a tamper (or open while armed), or unavailable. */
enum class ZoneLevel { Ok, Motion, Open, Tamper, Off }

/** What a Security Zone card on the dashboard is set up with. */
data class ZoneConfig(
    val name: String,
    val door: String?,
    val motion: List<String>,
    val doorbell: String?,
    val tamper: List<String>,
    val light: String?,
    val lightName: String?,
    /** Entity and optional name, for each of up to four batteries. */
    val batteries: List<Pair<String, String?>>,
    val alarm: String?,
    val hours: Int,
    val ticks: Boolean,
    val showLast: Boolean,
) {
    /** Everything whose history the strip needs. */
    fun historyIds(): List<String> = listOfNotNull(door) + motion + listOfNotNull(doorbell) + tamper + listOfNotNull(light)
}

fun zoneConfig(c: JSONObject): ZoneConfig {
    fun list(key: String): List<String> = c.optJSONArray(key)?.let { a -> (0 until a.length()).mapNotNull { a.optString(it).takeIf { s -> s.isNotBlank() } } }.orEmpty()
    fun text(key: String): String? = c.optString(key).takeIf { it.isNotBlank() }
    return ZoneConfig(
        name = text("name") ?: "Zone",
        door = text("door_entity"),
        motion = list("motion_entities"),
        doorbell = text("doorbell_entity"),
        tamper = list("tamper_entities"),
        light = text("light_entity"),
        lightName = text("light_name"),
        batteries = (1..4).mapNotNull { n -> text("battery_$n")?.let { it to text("battery_${n}_name") } },
        alarm = text("alarm_entity"),
        hours = text("hours")?.toIntOrNull() ?: c.optInt("hours", 12).takeIf { it > 0 } ?: 12,
        ticks = text("strip") == "ticks",
        showLast = !c.has("show_last") || c.optBoolean("show_last", true),
    )
}

data class ZoneTrack(val kind: ZoneKind, val spans: List<Pair<Long, Long>>)

data class ZoneBattery(val icon: String, val name: String, val level: Double?)

data class ZoneView(
    val tracks: List<ZoneTrack>,
    val word: String,
    val chip: String,
    val level: ZoneLevel,
    val warn: String,
    val last: String,
    val bats: List<ZoneBattery>,
    /** The zone's light: its name and whether it's on. */
    val light: Pair<String, Boolean>?,
)

private const val RECENT = 120_000L // "just now"
private const val EVENT_SPAN = 30_000L
const val LOW_BATTERY = 25

/** A series of (time in ms, state): history, plus the current state if it has changed since. */
private fun series(id: String, history: Map<String, List<Pair<Long, String>>>, entities: Map<String, EntityState>): List<Pair<Long, String>> {
    val hist = history[id].orEmpty()
    val last = hist.lastOrNull()?.first ?: 0L
    val e = entities[id]
    val t = parseMillis(e?.lastChanged)
    return if (e != null && t != null && t > last) hist + (t to e.state) else hist
}

/** The spans of time a sensor was "on" (an event entity gives a short span per event; its state is the event's time). */
fun spansOf(id: String, series: List<Pair<Long, String>>, from: Long, now: Long): List<Pair<Long, Long>> {
    val out = mutableListOf<Pair<Long, Long>>()
    if (id.startsWith("event.")) {
        val seen = mutableSetOf<Long>()
        for ((_, s) in series) {
            val t = parseMillis(s) ?: continue
            if (t in from..now && seen.add(t)) out += t to t + EVENT_SPAN
        }
        return out.sortedBy { it.first }
    }
    var on: Long? = null
    for ((t, s) in series) {
        val started = on
        if (s == "on" && started == null) on = max(t, from)
        else if (s != "on" && started != null) {
            if (t >= from) out += started to max(t, started + EVENT_SPAN)
            on = null
        }
    }
    on?.let { out += it to now }
    return out
}

/** "14:05" for a time in ms. */
fun clockMs(ms: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    DateTimeFormatter.ofPattern("HH:mm", Locale.UK).withZone(zone).format(Instant.ofEpochMilli(ms))

/** "14:05" today, "yesterday 14:05", else "Mon 14:05". */
fun whenMs(ms: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val day = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
    val today: LocalDate = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    return when (day) {
        today -> clockMs(ms, zone)
        today.minusDays(1) -> "yesterday ${clockMs(ms, zone)}"
        else -> DateTimeFormatter.ofPattern("EEE", Locale.UK).withZone(zone).format(Instant.ofEpochMilli(ms)) + " " + clockMs(ms, zone)
    }
}

/**
 * Everything the card shows for a zone, from the current states and the history of its sensors.
 * [history] is each entity's (time in ms, state) list over the strip's hours.
 */
fun zoneView(
    cfg: ZoneConfig,
    entities: Map<String, EntityState>,
    history: Map<String, List<Pair<Long, String>>>,
    now: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): ZoneView {
    val from = now - cfg.hours * 3_600_000L
    fun st(id: String?): EntityState? = id?.let { entities[it] }
    fun spans(id: String, since: Long = from) = spansOf(id, series(id, history, entities), since, now)

    val tracks = mutableListOf<ZoneTrack>()
    cfg.light?.let { tracks += ZoneTrack(ZoneKind.Light, spans(it)) }
    cfg.motion.forEach { tracks += ZoneTrack(ZoneKind.Motion, spans(it)) }
    cfg.door?.let { tracks += ZoneTrack(ZoneKind.Door, spans(it)) }
    cfg.tamper.forEach { tracks += ZoneTrack(ZoneKind.Tamper, spans(it)) }
    cfg.doorbell?.let { tracks += ZoneTrack(ZoneKind.Ring, spans(it)) }

    val door = st(cfg.door)
    val open = door?.state == "on"
    val tampered = cfg.tamper.mapNotNull { st(it) }.filter { it.state == "on" }
    val armed = st(cfg.alarm)?.state?.startsWith("armed") == true

    // The latest motion and ring times, from the current states and the history.
    fun lastOf(id: String): Long? {
        val e = st(id) ?: return null
        if (id.startsWith("event.")) return parseMillis(e.state)
        if (e.state == "on") return now
        return spans(id, 0L).lastOrNull()?.first
    }
    val motionAt = cfg.motion.mapNotNull { lastOf(it) }.maxOrNull() ?: 0L
    val ringAt = cfg.doorbell?.let { lastOf(it) }
    val moving = motionAt > 0 && now - motionAt < RECENT
    val ringing = ringAt != null && ringAt > 0 && now - ringAt < RECENT
    val sensors = listOfNotNull(cfg.door) + cfg.motion + listOfNotNull(cfg.doorbell)
    val unavailable = sensors.isNotEmpty() && sensors.all { st(it) == null || st(it)?.state == "unavailable" }

    var level = ZoneLevel.Ok
    var word = if (door != null) "Closed" else "Quiet"
    var chip = word
    var warn = ""
    if (moving) { level = ZoneLevel.Motion; word = "Motion just now"; chip = "Motion" }
    if (ringing) { level = ZoneLevel.Motion; word = "Doorbell just now"; chip = "Doorbell" }
    if (open && door != null) {
        val mins = max(0L, ((now - (parseMillis(door.lastChanged) ?: now)) / 60_000.0).roundToInt().toLong())
        level = if (armed) ZoneLevel.Tamper else ZoneLevel.Open
        word = if (armed) "Open while armed" else if (mins < 1) "Just opened" else "Open $mins min"
        chip = "Open"
    }
    if (tampered.isNotEmpty()) {
        level = ZoneLevel.Tamper
        word = "Tamper"
        chip = "Tampered"
        val at = parseMillis(tampered[0].lastChanged)?.let { clockMs(it, zone) }.orEmpty()
        warn = "${tampered.joinToString(", ") { it.str("friendly_name") ?: it.entityId }} tampered with at $at"
    }
    if (unavailable) { level = ZoneLevel.Off; word = "Unavailable"; chip = "Unavailable" }

    val last = mutableListOf<String>()
    if (door != null && door.state != "unavailable") {
        val since = parseMillis(door.lastChanged)
        last += "${if (open) "Open" else "Closed"} since ${since?.let { whenMs(it, now, zone) }.orEmpty()}"
    }
    if (motionAt > 0) last += "${if (last.isNotEmpty()) "motion" else "Motion"} ${if (now - motionAt < RECENT) "just now" else whenMs(motionAt, now, zone)}"
    if (ringAt != null && ringAt > 0) last += "${if (last.isNotEmpty()) "rang" else "Rang"} ${whenMs(ringAt, now, zone)}"

    val bats = cfg.batteries.map { (id, name) ->
        val e = st(id)
        ZoneBattery(
            icon = e?.str("icon") ?: "mdi:battery",
            name = name ?: e?.friendlyName?.replace(Regex("\\s*battery$", RegexOption.IGNORE_CASE), "") ?: id,
            level = e?.state?.toDoubleOrNull(),
        )
    }
    val lightEntity = st(cfg.light)
    val light = lightEntity?.let { (cfg.lightName ?: it.friendlyName) to (it.state == "on") }
    return ZoneView(tracks, word, chip, level, warn, last.joinToString(" · "), bats, light)
}

/** One bar of the strip's "busy periods" style: which slot, what kind of event wins it, and how tall (0 to 1). */
data class StripBar(val slot: Int, val kind: ZoneKind, val height: Float)

/**
 * The strip's bars: the hours split into [slots] slots, each as tall as it was busy. A slot takes the colour
 * of the most important thing in it (tamper, then doorbell, then door, then motion). The light isn't a bar.
 */
fun stripBars(tracks: List<ZoneTrack>, from: Long, span: Long, slots: Int = 24): List<StripBar> {
    val slot = span.toDouble() / slots
    val counts = Array(slots) { mutableMapOf<ZoneKind, Int>() }
    for (track in tracks) {
        if (track.kind == ZoneKind.Light) continue
        for ((a, _) in track.spans) {
            if (a < from) continue
            val k = min(slots - 1, ((a - from) / slot).toInt())
            counts[k][track.kind] = (counts[k][track.kind] ?: 0) + 1
        }
    }
    val totals = counts.map { it.values.sum() }
    val busiest = max(1, totals.maxOrNull() ?: 1)
    return counts.mapIndexedNotNull { k, c ->
        if (totals[k] == 0) return@mapIndexedNotNull null
        val kind = when {
            c.containsKey(ZoneKind.Tamper) -> ZoneKind.Tamper
            c.containsKey(ZoneKind.Ring) -> ZoneKind.Ring
            c.containsKey(ZoneKind.Door) -> ZoneKind.Door
            else -> ZoneKind.Motion
        }
        StripBar(k, kind, max(0.2f, totals[k].toFloat() / busiest * 0.85f))
    }
}

/**
 * Home Assistant's `history/history_during_period` result as entity to (time in ms, state). Each state is
 * `{"s": state, "lu": last updated in seconds}` (or the long names `state` and `last_updated`).
 */
fun parseHistory(result: Any?): Map<String, List<Pair<Long, String>>> {
    val o = result as? JSONObject ?: return emptyMap()
    val out = mutableMapOf<String, List<Pair<Long, String>>>()
    for (id in o.keys()) {
        val a = o.optJSONArray(id) ?: continue
        out[id] = (0 until a.length()).mapNotNull { i ->
            val p = a.optJSONObject(i) ?: return@mapNotNull null
            val state = if (p.has("s")) p.optString("s") else p.optString("state", "")
            val ms = when {
                p.has("lu") -> (p.optDouble("lu") * 1000).toLong()
                p.has("last_updated") -> parseMillis(p.optString("last_updated"))
                p.has("lc") -> (p.optDouble("lc") * 1000).toLong()
                else -> null
            } ?: return@mapNotNull null
            ms to state
        }
    }
    return out
}
