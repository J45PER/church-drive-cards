package com.churchdrive.todo

import com.churchdrive.app.ui.CdVectors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.churchdrive.app.ui.DUE_CHOICES
import com.churchdrive.app.ui.DatePickDialog
import com.churchdrive.app.ui.REPEAT_CHOICES
import com.churchdrive.app.ui.TodoItem
import com.churchdrive.app.ui.WHO_CHOICES
import com.churchdrive.app.ui.buildDescription
import com.churchdrive.app.ui.cleanTime
import com.churchdrive.app.ui.dateWords
import com.churchdrive.app.ui.dueChoiceOf
import com.churchdrive.app.ui.dueDateOf
import com.churchdrive.app.ui.parseTaskWords
import com.churchdrive.app.ui.repeatChoiceOf
import com.churchdrive.app.ui.repeatWords
import com.churchdrive.app.ui.timeOf
import com.churchdrive.app.ui.updateFields
import com.churchdrive.app.ui.whoChoiceOf
import com.churchdrive.app.ui.whoWords
import java.time.LocalDate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.churchdrive.app.ui.FullPage
import com.churchdrive.app.ui.LEAD_CHOICES
import com.churchdrive.app.ui.leadLabel
import com.churchdrive.app.ui.OptionRow
import com.churchdrive.app.ui.TileItem
import com.churchdrive.app.ui.TileRow
import com.churchdrive.app.ui.Tone
import com.churchdrive.app.ui.ToneColors
import com.churchdrive.app.ui.TimeTile
import com.churchdrive.app.ui.WashField
import com.churchdrive.app.ui.toneColors

/**
 * Edit a task: its name, day, repeat, who is reminded and notes, or delete it. For a repeating task "Next due" can be set to
 * any day, for when it has to be moved by hand; the repeat carries on from that day. It writes the same words as the dashboard
 * and the main app, so all three read each other's tasks.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoEditDialog(item: TodoItem, first: String?, tone: ToneColors, lead: Int?, canRemind: Boolean, onSave: (Map<String, Any?>, Int?) -> Unit, onDelete: () -> Unit, onClose: () -> Unit) {
    val today = remember { LocalDate.now() }
    val words = remember(item) { parseTaskWords(item.description) }
    val keepDay = remember(item) { item.due?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } }
    var name by remember { mutableStateOf(item.summary) }
    var due by remember { mutableStateOf(dueChoiceOf(item.due, today)) }
    var picked by remember { mutableStateOf<LocalDate?>(null) }
    var picking by remember { mutableStateOf(false) }
    var repeat by remember { mutableStateOf(repeatChoiceOf(words.repeat)) }
    var who by remember { mutableStateOf(whoChoiceOf(words.who, first)) }
    var time by remember { mutableStateOf(timeOf(words.repeat) ?: timeOf(item.due?.takeIf { 'T' in it }?.substringAfter('T')) ?: "09:00") }
    var notes by remember { mutableStateOf(words.notes) }
    var sure by remember { mutableStateOf(false) }
    var leadChoice by remember { mutableStateOf(lead) }

    val dueOptions = DUE_CHOICES + if (due == "Keep") listOf("Keep") else emptyList()
    val repeatOptions = REPEAT_CHOICES + if (repeat !in REPEAT_CHOICES) listOf(repeat) else emptyList()
    val whoOptions = WHO_CHOICES + if (who !in WHO_CHOICES) listOf(who) else emptyList()
    val repeating = repeat != "None"
    val cleaned = cleanTime(time)
    val shown = picked ?: dueDateOf(due, today) ?: keepDay

    fun save() {
        if (name.isBlank() || (repeating && cleaned == null)) return
        val day = picked ?: dueDateOf(due, today) ?: keepDay ?: today
        val timeOrNull = if (repeating || due != "No date" || picked != null) cleaned else null
        onSave(
            updateFields(
                item.uid, name, due, if (due == "No date" && picked == null) null else timeOrNull, today,
                buildDescription(repeatWords(repeat, cleaned ?: "09:00", day), whoWords(who, first), notes), picked,
            ),
            leadChoice,
        )
    }

    // The same page as the main app's, in the list's colour: wash fields, tile choices, the clock for a time and tile buttons.
    FullPage("Edit task", onClose, tone = tone) {
        WashField(name, { name = it }, "Task", tone)
        Text(if (repeating) "Next due" else "Day", style = MaterialTheme.typography.labelLarge)
        OptionRow(dueOptions, if (picked != null) "" else due, tone) { due = it; picked = null }
        if (shown != null) {
            Text(
                "${dateWords(shown, today)}${if (repeating) ": the ones after it follow from this date" else ""}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        // Any day, for when a repeating task's next date has to be moved by hand.
        TileRow(listOf(TileItem("mdi:calendar-edit", "Pick a date", picked != null) { picking = true }), tone, tone.onContainer, perRow = 1)
        if (picking) DatePickDialog(shown, { picked = it; picking = false }, { picking = false })
        Text("Repeat", style = MaterialTheme.typography.labelLarge)
        OptionRow(repeatOptions, repeat, tone) { repeat = it }
        if (repeating || due != "No date" || picked != null) {
            TimeTile(if (repeating) "Time" else "Reminder time", cleaned ?: "09:00", tone) { time = it }
        }
        if (repeating) {
            Text("Remind", style = MaterialTheme.typography.labelLarge)
            OptionRow(whoOptions, who, tone) { who = it }
        }
        // How long before its time the house reminds, on this phone (only for a task with a due time).
        if (canRemind && (repeating || due != "No date" || picked != null)) {
            Text("Remind me", style = MaterialTheme.typography.labelLarge)
            OptionRow(LEAD_CHOICES.map { it.first }, leadLabel(leadChoice), tone) { picked2 -> leadChoice = LEAD_CHOICES.first { it.first == picked2 }.second }
            Text("Overdue tasks are reminded when they pass their time, then each morning.", style = MaterialTheme.typography.bodySmall)
        }
        WashField(notes, { notes = it }, "Notes", tone, singleLine = false, minLines = 3)
        TileRow(
            listOf(
                TileItem("mdi:close", "Cancel", false) { onClose() },
                TileItem("mdi:check", "Save", true) { save() },
            ),
            tone, tone.onContainer,
        )
        TileRow(
            listOf(TileItem("mdi:delete-outline", if (sure) "Tap again to delete" else "Delete", sure) { if (sure) onDelete() else sure = true }),
            toneColors(Tone.Red), tone.onContainer,
        )
    }
}
