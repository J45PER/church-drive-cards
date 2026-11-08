package com.churchdrive.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.churchdrive.app.ha.EntityState

// The dashboard's Octopus charging plan card: what Octopus has planned for the car it controls (Intelligent Octopus Go), with its own card
// so it sits under the Cars and the charger on the Home and Energy pages. Nothing at all when the house has no Octopus Intelligent device.

@Composable
fun OctopusPlanCard(entities: Map<String, EntityState>) {
    val now = System.currentTimeMillis()
    val plan = octopusPlan(entities, now) ?: return
    val first = plan.slots.firstOrNull()
    val tone = toneColors(Tone.Energy)
    // Grid card: the title on row 1, a line per slot half a cell apart (the slot being used now in a pill), two and a half rows for one slot, half a row more for each.
    val shown = plan.slots.take(6)
    val rows = if (shown.isEmpty()) 3f else 2f + 0.5f * shown.size
    GridCard(tone.container, tone.onContainer, rows, "Octopus Charging Plan") {
        if (shown.isEmpty()) {
            Text("Nothing planned yet. Octopus plans once the car is plugged in.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.at(0.5f, 1.25f, 7f, 1.5f))
        } else shown.forEachIndexed { k, slot ->
            val y = 1.5f + 0.5f * k
            Box(Modifier.at(0.5f, y - 0.25f, 7f, 0.5f), contentAlignment = Alignment.CenterStart) {
                if (slot.start <= now) StatusPill(planLine(slot, now), tone)
                else Text(planLine(slot, now), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
