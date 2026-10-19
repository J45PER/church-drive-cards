package com.churchdrive.todo

import android.app.Application
import androidx.compose.runtime.getValue
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
import com.churchdrive.app.ui.ask
import com.churchdrive.app.ui.myTodoList
import com.churchdrive.app.ui.parseTodoItems
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

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

    /** Every to-do list in the house: its entity id and name, the person's own first. */
    var lists by mutableStateOf<List<Pair<String, String>>>(emptyList())
        private set
    var openList by mutableStateOf<Pair<String, String>?>(null)
        private set
    var items by mutableStateOf<List<TodoItem>?>(null)
        private set
    var problem by mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch { client.userName.collect { if (it != null) session.savePerson(it) } }
        viewModelScope.launch {
            var failures = 0
            client.connection.collect { state ->
                connected = state == ConnectionState.Connected
                if (connected) {
                    failures = 0
                    loadLists()
                    openList?.let { loadItems() }
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
            withContext(Dispatchers.Main) { client.connect(url, token, light = true) }
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
        lists = emptyList()
        openList = null
        items = null
    }

    // ---- Lists and tasks ----

    private fun loadLists() {
        viewModelScope.launch {
            val all = api.ask("get_states", JSONObject()) as? JSONArray ?: return@launch
            val mine = myTodoList(session.personName)
            lists = HaClient.parseStates(all).values
                .filter { it.entityId.startsWith("todo.") && it.available }
                .map { it.entityId to it.friendlyName }
                .sortedWith(compareBy({ it.first != mine }, { it.second.lowercase() }))
        }
    }

    fun open(list: Pair<String, String>?) {
        openList = list
        items = null
        if (list != null) loadItems()
    }

    private fun loadItems() {
        val list = openList ?: return
        viewModelScope.launch {
            val result = api.ask("todo/item/list", data("entity_id" to list.first))
            if (openList?.first != list.first) return@launch
            if (result == null) {
                problem = "Couldn't read ${list.second}."
            } else {
                problem = null
                items = parseTodoItems(result)
            }
        }
    }

    private fun changed() {
        viewModelScope.launch {
            // The house needs a moment to apply the change before the list is read again.
            delay(400)
            loadItems()
        }
    }

    fun setDone(item: TodoItem, done: Boolean) {
        val list = openList ?: return
        // Shown at once; the house's list is read again a moment later.
        items = items?.map { if (it.uid == item.uid) it.copy(done = done) else it }
        client.callService("todo", "update_item", list.first, data("item" to item.uid, "status" to if (done) "completed" else "needs_action"))
        changed()
    }

    fun add(text: String) {
        val list = openList ?: return
        if (text.isBlank()) return
        client.callService("todo", "add_item", list.first, data("item" to text.trim()))
        changed()
    }
}
