package com.churchdrive.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ha.Registry
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId

// Clive Admin's Home page: a tasks block, then cards on the main app's card grid (eight square cells wide, the height a number of rows),
// folded to two and a half rows or open. What each says is worked out in AdminHomeData.kt.

private val ElectricityGlow = CliveColours.Energy.colour
private val GasGlow = CliveColours.Gas.colour

/**
 * The Home page: Tasks, Security, Device health, Who's home, Energy, Clive's devices, then Updates and backups. A tap on a card (not its
 * arrow) opens the page that has more of it: [openHealth], [openEnergy] or [openDevices]; Tasks opens [openManager].
 */
@Composable
fun AdminHome(
    entities: Map<String, EntityState>, registry: Registry, areaNames: Map<String, String>,
    openManager: () -> Unit, openHealth: () -> Unit, openEnergy: () -> Unit, openDevices: () -> Unit,
) {
    val now = remember(entities) { System.currentTimeMillis() }
    val health = remember(entities, registry, areaNames) { deviceHealthList(entities, registry, areaNames) }
    val week = rememberEnergyWeek(entities)
    val clive = rememberWs("church_drive/clive/devices")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HomeTasks(entities, openManager)
        HomeCard("security", "Security", securitySummary(entities, registry, now), ring = true)
        HomeCard("health", "Device health", healthSummary(health), ring = true, onTap = openHealth)
        HomeCard("people", "Who's home", peopleSummary(entities, registry), ring = false, foldable = false)
        energySummary(entities, now).let { e ->
            HomeCard("energy", "Energy", e.copy(items = e.items + weekItems(week)), ring = false, onTap = openEnergy)
        }
        HomeCard("clive", "Clive's devices", cliveDevicesSummary(clive.data, now), ring = false)
        HomeCard("system", "Updates and backups", systemSummary(entities, now), ring = false, onTap = openDevices)
    }
}

/** One card of the Home page. [ring] draws the status ring with the headline beside it; otherwise the headline is one line under the title. */
@Composable
private fun HomeCard(key: String, title: String, s: HomeSummary, ring: Boolean, foldable: Boolean = true, onTap: (() -> Unit)? = null) {
    val colours = toneColors(s.tone)
    val fold = rememberFold("admin-home|$key", true)
    val open = !foldable || !fold.value
    // Open: the band over rows 2 and 3 (as the alarm card), or none for a plain card, then a row every three quarters of a cell and half a cell under the last.
    val top = if (ring) 3f else 1f
    val rows = if (open) Math.ceil(((top + s.items.sumOf { it.height.toDouble() } + 0.5) * 2.0)).toFloat() / 2f else if (ring) 2.5f else 2f
    val tap = if (onTap != null) Modifier.clip(RoundedCornerShape(28.dp)).clickable(onClick = onTap) else Modifier
    GridCard(colours.container, colours.onContainer, rows, title, tap, fold = if (foldable) fold else null) {
        val ink = colours.onContainer
        if (ring) {
            if (open) {
                Box(Modifier.atCentre(1.25f, 2f, 1.5f, 1.5f), contentAlignment = Alignment.Center) { StatusRing(colours.accent, 1.5f, cell, s.icon, 40) }
                Column(Modifier.at(2.5f, 1f, 5f, 2f), verticalArrangement = Arrangement.Center) {
                    Text(s.line1, style = MaterialTheme.typography.titleMedium, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (s.line2.isNotBlank()) Text(s.line2, style = MaterialTheme.typography.bodyMedium, color = ink.copy(alpha = 0.85f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            } else {
                Box(Modifier.atCentre(1f, 1.5f, 1f, 1f), contentAlignment = Alignment.Center) { StatusRing(colours.accent, 1f, cell, s.icon, 26) }
                Column(Modifier.at(2f, 0.9f, 5.5f, 1.2f), verticalArrangement = Arrangement.Center) {
                    Text(s.line1, style = MaterialTheme.typography.bodyMedium, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (s.line2.isNotBlank()) Text(s.line2, style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        } else if (!open) {
            Column(Modifier.at(0.5f, 0.9f, 7f, 0.9f), verticalArrangement = Arrangement.Center) {
                Text(s.line1, style = MaterialTheme.typography.bodyMedium, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (s.line2.isNotBlank()) Text(s.line2, style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (open) {
            var y = top
            s.items.forEach { item ->
                when (item) {
                    is HomeItem.Row -> HomeRow(item, colours, Modifier.at(0.5f, y, 7f, 0.75f))
                    is HomeItem.Head -> Row(Modifier.at(0.5f, y, 7f, 0.75f), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(item.name, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = ink.copy(alpha = 0.8f), maxLines = 1)
                        Text(item.value, style = MaterialTheme.typography.labelLarge, color = ink.copy(alpha = 0.8f), maxLines = 1)
                    }
                    is HomeItem.Balance -> Column(Modifier.at(0.5f, y, 7f, 1f), verticalArrangement = Arrangement.Center) {
                        Canvas(Modifier.fillMaxWidth().height(8.dp)) {
                            val e = (size.width - 2.dp.toPx()) * item.electricityPercent / 100f
                            drawRoundRect(ElectricityGlow, Offset(0f, 0f), Size(e, size.height), androidx.compose.ui.geometry.CornerRadius(size.height / 2))
                            drawRoundRect(GasGlow, Offset(e + 2.dp.toPx(), 0f), Size(size.width - e - 2.dp.toPx(), size.height), androidx.compose.ui.geometry.CornerRadius(size.height / 2))
                        }
                        Row(Modifier.fillMaxWidth().height(20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                            Text(item.left, style = MaterialTheme.typography.labelMedium, color = ElectricityGlow)
                            Text(item.right, style = MaterialTheme.typography.labelMedium, color = GasGlow)
                        }
                    }
                }
                y += item.height
            }
        }
    }
}

/** A line on a card: icon, name, value. A warning or a fault takes its colour for the icon and the value. */
@Composable
private fun HomeRow(item: HomeItem.Row, colours: ToneColors, modifier: Modifier) {
    val colour = when (item.level) {
        HealthLevel.Ok -> colours.accent
        HealthLevel.Warn -> toneColors(Tone.Amber).accent
        HealthLevel.Bad -> toneColors(Tone.Red).accent
    }
    val valueColour = if (item.level == HealthLevel.Ok) colours.onContainer.copy(alpha = 0.8f) else colour
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        HaIcon(item.icon, CdVectors.Info, colour, 20.dp)
        Text(item.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = colours.onContainer, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            item.value, style = MaterialTheme.typography.bodyMedium, color = valueColour, maxLines = 1,
            fontStyle = if (item.value == "Data not available") androidx.compose.ui.text.font.FontStyle.Italic else null,
        )
    }
}

/** The status ring a card's headline sits beside: a full ring in the card's strong colour with the card's icon in it. */
@Composable
private fun StatusRing(colour: Color, sizeCells: Float, cell: androidx.compose.ui.unit.Dp, icon: String, iconDp: Int) {
    Canvas(Modifier.size(cell * sizeCells)) {
        val stroke = 5.dp.toPx() * sizeCells / 1.5f
        val inset = stroke / 2
        drawArc(colour, -90f, 360f, false, Offset(inset, inset), Size(size.width - stroke, size.height - stroke), style = Stroke(stroke, cap = StrokeCap.Round))
    }
    HaIcon(icon, CdVectors.Info, colour, iconDp.dp)
}

/**
 * Tasks, at the top and always there as on the main app's Home: up to three lines (the house's jobs for this person, then the edits waiting
 * on the website), the rest behind "+N more"; the button opens the Manager, where the jobs and their lists are.
 */
@Composable
private fun HomeTasks(entities: Map<String, EntityState>, openManager: () -> Unit) {
    val me = LocalUserName.current
    val first = me?.trim()?.substringBefore(' ')
    val house = rememberTodoItems(HOUSE_LIST, entities)
    val jobs = house?.let { todoHighlights(mapOf(HOUSE_LIST to it), first) }.orEmpty()
    val edits = editsWaiting(entities)
    val purple = toneColors(Tone.Tasks)
    val red = toneColors(Tone.Red)
    val green = toneColors(Tone.Green)
    val coral = toneColors(Tone.TodoHouse)
    val lines = jobs.map { Triple(it.text, it.icon, if (it.overdue) red else coral) } + edits.map { Triple(it, "mdi:pencil", purple) }
    val shown = lines.take(3)
    val extra = lines.size - shown.size
    EntityCard(purple.container, purple.onContainer) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (shown.isEmpty()) {
                    val loaded = house != null
                    HighlightPill(if (loaded) "Nothing waiting on you" else "Loading…", if (loaded) "mdi:check-circle-outline" else "mdi:format-list-checks", if (loaded) green else purple, null, openManager)
                }
                shown.forEach { (text, icon, tone) -> HighlightPill(text, icon, tone, null, openManager) }
            }
            Column(
                Modifier.width(58.dp).fillMaxHeight().background(purple.accent.copy(alpha = 0.22f), RoundedCornerShape(12.dp)).clickable { openManager() },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                HaIcon("mdi:format-list-checks", CdVectors.Info, purple.onContainer, 22.dp)
                Text(if (extra > 0) "+$extra more" else "To-do", color = purple.onContainer, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
        }
    }
}

/** The last seven finished days, read from the house's long-term statistics and read again every half hour; null until they have been read. */
@Composable
private fun rememberEnergyWeek(entities: Map<String, EntityState>): WeekSummary? {
    val api = LocalHaApi.current
    val elec = Octopus.find(entities, Octopus.electricityRate)?.entityId
    val gas = Octopus.find(entities, Octopus.gasRate)?.entityId
    val ids = remember(elec, gas) { EnergyWeek.ids(elec, gas) }
    val zone = remember { ZoneId.systemDefault() }
    var result by remember(ids) { mutableStateOf<Any?>(null) }
    LaunchedEffect(ids, api) {
        if (api == null || ids.all().isEmpty()) return@LaunchedEffect
        while (true) {
            val params = JSONObject()
                .put("start_time", LocalDate.now(zone).minusDays(10).atStartOfDay(zone).toInstant().toString())
                .put("statistic_ids", JSONArray(ids.all()))
                .put("period", "day")
                .put("types", JSONArray(listOf("change")))
            api.ask("recorder/statistics_during_period", params)?.let { result = it }
            delay(30 * 60_000L)
        }
    }
    val data = result ?: return null
    return remember(data) { EnergyWeek.build(data, ids, System.currentTimeMillis(), zone) }
}
