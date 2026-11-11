package com.churchdrive.admin

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
import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ha.HaClient
import com.churchdrive.app.ha.LoginStep
import com.churchdrive.app.ha.Registry
import com.churchdrive.app.ha.asApi
import com.churchdrive.app.ui.HaApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Sign-in and the line to Home Assistant for Clive Admin. It is for administrators only: once Home Assistant has said who is signed in,
 * anyone who is not an administrator is told so (Home Assistant checks every change again, so this screen is courtesy, not security).
 */
class AdminModel(app: Application) : AndroidViewModel(app) {
    private val session = Session(app)
    private val client = HaClient(viewModelScope)

    /** Plain access to Home Assistant's commands, for the pages that fetch their own data (the activity log). */
    val api: HaApi = client.asApi()

    /** What the house knows right now: every entity, the device registry and the area names (Device Health, Energy and the rest read these). */
    var entities by mutableStateOf<Map<String, EntityState>>(emptyMap())
        private set
    var registry by mutableStateOf(Registry.Empty)
        private set
    var areaNames by mutableStateOf<Map<String, String>>(emptyMap())
        private set

    /** Switches, numbers and the like the Manager's settings cards change: a service call to the house. */
    val call: com.churchdrive.app.ha.CallService = { domain, service, entityId, data -> client.callService(domain, service, entityId, data) }

    /** Home Assistant's rendering of the dashboard's templates (panel summaries, the header lines), kept up to date. */
    val templates = com.churchdrive.app.ha.Templates(client)

    /** The Energy page as the house's dashboard lays it out (the same panels, in the same order, as the Church Drive app); null until read. */
    var energyPanels by mutableStateOf<List<ManagerSection>?>(null)
        private set
    var energyHeader by mutableStateOf<org.json.JSONObject?>(null)
        private set

    /** The Devices page as the house's dashboard lays it out (TVs and speakers, devices and services); null until read. */
    var devicesPanels by mutableStateOf<List<ManagerSection>?>(null)
        private set

    /** The house's address, for the cards that load pictures from it. */
    val baseUrl: String get() = session.url.orEmpty()

    /** The Manager's sections, read from the house's Manager dashboard (people, locations, notifications and the rest). */
    var managerSections by mutableStateOf<List<ManagerSection>>(emptyList())
        private set

    var signedIn by mutableStateOf(session.signedIn)
        private set
    /** The line's state, for the status pill at the top of the page. */
    val link get() = client.link

    /** Pull down to refresh: asks the house for everything again, or connects afresh when the line has gone quiet. */
    fun refresh() {
        if (!signedIn) return
        if (!connected) { connect(); return }
        client.refreshStates { alive -> if (!alive) connect() else loadHouse() }
    }

    var connected by mutableStateOf(false)
        private set

    /** Signed in through the Church Drive app: signing out is done there. */
    var borrowed by mutableStateOf(session.borrowed)
        private set
    var personName by mutableStateOf(session.personName)
        private set

    /** Home Assistant has said who this is (so [isAdmin] can be trusted). */
    var userKnown by mutableStateOf(false)
        private set
    var isAdmin by mutableStateOf(false)
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

    /** A newer test build of this app is out (a Play install is told by Google Play instead). */
    var updateAvailable by mutableStateOf(false)
        private set
    private var lastUpdateCheck = 0L

    init {
        viewModelScope.launch {
            client.userName.collect {
                userKnown = it != null
                if (it != null) {
                    session.savePerson(it)
                    personName = it
                }
            }
        }
        viewModelScope.launch { client.isAdmin.collect { isAdmin = it } }
        viewModelScope.launch { client.entities.collect { entities = it } }
        viewModelScope.launch {
            var failures = 0
            client.connection.collect { state ->
                connected = state == ConnectionState.Connected
                if (connected) {
                    failures = 0
                    templates.restart()
                    loadHouse()
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

    private var entityRegistry: Any? = null
    private var deviceRegistry: Any? = null

    /** Asks the house for its areas and its device registry (which entities belong to which device), as the phone app does when it connects. */
    private fun loadHouse() {
        client.request("config/area_registry/list") { result ->
            val list = result as? org.json.JSONArray ?: return@request
            val names = (0 until list.length()).mapNotNull { list.optJSONObject(it) }
                .filter { it.has("area_id") && it.has("name") }
                .associate { it.getString("area_id") to it.getString("name") }
            viewModelScope.launch(Dispatchers.Main) { areaNames = names }
        }
        client.request("lovelace/config", org.json.JSONObject().put("url_path", "dashboard-mobile")) { result ->
            val config = result as? org.json.JSONObject ?: return@request
            val views = config.optJSONArray("views") ?: return@request
            fun panelsOf(path: String) = (0 until views.length()).mapNotNull { views.optJSONObject(it) }.firstOrNull { it.optString("path") == path }
            val energy = panelsOf("energy")
            val devices = panelsOf("devices")
            val energyParsed = energy?.let { ManagerSections.parse(org.json.JSONObject().put("views", org.json.JSONArray().put(it))) }
            val header = energy?.let { ManagerSections.header(it) }
            val devicesParsed = devices?.let { ManagerSections.parse(org.json.JSONObject().put("views", org.json.JSONArray().put(it))) }
            viewModelScope.launch(Dispatchers.Main) { if (energyParsed != null) { energyPanels = energyParsed; energyHeader = header }; if (devicesParsed != null) devicesPanels = devicesParsed }
        }
        client.request("lovelace/config", org.json.JSONObject().put("url_path", "dashboard-manager")) { result ->
            val sections = (result as? org.json.JSONObject)?.let { ManagerSections.parse(it) } ?: return@request
            viewModelScope.launch(Dispatchers.Main) { managerSections = sections }
        }
        client.request("config/entity_registry/list_for_display") { result ->
            entityRegistry = result
            val parsed = Registry.parse(entityRegistry, deviceRegistry)
            viewModelScope.launch(Dispatchers.Main) { registry = parsed }
        }
        client.request("config/device_registry/list") { result ->
            deviceRegistry = result
            val parsed = Registry.parse(entityRegistry, deviceRegistry)
            viewModelScope.launch(Dispatchers.Main) { registry = parsed }
        }
    }

    private fun connect() {
        viewModelScope.launch(Dispatchers.IO) {
            val url = session.url ?: return@launch
            val token = session.freshToken() ?: return@launch
            withContext(Dispatchers.Main) { client.connect(url, token) }
        }
    }

    private fun checkForUpdate() {
        if (BuildConfig.PLAY) return
        lastUpdateCheck = System.currentTimeMillis()
        viewModelScope.launch(Dispatchers.IO) {
            val newer = com.churchdrive.app.UpdateCheck.check(okhttp3.OkHttpClient(), BuildConfig.BUILD.toIntOrNull() ?: 0, com.churchdrive.app.UpdateCheck.ADMIN_RELEASE_API)
            withContext(Dispatchers.Main) { updateAvailable = newer }
        }
    }

    /** The app came to the front: pick up the Church Drive app's sign-in, and connect again if the line has gone. */
    fun onForeground() {
        if (com.churchdrive.app.UpdateCheck.due(System.currentTimeMillis(), lastUpdateCheck)) checkForUpdate()
        importSignIn()
        if (signedIn && !connected) connect()
    }

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
        userKnown = false
        isAdmin = false
    }
}
