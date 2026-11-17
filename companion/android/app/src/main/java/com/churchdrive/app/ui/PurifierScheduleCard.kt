package com.churchdrive.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.json.JSONObject

/**
 * The dashboard's `custom:purifier-schedule-card`: when the air purifier goes to Auto and to Sleep, one line per
 * schedule ("Mon-Fri Auto: 07:00-23:30"). Nothing changes from that view; Edit (administrators) opens the full-page editor
 * ([ScheduleEditorPage]) with Save and Cancel, so a stray tap can't alter it. The house runs the schedules (purifier_schedule.py).
 */
@Composable
fun PurifierScheduleCard(config: JSONObject) {
    val api = LocalHaApi.current
    val admin = LocalIsAdmin.current
    var state by remember { mutableStateOf<PurifierScheduleState?>(null) }
    var missing by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
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
    if (editing) PurifierSchedulePage(s, onSaved = { state = it }, onClose = { editing = false })
    if (fold.value) {
        GridCard(neutral.container, ink, 2.5f, title, fold = fold) {
            FoldBand(0.5f, if (admin) 7f else 8f, ink, s.schedules.firstOrNull()?.let { purifierSummary(it) } ?: "No schedules set.", word.ifBlank { null },
                if (admin) listOf(TileItem("mdi:pencil", "Edit", false) { editing = true }) else emptyList(), neutral, true)
        }
        return
    }
    // A line for each schedule, then when it next changes, then Edit.
    val lines = (if (s.schedules.isEmpty()) 1 else s.schedules.size) + (if (word.isNotEmpty()) 1 else 0) + (if (s.lastFailed != null) 1 else 0)
    val editY = 1f + 0.5f * lines + 0.5f
    GridCard(neutral.container, ink, (if (admin) editY + 1f else 1f + 0.5f * lines) + 0.5f, title, fold = fold) {
        Column(Modifier.at(0.5f, 1f, 7f, 0.5f * lines + 0.25f)) {
            if (s.schedules.isEmpty()) Text("No schedules set.", style = MaterialTheme.typography.bodyMedium, color = ink)
            s.schedules.forEach { Text(purifierSummary(it), style = MaterialTheme.typography.bodyLarge, color = ink.copy(alpha = if (s.enabled) 1f else 0.55f)) }
            if (word.isNotEmpty()) Text(word, style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.8f))
            s.lastFailed?.let { Text("The last change to $it didn't go through.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        }
        if (admin) GridTiles(listOf(TileItem("mdi:pencil", "Edit", false) { editing = true }), editY, 1f, neutral, ink, true, stacked = false)
    }
}

/** The purifier's schedules on a page of their own: the list, each schedule opened on its own, Save and Cancel. */
@Composable
private fun PurifierSchedulePage(initial: PurifierScheduleState, onSaved: (PurifierScheduleState) -> Unit, onClose: () -> Unit) {
    val api = LocalHaApi.current
    ScheduleEditorPage(
        words = ScheduleWords(
            title = "Purifier schedule",
            firstLabel = "Auto from",
            secondLabel = "Sleep from",
            optionalTimes = false,
            emptyNote = "No schedules. Add one, or the purifier is left as it is.",
            clashWinner = "Auto",
        ),
        initialEnabled = initial.enabled,
        initial = initial.schedules.map { it.toSlot() },
        status = listOfNotNull(
            if (!initial.enabled) "Off" else initial.next?.let { "Next: $it" },
            initial.lastFailed?.let { "The last change to $it didn't go through." },
        ).joinToString(" · ").ifBlank { null },
        problemOf = { list -> purifierProblem(list.map { it.toPurifier() }) },
        overlaps = { list -> purifierOverlaps(list.map { it.toPurifier() }) },
        newSchedule = { list -> newPurifierSchedule(list.map { it.toPurifier() }).toSlot() },
        save = { enabled, list ->
            val saved = parsePurifierSchedule(api?.ask("church_drive/purifier_schedule/set", purifierSetBody(enabled, list.map { it.toPurifier() })))
            if (saved != null) { onSaved(saved); null }
            else "The house didn't accept that${api?.lastErrorCode?.let { " ($it)" }.orEmpty()}."
        },
        onClose = onClose,
    )
}
