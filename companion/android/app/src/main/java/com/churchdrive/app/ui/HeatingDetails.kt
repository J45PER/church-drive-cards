package com.churchdrive.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.churchdrive.app.ha.CallService
import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ha.Registry

// Heating details: press and hold any thermostat and this page opens over the app. At the top each thermostat's status (what it is doing, the
// temperature now, its target, and the room it is in); under them the house's overnight schedule, moved here from the Climate page, with its Edit.
// The Clive settings for a thermostat stay in the pane that slides up from the line at the bottom of its card.

/** What a thermostat's card does when pressed and held: opens Heating details. Null outside the app, where the card opens its device page. */
val LocalOpenHeating = compositionLocalOf<(() -> Unit)?> { null }

/** The word for what a thermostat is doing: Off, Eco, Heating or Idle (else its mode). */
fun heatingStatusWord(state: String, action: String, preset: String): String = when {
    state == "off" -> "Off"
    preset == "eco" -> "Eco"
    action == "heating" -> "Heating"
    action == "idle" -> "Idle"
    state.isBlank() -> "Unknown"
    else -> state.replaceFirstChar { it.uppercase() }
}

/** The thermostats to show: every available climate entity, by name. */
fun heatingThermostats(entities: Map<String, EntityState>): List<EntityState> =
    entities.values.filter { it.entityId.startsWith("climate.") && it.available }.sortedBy { it.friendlyName.lowercase() }

/** The page itself, over the app; Back or the arrow closes it. */
@Composable
fun HeatingDetailsOverlay(entities: Map<String, EntityState>, registry: Registry, areaNames: Map<String, String>, call: CallService, isAdmin: Boolean, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        CompositionLocalProvider(LocalIsAdmin provides isAdmin) {
            Column(
                Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                PanelTitle("Heating", onClose)
                heatingThermostats(entities).forEach { e -> ThermostatStatusCard(e, registry.areaOf(e.entityId)?.let { areaNames[it] ?: it }) }
                OvernightHeatingCard(entities, call)
            }
        }
    }
}

/** One thermostat's status: its name and what it is doing, the temperature now and the target, and the room it is in. */
@Composable
private fun ThermostatStatusCard(e: EntityState, room: String?) {
    val tone = toneColors(climateTone(e))
    val ink = tone.onContainer
    val status = heatingStatusWord(e.state, e.attributes.optString("hvac_action"), e.attributes.optString("preset_mode"))
    EntityCard(tone.container, ink) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(e.friendlyName, style = MaterialTheme.typography.headlineSmall, color = ink, modifier = Modifier.weight(1f))
            StatusPill(status, tone)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(32.dp), verticalAlignment = Alignment.Bottom) {
            Column {
                Text(e.num("current_temperature")?.let { "${temp(it)}°" } ?: "–", style = MaterialTheme.typography.displaySmall, color = ink)
                Text("Now", style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.8f))
            }
            Column {
                Text(e.num("temperature")?.let { "${temp(it)}°" } ?: "–", style = MaterialTheme.typography.headlineMedium, color = ink)
                Text("Target", style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.8f))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HaIcon("cd:home", CdVectors.Info, ink.copy(alpha = 0.8f), 20.dp)
            Text(room ?: "Room not set in Home Assistant", style = MaterialTheme.typography.bodyMedium, color = ink.copy(alpha = 0.9f))
        }
    }
}
