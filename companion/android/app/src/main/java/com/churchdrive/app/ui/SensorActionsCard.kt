package com.churchdrive.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.churchdrive.app.ha.CallService
import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ha.data
import kotlinx.coroutines.launch
import org.json.JSONObject

// Sensor actions: what lights do when a motion or presence sensor sees someone and when the room empties (the Lights page's Automations section,
// under Light routines, for administrators). The house runs them (`church_drive/sensor_actions`). A Hue sensor keeps its own lights on the Hue
// bridge until it is taken over here, which Church Drive can't read, so taking one over asks for that automation to be switched off first.

/** One line of readings for a sensor from live states: "Empty · 353 lx · Battery 100%". */
private fun sensorReadings(sensor: SensorInfo, entities: Map<String, EntityState>): String {
    val seen = entities[sensor.entityId]?.state
    val parts = buildList {
        add(when (seen) { "on" -> "Someone's there"; "off" -> "Empty"; else -> "Not reporting" })
        sensor.luxEntity?.let { e -> entities[e]?.state?.takeIf { it.toDoubleOrNull() != null }?.let { add("${it.toDouble().toInt()} lx") } }
        sensor.temperatureEntity?.let { e -> entities[e]?.state?.toDoubleOrNull()?.let { add("%.1f°".format(it)) } }
        sensor.batteryEntity?.let { e -> entities[e]?.state?.toDoubleOrNull()?.let { add("Battery ${it.toInt()}%") } }
    }
    return parts.joinToString(" · ")
}

/** The editor for one sensor's action ([action] null makes a new one): what it does, its limits and the sensor's own settings. [onDone] runs after Save, Remove or Cancel. */
@Composable
fun SensorEditorCard(sensor: SensorInfo, action: SensorAction?, entities: Map<String, EntityState>, call: CallService, onDone: () -> Unit) {
    val api = LocalHaApi.current
    val scope = rememberCoroutineScope()
    val tone = toneColors(Tone.Lights)
    val ink = tone.onContainer
    val key = action?.id ?: sensor.entityId
    var onAction by remember(key) { mutableStateOf(action?.onAction ?: "on") }
    var scene by remember(key) { mutableStateOf(action?.scene ?: WHITE_SCENES.first()) }
    var offAction by remember(key) { mutableStateOf(action?.offAction ?: "off") }
    var offDelay by remember(key) { mutableIntStateOf(action?.offDelay ?: 60) }
    var dimPct by remember(key) { mutableIntStateOf(action?.dimPct ?: 20) }
    var onlyDark by remember(key) { mutableStateOf(action?.onlyDark ?: false) }
    var darkLux by remember(key) { mutableIntStateOf(action?.darkLux ?: 30) }
    var windowOn by remember(key) { mutableStateOf(action?.windowStart != null) }
    var windowStart by remember(key) { mutableStateOf(action?.windowStart ?: "22:00") }
    var windowEnd by remember(key) { mutableStateOf(action?.windowEnd ?: "06:00") }
    var enabled by remember(key) { mutableStateOf(action?.enabled ?: true) }
    var pct by remember(key) { mutableIntStateOf(action?.pct ?: 0) }
    var slots by remember(key) { mutableStateOf(action?.slots ?: emptyList()) }
    var openSlot by remember(key) { mutableStateOf<Int?>(null) }
    var chosen by remember(key) { mutableStateOf(action?.lights ?: emptyList()) }
    var hueOff by remember(key) { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val lights = remember(entities) { schedulableLights(entities) }
    val names = lights.toMap()
    val needsHueOff = sensor.isHue && action == null
    val luxEntity = sensor.luxEntity

    EntityCard(tone.container, ink) {
        Text(sensor.name, style = MaterialTheme.typography.titleLarge, color = ink)
        Text(sensorReadings(sensor, entities), style = MaterialTheme.typography.bodyMedium, color = ink.copy(alpha = 0.85f))
        if (sensor.isHue && action == null) {
            Text(
                "Which lights this sensor switches is set on the Hue bridge, so Church Drive can't show it or change it. Set it up here to run it from Church Drive.",
                style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.8f),
            )
        }
        if (action != null) {
            SwitchLine("Action on", enabled, ink) { enabled = it }
        }

        Text("When someone is there", style = MaterialTheme.typography.titleSmall, color = ink)
        Text("Which lights", style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.8f))
        TileRow(
            chosen.map { id -> TileItem("cd:bulb-lit", names[id] ?: entities[id]?.friendlyName ?: id, true) { chosen = chosen - id; message = null } } +
                TileItem("cd:plus", "Add a light", picking) { picking = !picking },
            tone, ink, perRow = 2,
        )
        if (picking) {
            Column(Modifier.fillMaxWidth().heightIn(max = 240.dp).verticalScroll(rememberScrollState())) {
                lights.filter { it.first !in chosen }.forEach { (id, name) ->
                    Text(
                        name, style = MaterialTheme.typography.bodyLarge, color = ink,
                        modifier = Modifier.fillMaxWidth().clickable { chosen = chosen + id; picking = false; message = null }.padding(vertical = 10.dp),
                    )
                }
            }
        }
        TileRow(
            listOf(
                TileItem("cd:power", "Turn on", onAction == "on") { onAction = "on" },
                TileItem("cd:sun", "Scene", onAction == "scene") { onAction = "scene" },
                TileItem("cd:close", "Nothing", onAction == "none") { onAction = "none" },
            ),
            tone, ink,
        )
        if (onAction == "scene") {
            TileRow(WHITE_SCENES.map { k -> TileItem("", LightScene(k, "").name, scene == k) { scene = k } }, tone, ink)
        }
        if (onAction == "on") {
            StepLine("Brightness", if (pct == 0) "As it is" else "$pct%", ink, tone, { pct = (pct - 10).coerceAtLeast(0) }, { pct = (pct + 10).coerceAtMost(100) })
        }

        Text("Different at different times", style = MaterialTheme.typography.titleSmall, color = ink)
        Text(
            "Give part of the day its own look. The first slot that covers the time decides, and any other time uses the choice above.",
            style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.8f),
        )
        fun setSlot(i: Int, next: SensorSlot) { slots = slots.mapIndexed { j, x -> if (j == i) next else x }; message = null }
        slots.forEachIndexed { i, slot ->
            if (openSlot == i) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(ink.copy(alpha = 0.10f)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    StepLine("From", slot.start, ink, tone, { setSlot(i, slot.copy(start = stepTime(slot.start, -1, 30) ?: slot.start)) }, { setSlot(i, slot.copy(start = stepTime(slot.start, 1, 30) ?: slot.start)) })
                    StepLine("Until", slot.end, ink, tone, { setSlot(i, slot.copy(end = stepTime(slot.end, -1, 30) ?: slot.end)) }, { setSlot(i, slot.copy(end = stepTime(slot.end, 1, 30) ?: slot.end)) })
                    TileRow(
                        listOf(
                            TileItem("cd:power", "Turn on", slot.onAction == "on") { setSlot(i, slot.copy(onAction = "on")) },
                            TileItem("cd:sun", "Scene", slot.onAction == "scene") { setSlot(i, slot.copy(onAction = "scene", scene = slot.scene.ifBlank { WHITE_SCENES.first() })) },
                            TileItem("cd:close", "Nothing", slot.onAction == "none") { setSlot(i, slot.copy(onAction = "none")) },
                        ),
                        tone, ink,
                    )
                    if (slot.onAction == "scene") {
                        TileRow(WHITE_SCENES.map { k -> TileItem("", LightScene(k, "").name, slot.scene == k) { setSlot(i, slot.copy(scene = k)) } }, tone, ink)
                    }
                    if (slot.onAction == "on") {
                        StepLine("Brightness", if (slot.pct == 0) "As it is" else "${slot.pct}%", ink, tone, { setSlot(i, slot.copy(pct = (slot.pct - 10).coerceAtLeast(0))) }, { setSlot(i, slot.copy(pct = (slot.pct + 10).coerceAtMost(100))) })
                    }
                    TileRow(
                        listOf(
                            TileItem("cd:bin", "Remove", false) { slots = slots.filterIndexed { j, _ -> j != i }; openSlot = null },
                            TileItem("cd:check", "Done", false) { openSlot = null },
                        ),
                        tone, ink,
                    )
                }
            } else {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(ink.copy(alpha = 0.10f))
                        .clickable(onClickLabel = "Change this time slot") { openSlot = i }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("${slot.start}–${slot.end}", style = MaterialTheme.typography.titleSmall, color = ink)
                        Text(slotLookText(slot) { LightScene(it, "").name }, style = MaterialTheme.typography.bodyMedium, color = ink.copy(alpha = 0.85f))
                    }
                    HaIcon("cd:chevron-right", CdVectors.Info, ink.copy(alpha = 0.7f), 22.dp)
                }
            }
        }
        if (slots.size < MAX_SENSOR_SLOTS) {
            TileRow(listOf(TileItem("cd:plus", "Add a time slot", false) { slots = slots + newSensorSlot(); openSlot = slots.lastIndex }), tone, ink)
        }

        Text("When the room is empty", style = MaterialTheme.typography.titleSmall, color = ink)
        TileRow(
            listOf(
                TileItem("cd:bulb-off", "Turn off", offAction == "off") { offAction = "off" },
                TileItem("cd:moon", "Dim", offAction == "dim") { offAction = "dim" },
                TileItem("cd:close", "Nothing", offAction == "none") { offAction = "none" },
            ),
            tone, ink,
        )
        if (offAction != "none") {
            StepLine("After", delayText(offDelay), ink, tone, { offDelay = stepDelay(offDelay, -1) }, { offDelay = stepDelay(offDelay, 1) })
        }
        if (offAction == "dim") {
            StepLine("Dim to", "$dimPct%", ink, tone, { dimPct = (dimPct - 5).coerceAtLeast(5) }, { dimPct = (dimPct + 5).coerceAtMost(100) })
        }

        Text("Only", style = MaterialTheme.typography.titleSmall, color = ink)
        SwitchLine("When it's dark", onlyDark, ink) { onlyDark = it }
        if (onlyDark) {
            if (luxEntity != null) {
                StepLine("Dark is below", "$darkLux lx", ink, tone, { darkLux = (darkLux - 5).coerceAtLeast(5) }, { darkLux = (darkLux + 5).coerceAtMost(2000) })
            } else {
                Text("This sensor has no light reading, so dark means the sun is down.", style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.8f))
            }
        }
        SwitchLine("Between set times", windowOn, ink) { windowOn = it }
        if (windowOn) {
            StepLine("From", windowStart, ink, tone, { windowStart = stepTime(windowStart, -1, 30) ?: windowStart }, { windowStart = stepTime(windowStart, 1, 30) ?: windowStart })
            StepLine("Until", windowEnd, ink, tone, { windowEnd = stepTime(windowEnd, -1, 30) ?: windowEnd }, { windowEnd = stepTime(windowEnd, 1, 30) ?: windowEnd })
        }

        if (sensor.settings.isNotEmpty()) {
            Text("Sensor settings", style = MaterialTheme.typography.titleSmall, color = ink)
            sensor.settings.forEach { s -> SensorSettingRow(s, entities, call, tone, ink) }
        }

        if (needsHueOff) {
            SwitchLine("I've switched off this sensor's automation in the Hue app", hueOff, ink) { hueOff = it; message = null }
        }
        message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
        TileRow(
            listOfNotNull(
                action?.let { a -> TileItem("cd:bin", "Remove", false) { scope.launch { api?.ask("church_drive/sensor_actions/delete", JSONObject().put("action_id", a.id)); onDone() } } },
                TileItem("cd:close", "Cancel", false) { onDone() },
                TileItem("cd:check", if (saving) "Saving…" else "Save", false) {
                    val problem = sensorProblem(chosen, scene, onAction, needsHueOff, hueOff)
                    if (problem != null) message = problem
                    else if (!saving) {
                        saving = true
                        scope.launch {
                            val body = sensorBody(
                                action?.id, sensor.entityId, action?.name?.takeIf { it.isNotBlank() } ?: sensor.name.removeSuffix(" Sensor").trim(), chosen, onAction, scene, offAction,
                                offDelay, dimPct, onlyDark, darkLux, luxEntity ?: "", if (windowOn) windowStart else null,
                                if (windowOn) windowEnd else null, enabled, pct, slots,
                            )
                            val result = api?.ask("church_drive/sensor_actions/set", body)
                            saving = false
                            if (result != null) onDone() else message = "The house didn't save it."
                        }
                    }
                },
            ),
            tone, ink,
        )
    }
}

@Composable
private fun SwitchLine(label: String, checked: Boolean, ink: androidx.compose.ui.graphics.Color, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = ink, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun StepLine(label: String, value: String, ink: androidx.compose.ui.graphics.Color, tone: ToneColors, less: () -> Unit, more: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = ink, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleMedium, color = ink)
    }
    TileRow(listOf(TileItem("cd:minus", "Less", false) { less() }, TileItem("cd:plus", "More", false) { more() }), tone, ink)
}

/** One setting of the sensor's own (hold time, sensitivity): a number moves in its own steps and a choice is a row of tiles, both sent straight to the sensor. */
@Composable
private fun SensorSettingRow(setting: SensorSetting, entities: Map<String, EntityState>, call: CallService, tone: ToneColors, ink: androidx.compose.ui.graphics.Color) {
    val state = entities[setting.entityId]?.state
    if (setting.kind == "switch") {
        val on = state == "on"
        SwitchLine(setting.name, on, ink) { call("switch", if (it) "turn_on" else "turn_off", setting.entityId, data()) }
    } else if (setting.kind == "select") {
        Text(setting.name, style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.8f))
        TileRow(
            setting.options.map { o -> TileItem("", o.replaceFirstChar { it.uppercase() }, state == o) { call("select", "select_option", setting.entityId, data("option" to o)) } },
            tone, ink,
        )
    } else {
        val value = state?.toDoubleOrNull()
        val step = setting.step?.takeIf { it > 0 } ?: 1.0
        val shown = value?.let { (if (it % 1.0 == 0.0) it.toInt().toString() else "%.1f".format(it)) + (if (setting.unit.isNotBlank()) " ${setting.unit}" else "") } ?: "—"
        StepLine(
            setting.name, shown, ink, tone,
            { value?.let { call("number", "set_value", setting.entityId, data("value" to (it - step).coerceAtLeast(setting.min ?: Double.NEGATIVE_INFINITY))) } },
            { value?.let { call("number", "set_value", setting.entityId, data("value" to (it + step).coerceAtMost(setting.max ?: Double.POSITIVE_INFINITY))) } },
        )
    }
}

/**
 * The Lights page's Sensor actions card (administrators), under Light routines: a summary (how many sensors are set up and the first one's action), and just
 * that when folded. Press and hold it to open the page with a card for each motion or presence sensor. Nothing is drawn until the house answers.
 */
@Composable
fun SensorActionsCard(entities: Map<String, EntityState>, call: CallService) {
    if (!LocalIsAdmin.current) return
    val api = LocalHaApi.current
    var data by remember { mutableStateOf<SensorActionsData?>(null) }
    var changed by remember { mutableIntStateOf(0) }
    var page by remember { mutableStateOf(false) }
    val fold = rememberFold("grid-fold|sensor-actions-summary", folded = false)
    LaunchedEffect(api, changed) { parseSensorActions(api?.ask("church_drive/sensor_actions", JSONObject()))?.let { data = it } }
    DisposableEffect(api) {
        val sub = api?.subscribe("subscribe_events", JSONObject().put("event_type", "church_drive_sensor_actions_changed")) { changed++ } ?: -1
        onDispose { if (sub >= 0) api?.close(sub) }
    }
    val all = data ?: return
    if (all.sensors.isEmpty()) return
    val tone = toneColors(Tone.Lights)
    val ink = tone.onContainer
    val active = all.actions.count { it.enabled }
    Box(Modifier.pointerInput(Unit) { detectTapGestures(onLongPress = { page = true }) }) {
        EntityCard(tone.container, ink) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Sensor actions", style = MaterialTheme.typography.headlineSmall, color = ink, modifier = Modifier.weight(1f))
                Box(Modifier.size(Ui.TileHeight).clip(RoundedCornerShape(50)).clickable(onClickLabel = if (fold.value) "Expand" else "Collapse") { fold.value = !fold.value }, contentAlignment = Alignment.Center) {
                    Icon(if (fold.value) CdVectors.ChevronDown else CdVectors.ChevronUp, contentDescription = null, tint = ink.copy(alpha = 0.6f))
                }
            }
            StatusPill(if (active == 1) "1 sensor set up" else "$active of ${all.sensors.size} sensors set up", tone)
            Text(
                all.actions.firstOrNull { it.enabled }?.let { "${it.name}: ${it.summary}" } ?: "No sensor switches lights yet.",
                style = MaterialTheme.typography.bodyLarge, color = ink,
            )
            if (!fold.value) Text("Press and hold to open", style = MaterialTheme.typography.labelSmall, color = ink.copy(alpha = 0.7f))
        }
    }
    if (page) SensorActionsPage(all, entities, call, onClose = { page = false }) { changed++ }
}

/** The Sensor actions page: a card for each motion or presence sensor (what it does and who runs it, a switch for one that is set up); tap one to set it up or change it. */
@Composable
private fun SensorActionsPage(all: SensorActionsData, entities: Map<String, EntityState>, call: CallService, onClose: () -> Unit, onChanged: () -> Unit) {
    val api = LocalHaApi.current
    val scope = rememberCoroutineScope()
    val tone = toneColors(Tone.Lights)
    val ink = tone.onContainer
    var editing by remember { mutableStateOf<SensorInfo?>(null) }
    FullPage("Sensor actions", onClose) {
        val e = editing
        if (e != null) {
            SensorEditorCard(e, all.actionFor(e), entities, call) { editing = null; onChanged() }
        } else {
            all.sensors.forEach { s ->
                val a = all.actionFor(s)
                Box(Modifier.fillMaxWidth().clickable(onClickLabel = "Set up this sensor") { editing = s }) {
                    EntityCard(tone.container, ink) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(s.name, style = MaterialTheme.typography.titleLarge, color = ink.copy(alpha = if (a?.enabled == false) 0.55f else 1f), modifier = Modifier.weight(1f))
                            if (a != null) Switch(checked = a.enabled, onCheckedChange = { on -> scope.launch { api?.ask("church_drive/sensor_actions/set", JSONObject().put("action_id", a.id).put("enabled", on)); onChanged() } })
                        }
                        StatusPill(sensorBadge(s, a), tone)
                        Text(sensorSubtitle(s, a), style = MaterialTheme.typography.bodyLarge, color = ink.copy(alpha = 0.9f))
                        Text(sensorReadings(s, entities), style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.7f))
                    }
                }
            }
        }
    }
}
