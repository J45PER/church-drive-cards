package com.churchdrive.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ha.Registry
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Opens a device's health page for an entity of that device (a long press on a card). Does nothing where the app hasn't set it. */
val LocalOpenDevice = compositionLocalOf<(String) -> Unit> { {} }

private fun HealthLevel.tone(): Tone = when (this) {
    HealthLevel.Ok -> Tone.Green
    HealthLevel.Warn -> Tone.Amber
    HealthLevel.Bad -> Tone.Red
}

private fun DeviceHealthItem.icon(): String = when {
    offline -> "mdi:wifi-off"
    batteryLevel == HealthLevel.Bad || batteryLevel == HealthLevel.Warn -> "mdi:battery-alert"
    staleReason != null -> "mdi:alert-circle-outline"
    filterLevel == HealthLevel.Warn -> "mdi:air-filter"
    else -> "mdi:check-circle-outline"
}

/**
 * Device Health: a full-page overlay listing the devices with a battery, a filter, or that stopped answering, those needing
 * attention first. A device opens its own page. [device] is a device id or an entity id to open straight onto (from a
 * notification or a long press); null opens the list.
 */
@Composable
fun DeviceHealthOverlay(
    device: String?,
    entities: Map<String, EntityState>,
    registry: Registry,
    areaNames: Map<String, String>,
    onClose: () -> Unit,
) {
    val startId = device?.let { deviceIdFor(it, registry) }
    // Opened onto a device: back closes. Opened onto the list (or onto a device that isn't known): back from a device returns to the list.
    var selected by remember(device) { mutableStateOf(startId) }
    BackHandler { if (selected != null && startId == null) selected = null else onClose() }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        val item = selected?.let { healthOfDevice(it, entities, registry, areaNames) }
        if (item != null) {
            DevicePage(item, entities) { if (startId == null) selected = null else onClose() }
        } else {
            HealthList(deviceHealthList(entities, registry, areaNames), onClose) { selected = it.deviceId }
        }
    }
}

@Composable
private fun HealthHeader(title: String, subtitle: String?, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
        Column(Modifier.padding(start = 4.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1)
            subtitle?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun HealthList(items: List<DeviceHealthItem>, onClose: () -> Unit, onOpen: (DeviceHealthItem) -> Unit) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HealthHeader("Device health", deviceHealthSummary(items), onClose)
        if (items.isEmpty()) Text("No batteries, filters or devices to show yet.", style = MaterialTheme.typography.bodyMedium)
        val (attention, fine) = items.partition { it.needsAttention }
        if (attention.isNotEmpty()) {
            Text("Needs attention", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            attention.forEach { HealthRow(it) { onOpen(it) } }
        }
        if (fine.isNotEmpty()) {
            Text("All fine", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            fine.forEach { HealthRow(it) { onOpen(it) } }
        }
    }
}

@Composable
private fun HealthRow(item: DeviceHealthItem, onClick: () -> Unit) {
    val tone = toneColors(item.level.tone())
    EntityCard(tone.container, tone.onContainer, Modifier.clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ToneIconName(item.icon(), Icons.Filled.Info, tone, 36)
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                Text(
                    listOfNotNull(item.area, item.summary).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, maxLines = 2,
                )
            }
        }
    }
}

@Composable
private fun DevicePage(item: DeviceHealthItem, entities: Map<String, EntityState>, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HealthHeader(item.name, item.area, onBack)
        val tone = toneColors(item.level.tone())
        EntityCard(tone.container, tone.onContainer) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ToneIconName(item.icon(), Icons.Filled.Info, tone)
                Column(Modifier.weight(1f)) {
                    Text(item.summary, style = MaterialTheme.typography.titleMedium)
                    Text("Heard ${heardAgo(item.heard)}", style = MaterialTheme.typography.bodySmall)
                }
            }
            item.staleReason?.takeIf { !item.offline }?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
        val batterySensor = item.entityIds.firstOrNull { entities[it]?.let { e -> it.startsWith("sensor.") && e.str("device_class") == "battery" } == true }
        if (batterySensor != null) BatteryHistory(batterySensor, item.battery)
        Text("Everything on this device", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val neutral = panelNeutral()
        EntityCard(neutral.container, neutral.onContainer) {
            item.entityIds.mapNotNull { entities[it] }.forEach { e ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(e.friendlyName, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 2)
                    Text(
                        e.state.replace('_', ' ') + (e.str("unit_of_measurement")?.let { " $it" } ?: ""),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** A battery's last week, drawn like the dashboard's history graphs. */
@Composable
private fun BatteryHistory(entityId: String, now: Int?) {
    val load = LocalHistory.current
    var history by remember { mutableStateOf<Map<String, List<Pair<Long, String>>>>(emptyMap()) }
    LaunchedEffect(entityId) {
        while (true) {
            load(listOf(entityId), 24 * 7) { history = it }
            delay(10 * 60_000L)
        }
    }
    val points = numericSeries(history[entityId])
    if (points.size < 2) return
    val tone = toneColors(Tone.Teal)
    val neutral = panelNeutral()
    val to = System.currentTimeMillis()
    EntityCard(neutral.container, neutral.onContainer) {
        Text("Battery over the last week" + (now?.let { " · now $it%" } ?: ""), style = MaterialTheme.typography.titleSmall)
        LineGraph(
            listOf(GraphLayer(points, tone.accent, 0.0, 100.0, { "${it.roundToInt()}%" }, fill = true)),
            from = to - 7 * 24 * 3_600_000L, now = to, smooth = false, ink = neutral.onContainer,
            legend = listOf("● Battery" to tone.accent),
        )
    }
}
