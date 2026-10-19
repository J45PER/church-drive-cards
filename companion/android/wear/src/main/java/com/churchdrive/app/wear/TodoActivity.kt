package com.churchdrive.app.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.Text
import com.churchdrive.app.Session
import com.churchdrive.app.ha.data
import com.churchdrive.app.ui.TodoItem
import com.churchdrive.app.ui.ask
import com.churchdrive.app.ui.myTodoList
import com.churchdrive.app.ui.parseTodoItems
import com.churchdrive.app.ui.taskLine
import kotlinx.coroutines.delay

/** The to-do mini app: the lists, the open tasks in each (tap to tick one off), and adding a task by voice. */
class TodoActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Signed { connected -> Todo(connected) } }
    }
}

// The same lists as the phone's to-do widget (TodoLists in widget/WidgetModel2.kt): the person's own, the shared one and the cleaning jobs.
private fun lists(person: String?): List<Pair<String, String>> =
    listOfNotNull(myTodoList(person)?.let { "My to-do" to it }, "Shared" to "todo.priorities_everyone", "Cleaning" to "todo.cleaning")

@Composable
private fun Todo(connected: Boolean) {
    val context = LocalContext.current
    val all = remember { lists(Session(context).personName) }
    var open by remember { mutableStateOf<Pair<String, String>?>(null) }
    BackHandler(enabled = open != null) { open = null }

    val current = open
    if (current == null) {
        ScalingLazyColumn(modifier = Modifier.fillMaxWidth()) {
            item { ListHeader { Text("To-Do") } }
            for (list in all) row(list.first) { open = list }
        }
        return
    }

    val (name, listId) = current
    var items by remember(listId) { mutableStateOf<List<TodoItem>?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(connected, listId, reload) {
        if (!connected) return@LaunchedEffect
        val result = WatchHa.api.ask("todo/item/list", data("entity_id" to listId))
        items = if (result == null) items else parseTodoItems(result).filter { !it.done }
    }
    val add = rememberListener("What needs doing?") { said ->
        WatchHa.client.callService("todo", "add_item", listId, data("item" to said))
        toast(context, "Added")
        reload++
    }

    ScalingLazyColumn(modifier = Modifier.fillMaxWidth()) {
        item { ListHeader { Text(name) } }
        row("Add a task") { add() }
        val shown = items
        when {
            shown == null -> item { Text(if (connected) "Loading…" else "Connecting…", textAlign = TextAlign.Center) }
            shown.isEmpty() -> item { Text("Nothing to do", textAlign = TextAlign.Center) }
            else -> for (task in shown) row(task.summary, taskLine(task)) {
                if (!connected) {
                    toast(context, "Can't reach the house")
                } else {
                    WatchHa.client.callService("todo", "update_item", listId, data("item" to task.uid, "status" to "completed"))
                    toast(context, "Done: ${task.summary}")
                    // Gone from the list straight away; the house's list is read again a moment later.
                    items = shown.filter { it.uid != task.uid }
                    reload++
                }
            }
        }
    }
}
