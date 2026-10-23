package com.churchdrive.app.wear

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.material.Text
import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ui.Tone

/** The Zappi's modes in the dashboard's order, with their icons. */
private val MODES = listOf("Stopped" to ("Stop" to "stop-circle-outline"), "Eco" to ("Eco" to "leaf"), "Eco+" to ("Eco+" to "solar-power"), "Fast" to ("Fast" to "flash"))

private val MODE_COLOURS = mapOf("Stopped" to Color(0xFF9AA0A6), "Eco" to Color(0xFF81C995), "Eco+" to Color(0xFF4DD0E1), "Fast" to Color(0xFFFDD663))

/** What the charger page knows: the Zappi, and which car (if any) can be said to be on it. */
class Charger(val entities: Map<String, EntityState>, val cars: List<WatchCar>) {
    val mode: EntityState? get() = entities[ZAPPI_MODE]
    val powerKw: Float get() = (entities[ZAPPI_POWER]?.state?.toFloatOrNull() ?: 0f) / 1000f
    val addedKwh: Float? get() = entities[ZAPPI_SESSION]?.state?.toFloatOrNull()
    val charging: Boolean get() = powerKw > 0f

    /** The charger says a car is connected (its plug status isn't "EV Disconnected"). */
    val plugged: Boolean get() = entities[ZAPPI_PLUG]?.takeIf { it.available }?.state?.let { !it.contains("disconnected", ignoreCase = true) } == true

    /**
     * The car whose battery goes on the ring: only when the charger is connected and exactly one car says it is plugged in. With two plugged
     * in (one may be at another charger) or none saying so, it cannot be told, so there is no battery.
     */
    val car: Pair<WatchCar, Float?>?
        get() {
            if (!plugged) return null
            val on = cars.filter { entities[it.plugged]?.state == "on" }
            if (on.size != 1) return null
            return on[0] to entities[on[0].battery]?.state?.toFloatOrNull()
        }

    val smart: Boolean? get() = entities[SMART_ENABLED]?.takeIf { it.available }?.let { it.state == "on" }
    val smartPhase: String? get() = entities[SMART_STATE]?.takeIf { it.available }?.state
    val locked: Boolean? get() = entities[ZAPPI_LOCKED]?.takeIf { it.available }?.let { it.state == "on" }
}

/** What smart charge is doing, in words (the times of the cheap rate are on the phone). */
fun smartWords(on: Boolean, phase: String?): String = if (!on) "Off: it only charges when you start it" else when (phase) {
    "Charging" -> "Charging at the cheap rate"
    "Waiting" -> "Waiting for the cheap rate"
    "Done" -> "Finished for this plug-in"
    "Manual" -> "You're in control for this plug-in"
    else -> "Charges at the cheap rate when a car is plugged in"
}

private fun chargeNow(c: Charger) {
    if (c.locked == true) WatchData.call("myenergi", "myenergi_unlock", ZAPPI_MODE)
    WatchData.call("select", "select_option", ZAPPI_MODE, json("option" to "Fast"))
}

/** The car charger page: the modes with the car's battery on the ring, the charging speed and amount, and smart charge or charge now. */
@Composable
fun carFaces(entities: Map<String, EntityState>, config: WatchConfig): List<Face> {
    val c = Charger(entities, config.cars)
    val out = mutableListOf<Face>()
    if (c.mode == null) return emptyList()
    val teal = tone(Tone.Teal)
    val colours = if (c.charging) teal else tone(Tone.Grey)
    val accent = colours.accent
    val car = c.car
    val battery = car?.second
    // The ring: the battery of the one car known to be on the charger; otherwise, with a car connected, a full arc coloured by mode; with none, nothing.
    val modeNow = c.mode?.state ?: "Stopped"
    val spec = RingSpec(
        bg = colours.container,
        fill = when {
            battery != null -> RingFill((battery / 100f).coerceIn(0f, 1f), teal.accent)
            c.plugged -> RingFill(1f, MODE_COLOURS[modeNow] ?: Color(0xFF9AA0A6), if (modeNow == "Stopped") 0.5f else 1f)
            else -> null
        },
    )
    val status = when {
        !c.plugged -> "No car connected"
        c.charging -> "Charging · ${"%.1f".format(c.powerKw)} kW"
        else -> "Plugged in · ${if (modeNow == "Stopped") "Stopped" else "Waiting"}"
    }
    val offered = c.mode?.options().orEmpty()

    if (!config.hides(WatchPage.Car, "modes")) out += Face("car:modes", spec) {
        val ui = LocalUi.current
        Box(Modifier.fillMaxSize()) {
            FaceHeader("Car Charger", status, accent)
            when {
                car != null && battery != null -> {
                    Column(Modifier.at(ui, 0f, 78f, 280f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${battery.toInt()}%", color = Ink, fontSize = ui.sp(40f), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        Text(car.first.name, color = Ink.copy(alpha = 0.85f), fontSize = ui.sp(11f), textAlign = TextAlign.Center, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                }
                c.plugged -> Note(if (c.cars.size > 1 && c.cars.count { entities[it.plugged]?.state == "on" } > 1) "Two cars plugged in,\ncan't tell which" else "No car says it\nis plugged in", 0f, 84f, 280f, Ink.copy(alpha = 0.75f), 11f, maxLines = 2)
                else -> Note("Plug in to charge", 0f, 92f, 280f, Ink.copy(alpha = 0.75f), 12f)
            }
            ButtonRow(28f, 140f, 62f) {
                for ((key, label) in MODES) {
                    if (offered.isNotEmpty() && key !in offered) continue
                    ModeButton(label.second, label.first, modeNow == key, accent, Ink, Slot(), onClick = {
                        if (key == "Fast") chargeNow(c) else WatchData.call("select", "select_option", ZAPPI_MODE, json("option" to key))
                    })
                }
            }
        }
    }

    if (!config.hides(WatchPage.Car, "stats")) out += Face("car:stats", spec) {
        val ui = LocalUi.current
        Box(Modifier.fillMaxSize()) {
            FaceHeader("Charging", if (c.charging) "Zappi · $modeNow" else "Not charging", accent)
            Column(Modifier.at(ui, 0f, 84f, 280f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${"%.1f".format(c.powerKw)} kW", color = Ink, fontSize = ui.sp(46f), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text("CHARGING SPEED", color = Ink.copy(alpha = 0.8f), fontSize = ui.sp(11f), textAlign = TextAlign.Center)
            }
            Column(Modifier.at(ui, 0f, 158f, 280f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(c.addedKwh?.let { "%.1f kWh".format(it) } ?: "– kWh", color = Ink, fontSize = ui.sp(32f), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text("ADDED THIS SESSION", color = Ink.copy(alpha = 0.8f), fontSize = ui.sp(11f), textAlign = TextAlign.Center)
            }
        }
    }

    val smart = c.smart
    if (smart != null && !config.hides(WatchPage.Car, "smart")) out += Face("car:smart", spec) {
        val ui = LocalUi.current
        Box(Modifier.fillMaxSize()) {
            FaceHeader("Charge when?", if (smart) "Smart charge" else "Charge now", accent)
            ButtonRow(34f, 92f, 76f) {
                ModeButton("clock-fast", "Smart charge", smart, accent, Ink, Slot(), onClick = { WatchData.call("input_boolean", "turn_on", SMART_ENABLED) })
                ModeButton("flash", "Charge now", !smart, accent, Ink, Slot(), onClick = {
                    WatchData.call("input_boolean", "turn_off", SMART_ENABLED)
                    chargeNow(c)
                })
            }
            Note(smartWords(smart, c.smartPhase), 52f, 184f, 176f, Ink.copy(alpha = 0.85f), 12f)
        }
    }
    return out
}
