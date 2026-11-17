package com.churchdrive.app.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** What a schedule editor calls things: the page's title, its two times, and the sentences that depend on what it schedules. */
class ScheduleWords(
    val title: String,
    /** "Open at" and "Close at"; the word before each time in a summary is the first word of these ("Open 07:30"). */
    val firstLabel: String,
    val secondLabel: String,
    /** Either time can be left out (a blind's); otherwise both are always set (the purifier's). */
    val optionalTimes: Boolean,
    /** Said when there are no schedules ("…or the blind is left as it is"). */
    val emptyNote: String,
    /** Which time wins when two schedules change at the same moment ("Close"). */
    val clashWinner: String,
)

/**
 * A full-page editor for a set of schedules (a blind's, the purifier's), opened over the app. The first view lists the schedules,
 * each a row with its days and times, under the switch that turns them all on or off; a row opens that schedule on its own, with
 * big one-letter day buttons, quick picks (Weekdays, Weekend, Every day) and the two times. Nothing reaches the house until Save;
 * Back from a schedule returns to the list, and Back from the list with changes asks before throwing them away.
 *
 * [save] sends the draft and returns null when the house kept it, or what to say when it didn't. [problemOf] says what stops a save.
 */
@Composable
fun ScheduleEditorPage(
    words: ScheduleWords,
    initialEnabled: Boolean,
    initial: List<SlotSchedule>,
    status: String?,
    problemOf: (List<SlotSchedule>) -> String?,
    overlaps: (List<SlotSchedule>) -> String?,
    newSchedule: (List<SlotSchedule>) -> SlotSchedule,
    save: suspend (Boolean, List<SlotSchedule>) -> String?,
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var enabled by remember { mutableStateOf(initialEnabled) }
    var schedules by remember { mutableStateOf(initial) }
    var open by remember { mutableStateOf<Int?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var askDiscard by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val changed = enabled != initialEnabled || schedules != initial
    PopupWindow(
        onDismissRequest = onClose,
        handleBack = {
            when {
                open != null -> { open = null; true }
                changed -> { askDiscard = true; true }
                else -> false
            }
        },
    ) { close ->
        val back: () -> Unit = {
            if (open != null) open = null
            else if (changed) askDiscard = true
            else close()
        }
        val neutral = panelNeutral()
        val ink = neutral.onContainer
        val first = words.firstLabel.substringBefore(' ')
        val second = words.secondLabel.substringBefore(' ')
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                val index = open?.takeIf { it in schedules.indices }
                Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.clip(RoundedCornerShape(50)).clickable(onClickLabel = "Back") { back() }.padding(12.dp)) {
                        HaIcon("mdi:arrow-left", CdVectors.Info, MaterialTheme.colorScheme.onBackground, 24.dp)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(words.title, style = MaterialTheme.typography.titleLarge, maxLines = 1)
                        if (index != null) Text("Schedule ${index + 1}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (index != null) {
                        val sch = schedules[index]
                        fun replace(next: SlotSchedule) { schedules = schedules.toMutableList().also { it[index] = next }; message = null }
                        EntityCard(neutral.container, ink) {
                            Text("Days", style = MaterialTheme.typography.titleMedium, color = ink)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                SCHEDULE_DAY_LETTERS.forEachIndexed { day, letter ->
                                    val on = day in sch.days
                                    Box(
                                        Modifier.weight(1f).height(Ui.TileHeight).clip(RoundedCornerShape(50))
                                            .background(if (on) neutral.accent else ink.copy(alpha = 0.10f))
                                            .clickable(onClickLabel = PURIFIER_DAY_NAMES[day]) { replace(slotToggleDay(sch, day)) },
                                        contentAlignment = Alignment.Center,
                                    ) { CentredText(letter, if (on) neutral.onAccent else ink, Ui.TileText) }
                                }
                            }
                            TileRow(
                                SCHEDULE_DAY_PRESETS.map { (name, days) -> TileItem("", name, sch.days == days) { replace(sch.copy(days = days)) } },
                                neutral, ink,
                            )
                            Text(purifierDaysText(sch.days), style = MaterialTheme.typography.bodyMedium, color = ink.copy(alpha = 0.8f))
                        }
                        EntityCard(neutral.container, ink) {
                            Text("Times", style = MaterialTheme.typography.titleMedium, color = ink)
                            ScheduleTime(words.firstLabel, sch.first, words.optionalTimes) { replace(sch.copy(first = it)) }
                            ScheduleTime(words.secondLabel, sch.second, words.optionalTimes) { replace(sch.copy(second = it)) }
                        }
                        problemOf(listOf(sch))?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
                        TileRow(
                            listOf(
                                TileItem("mdi:delete", "Remove", false) { schedules = schedules.filterIndexed { j, _ -> j != index }; open = null; message = null },
                                TileItem("mdi:check", "Done", false) { open = null },
                            ),
                            neutral, ink,
                        )
                    } else {
                        EntityCard(neutral.container, ink) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Schedule on", style = MaterialTheme.typography.titleMedium, color = ink)
                                    status?.takeIf { !changed }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.8f)) }
                                }
                                Switch(checked = enabled, onCheckedChange = { enabled = it; message = null })
                            }
                        }
                        schedules.forEachIndexed { i, sch ->
                            EntityCard(neutral.container, ink, Modifier.clickable(onClickLabel = "Change this schedule") { open = i }) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(purifierDaysText(sch.days), style = MaterialTheme.typography.titleMedium, color = ink.copy(alpha = if (enabled) 1f else 0.55f))
                                        Text(slotTimesText(sch, first, second), style = MaterialTheme.typography.bodyLarge, color = ink.copy(alpha = if (enabled) 0.85f else 0.5f))
                                    }
                                    HaIcon("mdi:chevron-right", CdVectors.Info, ink.copy(alpha = 0.7f), 24.dp)
                                }
                            }
                        }
                        if (schedules.isEmpty()) Text(words.emptyNote, style = MaterialTheme.typography.bodyMedium)
                        overlaps(schedules)?.let { Text("$it ${if (',' in it) "are" else "is"} in more than one schedule. If both change at the same time, ${words.clashWinner} wins.", style = MaterialTheme.typography.bodySmall) }
                        message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
                        TileRow(
                            listOf(TileItem("mdi:plus", "Add schedule", false) {
                                schedules = schedules + newSchedule(schedules); open = schedules.lastIndex; message = null
                            }),
                            neutral, ink,
                        )
                        TileRow(
                            listOf(
                                TileItem("mdi:close", "Cancel", false) { close() },
                                TileItem("mdi:check", if (saving) "Saving…" else "Save", false) {
                                    val why = problemOf(schedules)
                                    if (why != null) message = why
                                    else if (!saving) scope.launch {
                                        saving = true
                                        val problem = save(enabled, schedules)
                                        saving = false
                                        if (problem == null) close() else message = problem
                                    }
                                },
                            ),
                            neutral, ink,
                        )
                    }
                    Spacer(Modifier.height(96.dp)) // clears the gesture bar even where the dialog window reports no bottom inset
                }
            }
        }
        if (askDiscard) {
            PopupWindow(onDismissRequest = { askDiscard = false }, shape = PopupShape.Card) { closeAsk ->
                EntityCard(neutral.container, ink, Modifier.padding(horizontal = 24.dp)) {
                    Text("Discard changes?", style = MaterialTheme.typography.titleMedium, color = ink)
                    Text("The schedule stays as it was.", style = MaterialTheme.typography.bodyMedium, color = ink)
                    TileRow(
                        listOf(
                            TileItem("mdi:pencil", "Keep editing", false) { closeAsk() },
                            TileItem("mdi:delete", "Discard", false) { closeAsk(); close() },
                        ),
                        neutral, ink,
                    )
                }
            }
        }
    }
}

/** A time as a wide tile: its label, the time large, a tap opens the clock; a cross clears it when it can be left out. */
@Composable
private fun ScheduleTime(label: String, time: String?, optional: Boolean, onPick: (String?) -> Unit) {
    val context = LocalContext.current
    val ink = panelNeutral().onContainer
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(ink.copy(alpha = 0.10f)).clickable(onClickLabel = "Change $label") {
            val hour = time?.substringBefore(':')?.toIntOrNull() ?: 7
            val minute = time?.substringAfter(':', "0")?.toIntOrNull() ?: 30
            TimePickerDialog(context, { _, h, m -> onPick("%02d:%02d".format(h, m)) }, hour, minute, true).show()
        }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = ink, maxLines = 1)
            Text(
                time ?: "Not set",
                style = MaterialTheme.typography.headlineMedium,
                color = if (time != null) MaterialTheme.colorScheme.primary else ink.copy(alpha = 0.6f),
            )
        }
        if (optional && time != null) {
            HaIcon("mdi:close", CdVectors.Close, ink.copy(alpha = 0.7f), 24.dp, Modifier.clip(RoundedCornerShape(50)).clickable(onClickLabel = "Clear $label") { onPick(null) }.padding(8.dp))
        }
    }
}
