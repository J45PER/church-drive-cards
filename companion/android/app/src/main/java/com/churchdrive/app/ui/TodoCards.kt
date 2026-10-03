package com.churchdrive.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.churchdrive.app.ha.CallService
import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ha.data
import org.json.JSONObject

/** A to-do list's items, kept up to date: fetched again whenever the list's count changes. Null until they arrive. */
@Composable
private fun rememberTodoItems(listId: String, entities: Map<String, EntityState>): List<TodoItem>? {
    val api = LocalHaApi.current
    var items by remember(listId) { mutableStateOf<List<TodoItem>?>(null) }
    val count = entities[listId]?.state
    LaunchedEffect(listId, count) {
        if (api != null && listId.isNotBlank()) {
            // A failed or slow answer is asked for again (a few times); it is not taken for an empty list.
            var tries = 0
            while (tries < 5) {
                val result = api.ask("todo/item/list", data("entity_id" to listId))
                if (result != null) {
                    items = parseTodoItems(result)
                    break
                }
                tries++
                kotlinx.coroutines.delay(2_000L * tries)
            }
        }
    }
    return items
}

/**
 * The dashboard's `custom:task-list-card`: a to-do list to tick off and add to. `entity: mine` is the signed-in
 * person's own list. (Repeats and reminders are set on the dashboard; they show here as the task's note.)
 */
@Composable
fun TaskListCard(config: JSONObject, entities: Map<String, EntityState>, call: CallService) {
    val me = LocalUserName.current
    val configured = config.optString("entity")
    val listId = if (configured == "mine") myTodoList(me).orEmpty() else configured
    val tone = toneColors(toneFromColour(config.optString("color")) ?: Tone.Purple)
    val showIcons = config.optBoolean("icons", false)
    val items = rememberTodoItems(listId, entities)
    var ticked by remember(listId) { mutableStateOf(setOf<String>()) }
    var text by remember(listId) { mutableStateOf("") }
    val now = java.time.LocalDateTime.now()
    var editing by remember { mutableStateOf<TodoItem?>(null) }
    var showDone by remember(listId) { mutableStateOf(false) }
    editing?.let { TaskDialog(it, listId, me?.trim()?.substringBefore(' '), tone, call) { editing = null } }

    EntityCard(tone.container, tone.onContainer) {
        config.optString("title").takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
        if (listId.isBlank()) {
            Text("Your list isn't ready yet.", style = MaterialTheme.typography.bodyMedium)
            return@EntityCard
        }
        val open = items.orEmpty().filter { !it.done && it.uid !in ticked }.sortedWith(compareBy<TodoItem>({ it.due == null }, { it.due }))
        when {
            items == null -> Text("Loading…", style = MaterialTheme.typography.bodyMedium)
            open.isEmpty() -> Text("All done", style = MaterialTheme.typography.titleMedium)
        }
        open.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Checkbox(
                    checked = false,
                    onCheckedChange = {
                        ticked = ticked + item.uid
                        call("todo", "update_item", listId, data("item" to item.uid, "status" to "completed"))
                    },
                    colors = CheckboxDefaults.colors(checkedColor = tone.accent, uncheckedColor = tone.accent),
                )
                if (showIcons) HaIcon(choreIcon(item.summary), Icons.Filled.Info, tone.accent, 22.dp)
                Column(
                    modifier = Modifier.weight(1f).padding(start = if (showIcons) 8.dp else 0.dp).clickable { editing = item },
                ) {
                    Text(item.summary, style = MaterialTheme.typography.bodyLarge)
                    taskLine(item, now).takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = tone.onContainer.copy(alpha = 0.75f))
                    }
                }
            }
        }
        // Finished tasks, kept out of the way until asked for; tap one to edit or delete it, tick it to bring it back.
        val finished = items.orEmpty().filter { it.done }
        if (finished.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { showDone = !showDone },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Done (${finished.size})", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                HaIcon(if (showDone) "mdi:chevron-up" else "mdi:chevron-down", Icons.Filled.Info, tone.onContainer, 22.dp)
            }
            if (showDone) finished.forEach { item ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Checkbox(
                        checked = true,
                        onCheckedChange = { call("todo", "update_item", listId, data("item" to item.uid, "status" to "needs_action")) },
                        colors = CheckboxDefaults.colors(checkedColor = tone.accent, uncheckedColor = tone.accent),
                    )
                    Text(
                        item.summary,
                        style = MaterialTheme.typography.bodyLarge,
                        textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough,
                        color = tone.onContainer.copy(alpha = 0.6f),
                        modifier = Modifier.weight(1f).clickable { editing = item },
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Add a task") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            FilledIconButton(
                onClick = {
                    if (text.isNotBlank()) {
                        call("todo", "add_item", listId, data("item" to text.trim()))
                        text = ""
                    }
                },
                enabled = text.isNotBlank(),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = tone.accent, contentColor = tone.onAccent),
            ) { HaIcon("mdi:plus", Icons.Filled.Info, tone.onAccent, 22.dp) }
        }
    }
}

/**
 * The dashboard's `custom:house-tasks-card`: the jobs the house has spotted (a low battery, a filter due), which clear
 * themselves once sorted. It shows the signed-in person's and everyone's.
 */
@Composable
fun HouseTasksCard(config: JSONObject, entities: Map<String, EntityState>) {
    val me = LocalUserName.current
    val first = me?.trim()?.substringBefore(' ')
    val listId = config.optString("entity").ifBlank { "todo.priorities_automatic" }
    val showAll = config.optString("show") == "all"
    val tone = toneColors(Tone.Purple)
    val items = rememberTodoItems(listId, entities)
    EntityCard(tone.container, tone.onContainer) {
        val tasks = items.orEmpty().filter { !it.done }.map { it to houseTask(it) }.filter { showAll || houseTaskFor(it.second, first) }
        when {
            items == null -> Text("Loading…", style = MaterialTheme.typography.bodyMedium)
            tasks.isEmpty() -> Text("Nothing from the house right now.", style = MaterialTheme.typography.bodyMedium)
        }
        tasks.forEach { (item, task) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ToneIconName(task.icon, Icons.Filled.Info, tone)
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.summary, style = MaterialTheme.typography.bodyLarge)
                    val sub = listOf(task.kind, task.detail, if (showAll) task.who else "").filter { it.isNotBlank() }.joinToString(" · ")
                    if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, color = tone.onContainer.copy(alpha = 0.75f))
                }
            }
        }
    }
}

/** The To-do page's header: how many things are waiting across the lists. */
@Composable
fun TodoSummaryHeader(entities: Map<String, EntityState>) {
    val me = LocalUserName.current
    val lists = listOfNotNull(myTodoList(me), "todo.priorities_everyone", "todo.cleaning", "todo.priorities_automatic")
    val count = lists.sumOf { entities[it]?.state?.toIntOrNull() ?: 0 }
    val tone = toneColors(if (count == 0) Tone.Green else Tone.Purple)
    EntityCard(tone.container, tone.onContainer) {
        Text(if (count == 0) "All done" else "$count to do", style = MaterialTheme.typography.headlineMedium)
        Text(
            if (count == 0) "Nothing waiting on you." else "Across your list, the shared list, cleaning and the house.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
