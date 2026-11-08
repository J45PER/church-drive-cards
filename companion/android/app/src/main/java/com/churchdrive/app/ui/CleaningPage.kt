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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.churchdrive.app.ha.CallService
import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ha.data

/** Gregg's switches shown under Settings: label, entity. (Washing the mop by itself and the child lock are left out: not wanted here.) */
private val CLEANING_SWITCHES = listOf(
    "Empty the dust bin by itself" to "switch.gregg_auto_empty",
    "Do not disturb" to "switch.gregg_do_not_disturb",
)

/**
 * The parts that wear out: name, the sensor that says how long is left, its reset button, the icon it is shown with and how many hours a new
 * one lasts (Home Assistant only says what is left; these are worked out from his own figures, so the bar is a good guide, not exact).
 */
private class Part(val name: String, val sensor: String, val reset: String, val icon: String, val lifeHours: Double)

private val CLEANING_PARTS = listOf(
    Part("Filter", "sensor.gregg_filter_remaining", "button.gregg_reset_filter", "mdi:air-filter", 360.0),
    Part("Rolling brush", "sensor.gregg_rolling_brush_remaining", "button.gregg_reset_rolling_brush", "phu:vac_mbrush", 360.0),
    Part("Side brush", "sensor.gregg_side_brush_remaining", "button.gregg_reset_side_brush", "phu:vac_sbrush", 180.0),
    Part("Mopping cloth", "sensor.gregg_mopping_cloth_remaining", "button.gregg_reset_mopping_cloth", "mdi:texture-box", 180.0),
    Part("Sensors", "sensor.gregg_sensor_remaining", "button.gregg_reset_sensors", "mdi:radar", 30.0),
    Part("Cleaning tray", "sensor.gregg_cleaning_tray_remaining", "button.gregg_reset_cleaning_tray", "mdi:tray-full", 30.0),
)

/** How much of its life is left, 0 to 1, for a part with [left] hours of [life]. */
fun partLifeLeft(left: Double?, life: Double): Float = if (left == null || life <= 0.0) 0f else (left / life).coerceIn(0.0, 1.0).toFloat()

/**
 * A part's colour as its life runs out, a smooth gradient and not steps: blue while it is nearly new (above 80% left), then green (60%), yellow
 * (35%) and red (10% and below), blending between each pair.
 */
fun partColour(fraction: Float, blue: Color, green: Color, yellow: Color, red: Color): Color {
    val stops = listOf(0.8f to blue, 0.6f to green, 0.35f to yellow, 0.1f to red)
    val f = fraction.coerceIn(0f, 1f)
    if (f >= stops.first().first) return stops.first().second
    for (i in 1 until stops.size) {
        val (upper, upperColour) = stops[i - 1]
        val (lower, lowerColour) = stops[i]
        if (f >= lower) return lerp(lowerColour, upperColour, (f - lower) / (upper - lower))
    }
    return stops.last().second
}

/**
 * The Cleaning page, drawn by the app from Gregg's own entities so every part of it looks the same, all in the cleaning blue: one card at the
 * top for what he is doing (his status icon, the battery, the last clean and the buttons), then his cleaning settings (drop-downs, the mode,
 * suction and water with the house's icons), the dock's four actions, the parts that wear out (a bar for the life left in each, fading from blue through green and yellow to red
 * as they near the end) and his switches. The cleaning tasks come first, then everything about Gregg in one section whose cards each have their title.
 */
@Composable
fun CleaningPage(entities: Map<String, EntityState>, call: CallService) {
    val vac = entities[VACUUM_ENTITY]
    val battery = entities[VACUUM_BATTERY]
    val tone = toneColors(vacuumTone(vac))
    val blue = toneColors(Tone.Gregg)
    val red = toneColors(Tone.Red)
    val green = toneColors(Tone.Green)
    val yellow = toneColors(Tone.Amber)
    val enabled = vac?.available == true
    val cleaning = vac?.state == "cleaning"
    val state = greggState(vac, battery)
    val pct = battery?.state?.toDoubleOrNull()
    // Reset asks twice: the first tap arms it for a few seconds, the second does it.
    var armed by remember { mutableStateOf<Part?>(null) }
    LaunchedEffect(armed) { if (armed != null) { kotlinx.coroutines.delay(4_000); armed = null } }

    fun press(id: String) = call("button", "press", id, data())
    fun pick(id: String, option: String) = call("select", "select_option", id, data("option" to option))

    // ---- Cleaning tasks: the cleaning to-do list, above Gregg
    if (entities["todo.cleaning"] != null) {
        SectionPanel("Cleaning tasks", iconName = "cd:broom", tone = Tone.Gregg) {
            TaskListCard(org.json.JSONObject().put("entity", "todo.cleaning"), entities, call)
        }
    }

    // ---- Gregg: everything about him in one section, as separate cards: what he is doing, his settings, the dock, parts and switches
    SectionPanel("Gregg", tone = Tone.Gregg, summary = vacuumSummary(vac, battery), iconSlot = { GreggIcon(GreggState.Idle, it.accent, 36.dp) }) {
        // The same card as on the Home screen.
        VacuumCard(vac, battery, call, entities)

    // ---- Cleaning settings: one drop-down for each
    val settings = listOf(
        "Mode" to "select.gregg_cleaning_mode",
        "Suction" to "select.gregg_suction_level",
        "Water" to "select.gregg_water_level",
        "Cleaning intensity" to "select.gregg_cleaning_intensity",
        "Mop intensity" to "select.gregg_mop_intensity",
    ).filter { (_, id) -> entities[id]?.options()?.isNotEmpty() == true }
    if (settings.isNotEmpty()) {
        GridCard(blue.container, blue.onContainer, 1.25f * settings.size + 1.25f, "Cleaning Settings") {
            settings.forEachIndexed { k, (label, id) ->
                val e = entities.getValue(id)
                PickRow(label, e.options(), e.state, { o -> greggLevelIcon(id, o) }, blue, e.available, Modifier.at(0.5f, 1f + 1.25f * k, 7f, 1f)) { pick(id, it) }
            }
        }
    }

    // ---- The dock: its four actions, two to a row
    val find = entities["switch.gregg_find_robot"]
    val dockButtons = listOf("button.gregg_empty_dust_bin", "button.gregg_wash_mop", "button.gregg_dry_mop", "button.gregg_stop_dry_mop")
    if (dockButtons.any { entities[it] != null } || find != null) {
        val status = entities["sensor.gregg_dock_status"]?.state?.takeIf { it != "unknown" && it != "unavailable" }.orEmpty()
        val doing = status.lowercase()
        run {
            val drying = doing.contains("dry") && entities["button.gregg_stop_dry_mop"] != null
            val tiles = buildList {
                if (entities["button.gregg_empty_dust_bin"] != null) add(TileItem("cd:empty", "Empty dust bin", doing.contains("empty") || doing.contains("dust")) { press("button.gregg_empty_dust_bin") })
                if (entities["button.gregg_wash_mop"] != null) add(TileItem("cd:wash", "Wash mop", doing.contains("wash")) { press("button.gregg_wash_mop") })
                // While the mop is drying the same place stops it.
                if (drying) add(TileItem("cd:stop", "Stop drying", true) { press("button.gregg_stop_dry_mop") })
                else if (entities["button.gregg_dry_mop"] != null) add(TileItem("cd:dry", "Dry mop", false) { press("button.gregg_dry_mop") })
                if (find != null) add(TileItem("cd:find", "Find Gregg", find.state == "on") { call("switch", "toggle", find.entityId, data()) })
            }
            val lines = tiles.chunked(2)
            GridCard(blue.container, blue.onContainer, 1f + 1.25f * lines.size + 0.25f, "Dock") {
                if (status.isNotBlank()) Box(Modifier.at(4f, 0.25f, 3.5f, 0.5f), contentAlignment = Alignment.CenterEnd) { StatusPill(status, blue) }
                lines.forEachIndexed { r, line ->
                    line.forEachIndexed { c, t ->
                        val w = if (line.size == 1) 7f else 3.25f
                        GridButton(t, blue, blue.onContainer, true, false, Modifier.at(0.5f + c * 3.75f, 1f + 1.25f * r, w, 1f))
                    }
                }
            }
        }
    }

    // ---- Parts that wear out: a bar for the life left, blue turning to red
    val parts = CLEANING_PARTS.filter { entities[it.sensor] != null }
    if (parts.isNotEmpty()) {
        run {
            GridCard(blue.container, blue.onContainer, 1.5f * parts.size + 1.5f, "Parts") {
                Box(Modifier.at(4f, 0.25f, 3.5f, 0.5f), contentAlignment = Alignment.CenterEnd) { StatusPill("Time left", blue) }
                parts.forEachIndexed { k, p ->
                    val e = entities[p.sensor]
                    val leftText = e?.state?.takeIf { it != "unknown" && it != "unavailable" }
                    val left = leftText?.toDoubleOrNull()
                    val unit = e?.str("unit_of_measurement") ?: "h"
                    val fraction = partLifeLeft(left, p.lifeHours)
                    val colour = partColour(fraction, blue.accent, green.accent, yellow.accent, red.accent)
                    val ink = blue.onContainer
                    Row(Modifier.at(0.5f, 1f + 1.5f * k, 7f, 1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(cell * 0.35f)) {
                        // A squircle behind each part's icon, its centre on a line.
                        Box(Modifier.size(cell * 0.9f).background(colour.copy(alpha = 0.18f), RoundedCornerShape(30)), contentAlignment = Alignment.Center) {
                            HaIcon(e?.str("icon")?.takeIf { it.isNotBlank() } ?: p.icon, CdVectors.Info, colour, 22.dp)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = MaterialTheme.typography.bodyLarge, color = ink, maxLines = 1)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(left?.let { "${Math.round(it)} $unit left" } ?: "No reading", style = MaterialTheme.typography.bodySmall, color = ink, maxLines = 1)
                                LevelBar(fraction, colour, ink, Modifier.width(cell * 1.5f), 5.dp)
                            }
                        }
                        if (entities[p.reset] != null) {
                            val sure = armed == p
                            val chip = if (sure) red.accent else ink.copy(alpha = 0.8f)
                            Text(
                                if (sure) "Tap to reset" else "Reset", style = MaterialTheme.typography.labelLarge, color = chip,
                                modifier = Modifier.clip(CircleShape).background(chip.copy(alpha = 0.14f)).clickable { if (sure) { press(p.reset); armed = null } else armed = p }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    // ---- Switches
    val switches = CLEANING_SWITCHES.filter { entities[it.second]?.available == true }
    if (switches.isNotEmpty()) {
        run {
            GridCard(blue.container, blue.onContainer, 1.25f * switches.size + 1.25f, "Settings") {
                switches.forEachIndexed { k, (label, id) ->
                    val on = entities[id]?.state == "on"
                    Row(Modifier.at(0.5f, 1f + 1.25f * k, 7f, 1f), verticalAlignment = Alignment.CenterVertically) {
                        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        Switch(
                            checked = on, onCheckedChange = { call("switch", if (it) "turn_on" else "turn_off", id, data()) },
                            colors = SwitchDefaults.colors(checkedTrackColor = blue.accent, checkedThumbColor = blue.onAccent),
                        )
                    }
                }
            }
        }
    }
    }
}

/** A card's heading inside the Gregg section: its title and, on the right, a short status. */
@Composable
private fun CardTitle(title: String, summary: String?, tone: ToneColors) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1)
        if (!summary.isNullOrBlank()) StatusPill(summary, tone)
    }
}

/** A thin bar like the battery's on the Security page: [fraction] of the track filled in [colour]. */
@Composable
private fun HealthBar(fraction: Float, colour: Color) {
    Box(Modifier.fillMaxWidth().height(8.dp).background(Color.Gray.copy(alpha = 0.25f), RoundedCornerShape(4.dp))) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().background(colour, RoundedCornerShape(4.dp)))
    }
}

/** A setting as a line: its name, and a drop-down showing the current choice (with its icon where there is one) that opens the others. */
@Composable
private fun PickRow(label: String, options: List<String>, selected: String, iconFor: (String) -> String?, tone: ToneColors, enabled: Boolean, modifier: Modifier, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val nice = { o: String -> presetLabel(o) }
    Box(modifier) {
        Row(
            Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp)).background(tone.onContainer.copy(alpha = if (enabled) 0.10f else 0.05f)).clickable(enabled = enabled) { open = true }
                .padding(start = 14.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            iconFor(selected)?.let { HaIcon(it, CdVectors.Info, tone.onContainer, 20.dp) }
            Text(nice(selected), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            Icon(CdVectors.ChevronDown, contentDescription = null, tint = tone.onContainer)
        }
        run {
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                for (o in options) {
                    val icon = iconFor(o)
                    val ink = if (o == selected) tone.accent else MaterialTheme.colorScheme.onSurface
                    DropdownMenuItem(
                        text = { Text(nice(o), color = ink) },
                        leadingIcon = if (icon != null) { { HaIcon(icon, CdVectors.Info, ink, 22.dp) } } else null,
                        onClick = { open = false; onPick(o) },
                    )
                }
            }
        }
    }
}
