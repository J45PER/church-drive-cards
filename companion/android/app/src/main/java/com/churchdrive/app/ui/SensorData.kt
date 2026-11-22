package com.churchdrive.app.ui

import org.json.JSONArray
import org.json.JSONObject

/**
 * Sensor actions (`church_drive/sensor_actions`): what lights do when a motion or presence sensor sees someone and when the room empties.
 * [SensorInfo] is a sensor the house has, with the readings and settings of its own device; [SensorAction] is what it is set to do.
 */
data class SensorSetting(
    val entityId: String,
    val kind: String,
    val name: String,
    val min: Double?,
    val max: Double?,
    val step: Double?,
    val unit: String,
    val options: List<String>,
)

data class SensorInfo(
    val entityId: String,
    val name: String,
    val model: String,
    val platform: String,
    val batteryEntity: String?,
    val luxEntity: String?,
    val temperatureEntity: String?,
    val settings: List<SensorSetting>,
    val actionId: String?,
    /** "motion" for a motion detector (the Hue ones), "occupancy" or "presence" for a presence sensor. */
    val deviceClass: String = "",
) {
    val isMotion: Boolean get() = deviceClass == "motion"
    /** A Hue sensor's lights are set on the Hue bridge, which Church Drive can't read. */
    val isHue: Boolean get() = platform == "hue"
}

/** One time slot: from [start] to [end] (it may run past midnight) someone being seen does [onAction] (on, scene or none), at [pct] brightness (0 leaves it). */
data class SensorSlot(val start: String, val end: String, val onAction: String, val scene: String, val pct: Int)

data class SensorAction(
    val id: String,
    val sensor: String,
    val name: String,
    val lights: List<String>,
    val onAction: String,
    val scene: String,
    val offAction: String,
    val offDelay: Int,
    val dimPct: Int,
    val onlyDark: Boolean,
    val darkLux: Int,
    val luxSensor: String,
    val windowStart: String?,
    val windowEnd: String?,
    val enabled: Boolean,
    val summary: String,
    val pct: Int = 0,
    val slots: List<SensorSlot> = emptyList(),
    /** Whether the light goes off after the room empties even if it was already on when someone came in. */
    val offIfOn: Boolean = false,
    /** Every sensor of the group (the first is [sensor]); one alone is the usual case. */
    val sensors: List<String> = emptyList(),
    val luxSensors: List<String> = emptyList(),
)

data class SensorActionsData(val actions: List<SensorAction>, val sensors: List<SensorInfo>) {
    fun actionFor(sensor: SensorInfo): SensorAction? = actions.firstOrNull { sensor.entityId == it.sensor || sensor.entityId in it.sensors }

    /** The sensors that share an action with [action]'s first one, in the order the action lists them. */
    fun groupOf(action: SensorAction): List<SensorInfo> = (action.sensors.ifEmpty { listOf(action.sensor) }).mapNotNull { id -> sensors.firstOrNull { it.entityId == id } }

    /** The sensors that get a card of their own: a sensor that is only a later member of a group is shown with the group's first. */
    fun cards(): List<SensorInfo> = sensors.filter { s -> actions.none { a -> s.entityId in a.sensors && s.entityId != a.sensor } }
}

private fun JSONArray?.strings(): List<String> = this?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty()

private fun JSONObject.readingEntity(key: String): String? = optJSONObject("readings")?.optJSONObject(key)?.optString("entity_id")?.takeIf { it.isNotBlank() }

fun parseSensorActions(result: Any?): SensorActionsData? {
    val root = result as? JSONObject ?: return null
    val actions = root.optJSONArray("actions")?.let { a ->
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val w = o.optJSONObject("window")
            SensorAction(
                id = o.optString("id"), sensor = o.optString("sensor"), name = o.optString("name"), lights = o.optJSONArray("lights").strings(),
                onAction = o.optString("on_action", "on"), scene = o.optString("scene"), offAction = o.optString("off_action", "off"),
                offDelay = o.optInt("off_delay", 60), dimPct = o.optInt("dim_pct", 20), onlyDark = o.optBoolean("only_dark"),
                darkLux = o.optInt("dark_lux", 30), luxSensor = o.optString("lux_sensor"),
                windowStart = w?.optString("start")?.takeIf { it.isNotBlank() }, windowEnd = w?.optString("end")?.takeIf { it.isNotBlank() },
                enabled = o.optBoolean("enabled", true), summary = o.optString("summary"), pct = o.optInt("pct", 0), offIfOn = o.optBoolean("off_if_on"), sensors = o.optJSONArray("sensors").strings(), luxSensors = o.optJSONArray("lux_sensors").strings(),
                slots = o.optJSONArray("slots")?.let { a ->
                    (0 until a.length()).mapNotNull { j ->
                        val x = a.optJSONObject(j) ?: return@mapNotNull null
                        SensorSlot(x.optString("start"), x.optString("end"), x.optString("on_action", "on"), x.optString("scene"), x.optInt("pct", 0))
                    }
                }.orEmpty(),
            )
        }
    }.orEmpty()
    val sensors = root.optJSONArray("sensors")?.let { a ->
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val settings = o.optJSONArray("settings")?.let { s ->
                (0 until s.length()).mapNotNull { j ->
                    val x = s.optJSONObject(j) ?: return@mapNotNull null
                    SensorSetting(
                        entityId = x.optString("entity_id"), kind = x.optString("kind"), name = x.optString("name"),
                        min = x.optDouble("min").takeIf { !it.isNaN() }, max = x.optDouble("max").takeIf { !it.isNaN() },
                        step = x.optDouble("step").takeIf { !it.isNaN() }, unit = x.optString("unit").takeIf { it != "null" }.orEmpty(),
                        options = x.optJSONArray("options").strings(),
                    )
                }
            }.orEmpty()
            SensorInfo(
                entityId = o.optString("entity_id"), name = o.optString("name"), model = o.optString("model"), platform = o.optString("platform"),
                batteryEntity = o.readingEntity("battery"), luxEntity = o.readingEntity("illuminance"), temperatureEntity = o.readingEntity("temperature"),
                settings = settings, actionId = if (o.isNull("action")) null else o.optString("action").takeIf { it.isNotBlank() }, deviceClass = o.optString("device_class"),
            )
        }
    }.orEmpty()
    return SensorActionsData(actions, sensors)
}

/** The delays a "turn off after" stepper moves through, in seconds. */
val SENSOR_DELAYS = listOf(10, 20, 30, 45, 60, 90, 120, 180, 300, 600, 900, 1800, 3600)

fun stepDelay(seconds: Int, direction: Int): Int {
    val at = SENSOR_DELAYS.indexOfFirst { it >= seconds }.let { if (it < 0) SENSOR_DELAYS.lastIndex else it }
    return SENSOR_DELAYS[(at + direction).coerceIn(0, SENSOR_DELAYS.lastIndex)]
}

fun delayText(seconds: Int): String = when {
    seconds < 60 -> "$seconds s"
    seconds % 60 == 0 -> "${seconds / 60} min"
    else -> "${seconds / 60} min ${seconds % 60} s"
}

/** "Church Drive" for a sensor that has an action, "Hue app" for a Hue one that doesn't (its lights are set on the Hue bridge), else "Not set up". */
fun sensorBadge(sensor: SensorInfo, action: SensorAction?): String = when {
    action != null -> if (action.enabled) "Church Drive" else "Off"
    sensor.isHue -> "Hue app"
    else -> "Not set up"
}

fun sensorSubtitle(sensor: SensorInfo, action: SensorAction?): String = when {
    action != null -> action.summary
    sensor.isHue -> "Set in the Hue app"
    else -> "No action yet"
}

/** Why an action can't be saved yet, or null. A Hue sensor also needs the Hue bridge's own automation switched off, or both would switch the lights. */
fun sensorProblem(lights: List<String>, scene: String, onAction: String, needsHueOff: Boolean, hueOff: Boolean): String? = when {
    lights.isEmpty() -> "Choose a light first."
    onAction == "scene" && scene.isBlank() -> "Choose which scene to play."
    onAction == "hybrid" && scene.isBlank() -> "Choose which theme to play."
    needsHueOff && !hueOff -> "Switch off this sensor's automation in the Hue app, then tick the box."
    else -> null
}

/** The body of `church_drive/sensor_actions/set` ([actionId] null makes a new one). */
fun sensorBody(
    actionId: String?, sensor: String, name: String, lights: List<String>, onAction: String, scene: String, offAction: String, offDelay: Int,
    dimPct: Int, onlyDark: Boolean, darkLux: Int, luxSensor: String, windowStart: String?, windowEnd: String?, enabled: Boolean,
    pct: Int = 0, slots: List<SensorSlot> = emptyList(), offIfOn: Boolean = false, sensors: List<String> = emptyList(), luxSensors: List<String> = emptyList(),
): JSONObject = JSONObject()
    .also { if (actionId != null) it.put("action_id", actionId) else it.put("sensor", sensor) }
    .put("name", name)
    .put("lights", JSONArray(lights))
    .put("on_action", onAction)
    .put("scene", scene)
    .put("off_action", offAction)
    .put("off_delay", offDelay)
    .put("dim_pct", dimPct)
    .put("only_dark", onlyDark)
    .put("dark_lux", darkLux)
    .put("lux_sensor", luxSensor)
    .put("window", if (windowStart != null && windowEnd != null) JSONObject().put("start", windowStart).put("end", windowEnd) else JSONObject.NULL)
    .put("enabled", enabled)
    .put("pct", pct)
    .put("off_if_on", offIfOn)
    .also { if (sensors.isNotEmpty()) it.put("sensors", JSONArray(sensors)) }
    .put("lux_sensors", JSONArray(luxSensors))
    .put("slots", JSONArray(slots.map { JSONObject().put("start", it.start).put("end", it.end).put("on_action", it.onAction).put("scene", it.scene).put("pct", it.pct) }))

/** "Turn on · 100%", "Scene · Relax", "Nothing": what a time slot does. */
fun slotLookText(slot: SensorSlot, sceneName: (String) -> String): String = when (slot.onAction) {
    "scene" -> "Scene · ${sceneName(slot.scene)}"
    "hybrid" -> "Theme · ${sceneName(slot.scene)}"
    "none" -> "Nothing"
    else -> if (slot.pct > 0) "Turn on · ${slot.pct}%" else "Turn on"
}

private fun slotMinutes(time: String): Int = time.substringBefore(':').toInt() * 60 + time.substringAfter(':').toInt()

private fun slotClock(minutes: Int): String = "%02d:%02d".format(((minutes % 1440) + 1440) % 1440 / 60, ((minutes % 1440) + 1440) % 1440 % 60)

/** Minutes from [from] round to [to] on the clock; a full day when they are the same. */
private fun slotSpan(from: String, to: String): Int = ((slotMinutes(to) - slotMinutes(from)) % 1440 + 1440) % 1440

/** Whether the slots go once round the clock in order, each starting where it should: no overlap, and a slot never has no length. */
fun slotsRoundTheClock(slots: List<SensorSlot>): Boolean {
    if (slots.size < 2) return true
    val spans = slots.indices.map { i -> slotSpan(slots[i].start, slots[(i + 1) % slots.size].start) }
    return spans.all { it > 0 } && spans.sum() == 1440
}

/**
 * Moves the time one slot ends and the next begins: slot [index] now starts at [time] and the one before it ends then, so the slots always cover the
 * day with no gap (the first starts where the last ends). Null when that would put it past another slot's time.
 */
fun moveSlotBoundary(slots: List<SensorSlot>, index: Int, time: String): List<SensorSlot>? {
    if (slots.size < 2 || index !in slots.indices) return slots
    val before = (index - 1 + slots.size) % slots.size
    val moved = slots.mapIndexed { i, x -> when (i) { index -> x.copy(start = time); before -> x.copy(end = time); else -> x } }
    return moved.takeIf { slotsRoundTheClock(it.map { x -> x.copy(end = x.end) }) }
}

/** Takes slot [index] out: the one before it carries on to where it ended. Fewer than two slots is no slots, which means the sensor's own look all day. */
fun removeSlot(slots: List<SensorSlot>, index: Int): List<SensorSlot> {
    if (index !in slots.indices) return slots
    val before = (index - 1 + slots.size) % slots.size
    val out = slots.mapIndexedNotNull { i, x -> if (i == index) null else if (i == before) x.copy(end = slots[index].end) else x }
    return if (out.size < 2) emptyList() else out
}

/** Adds a slot: the first two cover the day (daytime, then evening and night); after that the longest slot is split in two and the new one is the second half. */
fun addSlot(slots: List<SensorSlot>): List<SensorSlot> {
    if (slots.size >= MAX_SENSOR_SLOTS) return slots
    if (slots.isEmpty()) return listOf(SensorSlot("06:00", "18:00", "on", "", 0), SensorSlot("18:00", "06:00", "scene", "relax", 0))
    val at = slots.indices.maxByOrNull { slotSpan(slots[it].start, slots[it].end).let { d -> if (d == 0) 1440 else d } } ?: return slots
    val slot = slots[at]
    val span = slotSpan(slot.start, slot.end).let { if (it == 0) 1440 else it }
    val mid = slotClock(slotMinutes(slot.start) + (span / 2) / 5 * 5)
    return slots.take(at) + slot.copy(end = mid) + slot.copy(start = mid) + slots.drop(at + 1)
}

/** A new slot starts as the evening, relaxed. */
fun newSensorSlot(): SensorSlot = SensorSlot("18:00", "23:00", "scene", "relax", 0)

const val MAX_SENSOR_SLOTS = 6
