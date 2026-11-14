package com.churchdrive.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
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

/**
 * The dashboard's `custom:task-list-card`: a to-do list to tick off and add to. `entity: mine` is the signed-in
 * person's own list. (Repeats and reminders are set on the dashboard; they show here as the task's note.)
 */
@Composable
fun TaskListCard(config: JSONObject, entities: Map<String, EntityState>, call: CallService) {
    val me = LocalUserName.current
    val configured = config.optString("entity")
    val listId = if (configured == "mine") myTodoList(me).orEmpty() else configured
    val tone = toneColors(toneFromColour(config.optString("color")) ?: Tone.Tasks)
    val showIcons = config.optBoolean("icons", false)
    val items = rememberTodoItems(listId, entities)
    var ticked by remember(listId) { mutableStateOf(setOf<String>()) }
    var text by remember(listId) { mutableStateOf("") }
    val now = java.time.LocalDateTime.now()
    var editing by remember { mutableStateOf<TodoItem?>(null) }
    var shopping by remember { mutableStateOf<String?>(null) }
    shopping?.let { ShoppingScreen(it) { shopping = null } }
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
                if (shoppingIdOf(item.description).isNotEmpty()) {
                    // A shopping list has no tick of its own: it is finished by finishing its items.
                    Box(modifier = Modifier.padding(12.dp)) { HaIcon(if (isStepsTask(item.description)) "mdi:format-list-checks" else "mdi:cart-outline", CdVectors.Info, toneColors(if (isStepsTask(item.description)) Tone.Teal else Tone.Indigo).accent, 24.dp) }
                } else Checkbox(
                    checked = false,
                    onCheckedChange = {
                        ticked = ticked + item.uid
                        call("todo", "update_item", listId, data("item" to item.uid, "status" to "completed"))
                    },
                    colors = CheckboxDefaults.colors(checkedColor = tone.accent, uncheckedColor = tone.accent),
                )
                if (showIcons) HaIcon(choreIcon(item.summary), CdVectors.Info, tone.accent, 22.dp)
                Column(
                    modifier = Modifier.weight(1f).padding(start = if (showIcons) 8.dp else 0.dp).clickable {
                        // A shopping list's task opens the list page, not the task form.
                        val list = shoppingIdOf(item.description)
                        if (list.isNotEmpty()) shopping = list else editing = item
                    },
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
                HaIcon(if (showDone) "mdi:chevron-up" else "mdi:chevron-down", CdVectors.Info, tone.onContainer, 22.dp)
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
            ) { HaIcon("mdi:plus", CdVectors.Info, tone.onAccent, 22.dp) }
        }
        // A steps list is a checklist that shows in this list as one task; it is made here and worked through on its own page.
        TileRow(listOf(TileItem("mdi:format-list-checks", "New steps list", false) { shopping = NEW_STEPS }), tone, tone.onContainer)
    }
}

/** The To-do page's header: how many things are waiting across the lists. */
@Composable
fun TodoSummaryHeader(entities: Map<String, EntityState>) {
    val me = LocalUserName.current
    val lists = listOfNotNull(myTodoList(me), "todo.priorities_everyone", "todo.cleaning", "todo.priorities_automatic")
    val count = lists.sumOf { entities[it]?.state?.toIntOrNull() ?: 0 }
    val tone = toneColors(if (count == 0) Tone.Green else Tone.Tasks)
    EntityCard(tone.container, tone.onContainer) {
        Text(if (count == 0) "All done" else "$count to do", style = MaterialTheme.typography.headlineMedium)
        Text(
            if (count == 0) "Nothing waiting on you." else "Across your list, the shared list, cleaning and the house.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
