package com.churchdrive.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.churchdrive.app.ha.CallService
import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ha.data
import kotlin.math.roundToInt

private val SCENE_NAMES = mapOf(
    "bright" to "Bright", "dimmed" to "Dimmed", "relax" to "Relax", "nightlight" to "Nightlight",
    "cool_bright" to "Cool bright", "energise" to "Energise", "cyber_fidelity" to "Cyber fidelity",
    "lake_placid" to "Lake Placid", "emerald_isle" to "Emerald isle", "soho" to "Soho",
    "phantom" to "Phantom", "city_blue" to "City Blue", "dreamy_dusk" to "Dreamy dusk",
    "spellbound" to "Spellbound",
)

/** A scene tile: the Church Drive scene [key], played on [target]. */
data class LightScene(val key: String, val target: String) {
    val name: String get() = SCENE_NAMES[key] ?: key.replace('_', ' ').replaceFirstChar { it.uppercase() }

    /** The scene select entity Church Drive keeps for a Hue room or zone, e.g. select.kitchen_scene. */
    val selectEntity: String get() = "select.${target.removePrefix("light.")}_scene"
}

/** A room: its group light on top, the zones and lights under it, and its scenes. */
data class LightRoom(val title: String, val head: String, val rows: List<String>, val scenes: List<LightScene>)

private fun scenes(vararg specs: String, room: String): List<LightScene> =
    specs.map { spec ->
        val (key, target) = spec.split("@").let { it[0] to (it.getOrNull(1) ?: room) }
        LightScene(key, target)
    }

private val WHITE = arrayOf("bright", "dimmed", "relax", "nightlight")

/** The Home page's three rooms, as on the dashboard's Quick Actions page. */
val HOME_LIGHT_ROOMS = listOf(
    LightRoom(
        "Kitchen", "light.kitchen", listOf("light.kitchen_spotlights", "light.kitchen_ambience"),
        scenes(*WHITE, "cool_bright", "energise", "soho@light.kitchen_ambience", "emerald_isle", room = "light.kitchen"),
    ),
    LightRoom(
        "Living room", "light.living_room",
        listOf("light.living_room_centris", "light.tv_lightstrip", "light.tv_table_lamp", "light.living_room_lamp"),
        scenes(
            *WHITE, "cool_bright", "soho@light.living_room_ambience", "cyber_fidelity@light.living_room_ambience",
            room = "light.living_room",
        ),
    ),
    LightRoom(
        "Middle floor", "light.middle_floor", emptyList(),
        scenes(*WHITE, room = "light.middle_floor"),
    ),
)

private fun plain(title: String, head: String) = LightRoom(title, head, emptyList(), scenes(*WHITE, room = head))

/** The Lighting page: floors of rooms, as on the dashboard's Lighting page. */
val LIGHTING_FLOORS: List<Pair<String, List<LightRoom>>> = listOf(
    "Ground Floor" to listOf(
        LightRoom(
            "Kitchen", "light.kitchen", listOf("light.kitchen_spotlights", "light.kitchen_ambience"),
            scenes(*WHITE, "cool_bright", "cyber_fidelity", "lake_placid", "emerald_isle", room = "light.kitchen"),
        ),
        LightRoom(
            "Living room", "light.living_room",
            listOf("light.living_room_table_lights", "light.living_room_ambience", "light.living_room_centris", "light.tv_lightstrip"),
            scenes(
                *WHITE, "soho@light.living_room_ambience", "phantom@light.living_room_ambience",
                "cyber_fidelity@light.living_room_ambience", room = "light.living_room",
            ),
        ),
        plain("Entrance", "light.entrance"),
    ),
    "Middle Floor" to listOf(
        plain("Middle floor", "light.middle_floor"),
        plain("Second bedroom", "light.second_bedroom"),
        plain("Spare bedroom", "light.spare_bedroom"),
    ),
    "Top Floor" to listOf(
        LightRoom(
            "Hayley's landing", "light.hayley_s_landing_main", listOf("light.hayley_s_landing_ambience"),
            scenes("cyber_fidelity@light.hayley_s_landing_ambience", room = "light.hayley_s_landing_main"),
        ),
        plain("Office", "light.office"),
        LightRoom(
            "Hayley's bedroom", "light.hayleys_bedroom", listOf("light.hayley_s_bedroom_main", "light.hayley_s_bedroom_ambiance"),
            scenes(
                "bright", "cool_bright", "dimmed@light.hayley_s_bedroom_main", "nightlight", "city_blue",
                "dreamy_dusk@light.hayley_s_bedroom_ambiance", "soho@light.hayley_s_bedroom_ambiance",
                "spellbound@light.hayley_s_bedroom_ambiance", room = "light.hayleys_bedroom",
            ),
        ),
        plain("Hayley's en suite", "light.hayley_s_en_suite"),
    ),
    "Garden" to listOf(
        LightRoom(
            "Garden", "light.garden", listOf("light.patio_light_strip", "light.outside"),
            scenes(
                *WHITE, "cool_bright", "cyber_fidelity@light.patio_ambience", "emerald_isle", "city_blue",
                room = "light.garden",
            ),
        ),
    ),
    "Front Garden" to listOf(plain("Front garden", "light.front")),
)

fun roomsOn(rooms: List<LightRoom>, entities: Map<String, EntityState>) = rooms.count { entities[it.head]?.state == "on" }

fun lightsSummary(rooms: List<LightRoom>, entities: Map<String, EntityState>): String {
    val n = roomsOn(rooms, entities)
    return if (n == 0) "All off" else "$n room${if (n > 1) "s" else ""} on"
}

/** A room that opens out to its zones, lights and scenes when tapped. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LightRoomCard(room: LightRoom, entities: Map<String, EntityState>, call: CallService) {
    val head = entities[room.head]
    val on = head?.state == "on"
    val tone = toneColors(if (on) Tone.Amber else Tone.Grey)
    var expanded by rememberSaveable(room.head) { mutableStateOf(false) }
    val expandable = room.rows.isNotEmpty() || room.scenes.isNotEmpty()

    EntityCard(tone.container, tone.onContainer) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = expandable) { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ToneIcon(Icons.Filled.Lightbulb, tone)
            Column(modifier = Modifier.weight(1f)) {
                Text(room.title, style = MaterialTheme.typography.titleMedium)
                Text(lightStatus(head), style = MaterialTheme.typography.bodyMedium)
            }
            if (expandable) {
                Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
            }
            LightSwitch(head, room.head, tone, call)
        }
        if (on) BrightnessSlider(head, room.head, tone, call)

        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                room.rows.forEach { id -> LightRow(entities[id], id, tone, call) }
                if (room.scenes.isNotEmpty()) {
                    Text("Scenes", style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        room.scenes.forEach { scene ->
                            val active = entities[scene.selectEntity]?.state.equals(scene.name, ignoreCase = true)
                            FilterChip(
                                selected = active,
                                onClick = { call("church_drive", "apply_scene", scene.target, data("scene" to scene.key)) },
                                label = { Text(scene.name, maxLines = 1) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = tone.accent,
                                    selectedLabelColor = tone.onAccent,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** One zone or light inside an opened room: its name and state, a switch, and brightness when on. */
@Composable
private fun LightRow(light: EntityState?, entityId: String, tone: ToneColors, call: CallService) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(start = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(
                Icons.Filled.Lightbulb, contentDescription = null,
                tint = if (light?.state == "on") tone.accent else tone.onContainer.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(light?.friendlyName ?: entityId, style = MaterialTheme.typography.bodyLarge)
                Text(lightStatus(light), style = MaterialTheme.typography.bodySmall)
            }
            LightSwitch(light, entityId, tone, call)
        }
        if (light?.state == "on") BrightnessSlider(light, entityId, tone, call)
    }
}

private fun lightStatus(light: EntityState?): String = when {
    light == null -> "Loading…"
    light.state != "on" -> if (light.available) "Off" else "Unavailable"
    light.num("brightness") != null -> "On · ${brightnessPct(light)}%"
    else -> "On"
}

private fun brightnessPct(light: EntityState?): Int =
    ((light?.num("brightness") ?: 255.0) / 255.0 * 100).roundToInt().coerceIn(1, 100)

@Composable
private fun LightSwitch(light: EntityState?, entityId: String, tone: ToneColors, call: CallService) {
    Switch(
        checked = light?.state == "on",
        enabled = light?.available == true,
        onCheckedChange = { want -> call("light", if (want) "turn_on" else "turn_off", entityId, data()) },
        colors = SwitchDefaults.colors(checkedTrackColor = tone.accent, checkedThumbColor = tone.onAccent),
    )
}

@Composable
private fun BrightnessSlider(light: EntityState?, entityId: String, tone: ToneColors, call: CallService) {
    // On/off-only lamps have no brightness to set.
    if (light?.num("brightness") == null) return
    val pct = brightnessPct(light)
    var dragging by remember(pct) { mutableFloatStateOf(pct.toFloat()) }
    Slider(
        value = dragging,
        onValueChange = { dragging = it },
        onValueChangeFinished = { call("light", "turn_on", entityId, data("brightness_pct" to dragging.roundToInt())) },
        valueRange = 1f..100f,
        colors = SliderDefaults.colors(
            thumbColor = tone.accent,
            activeTrackColor = tone.accent,
            inactiveTrackColor = tone.accent.copy(alpha = 0.24f),
        ),
    )
}

/** The Lighting page: a panel per floor, a card per room. */
@Composable
fun LightingPage(entities: Map<String, EntityState>, call: CallService) {
    LIGHTING_FLOORS.forEach { (floor, rooms) ->
        SectionPanel(
            floor, icon = Icons.Filled.Lightbulb, tone = Tone.Amber,
            summary = lightsSummary(rooms, entities),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                rooms.forEach { LightRoomCard(it, entities, call) }
            }
        }
    }
}
