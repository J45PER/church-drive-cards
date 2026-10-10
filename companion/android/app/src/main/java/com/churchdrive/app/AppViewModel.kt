package com.churchdrive.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.churchdrive.app.ha.ConnectionState
import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ha.HaAuth
import com.churchdrive.app.ha.HaClient
import com.churchdrive.app.ha.IconPack
import com.churchdrive.app.ha.LoginStep
import com.churchdrive.app.ha.Refresh
import com.churchdrive.app.ha.data
import com.churchdrive.app.ha.Registry
import com.churchdrive.app.ha.Templates
import com.churchdrive.app.ui.CameraHost
import com.churchdrive.app.ui.DashboardLights
import com.churchdrive.app.ui.DashboardPanels
import com.churchdrive.app.ui.PanelSpec
import com.churchdrive.app.ui.LightLayout
import com.churchdrive.app.ui.SceneLooks
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val session = Session(app)
    private val client = HaClient(viewModelScope)

    val connection: StateFlow<ConnectionState> = client.connection
    val entities: StateFlow<Map<String, EntityState>> = client.entities
    val userName: StateFlow<String?> = client.userName
    val isAdmin: StateFlow<Boolean> = client.isAdmin
    val statesLoaded: StateFlow<Boolean> = client.statesLoaded

    private val _lights = MutableStateFlow(LightLayout.Fallback)
    val lights: StateFlow<LightLayout> = _lights

    private val _sceneLooks = MutableStateFlow(SceneLooks.Empty)
    val sceneLooks: StateFlow<SceneLooks> = _sceneLooks
    private var libraryResult: Any? = null
    private var stylesDashboard: org.json.JSONObject? = null

    /** Home Assistant's rendering of the dashboard's templates (panel summaries and colours), kept up to date. */
    val templates = Templates(client)

    private val _panels = MutableStateFlow<Map<String, List<PanelSpec>>>(emptyMap())
    val panels: StateFlow<Map<String, List<PanelSpec>>> = _panels

    /** The Manager dashboard's panels, for administrators: each is a page in the account panel. */
    private val _managerPanels = MutableStateFlow<List<PanelSpec>>(emptyList())
    val managerPanels: StateFlow<List<PanelSpec>> = _managerPanels

    private val _registry = MutableStateFlow(Registry.Empty)
    val registry: StateFlow<Registry> = _registry
    private var entityRegistryResult: Any? = null
    private var deviceRegistryResult: Any? = null

    private val _areaNames = MutableStateFlow<Map<String, String>>(emptyMap())
    val areaNames: StateFlow<Map<String, String>> = _areaNames

    /** A newer test build has been published (checked when the app starts and each time it comes to the front, at most every ten minutes). */
    private val _updateAvailable = MutableStateFlow(false)
    val updateAvailable: StateFlow<Boolean> = _updateAvailable

    private var lastUpdateCheck = 0L

    private fun checkForUpdate() {
        lastUpdateCheck = System.currentTimeMillis()
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _updateAvailable.value = UpdateCheck.check(okhttp3.OkHttpClient(), BuildConfig.COMMIT)
        }
    }

    /** What the person has chosen: notifications from the house, and sharing their location with it. */
    private val _notifyOn = MutableStateFlow(session.notifyOn)
    val notifyOn: StateFlow<Boolean> = _notifyOn
    private val _locationOn = MutableStateFlow(session.locationOn)
    val locationOn: StateFlow<Boolean> = _locationOn

    fun setNotifications(on: Boolean) {
        session.notifyOn = on
        _notifyOn.value = on
        com.churchdrive.app.house.House.sync(appContext)
    }

    fun setLocation(on: Boolean) {
        session.locationOn = on
        _locationOn.value = on
        com.churchdrive.app.house.House.sync(appContext)
    }

    /** The notify service for this phone (for an administrator to test with), once it's registered with Home Assistant. */
    val notifyService: String?
        get() = session.deviceName?.let { com.churchdrive.app.ha.MobileApp.notifyService(it) }

    /** Asks GitHub now whether a newer build is out; [done] gets the answer on the main thread. */
    fun checkUpdateNow(done: (Boolean) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            lastUpdateCheck = System.currentTimeMillis()
            val available = UpdateCheck.check(okhttp3.OkHttpClient(), BuildConfig.COMMIT)
            _updateAvailable.value = available
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { done(available) }
        }
    }

    private val _signedIn = MutableStateFlow(session.signedIn)
    val signedIn: StateFlow<Boolean> = _signedIn

    private val appContext = app

    init {
        // Templates are subscriptions, which end when the connection drops: start them again on reconnecting.
        viewModelScope.launch {
            client.connection.collect { if (it == ConnectionState.Connected) templates.restart() }
        }
    }

    init {
        // An administrator also reads the Manager dashboard, again whenever a dashboard is saved.
        viewModelScope.launch {
            combine(client.connection, client.isAdmin, client.dashboardTick) { connection, admin, _ -> connection == ConnectionState.Connected && admin }.collect { go ->
                if (go) fetch("lovelace/config", data("url_path" to "dashboard-manager")) { config ->
                    (config as? org.json.JSONObject)?.let { c ->
                        _managerPanels.value = DashboardPanels.parse(c).values.flatten()
                        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) { cache.write("manager", c.toString()) }
                    }
                }
            }
        }
    }

    init {
        // Read the dashboard's light cards on connecting, and again whenever a dashboard is saved in HA.
        viewModelScope.launch {
            combine(client.connection, client.dashboardTick) { connection, _ -> connection }.collect {
                if (it == ConnectionState.Connected) loadDashboard()
            }
        }
    }

    /** What was read last time, kept on the phone so pages show at once on opening while the live reading catches up. */
    private val cache = com.churchdrive.app.ha.DiskCache(app)

    /**
     * A command that tries again (a few times, a little slower each time) while the line is up, so a slow or dropped answer
     * does not leave a page empty. Its answer goes to [onResult] once it comes.
     */
    private fun fetch(type: String, params: org.json.JSONObject = org.json.JSONObject(), attempt: Int = 0, onResult: (Any?) -> Unit) {
        client.request(type, params) { result ->
            if (result != null) onResult(result)
            else if (attempt < 4 && client.connection.value == ConnectionState.Connected) {
                viewModelScope.launch {
                    kotlinx.coroutines.delay(2_000L * (attempt + 1))
                    fetch(type, params, attempt + 1, onResult)
                }
            }
        }
    }

    private fun applyDashboard(config: org.json.JSONObject) {
        _lights.value = DashboardLights.parse(config)
        _panels.value = DashboardPanels.parse(config)
    }

    /** The dashboard, registries and states as they were last time, shown straight away. */
    private fun loadCaches() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { cache.read("dashboard")?.let { org.json.JSONObject(it) } }.getOrNull()?.let { config ->
                val lights = DashboardLights.parse(config)
                val panels = DashboardPanels.parse(config)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    if (_panels.value.isEmpty()) { _lights.value = lights; _panels.value = panels }
                }
            }
            runCatching { cache.read("manager")?.let { org.json.JSONObject(it) } }.getOrNull()?.let { config ->
                val panels = DashboardPanels.parse(config).values.flatten()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { if (_managerPanels.value.isEmpty()) _managerPanels.value = panels }
            }
            runCatching { cache.read("areas")?.let { org.json.JSONArray(it) } }.getOrNull()?.let { areas ->
                val names = DashboardLights.areaNames(areas)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { if (_areaNames.value.isEmpty()) _areaNames.value = names }
            }
            runCatching { cache.read("registry")?.let { org.json.JSONObject(it) } }.getOrNull()?.let { reg ->
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    if (entityRegistryResult == null) {
                        entityRegistryResult = reg
                        _registry.value = Registry.parse(entityRegistryResult, deviceRegistryResult)
                    }
                }
            }
            runCatching {
                cache.read("states")?.let { text ->
                    val a = org.json.JSONArray(text)
                    HaClient.parseStates(a)
                }
            }.getOrNull()?.let { states -> kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { client.seed(states) } }
        }
        // The live states, saved now and then so the next opening has something to show.
        viewModelScope.launch {
            client.statesLoaded.collect { loaded ->
                if (loaded) {
                    kotlinx.coroutines.delay(5_000L)
                    while (client.statesLoaded.value) {
                        val snapshot = client.entities.value
                        withContextIO { cache.write("states", statesJson(snapshot)) }
                        kotlinx.coroutines.delay(60_000L)
                    }
                }
            }
        }
    }

    private suspend fun withContextIO(block: () -> Unit) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { block() }

    private fun statesJson(states: Map<String, EntityState>): String {
        val a = org.json.JSONArray()
        states.values.forEach { e ->
            a.put(org.json.JSONObject().put("entity_id", e.entityId).put("state", e.state).put("attributes", e.attributes).put("last_changed", e.lastChanged ?: ""))
        }
        return a.toString()
    }

    private fun loadDashboard() {
        fetch("lovelace/config", data("url_path" to DashboardLights.DASHBOARD)) { config ->
            (config as? org.json.JSONObject)?.let {
                applyDashboard(it)
                viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) { cache.write("dashboard", it.toString()) }
            }
        }
        fetch("config/area_registry/list") {
            _areaNames.value = DashboardLights.areaNames(it)
            (it as? org.json.JSONArray)?.let { a -> viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) { cache.write("areas", a.toString()) } }
        }
        // Which entities belong to which device (a smoke alarm's battery, for example) and the devices' names.
        fetch("config/entity_registry/list_for_display") {
            entityRegistryResult = it
            _registry.value = Registry.parse(entityRegistryResult, deviceRegistryResult)
            (it as? org.json.JSONObject)?.let { o -> viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) { cache.write("registry", o.toString()) } }
        }
        client.request("config/device_registry/list") {
            deviceRegistryResult = it
            _registry.value = Registry.parse(entityRegistryResult, deviceRegistryResult)
        }
        // The icons for modes (fan Sleep, thermostat Eco, charger Stop...) set in Home Assistant's Icon Styles card.
        fetch("church_drive/icons") { com.churchdrive.app.ui.IconMap.load(it) }
        // Each scene's colours and icon: Church Drive's scene library, and the looks set in the Scene Styles card.
        fetch("church_drive/library") {
            libraryResult = it
            _sceneLooks.value = SceneLooks.parse(libraryResult, stylesDashboard)
        }
        fetch("lovelace/config", data("url_path" to "design-presets")) {
            stylesDashboard = it as? org.json.JSONObject
            _sceneLooks.value = SceneLooks.parse(libraryResult, stylesDashboard)
        }
    }

    init {
        checkForUpdate()
        if (session.signedIn) {
            loadCaches()
            startSession()
            com.churchdrive.app.house.House.sync(appContext)
        }
        // The person's name, kept for naming this phone in Home Assistant.
        viewModelScope.launch { client.userName.collect { if (it != null) session.savePerson(it) } }
        // A username-and-password sign-in's access token lasts half an hour. If the connection is refused later
        // (the phone slept past that), get a new one from the refresh token and connect again.
        // Also when the app was opened just after waking (the network or the stored token not ready yet): try again a few times, a little slower each time.
        viewModelScope.launch {
            var failures = 0
            client.connection.collect {
                if (it == ConnectionState.Connected) failures = 0
                // Not given up on: Home Assistant may be restarting or the phone offline, and neither is the token's fault.
                if (it == ConnectionState.AuthFailed && session.refreshToken != null && session.signedIn) {
                    failures++
                    kotlinx.coroutines.delay((2_000L * failures).coerceAtMost(30_000L))
                    if (session.signedIn) startSession()
                }
            }
        }
    }

    private fun start(url: String, token: String) {
        client.connect(url, token)
        IconPack.load(viewModelScope, appContext.filesDir, url, token)
    }

    /** Connects with the stored sign-in, first making a fresh access token when there's a refresh token. */
    private var lastStart = 0L

    /** Whether a failed sign-in is tried again by itself (it is, when the phone has a refresh token). */
    val canRetrySignIn: Boolean get() = session.refreshToken != null

    /** The app came to the front: if it isn't connected, connect again now rather than waiting for the next try. */
    fun onForeground() {
        // Each time the app comes to the front, not just when it starts afresh (spaced out: see UpdateCheck.MIN_GAP_MS).
        if (UpdateCheck.due(System.currentTimeMillis(), lastUpdateCheck)) checkForUpdate()
        if (session.signedIn && client.connection.value != ConnectionState.Connected && System.currentTimeMillis() - lastStart > 5_000) startSession()
    }

    private fun startSession() {
        lastStart = System.currentTimeMillis()
        val url = session.url ?: return
        val refresh = session.refreshToken
        if (refresh == null) {
            start(url, session.token ?: return)
            return
        }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            when (val r = HaAuth.refresh(url, refresh)) {
                is Refresh.Fresh -> {
                    session.saveAccess(r.tokens.access)
                    start(url, r.tokens.access)
                }
                // Revoked in Home Assistant: back to the sign-in screen.
                Refresh.Rejected -> signOut()
                // Offline: try the last token; the next failure tries again.
                Refresh.Unreachable -> start(url, session.token ?: return@launch)
            }
        }
    }

    private var openFlow: String? = null
    private var flowUrl = ""

    /** Where the username-and-password sign-in is: null before it starts. [onStep] runs on the main thread. */
    fun login(url: String, username: String, password: String, onStep: (LoginStep) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            flowUrl = url
            val opened = HaAuth.start(url)
            val step = if (opened is LoginStep.Credentials) {
                openFlow = opened.flowId
                HaAuth.submit(url, opened.flowId, mapOf("username" to username.trim(), "password" to password))
            } else opened
            finishLogin(step, onStep)
        }
    }

    /** The two-step code, after [login] asked for it. */
    fun loginCode(code: String, onStep: (LoginStep) -> Unit) {
        val flow = openFlow ?: return onStep(LoginStep.Failed("Start again."))
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) { finishLogin(HaAuth.submit(flowUrl, flow, mapOf("code" to code.trim())), onStep) }
    }

    private suspend fun finishLogin(step: LoginStep, onStep: (LoginStep) -> Unit) {
        var result = step
        if (step is LoginStep.Mfa) openFlow = step.flowId
        if (step is LoginStep.Done) {
            val tokens = HaAuth.exchange(flowUrl, step.code)
            if (tokens == null) {
                result = LoginStep.Failed("Signed in, but Home Assistant wouldn't give the app access.")
            } else {
                session.saveSignIn(flowUrl, tokens.access, tokens.refresh)
                openFlow = null
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    start(flowUrl, tokens.access)
                    _signedIn.value = true
                }
            }
        }
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onStep(result) }
    }

    fun signIn(url: String, token: String) {
        session.save(url, token)
        start(url, token)
        _signedIn.value = true
    }

    fun signOut() {
        val url = session.url
        val refresh = session.refreshToken
        if (url != null && refresh != null) viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) { HaAuth.revoke(url, refresh) }
        client.disconnect()
        session.clear()
        cache.clear()
        // Nothing left to run: stops the notification connection and the location updates.
        com.churchdrive.app.house.House.sync(appContext)
        _notifyOn.value = false
        _locationOn.value = false
        _signedIn.value = false
    }

    /** History of some entities over the last [hours]: each one's (time in ms, state) list, for the zone strips. */
    fun history(ids: List<String>, hours: Int, done: (Map<String, List<Pair<Long, String>>>) -> Unit) {
        if (ids.isEmpty()) {
            done(emptyMap())
            return
        }
        val params = org.json.JSONObject()
            .put("start_time", java.time.Instant.now().minusSeconds(hours * 3600L).toString())
            .put("entity_ids", org.json.JSONArray(ids))
            .put("minimal_response", true)
            .put("no_attributes", true)
            .put("significant_changes_only", false)
        client.request("history/history_during_period", params) { done(com.churchdrive.app.ui.parseHistory(it)) }
    }

    /** Home Assistant's address, for pictures and video links. */
    val baseUrl: String get() = session.url?.trim()?.trimEnd('/').orEmpty()

    /** What the live view needs from Home Assistant: how a camera can be watched, and the WebRTC set-up. */
    val cameraHost = object : CameraHost {
        override fun capabilities(entityId: String, done: (List<String>) -> Unit) {
            client.request("camera/capabilities", data("entity_id" to entityId)) { result ->
                val types = (result as? org.json.JSONObject)?.optJSONArray("frontend_stream_types")
                done((0 until (types?.length() ?: 0)).map { types!!.getString(it) })
            }
        }

        override fun hlsStream(entityId: String, done: (String?) -> Unit) {
            client.request("camera/stream", data("entity_id" to entityId, "format" to "hls")) { result ->
                done((result as? org.json.JSONObject)?.optString("url")?.takeIf { it.isNotBlank() })
            }
        }

        override fun iceServers(entityId: String, done: (org.json.JSONArray?) -> Unit) {
            client.request("camera/webrtc/get_client_config", data("entity_id" to entityId)) { result ->
                done((result as? org.json.JSONObject)?.optJSONObject("configuration")?.optJSONArray("iceServers"))
            }
        }

        override fun webRtcOffer(entityId: String, sdp: String, onEvent: (org.json.JSONObject) -> Unit): Int =
            client.subscribe("camera/webrtc/offer", data("entity_id" to entityId, "offer" to sdp), onEvent)

        override fun webRtcCandidate(entityId: String, sessionId: String, candidate: org.json.JSONObject) {
            client.request(
                "camera/webrtc/candidate",
                data("entity_id" to entityId, "session_id" to sessionId, "candidate" to candidate),
            ) { }
        }

        override fun close(subscription: Int) = client.unsubscribe(subscription)

        override fun events(base: String, done: (org.json.JSONObject?) -> Unit) {
            client.request("church_drive/camera/events", data("camera" to base)) { done(it as? org.json.JSONObject) }
        }
    }

    /** Plain access to Home Assistant's commands, for the cards that fetch their own data. */
    val haApi = object : com.churchdrive.app.ui.HaApi {
        override fun request(type: String, params: org.json.JSONObject, done: (Any?) -> Unit) = client.request(type, params) { done(it) }

        override fun subscribe(type: String, params: org.json.JSONObject, onEvent: (org.json.JSONObject) -> Unit): Int =
            client.subscribe(type, params, onEvent)

        override fun close(subscription: Int) = client.unsubscribe(subscription)

        override val connected: Boolean get() = client.connection.value == com.churchdrive.app.ha.ConnectionState.Connected

        override val lastErrorCode: String? get() = client.lastErrorCode
    }

    fun call(domain: String, service: String, entityId: String, data: org.json.JSONObject) =
        client.callService(domain, service, entityId, data)

    companion object {
    }
}
