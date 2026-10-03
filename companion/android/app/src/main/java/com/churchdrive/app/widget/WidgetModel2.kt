package com.churchdrive.app.widget

import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ui.CLIMATE_ENTITY
import com.churchdrive.app.ui.CLIMATE_QUALITY_ENTITY
import com.churchdrive.app.ui.LightLayout
import com.churchdrive.app.ui.Tone
import com.churchdrive.app.ui.TodoItem
import com.churchdrive.app.ui.VACUUM_BATTERY
import com.churchdrive.app.ui.VACUUM_ENTITY
import com.churchdrive.app.ui.ZAPPI_MODE
import com.churchdrive.app.ui.ZAPPI_PLUG
import com.churchdrive.app.ui.ZAPPI_POWER
import com.churchdrive.app.ui.ZAPPI_SESSION
import com.churchdrive.app.ui.ZAPPI_STATUS
import com.churchdrive.app.ui.chargerModes
import com.churchdrive.app.ui.fanPresetIcon
import com.churchdrive.app.ui.fanSpeedIcon
import com.churchdrive.app.ui.fanSpeeds
import com.churchdrive.app.ui.list
import com.churchdrive.app.ui.parseMillis
import com.churchdrive.app.ui.pmTone
import com.churchdrive.app.ui.pmWord
import com.churchdrive.app.ui.presetLabel
import com.churchdrive.app.ui.purifierModeIcon
import com.churchdrive.app.ui.qualityTone
import com.churchdrive.app.ui.qualityWord
import com.churchdrive.app.ui.taskLine
import com.churchdrive.app.ui.vacuumSummary
import com.churchdrive.app.ui.vacuumTone
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

// ---------------------------------------------------------------------------------------------- People, doors, activity

data class Person(val name: String, val where: String, val home: Boolean) {
    val initial: String get() = name.trim().firstOrNull()?.uppercase() ?: "?"
}

/** Who is home, at work or out: each person, with where they are (a zone's name, "Home" or "Out"). */
fun peopleOf(entities: Map<String, EntityState>): List<Person> =
    entities.values.filter { it.entityId.startsWith("person.") && it.available }.sortedBy { it.friendlyName }.map {
        val where = when (it.state) {
            "home" -> "Home"
            "not_home" -> "Out"
            else -> presetLabel(it.state)
        }
        Person(it.friendlyName.substringBefore(' '), where, it.state == "home")
    }

/** What the doors and windows are doing: a line, its tone, and the names of what is open. */
data class DoorsStatus(val line: String, val tone: Tone, val open: List<String>)

fun doorsStatus(entities: Map<String, EntityState>): DoorsStatus {
    val kinds = setOf("door", "window", "opening", "garage_door")
    val sensors = entities.values.filter { it.entityId.startsWith("binary_sensor.") && it.available }
    val tamper = sensors.filter { it.str("device_class") == "tamper" && it.state == "on" }
    val open = sensors.filter { it.str("device_class") in kinds && it.state == "on" }.map { it.friendlyName }
    return when {
        tamper.isNotEmpty() -> DoorsStatus("Tamper", Tone.Red, open)
        open.size == 1 -> DoorsStatus("${open.first()} open", Tone.Amber, open)
        open.isNotEmpty() -> DoorsStatus("${open.size} open", Tone.Amber, open)
        else -> DoorsStatus("All closed", Tone.Green, open)
    }
}

/** The last doorbell ring or movement for a camera's base name (`front_door`), as words: "Doorbell · 17:41". */
fun lastActivity(base: String, entities: Map<String, EntityState>, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): Pair<String, Tone> {
    val ding = entities["event.${base}_ding"]?.state?.let { parseMillis(it) }
    val motion = entities["event.${base}_motion"]?.state?.let { parseMillis(it) }
    val (kind, at) = when {
        ding != null && (motion == null || ding >= motion) -> "Doorbell" to ding
        motion != null -> "Movement" to motion
        else -> return "Nothing yet" to Tone.Grey
    }
    val time = Instant.ofEpochMilli(at).atZone(zone)
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val when_ = if (time.toLocalDate() == today) time.format(DateTimeFormatter.ofPattern("HH:mm"))
    else time.format(DateTimeFormatter.ofPattern("EEE HH:mm", Locale.UK))
    val recent = now - at < 5 * 60_000L
    return "$kind · $when_" to if (kind == "Doorbell" && recent) Tone.Blue else Tone.Green
}

/** The camera base names that have a doorbell or movement event, for a picker. */
fun activityBases(entities: Map<String, EntityState>): List<String> =
    entities.keys.mapNotNull { Regex("^event\\.(.+)_(ding|motion)$").find(it)?.groupValues?.get(1) }.distinct().sorted()

// ---------------------------------------------------------------------------------------------- Alarm

private val ALARM_MODES = mapOf("armed_home" to "Home", "armed_away" to "Away", "armed_night" to "Night")

/** The alarm's line: during a delay, what is happening and the seconds left (as of the last reading), else its state. */
fun alarmStatus(alarm: EntityState): String {
    fun secs(key: String) = alarm.num(key)?.toInt()?.takeIf { it > 0 }?.let { " · ${it}s" }.orEmpty()
    return when (alarm.state) {
        "arming" -> "Arming" + (ALARM_MODES[alarm.str("targetState")]?.let { " $it" }.orEmpty()) + secs("exitSecondsLeft")
        "pending" -> "Entry delay" + secs("entrySecondsLeft")
        else -> com.churchdrive.app.ui.alarmLabel(alarm.state)
    }
}

/** Whether the alarm is counting down, so its widget should keep refreshing itself. */
fun alarmInDelay(alarm: EntityState?): Boolean = alarm?.state == "arming" || alarm?.state == "pending"

// ---------------------------------------------------------------------------------------------- Vacuum, charger, fan, purifier, blinds

data class Card(val title: String, val sub: String, val tone: Tone, val icon: String, val tiles: List<WidgetTile>)

fun vacuumCard(entities: Map<String, EntityState>): Card {
    val v = entities[VACUUM_ENTITY]
    val cleaning = v?.state == "cleaning"
    val tiles = listOf(
        WidgetTile(if (cleaning) "mdi:pause" else "mdi:play", if (cleaning) "Pause" else if (v?.state == "paused") "Resume" else "Start", cleaning, "vacuum", if (cleaning) "pause" else "start", VACUUM_ENTITY),
        WidgetTile("mdi:home-import-outline", "Dock", v?.state == "docked", "vacuum", "return_to_base", VACUUM_ENTITY),
    )
    return Card(v?.friendlyName ?: "Vacuum", vacuumSummary(v, entities[VACUUM_BATTERY]), vacuumTone(v), "mdi:robot-vacuum", tiles)
}

fun chargerCard(entities: Map<String, EntityState>): Card {
    val mode = entities[ZAPPI_MODE]
    val power = entities[ZAPPI_POWER]?.state?.toDoubleOrNull() ?: 0.0
    val charging = power > 0
    val session = entities[ZAPPI_SESSION]?.state?.toDoubleOrNull()
    val status = when {
        mode == null -> "Not connected"
        charging -> "Charging · %.1f kW".format(power / 1000)
        else -> entities[ZAPPI_PLUG]?.state ?: entities[ZAPPI_STATUS]?.state ?: "Idle"
    }
    val tiles = chargerModes(mode?.options().orEmpty()).map {
        WidgetTile(it.icon, it.name, mode?.state == it.key, "select", "select_option", ZAPPI_MODE, JSONObject().put("option", it.key).toString())
    }
    return Card("Car charger", listOfNotNull(status, session?.let { "%.1f kWh added".format(it) }).joinToString(" · "), if (charging) Tone.Teal else Tone.Grey, "mdi:ev-station", tiles)
}

fun fanCard(fan: EntityState?, entity: String): Card {
    val on = fan?.state == "on"
    val speeds = fanSpeeds(fan)
    val tiles = buildList {
        add(WidgetTile("mdi:power", "Off", !on, "fan", "turn_off", entity))
        speeds.forEachIndexed { i, s ->
            val selected = on && (if (s.preset != null) fan?.str("preset_mode") == s.preset else fan?.num("percentage")?.toInt() == s.percentage)
            if (s.preset != null) add(WidgetTile(fanSpeedIcon(i, speeds.size), "${s.n}", selected, "fan", "set_preset_mode", entity, JSONObject().put("preset_mode", s.preset).toString()))
            else add(WidgetTile(fanSpeedIcon(i, speeds.size), "${s.n}", selected, "fan", "set_percentage", entity, JSONObject().put("percentage", s.percentage).toString()))
        }
        fan?.list("preset_modes")?.filter { p -> speeds.none { it.preset == p } }?.take(2)?.forEach { p ->
            add(WidgetTile(fanPresetIcon(p), presetLabel(p), on && fan?.str("preset_mode") == p, "fan", "set_preset_mode", entity, JSONObject().put("preset_mode", p).toString()))
        }
        if (fan != null && fan.attributes.has("oscillating") && !fan.attributes.isNull("oscillating")) {
            val osc = fan.attributes.optBoolean("oscillating")
            add(WidgetTile("mdi:arrow-oscillating", "Swing", osc, "fan", "oscillate", entity, JSONObject().put("oscillating", !osc).toString()))
        }
    }
    val sub = if (!on) "Off" else fan?.str("preset_mode")?.let { presetLabel(it) } ?: fan?.num("percentage")?.let { "${it.toInt()}%" } ?: "On"
    return Card(fan?.friendlyName ?: "Fan", sub, if (on) Tone.Teal else Tone.Grey, "mdi:fan", tiles)
}

/** Whether a fan is an air purifier: it says so in its name, or a PM2.5 reading goes with it. */
fun isPurifier(fan: EntityState, entities: Map<String, EntityState>): Boolean {
    val said = Regex("purif|air_clean|air clean", RegexOption.IGNORE_CASE)
    if (said.containsMatchIn(fan.entityId) || said.containsMatchIn(fan.friendlyName)) return true
    val stem = fan.entityId.removePrefix("fan.").split('_').take(2).joinToString("_")
    return entities.keys.any { Regex("pm2_?5").containsMatchIn(it) && it.removePrefix("sensor.").startsWith(stem) }
}

/** The fans a Fan widget offers (not the purifiers), or the purifiers a Purifier widget does; every fan if the split leaves nothing. */
fun fansFor(purifiers: Boolean, entities: Map<String, EntityState>): List<EntityState> {
    val all = entities.values.filter { it.entityId.startsWith("fan.") }.sortedBy { it.friendlyName }
    return all.filter { isPurifier(it, entities) == purifiers }.ifEmpty { all }
}

/** The PM2.5 reading that belongs to a purifier: a sensor whose id says PM2.5 and starts like the purifier's own. */
fun purifierPm(fanId: String, entities: Map<String, EntityState>): EntityState? {
    val sensors = entities.values.filter { it.entityId.startsWith("sensor.") && Regex("pm2_?5").containsMatchIn(it.entityId) }
    val stem = fanId.removePrefix("fan.").split('_').take(2).joinToString("_")
    return sensors.firstOrNull { it.entityId.removePrefix("sensor.").startsWith(stem) } ?: sensors.firstOrNull()
}

fun purifierCard(fan: EntityState?, entity: String, entities: Map<String, EntityState>): Card {
    val on = fan?.state == "on"
    val pm = purifierPm(entity, entities)?.state?.toDoubleOrNull()
    val tiles = buildList {
        add(WidgetTile("mdi:power", "Off", !on, "fan", "turn_off", entity))
        (fan?.list("preset_modes").orEmpty()).take(4).forEach { m ->
            add(WidgetTile(purifierModeIcon(m), presetLabel(m), on && fan?.str("preset_mode") == m, "fan", "set_preset_mode", entity, JSONObject().put("preset_mode", m).toString()))
        }
    }
    val sub = listOfNotNull(if (!on) "Off" else fan?.str("preset_mode")?.let { presetLabel(it) } ?: "On", pm?.let { "PM2.5 ${it.toInt()} · ${pmWord(it)}" }).joinToString(" · ")
    return Card(fan?.friendlyName ?: "Air purifier", sub, pmTone(pm), "mdi:air-purifier", tiles)
}

fun coverCard(cover: EntityState?, entity: String): Card {
    val position = cover?.num("current_position")
    val tiles = listOf(
        WidgetTile("mdi:arrow-up", "Open", cover?.state == "open", "cover", "open_cover", entity),
        WidgetTile("mdi:stop", "Stop", false, "cover", "stop_cover", entity),
        WidgetTile("mdi:arrow-down", "Close", cover?.state == "closed", "cover", "close_cover", entity),
    )
    val sub = when (val s = cover?.state) {
        null -> "–"
        "open", "closed" -> presetLabel(s) + (position?.let { " · ${it.toInt()}%" } ?: "")
        else -> presetLabel(s)
    }
    return Card(cover?.friendlyName ?: "Blind", sub, if (cover?.state == "closed" || cover?.available != true) Tone.Grey else Tone.Blue, "mdi:blinds", tiles)
}

// ---------------------------------------------------------------------------------------------- Scenes

/** A scene button: its label, its colour (ARGB) and the call that plays it. */
data class SceneButton(val id: String, val group: String, val label: String, val key: String?, val tile: WidgetTile)

object SceneButtons {
    /** Every scene that can go on a widget: the house's own scenes, then each room's scenes (the scene library). */
    fun catalogue(entities: Map<String, EntityState>, layout: LightLayout): List<SceneButton> = buildList {
        entities.values.filter { it.entityId.startsWith("scene.") }.sortedBy { it.friendlyName }.forEach {
            add(SceneButton("scene:${it.entityId}", "House scenes", it.friendlyName, null, WidgetTile("mdi:lightbulb-group", it.friendlyName, false, "scene", "turn_on", it.entityId)))
        }
        allRooms(layout).forEach { room ->
            val roomName = WidgetModel.roomName(room, emptyMap(), entities)
            room.scenes.forEach { s ->
                val tile = if (s.haScene != null) WidgetTile("mdi:lightbulb-group", s.name, false, "scene", "turn_on", s.haScene)
                else WidgetTile("mdi:lightbulb-group", s.name, false, "church_drive", "apply_scene", s.target, JSONObject().put("scene", s.key).toString())
                add(SceneButton("room:${room.head}:${s.key}", roomName, "$roomName · ${s.name}", s.key, tile.copy(label = s.name)))
            }
        }
    }

    fun chosen(ids: List<String>, entities: Map<String, EntityState>, layout: LightLayout): List<SceneButton> {
        val all = catalogue(entities, layout).associateBy { it.id }
        return ids.take(8).mapNotNull { all[it] }
    }
}

// ---------------------------------------------------------------------------------------------- To-do, jobs, weather

/** The rows of a to-do list: its open tasks, soonest first, each with the words under it. */
data class TodoRow(val uid: String, val summary: String, val line: String)

fun todoRows(items: List<TodoItem>, max: Int): List<TodoRow> =
    items.filter { !it.done }.sortedWith(compareBy<TodoItem>({ it.due == null }, { it.due })).take(max)
        .map { TodoRow(it.uid, it.summary, taskLine(it)) }

/** One day of the forecast for the Weather widget. */
data class Day(val label: String, val temp: String, val condition: String)

fun forecastDays(points: List<com.churchdrive.app.ui.ForecastPoint>, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault(), max: Int = 4): List<Day> {
    val today = LocalDate.now(zone)
    return points.filter { it.timeMs >= now - 12 * 3_600_000L }.take(max).map {
        val d = Instant.ofEpochMilli(it.timeMs).atZone(zone).toLocalDate()
        Day(if (d == today) "Today" else d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.UK), it.temperature?.let { t -> "${Math.round(t)}°" } ?: "–", it.condition)
    }
}

// ---------------------------------------------------------------------------------------------- Gauges

/** A reading for a gauge: what it is, its value as text, how far round the ring goes (0 to 1) and its colour. */
data class GaugeReading(val id: String, val label: String, val value: String, val fraction: Float, val tone: Tone, val icon: String, val marker: Float? = null, val sub: String = "")

object Gauges {
    val BASIC = listOf(
        "inside" to "Inside temperature",
        "humidity" to "Humidity",
        "air" to "Air quality score",
        "heating" to "Heating (target, with − and +)",
        "outside" to "Outside temperature",
        "pm25" to "Air purifier PM2.5",
        "charger" to "Car charging power",
    )

    private val CLASSES = setOf("battery", "temperature", "humidity", "pm25", "pm10", "co2", "power", "illuminance", "carbon_dioxide")

    /** Other readings the person can choose: any sensor with a number and a usual kind of reading. */
    fun sensorChoices(entities: Map<String, EntityState>): List<Pair<String, String>> =
        entities.values.filter {
            it.entityId.startsWith("sensor.") && it.state.toDoubleOrNull() != null && it.str("device_class") in CLASSES
        }.sortedBy { it.friendlyName }.map { "entity:${it.entityId}" to it.friendlyName }

    private fun frac(v: Double, lo: Double, hi: Double) = ((v - lo) / (hi - lo)).toFloat().coerceIn(0f, 1f)

    fun compute(id: String, entities: Map<String, EntityState>): GaugeReading? {
        val climate = entities[CLIMATE_ENTITY]
        return when {
            id == "inside" -> climate?.num("current_temperature")?.let { GaugeReading(id, "Inside", "%.1f°".format(it), frac(it, 10.0, 30.0), Tone.Orange, "mdi:thermometer") }
            id == "humidity" -> climate?.num("current_humidity")?.let { GaugeReading(id, "Humidity", "${it.toInt()}%", frac(it, 0.0, 100.0), if (it in 40.0..60.0) Tone.Green else Tone.Amber, "mdi:water-percent") }
            id == "air" -> entities[CLIMATE_QUALITY_ENTITY]?.state?.toIntOrNull()?.let { GaugeReading(id, "Air quality", "$it", it / 100f, qualityTone(it), "mdi:leaf", sub = qualityWord(it)) }
            id == "heating" -> climate?.num("current_temperature")?.let {
                val lo = climate.num("min_temp") ?: 7.0
                val hi = climate.num("max_temp") ?: 25.0
                val target = climate.takeIf { c -> c.state != "off" }?.num("temperature")
                GaugeReading(id, "Heating", "%.1f°".format(it), frac(it, lo, hi), Tone.Orange, "mdi:thermostat", target?.let { t -> frac(t, lo, hi) }, target?.let { t -> "target %.1f°".format(t) } ?: "Off")
            }
            id == "outside" -> entities.values.firstOrNull { it.entityId.startsWith("weather.") }?.num("temperature")?.let { GaugeReading(id, "Outside", "%.1f°".format(it), frac(it, -5.0, 35.0), Tone.Blue, "mdi:weather-partly-cloudy") }
            id == "pm25" -> {
                val fan = entities.values.firstOrNull { it.entityId.startsWith("fan.") && purifierPm(it.entityId, entities) != null }
                val pm = fan?.let { purifierPm(it.entityId, entities) }?.state?.toDoubleOrNull() ?: purifierPm("", entities)?.state?.toDoubleOrNull()
                pm?.let { GaugeReading(id, "PM2.5", "${it.toInt()}", frac(it, 0.0, 150.0), pmTone(it), "mdi:air-purifier", sub = pmWord(it)) }
            }
            id == "charger" -> entities[ZAPPI_POWER]?.state?.toDoubleOrNull()?.let { GaugeReading(id, "Charging", "%.1f kW".format(it / 1000), frac(it, 0.0, 7400.0), if (it > 0) Tone.Teal else Tone.Grey, "mdi:ev-station") }
            id.startsWith("entity:") -> entities[id.removePrefix("entity:")]?.let { e ->
                val v = e.state.toDoubleOrNull() ?: return null
                val unit = e.str("unit_of_measurement").orEmpty()
                val text = (if (v % 1.0 == 0.0) v.toInt().toString() else "%.1f".format(v)) + unit
                when (e.str("device_class")) {
                    "battery" -> GaugeReading(id, e.friendlyName, text, frac(v, 0.0, 100.0), if (v < 20) Tone.Red else if (v < 40) Tone.Amber else Tone.Green, "mdi:battery")
                    "temperature" -> GaugeReading(id, e.friendlyName, text, frac(v, 0.0, 40.0), Tone.Orange, "mdi:thermometer")
                    "humidity" -> GaugeReading(id, e.friendlyName, text, frac(v, 0.0, 100.0), Tone.Teal, "mdi:water-percent")
                    "pm25", "pm10" -> GaugeReading(id, e.friendlyName, text, frac(v, 0.0, 150.0), pmTone(v), "mdi:air-purifier")
                    else -> GaugeReading(id, e.friendlyName, text, 0.75f, Tone.Blue, "mdi:speedometer")
                }
            }
            else -> null
        }
    }

    /** The chosen readings (2 to 4) for a cluster, in order, those that exist. */
    fun cluster(ids: List<String>, entities: Map<String, EntityState>): List<GaugeReading> =
        (ids.ifEmpty { listOf("inside", "humidity", "air") }).take(4).map { compute(it, entities) ?: missing(it, entities) }

    /** A reading that can't be had right now keeps its place, empty, so the widget always shows what was chosen. */
    fun missing(id: String, entities: Map<String, EntityState>): GaugeReading {
        val label = BASIC.firstOrNull { it.first == id }?.second?.substringBefore(" (")?.substringBefore(" temperature")
            ?: entities[id.removePrefix("entity:")]?.friendlyName ?: id
        return GaugeReading(id, label, "–", 0f, Tone.Grey, "mdi:speedometer")
    }
}
