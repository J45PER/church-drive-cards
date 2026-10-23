package com.churchdrive.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.churchdrive.app.ha.CallService
import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ha.data

/** Gregg's switches shown under Settings: label, entity. */
private val CLEANING_SWITCHES = listOf(
    "Empty the dust bin by itself" to "switch.gregg_auto_empty",
    "Wash the mop by itself" to "switch.gregg_auto_wash",
    "Do not disturb" to "switch.gregg_do_not_disturb",
    "Child lock" to "switch.gregg_child_lock",
)

/** The parts that wear out: name, the sensor that says how long is left, its reset button and the icon it is shown with. */
private class Part(val name: String, val sensor: String, val reset: String, val icon: String)

private val CLEANING_PARTS = listOf(
    Part("Filter", "sensor.gregg_filter_remaining", "button.gregg_reset_filter", "mdi:air-filter"),
    Part("Rolling brush", "sensor.gregg_rolling_brush_remaining", "button.gregg_reset_rolling_brush", "phu:vac_mbrush"),
    Part("Side brush", "sensor.gregg_side_brush_remaining", "button.gregg_reset_side_brush", "phu:vac_sbrush"),
    Part("Mopping cloth", "sensor.gregg_mopping_cloth_remaining", "button.gregg_reset_mopping_cloth", "mdi:texture-box"),
    Part("Sensors", "sensor.gregg_sensor_remaining", "button.gregg_reset_sensors", "mdi:radar"),
    Part("Cleaning tray", "sensor.gregg_cleaning_tray_remaining", "button.gregg_reset_cleaning_tray", "mdi:tray-full"),
)

/**
 * The Cleaning page, drawn by the app from Gregg's own entities so every part of it looks the same: one card at the top for what he is
 * doing (his status icon, the battery, the last clean and the buttons), then how he cleans (the mode, suction and water as the house's
 * icons), the dock, the parts that wear out and his switches. Each group is a panel with its title, as on the other pages.
 */
@Composable
fun CleaningPage(entities: Map<String, EntityState>, call: CallService) {
    val vac = entities[VACUUM_ENTITY]
    val battery = entities[VACUUM_BATTERY]
    val tone = toneColors(vacuumTone(vac))
    val enabled = vac?.available == true
    val cleaning = vac?.state == "cleaning"
    val state = greggState(vac, battery)
    val pct = battery?.state?.toDoubleOrNull()
    // Reset asks twice: the first tap arms it for a few seconds, the second does it.
    var armed by remember { mutableStateOf<Part?>(null) }
    androidx.compose.runtime.LaunchedEffect(armed) { if (armed != null) { kotlinx.coroutines.delay(4_000); armed = null } }

    fun press(id: String) = call("button", "press", id, data())
    fun pick(id: String, option: String) = call("select", "select_option", id, data("option" to option))

    // ---- Gregg: what he is doing
    SectionPanel("Gregg", tone = vacuumTone(vac), summary = vacuumSummary(vac, battery), iconSlot = { GreggIcon(GreggState.Idle, it.accent, 36.dp) }) {
        EntityCard(tone.container, tone.onContainer, deviceEntity = VACUUM_ENTITY) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                GreggIcon(state, tone.accent, 72.dp, pct)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(state.word(), style = MaterialTheme.typography.headlineSmall)
                    val area = entities["sensor.gregg_cleaning_area"]?.state?.toDoubleOrNull()
                    val minutes = entities["sensor.gregg_cleaning_time"]?.state?.toDoubleOrNull()
                    val last = listOfNotNull(area?.let { "${Math.round(it)} m²" }, minutes?.let { "${Math.round(it)} min" }).joinToString(" · ")
                    if (last.isNotBlank()) Text("Last clean $last", style = MaterialTheme.typography.bodyMedium, color = tone.onContainer.copy(alpha = 0.8f))
                    entities["sensor.gregg_error_message"]?.state?.takeIf { it.isNotBlank() && it != "unknown" && it != "unavailable" && it != "None" }?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = toneColors(Tone.Red).accent)
                    }
                }
                Text(pct?.let { "${Math.round(it)}%" } ?: "–", style = MaterialTheme.typography.headlineMedium, color = tone.accent)
            }
            Box(Modifier.fillMaxWidth().height(8.dp).background(Color.Gray.copy(alpha = 0.25f), RoundedCornerShape(4.dp))) {
                Box(Modifier.fillMaxWidth(((pct ?: 0.0) / 100.0).coerceIn(0.0, 1.0).toFloat()).fillMaxHeight().background(tone.accent, RoundedCornerShape(4.dp)))
            }
            IconRow(
                listOf(
                    IconItem(if (cleaning) "cd:pause" else "cd:play", if (cleaning) "Pause" else if (vac?.state == "paused") "Resume" else "Start", true) {
                        call("vacuum", if (cleaning) "pause" else "start", VACUUM_ENTITY, data())
                    },
                    IconItem("cd:stop", "Stop", false) { call("vacuum", "stop", VACUUM_ENTITY, data()) },
                    IconItem("cd:home", "Dock", false) { call("vacuum", "return_to_base", VACUUM_ENTITY, data()) },
                ),
                tone, tone.onContainer, enabled,
            )
        }
    }

    // ---- How he cleans
    val levels = listOf(
        "Mode" to "select.gregg_cleaning_mode",
        "Suction" to "select.gregg_suction_level",
        "Water" to "select.gregg_water_level",
    )
    val plain = listOf("Cleaning intensity" to "select.gregg_cleaning_intensity", "Mop intensity" to "select.gregg_mop_intensity")
    if (levels.any { entities[it.second] != null } || plain.any { entities[it.second] != null }) {
        val grey = toneColors(Tone.Grey)
        SectionPanel("How he cleans", iconName = "mdi:tune-variant", tone = Tone.Blue) {
            EntityCard(grey.container, grey.onContainer) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    for ((label, id) in levels) {
                        val e = entities[id] ?: continue
                        val items = e.options().map { it to greggLevelIcon(id, it) }
                        if (items.isEmpty()) continue
                        Label(label)
                        if (items.all { it.second != null }) TileRow(
                            items.map { (o, icon) -> TileItem(icon.orEmpty(), o.replace('_', ' '), o == e.state) { pick(id, o) } },
                            toneColors(Tone.Blue), grey.onContainer, e.available,
                        ) else OptionRow(e.options(), e.state, toneColors(Tone.Blue), e.available) { pick(id, it) }
                    }
                    for ((label, id) in plain) {
                        val e = entities[id] ?: continue
                        if (e.options().isEmpty()) continue
                        Label(label)
                        OptionRow(e.options(), e.state, toneColors(Tone.Blue), e.available) { pick(id, it) }
                    }
                }
            }
        }
    }

    // ---- The dock
    val status = entities["sensor.gregg_dock_status"]?.state?.takeIf { it != "unknown" && it != "unavailable" }.orEmpty()
    val find = entities["switch.gregg_find_robot"]
    val dockButtons = listOf("button.gregg_empty_dust_bin", "button.gregg_wash_mop", "button.gregg_dry_mop", "button.gregg_stop_dry_mop")
    if (dockButtons.any { entities[it] != null } || find != null) {
        val grey = toneColors(Tone.Grey)
        val doing = status.lowercase()
        SectionPanel("Dock", iconName = "cd:home", tone = Tone.Teal, summary = status.ifBlank { null }) {
            EntityCard(grey.container, grey.onContainer) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    val tiles = buildList {
                        if (entities["button.gregg_empty_dust_bin"] != null) add(TileItem("cd:empty", "Empty bin", doing.contains("empty") || doing.contains("dust")) { press("button.gregg_empty_dust_bin") })
                        if (entities["button.gregg_wash_mop"] != null) add(TileItem("cd:wash", "Wash mop", doing.contains("wash")) { press("button.gregg_wash_mop") })
                        if (entities["button.gregg_dry_mop"] != null) add(TileItem("cd:dry", "Dry mop", doing.contains("dry")) { press("button.gregg_dry_mop") })
                        if (entities["button.gregg_stop_dry_mop"] != null && doing.contains("dry")) add(TileItem("cd:stop", "Stop drying", false) { press("button.gregg_stop_dry_mop") })
                        if (find != null) add(TileItem("cd:find", "Find Gregg", find.state == "on") { call("switch", "toggle", find.entityId, data()) })
                    }
                    TileRow(tiles, toneColors(Tone.Teal), grey.onContainer, true)
                    for ((label, id) in listOf("Dry for" to "select.gregg_dry_duration", "Empty the bin" to "select.gregg_auto_empty_mode")) {
                        val e = entities[id] ?: continue
                        if (e.options().isEmpty()) continue
                        Label(label)
                        OptionRow(e.options(), e.state, toneColors(Tone.Teal), e.available) { pick(id, it) }
                    }
                }
            }
        }
    }

    // ---- Parts that wear out
    val parts = CLEANING_PARTS.filter { entities[it.sensor] != null }
    if (parts.isNotEmpty()) {
        val grey = toneColors(Tone.Grey)
        SectionPanel("Parts", iconName = "mdi:wrench-outline", tone = Tone.Amber, summary = "Time left") {
            EntityCard(grey.container, grey.onContainer) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (p in parts) {
                        val e = entities[p.sensor]
                        val left = e?.state?.takeIf { it != "unknown" && it != "unavailable" }
                        val unit = e?.str("unit_of_measurement") ?: "h"
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ToneIconName(e?.str("icon")?.takeIf { it.isNotBlank() } ?: p.icon, Icons.Filled.Info, toneColors(Tone.Amber), 40)
                            Text(p.name, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1)
                            Text(left?.let { "${it.toDoubleOrNull()?.let { n -> Math.round(n).toString() } ?: it} $unit" } ?: "–", style = MaterialTheme.typography.titleMedium)
                            if (entities[p.reset] != null) {
                                val sure = armed == p
                                val red = toneColors(Tone.Red)
                                val colour = if (sure) red.accent else grey.onContainer.copy(alpha = 0.8f)
                                Text(
                                    if (sure) "Tap to reset" else "Reset", style = MaterialTheme.typography.labelLarge, color = colour,
                                    modifier = Modifier.clip(CircleShape).background(colour.copy(alpha = 0.14f)).clickable { if (sure) { press(p.reset); armed = null } else armed = p }
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ---- Switches
    val switches = CLEANING_SWITCHES.filter { entities[it.second]?.available == true }
    if (switches.isNotEmpty()) {
        val grey = toneColors(Tone.Grey)
        SectionPanel("Settings", iconName = "mdi:cog-outline", tone = Tone.Grey) {
            EntityCard(grey.container, grey.onContainer) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for ((label, id) in switches) {
                        val on = entities[id]?.state == "on"
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            Switch(checked = on, onCheckedChange = { call("switch", if (it) "turn_on" else "turn_off", id, data()) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
