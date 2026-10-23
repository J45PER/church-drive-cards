package com.churchdrive.app.wear

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.material.Text
import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ui.Tone
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val HEAT = Color(0xFFFFAB70)
private val COOL = Color(0xFF8AB4F8)
private val IDLE = Color(0xFF9AA0A6)

/** The climate page: every thermostat, air con, fan and air purifier the house has, one face each (an air con has a second for its fan speeds). */
@Composable
fun climateFaces(entities: Map<String, EntityState>, config: WatchConfig): List<Face> {
    val climates = entities.values.filter { it.entityId.startsWith("climate.") && !config.hides(WatchPage.Climate, it.entityId) }.sortedBy { it.friendlyName }
    val fans = entities.values.filter { it.entityId.startsWith("fan.") && !config.hides(WatchPage.Climate, it.entityId) }.sortedBy { it.friendlyName }
    val out = mutableListOf<Face>()
    for (c in climates) {
        val ac = isAirCon(c)
        out += key(c.entityId) { thermostatFace(c, ac) }
        if (ac && c.strings("fan_modes").isNotEmpty()) out += key("fan:${c.entityId}") { airConFanFace(c) }
    }
    for (f in fans) out += key(f.entityId) { if (isPurifier(f)) purifierFace(f, entities["sensor.${f.entityId.removePrefix("fan.")}_pm2_5"]) else fanFace(f) }
    return out
}

/** Whether a climate device is an air con (it can cool, dry or blow) rather than a heating thermostat. */
fun isAirCon(c: EntityState): Boolean = c.strings("hvac_modes").any { it == "cool" || it == "dry" || it == "fan_only" }

fun isPurifier(f: EntityState): Boolean = "purifier" in f.entityId.lowercase()

/** What the device is doing, for the colour of the ring: heating, cooling, idle or off. */
private fun action(c: EntityState): String {
    if (c.state == "off") return "off"
    when (c.str("hvac_action")) {
        "heating", "preheating" -> return "heating"
        "cooling" -> return "cooling"
        "idle", "off", "drying", "fan" -> return "idle"
    }
    val cur = c.num("current_temperature") ?: return "idle"
    val target = c.num("temperature") ?: return "idle"
    return when (c.state) {
        "cool" -> if (cur > target) "cooling" else "idle"
        "heat" -> if (cur < target) "heating" else "idle"
        else -> "idle"
    }
}

private fun arcColour(c: EntityState): Color = when (action(c)) {
    "heating" -> HEAT
    "cooling" -> COOL
    else -> IDLE
}

private fun climateWord(c: EntityState, eco: Boolean): String {
    val now = c.num("current_temperature")?.let { " · now ${temp(it.toFloat())}°" }.orEmpty()
    return when {
        c.state == "off" -> "Off$now"
        c.state == "fan_only" -> "Fan only$now"
        action(c) == "heating" -> "Heating$now"
        action(c) == "cooling" -> "Cooling$now"
        else -> (if (eco) "Eco" else "At target") + now
    }
}

@Composable
private fun thermostatFace(c: EntityState, ac: Boolean): Face {
    val pending = rememberPending(c.entityId)
    val scope = rememberCoroutineScope()
    val off = c.state == "off"
    val eco = c.str("preset_mode") == "eco" && c.strings("preset_modes").contains("eco")
    val min = (c.num("min_temp") ?: if (ac) 16.0 else 10.0).toFloat()
    val max = (c.num("max_temp") ?: if (ac) 30.0 else 32.0).toFloat()
    val step = (c.num("target_temp_step") ?: if (ac) 1.0 else 0.5).toFloat()
    val serverTarget = (c.num("temperature") ?: c.num("target_temp_low"))?.toFloat() ?: if (ac) 22f else 19.5f
    val target = pending.value ?: serverTarget
    val current = c.num("current_temperature")?.toFloat()
    val colour = arcColour(c)
    val toneColours = tone(
        when {
            off -> Tone.Grey
            ac && c.state == "cool" -> Tone.Blue
            ac && c.state == "fan_only" -> Tone.Teal
            eco -> Tone.Green
            else -> Tone.Orange
        },
    )
    val span = (max - min).coerceAtLeast(1f)

    fun sendTarget(v: Float) {
        scope.launch {
            // A thermostat in Eco ignores a new target (its number is the Eco setpoint), so leave Eco first.
            if (eco && c.strings("preset_modes").contains("none")) {
                WatchData.call("climate", "set_preset_mode", c.entityId, json("preset_mode" to "none"))
                delay(1_200)
            }
            WatchData.call("climate", "set_temperature", c.entityId, json("temperature" to v.toDouble()))
        }
    }
    fun nudge(by: Float) = pending.set(snapTo(target + by, step, min, max), 600) { sendTarget(it) }

    val spec = RingSpec(
        bg = toneColours.container,
        fill = current?.let { RingFill(((it - min) / span).coerceIn(0f, 1f), colour, if (off) 0.55f else 1f) },
        dot = if (off) null else RingDot(((target - min) / span).coerceIn(0f, 1f), colour),
        drag = if (off) null else RingDrag { f -> pending.set(snapTo(min + f * span, step, min, max), 450) { sendTarget(it) } },
        instant = pending.dragging,
    )
    val accent = toneColours.accent

    val modes: List<Triple<String, String, String>> = if (ac) {
        val have = c.strings("hvac_modes")
        listOf("off") .plus(listOf("cool", "heat", "fan_only", "dry", "auto", "heat_cool").filter { it in have }).take(4).map { m ->
            Triple(m, when (m) { "fan_only" -> "Fan"; "heat_cool" -> "Heat/Cool"; "dry" -> "Dry"; else -> presetLabel(m) }, modeIcon(m))
        }
    } else buildList {
        add(Triple("off", "Off", "power"))
        if ("heat" in c.strings("hvac_modes")) add(Triple("heat", "Heat", "fire"))
        if ("eco" in c.strings("preset_modes")) add(Triple("eco", "Eco", "leaf"))
    }

    fun pick(mode: String) {
        scope.launch {
            if (mode == "eco") {
                if (off) onMode(c.strings("hvac_modes"))?.let { WatchData.call("climate", "set_hvac_mode", c.entityId, json("hvac_mode" to it)); delay(600) }
                WatchData.call("climate", "set_preset_mode", c.entityId, json("preset_mode" to "eco"))
            } else {
                WatchData.call("climate", "set_hvac_mode", c.entityId, json("hvac_mode" to mode))
                val active = c.str("preset_mode")
                if (mode != "off" && !active.isNullOrBlank() && active != "none" && "none" in c.strings("preset_modes")) {
                    delay(600)
                    WatchData.call("climate", "set_preset_mode", c.entityId, json("preset_mode" to "none"))
                }
            }
        }
    }

    return Face("c:${c.entityId}", spec) {
        val ui = LocalUi.current
        Box(Modifier.fillMaxSize()) {
            FaceHeader(c.friendlyName, climateWord(c, eco), accent)
            Stepper(temp(target), "°", Ink, 88f, off, onMinus = { nudge(-step) }, onPlus = { nudge(step) })
            val inset = if (modes.size <= 3) 54f else 42f
            ButtonRow(inset, 166f, 54f) {
                for ((key, label, icon) in modes) {
                    val lit = when (key) {
                        "off" -> c.state == "off"
                        "eco" -> eco && !off
                        "heat" -> c.state == "heat" && !eco
                        else -> c.state == key
                    }
                    ModeButton(icon, label, lit, accent, Ink, Slot(), onClick = { pick(key) })
                }
            }
            if (ac) {
                val fan = c.str("fan_mode")
                if (fan != null && c.strings("fan_modes").isNotEmpty()) {
                    val next = LocalNextFace.current
                    Box(Modifier.at(ui, 0f, 226f, 280f, 28f), contentAlignment = Alignment.Center) {
                        PillChip("Fan · ${presetLabel(fan)} ⌄", Ink, Modifier.height(ui.d(28f)), onClick = next)
                    }
                }
            }
        }
    }
}

private fun modeIcon(mode: String): String = when (mode) {
    "off" -> "power"
    "heat" -> "fire"
    "cool" -> "snowflake"
    "heat_cool" -> "sun-snowflake-variant"
    "auto" -> "thermostat-auto"
    "dry" -> "water-percent"
    "fan_only" -> "fan"
    else -> "thermostat-auto"
}

/** An air con's second face: its fan speeds as buttons, with the ring picturing how fast. */
@Composable
private fun airConFanFace(c: EntityState): Face {
    val off = c.state == "off"
    val modes = c.strings("fan_modes")
    val current = c.str("fan_mode")
    val colours = tone(if (off) Tone.Grey else if (c.state == "cool") Tone.Blue else if (c.state == "heat") Tone.Orange else Tone.Teal)
    val accent = colours.accent
    val speeds = modes.filter { it.lowercase() != "auto" }.take(5)
    val idx = speeds.indexOf(current)
    val auto = current?.lowercase() == "auto" && !off
    val spec = RingSpec(
        bg = colours.container,
        segs = if (speeds.isEmpty()) null else RingSegs(accent, speeds.indices.map { i -> if (!off && idx >= i) 1f else if (auto) 0.55f else 0.22f }),
    )
    return Face("cf:${c.entityId}", spec) {
        Box(Modifier.fillMaxSize()) {
            FaceHeader("${c.friendlyName} fan", if (off) "Air con is off" else "Fan speed · ${current?.let { presetLabel(it) } ?: "–"}", accent)
            val rows = rowsOf(modes.take(6), 4)
            rows.forEachIndexed { r, row ->
                val y = if (rows.size == 1) 100f else 92f + r * 62f
                val h = if (rows.size == 1) 68f else 56f
                ButtonRow(if (row.size <= 3) 54f else 34f, y, h) {
                    for (m in row) {
                        val icon = when (m.lowercase()) { "auto" -> "fan-auto"; "low", "quiet" -> "speedometer-slow"; "medium", "mid" -> "speedometer-medium"; "high", "turbo" -> "speedometer"; else -> "fan" }
                        ModeButton(icon, presetLabel(m), !off && current == m, accent, Ink, Slot(), onClick = { WatchData.call("climate", "set_fan_mode", c.entityId, json("fan_mode" to m)) })
                    }
                }
            }
        }
    }
}

@Composable
private fun fanFace(f: EntityState): Face {
    val on = f.state == "on"
    val speeds = fanSpeeds(f).take(5)
    val idx = currentSpeed(f, speeds)
    val preset = f.str("preset_mode")
    val others = f.strings("preset_modes").filter { speedNumber(it) == null }
    val canOscillate = f.attributes.has("oscillating") || ((f.num("supported_features")?.toInt() ?: 0) and 2) == 2
    val oscillating = f.attributes.optBoolean("oscillating", false)
    val colours = tone(if (on) Tone.Teal else Tone.Grey)
    val accent = colours.accent
    val word = when {
        !on -> "Off"
        idx >= 0 -> "Speed ${speeds[idx].n}"
        preset != null -> presetLabel(preset)
        else -> "On"
    } + if (on && oscillating) " · Oscillating" else ""
    val spec = RingSpec(
        bg = colours.container,
        segs = if (speeds.isEmpty()) null else RingSegs(accent, speeds.indices.map { i -> if (on && idx >= i) 1f else 0.22f }),
    )
    return Face("f:${f.entityId}", spec) {
        Box(Modifier.fillMaxSize()) {
            FaceHeader(f.friendlyName.trim(), word, accent)
            ButtonRow(if (speeds.size <= 3) 40f else 30f, 90f, 58f) {
                ModeButton("power", "Off", !on, accent, Ink, Slot(), onClick = { WatchData.call("fan", "turn_off", f.entityId) })
                speeds.forEachIndexed { i, sp ->
                    ModeButton(speedIcon(i, speeds.size), "${sp.n}", on && idx == i, accent, Ink, Slot(), onClick = {
                        if (sp.preset != null) WatchData.call("fan", "set_preset_mode", f.entityId, json("preset_mode" to sp.preset))
                        else WatchData.call("fan", "set_percentage", f.entityId, json("percentage" to (sp.percentage ?: 0)))
                    })
                }
            }
            val second = others.size + if (canOscillate) 1 else 0
            if (second > 0) ButtonRow(if (second <= 3) 54f else 34f, 156f, 54f) {
                for (p in others.take(3)) {
                    ModeButton(fanPresetIcon(p), presetLabel(p), on && preset == p, accent, Ink, Slot(), onClick = { WatchData.call("fan", "set_preset_mode", f.entityId, json("preset_mode" to p)) })
                }
                if (canOscillate) ModeButton("arrow-oscillating", "Oscillate", on && oscillating, accent, Ink, Slot(), onClick = { WatchData.call("fan", "oscillate", f.entityId, json("oscillating" to !oscillating)) })
            }
        }
    }
}

@Composable
private fun purifierFace(f: EntityState, pmSensor: EntityState?): Face {
    val on = f.state == "on"
    val pm = pmSensor?.state?.toFloatOrNull()
    val toneColours = tone(if (!on) Tone.Grey else if (pm == null || pm <= 35f) Tone.Green else if (pm <= 75f) Tone.Amber else Tone.Red)
    val accent = toneColours.accent
    val presets = f.strings("preset_modes")
    val preset = f.str("preset_mode")
    val word = if (!on) "Off" else preset?.let { presetLabel(it) } ?: "On"
    val quality = when {
        pm == null -> null
        pm <= 35f -> "Good"
        pm <= 75f -> "Fair"
        pm <= 115f -> "Poor"
        else -> "Very poor"
    }
    val spec = RingSpec(bg = toneColours.container, gradient = pm?.let { (it / 150f).coerceIn(0f, 1f) })
    val items = buildList {
        add(Triple("off", "Off", "power"))
        if (presets.isEmpty()) add(Triple("on", "On", "autorenew"))
        presets.forEach { add(Triple(it, presetLabel(it), purifierModeIcon(it))) }
    }
    val rows = rowsOf(items.take(6), 4)
    return Face("p:${f.entityId}", spec) {
        val ui = LocalUi.current
        Box(Modifier.fillMaxSize()) {
            FaceHeader(f.friendlyName.trim(), word, accent)
            if (quality != null && pm != null) Column(Modifier.at(ui, 0f, 78f, 280f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(quality, color = gradientColour((pm / 150f).coerceIn(0f, 1f)), fontSize = ui.sp(24f), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 1)
                Text("PM2.5 · ${pm.toInt()} µg/m³", color = Ink.copy(alpha = 0.85f), fontSize = ui.sp(12f), textAlign = TextAlign.Center, maxLines = 1)
            }
            rows.forEachIndexed { r, row ->
                ButtonRow(if (row.size <= 3) 54f else 40f, (if (quality != null) 124f else 100f) + r * 52f, 46f) {
                    for ((key, label, icon) in row) {
                        val lit = when (key) { "off" -> !on; "on" -> on; else -> on && preset == key }
                        ModeButton(icon, label, lit, accent, Ink, Slot(), onClick = {
                            when (key) {
                                "off" -> WatchData.call("fan", "turn_off", f.entityId)
                                "on" -> WatchData.call("fan", "turn_on", f.entityId)
                                else -> WatchData.call("fan", "set_preset_mode", f.entityId, json("preset_mode" to key))
                            }
                        })
                    }
                }
            }
        }
    }
}
