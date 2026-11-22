package com.churchdrive.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
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

/** A card of the sensor editor that folds: a round icon, its title and a short summary, then [content] while it is open. */
@Composable
private fun AreaCard(icon: String, title: String, summary: String, key: String, startOpen: Boolean, tone: ToneColors, content: @Composable () -> Unit) {
    var open by rememberSaveable(key) { mutableStateOf(startOpen) }
    EntityCard(tone.container, tone.onContainer) {
        Row(
            Modifier.fillMaxWidth().clickable(onClickLabel = if (open) "Fold $title" else "Open $title"),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(40.dp).background(tone.accent, CircleShape), contentAlignment = Alignment.Center) { HaIcon(icon, CdVectors.Info, tone.onAccent, 22.dp) }
            Text(title, style = MaterialTheme.typography.titleMedium, color = tone.onContainer, modifier = Modifier.weight(1f))
            StatusPill(summary, tone)
            HaIcon("cd:chevron", CdVectors.ChevronDown, tone.onContainer.copy(alpha = 0.6f), 22.dp, Modifier.rotate(if (open) 180f else 0f))
        }
        if (open) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
    }
}

/** The scenes to choose from as the room cards draw them (their colours and icons), three to a row, the chosen one ringed. */
@Composable
private fun SceneChoices(keys: List<String>, selected: String, onPick: (String) -> Unit) {
    val library = LocalSceneLooks.current.library
    Column(verticalArrangement = Arrangement.spacedBy(Ui.TileGap)) {
        keys.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Ui.TileGap)) {
                row.forEach { k -> SceneTile(LightScene(k, "", library[k]?.name), active = k == selected, dimmed = false, modifier = Modifier.weight(1f)) { onPick(k) } }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** The themes to choose from as the room cards draw them, three to a row, the chosen one ringed. */
@Composable
private fun ThemeChoices(themes: List<Hybrid>, selected: String, onPick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Ui.TileGap)) {
        themes.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Ui.TileGap)) {
                row.forEach { t -> HybridTile(t, active = t.id == selected, dimmed = false, modifier = Modifier.weight(1f)) { onPick(t.id) } }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** What a time slot looks like in its list: a bar in the scene's first colour, the times, and what it plays. */
@Composable
private fun SlotRow(slot: SensorSlot, look: String, bar: Color, ink: Color, open: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(ink.copy(alpha = 0.10f)).clickable(onClickLabel = "Change this time slot", onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(width = 6.dp, height = 36.dp).background(bar, RoundedCornerShape(3.dp)))
        Column(Modifier.weight(1f)) {
            Text("${slot.start}–${slot.end}", style = MaterialTheme.typography.titleSmall, color = ink)
            Text(look, style = MaterialTheme.typography.bodyMedium, color = ink.copy(alpha = 0.75f))
        }
        HaIcon("cd:chevron", CdVectors.ChevronDown, ink.copy(alpha = 0.6f), 20.dp, Modifier.rotate(if (open) 180f else -90f))
    }
}

/** The editor for one sensor's action ([action] null makes a new one), as a card for each area: the sensors, what happens when someone is there, when the room empties, its limits and the sensor's own settings. [onDone] runs after Save, Remove or Cancel. */
@Composable
fun SensorEditorCard(sensor: SensorInfo, action: SensorAction?, entities: Map<String, EntityState>, call: CallService, others: List<SensorInfo> = emptyList(), onDone: () -> Unit) {
    val api = LocalHaApi.current
    val scope = rememberCoroutineScope()
    val tone = toneColors(Tone.Grey)
    val ink = tone.onContainer
    val key = action?.id ?: sensor.entityId
    // Sensors that act as one: someone seen by any of them is someone there, and the room empties when all have gone.
    var members by remember(key) { mutableStateOf(action?.sensors?.takeIf { it.isNotEmpty() } ?: listOf(sensor.entityId)) }
    var addingSensor by remember { mutableStateOf(false) }
    val everyone = remember(sensor, others) { listOf(sensor) + others.filter { it.entityId != sensor.entityId } }
    val memberInfos = everyone.filter { it.entityId in members }
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
    var offIfOn by remember(key) { mutableStateOf(action?.offIfOn ?: false) }
    val themes = LocalHybrids.current.hybrids
    val themeName = { id: String -> themes.firstOrNull { it.id == id }?.name ?: "A theme" }
    val looks = LocalSceneLooks.current
    // The scenes a sensor can play: the white ones, then the ones you made.
    val sceneKeys = remember(looks.library) { WHITE_SCENES + looks.library.values.filter { it.custom && it.key !in WHITE_SCENES }.sortedBy { it.name }.map { it.key } }
    val sceneName = { k: String -> looks.library[k]?.name ?: LightScene(k, "").name }
    var openSlot by remember(key) { mutableStateOf<Int?>(null) }
    var chosen by remember(key) { mutableStateOf(action?.lights ?: emptyList()) }
    var hueOff by remember(key) { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val lights = remember(entities) { schedulableLights(entities) }
    val names = lights.toMap()
    val needsHueOff = memberInfos.any { it.isHue } && action == null
    val luxSensors = memberInfos.mapNotNull { it.luxEntity }
    val luxEntity = luxSensors.firstOrNull()
    val motionOnly = memberInfos.isNotEmpty() && memberInfos.all { it.isMotion }
    val onLabel = when (onAction) { "scene" -> "Scene"; "hybrid" -> "Theme"; "none" -> "Nothing"; else -> "Turn on" }
    val offLabel = when (offAction) {
        "dim" -> "Dims after ${delayText(offDelay)}"; "restore" -> "Goes back after ${delayText(offDelay)}"; "none" -> "Nothing"; else -> "Off after ${delayText(offDelay)}"
    }
    val onlyLabel = listOfNotNull("When it's dark".takeIf { onlyDark }, "Set times".takeIf { windowOn }).joinToString(" · ").ifBlank { "Always" }
    fun barOf(look: String, id: String, hybrid: Boolean): Color =
        if (hybrid) themes.firstOrNull { it.id == id }?.let { t -> Color(hybridTileColours(t, looks).first()) } ?: ink
        else sceneColours(LightScene(id, "", looks.library[id]?.name), looks).firstOrNull() ?: ink

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AreaCard("cd:motion-sensor", memberInfos.joinToString(" + ") { it.name }.ifBlank { sensor.name }, if (members.size == 1) "1 sensor" else "${members.size} sensors", "$key|sensors", true, tone) {
            memberInfos.forEach { m -> Text(if (memberInfos.size > 1) "${m.name}: ${sensorReadings(m, entities)}" else sensorReadings(m, entities), style = MaterialTheme.typography.bodyMedium, color = ink.copy(alpha = 0.85f)) }
            if (others.any { it.entityId != sensor.entityId } || members.size > 1) {
                TileRow(
                    memberInfos.map { m -> TileItem("mdi:motion-sensor", m.name, true) { if (m.entityId != sensor.entityId) members = members - m.entityId } } +
                        (if (others.any { it.entityId !in members && it.entityId != sensor.entityId }) listOf(TileItem("cd:plus", "Add a sensor", addingSensor) { addingSensor = !addingSensor }) else emptyList()),
                    tone, ink, perRow = 2,
                )
                if (addingSensor) {
                    others.filter { it.entityId !in members && it.entityId != sensor.entityId }.forEach { o ->
                        Text(
                            o.name, style = MaterialTheme.typography.bodyLarge, color = ink,
                            modifier = Modifier.fillMaxWidth().clickable { members = members + o.entityId; addingSensor = false }.padding(vertical = 10.dp),
                        )
                    }
                }
                Text("Someone seen by any of them counts. The lights go off after all have seen nobody. The light reading is the brightest of them.", style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.8f))
            }
            if (sensor.isHue && action == null) {
                Text(
                    "Which lights this sensor switches is set on the Hue bridge, so Church Drive can't show it or change it. Set it up here to run it from Church Drive.",
                    style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.8f),
                )
            }
            if (action != null) {
                SwitchLine("Action on", enabled, ink) { enabled = it }
            }
        }

        AreaCard("cd:bulb-lit", if (motionOnly) "When motion is detected" else "When someone is there", if (slots.isNotEmpty()) "${slots.size} time slots" else onLabel, "$key|there", true, tone) {
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
            Text(if (slots.isEmpty()) "Then" else "Any other time", style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.8f))
            TileRow(
                listOf(
                    TileItem("cd:power", "Turn on", onAction == "on") { onAction = "on" },
                    TileItem("cd:sun", "Scene", onAction == "scene") { onAction = "scene"; if (scene !in sceneKeys) scene = WHITE_SCENES.first() },
                    TileItem("cd:animation-play", "Theme", onAction == "hybrid") { onAction = "hybrid"; if (themes.none { it.id == scene }) scene = themes.firstOrNull()?.id ?: "" },
                    TileItem("cd:close", "Nothing", onAction == "none") { onAction = "none" },
                ),
                tone, ink, perRow = 4, column = true,
            )
            if (onAction == "scene") SceneChoices(sceneKeys, scene) { scene = it }
            if (onAction == "hybrid") {
                if (themes.isEmpty()) Text("No themes yet. Make one in a room's settings.", style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.8f))
                else ThemeChoices(themes, scene) { scene = it }
            }
            if (onAction == "on") {
                StepLine("Brightness", if (pct == 0) "As it is" else "$pct%", ink, tone, { pct = (pct - 10).coerceAtLeast(0) }, { pct = (pct + 10).coerceAtMost(100) })
            }

            Text("Different at different times", style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.8f))
            fun setSlot(i: Int, next: SensorSlot) { slots = slots.mapIndexed { j, x -> if (j == i) next else x }; message = null }
            slots.forEachIndexed { i, slot ->
                val lookText = slotLookText(slot) { if (slot.onAction == "hybrid") themeName(it) else sceneName(it) }
                SlotRow(slot, lookText, barOf(lookText, slot.scene, slot.onAction == "hybrid"), ink, openSlot == i) { openSlot = if (openSlot == i) null else i }
                if (openSlot == i) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // The slots always cover the whole day: changing when one ends changes when the next begins.
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.weight(1f)) { ScheduleTime("From", slot.start, false, ink) { t -> if (t != null) moveSlotBoundary(slots, i, t)?.let { slots = it; message = null } ?: run { message = "That time would go past another time slot." } } }
                            Box(Modifier.weight(1f)) { ScheduleTime("Until", slot.end, false, ink) { t -> if (t != null) moveSlotBoundary(slots, (i + 1) % slots.size, t)?.let { slots = it; message = null } ?: run { message = "That time would go past another time slot." } } }
                        }
                        TileRow(
                            listOf(
                                TileItem("cd:power", "Turn on", slot.onAction == "on") { setSlot(i, slot.copy(onAction = "on")) },
                                TileItem("cd:sun", "Scene", slot.onAction == "scene") { setSlot(i, slot.copy(onAction = "scene", scene = slot.scene.takeIf { it in sceneKeys } ?: WHITE_SCENES.first())) },
                                TileItem("cd:animation-play", "Theme", slot.onAction == "hybrid") { setSlot(i, slot.copy(onAction = "hybrid", scene = slot.scene.takeIf { s -> themes.any { it.id == s } } ?: themes.firstOrNull()?.id.orEmpty())) },
                                TileItem("cd:close", "Nothing", slot.onAction == "none") { setSlot(i, slot.copy(onAction = "none")) },
                            ),
                            tone, ink, perRow = 4, column = true,
                        )
                        if (slot.onAction == "hybrid") {
                            if (themes.isEmpty()) Text("No themes yet. Make one in a room's settings.", style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.8f))
                            else ThemeChoices(themes, slot.scene) { setSlot(i, slot.copy(scene = it)) }
                        }
                        if (slot.onAction == "scene") SceneChoices(sceneKeys, slot.scene) { setSlot(i, slot.copy(scene = it)) }
                        if (slot.onAction == "on") {
                            StepLine("Brightness", if (slot.pct == 0) "As it is" else "${slot.pct}%", ink, tone, { setSlot(i, slot.copy(pct = (slot.pct - 10).coerceAtLeast(0))) }, { setSlot(i, slot.copy(pct = (slot.pct + 10).coerceAtMost(100))) })
                        }
                        TileRow(
                            listOf(
                                TileItem("cd:bin", "Remove", false) { slots = removeSlot(slots, i); openSlot = null },
                                TileItem("cd:check", "Done", false) { openSlot = null },
                            ),
                            tone, ink,
                        )
                    }
                }
            }
            if (slots.size < MAX_SENSOR_SLOTS) {
                TileRow(listOf(TileItem("cd:plus", "Add a time slot", false) { slots = addSlot(slots); openSlot = null }), tone, ink)
            }
        }

        AreaCard("cd:bulb-off", if (motionOnly) "When there has been no motion" else "When the room is empty", offLabel, "$key|empty", false, tone) {
            TileRow(
                listOf(
                    TileItem("cd:bulb-off", "Turn off", offAction == "off") { offAction = "off" },
                    TileItem("cd:moon", "Dim", offAction == "dim") { offAction = "dim" },
                    TileItem("cd:restore", "Go back", offAction == "restore") { offAction = "restore" },
                    TileItem("cd:close", "Nothing", offAction == "none") { offAction = "none" },
                ),
                tone, ink, perRow = 4, column = true,
            )
            if (offAction != "none") DurationTile("After", offDelay, ink) { offDelay = it }
            if (offAction == "restore") {
                Text("The lights go back to how they were when someone came in, such as a night scene.", style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.8f))
            }
            if (offAction == "off" || offAction == "dim") {
                SwitchLine("Switch off even if it was already on", offIfOn, ink) { offIfOn = it }
            }
            if (offAction == "dim") {
                StepLine("Dim to", "$dimPct%", ink, tone, { dimPct = (dimPct - 5).coerceAtLeast(5) }, { dimPct = (dimPct + 5).coerceAtMost(100) })
            }
        }

        AreaCard("cd:clock", "Only when", onlyLabel, "$key|only", false, tone) {
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
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) { ScheduleTime("From", windowStart, false, ink) { t -> if (t != null) windowStart = t } }
                    Box(Modifier.weight(1f)) { ScheduleTime("Until", windowEnd, false, ink) { t -> if (t != null) windowEnd = t } }
                }
            }
        }

        if (memberInfos.any { it.settings.isNotEmpty() }) {
            val count = memberInfos.sumOf { it.settings.size }
            AreaCard("cd:motion-sensor", "Sensor settings", if (count == 1) "1 setting" else "$count settings", "$key|settings", false, tone) {
                memberInfos.forEach { m -> m.settings.forEach { s -> SensorSettingRow(s, entities, call, tone, ink) } }
            }
        }

        if (needsHueOff) {
            SwitchLine("I've switched off this sensor's automation in the Hue app", hueOff, ink) { hueOff = it; message = null }
        }
        message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
        TileRow(
            listOfNotNull(
                action?.let { a -> TileItem("cd:bin", "Remove", false) { scope.launch { api?.ask("church_drive/sensor_actions/delete", JSONObject().put("action_id", a.id)); onDone() } } },
                TileItem("cd:close", "Cancel", false) { onDone() },
                TileItem("cd:check", if (saving) "Saving…" else "Save", true) {
                    val problem = sensorProblem(chosen, scene, onAction, needsHueOff, hueOff)
                    if (problem != null) message = problem
                    else if (!saving) {
                        saving = true
                        scope.launch {
                            val body = sensorBody(
                                action?.id, sensor.entityId, action?.name?.takeIf { it.isNotBlank() } ?: memberInfos.joinToString(" + ") { it.name.removeSuffix(" Sensor").trim() }.ifBlank { sensor.name.removeSuffix(" Sensor").trim() }, chosen, onAction, scene, offAction,
                                offDelay, dimPct, onlyDark, darkLux, luxEntity ?: "", if (windowOn) windowStart else null,
                                if (windowOn) windowEnd else null, enabled, pct, slots, offIfOn, members, luxSensors,
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
        val tone = toneColors(Tone.Grey)
        Text(label, style = MaterialTheme.typography.bodyLarge, color = ink, modifier = Modifier.weight(1f))
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = tone.onAccent, checkedTrackColor = tone.accent,
                uncheckedThumbColor = ink.copy(alpha = 0.7f), uncheckedTrackColor = ink.copy(alpha = 0.10f), uncheckedBorderColor = ink.copy(alpha = 0.5f),
            ),
        )
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
    LaunchedEffect(api, changed) { parseSensorActions(api?.ask("church_drive/sensor_actions", JSONObject()))?.let { data = it } }
    DisposableEffect(api) {
        val sub = api?.subscribe("subscribe_events", JSONObject().put("event_type", "church_drive_sensor_actions_changed")) { changed++ } ?: -1
        onDispose { if (sub >= 0) api?.close(sub) }
    }
    val all = data ?: return
    if (all.sensors.isEmpty()) return
    val tone = toneColors(Tone.Lights)
    val on = all.actions.filter { it.enabled }
    AutomationCard(
        "Sensor actions", if (on.size == 1) "1 sensor set up" else "${on.size} of ${all.sensors.size} sensors set up",
        on.map { it.name to it.summary.substringAfter(" · ", it.summary) }, "No sensor switches lights yet.", tone,
    ) { page = true }
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
    FullPage(editing?.name ?: "Sensor actions", onClose, handleBack = { if (editing != null) { editing = null; true } else false }) {
        val e = editing
        if (e != null) {
            SensorEditorCard(e, all.actionFor(e), entities, call, others = all.sensors.filter { it.entityId == e.entityId || all.actionFor(it) == null || all.actionFor(it) == all.actionFor(e) }) { editing = null; onChanged() }
        } else {
            all.cards().forEach { s ->
                val a = all.actionFor(s)
                val group = a?.let { all.groupOf(it) }.orEmpty()
                Box(Modifier.fillMaxWidth().clickable(onClickLabel = "Set up this sensor") { editing = s }) {
                    EntityCard(tone.container, ink) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (group.size > 1) group.joinToString(" + ") { it.name } else s.name, style = MaterialTheme.typography.titleLarge, color = ink.copy(alpha = if (a?.enabled == false) 0.55f else 1f), modifier = Modifier.weight(1f))
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
