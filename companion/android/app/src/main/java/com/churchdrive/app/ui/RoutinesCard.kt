package com.churchdrive.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.Switch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.churchdrive.app.ha.EntityState
import kotlinx.coroutines.launch
import org.json.JSONObject

// Light routines: lights that switch themselves on or off at a set time, on set days. Clive makes them when asked ("turn the landing light off at 11pm
// every night"); an administrator can also make, change and remove them here. They are the house's scheduled actions (`church_drive/routines`), and
// the Upcoming page opens one of them in this same editor. Thermostats have their own Overnight Heating card, and blinds, fans and the purifier
// their device pages, so lights are the only kind of repeating routine that needed a home.

/** The editor for one routine that repeats ([routine] null makes a new one): days, what it does, the time and which lights. [onDone] runs after Save, Remove or Cancel. */
@Composable
fun RoutineEditorCard(routine: RoutineInfo?, entities: Map<String, EntityState>, onDone: () -> Unit) {
    val api = LocalHaApi.current
    val scope = rememberCoroutineScope()
    val tone = toneColors(Tone.Lights)
    val ink = tone.onContainer
    var days by remember(routine?.id) { mutableStateOf(routine?.days ?: EVERY_DAY) }
    var time by remember(routine?.id) { mutableStateOf(routine?.time?.takeIf { it.isNotBlank() } ?: "23:00") }
    var action by remember(routine?.id) { mutableStateOf(routine?.action?.takeIf { it == "on" || it == "off" } ?: "off") }
    var chosen by remember(routine?.id) { mutableStateOf(routine?.entities ?: emptyList()) }
    var picking by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val lights = remember(entities) { schedulableLights(entities) }
    val names = lights.toMap()
    EntityCard(tone.container, ink) {
        Text(if (routine == null) "New routine" else "Routine", style = MaterialTheme.typography.titleLarge, color = ink)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ROUTINE_DAY_LETTERS.forEachIndexed { day, letter ->
                val on = day in days
                Box(
                    Modifier.weight(1f).height(Ui.TileHeight).clip(RoundedCornerShape(50))
                        .background(if (on) tone.accent else ink.copy(alpha = 0.10f))
                        .clickable(onClickLabel = ROUTINE_DAY_NAMES[day]) { days = if (on) days - day else (days + day).sorted(); message = null },
                    contentAlignment = Alignment.Center,
                ) { CentredText(letter, if (on) tone.onAccent else ink, Ui.TileText) }
            }
        }
        TileRow(
            listOf(
                TileItem("", "Weekdays", days.sorted() == WEEKDAYS) { days = WEEKDAYS },
                TileItem("", "Weekend", days.sorted() == WEEKEND) { days = WEEKEND },
                TileItem("", "Every day", days.sorted() == EVERY_DAY) { days = EVERY_DAY },
            ),
            tone, ink,
        )
        Text("Do", style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.8f))
        TileRow(
            listOf(
                TileItem("cd:bulb-off", "Off", action == "off") { action = "off" },
                TileItem("cd:bulb-lit", "On", action == "on") { action = "on" },
            ),
            tone, ink,
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("At", style = MaterialTheme.typography.bodyLarge, color = ink, modifier = Modifier.weight(1f))
            Text(time, style = MaterialTheme.typography.titleMedium, color = ink)
        }
        TileRow(
            listOf(
                TileItem("cd:minus", "Earlier", false) { time = stepTime(time, -1, 5) ?: time },
                TileItem("cd:plus", "Later", false) { time = stepTime(time, 1, 5) ?: time },
            ),
            tone, ink,
        )
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
        message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
        TileRow(
            listOfNotNull(
                routine?.let { r -> TileItem("cd:bin", "Remove", false) { scope.launch { api?.ask("church_drive/routines/delete", JSONObject().put("routine_id", r.id)); onDone() } } },
                TileItem("cd:close", "Cancel", false) { onDone() },
                TileItem("cd:check", if (saving) "Saving…" else "Save", false) {
                    val problem = routineProblem(chosen, days)
                    if (problem != null) message = problem
                    else if (!saving) {
                        saving = true
                        scope.launch {
                            val result = api?.ask("church_drive/routines/set", repeatBody(routine?.id, chosen, action, time, days))
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

/**
 * The Lights page's Light routines card (administrators): a summary (how many there are and the next one), and just the next one when folded. Press and
 * hold it to open the page with a card for each routine. Nothing is drawn until the house answers.
 */
@Composable
fun LightRoutinesCard(entities: Map<String, EntityState>) {
    if (!LocalIsAdmin.current) return
    val api = LocalHaApi.current
    var routines by remember { mutableStateOf<List<RoutineInfo>?>(null) }
    var changed by remember { mutableIntStateOf(0) }
    var page by remember { mutableStateOf(false) }
    LaunchedEffect(api, changed) { parseRoutines(api?.ask("church_drive/routines", JSONObject()))?.let { routines = it } }
    DisposableEffect(api) {
        val sub = api?.subscribe("subscribe_events", JSONObject().put("event_type", "church_drive_routines_changed")) { changed++ } ?: -1
        onDispose { if (sub >= 0) api?.close(sub) }
    }
    val all = routines ?: return
    val list = all.filter(::isLightRoutine)
    val tone = toneColors(Tone.Lights)
    val next = list.filter { it.enabled && it.next != null }.sortedBy { it.next.orEmpty() }.take(AUTOMATION_LINES)
    AutomationCard(
        "Light routines", if (list.size == 1) "1 routine" else "${list.size} routines",
        next.map { it.name to it.next.orEmpty().drop(11).take(5) },
        if (list.isEmpty()) "No routines yet." else "All switched off.", tone,
    ) { page = true }
    if (page) LightRoutinesPage(list, entities, onClose = { page = false }) { changed++ }
}

/** The Light routines page: a card for each routine (days and time, who made it, a switch; tap to change) and a way to add one. */
@Composable
private fun LightRoutinesPage(list: List<RoutineInfo>, entities: Map<String, EntityState>, onClose: () -> Unit, onChanged: () -> Unit) {
    val api = LocalHaApi.current
    val scope = rememberCoroutineScope()
    val tone = toneColors(Tone.Lights)
    val ink = tone.onContainer
    var editing by remember { mutableStateOf<RoutineInfo?>(null) }
    var adding by remember { mutableStateOf(false) }
    FullPage("Light routines", onClose) {
        if (editing != null || adding) {
            RoutineEditorCard(editing, entities) { editing = null; adding = false; onChanged() }
        } else {
            if (list.isEmpty()) Text("No routines yet. Ask ${AssistantBrand.shown} to make one, or add one here.", style = MaterialTheme.typography.bodyMedium)
            list.forEach { r ->
                Box(Modifier.fillMaxWidth().clickable(onClickLabel = "Change this routine") { editing = r }) {
                    EntityCard(tone.container, ink) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(r.name, style = MaterialTheme.typography.titleLarge, color = ink.copy(alpha = if (r.enabled) 1f else 0.55f))
                                Text("${routineDaysText(r.days)} · ${r.time}", style = MaterialTheme.typography.bodyLarge, color = ink.copy(alpha = if (r.enabled) 0.9f else 0.55f))
                                Text(
                                    if (r.src == "user") "Added by ${r.by.ifBlank { "someone" }}" else "${AssistantBrand.shown}${if (r.by.isNotBlank()) " · ${r.by} asked" else ""}",
                                    style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.7f),
                                )
                            }
                            Switch(checked = r.enabled, onCheckedChange = { on -> scope.launch { api?.ask("church_drive/routines/set", JSONObject().put("routine_id", r.id).put("enabled", on)); onChanged() } })
                        }
                    }
                }
            }
            TileRow(listOf(TileItem("cd:plus", "Add routine", false) { adding = true }), tone, ink)
        }
    }
}
