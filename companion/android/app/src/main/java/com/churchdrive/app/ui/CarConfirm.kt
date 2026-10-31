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
import org.json.JSONObject

// "Which car is plugged in?": every time a car is plugged in (or unplugged and plugged in again) the house asks which car it is, and
// nothing unlocks or starts it until someone answers (set up in Home Assistant: the "Church Drive: Zappi car confirmation" automation
// and the two scripts below). Primary is the car on the Octopus app (today the ID.5): it charges in the cheap window and whenever
// Octopus schedules it. Additional is any other car: it charges in the cheap window only. The same question comes as a notification
// with Primary, Additional and No buttons; this is the Home page's copy of it, and the charger card has the same choice to change it.

const val ZAPPI_CONFIRMED = "input_boolean.zappi_car_confirmed"
const val ZAPPI_CAR_ROLE = "input_select.zappi_car_role"
const val CAR_YES_SCRIPT = "script.zappi_car_yes"
const val CAR_NO_SCRIPT = "script.zappi_car_no"
const val CAR_ROLE_PRIMARY = "Primary"
const val CAR_ROLE_ADDITIONAL = "Additional"

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

/** Which car is on the charger: its role (Primary, Additional or Unknown) and whether somebody has confirmed it. */
data class CarRoleChoice(val role: String, val confirmed: Boolean)

/** The charger card's "Car plugged in" choice: only while a car is plugged in, and only when the house has the role helper. */
fun carRoleChoice(entities: Map<String, EntityState>): CarRoleChoice? {
    val role = entities[ZAPPI_CAR_ROLE]?.takeIf { it.available } ?: return null
    if (!chargerOverrides(entities).show) return null
    val confirmed = entities[ZAPPI_CONFIRMED]?.takeIf { it.available }?.state != "off"
    return CarRoleChoice(role.state, confirmed)
}

/** What the role means, under the choice (the same words as the dashboard's charger card). */
fun carRoleText(c: CarRoleChoice): String = when {
    c.role == CAR_ROLE_PRIMARY -> "Primary: also charges when Octopus schedules it"
    c.role == CAR_ROLE_ADDITIONAL -> "Additional: charges 23:30 to 05:30 only"
    c.confirmed -> "Not set: charges 23:30 to 05:30 only"
    else -> "Not answered yet, so it will not charge"
}

/**
 * Choose which car is plugged in. A car nobody has answered for is confirmed by the choice (the Yes script, with the role); one that is
 * already confirmed only changes its role, so a charge somebody took over by hand carries on.
 */
fun chooseCarRole(call: CallService, role: String, confirmed: Boolean) {
    if (confirmed) call("input_select", "select_option", ZAPPI_CAR_ROLE, data("option" to role))
    else call("script", "turn_on", CAR_YES_SCRIPT, data("variables" to JSONObject().put("role", role)))
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
                Text("Which car is plugged in?", style = MaterialTheme.typography.titleMedium)
                Text("A car is plugged in. It only charges once you choose.", style = MaterialTheme.typography.bodySmall)
            }
            TileRow(
                listOf(
                    TileItem("mdi:car-electric", CAR_ROLE_PRIMARY, true) {
                        answered = true
                        chooseCarRole(call, CAR_ROLE_PRIMARY, false)
                    },
                    TileItem("mdi:car", CAR_ROLE_ADDITIONAL, true) {
                        answered = true
                        chooseCarRole(call, CAR_ROLE_ADDITIONAL, false)
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
