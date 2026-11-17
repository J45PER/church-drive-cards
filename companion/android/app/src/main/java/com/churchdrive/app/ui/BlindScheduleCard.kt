package com.churchdrive.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.json.JSONObject

/**
 * A blind's schedules, kept by the house (`church_drive/blind_schedule`) and refreshed when they change. Null until the house
 * answers, and for good if the house is older and has no blind schedules (the device page then has no Schedule).
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

/** The words of a blind's schedule page. */
private fun blindWords(name: String) = ScheduleWords(
    title = "$name schedule",
    firstLabel = "Open at",
    secondLabel = "Close at",
    optionalTimes = true,
    emptyNote = "No schedules. Add one, or the blind is left as it is.",
    clashWinner = "Close",
)

/** A blind's schedules on a page of their own (from its device page): the list, each schedule opened on its own, Save and Cancel. */
@Composable
fun BlindSchedulePage(entity: String, name: String, initial: BlindScheduleState, onSaved: (BlindScheduleState) -> Unit, onClose: () -> Unit) {
    val api = LocalHaApi.current
    ScheduleEditorPage(
        words = blindWords(name),
        initialEnabled = initial.enabled,
        initial = initial.schedules.map { it.toSlot() },
        status = listOfNotNull(
            if (!initial.enabled) "Off" else initial.next?.let { "Next: $it" },
            initial.lastFailed?.let { "The last ${it.lowercase()} didn't go through." },
        ).joinToString(" · ").ifBlank { null },
        problemOf = { list -> blindProblem(list.map { it.toBlind() }) },
        overlaps = { list -> blindOverlaps(list.map { it.toBlind() }) },
        newSchedule = { list -> newBlindSchedule(list.map { it.toBlind() }).toSlot() },
        save = { enabled, list ->
            val saved = parseBlindSchedule(api?.ask("church_drive/blind_schedule/set", blindSetBody(entity, enabled, list.map { it.toBlind() })))
            if (saved != null) { onSaved(saved); null }
            else "The house didn't accept that${api?.lastErrorCode?.let { " ($it)" }.orEmpty()}."
        },
        onClose = onClose,
    )
}

/**
 * What the phone app adds to a device's page: for an administrator, a Schedule row for each blind the device has (when the house
 * keeps blind schedules), saying when it next moves; the row opens [BlindSchedulePage].
 */
@Composable
fun DeviceScheduleRows(item: DeviceHealthItem) {
    if (!LocalIsAdmin.current) return
    item.entityIds.filter { it.startsWith("cover.") }.forEach { id -> BlindScheduleRow(id, item.name) }
}

@Composable
private fun BlindScheduleRow(entity: String, name: String) {
    val schedule = rememberBlindSchedule(entity)
    val s = schedule.value ?: return
    var open by remember { mutableStateOf(false) }
    val neutral = panelNeutral()
    val ink = neutral.onContainer
    val line = when {
        s.schedules.isEmpty() -> "Open and close at set times"
        !s.enabled -> "Off"
        else -> s.next?.let { "Next: $it" } ?: s.schedules.joinToString(" · ") { blindSummary(it) }
    }
    EntityCard(neutral.container, ink, Modifier.clickable(onClickLabel = "Change the schedule") { open = true }) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HaIcon("mdi:clock-outline", CdVectors.Info, ink, 24.dp)
            Column(Modifier.weight(1f)) {
                Text("Schedule", style = MaterialTheme.typography.titleMedium, color = ink)
                Text(line, style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.8f))
            }
            HaIcon("mdi:chevron-right", CdVectors.Info, ink.copy(alpha = 0.7f), 24.dp)
        }
    }
    if (open) BlindSchedulePage(entity, name, s, onSaved = { schedule.value = it }, onClose = { open = false })
}
