package com.churchdrive.app.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.churchdrive.app.ha.CallService
import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ha.data

// Zappi smart charge, set up in Home Assistant (ha/zappi-smart-charge.yaml in the cards repo): it charges a plugged-in car at
// the cheapest Octopus rate by itself. The charger card has its switch, and pressing Start charge (or Fast) at the normal rate
// with the cheap rate coming up asks first: charge at the cheap rate, or now.

const val SMART_ENABLED = "input_boolean.zappi_smart_charge"
const val SMART_STATE = "input_select.zappi_smart_charge_state"
const val CHARGE_LATER_SCRIPT = "script.zappi_charge_later"

/** Smart charge as the house reports it: whether it's on, what it's doing, and whether the "charge later" script exists. */
data class SmartCharge(val on: Boolean, val phase: String?, val later: Boolean)

/** Null when the house hasn't set smart charge up (the card then shows no switch and never asks). */
fun smartCharge(entities: Map<String, EntityState>): SmartCharge? {
    val on = entities[SMART_ENABLED]?.takeIf { it.available } ?: return null
    return SmartCharge(on.state == "on", entities[SMART_STATE]?.takeIf { it.available }?.state, entities[CHARGE_LATER_SCRIPT] != null)
}

/** What the switch says under its name, as on the dashboard's charger card. */
fun smartText(sm: SmartCharge, rates: List<Rate>, now: Long): String {
    if (!sm.on) return "Off: it only charges when you start it"
    val until = Octopus.cheapUntil(rates, now)
    val next = if (until == null) Octopus.nextCheapStart(rates, now) else null
    return when (sm.phase) {
        "Charging" -> if (until != null) "Charging at the cheap rate until ${Octopus.clock(until)}" else "Charging at the cheap rate"
        "Waiting" -> if (next != null) "Waiting for the cheap rate, ${Octopus.clock(next)}" else "Starting at the cheap rate"
        "Done" -> "Finished for this plug-in"
        "Manual" -> "You're in control for this plug-in"
        else -> "Charges at the cheap rate when a car is plugged in"
    }
}

/** The normal rate now and the cheap rate that's coming: [startMs] is when it starts (epoch ms). */
data class ChargeAsk(val nowPence: Double, val cheapPence: Double, val startMs: Long)

/** The cheap window is nearer than this, or it isn't worth waiting for. */
private const val ASK_WITHIN_MS = 18 * 3_600_000L

/**
 * What to ask when Start charge or Fast is pressed, or null to just charge: nothing to ask when smart charge isn't set up,
 * when the rate is already cheap, when the cheap rate is hardly cheaper (under half a penny), or when it's over 18 hours away.
 */
fun chargeAsk(entities: Map<String, EntityState>, now: Long): ChargeAsk? {
    if (smartCharge(entities)?.later != true) return null
    val rates = Octopus.allRates(entities)
    val cur = Octopus.current(rates, now) ?: return null
    val low = Octopus.cheapest(rates) ?: return null
    if (cur.pence <= low + 0.01 || low >= cur.pence - 0.5) return null
    val start = Octopus.nextCheapStart(rates, now) ?: return null
    if (start - now > ASK_WITHIN_MS) return null
    return ChargeAsk(cur.pence, low, start)
}

/** "6 h 12 m", "45 min". */
fun inText(ms: Long): String {
    val m = (ms.coerceAtLeast(0) + 30_000) / 60_000
    return if (m < 60) "$m min" else "${m / 60} h" + if (m % 60 != 0L) " ${m % 60} m" else ""
}

/** The wording of the question. */
fun chargeAskText(ask: ChargeAsk, now: Long): String =
    "It's the normal rate now (${Octopus.pence(ask.nowPence)}p a kWh). The cheap rate (${Octopus.pence(ask.cheapPence)}p) starts at " +
        "${Octopus.clock(ask.startMs)}, in ${inText(ask.startMs - now)}. Charging later hands it to smart charge: it starts by itself " +
        "at the cheap rate and stops when that ends."

/** "Charge at 00:30 (cheap rate)" or "Charge now at 25.3p": the question as a dialog. Tapping outside it cancels. */
@Composable
fun ChargeAskDialog(ask: ChargeAsk, onLater: () -> Unit, onNow: () -> Unit, onCancel: () -> Unit) {
    val now = remember { System.currentTimeMillis() }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Wait for the cheap rate?") },
        text = { Text(chargeAskText(ask, now)) },
        confirmButton = { TextButton(onClick = onLater) { Text("Charge at ${Octopus.clock(ask.startMs)}") } },
        dismissButton = { TextButton(onClick = onNow) { Text("Charge now at ${Octopus.pence(ask.nowPence)}p") } },
    )
}

/** Hands the charge to smart charge: it waits for the cheap rate (or starts at once if it's cheap already). */
fun chargeLater(call: CallService) = call("script", "turn_on", CHARGE_LATER_SCRIPT, data())

/** Start charge: unlock if locked, then Fast (the charger otherwise waits at the unit). */
fun chargeNow(entities: Map<String, EntityState>, call: CallService) {
    if (chargerOverrides(entities).locked == true) call("myenergi", "myenergi_unlock", ZAPPI_MODE, data())
    call("select", "select_option", ZAPPI_MODE, data("option" to "Fast"))
}

/**
 * Opened from the charger widget's Start charge (which can't show a question): asks if it should, else just charges, then
 * calls [onClose]. Waits for the house's states to arrive first.
 */
@Composable
fun ChargePromptHost(entities: Map<String, EntityState>, statesLoaded: Boolean, call: CallService, onClose: () -> Unit) {
    var ask by remember { mutableStateOf<ChargeAsk?>(null) }
    var decided by remember { mutableStateOf(false) }
    LaunchedEffect(statesLoaded) {
        if (!statesLoaded || decided) return@LaunchedEffect
        decided = true
        val found = chargeAsk(entities, System.currentTimeMillis())
        if (found == null) {
            chargeNow(entities, call)
            onClose()
        } else {
            ask = found
        }
    }
    ask?.let {
        ChargeAskDialog(
            it,
            onLater = { chargeLater(call); onClose() },
            onNow = { chargeNow(entities, call); onClose() },
            onCancel = onClose,
        )
    }
}
