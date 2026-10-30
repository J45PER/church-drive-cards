package com.churchdrive.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

private const val ACTIVITY_PAGE = 100

/**
 * The activity log (administrators, opened from the account panel): what people did from the app or a dashboard, what the assistant did
 * (because someone asked him, or because an automation ran his script) and who arrived or left. Filtered by day and time of day, by kind and by
 * person; new lines appear by themselves. The house (activity.py) keeps 30 days and only answers administrators.
 */
@Composable
fun ActivityPage() {
    val api = LocalHaApi.current
    val scope = rememberCoroutineScope()
    var filters by remember { mutableStateOf(ActivityFilters()) }
    var result by remember { mutableStateOf<ActivityResult?>(null) }
    var lines by remember { mutableStateOf<List<ActivityLine>>(emptyList()) }
    var failed by remember { mutableStateOf(false) }
    var changed by remember { mutableIntStateOf(0) }

    // Asked again whenever a choice changes, and a moment after the house says there is something new (they come in bursts).
    LaunchedEffect(api, filters, changed) {
        if (changed > 0) delay(1_000)
        val answer = parseActivity(api?.ask("church_drive/activity", activityQuery(filters, System.currentTimeMillis(), limit = ACTIVITY_PAGE)))
        if (answer != null) {
            result = answer
            lines = answer.lines
            failed = false
        } else if (result == null) failed = true
    }
    DisposableEffect(api) {
        val sub = api?.subscribe("subscribe_events", JSONObject().put("event_type", "church_drive_activity_changed")) { changed++ } ?: -1
        onDispose { if (sub >= 0) api?.close(sub) }
    }

    val r = result
    val assistant = r?.assistant ?: "Clive"
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ActivityChips("Day", ActivityDay.entries.map { it.label to (filters.day == it) }) { i -> filters = filters.copy(day = ActivityDay.entries[i]) }
        ActivityChips("Time", ActivityTime.entries.map { it.label to (filters.time == it) }) { i -> filters = filters.copy(time = ActivityTime.entries[i]) }
        ActivityChips("Show", ACTIVITY_KINDS.map { activityKindLabel(it, assistant) to (it in filters.kinds) }) { i -> filters = filters.toggleKind(ACTIVITY_KINDS[i]) }
        val people = r?.people.orEmpty()
        if (people.isNotEmpty()) ActivityChips("Who", people.map { it to (filters.who == it) }) { i -> filters = filters.toggleWho(people[i]) }

        when {
            r == null && failed -> Text(
                "The activity log couldn't be read from the house. Only administrators can see it, and it needs the latest Church Drive integration (and a restart of Home Assistant).",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            r == null -> LoadingNote("Loading the activity log…")
            else -> {
                Text(activitySummary(filters, r.total), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (lines.isEmpty()) Text("Nothing matches these choices.", style = MaterialTheme.typography.bodyMedium)
                activityDays(lines, System.currentTimeMillis()).forEach { (day, list) ->
                    Text(day, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                    list.forEach { ActivityRow(it, assistant) }
                }
                if (lines.size < r.total) {
                    FilterChip(
                        selected = false,
                        onClick = {
                            scope.launch {
                                val more = parseActivity(api?.ask("church_drive/activity", activityQuery(filters, System.currentTimeMillis(), limit = ACTIVITY_PAGE, offset = lines.size)))
                                if (more != null) lines = lines + more.lines
                            }
                        },
                        label = { Text("Show older") },
                    )
                }
            }
        }
    }
}

/** A labelled row of choice chips that scrolls sideways; [onPick] gets the position of the chip pressed. */
@Composable
private fun ActivityChips(label: String, chips: List<Pair<String, Boolean>>, onPick: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(44.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            chips.forEachIndexed { i, (text, on) -> FilterChip(selected = on, onClick = { onPick(i) }, label = { Text(text) }) }
        }
    }
}

@Composable
private fun ActivityRow(line: ActivityLine, assistant: String) {
    val dot = when (line.kind) {
        "clive" -> MaterialTheme.colorScheme.tertiary
        "clive_auto" -> MaterialTheme.colorScheme.secondary
        "presence" -> toneColors(Tone.Green).accent
        else -> MaterialTheme.colorScheme.primary
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Text(eventClock(line.ts), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(40.dp).padding(top = 3.dp))
        Box(Modifier.size(28.dp).background(dot, CircleShape), contentAlignment = Alignment.Center) {
            HaIcon(line.icon, Icons.Outlined.History, Color.White, 16.dp)
        }
        Column(Modifier.weight(1f)) {
            Text(buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(activityWho(line, assistant)) }
                append(" ")
                append(line.text)
            }, style = MaterialTheme.typography.bodyMedium)
            activityNote(line)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
