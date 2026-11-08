package com.churchdrive.app.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
    if (LocalCompactCards.current) {
        // Key info only: when it next changes. Editing needs the full card.
        val word = when {
            s == null -> if (missing) "" else "Loading…"
            !s.enabled -> "Schedule off"
            s.next != null -> "Next: ${s.next}"
            else -> ""
        }
        CompactStrip(
            neutral, config.optString("title").ifBlank { "Purifier schedule" }, null, word,
            detail = s?.schedules?.take(2)?.joinToString(" · ") { purifierSummary(it) },
        )
        return
    }
    EntityCard(neutral.container, neutral.onContainer) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(config.optString("title").ifBlank { "Purifier schedule" }, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            val word = when {
                draft != null -> "Editing"
                s == null -> if (missing) "" else "Loading…"
                !s.enabled -> "Schedule off"
                s.next != null -> "Next: ${s.next}"
                else -> ""
            }
            if (word.isNotEmpty()) Text(word, style = MaterialTheme.typography.bodyMedium, color = neutral.onContainer.copy(alpha = 0.8f))
        }
        if (s == null) {
            if (missing) Text("Update Church Drive in Home Assistant to use the purifier schedule.", style = MaterialTheme.typography.bodyMedium)
            return@EntityCard
        }
        val d = draft
        if (d == null) {
            if (s.schedules.isEmpty()) Text("No schedules set.", style = MaterialTheme.typography.bodyMedium)
            s.schedules.forEach { Text(purifierSummary(it), style = MaterialTheme.typography.bodyLarge, color = neutral.onContainer.copy(alpha = if (s.enabled) 1f else 0.55f)) }
            s.lastFailed?.let { Text("The last change to $it didn't go through.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            if (admin) {
                TileRow(listOf(TileItem("mdi:pencil", "Edit", false) { message = null; draft = PurifierDraft(s.enabled, s.schedules) }), neutral, neutral.onContainer, perRow = 1)
            }
            return@EntityCard
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Schedule on", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Switch(checked = d.enabled, onCheckedChange = { draft = d.copy(enabled = it); message = null })
        }
        if (d.schedules.isEmpty()) Text("No schedules. Add one, or the purifier is left as it is.", style = MaterialTheme.typography.bodyMedium)
        d.schedules.forEachIndexed { i, sch ->
            fun replace(next: PurifierSchedule) { draft = d.copy(schedules = d.schedules.toMutableList().also { it[i] = next }); message = null }
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(purifierSummary(sch), style = MaterialTheme.typography.labelLarge)
                TileRow(
                    PURIFIER_DAY_NAMES.mapIndexed { day, name -> TileItem("mdi:calendar-today", name, day in sch.days) { replace(purifierToggleDay(sch, day)) } },
                    neutral, neutral.onContainer,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    PurifierTime("Auto from", sch.auto) { replace(sch.copy(auto = it)) }
                    PurifierTime("Sleep from", sch.sleep) { replace(sch.copy(sleep = it)) }
                }
                TileRow(listOf(TileItem("mdi:delete", "Remove", false) { draft = d.copy(schedules = d.schedules.filterIndexed { j, _ -> j != i }); message = null }), neutral, neutral.onContainer, perRow = 1)
            }
        }
        purifierOverlaps(d.schedules)?.let { Text("$it ${if (',' in it) "are" else "is"} in more than one schedule. If both change at the same time, Auto wins.", style = MaterialTheme.typography.bodySmall) }
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        TileRow(listOf(TileItem("mdi:plus", "Add schedule", false) { draft = d.copy(schedules = d.schedules + newPurifierSchedule(d.schedules)); message = null }), neutral, neutral.onContainer, perRow = 1)
        TileRow(
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
            neutral, neutral.onContainer,
        )
    }
}

/** A time that opens the clock to change it. */
@Composable
private fun PurifierTime(label: String, time: String, onPick: (String) -> Unit) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            time, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.clip(CircleShape).clickable {
                val hour = time.substringBefore(':').toIntOrNull() ?: 0
                val minute = time.substringAfter(':', "0").toIntOrNull() ?: 0
                TimePickerDialog(context, { _, h, m -> onPick("%02d:%02d".format(h, m)) }, hour, minute, true).show()
            }.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}
