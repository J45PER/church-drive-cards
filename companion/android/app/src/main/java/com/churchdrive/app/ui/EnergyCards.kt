package com.churchdrive.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.delay
import org.json.JSONArray
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.churchdrive.app.ha.EntityState
import org.json.JSONObject

/** A label on the left and its value on the right, the Energy card's basic row. */
@Composable
private fun FactRow(label: String, value: String, strong: Boolean = false) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(
            value,
            style = if (strong) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
            color = if (strong) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The day's half-hourly prices as bars: the cheapest in green, now in a stronger colour. */
@Composable
private fun RateStrip(rates: List<Rate>, now: Long, accent: Color, cheap: Color, plain: Color) {
    if (rates.isEmpty()) return
    val top = rates.maxOf { it.pence }.coerceAtLeast(1.0)
    val low = rates.minOf { it.pence }
    Canvas(Modifier.fillMaxWidth().height(60.dp)) {
        val gap = 2f
        val w = (size.width - gap * (rates.size - 1)) / rates.size
        rates.forEachIndexed { i, r ->
            val h = (size.height * (r.pence / top)).toFloat().coerceAtLeast(4f)
            val isNow = r.start <= now && now < r.end
            val colour = when {
                isNow -> accent
                r.pence <= low + 0.01 -> cheap
                else -> plain
            }
            drawRect(colour, Offset(i * (w + gap), size.height - h), Size(w, h))
        }
    }
}

/** The two fuels' colours, the same as the Energy page's electricity and gas sections on the dashboard. */
private val ElectricityColour = Color(0xFFF050F8)
private val GasColour = Color(0xFF7B61FF)

/**
 * The last seven finished days, read from the long-term statistics the Octopus integration keeps in Home Assistant (so days Octopus
 * sends late fill in): a stacked bar per day in the electricity and gas colours, the week's balance between them, one day's
 * figures (tap a day), and how far behind Octopus is.
 */
@Composable
private fun OctopusWeek(entities: Map<String, EntityState>, tone: ToneColors) {
    val api = LocalHaApi.current
    val elec = Octopus.find(entities, Octopus.electricityRate)?.entityId
    val gas = Octopus.find(entities, Octopus.gasRate)?.entityId
    val ids = remember(elec, gas) { EnergyWeek.ids(elec, gas) }
    val zone = remember { ZoneId.systemDefault() }
    var result by remember(ids) { mutableStateOf<Any?>(null) }
    var failed by remember(ids) { mutableStateOf(false) }
    var selected by remember(ids) { mutableStateOf<LocalDate?>(null) }
    LaunchedEffect(ids, api) {
        if (api == null || ids.all().isEmpty()) return@LaunchedEffect
        while (true) {
            val params = JSONObject()
                .put("start_time", LocalDate.now(zone).minusDays(10).atStartOfDay(zone).toInstant().toString())
                .put("statistic_ids", JSONArray(ids.all()))
                .put("period", "day")
                .put("types", JSONArray(listOf("change")))
            val r = api.ask("recorder/statistics_during_period", params)
            if (r != null) {
                result = r
                failed = false
            } else if (result == null) failed = true
            delay(30 * 60_000L)
        }
    }
    if (ids.all().isEmpty()) {
        Text("No Octopus meter found.", style = MaterialTheme.typography.bodyMedium)
        return
    }
    val data = result
    if (data == null) {
        Text(if (failed) "Couldn't read the Octopus history from the house." else "Reading the Octopus history…", style = MaterialTheme.typography.bodyMedium)
        return
    }
    val week = EnergyWeek.build(data, ids, System.currentTimeMillis(), zone)
    if (week.days.none { it.hasData }) {
        Text("Octopus hasn't sent any of the last seven days yet.", style = MaterialTheme.typography.bodyMedium)
        return
    }
    val label = { d: LocalDate -> d.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.UK)) }
    val pick = week.days.firstOrNull { it.date == selected } ?: week.days.first { it.date == week.latestWithData }
    val max = week.days.maxOf { it.total }.coerceAtLeast(0.01)
    FactRow("Last 7 days", EnergyWeek.pounds(week.cost) + " · " + EnergyWeek.pounds(week.average) + " a day", strong = true)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        week.days.forEach { d ->
            Column(
                Modifier.weight(1f)
                    .background(if (d == pick) tone.onContainer.copy(alpha = 0.12f) else Color.Transparent, RoundedCornerShape(10.dp))
                    .clickable { selected = d.date }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(if (d.hasData) EnergyWeek.pounds(d.total) else "–", style = MaterialTheme.typography.labelSmall, color = tone.onContainer.copy(alpha = 0.7f))
                Canvas(Modifier.fillMaxWidth(0.7f).height(72.dp)) {
                    if (!d.hasData) {
                        drawRect(
                            tone.onContainer.copy(alpha = 0.35f), style = Stroke(2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))),
                        )
                    } else {
                        val gasH = (size.height * ((d.gasCost ?: 0.0) / max)).toFloat()
                        val elecH = (size.height * ((d.elecCost ?: 0.0) / max)).toFloat()
                        drawRect(GasColour, Offset(0f, size.height - gasH), Size(size.width, gasH))
                        drawRect(ElectricityColour, Offset(0f, size.height - gasH - elecH), Size(size.width, elecH))
                    }
                }
                Text(d.date.format(DateTimeFormatter.ofPattern("EEE", Locale.UK)), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
            }
        }
    }
    EnergyWeek.balance(week)?.let { b ->
        Canvas(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp))) {
            val e = size.width * b.elec / 100f
            drawRect(ElectricityColour, Offset(0f, 0f), Size(e, size.height))
            drawRect(GasColour, Offset(e, 0f), Size(size.width - e, size.height))
        }
        Text(
            "Electricity ${EnergyWeek.pounds(week.elecCost)} · ${b.elec}% · ${EnergyWeek.kwh(week.elecKwh)}",
            style = MaterialTheme.typography.bodyMedium, color = ElectricityColour,
        )
        Text(
            "Gas ${EnergyWeek.pounds(week.gasCost)} · ${b.gas}% · ${EnergyWeek.kwh(week.gasKwh)}",
            style = MaterialTheme.typography.bodyMedium, color = GasColour,
        )
    }
    Text(label(pick.date), style = MaterialTheme.typography.titleSmall)
    FactRow("Electricity", EnergyWeek.pounds(pick.elecCost) + (pick.elecKwh?.let { " · " + EnergyWeek.kwh(it) } ?: ""))
    FactRow("Gas", EnergyWeek.pounds(pick.gasCost) + (pick.gasKwh?.let { " · " + EnergyWeek.kwh(it) } ?: ""))
    pick.cheapShare?.let {
        Text("$it% of that day's electricity was used at the cheap rate.", style = MaterialTheme.typography.bodySmall, color = tone.onContainer.copy(alpha = 0.75f))
    }
    EnergyWeek.lagNote(week, label).takeIf { it.isNotEmpty() }?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = toneColors(Tone.Amber).accent)
    }
}

/** The dashboard's Octopus card: `show` picks electricity, gas, week (last 7 days), cheap or octoplus. */
@Composable
fun OctopusCard(config: JSONObject, entities: Map<String, EntityState>) {
    val tone = panelNeutral()
    val now = remember { System.currentTimeMillis() }
    EntityCard(tone.container, tone.onContainer) {
        when (config.optString("show")) {
            "gas" -> {
                FactRow("Rate", Octopus.rateText(Octopus.find(entities, Octopus.gasRate), 2) + " per kWh", strong = true)
                FactRow("Today so far", Octopus.pounds(Octopus.find(entities, Octopus.gasToday)))
            }
            // "last_day" is what the card was called before it showed seven days.
            "week", "last_day" -> OctopusWeek(entities, tone)
            "octoplus" -> FactRow("Points", Octopus.find(entities, Octopus.points)?.state ?: "–", strong = true)
            "cheap" -> {
                val rates = Octopus.allRates(entities)
                Text(Octopus.rateLine(rates, now), style = MaterialTheme.typography.titleMedium)
                RateStrip(rates, now, MaterialTheme.colorScheme.primary, toneColors(Tone.Green).accent, tone.onContainer.copy(alpha = 0.3f))
            }
            else -> {
                val rates = Octopus.allRates(entities)
                val offPeak = Octopus.find(entities, Octopus.offPeak)?.state == "on"
                FactRow("Rate", Octopus.rateText(Octopus.find(entities, Octopus.electricityRate)) + if (offPeak) " · Cheap" else " · Peak", strong = true)
                FactRow("Using now", Octopus.power(Octopus.find(entities, Octopus.demand)))
                FactRow("Today so far", Octopus.pounds(Octopus.find(entities, Octopus.electricityToday)) +
                    " · " + Octopus.kwh(Octopus.find(entities, Octopus.electricityTodayKwh)))
                RateStrip(rates, now, MaterialTheme.colorScheme.primary, toneColors(Tone.Green).accent, tone.onContainer.copy(alpha = 0.3f))
            }
        }
    }
}

/** The dashboard's System card for an administrator: internet, remote access, backups and updates. */
@Composable
fun SystemCard(entities: Map<String, EntityState>) {
    val tone = panelNeutral()
    EntityCard(tone.container, tone.onContainer) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SystemRows.rows(entities).forEach { row ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ToneIconName(row.icon, CdVectors.Info, toneColors(if (row.bad) Tone.Red else Tone.Green), 36)
                    Column(Modifier.weight(1f)) {
                        Text(row.name, style = MaterialTheme.typography.bodyLarge)
                        Text(row.sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    row.pill?.let {
                        Text(it, style = MaterialTheme.typography.labelLarge, color = toneColors(if (row.bad) Tone.Red else Tone.Amber).accent)
                    }
                }
            }
        }
    }
}
