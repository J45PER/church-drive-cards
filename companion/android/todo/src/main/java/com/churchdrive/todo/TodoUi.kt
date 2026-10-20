package com.churchdrive.todo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HomeWork
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Badge
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.churchdrive.app.ui.HouseTask
import com.churchdrive.app.ui.TodoItem
import com.churchdrive.app.ui.ToneColors
import com.churchdrive.app.ui.houseTask
import com.churchdrive.app.ui.isStepsTask
import com.churchdrive.app.ui.shoppingIdOf
import com.churchdrive.app.ui.taskLine
import com.churchdrive.app.ui.toneColors

private fun TodoTab.icon(): ImageVector = when (this) {
    TodoTab.Mine -> Icons.Filled.Person
    TodoTab.Shared -> Icons.Filled.Groups
    TodoTab.Cleaning -> Icons.Filled.CleaningServices
    TodoTab.House -> Icons.Filled.HomeWork
}

/**
 * The signed-in app, laid out like the main Church Drive app: a collapsing title with the person's initial, a card for the
 * page's tasks, and the pages along the bottom (the person's own list, the shared one, cleaning and the house's jobs).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoApp(vm: TodoModel) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    // The account panel (null shut, "account", "settings" or "about"), which slides in from the right.
    var panel by rememberSaveable { mutableStateOf<String?>(null) }
    val tab = vm.tab
    Box(Modifier.fillMaxSize()) {
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            MediumTopAppBar(
                title = { Text(tab.title) },
                actions = {
                    // A round button with the person's initial opens the account panel, as in the main app.
                    Box(
                        Modifier.padding(horizontal = 6.dp).size(44.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .clickable(onClickLabel = "Account and settings") { panel = "account" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            vm.personName?.trim()?.firstOrNull()?.uppercase() ?: "?",
                            style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                },
                scrollBehavior = scroll,
            )
        },
        bottomBar = {
            NavigationBar {
                TodoTab.entries.forEach { t ->
                    val open = vm.openCount(t)
                    NavigationBarItem(
                        selected = t == tab,
                        onClick = { vm.openTab(t) },
                        icon = {
                            BadgedBox(badge = { if (open > 0) Badge { Text(open.toString()) } }) { Icon(t.icon(), contentDescription = null) }
                        },
                        label = { Text(t.label, fontSize = 11.sp, maxLines = 1) },
                    )
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (vm.updateAvailable) UpdateBanner { panel = "about" }
            if (!vm.connected) Text("Connecting to the house…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TaskCard(vm, tab)
            Spacer(Modifier.height(16.dp))
        }
    }
    TodoPanel(panel, { panel = it }, vm)
    }
}

/** A newer build is ready: taps through to About, where it can be downloaded. */
@Composable
private fun UpdateBanner(onOpen: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
    ) { Text("A newer version of Church Drive To-Do is ready. Tap to get it.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(12.dp)) }
}

/** A page's card, in the page's colour like the main app's task cards. */
@Composable
private fun TaskCard(vm: TodoModel, tab: TodoTab) {
    val tone = toneColors(tab.tone)
    val items = vm.items(tab)
    var text by rememberSaveable(tab) { mutableStateOf("") }
    var showDone by rememberSaveable(tab) { mutableStateOf(vm.showDone) }
    var editing by remember(tab) { mutableStateOf<TodoItem?>(null) }
    editing?.let { item ->
        TodoEditDialog(
            item, vm.personName?.trim()?.substringBefore(' '),
            onSave = { vm.save(tab, it); editing = null },
            onDelete = { vm.remove(tab, item); editing = null },
            onClose = { editing = null },
        )
    }
    Surface(shape = RoundedCornerShape(28.dp), color = tone.container, contentColor = tone.onContainer, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (vm.listId(tab) == null) {
                Text("Your list isn't ready yet.", style = MaterialTheme.typography.bodyMedium)
                return@Column
            }
            val open = items.orEmpty().filter { !it.done }.sortedWith(compareBy<TodoItem>({ it.due == null }, { it.due }))
            when {
                items == null -> Text(vm.problem ?: "Loading…", style = MaterialTheme.typography.bodyMedium)
                open.isEmpty() -> Text(if (tab == TodoTab.House) "Nothing from the house right now." else "All done", style = MaterialTheme.typography.titleMedium)
            }
            open.forEach { item ->
                if (tab == TodoTab.House) HouseRow(item, houseTask(item), tone) else TaskRow(item, tone, onEdit = { editing = item }) { vm.setDone(tab, item, true) }
            }
            // Finished tasks, kept out of the way until asked for; tick one to bring it back.
            val finished = items.orEmpty().filter { it.done }
            if (finished.isNotEmpty() && tab != TodoTab.House) {
                Row(Modifier.fillMaxWidth().clickable { showDone = !showDone }, verticalAlignment = Alignment.CenterVertically) {
                    Text("Done (${finished.size})", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Icon(if (showDone) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
                }
                if (showDone) finished.forEach { item ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Checkbox(checked = true, onCheckedChange = { vm.setDone(tab, item, false) }, colors = checks(tone))
                        Text(
                            item.summary, style = MaterialTheme.typography.bodyLarge, textDecoration = TextDecoration.LineThrough,
                            color = tone.onContainer.copy(alpha = 0.6f), modifier = Modifier.weight(1f).clickable { editing = item },
                        )
                    }
                }
            }
            if (tab.canAdd) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text, onValueChange = { text = it }, placeholder = { Text("Add a task") }, singleLine = true,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { vm.add(tab, text); text = "" }),
                )
                FilledIconButton(
                    onClick = { vm.add(tab, text); text = "" },
                    enabled = text.isNotBlank(),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = tone.accent, contentColor = tone.onAccent),
                ) { Icon(Icons.Filled.Add, contentDescription = "Add") }
            }
        }
    }
}

@Composable
private fun checks(tone: ToneColors) = CheckboxDefaults.colors(checkedColor = tone.accent, uncheckedColor = tone.accent)

@Composable
private fun TaskRow(item: TodoItem, tone: ToneColors, onEdit: () -> Unit, onTick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        val list = shoppingIdOf(item.description)
        if (list.isNotEmpty()) {
            // A shopping or steps list has no tick of its own: it is finished by finishing its items, in the main Church Drive app.
            Box(Modifier.padding(12.dp)) {
                Icon(if (isStepsTask(item.description)) Icons.Filled.Checklist else Icons.Filled.ShoppingCart, contentDescription = null, tint = tone.accent)
            }
        } else {
            Checkbox(checked = false, onCheckedChange = { onTick() }, colors = checks(tone))
        }
        // Tap a task to change it (its day, repeat, next date, who is reminded, notes); the box on its left ticks it.
        Column(Modifier.weight(1f).clickable(enabled = list.isEmpty()) { onEdit() }) {
            Text(item.summary, style = MaterialTheme.typography.bodyLarge)
            taskLine(item).takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = tone.onContainer.copy(alpha = 0.75f))
            }
        }
    }
}

/** A job the house spotted: it clears itself once sorted, so it has no tick. */
@Composable
private fun HouseRow(item: TodoItem, task: HouseTask, tone: ToneColors) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(vertical = 4.dp)) {
        Icon(Icons.Filled.HomeWork, contentDescription = null, tint = tone.accent)
        Column(Modifier.weight(1f)) {
            Text(item.summary, style = MaterialTheme.typography.bodyLarge)
            val sub = listOf(task.kind, task.detail).filter { it.isNotBlank() }.joinToString(" · ")
            if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, color = tone.onContainer.copy(alpha = 0.75f))
        }
    }
}
