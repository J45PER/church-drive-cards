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

    private val _registry = MutableStateFlow(Registry.Empty)
    val registry: StateFlow<Registry> = _registry
    private var entityRegistryResult: Any? = null
    private var deviceRegistryResult: Any? = null

    private val _areaNames = MutableStateFlow<Map<String, String>>(emptyMap())
    val areaNames: StateFlow<Map<String, String>> = _areaNames

    /** A newer test build has been published (checked each time the app opens). */
    private val _updateAvailable = MutableStateFlow(false)
    val updateAvailable: StateFlow<Boolean> = _updateAvailable

    private fun checkForUpdate() {
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
        com.churchdrive.app.house.HouseService.sync(appContext)
    }

    fun setLocation(on: Boolean) {
        session.locationOn = on
        _locationOn.value = on
        com.churchdrive.app.house.HouseService.sync(appContext)
    }

    /** The notify service for this phone (for an administrator to test with), once it's registered with Home Assistant. */
    val notifyService: String?
        get() = session.deviceName?.let { com.churchdrive.app.ha.MobileApp.notifyService(it) }

    /** Asks GitHub now whether a newer build is out; [done] gets the answer on the main thread. */
    fun checkUpdateNow(done: (Boolean) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
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
        // Read the dashboard's light cards on connecting, and again whenever a dashboard is saved in HA.
        viewModelScope.launch {
            combine(client.connection, client.dashboardTick) { connection, _ -> connection }.collect {
                if (it == ConnectionState.Connected) loadDashboard()
            }
        }
    }

    private fun loadDashboard() {
        client.request("lovelace/config", data("url_path" to DashboardLights.DASHBOARD)) { config ->
            (config as? org.json.JSONObject)?.let {
                _lights.value = DashboardLights.parse(it)
                _panels.value = DashboardPanels.parse(it)
            }
        }
        client.request("config/area_registry/list") { _areaNames.value = DashboardLights.areaNames(it) }
        // Which entities belong to which device (a smoke alarm's battery, for example) and the devices' names.
        client.request("config/entity_registry/list_for_display") {
            entityRegistryResult = it
            _registry.value = Registry.parse(entityRegistryResult, deviceRegistryResult)
        }
        client.request("config/device_registry/list") {
            deviceRegistryResult = it
            _registry.value = Registry.parse(entityRegistryResult, deviceRegistryResult)
        }
        // The icons for modes (fan Sleep, thermostat Eco, charger Stop...) set in Home Assistant's Icon Styles card.
        client.request("church_drive/icons") { com.churchdrive.app.ui.IconMap.load(it) }
        // Each scene's colours and icon: Church Drive's scene library, and the looks set in the Scene Styles card.
        client.request("church_drive/library") {
            libraryResult = it
            _sceneLooks.value = SceneLooks.parse(libraryResult, stylesDashboard)
        }
        client.request("lovelace/config", data("url_path" to "design-presets")) {
            stylesDashboard = it as? org.json.JSONObject
            _sceneLooks.value = SceneLooks.parse(libraryResult, stylesDashboard)
        }
    }

    init {
        checkForUpdate()
        if (session.signedIn) {
            startSession()
            com.churchdrive.app.house.HouseService.sync(appContext)
        }
        // The person's name, kept for naming this phone in Home Assistant.
        viewModelScope.launch { client.userName.collect { if (it != null) session.savePerson(it) } }
        // A username-and-password sign-in's access token lasts half an hour. If the connection is refused later
        // (the phone slept past that), get a new one from the refresh token and connect again.
        viewModelScope.launch {
            client.connection.collect {
                if (it == ConnectionState.AuthFailed && session.refreshToken != null && session.signedIn &&
                    System.currentTimeMillis() - lastStart > 30_000
                ) startSession()
            }
        }
    }

    private fun start(url: String, token: String) {
        client.connect(url, token)
        IconPack.load(viewModelScope, appContext.filesDir, url, token)
    }

    /** Connects with the stored sign-in, first making a fresh access token when there's a refresh token. */
    private var lastStart = 0L

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
        appContext.stopService(android.content.Intent(appContext, com.churchdrive.app.house.HouseService::class.java))
        client.disconnect()
        session.clear()
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
        override fun request(type: String, params: org.json.JSONObject, done: (Any?) -> Unit) = client.request(type, params, done)

        override fun subscribe(type: String, params: org.json.JSONObject, onEvent: (org.json.JSONObject) -> Unit): Int =
            client.subscribe(type, params, onEvent)

        override fun close(subscription: Int) = client.unsubscribe(subscription)
    }

    fun call(domain: String, service: String, entityId: String, data: org.json.JSONObject) =
        client.callService(domain, service, entityId, data)

    companion object {
    }
}
