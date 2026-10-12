package com.churchdrive.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.churchdrive.app.ha.CallService
import com.churchdrive.app.ha.data
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** Edit a task: its name, day, repeat, who is reminded and notes; or delete it. */
@Composable
fun TaskDialog(item: TodoItem, listId: String, first: String?, tone: ToneColors, call: CallService, onClose: () -> Unit) {
    val today = remember { LocalDate.now() }
    val words = remember(item) { parseTaskWords(item.description) }
    val keepDay = remember(item) { item.due?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } }
    var name by remember { mutableStateOf(item.summary) }
    var due by remember { mutableStateOf(dueChoiceOf(item.due, today)) }
    var repeat by remember { mutableStateOf(repeatChoiceOf(words.repeat)) }
    var who by remember { mutableStateOf(whoChoiceOf(words.who, first)) }
    var time by remember { mutableStateOf(timeOf(words.repeat) ?: timeOf(item.due?.takeIf { 'T' in it }?.substringAfter('T')) ?: "09:00") }
    val split = remember(item) { splitSteps(words.notes) }
    var notes by remember { mutableStateOf(split.text) }
    var steps by remember { mutableStateOf(split.steps) }
    // A reminder at a place instead of a time: the places come from the house, and a house without them shows nothing here.
    val api = LocalHaApi.current
    var places by remember { mutableStateOf<List<Place>?>(null) }
    var place by remember { mutableStateOf(NO_PLACE) }
    var placeWas by remember { mutableStateOf(NO_PLACE) }
    LaunchedEffect(item.uid) {
        val found = api?.ask("church_drive/reminders", JSONObject())?.let(::parsePlaceReminders) ?: return@LaunchedEffect
        places = found.places
        placeWas = placeChoiceOf(found.places, found.zoneByTask[reminderKey(listId, item.uid)])
        place = placeWas
    }

    val dueOptions = DUE_CHOICES + if (due == "Keep") listOf("Keep") else emptyList()
    val repeatOptions = REPEAT_CHOICES + if (repeat !in REPEAT_CHOICES) listOf(repeat) else emptyList()
    val whoOptions = WHO_CHOICES + if (who !in WHO_CHOICES) listOf(who) else emptyList()
    val repeating = repeat != "None"
    val cleaned = cleanTime(time)

    val save = {
        val day = dueDateOf(due, today) ?: keepDay ?: today
        val timeOrNull = if (repeating || due != "No date") cleaned else null
        val words2 = repeatWords(repeat, cleaned ?: "09:00", day)
        val fields = updateFields(
            item.uid, name, due, if (due == "No date") null else timeOrNull, today,
            buildDescription(words2, whoWords(who, first), joinSteps(notes, steps)),
        )
        call("todo", "update_item", listId, data(*fields.map { it.key to (it.value ?: JSONObject.NULL) }.toTypedArray()))
        if (place != placeWas && first != null) {
            api?.request(
                "church_drive/reminders/set",
                JSONObject().put("list", listId).put("uid", item.uid).put("zone", places?.let { zoneOfChoice(it, place) } ?: JSONObject.NULL)
                    .put("who", JSONArray().put(first)),
            ) { }
        }
        onClose()
    }
    var sure by remember { mutableStateOf(false) }
    val delete = {
        call("todo", "remove_item", listId, data("item" to item.uid))
        if (placeWas != NO_PLACE) api?.request("church_drive/reminders/set", JSONObject().put("list", listId).put("uid", item.uid).put("zone", JSONObject.NULL).put("who", JSONArray())) { }
        onClose()
    }

    FullPage("Edit task", onClose) {
        OutlinedTextField(name, { name = it }, label = { Text("Task") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Text("Day", style = MaterialTheme.typography.labelLarge)
        OptionRow(dueOptions, due, tone) { due = it }
        Text("Repeat", style = MaterialTheme.typography.labelLarge)
        OptionRow(repeatOptions, repeat, tone) { repeat = it }
        if (repeating || due != "No date") {
            OutlinedTextField(
                time, { time = it }, label = { Text("Time (24 hour, like 09:00)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                isError = cleaned == null && time.isNotBlank(),
            )
        }
        if (repeating) {
            Text("Remind", style = MaterialTheme.typography.labelLarge)
            OptionRow(whoOptions, who, tone) { who = it }
        }
        places?.takeIf { it.isNotEmpty() && first != null }?.let { all ->
            Text("Remind me at a place", style = MaterialTheme.typography.labelLarge)
            OptionRow(placeChoices(all), place, tone) { place = it }
            if (place != NO_PLACE) Text("Reminds you each time you arrive, until it's done.", style = MaterialTheme.typography.bodySmall)
        }
        OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
        Text("Steps", style = MaterialTheme.typography.labelLarge)
        steps.forEachIndexed { i, step ->
            OutlinedTextField(
                step.text,
                { v -> steps = steps.mapIndexed { j, s -> if (j == i) s.copy(text = v) else s } },
                label = { Text("Step ${i + 1}") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    Box(modifier = Modifier.clickable { steps = steps.filterIndexed { j, _ -> j != i } }.padding(8.dp)) {
                        HaIcon("mdi:close", Icons.Filled.Info, tone.onContainer, 22.dp)
                    }
                },
            )
        }
        TileRow(listOf(TileItem("mdi:plus", "Add a step", false) { steps = steps + TaskStep("", false) }), tone, tone.onContainer)
        TileRow(
            listOf(
                TileItem("mdi:close", "Cancel", false) { onClose() },
                TileItem("mdi:check", "Save", true) { if (name.isNotBlank() && (!repeating || cleaned != null)) save() },
            ),
            tone, tone.onContainer,
        )
        TileRow(
            listOf(TileItem("mdi:delete-outline", if (sure) "Tap again to delete" else "Delete", sure) { if (sure) delete() else sure = true }),
            toneColors(Tone.Red), tone.onContainer,
        )
    }
}
