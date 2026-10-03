package com.churchdrive.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** One choice in a row of icon buttons. [description] is what a screen reader says. */
data class IconItem(val icon: String, val description: String, val selected: Boolean, val onClick: () -> Unit)

/** A button drawn as an icon: filled in the tone's colour when it's the current choice, outlined when not. */
@Composable
fun IconChoice(
    icon: String,
    description: String,
    selected: Boolean,
    tone: ToneColors,
    content: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(shape)
            .background(if (selected) tone.accent else Color.Transparent, shape)
            .border(1.dp, if (selected) tone.accent else content.copy(alpha = 0.4f), shape)
            .clickable(enabled = enabled, onClickLabel = description, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        HaIcon(icon, Icons.Filled.Info, (if (selected) tone.onAccent else content).copy(alpha = if (enabled) 1f else 0.4f), 24.dp)
    }
}

/** A row of icon buttons: sharing the width when there are a few, scrolling sideways when there are many. */
@Composable
fun IconRow(items: List<IconItem>, tone: ToneColors, content: Color, enabled: Boolean = true) {
    if (items.size <= 5) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items.forEach { IconChoice(it.icon, it.description, it.selected, tone, content, Modifier.weight(1f), enabled, it.onClick) }
        }
    } else {
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items.forEach { IconChoice(it.icon, it.description, it.selected, tone, content, Modifier.width(56.dp), enabled, it.onClick) }
        }
    }
}

private val FAN_PRESET_ICONS = mapOf(
    "natural" to "mdi:weather-windy", "nature" to "mdi:weather-windy", "breeze" to "mdi:weather-windy",
    "sleep" to "mdi:power-sleep", "auto" to "mdi:fan-auto", "smart" to "mdi:fan-auto",
    "turbo" to "mdi:rocket-launch", "boost" to "mdi:rocket-launch", "eco" to "mdi:leaf",
)

private val SPEED_NAME = Regex("^speed[_ ]?(\\d+)$", RegexOption.IGNORE_CASE)

/** An icon for each of a fan's modes, as the dashboard's fan card has them: speeds are speedometers, the rest by name. */
fun fanPresetIcons(presets: List<String>): Map<String, String> {
    val speeds = presets.filter { SPEED_NAME.matches(it) }
    val gauges = listOf("mdi:speedometer-slow", "mdi:speedometer-medium", "mdi:speedometer")
    return presets.associateWith { p ->
        val i = speeds.indexOf(p)
        when {
            i >= 0 -> gauges[minOf(2, Math.round(i.toDouble() / maxOf(1, speeds.size - 1) * 2).toInt())]
            else -> FAN_PRESET_ICONS[p.lowercase()] ?: "mdi:fan"
        }
    }
}

private val PURIFIER_MODE_ICONS = mapOf(
    "auto" to "mdi:autorenew", "auto (general)" to "mdi:autorenew", "allergen" to "mdi:flower", "medium" to "mdi:fan",
    "turbo" to "mdi:rocket-launch", "sleep" to "mdi:power-sleep", "night" to "mdi:power-sleep",
    "low" to "mdi:fan-speed-1", "high" to "mdi:fan-speed-3",
)

/** An icon for an air purifier's mode, as the dashboard's air purifier card has them. */
fun purifierModeIcon(mode: String): String {
    val speed = SPEED_NAME.find(mode)?.groupValues?.get(1)?.toIntOrNull()
    return PURIFIER_MODE_ICONS[mode.lowercase()] ?: speed?.let { "mdi:fan-speed-${it.coerceIn(1, 3)}" } ?: "mdi:fan"
}

private val HVAC_MODE_ICONS = mapOf(
    "off" to "mdi:power", "heat" to "mdi:fire", "cool" to "mdi:snowflake", "heat_cool" to "mdi:sun-snowflake-variant",
    "auto" to "mdi:thermostat-auto", "dry" to "mdi:water-percent", "fan_only" to "mdi:fan",
)

private val CLIMATE_PRESET_ICONS = mapOf(
    "none" to "mdi:circle-off-outline", "eco" to "mdi:leaf", "boost" to "mdi:rocket-launch", "away" to "mdi:home-export-outline",
    "sleep" to "mdi:power-sleep", "comfort" to "mdi:sofa", "home" to "mdi:home",
)

/** The icon of a thermostat shortcut: its heating mode's or its preset's, as on the dashboard's climate card. */
fun quickSettingIcon(q: QuickSetting): String = when {
    q.hvacMode != null -> HVAC_MODE_ICONS[q.hvacMode] ?: "mdi:thermostat"
    q.presetMode != null -> CLIMATE_PRESET_ICONS[q.presetMode] ?: "mdi:tune-variant"
    else -> "mdi:tune-variant"
}

/** An icon for a car charger's mode (Eco, Eco+, Fast, Stop). */
fun chargerModeIcon(option: String): String = when (option.lowercase()) {
    "eco" -> "mdi:leaf"
    "eco+" -> "mdi:leaf-circle-outline"
    "fast" -> "mdi:lightning-bolt"
    "stop" -> "mdi:stop"
    else -> "mdi:ev-station"
}
