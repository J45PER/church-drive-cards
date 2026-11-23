package com.churchdrive.todo

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.churchdrive.app.Session
import com.churchdrive.app.SessionShare
import com.churchdrive.app.UpdateCheck
import com.churchdrive.app.SharedLookup
import com.churchdrive.app.freshToken
import com.churchdrive.app.ha.ConnectionState
import com.churchdrive.app.ha.HaAuth
import com.churchdrive.app.ha.HaClient
import com.churchdrive.app.ha.LoginStep
import com.churchdrive.app.ha.asApi
import com.churchdrive.app.ha.data
import com.churchdrive.app.ui.AssistantBrand
import com.churchdrive.app.ui.AssistantList
import com.churchdrive.app.ui.AssistantMatch
import com.churchdrive.app.ui.AssistantTodoCommand
import com.churchdrive.app.ui.ShoppingItem
import com.churchdrive.app.ui.ShoppingList
import com.churchdrive.app.ui.ShoppingState
import com.churchdrive.app.ui.TodoItem
import com.churchdrive.app.ui.parseShopping
import com.churchdrive.app.ui.Tone
import com.churchdrive.app.ui.ask
import com.churchdrive.app.ui.houseTask
import com.churchdrive.app.ui.houseTaskFor
import com.churchdrive.app.ui.sortedHouseTasks
import com.churchdrive.app.ui.matchOpenTask
import com.churchdrive.app.ui.myTodoList
import com.churchdrive.app.ui.parseAssistantTodo
import com.churchdrive.app.ui.spokenList
import com.churchdrive.app.ui.parseTodoItems
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** A list the app reads: the person's own, the shared one, the house's jobs, the shopping list and the cleaning chores. */
enum class TodoSection(val title: String, val tone: Tone, val canAdd: Boolean) {
    Mine("My list", Tone.Tasks, true),
    Shared("Shared", Tone.TodoShared, true),
    House("From the house", Tone.TodoHouse, false),
    Shopping("Shopping list", Tone.Shopping, true),
    Cleaning("Chores", Tone.Gregg, true),
}

/** The pages along the bottom, as in the main app's to-do page: Home, Shopping, Cleaning and Projects. */
enum class TodoTab(val label: String, val title: String, val tone: Tone, val sections: List<TodoSection>) {
    Home("Home", "Home", Tone.Tasks, listOf(TodoSection.Mine, TodoSection.Shared, TodoSection.House)),
    Shopping("Shopping", "Shopping", Tone.Shopping, listOf(TodoSection.Shopping)),
    Cleaning("Cleaning", "Cleaning", Tone.Gregg, listOf(TodoSection.Cleaning)),
    Projects("Projects", "Projects", Tone.Lights, emptyList()),
}

const val SHARED_LIST = "todo.priorities_everyone"
const val CLEANING_LIST = "todo.cleaning"
const val HOUSE_LIST = "todo.priorities_automatic"
const val SHOPPING_LIST = "todo.shopping_list"

/** Sign-in, the line to Home Assistant, the to-do lists and the tasks in the open one. */
class TodoModel(app: Application) : AndroidViewModel(app) {
    private val session = Session(app)
    private val client = HaClient(viewModelScope)

    /** Reads the whole house again after a minute away and every five minutes while open, so anything that missed a live event does not stay stale. */
    private val freshness = com.churchdrive.app.ha.FreshnessWatcher(
        viewModelScope, client,
        onChecked = { visible, changed -> if (visible || changed) refreshAll() },
        onDead = { if (signedIn) connect() },
    )
    fun onBackground() = freshness.onBackground()
    private val api = client.asApi()

    var signedIn by mutableStateOf(session.signedIn)
        private set
    /** The line's state, for the status pill at the top of the page. */
    val link get() = client.link

    /** Pull down to refresh: asks the house for everything again, or connects afresh when the line has gone quiet. */
    fun refresh() {
        if (!signedIn) return
        if (!connected) { connect(); return }
        client.refreshStates { alive -> if (!alive) connect() else refreshAll() }
    }

    var connected by mutableStateOf(false)
        private set
    /** Signed in through the Church Drive app: signing out is done there. */
    var borrowed by mutableStateOf(session.borrowed)
        private set

    // Signing in.
    var busy by mutableStateOf(false)
        private set
    var loginError by mutableStateOf<String?>(null)
        private set
    var needCode by mutableStateOf(false)
        private set
    private var flowId = ""
    private var flowUrl = ""

    /** The house's address, for the link to Home Assistant when the Church Drive app isn't on the phone. */
    val houseAddress: String? get() = session.url

    /** A task another app asked to open (from the Church Drive app's Home page): shown on its page once its list has arrived. */
    var pendingUid by mutableStateOf<String?>(null)
        private set

    /** Opens the page of the task's list, and the task itself once it has loaded. */
    fun openFromLink(list: String, uid: String) {
        tab = when (list) {
            SHOPPING_LIST -> TodoTab.Shopping
            CLEANING_LIST -> TodoTab.Cleaning
            else -> TodoTab.Home
        }
        pendingUid = uid
    }

    fun clearPending() {
        pendingUid = null
    }

    /** Who is signed in (their name in Home Assistant); only their own list is ever shown. */
    var personName by mutableStateOf(session.personName)
        private set

    val settings = TodoSettings(app)
    var showDone by mutableStateOf(settings.showDone)
    var rememberPage by mutableStateOf(settings.rememberPage)

    /** The page shown: My to-do, or the one last looked at when the person has asked for that. */
    var tab by mutableStateOf(if (settings.rememberPage) TodoTab.entries.firstOrNull { it.name == settings.lastPage } ?: TodoTab.Home else TodoTab.Home)
        private set

    fun openTab(t: TodoTab) {
        tab = t
        settings.lastPage = t.name
    }

    fun changeShowDone(on: Boolean) {
        showDone = on
        settings.showDone = on
    }

    fun changeRememberPage(on: Boolean) {
        rememberPage = on
        settings.rememberPage = on
    }

    // A newer test build of this app, asked about when the app comes to the front (not more than every ten minutes).
    var updateAvailable by mutableStateOf(false)
        private set
    private var lastUpdateCheck = 0L

    fun checkForUpdate(then: (Boolean) -> Unit = {}) {
        if (BuildConfig.PLAY) { then(false); return }
        lastUpdateCheck = System.currentTimeMillis()
        viewModelScope.launch(Dispatchers.IO) {
            val newer = UpdateCheck.check(okhttp3.OkHttpClient(), BuildConfig.BUILD.toIntOrNull() ?: 0, UpdateCheck.TODO_RELEASE_API)
            withContext(Dispatchers.Main) {
                updateAvailable = newer
                then(newer)
            }
        }
    }
    /** The tasks of each of the four lists, by entity id; a list is absent until it has arrived. */
    private val loaded = mutableStateMapOf<String, List<TodoItem>>()
    var problem by mutableStateOf<String?>(null)
        private set

    /** Why the last task could not be saved ("no answer from the house"), shown by the add box until the next try; null when it went well. */
    var notice by mutableStateOf<String?>(null)
        private set
    private val counts = HashMap<String, String?>()
    /** Tasks added while there was no line, waiting to be sent: (list, text, the uid shown meanwhile). */
    private val queuedAdds = mutableListOf<Triple<String, String, String>>()

    /** Every steps list and project, null until they arrive. */
    var shopping by mutableStateOf<ShoppingState?>(null)
        private set
    private var shoppingSub = -1

    init {
        viewModelScope.launch {
            client.userName.collect {
                if (it != null) {
                    session.savePerson(it)
                    personName = it
                    refreshAll()
                }
            }
        }
        // A list is read again whenever its count changes in the house, as the main app does.
        viewModelScope.launch {
            client.entities.collect { states ->
                listIds().forEach { id ->
                    val count = states[id]?.state
                    if (states.containsKey(id) && counts[id] != count) {
                        counts[id] = count
                        loadItems(id)
                    }
                }
            }
        }
        viewModelScope.launch {
            var failures = 0
            client.connection.collect { state ->
                connected = state == ConnectionState.Connected
                if (connected) {
                    failures = 0
                    refreshAll()
                    flushQueuedAdds()
                    // Steps lists and projects change in the house when anyone ticks one: read them again then.
                    if (shoppingSub >= 0) api.close(shoppingSub)
                    shoppingSub = api.subscribe("subscribe_events", JSONObject().put("event_type", "church_drive_shopping_changed")) { loadShoppingSoon() }
                }
                // Not given up on at once: Home Assistant may be restarting. A pasted token that is refused is just wrong.
                if (state == ConnectionState.AuthFailed && signedIn) {
                    if (session.refreshToken == null) {
                        signOut()
                    } else {
                        failures++
                        delay((2_000L * failures).coerceAtMost(30_000L))
                        if (signedIn) connect()
                    }
                }
            }
        }
        if (signedIn) connect()
    }

    private fun connect() {
        viewModelScope.launch(Dispatchers.IO) {
            val url = session.url ?: return@launch
            val token = session.freshToken() ?: return@launch
            withContext(Dispatchers.Main) { client.connect(url, token) }
        }
    }

    /** The app came to the front: connect again if the line has gone. */
    fun onForeground() {
        if (UpdateCheck.due(System.currentTimeMillis(), lastUpdateCheck)) checkForUpdate()
        importSignIn()
        freshness.onForeground()
        if (signedIn && !connected) connect()
    }

    /** Signs in with the Church Drive app's sign-in when it is on this phone, and follows it when it signs out or in as someone else. */
    private fun importSignIn() {
        if (signedIn && !session.borrowed) return
        viewModelScope.launch(Dispatchers.IO) {
            val found = SessionShare.read(getApplication())
            withContext(Dispatchers.Main) {
                when {
                    found is SharedLookup.Found -> {
                        val changed = !signedIn || session.url != found.session.url || session.refreshToken != found.session.refresh
                        session.saveBorrowed(found.session)
                        borrowed = true
                        signedIn = true
                        if (changed) {
                            client.disconnect()
                            connect()
                        }
                    }
                    found is SharedLookup.SignedOut && signedIn -> signOut()
                    else -> Unit
                }
            }
        }
    }

    // ---- Signing in ----

    fun login(url: String, username: String, password: String) {
        busy = true
        loginError = null
        flowUrl = url.trim().let { if ("://" in it) it else "http://$it" }
        viewModelScope.launch(Dispatchers.IO) {
            val opened = HaAuth.start(flowUrl)
            val step = if (opened is LoginStep.Credentials) {
                flowId = opened.flowId
                HaAuth.submit(flowUrl, opened.flowId, mapOf("username" to username.trim(), "password" to password))
            } else {
                opened
            }
            onStep(step)
        }
    }

    fun submitCode(code: String) {
        busy = true
        loginError = null
        viewModelScope.launch(Dispatchers.IO) { onStep(HaAuth.submit(flowUrl, flowId, mapOf("code" to code.trim()))) }
    }

    private suspend fun onStep(step: LoginStep) {
        when (step) {
            is LoginStep.Done -> {
                val tokens = HaAuth.exchange(flowUrl, step.code)
                withContext(Dispatchers.Main) {
                    busy = false
                    if (tokens == null) {
                        loginError = "Home Assistant didn't give a sign-in. Try again."
                    } else {
                        session.saveSignIn(flowUrl, tokens.access, tokens.refresh)
                        needCode = false
                        borrowed = false
                        signedIn = true
                        connect()
                    }
                }
            }
            is LoginStep.Credentials -> withContext(Dispatchers.Main) {
                flowId = step.flowId
                loginError = step.error ?: "Sign-in didn't work."
                busy = false
            }
            is LoginStep.Mfa -> withContext(Dispatchers.Main) {
                flowId = step.flowId
                needCode = true
                loginError = step.error
                busy = false
            }
            is LoginStep.Failed -> withContext(Dispatchers.Main) {
                loginError = step.message
                busy = false
            }
        }
    }

    fun signOut() {
        val url = session.url
        val refresh = session.refreshToken
        // A borrowed sign-in belongs to the Church Drive app: revoking it here would sign that app out too.
        if (url != null && refresh != null && !session.borrowed) viewModelScope.launch(Dispatchers.IO) { HaAuth.revoke(url, refresh) }
        client.disconnect()
        session.clear()
        borrowed = false
        signedIn = false
        personName = null
        loaded.clear()
        counts.clear()
    }

    // ---- Lists and tasks ----

    /** The signed-in person's own list: no one else's list is ever asked for. */
    private fun mineId() = myTodoList(personName)

    fun listId(section: TodoSection): String? = when (section) {
        TodoSection.Mine -> mineId()
        TodoSection.Shared -> SHARED_LIST
        TodoSection.Cleaning -> CLEANING_LIST
        TodoSection.House -> HOUSE_LIST
        TodoSection.Shopping -> SHOPPING_LIST
    }

    private fun listIds() = TodoSection.entries.mapNotNull { listId(it) }

    private fun refreshAll() {
        listIds().forEach { loadItems(it) }
        loadShopping()
    }

    private fun loadItems(id: String) {
        viewModelScope.launch {
            // A failed or slow answer is asked for again; it is not taken for an empty list.
            var tries = 0
            while (tries < 4) {
                val result = api.ask("todo/item/list", data("entity_id" to id))
                if (result != null) {
                    problem = null
                    loaded[id] = parseTodoItems(result)
                    return@launch
                }
                tries++
                delay(2_000L * tries)
            }
            if (loaded[id] == null) problem = "Couldn't read that list."
        }
    }

    /** The tasks of a list, null until they arrive. The house's list shows only the jobs for this person or for everyone. */
    fun items(section: TodoSection): List<TodoItem>? {
        val all = listId(section)?.let { loaded[it] } ?: return null
        if (section != TodoSection.House) return all
        val first = personName?.trim()?.substringBefore(' ')
        return sortedHouseTasks(all.map { it to houseTask(it) }.filter { houseTaskFor(it.second, first) }).map { it.first }
    }

    /** What the highlights card at the top shows: every list's tasks by entity id (the house's already filtered to this person). */
    fun highlightLists(): Map<String, List<TodoItem>> = buildMap {
        TodoSection.entries.filter { it != TodoSection.Shopping && it != TodoSection.Cleaning }.forEach { s ->
            val id = listId(s) ?: return@forEach
            val list = if (s == TodoSection.House) loaded[id]?.let { items(s) } else loaded[id]
            if (list != null) put(id, list)
        }
    }

    /** Whether the lists the highlights use have arrived. */
    val highlightsLoaded: Boolean get() = TodoSection.entries.any { it != TodoSection.Shopping && it != TodoSection.Cleaning && listId(it)?.let { id -> loaded.containsKey(id) } == true }

    /** How many tasks are still open on a page (for the badge on its button). */
    fun openCount(tab: TodoTab): Int =
        if (tab == TodoTab.Projects) projectsOpen().size
        else tab.sections.filter { it != TodoSection.House }.sumOf { s -> items(s)?.count { !it.done } ?: 0 }

    fun setDone(section: TodoSection, item: TodoItem, done: Boolean) {
        val id = listId(section) ?: return
        // Shown at once; the house's list is read again a moment later.
        loaded[id] = loaded[id].orEmpty().map { if (it.uid == item.uid) it.copy(done = done) else it }
        client.callService("todo", "update_item", id, data("item" to item.uid, "status" to if (done) "completed" else "needs_action"))
        changed(id)
    }

    /** Ticks a task off from the highlights card, whichever list it is on. */
    fun tickByUid(list: String, uid: String) {
        loaded[list] = loaded[list].orEmpty().map { if (it.uid == uid) it.copy(done = true) else it }
        client.callService("todo", "update_item", list, data("item" to uid, "status" to "completed"))
        changed(list)
    }

    /** Saves an edited task (the fields `todo.update_item` takes: see `updateFields`). */
    fun save(section: TodoSection, fields: Map<String, Any?>) {
        val id = listId(section) ?: return
        client.callService("todo", "update_item", id, data(*fields.map { it.key to (it.value ?: JSONObject.NULL) }.toTypedArray()))
        changed(id)
    }

    fun remove(section: TodoSection, item: TodoItem) {
        val id = listId(section) ?: return
        loaded[id] = loaded[id].orEmpty().filter { it.uid != item.uid }
        client.callService("todo", "remove_item", id, data("item" to item.uid))
        changed(id)
    }

    fun add(section: TodoSection, text: String) {
        val id = listId(section) ?: return
        if (text.isBlank() || !section.canAdd) return
        val summary = text.trim()
        notice = null
        // Shown at once, and kept only if the house says it has it: a task sent down a line that was down used to vanish without a word.
        val local = TodoItem("local-${System.nanoTime()}", summary, false, null, null)
        loaded[id] = loaded[id].orEmpty() + local
        if (!connected) {
            // No line to the house right now: the task stays on screen and is sent as soon as the line is back.
            queuedAdds += Triple(id, summary, local.uid)
            notice = "No line to the house yet. \"$summary\" will be saved when it is back."
            connect()
            return
        }
        sendAdd(id, summary, local.uid)
    }

    private fun flushQueuedAdds() {
        val now = queuedAdds.toList()
        queuedAdds.clear()
        now.forEach { (list, text, uid) -> sendAdd(list, text, uid) }
    }

    private fun sendAdd(id: String, summary: String, localUid: String) {
        val body = JSONObject().put("domain", "todo").put("service", "add_item").put("service_data", data("item" to summary)).put("target", JSONObject().put("entity_id", id))
        client.request("call_service", body, 15_000L) { result ->
            viewModelScope.launch {
                if (result == null) {
                    loaded[id] = loaded[id].orEmpty().filter { it.uid != localUid }
                    // Say why: the house refused it (and what it said), or there was no line, or no answer came.
                    val why = when (val code = client.lastErrorCode) {
                        "no_line" -> "there is no line to the house"
                        "no_answer", null -> "the house didn't answer"
                        else -> "the house refused it (${listOfNotNull(code, client.lastErrorMessage).joinToString(": ")})"
                    }
                    notice = "Couldn't save \"$summary\": $why. Try again."
                } else {
                    notice = null
                    changed(id)
                }
            }
        }
    }

    // ---- Steps lists and projects (the house's own lists, kept by Church Drive) ----

    private fun loadShopping() {
        viewModelScope.launch {
            parseShopping(api.ask("church_drive/shopping", JSONObject()))?.let { shopping = it }
        }
    }

    private fun first() = personName?.trim()?.substringBefore(' ').orEmpty()

    /** Steps lists on the Home page: those assigned to this person or shared with them, still open. */
    fun stepsLists(): List<ShoppingList> = shopping?.lists.orEmpty().filter { it.isSteps && !it.done && first() in it.people }

    private fun projectsOpen(): List<ShoppingList> = shopping?.lists.orEmpty().filter { it.isProject && !it.done && first() in it.people }

    fun projects(): List<ShoppingList> = projectsOpen().sortedWith(compareBy<ShoppingList>({ it.date.isEmpty() }, { it.date }))

    fun finishedProjects(): List<ShoppingList> = shopping?.lists.orEmpty().filter { it.isProject && it.done && first() in it.people }

    /** A list belongs to its person alone ("Mine") or to several ("Shared"). */
    fun shareLabel(l: ShoppingList): String = if (l.people.size > 1) "Shared" else "Mine"

    private fun sendList(type: String, body: JSONObject) {
        api.request(type, body) { loadShoppingSoon() }
    }

    private fun loadShoppingSoon() {
        android.os.Handler(android.os.Looper.getMainLooper()).post { loadShopping() }
    }

    /** Ticks a step or project step (shown at once; the house is then asked again). */
    fun tickStep(list: ShoppingList, item: ShoppingItem, done: Boolean) {
        val status = if (done) "bought" else "open"
        shopping = shopping?.let { st -> st.copy(lists = st.lists.map { l -> if (l.id == list.id) l.copy(items = l.items.map { if (it.id == item.id) it.copy(status = status) else it }, done = l.items.all { (if (it.id == item.id) status else it.status) != "open" } && l.items.isNotEmpty()) else l }) }
        sendList("church_drive/shopping/item", JSONObject().put("list", list.id).put("item", item.id).put("status", status))
    }

    fun addStep(list: ShoppingList, text: String) {
        val steps = text.split('\n', ';').map { it.trim() }.filter { it.isNotEmpty() }
        if (steps.isEmpty()) return
        sendList("church_drive/shopping/add", JSONObject().put("list", list.id).put("items", JSONArray(steps)))
    }

    fun removeStep(list: ShoppingList, item: ShoppingItem) {
        sendList("church_drive/shopping/item", JSONObject().put("list", list.id).put("item", item.id).put("status", "remove"))
    }

    /** Starts a project for this person, or for everyone in the house when [shared]. */
    fun createProject(name: String, shared: Boolean, date: String, steps: List<String>) {
        if (name.isBlank()) return
        val who = if (shared) shopping?.people.orEmpty().ifEmpty { listOf(first()) } else listOf(first())
        sendList(
            "church_drive/shopping/create",
            JSONObject().put("name", name.trim()).put("kind", "project").put("people", JSONArray(who)).put("date", date).put("items", JSONArray(steps)).put("include_missed", false),
        )
    }

    /** Changes a project's name, who it is for (Mine or Shared) and its target date ("" for none). */
    fun updateProject(list: ShoppingList, name: String, shared: Boolean, date: String) {
        if (name.isBlank()) return
        val who = if (shared) shopping?.people.orEmpty().ifEmpty { list.people } else listOf(first())
        shopping = shopping?.let { st -> st.copy(lists = st.lists.map { if (it.id == list.id) it.copy(name = name.trim(), people = who, date = date) else it }) }
        sendList("church_drive/shopping/update", JSONObject().put("list", list.id).put("name", name.trim()).put("people", JSONArray(who)).put("date", date))
    }

    fun deleteProject(list: ShoppingList) {
        shopping = shopping?.let { st -> st.copy(lists = st.lists.filter { it.id != list.id }) }
        sendList("church_drive/shopping/delete", JSONObject().put("list", list.id))
    }

    private fun tabOf(l: AssistantList): TodoSection = when (l) {
        AssistantList.Mine -> TodoSection.Mine
        AssistantList.Shared -> TodoSection.Shared
        AssistantList.Cleaning -> TodoSection.Cleaning
    }

    private fun listName(t: TodoSection) = when (t) {
        TodoSection.Mine -> "your list"
        TodoSection.Shared -> "the shared list"
        TodoSection.Cleaning -> "the cleaning list"
        TodoSection.House -> "the house list"
        TodoSection.Shopping -> "the shopping list"
    }

    /**
     * Clive To Do: does one typed or spoken sentence and says what happened. It can only add a task to the person's own, the shared or
     * the cleaning list, or tick one off there; every other sentence is turned down, so nothing else in the house is reachable from here.
     */
    /** Asks the house what the assistant is called (remembered, so it is right before the house answers next time). */
    fun refreshAssistantName(context: android.content.Context) {
        api.request("church_drive/assist/info", JSONObject()) { r ->
            if (r is JSONObject) android.os.Handler(android.os.Looper.getMainLooper()).post { AssistantBrand.update(context, r.optString("name"), null) }
        }
    }

    fun askAssistant(input: String): String {
        if (!connected) return "I can't reach the house right now."
        return when (val c = parseAssistantTodo(input)) {
            is AssistantTodoCommand.Add -> {
                val tab = tabOf(c.list)
                if (listId(tab) == null) return "I can't find that list."
                c.items.forEach { add(tab, it) }
                "Added ${spokenList(c.items)} to ${listName(tab)}."
            }
            is AssistantTodoCommand.Done -> {
                val tabs = c.list?.let { listOf(tabOf(it)) } ?: listOf(TodoSection.Mine, TodoSection.Shared, TodoSection.Cleaning)
                val found = tabs.flatMap { t ->
                    when (val m = matchOpenTask(items(t).orEmpty(), c.text)) {
                        is AssistantMatch.One -> listOf(t to m.item)
                        is AssistantMatch.Several -> m.items.map { t to it }
                        AssistantMatch.None -> emptyList()
                    }
                }
                when {
                    found.isEmpty() -> "I can't find \"${c.text}\" on your open lists."
                    found.size == 1 -> {
                        setDone(found[0].first, found[0].second, true)
                        "Ticked off ${found[0].second.summary}."
                    }
                    else -> "Which one? " + spokenList(found.map { "${it.second.summary} (${listName(it.first)})" }) + ". Say a bit more of its name."
                }
            }
            AssistantTodoCommand.Unknown -> "I can only add things and tick them off here. Try \"add milk\" or \"tick off the bins\"."
        }
    }

    private fun changed(id: String) {
        viewModelScope.launch {
            // The house needs a moment to apply the change before the list is read again.
            delay(400)
            loadItems(id)
        }
    }
}
