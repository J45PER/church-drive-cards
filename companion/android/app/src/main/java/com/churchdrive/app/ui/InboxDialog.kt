package com.churchdrive.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.json.JSONObject

private sealed interface InboxState {
    object Reading : InboxState
    object Empty : InboxState
    object Refused : InboxState
    data class Added(val lines: List<String>) : InboxState
    data class Failed(val code: String? = null) : InboxState
    data class Check(val batch: InboxBatch, val lists: List<InboxList>) : InboxState
}

/**
 * Tasks the house found in some text, to check before they go on the lists: tick the ones to keep, change the wording,
 * and pick the list each belongs on. With [text] it sends that text to be read first (the share sheet and the highlight
 * menu); without it, it shows whatever is waiting from earlier (a notification, or a voice request). [onClose] is called
 * when the person is done.
 */
@Composable
fun InboxDialog(text: String?, source: String, autoAdd: Boolean = false, onClose: () -> Unit) {
    val api = LocalHaApi.current
    var state by remember { mutableStateOf<InboxState>(InboxState.Reading) }
    LaunchedEffect(text) {
        val ha = api
        if (ha == null) {
            state = InboxState.Failed()
            return@LaunchedEffect
        }
        var code: String? = null
        // Wait for the line to the house to be signed in before asking (up to about fifteen seconds; opening the app from another one
        // can beat it), then ask. A command that fails on a live line was refused: its code says why.
        suspend fun ask(type: String, params: JSONObject): Any? {
            repeat(3) {
                var waited = 0
                while (!ha.connected && waited < 15_000) {
                    kotlinx.coroutines.delay(500)
                    waited += 500
                }
                if (!ha.connected) return null
                ha.ask(type, params)?.let { return it }
                code = ha.lastErrorCode
                if (ha.connected && code != null) return null
                // The line dropped while asking: wait for it and ask again.
            }
            return null
        }
        val lists = parseInboxLists(ask("church_drive/inbox", JSONObject()))
        if (code != null) {
            state = if (inboxProblem(code, ha.connected) == InboxProblem.OutOfDate) InboxState.Refused else InboxState.Failed(code)
            return@LaunchedEffect
        }
        if (text != null) {
            val reply = ask("church_drive/inbox/submit", JSONObject().put("text", text).put("source", source).put("auto_add", autoAdd))
            val batch = parseSubmitted(reply, source)
            val added = if (autoAdd) parseAdded(reply, lists) else null
            state = when {
                added != null && added.isNotEmpty() -> InboxState.Added(added)
                added != null -> InboxState.Empty
                reply == null -> if (inboxProblem(code, ha.connected) == InboxProblem.OutOfDate) InboxState.Refused else InboxState.Failed(code)
                batch == null -> InboxState.Empty
                else -> InboxState.Check(batch, lists)
            }
        } else {
            // Nothing was sent: show what is waiting from earlier, and say nothing when there is none.
            val batch = parseInbox(ask("church_drive/inbox", JSONObject())).firstOrNull()
            if (batch == null) onClose() else state = InboxState.Check(batch, lists)
        }
    }

    // A quick answer shows no box at all; one that takes a moment shows a small one.
    var waited by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(800)
        waited = true
    }

    when (val s = state) {
        InboxState.Reading -> if (waited) AlertDialog(
            onDismissRequest = onClose,
            title = { Text(if (autoAdd) "Adding…" else "Reading…") },
            confirmButton = { TextButton(onClick = onClose) { Text("Cancel") } },
        )
        InboxState.Refused -> AlertDialog(
            onDismissRequest = onClose,
            title = { Text("The house can't do that yet") },
            text = { Text("Church Drive in Home Assistant needs updating to 0.39.0 or later for tasks from text and voice. Nothing was added.") },
            confirmButton = { TextButton(onClick = onClose) { Text("OK") } },
        )
        InboxState.Empty -> AlertDialog(
            onDismissRequest = onClose,
            title = { Text("No tasks found") },
            text = { Text("There was nothing in that text to do.") },
            confirmButton = { TextButton(onClick = onClose) { Text("OK") } },
        )
        is InboxState.Added -> {
            // Said by voice and already on the lists: show where each went, and go away by itself.
            LaunchedEffect(s) {
                kotlinx.coroutines.delay(4_000)
                onClose()
            }
            AlertDialog(
                onDismissRequest = onClose,
                title = { Text(if (s.lines.size == 1) "Task added" else "${s.lines.size} tasks added") },
                text = { Text(s.lines.joinToString("\n")) },
                confirmButton = { TextButton(onClick = onClose) { Text("OK") } },
            )
        }
        is InboxState.Failed -> AlertDialog(
            onDismissRequest = onClose,
            title = { Text(if (s.code != null) "The house couldn't do that" else "Couldn't reach the house") },
            text = { Text(if (s.code != null) "Nothing was added. Home Assistant said: ${s.code}. Try again, and tell Jamie if it keeps happening." else "Nothing was added. Check the connection and try again.") },
            confirmButton = { TextButton(onClick = onClose) { Text("OK") } },
        )
        is InboxState.Check -> CheckTasks(s.batch, s.lists, onClose)
    }
}

@Composable
private fun CheckTasks(batch: InboxBatch, lists: List<InboxList>, onClose: () -> Unit) {
    val api = LocalHaApi.current
    val tone = toneColors(Tone.Teal)
    val tasks = remember(batch.id) { mutableStateListOf<InboxTask>().apply { addAll(batch.tasks) } }
    var saving by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val chosen = tasks.count { it.keep && it.summary.isNotBlank() }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (batch.tasks.size == 1) "Add this task?" else "Add these ${batch.tasks.size} tasks?") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (failed) Text("That didn't save. Try again.", color = MaterialTheme.colorScheme.error)
                tasks.forEachIndexed { i, t ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(t.keep, { tasks[i] = t.copy(keep = it) })
                            OutlinedTextField(
                                t.summary, { tasks[i] = t.copy(summary = it) },
                                modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = t.keep,
                            )
                        }
                        if (t.due.isNotEmpty()) Text("Due ${t.due.replace('T', ' ')}", style = MaterialTheme.typography.labelMedium)
                        if (t.keep && lists.isNotEmpty()) {
                            val names = lists.map { listChipName(it.name) }
                            OptionRow(names, lists.firstOrNull { it.id == t.list }?.let { listChipName(it.name) }, tone) { picked ->
                                lists.firstOrNull { listChipName(it.name) == picked }?.let { tasks[i] = t.copy(list = it.id) }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = chosen > 0 && !saving,
                onClick = {
                    val ha = api ?: return@TextButton
                    saving = true
                    ha.request(
                        "church_drive/inbox/confirm",
                        JSONObject().put("batch", batch.id).put("tasks", confirmTasks(tasks.toList())),
                    ) { reply ->
                        if (reply == null) { saving = false; failed = true } else onClose()
                    }
                },
            ) { Text(if (chosen == 1) "Add 1 task" else "Add $chosen tasks") }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    api?.request("church_drive/inbox/dismiss", JSONObject().put("batch", batch.id)) { }
                    onClose()
                },
            ) { Text("Discard") }
        },
    )
}
