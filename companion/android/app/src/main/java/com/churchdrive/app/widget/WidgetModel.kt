package com.churchdrive.app.widget

import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ui.LightRoom
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

    /** The thermostat's quick settings (Off, Heat, Eco unless the dashboard says otherwise) as buttons. */
    fun climateTiles(climate: EntityState?, entity: String, quick: List<QuickSetting>): List<WidgetTile> {
        val eco = climate?.str("preset_mode") == "eco"
        return quick.map { q ->
            val selected = when {
                q.hvacMode != null -> climate?.state == q.hvacMode && (q.hvacMode == "off" || !eco)
                q.presetMode != null -> climate?.str("preset_mode") == q.presetMode && climate.state != "off"
                else -> false
            }
            if (q.hvacMode != null) WidgetTile(quickSettingIcon(q), q.name, selected, "climate", "set_hvac_mode", entity, """{"hvac_mode":"${q.hvacMode}"}""")
            else WidgetTile(quickSettingIcon(q), q.name, selected, "climate", "set_preset_mode", entity, """{"preset_mode":"${q.presetMode}"}""")
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
