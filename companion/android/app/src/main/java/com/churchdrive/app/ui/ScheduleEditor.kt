package com.churchdrive.app.ui

import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** What a schedule editor calls things: its two times, the sentences that depend on what it schedules, and a fan's speeds. */
class ScheduleWords(
    /** "Open at" and "Close at"; the word before each time in a summary is the first word of these ("Open 07:30"). */
    val firstLabel: String,
    val secondLabel: String,
    /** Either time can be left out (a blind's, a fan's); otherwise both are always set (the purifier's). */
    val optionalTimes: Boolean,
    /** Said when there are no schedules ("…or the blind is left as it is"). */
    val emptyNote: String,
    /** Which time wins when two schedules change at the same moment ("Close"). */
    val clashWinner: String,
    /** What the first time turns a fan on at: (value, label) pairs, the first being null for "as it was". Empty for no choice. */
    val modes: List<Pair<String?, String>> = emptyList(),
)

/**
 * A device's schedules, edited in place where its Schedule row was (on the device's page): the switch that turns them all on or off,
 * then a row per schedule with its days and times. A row opens that schedule in place, with big one-letter day buttons, quick picks
 * (Weekdays, Weekend, Every day), the two times and, for a fan, the speed it turns on at. Nothing reaches the house until Save;
 * Cancel puts it back as it was. Back from a schedule returns to the list, and Back from the list with changes asks first.
 *
 * [save] sends the draft and returns null when the house kept it, or what to say when it didn't. [problemOf] says what stops a save.
 * [onDone] closes the editor (after Save or Cancel). It draws on [tone], the card it sits in.
 */
@Composable
fun ScheduleEditor(
    words: ScheduleWords,
    initialEnabled: Boolean,
    initial: List<SlotSchedule>,
    tone: ToneColors,
    problemOf: (List<SlotSchedule>) -> String?,
    overlaps: (List<SlotSchedule>) -> String?,
    newSchedule: (List<SlotSchedule>) -> SlotSchedule,
    save: suspend (Boolean, List<SlotSchedule>) -> String?,
    onDone: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var enabled by remember { mutableStateOf(initialEnabled) }
    var schedules by remember { mutableStateOf(initial) }
    var open by remember { mutableStateOf<Int?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var askDiscard by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val changed = enabled != initialEnabled || schedules != initial
    BackHandler {
        when {
            open != null -> open = null
            changed -> askDiscard = true
            else -> onDone()
        }
    }
    val ink = tone.onContainer
    val first = words.firstLabel.substringBefore(' ')
    val second = words.secondLabel.substringBefore(' ')
    val index = open?.takeIf { it in schedules.indices }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (index != null) {
            val sch = schedules[index]
            fun replace(next: SlotSchedule) { schedules = schedules.toMutableList().also { it[index] = next }; message = null }
            Text("Schedule ${index + 1} · ${purifierDaysText(sch.days)}", style = MaterialTheme.typography.titleSmall, color = ink)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SCHEDULE_DAY_LETTERS.forEachIndexed { day, letter ->
                    val on = day in sch.days
                    Box(
                        Modifier.weight(1f).height(Ui.TileHeight).clip(RoundedCornerShape(50))
                            .background(if (on) tone.accent else ink.copy(alpha = 0.10f))
                            .clickable(onClickLabel = PURIFIER_DAY_NAMES[day]) { replace(slotToggleDay(sch, day)) },
                        contentAlignment = Alignment.Center,
                    ) { CentredText(letter, if (on) tone.onAccent else ink, Ui.TileText) }
                }
            }
            TileRow(SCHEDULE_DAY_PRESETS.map { (name, days) -> TileItem("", name, sch.days == days) { replace(sch.copy(days = days)) } }, tone, ink)
            ScheduleTime(words.firstLabel, sch.first, words.optionalTimes, ink) { replace(sch.copy(first = it, mode = if (it == null) null else sch.mode)) }
            if (words.modes.isNotEmpty() && sch.first != null) {
                Text("${words.firstLabel.substringBefore(' ')} at", style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.8f))
                TileRow(words.modes.map { (value, label) -> TileItem("", label, sch.mode == value) { replace(sch.copy(mode = value)) } }, tone, ink)
            }
            ScheduleTime(words.secondLabel, sch.second, words.optionalTimes, ink) { replace(sch.copy(second = it)) }
            problemOf(listOf(sch))?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
            TileRow(
                listOf(
                    TileItem("cd:bin", "Remove", false) { schedules = schedules.filterIndexed { j, _ -> j != index }; open = null; message = null },
                    TileItem("cd:check", "Done", false) { open = null },
                ),
                tone, ink,
            )
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Schedule on", style = MaterialTheme.typography.titleSmall, color = ink, modifier = Modifier.weight(1f))
                Switch(checked = enabled, onCheckedChange = { enabled = it; message = null })
            }
            schedules.forEachIndexed { i, sch ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(ink.copy(alpha = 0.10f))
                        .clickable(onClickLabel = "Change this schedule") { open = i }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(purifierDaysText(sch.days), style = MaterialTheme.typography.titleSmall, color = ink.copy(alpha = if (enabled) 1f else 0.55f))
                        Text(
                            slotTimesText(sch, first, second) + (sch.mode?.let { m -> words.modes.firstOrNull { it.first == m }?.second?.let { " · $it" } }.orEmpty()),
                            style = MaterialTheme.typography.bodyMedium, color = ink.copy(alpha = if (enabled) 0.85f else 0.5f),
                        )
                    }
                    HaIcon("cd:chevron-right", CdVectors.Info, ink.copy(alpha = 0.7f), 22.dp)
                }
            }
            if (schedules.isEmpty()) Text(words.emptyNote, style = MaterialTheme.typography.bodyMedium, color = ink)
            overlaps(schedules)?.let { Text("$it ${if (',' in it) "are" else "is"} in more than one schedule. If both change at the same time, ${words.clashWinner} wins.", style = MaterialTheme.typography.bodySmall, color = ink) }
            message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
            TileRow(listOf(TileItem("cd:plus", "Add schedule", false) { schedules = schedules + newSchedule(schedules); open = schedules.lastIndex; message = null }), tone, ink)
            TileRow(
                listOf(
                    TileItem("cd:close", "Cancel", false) { onDone() },
                    TileItem("cd:check", if (saving) "Saving…" else "Save", false) {
                        val why = problemOf(schedules)
                        if (why != null) message = why
                        else if (!saving) scope.launch {
                            saving = true
                            val problem = save(enabled, schedules)
                            saving = false
                            if (problem == null) onDone() else message = problem
                        }
                    },
                ),
                tone, ink,
            )
        }
    }
    if (askDiscard) {
        PopupWindow(onDismissRequest = { askDiscard = false }, shape = PopupShape.Card) { closeAsk ->
            EntityCard(tone.container, ink, Modifier.padding(horizontal = 24.dp)) {
                Text("Discard changes?", style = MaterialTheme.typography.titleMedium, color = ink)
                Text("The schedule stays as it was.", style = MaterialTheme.typography.bodyMedium, color = ink)
                TileRow(
                    listOf(
                        TileItem("cd:pencil", "Keep editing", false) { closeAsk() },
                        TileItem("cd:bin", "Discard", false) { closeAsk(); onDone() },
                    ),
                    tone, ink,
                )
            }
        }
    }
}

/** A time as a wide tile: its label, the time large, a tap opens the clock; a cross clears it when it can be left out. */
@Composable
internal fun ScheduleTime(label: String, time: String?, optional: Boolean, ink: Color, onPick: (String?) -> Unit) {
    val context = LocalContext.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(ink.copy(alpha = 0.10f)).clickable(onClickLabel = "Change $label") {
            val hour = time?.substringBefore(':')?.toIntOrNull() ?: 7
            val minute = time?.substringAfter(':', "0")?.toIntOrNull() ?: 30
            TimePickerDialog(context, { _, h, m -> onPick("%02d:%02d".format(h, m)) }, hour, minute, true).show()
        }.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = ink, maxLines = 1)
            Text(time ?: "Not set", style = MaterialTheme.typography.headlineMedium, color = if (time != null) MaterialTheme.colorScheme.primary else ink.copy(alpha = 0.6f))
        }
        if (optional && time != null) {
            HaIcon("cd:close", CdVectors.Close, ink.copy(alpha = 0.7f), 24.dp, Modifier.clip(RoundedCornerShape(50)).clickable(onClickLabel = "Clear $label") { onPick(null) }.padding(8.dp))
        }
    }
}
