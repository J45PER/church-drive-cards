package com.churchdrive.app.widget

import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ui.ALARM_ENTITY
import com.churchdrive.app.ui.CLIMATE_ENTITY
import com.churchdrive.app.ui.CLIMATE_QUALITY_ENTITY
import com.churchdrive.app.ui.LightLayout
import com.churchdrive.app.ui.LightRoom
import com.churchdrive.app.ui.Tone
import com.churchdrive.app.ui.VACUUM_ENTITY
import com.churchdrive.app.ui.alarmLabel
import com.churchdrive.app.ui.alarmTone
import com.churchdrive.app.ui.qualityTone
import com.churchdrive.app.ui.quickSteps
import org.json.JSONObject
import com.churchdrive.app.ui.QuickSetting
import com.churchdrive.app.ui.quickSettingIcon

/** A button on a widget: its icon, label, whether it's the current choice, and the Home Assistant service it calls. */
data class WidgetTile(val icon: String, val label: String, val selected: Boolean, val domain: String, val service: String, val entity: String, val data: String = "{}")

/** What the widgets show, worked out from the house's states (kept apart from drawing so it can be checked). */
object WidgetModel {
    /** The alarm's mode buttons, as the app's alarm card has them: Disarm, then Home, Away and Night where the panel supports them. */
    fun alarmTiles(alarm: EntityState?, entity: String): List<WidgetTile> {
        val features = alarm?.num("supported_features")?.toInt() ?: 7
        val state = alarm?.state
        return buildList {
            add(WidgetTile("mdi:shield-off-outline", "Disarm", state == "disarmed", "alarm_control_panel", "alarm_disarm", entity))
            if (features and 1 != 0) add(WidgetTile("mdi:shield-home", "Home", state == "armed_home", "alarm_control_panel", "alarm_arm_home", entity))
            if (features and 2 != 0) add(WidgetTile("mdi:shield-lock", "Away", state == "armed_away", "alarm_control_panel", "alarm_arm_away", entity))
            if (features and 4 != 0) add(WidgetTile("mdi:shield-moon", "Night", state == "armed_night", "alarm_control_panel", "alarm_arm_night", entity))
        }
    }

    /** The name a room's tile shows. */
    fun roomName(room: LightRoom, areaNames: Map<String, String>, entities: Map<String, EntityState>): String =
        room.title?.takeIf { it.isNotBlank() }
            ?: room.area?.let { areaNames[it] }
            ?: entities[room.head]?.friendlyName
            ?: room.head

    /** A tile for each of the first [max] rooms: lit when its light is on, a tap switches it. */
    fun roomTiles(rooms: List<LightRoom>, areaNames: Map<String, String>, entities: Map<String, EntityState>, max: Int = 6): List<WidgetTile> =
        rooms.filter { entities[it.head]?.available == true }.take(max).map {
            WidgetTile("mdi:lightbulb", roomName(it, areaNames, entities), entities[it.head]?.state == "on", "light", "toggle", it.head)
        }

    /**
     * The thermostat's quick settings (Off, Heat, Eco unless the dashboard says otherwise) as buttons. A button that takes
     * more than one call (Eco on a thermostat that is off: Heat first, then Eco) carries the later calls in `__then`.
     */
    fun climateTiles(climate: EntityState?, entity: String, quick: List<QuickSetting>): List<WidgetTile> {
        val eco = climate?.str("preset_mode") == "eco"
        return quick.map { q ->
            val selected = when {
                q.hvacMode != null -> climate?.state == q.hvacMode && (q.hvacMode == "off" || !eco)
                q.presetMode != null -> climate?.str("preset_mode") == q.presetMode && climate.state != "off"
                else -> false
            }
            val steps = quickSteps(climate, q)
            val first = steps.firstOrNull()
            val data = JSONObject()
            if (first != null) data.put(first.key, first.value)
            if (steps.size > 1) {
                data.put("__then", org.json.JSONArray(steps.drop(1).map { JSONObject().put("service", it.service).put("key", it.key).put("value", it.value) }))
            }
            WidgetTile(quickSettingIcon(q), q.name, selected, "climate", first?.service ?: "set_hvac_mode", entity, data.toString())
        }
    }

    /** The target after a − or + press (a step of half a degree unless the thermostat says), kept inside its limits. */
    fun nextTarget(climate: EntityState?, direction: Int): Double? {
        val target = climate?.num("temperature") ?: return null
        val step = climate.num("target_temp_step") ?: 0.5
        val low = climate.num("min_temp") ?: 7.0
        val high = climate.num("max_temp") ?: 35.0
        return (target + direction * step).coerceIn(low, high)
    }
}

/** How a widget is laid out, from how big it has been made: a thin strip, a small square, wide, or tall. */
enum class SizeClass { Strip, Square, Wide, Tall }

fun sizeClass(widthDp: Float, heightDp: Float): SizeClass = when {
    widthDp < 200f -> SizeClass.Square
    heightDp < 100f -> SizeClass.Strip
    heightDp < 190f -> SizeClass.Wide
    else -> SizeClass.Tall
}

/** A reading for the Summary widget. */
data class Stat(val key: String, val label: String, val value: String, val tone: Tone, val icon: String)

/** The stats a Summary widget can show, and how each is worked out from the house's states. */
object Stats {
    val CATALOGUE = listOf(
        "inside" to "Inside temperature",
        "humidity" to "Humidity",
        "air" to "Air quality score",
        "alarm" to "Alarm",
        "lights" to "Lights on",
        "people" to "Who is home",
        "doors" to "Doors and windows",
        "vacuum" to "Vacuum",
        "outside" to "Outside temperature",
    )
    val DEFAULT = listOf("inside", "humidity", "air", "alarm")

    private fun degrees(v: Double?) = v?.let { "%.1f°".format(it) } ?: "–"

    fun compute(key: String, entities: Map<String, EntityState>): Stat? {
        val climate = entities[CLIMATE_ENTITY]
        return when (key) {
            "inside" -> Stat(key, "Inside", degrees(climate?.num("current_temperature")), Tone.Orange, "mdi:thermometer")
            "humidity" -> Stat(key, "Humidity", climate?.num("current_humidity")?.let { "${it.toInt()}%" } ?: "–", Tone.Teal, "mdi:water-percent")
            "air" -> {
                val score = entities[CLIMATE_QUALITY_ENTITY]?.state?.toIntOrNull()
                Stat(key, "Air quality", score?.toString() ?: "–", qualityTone(score), "mdi:leaf")
            }
            "alarm" -> {
                val state = entities[ALARM_ENTITY]?.state
                Stat(key, "Alarm", alarmLabel(state).removePrefix("Armed ").ifBlank { "–" }, alarmTone(state), "mdi:shield-home")
            }
            "lights" -> {
                val on = entities.values.count { it.entityId.startsWith("light.") && it.state == "on" }
                Stat(key, "Lights", if (on == 0) "All off" else "$on on", if (on == 0) Tone.Grey else Tone.Amber, "mdi:lightbulb")
            }
            "people" -> {
                val home = entities.values.count { it.entityId.startsWith("person.") && it.state == "home" }
                Stat(key, "At home", "$home in", if (home == 0) Tone.Grey else Tone.Green, "mdi:account-group")
            }
            "doors" -> {
                val kinds = setOf("door", "window", "opening", "garage_door")
                val open = entities.values.count { it.entityId.startsWith("binary_sensor.") && it.str("device_class") in kinds && it.state == "on" }
                Stat(key, "Doors", if (open == 0) "Closed" else "$open open", if (open == 0) Tone.Green else Tone.Amber, "mdi:door-closed")
            }
            "vacuum" -> {
                val state = entities[VACUUM_ENTITY]?.state
                Stat(key, "Vacuum", state?.replace('_', ' ')?.replaceFirstChar { it.uppercase() } ?: "–", if (state == "cleaning") Tone.Blue else Tone.Grey, "mdi:robot-vacuum")
            }
            "outside" -> {
                val weather = entities.values.firstOrNull { it.entityId.startsWith("weather.") }
                Stat(key, "Outside", degrees(weather?.num("temperature")), Tone.Blue, "mdi:weather-partly-cloudy")
            }
            else -> null
        }
    }

    /** The chosen stats (up to 4), or the usual four when none has been chosen. */
    fun chosen(keys: List<String>, entities: Map<String, EntityState>): List<Stat> =
        (keys.ifEmpty { DEFAULT }).take(4).mapNotNull { compute(it, entities) }
}

/** A button of the Shortcuts widget: what it's called, its icon, group, and the service it calls. */
data class Shortcut(val id: String, val group: String, val label: String, val icon: String, val tile: WidgetTile)

object Shortcuts {
    val DEFAULT = listOf("alarm:home", "lights:off", "vacuum:start", "alarm:disarm")

    /** Every button the widget can have: alarm modes, all lights off, each room, the house's scenes and the vacuum. */
    fun catalogue(entities: Map<String, EntityState>, layout: LightLayout): List<Shortcut> = buildList {
        fun item(id: String, group: String, label: String, icon: String, domain: String, service: String, entity: String, data: String = "{}") =
            add(Shortcut(id, group, label, icon, WidgetTile(icon, label, false, domain, service, entity, data)))
        item("alarm:disarm", "Alarm", "Disarm", "mdi:shield-off-outline", "alarm_control_panel", "alarm_disarm", ALARM_ENTITY)
        item("alarm:home", "Alarm", "Arm home", "mdi:shield-home", "alarm_control_panel", "alarm_arm_home", ALARM_ENTITY)
        item("alarm:away", "Alarm", "Arm away", "mdi:shield-lock", "alarm_control_panel", "alarm_arm_away", ALARM_ENTITY)
        item("alarm:night", "Alarm", "Arm night", "mdi:shield-moon", "alarm_control_panel", "alarm_arm_night", ALARM_ENTITY)
        item("lights:off", "Lights", "All off", "mdi:lightbulb", "light", "turn_off", "all")
        allRooms(layout).forEach { item("room:${it.head}", "Lights", WidgetModel.roomName(it, emptyMap(), entities), "mdi:lightbulb", "light", "toggle", it.head) }
        entities.values.filter { it.entityId.startsWith("scene.") }.sortedBy { it.friendlyName }.forEach {
            item("scene:${it.entityId}", "Scenes", it.friendlyName, "mdi:lightbulb-group", "scene", "turn_on", it.entityId)
        }
        item("vacuum:start", "Jobs", "Hoover", "mdi:robot-vacuum", "vacuum", "start", VACUUM_ENTITY)
        item("vacuum:dock", "Jobs", "Dock", "mdi:home", "vacuum", "return_to_base", VACUUM_ENTITY)
    }

    /** The chosen buttons (up to 8, in order), or the usual four when none has been chosen. */
    fun chosen(ids: List<String>, entities: Map<String, EntityState>, layout: LightLayout): List<Shortcut> {
        val all = catalogue(entities, layout).associateBy { it.id }
        return (ids.ifEmpty { DEFAULT }).take(8).mapNotNull { all[it] }
    }
}

/** Every light room the dashboard knows, the Home panel's and each floor's, once each. */
fun allRooms(layout: LightLayout): List<LightRoom> = (layout.home + layout.floors.flatMap { it.second }).distinctBy { it.head }

/** The rooms a Lights widget was set up for, in the order chosen; the Home panel's rooms when none has been chosen. */
fun chosenRooms(layout: LightLayout, heads: List<String>): List<LightRoom> {
    if (heads.isEmpty()) return layout.home
    val all = allRooms(layout).associateBy { it.head }
    return heads.mapNotNull { all[it] }
}
