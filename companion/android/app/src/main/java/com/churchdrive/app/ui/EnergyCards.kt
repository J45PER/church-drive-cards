package com.churchdrive.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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

/** The dashboard's Octopus card: `show` picks electricity, gas, last_day, cheap or octoplus. */
@Composable
fun OctopusCard(config: JSONObject, entities: Map<String, EntityState>) {
    val tone = toneColors(Tone.Grey)
    val now = remember { System.currentTimeMillis() }
    EntityCard(tone.container, tone.onContainer) {
        when (config.optString("show")) {
            "gas" -> {
                FactRow("Rate", Octopus.rateText(Octopus.find(entities, Octopus.gasRate), 2) + " per kWh", strong = true)
                FactRow("Today so far", Octopus.pounds(Octopus.find(entities, Octopus.gasToday)))
            }
            "last_day" -> {
                FactRow("Electricity", Octopus.pounds(Octopus.find(entities, Octopus.electricityYesterday)) +
                    " · " + Octopus.kwh(Octopus.find(entities, Octopus.electricityYesterdayKwh)))
                FactRow("Gas", Octopus.pounds(Octopus.find(entities, Octopus.gasYesterday)))
            }
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
    val tone = toneColors(Tone.Grey)
    EntityCard(tone.container, tone.onContainer) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SystemRows.rows(entities).forEach { row ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ToneIconName(row.icon, Icons.Filled.Info, toneColors(if (row.bad) Tone.Red else Tone.Green), 36)
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
