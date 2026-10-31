package com.churchdrive.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.churchdrive.app.ha.CallService
import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ha.data
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// The assistant looks after each thermostat on its own (Home Assistant: the "Clive: keep the climate comfortable" automation and its scripts). Each
// thermostat has its own switch (Controlled by him, or Manual), its comfortable range (two numbers), and the time he is paused until after somebody changes
// it by hand. This is the line at the bottom of a thermostat's card that says which, and the sheet that opens from it to change them.
// (The helpers' ids carry the old name: they are Home Assistant's, set up there, and never change here.)

/** What the house holds about how the assistant looks after one thermostat. */
class ZoneControl(
    val climateId: String,
    val auto: EntityState,
    val low: EntityState?,
    val high: EntityState?,
    val resume: EntityState?,
    val note: EntityState?,
) {
    /** "downstairs" for climate.downstairs. */
    val zone: String get() = climateId.removePrefix("climate.")
    val controlled: Boolean get() = auto.state == "on"
    val lowValue: Double? get() = low?.state?.toDoubleOrNull()
    val highValue: Double? get() = high?.state?.toDoubleOrNull()

    /** When the pause ends (epoch milliseconds), or null when he is not paused at [nowMs]. A time in the past, or the year 2000 ("no pause"), is none. */
    fun pausedUntilMs(nowMs: Long): Long? =
        resume?.takeIf { it.available }?.attributes?.optDouble("timestamp", 0.0)?.let { (it * 1000).toLong() }?.takeIf { it > nowMs }
}

/** The control state of [climateId]'s thermostat, or null when the house has no switch for it (nothing is then shown). */
fun zoneControl(entities: Map<String, EntityState>, climateId: String): ZoneControl? {
    val zone = climateId.removePrefix("climate.")
    val auto = entities["input_boolean.clive_climate_${zone}_auto"]?.takeIf { it.available } ?: return null
    return ZoneControl(
        climateId, auto,
        entities["input_number.clive_climate_${zone}_low"]?.takeIf { it.available },
        entities["input_number.clive_climate_${zone}_high"]?.takeIf { it.available },
        entities["input_datetime.clive_climate_${zone}_resume"],
        entities["input_text.clive_climate_${zone}_note"]?.takeIf { it.available && it.state.isNotBlank() },
    )
}

private fun clock(ms: Long): String = HomeTime.format(ms, "HH:mm")

/** "Clive controlled", "Clive controlled · paused until 12:24" or "Manual". */
fun zoneStatusText(zone: ZoneControl, name: String, nowMs: Long): String {
    if (!zone.controlled) return "Manual"
    val until = zone.pausedUntilMs(nowMs)
    return if (until != null) "$name controlled · paused until ${clock(until)}" else "$name controlled"
}

/** The line at the bottom of a thermostat's card: who looks after it, and a tap opens its settings. */
@Composable
fun ZoneStatusLine(zone: ZoneControl, tone: ToneColors, onClick: () -> Unit) {
    val name = AssistantBrand.shown
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(tone.onContainer.copy(alpha = 0.10f))
            .clickable(onClick = onClick).heightIn(min = 48.dp).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        HaIcon(if (zone.controlled) "mdi:check-circle-outline" else "mdi:hand-back-right-outline", Icons.Filled.Info, tone.onContainer, 20.dp)
        Text(zoneStatusText(zone, name, System.currentTimeMillis()), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text("Settings ›", style = MaterialTheme.typography.bodyMedium)
    }
}

/** One end of the comfortable range: its value between a minus and a plus, half a degree at a time. */
@Composable
internal fun RangeRow(label: String, value: Double?, tone: ToneColors, onChange: (Double) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        StepButton(Icons.Filled.Remove, "Lower $label", tone, value != null) { value?.let { onChange(it - 0.5) } }
        Text("${temp(value)}°", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.width(56.dp))
        StepButton(Icons.Filled.Add, "Raise $label", tone, value != null) { value?.let { onChange(it + 0.5) } }
    }
}

/**
 * The settings of one thermostat, as a sheet from the bottom: controlled by the assistant or manual, the comfortable range, the pause (with a way
 * to end it now) and the last thing he did.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThermostatSheet(title: String, zone: ZoneControl, tone: ToneColors, call: CallService, onDismiss: () -> Unit) {
    val name = AssistantBrand.shown
    val manual = "Manual"
    val controlledLabel = "$name controlled"
    val now = System.currentTimeMillis()
    val lo = zone.lowValue
    val hi = zone.highValue
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text("Thermostat settings", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OptionRow(listOf(controlledLabel, manual), if (zone.controlled) controlledLabel else manual, tone) { pick ->
                call("input_boolean", if (pick == controlledLabel) "turn_on" else "turn_off", zone.auto.entityId, data())
            }
            Text(
                if (zone.controlled) {
                    val range = if (lo != null && hi != null) "between ${temp(lo)}° and ${temp(hi)}°" else "comfortable"
                    val waits = if (lo != null && hi != null) ", and waits until it is below ${temp(lo - 1.5)}° (or above ${temp(hi + 1)}°) before he does" else ""
                    "$name keeps the room $range. He only changes the target of heating that is already on$waits."
                } else {
                    "$name leaves this thermostat alone."
                },
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (lo != null && hi != null) {
                RangeRow("Comfortable from", lo, tone) { v -> zone.low?.let { call("input_number", "set_value", it.entityId, data("value" to minOf(v, hi - 1.0))) } }
                RangeRow("Comfortable up to", hi, tone) { v -> zone.high?.let { call("input_number", "set_value", it.entityId, data("value" to maxOf(v, lo + 1.0))) } }
            }
            zone.pausedUntilMs(now)?.let { until ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Paused until ${clock(until)}", style = MaterialTheme.typography.titleMedium)
                    Text("The thermostat was changed by hand, so $name leaves it alone until then.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TileRow(
                        listOf(TileItem("mdi:play", "Resume $name now", true) {
                            call("script", "turn_on", PAUSE_CONTROL_SCRIPT, data("variables" to JSONObject().put("command", "resume").put("zone", zone.zone)))
                        }),
                        tone, tone.onContainer, perRow = 1,
                    )
                }
            }
            zone.note?.let { Text("Last: ${it.state}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

/** The comfortable range of a room that has no thermostat of its own, as a sheet from the bottom: its two number helpers, half a degree at a time. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomRangeSheet(title: String, low: EntityState, high: EntityState, tone: ToneColors, call: CallService, onDismiss: () -> Unit) {
    val lo = low.state.toDoubleOrNull()
    val hi = high.state.toDoubleOrNull()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text("Comfortable range", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("The room's graph and colours follow this range.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (lo != null && hi != null) {
                RangeRow("Comfortable from", lo, tone) { v -> call("input_number", "set_value", low.entityId, data("value" to minOf(v, hi - 1.0))) }
                RangeRow("Comfortable up to", hi, tone) { v -> call("input_number", "set_value", high.entityId, data("value" to maxOf(v, lo + 1.0))) }
            }
        }
    }
}
