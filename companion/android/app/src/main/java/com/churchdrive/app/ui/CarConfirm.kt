package com.churchdrive.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.churchdrive.app.ha.CallService
import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ha.data

// "Confirm car to charge tonight": every time a car is plugged in (or unplugged and plugged in again) the house asks whether it
// should be charged, and nothing unlocks or starts it until someone says Yes (set up in Home Assistant: the "Church Drive: Zappi car
// confirmation" automation and the two scripts below). The same question comes as a notification with Yes and No buttons; this is
// the Home page's copy of it, with the same two buttons.

const val ZAPPI_CONFIRMED = "input_boolean.zappi_car_confirmed"
const val CAR_YES_SCRIPT = "script.zappi_car_yes"
const val CAR_NO_SCRIPT = "script.zappi_car_no"

/**
 * Whether to ask: a car is plugged in, nobody has answered for it, and smart charge is waiting on it. Never while it is already
 * charging (a car someone started on purpose carries on), and not after No (smart charge then says Manual).
 */
fun carNeedsConfirm(entities: Map<String, EntityState>): Boolean {
    val plug = entities[ZAPPI_PLUG]?.takeIf { it.available }?.state ?: return false
    if (plug.contains("disconnected", ignoreCase = true) || plug.startsWith("charging", ignoreCase = true)) return false
    if (entities[ZAPPI_CONFIRMED]?.takeIf { it.available }?.state != "off") return false
    return entities[SMART_STATE]?.takeIf { it.available }?.state == "Waiting"
}

/** The card on the Home page, above the panels and below the to-do highlights: nothing at all when there is nothing to ask. */
@Composable
fun CarConfirmCard(entities: Map<String, EntityState>, call: CallService) {
    val needed = carNeedsConfirm(entities)
    // Hides at once on a tap, then follows the house (which turns the question off itself).
    var answered by remember { mutableStateOf(false) }
    LaunchedEffect(needed) { if (!needed) answered = false }
    if (!needed || answered) return
    val amber = toneColors(Tone.Amber)
    EntityCard(amber.container, amber.onContainer) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Confirm car to charge tonight", style = MaterialTheme.typography.titleMedium)
                Text("A car is plugged in. It only charges if you tap Yes.", style = MaterialTheme.typography.bodySmall)
            }
            TileRow(
                listOf(
                    TileItem("mdi:check", "Yes", true) {
                        answered = true
                        call("script", "turn_on", CAR_YES_SCRIPT, data())
                    },
                    TileItem("mdi:close", "No", false) {
                        answered = true
                        call("script", "turn_on", CAR_NO_SCRIPT, data())
                    },
                ),
                amber, amber.onContainer,
            )
        }
    }
}
