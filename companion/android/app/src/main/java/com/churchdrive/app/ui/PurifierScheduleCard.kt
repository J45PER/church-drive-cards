package com.churchdrive.app.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONObject

/** What is being edited: whether the schedules run, and the schedules. Null while the card is only showing them. */
private data class PurifierDraft(val enabled: Boolean, val schedules: List<PurifierSchedule>)

/**
 * The dashboard's `custom:purifier-schedule-card`: when the air purifier goes to Auto and to Sleep, one line per
 * schedule ("Mon-Fri Auto: 07:00-23:30"). Nothing changes from that view; Edit (administrators) opens the editor with
 * Save and Cancel, so a stray tap can't alter it. The house runs the schedules (purifier_schedule.py).
 */
@Composable
fun PurifierScheduleCard(config: JSONObject) {
    val api = LocalHaApi.current
    val admin = LocalIsAdmin.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<PurifierScheduleState?>(null) }
    var missing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf<PurifierDraft?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var changed by remember { mutableIntStateOf(0) }

    // Asked again when the house says the schedule changed (from another phone or the dashboard).
    LaunchedEffect(api, changed) {
        val answer = parsePurifierSchedule(api?.ask("church_drive/purifier_schedule", JSONObject()))
        if (answer != null) state = answer else if (state == null) missing = true
    }
    DisposableEffect(api) {
        val sub = api?.subscribe("subscribe_events", JSONObject().put("event_type", "church_drive_purifier_schedule_changed")) { changed++ } ?: -1
        onDispose { if (sub >= 0) api?.close(sub) }
    }

    val neutral = panelNeutral()
    val s = state
    val title = config.optString("title").ifBlank { "Purifier schedule" }
    val ink = neutral.onContainer
    val fold = rememberFold("grid-fold|purifier-schedule")
    val word = when {
        s == null -> if (missing) "" else "Loading…"
        !s.enabled -> "Schedule off"
        s.next != null -> "Next: ${s.next}"
        else -> ""
    }
    if (s == null) {
        GridCard(neutral.container, ink, 3f, title) {
            if (missing) Text("Update Church Drive in Home Assistant to use the purifier schedule.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.at(0.5f, 1f, 7f, 1.5f))
            else Text("Loading…", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.at(0.5f, 1f, 7f, 1f))
        }
        return
    }
    val d = draft
    if (d == null && fold.value) {
        GridCard(neutral.container, ink, 2.5f, title, fold = fold) {
            FoldBand(0.5f, if (admin) 7f else 8f, ink, s.schedules.firstOrNull()?.let { purifierSummary(it) } ?: "No schedules set.", word.ifBlank { null },
                if (admin) listOf(TileItem("mdi:pencil", "Edit", false) { message = null; draft = PurifierDraft(s.enabled, s.schedules) }) else emptyList(), neutral, true)
        }
        return
    }
    if (d == null) {
        // Viewing: a line for each schedule, then when it next changes, then Edit.
        val lines = (if (s.schedules.isEmpty()) 1 else s.schedules.size) + (if (word.isNotEmpty()) 1 else 0) + (if (s.lastFailed != null) 1 else 0) + (if (message != null) 1 else 0)
        val editY = 1f + 0.5f * lines + 0.5f
        GridCard(neutral.container, ink, (if (admin) editY + 1f else 1f + 0.5f * lines) + 0.5f, title, fold = fold) {
            Column(Modifier.at(0.5f, 1f, 7f, 0.5f * lines + 0.25f)) {
                if (s.schedules.isEmpty()) Text("No schedules set.", style = MaterialTheme.typography.bodyMedium, color = ink)
                s.schedules.forEach { Text(purifierSummary(it), style = MaterialTheme.typography.bodyLarge, color = ink.copy(alpha = if (s.enabled) 1f else 0.55f)) }
                if (word.isNotEmpty()) Text(word, style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.8f))
                s.lastFailed?.let { Text("The last change to $it didn't go through.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
            if (admin) GridTiles(listOf(TileItem("mdi:pencil", "Edit", false) { message = null; draft = PurifierDraft(s.enabled, s.schedules) }), editY, 1f, neutral, ink, true, stacked = false)
        }
        return
    }
    // Editing: no fold arrow. The switch, then a block for each schedule (its days, its two times, Remove), Add schedule, Cancel and Save.
    val overlap = purifierOverlaps(d.schedules)
    val blocks = d.schedules.size
    val afterBlocks = 2.25f + 4.5f * blocks
    val noteY = afterBlocks
    val notes = (if (d.schedules.isEmpty()) 1 else 0) + (if (overlap != null) 1 else 0) + (if (message != null) 1 else 0)
    val addY = noteY + (if (notes > 0) 0.75f * notes + 0.25f else 0f)
    val saveY = addY + 1.5f
    GridCard(neutral.container, ink, saveY + 1.5f, title) {
        Row(Modifier.at(0.5f, 1f, 7f, 1f), verticalAlignment = Alignment.CenterVertically) {
            Text("Schedule on", style = MaterialTheme.typography.bodyLarge, color = ink, modifier = Modifier.weight(1f))
            Switch(checked = d.enabled, onCheckedChange = { draft = d.copy(enabled = it); message = null })
        }
        d.schedules.forEachIndexed { i, sch ->
            fun replace(next: PurifierSchedule) { draft = d.copy(schedules = d.schedules.toMutableList().also { it[i] = next }); message = null }
            val y = 2.25f + 4.5f * i
            Text(purifierSummary(sch), style = MaterialTheme.typography.labelLarge, color = ink, modifier = Modifier.at(0.5f, y, 7f, 0.5f))
            // Seven day buttons with a quarter cell between: each 0.79 of a cell.
            val dw = (7f - 6 * 0.25f) / 7f
            PURIFIER_DAY_NAMES.forEachIndexed { day, name ->
                GridButton(TileItem("", name, day in sch.days) { replace(purifierToggleDay(sch, day)) }, neutral, ink, true, false, Modifier.at(0.5f + day * (dw + 0.25f), y + 0.5f, dw, 1f))
            }
            PurifierTime("Auto from", sch.auto, Modifier.at(0.5f, y + 2f, 3.25f, 1f)) { replace(sch.copy(auto = it)) }
            PurifierTime("Sleep from", sch.sleep, Modifier.at(4.25f, y + 2f, 3.25f, 1f)) { replace(sch.copy(sleep = it)) }
            GridTiles(listOf(TileItem("mdi:delete", "Remove", false) { draft = d.copy(schedules = d.schedules.filterIndexed { j, _ -> j != i }); message = null }), y + 3.25f, 1f, neutral, ink, true, stacked = false)
        }
        Column(Modifier.at(0.5f, noteY, 7f, 0.75f * notes + 0.25f)) {
            if (d.schedules.isEmpty()) Text("No schedules. Add one, or the purifier is left as it is.", style = MaterialTheme.typography.bodyMedium, color = ink)
            overlap?.let { Text("$it ${if (',' in it) "are" else "is"} in more than one schedule. If both change at the same time, Auto wins.", style = MaterialTheme.typography.bodySmall, color = ink) }
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        }
        GridTiles(listOf(TileItem("mdi:plus", "Add schedule", false) { draft = d.copy(schedules = d.schedules + newPurifierSchedule(d.schedules)); message = null }), addY, 1f, neutral, ink, true, stacked = false)
        GridTiles(
            listOf(
                TileItem("mdi:close", "Cancel", false) { draft = null; message = null },
                TileItem("mdi:check", "Save", false) {
                    val why = purifierProblem(d.schedules)
                    if (why != null) message = why
                    else scope.launch {
                        val saved = parsePurifierSchedule(api?.ask("church_drive/purifier_schedule/set", purifierSetBody(d.enabled, d.schedules)))
                        if (saved != null) { state = saved; draft = null; message = null }
                        else message = "The house didn't accept that${api?.lastErrorCode?.let { " ($it)" }.orEmpty()}."
                    }
                },
            ),
            saveY, 1f, neutral, ink, true, stacked = false,
        )
    }
}

/** A time that opens the clock to change it. */
@Composable
private fun PurifierTime(label: String, time: String, modifier: Modifier, onPick: (String) -> Unit) {
    val context = LocalContext.current
    val ink = panelNeutral().onContainer
    Row(
        modifier.clip(RoundedCornerShape(18.dp)).background(ink.copy(alpha = 0.10f)).clickable {
            val hour = time.substringBefore(':').toIntOrNull() ?: 0
            val minute = time.substringAfter(':', "0").toIntOrNull() ?: 0
            TimePickerDialog(context, { _, h, m -> onPick("%02d:%02d".format(h, m)) }, hour, minute, true).show()
        }.padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = ink, maxLines = 1)
        Text(time, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyLarge)
    }
}
