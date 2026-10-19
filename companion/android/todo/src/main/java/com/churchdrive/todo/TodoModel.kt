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
import com.churchdrive.app.SharedLookup
import com.churchdrive.app.freshToken
import com.churchdrive.app.ha.ConnectionState
import com.churchdrive.app.ha.HaAuth
import com.churchdrive.app.ha.HaClient
import com.churchdrive.app.ha.LoginStep
import com.churchdrive.app.ha.asApi
import com.churchdrive.app.ha.data
import com.churchdrive.app.ui.TodoItem
import com.churchdrive.app.ui.Tone
import com.churchdrive.app.ui.ask
import com.churchdrive.app.ui.houseTask
import com.churchdrive.app.ui.houseTaskFor
import com.churchdrive.app.ui.myTodoList
import com.churchdrive.app.ui.parseTodoItems
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** The pages of the app, as on the main app's To-do page: the person's own list, the shared one, cleaning and the house's jobs. */
enum class TodoTab(val label: String, val title: String, val tone: Tone, val canAdd: Boolean) {
    Mine("My to-do", "My to-do", Tone.Purple, true),
    Shared("Shared", "Shared", Tone.Blue, true),
    Cleaning("Cleaning", "Cleaning", Tone.Teal, true),
    House("House", "From the house", Tone.Purple, false),
}

const val SHARED_LIST = "todo.priorities_everyone"
const val CLEANING_LIST = "todo.cleaning"
const val HOUSE_LIST = "todo.priorities_automatic"

/** Sign-in, the line to Home Assistant, the to-do lists and the tasks in the open one. */
class TodoModel(app: Application) : AndroidViewModel(app) {
    private val session = Session(app)
    private val client = HaClient(viewModelScope)
    private val api = client.asApi()

    var signedIn by mutableStateOf(session.signedIn)
        private set
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

    /** Who is signed in (their name in Home Assistant); only their own list is ever shown. */
    var personName by mutableStateOf(session.personName)
        private set

    var tab by mutableStateOf(TodoTab.Mine)
    /** The tasks of each of the four lists, by entity id; a list is absent until it has arrived. */
    private val loaded = mutableStateMapOf<String, List<TodoItem>>()
    var problem by mutableStateOf<String?>(null)
        private set
    private val counts = HashMap<String, String?>()

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
        importSignIn()
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

    fun listId(tab: TodoTab): String? = when (tab) {
        TodoTab.Mine -> mineId()
        TodoTab.Shared -> SHARED_LIST
        TodoTab.Cleaning -> CLEANING_LIST
        TodoTab.House -> HOUSE_LIST
    }

    private fun listIds() = TodoTab.entries.mapNotNull { listId(it) }

    private fun refreshAll() = listIds().forEach { loadItems(it) }

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

    /** The tasks of a page, null until they arrive. The house's page shows only the jobs for this person or for everyone. */
    fun items(tab: TodoTab): List<TodoItem>? {
        val all = listId(tab)?.let { loaded[it] } ?: return null
        if (tab != TodoTab.House) return all
        val first = personName?.trim()?.substringBefore(' ')
        return all.filter { houseTaskFor(houseTask(it), first) }
    }

    /** How many tasks are still open on a page (for the badge on its button). */
    fun openCount(tab: TodoTab): Int = items(tab)?.count { !it.done } ?: 0

    fun setDone(tab: TodoTab, item: TodoItem, done: Boolean) {
        val id = listId(tab) ?: return
        // Shown at once; the house's list is read again a moment later.
        loaded[id] = loaded[id].orEmpty().map { if (it.uid == item.uid) it.copy(done = done) else it }
        client.callService("todo", "update_item", id, data("item" to item.uid, "status" to if (done) "completed" else "needs_action"))
        changed(id)
    }

    /** Saves an edited task (the fields `todo.update_item` takes: see `updateFields`). */
    fun save(tab: TodoTab, fields: Map<String, Any?>) {
        val id = listId(tab) ?: return
        client.callService("todo", "update_item", id, data(*fields.map { it.key to (it.value ?: JSONObject.NULL) }.toTypedArray()))
        changed(id)
    }

    fun remove(tab: TodoTab, item: TodoItem) {
        val id = listId(tab) ?: return
        loaded[id] = loaded[id].orEmpty().filter { it.uid != item.uid }
        client.callService("todo", "remove_item", id, data("item" to item.uid))
        changed(id)
    }

    fun add(tab: TodoTab, text: String) {
        val id = listId(tab) ?: return
        if (text.isBlank() || !tab.canAdd) return
        client.callService("todo", "add_item", id, data("item" to text.trim()))
        changed(id)
    }

    private fun changed(id: String) {
        viewModelScope.launch {
            // The house needs a moment to apply the change before the list is read again.
            delay(400)
            loadItems(id)
        }
    }
}
