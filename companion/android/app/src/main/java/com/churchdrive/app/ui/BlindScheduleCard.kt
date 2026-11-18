package com.churchdrive.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.churchdrive.app.ha.EntityState
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * A blind's or a fan's schedules, kept by the house (`church_drive/blind_schedule`, which takes covers, fans and humidifiers) and
 * refreshed when they change. Null until the house answers, and for good if the house is older and has none for this kind of device
 * (the device page then has no Schedule).
 */
@Composable
fun rememberBlindSchedule(entity: String): MutableState<BlindScheduleState?> {
    val api = LocalHaApi.current
    val state = remember(entity) { mutableStateOf<BlindScheduleState?>(null) }
    var changed by remember { mutableIntStateOf(0) }
    LaunchedEffect(api, entity, changed) {
        if (entity.isNotBlank()) parseBlindSchedule(api?.ask("church_drive/blind_schedule", JSONObject().put("entity", entity)))?.let { state.value = it }
    }
    DisposableEffect(api) {
        val sub = api?.subscribe("subscribe_events", JSONObject().put("event_type", "church_drive_blind_schedule_changed")) { changed++ } ?: -1
        onDispose { if (sub >= 0) api?.close(sub) }
    }
    return state
}

/** What the air purifier's display does in each mode (`church_drive/purifier_display`), refreshed when it changes; null until the house answers (and for good from an older house). */
@Composable
fun rememberPurifierDisplayModes(): MutableState<PurifierDisplayModes?> {
    val api = LocalHaApi.current
    val state = remember { mutableStateOf<PurifierDisplayModes?>(null) }
    var changed by remember { mutableIntStateOf(0) }
    LaunchedEffect(api, changed) {
        parsePurifierDisplayModes(api?.ask("church_drive/purifier_display", JSONObject()))?.let { state.value = it }
    }
    DisposableEffect(api) {
        val sub = api?.subscribe("subscribe_events", JSONObject().put("event_type", "church_drive_purifier_display_changed")) { changed++ } ?: -1
        onDispose { if (sub >= 0) api?.close(sub) }
    }
    return state
}

/** The air purifier's schedules (`church_drive/purifier_schedule`), refreshed when they change; null until the house answers. */
@Composable
fun rememberPurifierSchedule(): MutableState<PurifierScheduleState?> {
    val api = LocalHaApi.current
    val state = remember { mutableStateOf<PurifierScheduleState?>(null) }
    var changed by remember { mutableIntStateOf(0) }
    LaunchedEffect(api, changed) {
        parsePurifierSchedule(api?.ask("church_drive/purifier_schedule", JSONObject()))?.let { state.value = it }
    }
    DisposableEffect(api) {
        val sub = api?.subscribe("subscribe_events", JSONObject().put("event_type", "church_drive_purifier_schedule_changed")) { changed++ } ?: -1
        onDispose { if (sub >= 0) api?.close(sub) }
    }
    return state
}

/** A fan's speeds as schedule choices: "As it was", then its preset modes ("Speed 1", "Sleep"...). */
fun fanScheduleModes(fan: EntityState?): List<Pair<String?, String>> =
    listOf<Pair<String?, String>>(null to "As it was") + fan?.list("preset_modes").orEmpty().map { it to presetLabel(it) }

/**
 * What the phone app adds to a device's page, for an administrator: a Schedule row for each of its blinds, fans and humidifiers,
 * and for the air purifier its own schedule. Tapping the row opens the schedules in place, under it.
 */
@Composable
fun DeviceScheduleRows(item: DeviceHealthItem, entities: Map<String, EntityState>) {
    if (!LocalIsAdmin.current) return
    val purifier = rememberPurifierSchedule()
    val purifierEntity = purifier.value?.entity?.takeIf { it.isNotBlank() }
    val displayModes = rememberPurifierDisplayModes()
    item.entityIds.forEach { id ->
        when {
            id == purifierEntity -> { PurifierScheduleRow(purifier); PurifierDisplayRow(displayModes) }
            id.startsWith("cover.") || id.startsWith("fan.") || id.startsWith("humidifier.") -> DeviceScheduleRow(id, entities[id])
        }
    }
}

/** A Schedule row that opens its editor in place: the header (clock, "Schedule", what's next) and, open, the editor under it. */
@Composable
private fun ScheduleRow(
    line: String, open: Boolean, onToggle: () -> Unit, title: String = "Schedule", icon: String = "cd:clock", editor: @Composable (ToneColors) -> Unit,
) {
    val neutral = panelNeutral()
    val ink = neutral.onContainer
    EntityCard(neutral.container, ink) {
        Row(
            Modifier.fillMaxWidth().clickable(onClickLabel = if (open) "Close $title" else "Change $title", onClick = onToggle),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HaIcon(icon, CdVectors.Info, ink, 24.dp)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = ink)
                if (!open) Text(line, style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.8f))
            }
            HaIcon(if (open) "cd:chevron-up" else "cd:chevron-right", CdVectors.Info, ink.copy(alpha = 0.7f), 22.dp)
        }
        if (open) editor(neutral)
    }
}

@Composable
private fun DeviceScheduleRow(entity: String, state: EntityState?) {
    val api = LocalHaApi.current
    val schedule = rememberBlindSchedule(entity)
    val s = schedule.value ?: return
    var open by remember { mutableStateOf(false) }
    val fan = !entity.startsWith("cover.")
    val (on, off) = if (fan) "On" to "Off" else "Open" to "Close"
    val line = when {
        s.schedules.isEmpty() -> if (fan) "Turn on and off at set times" else "Open and close at set times"
        !s.enabled -> "Off"
        else -> s.next?.let { "Next: $it" } ?: s.schedules.joinToString(" · ") { slotTimesText(it.toSlot(), on, off) }
    }
    ScheduleRow(line, open, { open = !open }) { tone ->
        ScheduleEditor(
            words = ScheduleWords(
                firstLabel = "$on at", secondLabel = "$off at", optionalTimes = true,
                emptyNote = if (fan) "No schedules. Add one, or it is left as it is." else "No schedules. Add one, or the blind is left as it is.",
                clashWinner = off,
                modes = if (entity.startsWith("fan.")) fanScheduleModes(state) else emptyList(),
            ),
            initialEnabled = s.enabled,
            initial = s.schedules.map { it.toSlot() },
            tone = tone,
            problemOf = { list -> blindProblem(list.map { it.toBlind() })?.replace("to open or to close", if (fan) "to turn on or off" else "to open or to close") },
            overlaps = { list -> blindOverlaps(list.map { it.toBlind() }) },
            newSchedule = { list -> newBlindSchedule(list.map { it.toBlind() }).toSlot().let { if (fan) it.copy(first = "22:00", second = "07:00") else it } },
            save = { enabled, list ->
                val saved = parseBlindSchedule(api?.ask("church_drive/blind_schedule/set", blindSetBody(entity, enabled, list.map { it.toBlind() })))
                if (saved != null) { schedule.value = saved; null }
                else "The house didn't accept that${api?.lastErrorCode?.let { " ($it)" }.orEmpty()}."
            },
            onDone = { open = false },
        )
    }
}

@Composable
private fun PurifierScheduleRow(purifier: MutableState<PurifierScheduleState?>) {
    val api = LocalHaApi.current
    val s = purifier.value ?: return
    var open by remember { mutableStateOf(false) }
    val line = when {
        s.schedules.isEmpty() -> "Auto and Sleep at set times"
        !s.enabled -> "Off"
        else -> s.next?.let { "Next: $it" } ?: s.schedules.joinToString(" · ") { purifierSummary(it) }
    }
    ScheduleRow(line, open, { open = !open }) { tone ->
        ScheduleEditor(
            words = ScheduleWords(
                firstLabel = "Auto from", secondLabel = "Sleep from", optionalTimes = false,
                emptyNote = "No schedules. Add one, or the purifier is left as it is.", clashWinner = "Auto",
            ),
            initialEnabled = s.enabled,
            initial = s.schedules.map { it.toSlot() },
            tone = tone,
            problemOf = { list -> purifierProblem(list.map { it.toPurifier() }) },
            overlaps = { list -> purifierOverlaps(list.map { it.toPurifier() }) },
            newSchedule = { list -> newPurifierSchedule(list.map { it.toPurifier() }).toSlot() },
            save = { enabled, list ->
                val saved = parsePurifierSchedule(api?.ask("church_drive/purifier_schedule/set", purifierSetBody(enabled, list.map { it.toPurifier() })))
                if (saved != null) { purifier.value = saved; null }
                else "The house didn't accept that${api?.lastErrorCode?.let { " ($it)" }.orEmpty()}."
            },
            onDone = { open = false },
        )
    }
}

/**
 * The purifier's display by mode: a row for each of its modes with Leave, Off, Dim and Bright. A pick is saved at once; the house sets the
 * display when the mode changes (so a display changed by hand stays until the next change).
 */
@Composable
private fun PurifierDisplayRow(modes: MutableState<PurifierDisplayModes?>) {
    val api = LocalHaApi.current
    val scope = rememberCoroutineScope()
    val s = modes.value ?: return
    var open by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    ScheduleRow(purifierDisplayLine(s), open, { open = !open }, title = "Display by mode", icon = "cd:sun") { tone ->
        Column(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (s.display == null) Text("This purifier's display light wasn't found.", style = MaterialTheme.typography.bodySmall, color = tone.onContainer.copy(alpha = 0.8f))
            s.presetModes.forEach { mode ->
                Text(presetLabel(mode), style = MaterialTheme.typography.titleSmall, color = tone.onContainer)
                val items = DISPLAY_MODE_CHOICES.map { (choice, word) ->
                    TileItem(IconMap.of("purifier", "display_$choice", displayChoiceIcon(choice)), word, s.choiceFor(mode) == choice) {
                        scope.launch {
                            val saved = parsePurifierDisplayModes(api?.ask("church_drive/purifier_display/set", purifierDisplaySetBody(mode, choice)))
                            if (saved != null) { modes.value = saved; problem = null }
                            else problem = "The house didn't accept that${api?.lastErrorCode?.let { " ($it)" }.orEmpty()}."
                        }
                    }
                }
                TileRow(items, tone, tone.onContainer)
            }
            problem?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            Text("The display is set when the mode changes. Change it by hand and it stays until the next change.", style = MaterialTheme.typography.bodySmall, color = tone.onContainer.copy(alpha = 0.7f))
        }
    }
}

/** The icon a display choice has unless the Icon Styles card says otherwise (the group "purifier", key "display_<choice>"). */
private fun displayChoiceIcon(choice: String): String = when (choice) {
    "off" -> "cd:sun-off"
    "dim" -> "cd:sun-half"
    "bright" -> "cd:sun"
    else -> "cd:minus"
}
