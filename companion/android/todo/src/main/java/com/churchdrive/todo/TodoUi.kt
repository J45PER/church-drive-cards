package com.churchdrive.todo

import com.churchdrive.app.ui.sidebarBottomInset
import com.churchdrive.app.ui.CdVectors

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
import androidx.compose.runtime.collectAsState
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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.churchdrive.app.ui.AssistantBrand
import com.churchdrive.app.ui.AppNavigationBar
import com.churchdrive.app.ui.navBarItemColors
import com.churchdrive.app.ui.acrylicBehind
import com.churchdrive.app.ui.rememberToTop
import com.churchdrive.app.ui.withoutBottom
import dev.chrisbanes.haze.HazeState
import com.churchdrive.app.ui.HouseTask
import com.churchdrive.app.ui.PersonButton
import com.churchdrive.app.ui.TodoItem
import com.churchdrive.app.ui.Tone
import com.churchdrive.app.ui.ToneColors
import com.churchdrive.app.ui.ShoppingList
import com.churchdrive.app.ui.houseTask
import com.churchdrive.app.ui.houseTaskHaPath
import com.churchdrive.app.ui.houseTaskPage
import com.churchdrive.app.ui.isStepsTask
import com.churchdrive.app.ui.shoppingIdOf
import com.churchdrive.app.ui.taskLine
import com.churchdrive.app.ui.glowBehind
import com.churchdrive.app.ui.toneColors

/** A page's icon on the bottom bar: the house's own house for Home, Gregg for Cleaning, the cart, and the dashboard tiles for Projects. */
@Composable
private fun TabIcon(tab: TodoTab) {
    when (tab) {
        TodoTab.Home -> Icon(androidx.compose.ui.res.painterResource(com.churchdrive.core.R.drawable.cd_house), contentDescription = null)
        TodoTab.Shopping -> Icon(CdVectors.Cart, contentDescription = null)
        TodoTab.Cleaning -> com.churchdrive.app.ui.CdIcon("cd:vac", androidx.compose.material3.LocalContentColor.current, 24.dp)
        TodoTab.Projects -> com.churchdrive.app.ui.CdIcon("cd:view-dashboard", androidx.compose.material3.LocalContentColor.current, 24.dp)
    }
}

/** A section's icon, in the section's colour. */
@Composable
private fun SectionIcon(section: TodoSection, tint: androidx.compose.ui.graphics.Color) {
    when (section) {
        TodoSection.Mine -> Icon(CdVectors.Person, contentDescription = null, tint = tint)
        TodoSection.Shared -> Icon(CdVectors.Group, contentDescription = null, tint = tint)
        TodoSection.House -> Icon(androidx.compose.ui.res.painterResource(com.churchdrive.core.R.drawable.cd_house), contentDescription = null, tint = tint)
        TodoSection.Shopping -> Icon(CdVectors.Cart, contentDescription = null, tint = tint)
        TodoSection.Cleaning -> com.churchdrive.app.ui.CdIcon("cd:vac", tint, 24.dp)
    }
}

/**
 * The signed-in app, laid out like the main Church Drive app: a collapsing white title with the person's initial, the to-do
 * highlights, then each page's sections (a heading outside, a card of tasks inside), and the pages along the bottom
 * (Home, Shopping, Cleaning and Projects).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoApp(vm: TodoModel) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    // Pressing a page's button shows it from its top; the page scrolls under the acrylic bar.
    val pageScroll = rememberScrollState()
    val acrylic = remember { HazeState() }
    val toTop = rememberToTop(pageScroll, scroll)
    // The account panel (null shut, "account", "settings" or "about"), which slides in from the right.
    var panel by rememberSaveable { mutableStateOf<String?>(null) }
    // Clive To Do: the small add-and-tick-off assistant.
    var assistantOpen by rememberSaveable { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(Unit) { AssistantBrand.load(context) }
    androidx.compose.runtime.LaunchedEffect(vm.connected) { if (vm.connected) vm.refreshAssistantName(context) }
    val tab = vm.tab
    // The number of tasks due on the app icon, when the person has asked for it.
    val due = vm.dueCount()
    androidx.compose.runtime.LaunchedEffect(due, vm.iconCount) { TaskBadge.update(context, due, vm.iconCount) }
    // Swiping left anywhere that doesn't scroll sideways opens the account panel, as in the main app, as well as its button.
    var swiped by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0f) }
    Box(
        Modifier.fillMaxSize().pointerInput(panel) {
            if (panel == null) {
                detectHorizontalDragGestures(
                    onDragStart = { swiped = 0f },
                    onDragEnd = { if (swiped < -120f) panel = "account" },
                    onHorizontalDrag = { _, dx -> swiped += dx },
                )
            }
        },
    ) {
    val link by vm.link.collectAsState()
    // The pull is outside the Scaffold so the collapsing title bar hears the drag first and can open before a pull starts.
    com.churchdrive.app.ui.LinkRefresh(link, vm::refresh, Modifier.fillMaxSize()) {
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            MediumTopAppBar(
                // White like the main app's page titles (the page's colour is in the bar, the cards and the summary).
                title = { Text(tab.title, color = MaterialTheme.colorScheme.onBackground) },
                actions = {
                    // A round button with the person's initial opens the account panel, as in the main app.
                    PersonButton(vm.personName) { panel = "account" }
                },
                scrollBehavior = scroll,
            )
        },
        floatingActionButton = { if (vm.connected) AssistantButton { assistantOpen = true } },
        bottomBar = {
            AppNavigationBar(acrylic, sidebarOpen = panel != null, onSidebar = { panel = if (panel == null) "account" else null }) {
                TodoTab.entries.forEach { t ->
                    // No count on the buttons: the summary at the top of every page already says what is waiting.
                    NavigationBarItem(
                        colors = navBarItemColors(),
                        selected = t == tab && panel == null,
                        onClick = { panel = null; vm.openTab(t); toTop() },
                        icon = { TabIcon(t) },
                        label = { Text(t.label, fontSize = 11.sp, maxLines = 1) },
                    )
                }
            }
        },
    ) { padding ->
        // The page scrolls under the acrylic bar, so the bar's height is padding at the foot of the page, not around it.
        Box(Modifier.fillMaxSize().acrylicBehind(acrylic)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(padding.withoutBottom()).verticalScroll(pageScroll).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            com.churchdrive.app.ui.UpdateNotice(BuildConfig.PLAY, vm.updateAvailable, com.churchdrive.app.UpdateCheck.TODO_APK_URL, "Church Drive To-Do")
            TodoSummary(vm) { panel = null; vm.openTab(TodoTab.Home); toTop() }
            when (tab) {
                TodoTab.Home -> {
                    Section(vm, TodoSection.Mine)
                    Section(vm, TodoSection.Shared)
                    StepsSection(vm)
                    Section(vm, TodoSection.House)
                }
                TodoTab.Shopping -> ShoppingPage(vm)
                TodoTab.Cleaning -> Section(vm, TodoSection.Cleaning)
                TodoTab.Projects -> ProjectsPage(vm)
            }
            Spacer(Modifier.height(16.dp))
            Spacer(Modifier.height(padding.calculateBottomPadding()))
        }
        }
    }
    }
    TodoPanel(panel, { panel = it }, vm, acrylic = acrylic, bottomInset = sidebarBottomInset())
    if (assistantOpen) AssistantSheet(vm) { assistantOpen = false }
    }
}

/** The highlights card at the top of every page, as on the main app's Home page: what is waiting for this person, and a tile to the lists. */
@Composable
private fun TodoSummary(vm: TodoModel, onOpen: () -> Unit) {
    val first = vm.personName?.trim()?.substringBefore(' ')
    var ticked by remember { mutableStateOf(setOf<String>()) }
    val all = com.churchdrive.app.ui.todoHighlights(vm.highlightLists(), first).filter { it.uid !in ticked }
    val shown = all.take(3)
    com.churchdrive.app.ui.TodoHighlightsView(shown, all.size - shown.size, vm.highlightsLoaded, onOpen, { onOpen() }) { h ->
        ticked = ticked + h.uid
        vm.tickByUid(h.list, h.uid)
    }
}

/** A heading that sits outside its cards, so a section can hold several cards later. */
@Composable
internal fun SectionHeader(title: String, count: String?, tone: ToneColors, icon: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 6.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        icon()
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
        if (count != null) Text(
            count, style = MaterialTheme.typography.labelMedium, color = tone.accent,
            modifier = Modifier.background(tone.accent.copy(alpha = 0.18f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/** One of a page's lists: its heading, then its card. */
@Composable
private fun Section(vm: TodoModel, section: TodoSection) {
    val tone = toneColors(section.tone)
    val open = vm.items(section)?.count { !it.done }
    // The house's list says nothing when it is empty, instead of an empty card.
    if (section == TodoSection.House && vm.items(section).orEmpty().none { !it.done }) return
    SectionHeader(section.title, open?.let { if (section == TodoSection.House) "$it jobs" else "$it to do" }, tone) { SectionIcon(section, tone.accent) }
    TaskCard(vm, section)
}

/** A page's card of tasks, in the section's colour like the main app's task cards. */
@Composable
private fun TaskCard(vm: TodoModel, section: TodoSection) {
    val tone = toneColors(section.tone)
    val items = vm.items(section)
    val context = androidx.compose.ui.platform.LocalContext.current
    var text by rememberSaveable(section) { mutableStateOf("") }
    var showDone by rememberSaveable(section) { mutableStateOf(vm.showDone) }
    var editing by remember(section) { mutableStateOf<TodoItem?>(null) }
    // A task asked for from the Church Drive app's Home page opens for editing as soon as its list has arrived.
    val pending = vm.pendingUid
    androidx.compose.runtime.LaunchedEffect(pending, items, section) {
        if (pending != null && items != null && section != TodoSection.House) {
            items.firstOrNull { it.uid == pending }?.let {
                editing = it
                vm.clearPending()
            }
        }
    }
    editing?.let { item ->
        TodoEditDialog(
            item, vm.personName?.trim()?.substringBefore(' '), tone, vm.leadOf(section, item), section != TodoSection.House,
            onSave = { fields, lead -> vm.save(section, fields); vm.setLead(section, item, lead); editing = null },
            onDelete = { vm.remove(section, item); editing = null },
            onClose = { editing = null },
        )
    }
    Surface(shape = RoundedCornerShape(28.dp), color = tone.container, contentColor = tone.onContainer, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.glowBehind(tone.container).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (vm.listId(section) == null) {
                Text("Your list isn't ready yet.", style = MaterialTheme.typography.bodyMedium)
                return@Column
            }
            val open = items.orEmpty().filter { !it.done }.sortedWith(compareBy<TodoItem>({ it.due == null }, { it.due }))
            when {
                items == null -> Text(vm.problem ?: "Loading…", style = MaterialTheme.typography.bodyMedium)
                open.isEmpty() -> Text(if (section == TodoSection.House) "Nothing from the house right now." else "All done", style = MaterialTheme.typography.titleMedium)
            }
            open.forEach { item ->
                if (section == TodoSection.House) HouseRow(item, houseTask(item), tone) { ChurchDriveLinkOpen(context, item, vm.houseAddress) } else TaskRow(item, tone, onEdit = { editing = item }) { vm.setDone(section, item, true) }
            }
            // Finished tasks, kept out of the way until asked for; tick one to bring it back.
            val finished = items.orEmpty().filter { it.done }
            if (finished.isNotEmpty() && section != TodoSection.House) {
                Row(Modifier.fillMaxWidth().clickable { showDone = !showDone }, verticalAlignment = Alignment.CenterVertically) {
                    Text("Done (${finished.size})", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Icon(if (showDone) CdVectors.ChevronUp else CdVectors.ChevronDown, contentDescription = null)
                }
                if (showDone) finished.forEach { item ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Checkbox(checked = true, onCheckedChange = { vm.setDone(section, item, false) }, colors = checks(tone))
                        Text(
                            item.summary, style = MaterialTheme.typography.bodyLarge, textDecoration = TextDecoration.LineThrough,
                            color = tone.onContainer.copy(alpha = 0.6f), modifier = Modifier.weight(1f).clickable { editing = item },
                        )
                    }
                }
            }
            if (section.canAdd) vm.notice?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
            if (section.canAdd) AddRow(
                when (section) {
                    TodoSection.Shopping -> "Add an item"
                    TodoSection.Cleaning -> "Add a chore"
                    TodoSection.Shared -> "Add a shared task"
                    else -> "Add a task"
                },
                text, { text = it }, tone,
            ) { vm.add(section, text); text = "" }
        }
    }
}

/** The text box and round plus button at the foot of a card. */
@Composable
internal fun AddRow(hint: String, text: String, onText: (String) -> Unit, tone: ToneColors, onAdd: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        androidx.compose.material3.TextField(
            value = text, onValueChange = onText, placeholder = { Text(hint) }, singleLine = true,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(16.dp),
            colors = androidx.compose.material3.TextFieldDefaults.colors(
                focusedContainerColor = tone.onContainer.copy(alpha = 0.12f), unfocusedContainerColor = tone.onContainer.copy(alpha = 0.08f),
                focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent, unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                focusedTextColor = tone.onContainer, unfocusedTextColor = tone.onContainer,
                focusedPlaceholderColor = tone.onContainer.copy(alpha = 0.6f), unfocusedPlaceholderColor = tone.onContainer.copy(alpha = 0.6f),
                cursorColor = tone.accent,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onAdd() }),
        )
        FilledIconButton(
            onClick = onAdd,
            enabled = text.isNotBlank(),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = tone.accent, contentColor = tone.onAccent),
        ) { Icon(CdVectors.Plus, contentDescription = "Add") }
    }
}

// ---- Steps lists and projects ----

/** Steps lists assigned to this person or shared with them, on the Home page; each is a card that opens to its steps. */
@Composable
private fun StepsSection(vm: TodoModel) {
    val lists = vm.stepsLists()
    if (lists.isEmpty()) return
    val tone = toneColors(Tone.Tasks)
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    SectionHeader("Steps", "${lists.size} ${if (lists.size == 1) "list" else "lists"}", tone) { Icon(CdVectors.Todo, contentDescription = null, tint = tone.accent) }
    lists.forEach { l -> ChecklistCard(vm, l, tone, open == l.id, canDelete = false) { open = if (open == l.id) null else l.id } }
}

/** The Projects page: what is under way (the first one open), a way to start another, and what has been finished. */
@Composable
private fun ProjectsPage(vm: TodoModel) {
    val tone = toneColors(TodoTab.Projects.tone)
    val active = vm.projects()
    val finished = vm.finishedProjects()
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    if (creating) NewProjectDialog(tone, onCreate = { name, shared, date, steps -> vm.createProject(name, shared, date, steps); creating = false }, onClose = { creating = false })
    SectionHeader("Active projects", if (vm.shopping == null) null else active.size.toString(), tone) { Icon(CdVectors.Todo, contentDescription = null, tint = tone.accent) }
    if (vm.shopping == null) Text(vm.problem ?: "Loading…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
    active.forEach { l -> ChecklistCard(vm, l, tone, open == l.id || (open == null && l == active.first()), canDelete = true, canEdit = true) { open = if (open == l.id) "" else l.id } }
    Row(
        Modifier.fillMaxWidth().height(48.dp).background(tone.onContainer.copy(alpha = 0.1f), RoundedCornerShape(18.dp)).clickable { creating = true }.padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(CdVectors.Plus, contentDescription = null, tint = tone.accent)
        Text("New project", color = MaterialTheme.colorScheme.onBackground)
    }
    if (finished.isNotEmpty()) {
        SectionHeader("Finished", finished.size.toString(), tone) { Icon(CdVectors.Todo, contentDescription = null, tint = tone.accent) }
        finished.forEach { l -> ChecklistCard(vm, l, tone, open == l.id, canDelete = true, canEdit = true) { open = if (open == l.id) "" else l.id } }
    }
}

private fun shortDate(date: String): String? =
    runCatching { java.time.LocalDate.parse(date).format(java.time.format.DateTimeFormatter.ofPattern("d MMM")) }.getOrNull()

/** A steps list or project as a card: its name, who it is for, steps done, a bar; open it to tick, add and (for a project) delete. */
@Composable
private fun ChecklistCard(vm: TodoModel, list: ShoppingList, tone: ToneColors, expanded: Boolean, canDelete: Boolean, canEdit: Boolean = false, onToggle: () -> Unit) {
    var editing by remember(list.id) { mutableStateOf(false) }
    if (editing) EditProjectPage(vm, list, tone) { editing = false }
    val total = list.items.size
    val done = list.items.count { it.status != "open" }
    val next = list.items.firstOrNull { it.status == "open" }
    var text by rememberSaveable(list.id) { mutableStateOf("") }
    var sure by remember(list.id) { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(28.dp), color = tone.container, contentColor = tone.onContainer, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.glowBehind(tone.container).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth().clickable { onToggle() }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(40.dp).background(tone.accent.copy(alpha = 0.2f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(CdVectors.Todo, contentDescription = null, tint = tone.accent)
                }
                Column(Modifier.weight(1f)) {
                    Text(list.title, style = MaterialTheme.typography.titleMedium)
                    val sub = listOfNotNull(
                        vm.shareLabel(list),
                        list.date.takeIf { it.isNotBlank() }?.let { d -> shortDate(d)?.let { "by $it" } },
                        if (!expanded && next != null) "Next: ${next.text}" else null,
                    ).joinToString(" · ")
                    Text(sub, style = MaterialTheme.typography.bodySmall, color = tone.onContainer.copy(alpha = 0.75f), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
                Text(
                    "$done of $total", style = MaterialTheme.typography.labelMedium, color = tone.accent,
                    modifier = Modifier.background(tone.accent.copy(alpha = 0.18f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp),
                )
                Icon(if (expanded) CdVectors.ChevronUp else CdVectors.ChevronDown, contentDescription = null)
            }
            Box(Modifier.fillMaxWidth().height(6.dp).background(tone.onContainer.copy(alpha = 0.25f), RoundedCornerShape(3.dp))) {
                if (total > 0) Box(Modifier.fillMaxWidth(done.toFloat() / total).height(6.dp).background(tone.accent, RoundedCornerShape(3.dp)))
            }
            if (expanded) {
                list.items.forEach { item ->
                    val isDone = item.status != "open"
                    Row(
                        Modifier.fillMaxWidth().background(tone.accent.copy(alpha = if (item.id == next?.id) 0.22f else 0.1f), RoundedCornerShape(16.dp)).padding(end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Checkbox(checked = isDone, onCheckedChange = { vm.tickStep(list, item, it) }, colors = checks(tone))
                        Text(
                            item.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f),
                            textDecoration = if (isDone) TextDecoration.LineThrough else null,
                            color = if (isDone) tone.onContainer.copy(alpha = 0.6f) else tone.onContainer,
                        )
                    }
                }
                AddRow("Add a step", text, { text = it }, tone) { vm.addStep(list, text); text = "" }
                if (canEdit) com.churchdrive.app.ui.TileRow(
                    listOf(com.churchdrive.app.ui.TileItem("mdi:pencil", "Edit project", false) { editing = true }),
                    tone, tone.onContainer, perRow = 1,
                )
                if (canDelete) androidx.compose.material3.TextButton(onClick = { if (sure) vm.deleteProject(list) else sure = true }) {
                    Text(if (sure) "Tap again to delete this project" else "Delete project", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

/** Edit a project: its name, whether it is Mine or Shared, its target date and its steps (take one out); or delete it. */
@Composable
private fun EditProjectPage(vm: TodoModel, list: ShoppingList, tone: ToneColors, onClose: () -> Unit) {
    var name by remember { mutableStateOf(list.name) }
    var shared by remember { mutableStateOf(list.people.size > 1) }
    var date by remember { mutableStateOf(list.date) }
    var picking by remember { mutableStateOf(false) }
    var sure by remember { mutableStateOf(false) }
    val texts = remember { androidx.compose.runtime.mutableStateMapOf<String, String>() }
    val steps = vm.shopping?.lists?.firstOrNull { it.id == list.id }?.items ?: list.items
    val day = runCatching { java.time.LocalDate.parse(date) }.getOrNull()
    com.churchdrive.app.ui.FullPage("Edit project", onClose, tone = tone) {
        com.churchdrive.app.ui.WashField(name, { name = it }, "Name", tone)
        Text("For", style = MaterialTheme.typography.labelLarge)
        com.churchdrive.app.ui.OptionRow(listOf("Mine", "Shared"), if (shared) "Shared" else "Mine", tone) { shared = it == "Shared" }
        Text("Aim to finish by", style = MaterialTheme.typography.labelLarge)
        if (day != null) Text(com.churchdrive.app.ui.dateWords(day, java.time.LocalDate.now()), style = MaterialTheme.typography.bodySmall)
        com.churchdrive.app.ui.TileRow(
            listOf(
                com.churchdrive.app.ui.TileItem("mdi:calendar-edit", "Pick a date", day != null) { picking = true },
                com.churchdrive.app.ui.TileItem("mdi:calendar-remove", "No date", day == null) { date = "" },
            ),
            tone, tone.onContainer,
        )
        if (picking) com.churchdrive.app.ui.DatePickDialog(day, { date = it.toString(); picking = false }, { picking = false })
        Text("Steps", style = MaterialTheme.typography.labelLarge)
        // Each step is a box to change its words in, with a cross to take it out.
        steps.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.weight(1f)) { com.churchdrive.app.ui.WashField(texts[item.id] ?: item.text, { texts[item.id] = it }, "Step", tone) }
                Box(Modifier.clickable(onClickLabel = "Remove ${item.text}") { vm.removeStep(list, item) }.padding(12.dp)) {
                    Icon(CdVectors.Close, contentDescription = null, tint = tone.onContainer)
                }
            }
        }
        com.churchdrive.app.ui.TileRow(
            listOf(
                com.churchdrive.app.ui.TileItem("mdi:close", "Cancel", false) { onClose() },
                com.churchdrive.app.ui.TileItem("mdi:check", "Save", true) { vm.updateProject(list, name, shared, date); steps.forEach { vm.renameStep(list, it, texts[it.id] ?: it.text) }; onClose() },
            ),
            tone, tone.onContainer,
        )
        com.churchdrive.app.ui.TileRow(
            listOf(com.churchdrive.app.ui.TileItem("mdi:delete-outline", if (sure) "Tap again to delete" else "Delete project", sure) { if (sure) { vm.deleteProject(list); onClose() } else sure = true }),
            toneColors(Tone.Red), tone.onContainer,
        )
    }
}

/** Starts a project: its name, whether it is Mine or Shared, a target date and its first steps (one a line), on a page like Edit task. */
@Composable
private fun NewProjectDialog(tone: ToneColors, onCreate: (String, Boolean, String, List<String>) -> Unit, onClose: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var shared by remember { mutableStateOf(false) }
    var date by remember { mutableStateOf("") }
    var picking by remember { mutableStateOf(false) }
    var steps by remember { mutableStateOf("") }
    val day = runCatching { java.time.LocalDate.parse(date) }.getOrNull()
    com.churchdrive.app.ui.FullPage("New project", onClose, tone = tone) {
        com.churchdrive.app.ui.WashField(name, { name = it }, "Name", tone)
        Text("For", style = MaterialTheme.typography.labelLarge)
        com.churchdrive.app.ui.OptionRow(listOf("Mine", "Shared"), if (shared) "Shared" else "Mine", tone) { shared = it == "Shared" }
        Text("Aim to finish by", style = MaterialTheme.typography.labelLarge)
        if (day != null) Text(com.churchdrive.app.ui.dateWords(day, java.time.LocalDate.now()), style = MaterialTheme.typography.bodySmall)
        com.churchdrive.app.ui.TileRow(
            listOf(
                com.churchdrive.app.ui.TileItem("mdi:calendar-edit", "Pick a date", day != null) { picking = true },
                com.churchdrive.app.ui.TileItem("mdi:calendar-remove", "No date", day == null) { date = "" },
            ),
            tone, tone.onContainer,
        )
        if (picking) com.churchdrive.app.ui.DatePickDialog(day, { date = it.toString(); picking = false }, { picking = false })
        com.churchdrive.app.ui.WashField(steps, { steps = it }, "First steps, one a line", tone, singleLine = false, minLines = 3)
        com.churchdrive.app.ui.TileRow(
            listOf(
                com.churchdrive.app.ui.TileItem("mdi:close", "Cancel", false) { onClose() },
                com.churchdrive.app.ui.TileItem("mdi:check", "Create", true) {
                    if (name.isNotBlank()) onCreate(name, shared, date, steps.lines().map { it.trim() }.filter { it.isNotEmpty() })
                },
            ),
            tone, tone.onContainer,
        )
    }
}

@Composable
internal fun checks(tone: ToneColors) = CheckboxDefaults.colors(checkedColor = tone.accent, uncheckedColor = tone.onContainer.copy(alpha = 0.7f))

@Composable
private fun TaskRow(item: TodoItem, tone: ToneColors, onEdit: () -> Unit, onTick: () -> Unit) {
    // Each task sits on a faint wash of the text colour, like the main app's to-do items; icons and text are light.
    Row(
        Modifier.fillMaxWidth().background(tone.onContainer.copy(alpha = 0.08f), RoundedCornerShape(16.dp)).padding(end = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val list = shoppingIdOf(item.description)
        if (list.isNotEmpty()) {
            // A shopping or steps list has no tick of its own: it is finished by finishing its items, in the main Church Drive app.
            Box(Modifier.padding(12.dp)) {
                Icon(if (isStepsTask(item.description)) CdVectors.Todo else CdVectors.Cart, contentDescription = null, tint = tone.onContainer)
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
private fun HouseRow(item: TodoItem, task: HouseTask, tone: ToneColors, onOpen: () -> Unit) {
    // Tap a job to go to the part of the house it is about: the device's page in the Church Drive app, or Home Assistant.
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().clickable(onClickLabel = "Open the device") { onOpen() }.padding(vertical = 4.dp)) {
        Icon(androidx.compose.ui.res.painterResource(com.churchdrive.core.R.drawable.cd_house), contentDescription = null, tint = tone.onContainer)
        Column(Modifier.weight(1f)) {
            Text(item.summary, style = MaterialTheme.typography.bodyLarge)
            val sub = listOf(task.kind, task.detail).filter { it.isNotBlank() }.joinToString(" · ")
            if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, color = tone.onContainer.copy(alpha = 0.75f))
        }
    }
}

private fun ChurchDriveLinkOpen(context: android.content.Context, item: TodoItem, houseAddress: String?) {
    val kind = houseTask(item).kind
    ChurchDriveLink.openHouseJob(context, item.summary, houseTaskPage(kind), houseTaskHaPath(kind), houseAddress)
}
