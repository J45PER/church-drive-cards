package com.churchdrive.app.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

private const val ACTIVITY_PAGE = 100

/**
 * The activity log (administrators, opened from the account panel): what people did from the app or a dashboard, what the assistant did
 * (because someone asked him, or because an automation ran his script) and who arrived or left. Chosen from drop-down lists: the dates
 * (a day, a few days, everything, or an exact date-and-time range), the time of day, the kind of action and the person. New lines appear by
 * themselves. The house (activity.py) keeps 30 days and only answers administrators.
 */
@Composable
fun ActivityPage() {
    val api = LocalHaApi.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var filters by remember { mutableStateOf(ActivityFilters()) }
    var result by remember { mutableStateOf<ActivityResult?>(null) }
    var lines by remember { mutableStateOf<List<ActivityLine>>(emptyList()) }
    var problem by remember { mutableStateOf<String?>(null) }
    var changed by remember { mutableIntStateOf(0) }

    // Asked again whenever a choice changes, and a moment after the house says there is something new (they come in bursts).
    LaunchedEffect(api, filters, changed) {
        if (changed > 0) delay(1_000)
        val answer = parseActivity(api?.ask("church_drive/activity", activityQuery(filters, System.currentTimeMillis(), limit = ACTIVITY_PAGE)))
        if (answer != null) {
            result = answer
            lines = answer.lines
            problem = null
        } else problem = activityProblem(api?.lastErrorCode)
    }
    DisposableEffect(api) {
        val sub = api?.subscribe("subscribe_events", JSONObject().put("event_type", "church_drive_activity_changed")) { changed++ } ?: -1
        onDispose { if (sub >= 0) api?.close(sub) }
    }

    val r = result
    val assistant = r?.assistant ?: "Clive"
    val now = System.currentTimeMillis()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActivityDropdown("Dates", filters.day.label, ActivityDay.entries.map { it.label }, Modifier.weight(1f)) { filters = filters.withDay(ActivityDay.entries[it], now) }
            ActivityDropdown("Actions", activityActionLabel(filters.action, assistant), ActivityAction.entries.map { activityActionLabel(it, assistant) }, Modifier.weight(1f)) {
                filters = filters.copy(action = ActivityAction.entries[it])
            }
        }
        if (filters.day == ActivityDay.Custom) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActivityField("From", filters.from?.let { activityStamp(it) } ?: "Any time", Modifier.weight(1f)) {
                    pickDateTime(context, filters.from ?: now) { filters = filters.copy(from = it) }
                }
                ActivityField("To", filters.to?.let { activityStamp(it) } ?: "Any time", Modifier.weight(1f)) {
                    pickDateTime(context, filters.to ?: now) { filters = filters.copy(to = it) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (filters.usesTimeOfDay()) {
                ActivityDropdown("Time of day", filters.time.label, ActivityTime.entries.map { it.label }, Modifier.weight(1f)) { filters = filters.copy(time = ActivityTime.entries[it]) }
            }
            val people = r?.people.orEmpty()
            ActivityDropdown("Who", filters.who ?: "Everyone", listOf("Everyone") + people, Modifier.weight(1f)) { filters = filters.copy(who = if (it == 0) null else people[it - 1]) }
        }

        when {
            r == null && problem != null -> Text(problem.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            r == null -> LoadingNote("Loading the activity log…")
            else -> {
                Text(activitySummary(filters, r.total), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (lines.isEmpty()) Text(
                    "Nothing matches these choices. Actions are recorded from when Church Drive 0.71.0 started running in Home Assistant, and the house keeps 30 days.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                activityDays(lines, now).forEach { (day, list) ->
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

private val FieldShape = RoundedCornerShape(12.dp)

/** A drop-down list: the label above the current choice, and the options in a menu; [onPick] gets the position chosen. */
@Composable
private fun ActivityDropdown(label: String, value: String, options: List<String>, modifier: Modifier, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        ActivityField(label, value, Modifier.fillMaxWidth()) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEachIndexed { i, text ->
                DropdownMenuItem(text = { Text(text, fontWeight = if (text == value) FontWeight.Bold else FontWeight.Normal) }, onClick = { open = false; onPick(i) })
            }
        }
    }
}

/** A tappable box with a small label above its value (a drop-down's face, and the ends of a date range). */
@Composable
private fun ActivityField(label: String, value: String, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.clip(FieldShape).border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), FieldShape).clickable(onClick = onClick).padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Asks for a date, then a time, with Android's own pickers, and gives the moment chosen (milliseconds). */
private fun pickDateTime(context: Context, initial: Long, onPicked: (Long) -> Unit) {
    val zone = ZoneId.systemDefault()
    val at = Instant.ofEpochMilli(initial).atZone(zone)
    DatePickerDialog(context, { _, year, month, day ->
        TimePickerDialog(context, { _, hour, minute ->
            onPicked(ZonedDateTime.of(year, month + 1, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli())
        }, at.hour, at.minute, true).show()
    }, at.year, at.monthValue - 1, at.dayOfMonth).show()
}

@Composable
private fun ActivityRow(line: ActivityLine, assistant: String) {
    val dot = when (line.kind) {
        "clive" -> MaterialTheme.colorScheme.tertiary
        "clive_auto" -> MaterialTheme.colorScheme.secondary
        "presence" -> toneColors(Tone.Green).accent
        "outside" -> toneColors(Tone.Amber).accent
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
