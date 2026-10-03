package com.churchdrive.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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

/**
 * The icons the cards use for modes, in one place. The defaults below are the dashboard's own; [overrides] (keyed
 * `group.key`, such as `fan.sleep`) take over when Home Assistant supplies icons, so the app follows the dashboard.
 */
object IconMap {
    val overrides = mutableStateMapOf<String, String>()

    fun of(group: String, key: String, default: String): String = overrides["$group.${key.lowercase()}"] ?: default
}

/** One tile: an icon and a label (as on the dashboard's cards), filled when it's the current choice. */
data class TileItem(val icon: String, val label: String, val selected: Boolean, val showLabel: Boolean = true, val onClick: () -> Unit)

@Composable
private fun Tile(item: TileItem, tone: ToneColors, content: Color, enabled: Boolean, column: Boolean, modifier: Modifier) {
    val shape = RoundedCornerShape(12.dp)
    val ink = (if (item.selected) tone.onAccent else content).copy(alpha = if (enabled) 1f else 0.4f)
    Box(
        modifier = modifier
            .height(if (column) 56.dp else 48.dp)
            .clip(shape)
            .background(if (item.selected) tone.accent else content.copy(alpha = 0.10f), shape)
            .clickable(enabled = enabled, onClickLabel = item.label, onClick = item.onClick)
            .semantics { contentDescription = item.label },
        contentAlignment = Alignment.Center,
    ) {
        if (column) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                HaIcon(item.icon, Icons.Filled.Info, ink, 20.dp)
                if (item.showLabel) Text(item.label, color = ink, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                HaIcon(item.icon, Icons.Filled.Info, ink, 20.dp)
                if (item.showLabel) Text(item.label, color = ink, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(end = 2.dp))
            }
        }
    }
}

/**
 * Tiles in rows of up to [perRow], shared out evenly (so six tiles are two rows of three) and, as on the dashboard,
 * going onto more rows rather than scrolling. [column] puts the label under the icon.
 */
@Composable
fun TileRow(items: List<TileItem>, tone: ToneColors, content: Color, enabled: Boolean = true, perRow: Int = 4, column: Boolean = false) {
    if (items.isEmpty()) return
    val rows = (items.size + perRow - 1) / perRow
    val size = (items.size + rows - 1) / rows
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(size).forEach { chunk ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                chunk.forEach { Tile(it, tone, content, enabled, column, Modifier.weight(1f)) }
                repeat(size - chunk.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

private val FAN_PRESET_ICONS = mapOf(
    "natural" to "mdi:weather-windy", "nature" to "mdi:weather-windy", "breeze" to "mdi:weather-windy",
    "sleep" to "mdi:power-sleep", "auto" to "mdi:fan-auto", "smart" to "mdi:fan-auto",
    "turbo" to "mdi:rocket-launch", "boost" to "mdi:rocket-launch", "eco" to "mdi:leaf",
)

private val SPEED_NAME = Regex("^speed[ _-]?(\\d+)$", RegexOption.IGNORE_CASE)

/** The number in a speed preset such as `speed_2`, or null for any other mode. */
fun speedNumber(preset: String): Int? = SPEED_NAME.find(preset)?.groupValues?.get(1)?.toIntOrNull()

/** One speed of a fan: its number, and the preset or the percentage that sets it. */
data class FanSpeed(val n: Int, val preset: String?, val percentage: Int?)

/** The speeds a fan offers, as the dashboard's fan card works them out: `speed_N` presets, else steps of its percentage. */
fun fanSpeeds(fan: com.churchdrive.app.ha.EntityState?): List<FanSpeed> {
    if (fan == null) return emptyList()
    val presets = fan.list("preset_modes").filter { speedNumber(it) != null }.sortedBy { speedNumber(it) }
    if (presets.isNotEmpty()) return presets.map { FanSpeed(speedNumber(it) ?: 0, it, null) }
    val step = fan.num("percentage_step") ?: 0.0
    val count = if (step > 0) Math.round(100 / step).toInt() else 0
    if (count < 2 || count > 6) return emptyList()
    return (0 until count).map { FanSpeed(it + 1, null, Math.round(step * (it + 1)).toInt()) }
}

/** The speedometer for the [i]th of [count] speeds: slow, medium or fast. */
fun fanSpeedIcon(i: Int, count: Int): String {
    val gauges = listOf("mdi:speedometer-slow", "mdi:speedometer-medium", "mdi:speedometer")
    return IconMap.of("fan", "speed_${i + 1}", gauges[minOf(2, Math.round(i.toDouble() / maxOf(1, count - 1) * 2).toInt())])
}

/** The icon of a fan's other modes (sleep, auto, natural...), as on the dashboard's fan card. */
fun fanPresetIcon(preset: String): String = IconMap.of("fan", preset, FAN_PRESET_ICONS[preset.lowercase()] ?: "mdi:fan")

private val PURIFIER_MODE_ICONS = mapOf(
    "auto" to "mdi:autorenew", "auto (general)" to "mdi:autorenew", "allergen" to "mdi:flower", "medium" to "mdi:fan",
    "turbo" to "mdi:rocket-launch", "sleep" to "mdi:power-sleep", "night" to "mdi:power-sleep",
    "low" to "mdi:fan-speed-1", "high" to "mdi:fan-speed-3",
)

/** An icon for an air purifier's mode, as the dashboard's air purifier card has them. */
fun purifierModeIcon(mode: String): String {
    val speed = SPEED_NAME.find(mode)?.groupValues?.get(1)?.toIntOrNull()
    return IconMap.of("purifier", mode, PURIFIER_MODE_ICONS[mode.lowercase()] ?: speed?.let { "mdi:fan-speed-${it.coerceIn(1, 3)}" } ?: "mdi:fan")
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
    q.hvacMode != null -> IconMap.of("climate", q.hvacMode, HVAC_MODE_ICONS[q.hvacMode] ?: "mdi:thermostat")
    q.presetMode != null -> IconMap.of("climate", q.presetMode, CLIMATE_PRESET_ICONS[q.presetMode] ?: "mdi:tune-variant")
    else -> "mdi:tune-variant"
}

/** One of the Zappi's modes: what it's called in Home Assistant, what the button says, and its icon. */
data class ChargerMode(val key: String, val name: String, val icon: String)

/** The Zappi's modes in the dashboard's order (Stop, Eco, Eco+, Fast), those the charger offers. */
fun chargerModes(options: List<String>): List<ChargerMode> =
    listOf(
        ChargerMode("Stopped", "Stop", IconMap.of("charger", "Stopped", "mdi:stop-circle-outline")),
        ChargerMode("Eco", "Eco", IconMap.of("charger", "Eco", "mdi:leaf")),
        ChargerMode("Eco+", "Eco+", IconMap.of("charger", "Eco+", "mdi:solar-power")),
        ChargerMode("Fast", "Fast", IconMap.of("charger", "Fast", "mdi:lightning-bolt")),
    ).filter { options.isEmpty() || it.key in options }
