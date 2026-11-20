package com.churchdrive.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Everything the house has scheduled, in one list: what Clive was asked to do later or on repeat, and the schedules set from the blind, fan,
 * humidifier and air purifier device pages (`church_drive/schedules`). Coming up first, then switched off, then done. Anyone can look;
 * administrators can switch one off or on, and take away one of Clive's.
 */
@Composable
fun SchedulesPage(isAdmin: Boolean) {
    val api = LocalHaApi.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<ScheduleItem>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var changed by remember { mutableIntStateOf(0) }
    var open by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(api, changed) {
        val got = parseSchedules(api?.ask("church_drive/schedules", JSONObject()))
        if (got != null) items = got else if (items == null) failed = true
    }
    DisposableEffect(api) {
        val subs = listOf("church_drive_routines_changed", "church_drive_blind_schedule_changed", "church_drive_purifier_schedule_changed").map {
            api?.subscribe("subscribe_events", JSONObject().put("event_type", it)) { changed++ } ?: -1
        }
        onDispose { subs.forEach { if (it >= 0) api?.close(it) } }
    }
    val list = items
    when {
        list == null -> LoadingNote(if (failed) "Home Assistant didn't answer: the house may need updating." else "Loading the schedules from Home Assistant…")
        list.isEmpty() -> PanelCard {
            PanelRow(CdVectors.Info, "Nothing is scheduled", "Ask Clive to do something later or on repeat, or set a schedule from a blind, fan or humidifier's page.")
        }
        else -> Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            groupSchedules(list).forEach { (group, rows) ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(group.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp))
                    PanelCard {
                        rows.forEach { item ->
                            val key = "${item.source}:${item.id}"
                            val detail = (item.nextText?.takeIf { group == ScheduleGroup.Coming }?.let { listOf("Next: $it") } ?: emptyList()) + item.lines.filter { it != item.nextText } + scheduleSourceWord(item)
                            PanelRow(
                                CdVectors.Info, item.title, detail.joinToString("\n"),
                                onClick = if (isAdmin && item.source == "clive") ({ open = if (open == key) null else key }) else null,
                                leading = { HaIcon(scheduleIcon(item), CdVectors.Info, MaterialTheme.colorScheme.onSurfaceVariant, 24.dp) },
                                trailing = if (isAdmin && !item.done) ({
                                    Switch(checked = item.enabled, onCheckedChange = { on ->
                                        val message = scheduleToggle(item, on) ?: return@Switch
                                        scope.launch { api?.ask(message.first, message.second); changed++ }
                                    })
                                }) else null,
                            )
                            if (open == key && item.source == "clive") PanelRow(
                                CdVectors.Close, "Remove this", "Clive won't do it any more",
                                onClick = { scope.launch { api?.ask("church_drive/routines/delete", JSONObject().put("routine_id", item.id)); open = null; changed++ } },
                            )
                        }
                    }
                }
            }
        }
    }
}
