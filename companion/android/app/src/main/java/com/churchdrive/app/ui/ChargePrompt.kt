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

/** "6 h 12 m", "45 min". */
fun inText(ms: Long): String {
    val m = (ms.coerceAtLeast(0) + 30_000) / 60_000
    return if (m < 60) "$m min" else "${m / 60} h" + if (m % 60 != 0L) " ${m % 60} m" else ""
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
