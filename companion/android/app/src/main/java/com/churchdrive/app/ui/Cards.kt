package com.churchdrive.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.churchdrive.app.ha.CallService
import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ha.data
import kotlin.math.roundToInt

private val CardShape = RoundedCornerShape(28.dp)

/** The shared look of every entity card: a rounded card tinted in the entity's tone. */
@Composable
fun EntityCard(
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    body: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) { body() }
    }
}

/** An icon in a tinted circle, as used at the start of a card or section header. */
@Composable
fun ToneIcon(icon: ImageVector, tone: ToneColors, size: Int = 40) {
    Box(
        modifier = Modifier.size(size.dp).background(tone.accent.copy(alpha = 0.18f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tone.accent, modifier = Modifier.size((size * 0.55f).dp))
    }
}

/** One of a row of mutually exclusive choices; the current one is filled in the tone's strong colour. */
@Composable
fun ChoiceButton(
    label: String,
    selected: Boolean,
    tone: ToneColors,
    content: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val padding = PaddingValues(horizontal = 4.dp)
    if (selected) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier,
            contentPadding = padding,
            colors = ButtonDefaults.buttonColors(containerColor = tone.accent, contentColor = tone.onAccent),
        ) { Text(label, maxLines = 1) }
    } else {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier,
            contentPadding = padding,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = content),
            border = BorderStroke(1.dp, content.copy(alpha = 0.4f)),
        ) { Text(label, maxLines = 1) }
    }
}

// ---- Alarm ----

@Composable
fun AlarmCard(alarm: EntityState?, call: CallService) {
    val state = alarm?.state
    val tone = toneColors(alarmTone(state))
    val triggered = state == "triggered"
    // Triggered is fully tinted, like the dashboard card.
    val container = if (triggered) tone.accent else tone.container
    val content = if (triggered) tone.onAccent else tone.onContainer
    val enabled = alarm != null
    fun send(service: String) = call("alarm_control_panel", service, ALARM_ENTITY, data())

    EntityCard(container, content) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(alarmIcon(state), contentDescription = null, modifier = Modifier.size(36.dp))
            Text(alarmLabel(state), style = MaterialTheme.typography.headlineSmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val m = Modifier.weight(1f)
            ChoiceButton("Disarm", state == "disarmed", tone, content, m, enabled) { send("alarm_disarm") }
            ChoiceButton("Home", state == "armed_home", tone, content, m, enabled) { send("alarm_arm_home") }
            ChoiceButton("Away", state == "armed_away", tone, content, m, enabled) { send("alarm_arm_away") }
            ChoiceButton("Night", state == "armed_night", tone, content, m, enabled) { send("alarm_arm_night") }
        }
    }
}

const val ALARM_ENTITY = "alarm_control_panel.church_drive_alarm"

private fun alarmIcon(state: String?): ImageVector = when (state) {
    "disarmed" -> Icons.Filled.LockOpen
    "triggered" -> Icons.Filled.Warning
    "armed_home" -> Icons.Filled.Home
    "armed_away" -> Icons.Filled.Lock
    "armed_night" -> Icons.Filled.Bedtime
    else -> Icons.Filled.Security
}

fun alarmLabel(state: String?): String = when (state) {
    "disarmed" -> "Disarmed"
    "armed_home" -> "Armed home"
    "armed_away" -> "Armed away"
    "armed_night" -> "Armed night"
    "arming" -> "Arming"
    "pending" -> "Entry delay"
    "triggered" -> "Triggered"
    null -> "Loading…"
    else -> state.replaceFirstChar { it.uppercase() }
}

// ---- Climate ----

const val CLIMATE_ENTITY = "climate.downstairs"

/** Off grey, Eco green, cooling blue, otherwise heating orange: the same as the dashboard's thermostat. */
fun climateTone(c: EntityState?): Tone = when {
    c == null || c.state == "off" || !c.available -> Tone.Grey
    c.str("preset_mode") == "eco" -> Tone.Green
    c.str("hvac_action") == "cooling" -> Tone.Blue
    else -> Tone.Orange
}

fun climateWord(c: EntityState?): String = when {
    c == null -> "Loading…"
    c.state == "off" -> "Off"
    c.str("preset_mode") == "eco" -> "Eco"
    c.str("hvac_action") == "heating" -> "Heating"
    c.str("hvac_action") == "cooling" -> "Cooling"
    else -> "Idle"
}

fun temp(v: Double?): String = if (v == null) "–" else "%.1f".format(v).removeSuffix(".0")

@Composable
fun ClimateCard(climate: EntityState?, call: CallService) {
    val tone = toneColors(climateTone(climate))
    val enabled = climate?.available == true
    val target = climate?.num("temperature")
    val step = climate?.num("target_temp_step") ?: 0.5
    // Taps build on what was last shown, so quick presses add up.
    var pending by remember(target) { mutableStateOf(target) }
    fun nudge(by: Double) {
        val next = ((pending ?: return) + by).coerceIn(climate?.num("min_temp") ?: 5.0, climate?.num("max_temp") ?: 30.0)
        pending = next
        call("climate", "set_temperature", CLIMATE_ENTITY, data("temperature" to next))
    }

    EntityCard(tone.container, tone.onContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${temp(climate?.num("current_temperature"))}°",
                    style = MaterialTheme.typography.displayMedium,
                )
                Text(
                    buildString {
                        append(climateWord(climate))
                        if (climate != null && climate.state != "off") append(" · target ${temp(pending)}°")
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StepButton(Icons.Filled.Remove, "Lower", tone, enabled && climate?.state != "off") { nudge(-step) }
                StepButton(Icons.Filled.Add, "Raise", tone, enabled && climate?.state != "off") { nudge(step) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val m = Modifier.weight(1f)
            val eco = climate?.str("preset_mode") == "eco"
            ChoiceButton("Off", climate?.state == "off", tone, tone.onContainer, m, enabled) {
                call("climate", "set_hvac_mode", CLIMATE_ENTITY, data("hvac_mode" to "off"))
            }
            ChoiceButton("Heat", climate?.state == "heat" && !eco, tone, tone.onContainer, m, enabled) {
                call("climate", "set_hvac_mode", CLIMATE_ENTITY, data("hvac_mode" to "heat"))
            }
            ChoiceButton("Eco", eco && climate?.state != "off", tone, tone.onContainer, m, enabled) {
                call("climate", "set_preset_mode", CLIMATE_ENTITY, data("preset_mode" to "eco"))
            }
        }
    }
}

@Composable
private fun StepButton(icon: ImageVector, description: String, tone: ToneColors, enabled: Boolean, onClick: () -> Unit) {
    FilledIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(52.dp),
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = tone.accent, contentColor = tone.onAccent),
    ) { Icon(icon, contentDescription = description) }
}

// ---- Lights ----

val LIGHT_ROOMS = listOf("light.kitchen", "light.living_room", "light.middle_floor")

fun lightsSummary(entities: Map<String, EntityState>): String {
    val n = LIGHT_ROOMS.count { entities[it]?.state == "on" }
    return if (n == 0) "All off" else "$n room${if (n > 1) "s" else ""} on"
}

@Composable
fun LightRoomCard(light: EntityState?, entityId: String, call: CallService) {
    val tone = toneColors(if (light?.state == "on") Tone.Amber else Tone.Grey)
    val on = light?.state == "on"
    val pct = ((light?.num("brightness") ?: 0.0) / 255.0 * 100).roundToInt().coerceIn(1, 100)
    var dragging by remember(pct) { mutableFloatStateOf(pct.toFloat()) }

    EntityCard(tone.container, tone.onContainer) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ToneIcon(Icons.Filled.Lightbulb, tone)
            Column(modifier = Modifier.weight(1f)) {
                Text(light?.friendlyName ?: entityId, style = MaterialTheme.typography.titleMedium)
                Text(
                    if (on) "On · $pct%" else if (light == null) "Loading…" else "Off",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Switch(
                checked = on,
                enabled = light?.available == true,
                onCheckedChange = { want ->
                    call("light", if (want) "turn_on" else "turn_off", entityId, data())
                },
                colors = SwitchDefaults.colors(checkedTrackColor = tone.accent, checkedThumbColor = tone.onAccent),
            )
        }
        if (on) {
            Slider(
                value = dragging,
                onValueChange = { dragging = it },
                onValueChangeFinished = {
                    call("light", "turn_on", entityId, data("brightness_pct" to dragging.roundToInt()))
                },
                valueRange = 1f..100f,
                colors = SliderDefaults.colors(
                    thumbColor = tone.accent,
                    activeTrackColor = tone.accent,
                    inactiveTrackColor = tone.accent.copy(alpha = 0.24f),
                ),
            )
        }
    }
}

// ---- Vacuum ----

const val VACUUM_ENTITY = "vacuum.gregg"
const val VACUUM_BATTERY = "sensor.gregg_battery"

fun vacuumTone(v: EntityState?): Tone = when (v?.state) {
    "error" -> Tone.Red
    "cleaning" -> Tone.Blue
    "returning" -> Tone.Blue
    null, "unavailable", "unknown" -> Tone.Grey
    else -> Tone.Blue
}

fun vacuumSummary(v: EntityState?, battery: EntityState?): String {
    val state = v?.state?.replaceFirstChar { it.uppercase() } ?: "Loading…"
    val b = battery?.state?.takeIf { it != "unknown" && it != "unavailable" }
    return if (b != null) "$state · $b%" else state
}

@Composable
fun VacuumCard(vacuum: EntityState?, battery: EntityState?, call: CallService) {
    val tone = toneColors(vacuumTone(vacuum))
    val enabled = vacuum?.available == true
    val cleaning = vacuum?.state == "cleaning"
    EntityCard(tone.container, tone.onContainer) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ToneIcon(Icons.Filled.CleaningServices, tone)
            Column(modifier = Modifier.weight(1f)) {
                Text(vacuum?.friendlyName ?: "Vacuum", style = MaterialTheme.typography.titleMedium)
                Text(vacuumSummary(vacuum, battery), style = MaterialTheme.typography.bodyMedium)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val m = Modifier.weight(1f)
            ChoiceButton(
                label = if (cleaning) "Pause" else if (vacuum?.state == "paused") "Resume" else "Start",
                selected = true, tone = tone, content = tone.onContainer, modifier = m, enabled = enabled,
            ) { call("vacuum", if (cleaning) "pause" else "start", VACUUM_ENTITY, data()) }
            ChoiceButton("Dock", false, tone, tone.onContainer, m, enabled) {
                call("vacuum", "return_to_base", VACUUM_ENTITY, data())
            }
        }
    }
}

// ---- Car charger (Zappi) ----

const val ZAPPI_MODE = "select.zappi_charge_mode"
const val ZAPPI_POWER = "sensor.zappi_charging_power"
const val ZAPPI_STATUS = "sensor.zappi_status"
const val ZAPPI_PLUG = "sensor.zappi_plug_status"
const val ZAPPI_SESSION = "sensor.zappi_charge_added_session"

@Composable
fun ChargerCard(entities: Map<String, EntityState>, call: CallService) {
    val mode = entities[ZAPPI_MODE]
    val power = entities[ZAPPI_POWER]?.state?.toDoubleOrNull() ?: 0.0
    val charging = power > 0
    val tone = toneColors(if (charging) Tone.Teal else Tone.Grey)
    val session = entities[ZAPPI_SESSION]?.state?.toDoubleOrNull()
    val status = when {
        mode == null -> "Loading…"
        charging -> "Charging · ${"%.1f".format(power / 1000)} kW"
        else -> entities[ZAPPI_PLUG]?.state ?: entities[ZAPPI_STATUS]?.state ?: "Idle"
    }
    EntityCard(tone.container, tone.onContainer) {
        Column {
            Text(status, style = MaterialTheme.typography.titleLarge)
            if (session != null) {
                Text("${temp(session)} kWh this session", style = MaterialTheme.typography.bodyMedium)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val m = Modifier.weight(1f)
            val teal = toneColors(Tone.Teal)
            mode?.options()?.forEach { option ->
                ChoiceButton(option, mode.state == option, teal, tone.onContainer, m, mode.available) {
                    call("select", "select_option", ZAPPI_MODE, data("option" to option))
                }
            }
        }
    }
}
