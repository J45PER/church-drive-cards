package com.churchdrive.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.churchdrive.app.ha.EntityState

// The dashboard's Octopus charging plan card: what Octopus has planned for the car it controls (Intelligent Octopus Go), with its own card
// so it sits under the Cars and the charger on the Home and Energy pages. Nothing at all when the house has no Octopus Intelligent device.

@Composable
fun OctopusPlanCard(entities: Map<String, EntityState>) {
    val now = System.currentTimeMillis()
    val plan = octopusPlan(entities, now) ?: return
    val first = plan.slots.firstOrNull()
    val tone = toneColors(if (first != null && first.start <= now) Tone.Teal else Tone.Grey)
    EntityCard(tone.container, tone.onContainer) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Octopus charging plan", style = MaterialTheme.typography.titleMedium)
            if (plan.slots.isEmpty()) Text("Nothing planned yet. Octopus plans once the car is plugged in.", style = MaterialTheme.typography.bodyMedium)
            else plan.slots.take(6).forEach { Text(planLine(it, now), style = MaterialTheme.typography.bodyMedium) }
        }
    }
}
