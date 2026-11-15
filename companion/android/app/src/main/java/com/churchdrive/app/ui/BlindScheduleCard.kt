package com.churchdrive.app.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * A blind's schedules, kept by the house (`church_drive/blind_schedule`) and refreshed when they change. Null until the house
 * answers, and for good if the house is older and has no blind schedules (the card then has no Schedule).
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

/** What opens when a blind's card is pressed and held (an administrator): its device page, or its schedule. */
@Composable
fun BlindHoldMenu(name: String, onDevice: () -> Unit, onSchedule: () -> Unit, onDismiss: () -> Unit) {
    PopupWindow(onDismissRequest = onDismiss, shape = PopupShape.Card) { close ->
        Card(Modifier.padding(horizontal = 24.dp)) {
            Column(Modifier.padding(vertical = 8.dp)) {
                Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
                PanelRow(
                    CdVectors.Info, "Device page", "Its settings, battery and history",
                    onClick = { close(); onDevice() },
                    leading = { HaIcon("mdi:devices", CdVectors.Info, MaterialTheme.colorScheme.onSurface, 24.dp) },
                )
                PanelRow(
                    CdVectors.Info, "Schedule", "Open and close at set times",
                    onClick = { close(); onSchedule() },
                    leading = { HaIcon("mdi:clock-outline", CdVectors.Info, MaterialTheme.colorScheme.onSurface, 24.dp) },
                )
            }
        }
    }
}

/** What is being edited: whether the schedules run, and the schedules. */
private data class BlindDraft(val enabled: Boolean, val schedules: List<BlindSchedule>)

/**
 * The editor for a blind's schedules, in place of its card: a switch, a block per schedule (days, Open at, Close at, Remove),
 * Add schedule, and Cancel and Save, as the purifier's. [onDone] gets what the house kept, or null on Cancel.
 */
@Composable
fun BlindScheduleEditor(entity: String, name: String, initial: BlindScheduleState, onDone: (BlindScheduleState?) -> Unit) {
    val api = LocalHaApi.current
    val scope = rememberCoroutineScope()
    val neutral = panelNeutral()
    val ink = neutral.onContainer
    var draft by remember { mutableStateOf(BlindDraft(initial.enabled, initial.schedules)) }
    var message by remember { mutableStateOf<String?>(null) }
    val d = draft
    val overlap = blindOverlaps(d.schedules)
    val blocks = d.schedules.size
    val afterBlocks = 2.25f + 4.5f * blocks
    val noteY = afterBlocks
    val notes = (if (d.schedules.isEmpty()) 1 else 0) + (if (overlap != null) 1 else 0) + (if (message != null) 1 else 0)
    val addY = noteY + (if (notes > 0) 0.75f * notes + 0.25f else 0f)
    val saveY = addY + 1.5f
    GridCard(neutral.container, ink, saveY + 1.5f, "$name Schedule") {
        Row(Modifier.at(0.5f, 1f, 7f, 1f), verticalAlignment = Alignment.CenterVertically) {
            Text("Schedule on", style = MaterialTheme.typography.bodyLarge, color = ink, modifier = Modifier.weight(1f))
            Switch(checked = d.enabled, onCheckedChange = { draft = d.copy(enabled = it); message = null })
        }
        d.schedules.forEachIndexed { i, sch ->
            fun replace(next: BlindSchedule) { draft = d.copy(schedules = d.schedules.toMutableList().also { it[i] = next }); message = null }
            val y = 2.25f + 4.5f * i
            Text(blindSummary(sch), style = MaterialTheme.typography.labelLarge, color = ink, modifier = Modifier.at(0.5f, y, 7f, 0.5f))
            val dw = (7f - 6 * 0.25f) / 7f
            PURIFIER_DAY_NAMES.forEachIndexed { day, dayName ->
                GridButton(TileItem("", dayName, day in sch.days) { replace(blindToggleDay(sch, day)) }, neutral, ink, true, false, Modifier.at(0.5f + day * (dw + 0.25f), y + 0.5f, dw, 1f))
            }
            BlindTime("Open at", sch.open, Modifier.at(0.5f, y + 2f, 3.25f, 1f)) { replace(sch.copy(open = it)) }
            BlindTime("Close at", sch.close, Modifier.at(4.25f, y + 2f, 3.25f, 1f)) { replace(sch.copy(close = it)) }
            GridTiles(listOf(TileItem("mdi:delete", "Remove", false) { draft = d.copy(schedules = d.schedules.filterIndexed { j, _ -> j != i }); message = null }), y + 3.25f, 1f, neutral, ink, true, stacked = false)
        }
        Column(Modifier.at(0.5f, noteY, 7f, 0.75f * notes + 0.25f)) {
            if (d.schedules.isEmpty()) Text("No schedules. Add one, or the blind is left as it is.", style = MaterialTheme.typography.bodyMedium, color = ink)
            overlap?.let { Text("$it ${if (',' in it) "are" else "is"} in more than one schedule. If both change at the same time, Close wins.", style = MaterialTheme.typography.bodySmall, color = ink) }
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        }
        GridTiles(listOf(TileItem("mdi:plus", "Add schedule", false) { draft = d.copy(schedules = d.schedules + newBlindSchedule(d.schedules)); message = null }), addY, 1f, neutral, ink, true, stacked = false)
        GridTiles(
            listOf(
                TileItem("mdi:close", "Cancel", false) { onDone(null) },
                TileItem("mdi:check", "Save", false) {
                    val why = blindProblem(d.schedules)
                    if (why != null) message = why
                    else scope.launch {
                        val saved = parseBlindSchedule(api?.ask("church_drive/blind_schedule/set", blindSetBody(entity, d.enabled, d.schedules)))
                        if (saved != null) onDone(saved)
                        else message = "The house didn't accept that${api?.lastErrorCode?.let { " ($it)" }.orEmpty()}."
                    }
                },
            ),
            saveY, 1f, neutral, ink, true, stacked = false,
        )
    }
}

/** A time that opens the clock to change it; "Not set" until one is picked, and a cross clears it. */
@Composable
private fun BlindTime(label: String, time: String?, modifier: Modifier, onPick: (String?) -> Unit) {
    val context = LocalContext.current
    val ink = panelNeutral().onContainer
    Row(
        modifier.clip(RoundedCornerShape(18.dp)).background(ink.copy(alpha = 0.10f)).clickable {
            val hour = time?.substringBefore(':')?.toIntOrNull() ?: 7
            val minute = time?.substringAfter(':', "0")?.toIntOrNull() ?: 30
            TimePickerDialog(context, { _, h, m -> onPick("%02d:%02d".format(h, m)) }, hour, minute, true).show()
        }.padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = ink, maxLines = 1)
            Text(time ?: "Not set", color = if (time != null) MaterialTheme.colorScheme.primary else ink.copy(alpha = 0.6f), style = MaterialTheme.typography.bodyLarge)
        }
        if (time != null) {
            HaIcon("mdi:close", CdVectors.Close, ink.copy(alpha = 0.7f), 20.dp, Modifier.clip(RoundedCornerShape(50)).clickable { onPick(null) }.padding(4.dp))
        }
    }
}
