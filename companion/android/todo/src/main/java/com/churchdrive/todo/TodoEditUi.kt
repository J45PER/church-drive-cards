package com.churchdrive.todo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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

/**
 * Edit a task: its name, day, repeat, who is reminded and notes, or delete it. For a repeating task "Next due" can be set to
 * any day, for when it has to be moved by hand; the repeat carries on from that day. It writes the same words as the dashboard
 * and the main app, so all three read each other's tasks.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TodoEditDialog(item: TodoItem, first: String?, onSave: (Map<String, Any?>) -> Unit, onDelete: () -> Unit, onClose: () -> Unit) {
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
        )
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Edit task", style = MaterialTheme.typography.headlineSmall)
                OutlinedTextField(name, { name = it }, label = { Text("Task") }, singleLine = true, modifier = Modifier.fillMaxWidth())

                Text(if (repeating) "Next due" else "Day", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    dueOptions.forEach { o -> FilterChip(selected = picked == null && due == o, onClick = { due = o; picked = null }, label = { Text(o) }) }
                    // Any day, for when a repeating task's next date has to be moved by hand.
                    AssistChip(
                        onClick = { picking = true },
                        label = { Text("Pick a date") },
                        leadingIcon = { Icon(Icons.Filled.CalendarMonth, contentDescription = null) },
                    )
                }
                if (shown != null) {
                    Text(
                        "${dateWords(shown, today)}${if (repeating) ": the ones after it follow from this date" else ""}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (picking) DatePickDialog(shown, { picked = it; picking = false }, { picking = false })

                Text("Repeat", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeatOptions.forEach { o -> FilterChip(selected = repeat == o, onClick = { repeat = o }, label = { Text(o) }) }
                }
                if (repeating || due != "No date" || picked != null) {
                    OutlinedTextField(
                        time, { time = it }, label = { Text("Time (24 hour, like 09:00)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        isError = cleaned == null && time.isNotBlank(),
                    )
                }
                if (repeating) {
                    Text("Remind", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        whoOptions.forEach { o -> FilterChip(selected = who == o, onClick = { who = o }, label = { Text(o) }) }
                    }
                }
                OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth(), minLines = 3)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onClose, modifier = Modifier.weight(1f)) { Text("Cancel") }
                    Button(onClick = { save() }, enabled = name.isNotBlank() && (!repeating || cleaned != null), modifier = Modifier.weight(1f)) { Text("Save") }
                }
                TextButton(
                    onClick = { if (sure) onDelete() else sure = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (sure) "Tap again to delete" else "Delete") }
                Spacer(Modifier.height(48.dp))
            }
        }
    }
}
