package com.churchdrive.app.ha

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Minimal Home Assistant WebSocket client: authenticates with a long-lived token,
 * keeps a live map of entity states, calls services, and reconnects with backoff.
 */
class HaClient(private val scope: CoroutineScope) {
    private val http = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    private val _connection = MutableStateFlow(ConnectionState.Disconnected)
    val connection: StateFlow<ConnectionState> = _connection

    private val _entities = MutableStateFlow<Map<String, EntityState>>(emptyMap())
    val entities: StateFlow<Map<String, EntityState>> = _entities

    /** True once Home Assistant has sent every entity's state (before that, [entities] holds only what has changed since connecting). */
    private val _statesLoaded = MutableStateFlow(false)
    val statesLoaded: StateFlow<Boolean> = _statesLoaded

    private val _userName = MutableStateFlow<String?>(null)
    val userName: StateFlow<String?> = _userName
    private val _refreshing = MutableStateFlow(false)

    /** When every state was last read in full (the line's first answer, or a refresh), in milliseconds; 0 until then and while a new line loads. */
    @Volatile var lastSyncAt = 0L
        private set

    /** Whether the last [refreshStates] found anything different from what the live events had left, which is a sign something went stale. */
    @Volatile var lastRefreshChanged = false
        private set
    private val _restarting = MutableStateFlow(false)

    /**
     * True when Home Assistant has said it is restarting (it closed the line itself, or reported it is not running yet) and its
     * states have not all come back. It clears itself when they do, and after five minutes in case they never do.
     */
    val restarting: StateFlow<Boolean> = _restarting

    /** Everything the status pill is drawn from, in one value. */
    val link: StateFlow<LinkStatus> = kotlinx.coroutines.flow.combine(_connection, _statesLoaded, _refreshing, _restarting) { c, s, r, x ->
        LinkStatus(c, s, r, x)
    }.stateIn(scope, kotlinx.coroutines.flow.SharingStarted.Eagerly, LinkStatus())

    private fun noteRestarting() {
        if (_restarting.value) return
        _restarting.value = true
        scope.launch {
            delay(300_000L)
            _restarting.value = false
        }
    }

    /** True while [refreshUser] waits for an answer (the app checking its line after being away). */
    val refreshing: StateFlow<Boolean> = _refreshing

    /** Whether the signed-in person is a Home Assistant administrator (`auth/current_user`). */
    private val _isAdmin = MutableStateFlow(false)
    val isAdmin: StateFlow<Boolean> = _isAdmin

    /** Counts `lovelace_updated` events (a dashboard was saved in Home Assistant) and icon changes. */
    private val _dashboardTick = MutableStateFlow(0)
    val dashboardTick: StateFlow<Int> = _dashboardTick

    private val pending = java.util.concurrent.ConcurrentHashMap<Int, (Any?) -> Unit>()
    private val subscriptions = java.util.concurrent.ConcurrentHashMap<Int, (JSONObject) -> Unit>()

    private var socket: WebSocket? = null

    /**
     * True only between Home Assistant accepting the sign-in (`auth_ok`) and the line closing. A command sent before that is
     * read by Home Assistant as a badly formed sign-in: it refuses it and drops the connection, which looked like "Can't
     * sign in just now" and made whatever asked fail. So nothing is sent until this is true.
     */
    @Volatile
    private var authed = false

    /** Why the last command Home Assistant refused was refused (its error code, e.g. `unknown_command`), or null. */
    @Volatile
    var lastErrorCode: String? = null
        private set
    private var job: Job? = null
    private var nextId = 1

    /** The colours sensor's revision last read, so the palettes are fetched again only when Home Assistant says they changed. */
    @Volatile
    private var colourRevision: String? = null
    private var url = ""
    private var token = ""

    /** A light connection (the background service's): no entity states, no dashboard events, just what it asks for. */
    private var light = false

    @Synchronized
    private fun id() = nextId++

    /** The id of the latest get_states ask, so its answer is told from the others. */
    @Volatile private var getStatesId = -1

    /**
     * The house's states are asked for once when the line opens; if that answer never comes (or comes back as an error) the app would sit
     * "connected" with nothing to show. Ask again every ten seconds, and after three silent tries drop the line so it reconnects fresh.
     */
    private fun watchStates(webSocket: WebSocket) {
        scope.launch {
            var tries = 0
            while (authed && socket === webSocket && !_statesLoaded.value) {
                delay(10_000)
                if (_statesLoaded.value || !authed || socket !== webSocket) break
                tries++
                if (tries >= 3) {
                    webSocket.close(4000, "no states")
                    break
                }
                getStatesId = id()
                webSocket.send(JSONObject().put("id", getStatesId).put("type", "get_states").toString())
            }
        }
    }

    fun connect(baseUrl: String, accessToken: String, light: Boolean = false) {
        url = baseUrl
        token = accessToken
        this.light = light
        job?.cancel()
        socket?.close(1000, null)
        job = scope.launch {
            var backoff = 1_000L
            while (true) {
                _connection.value = ConnectionState.Connecting
                authed = false
                val closed = kotlinx.coroutines.CompletableDeferred<Unit>()
                socket = http.newWebSocket(Request.Builder().url(websocketUrl(url)).build(), Listener(closed))
                closed.await()
                if (_connection.value == ConnectionState.AuthFailed) return@launch
                _connection.value = ConnectionState.Disconnected
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(30_000L)
            }
        }
    }

    fun disconnect() {
        job?.cancel()
        authed = false
        socket?.close(1000, null)
        socket = null
        _connection.value = ConnectionState.Disconnected
        _entities.value = emptyMap()
        _userName.value = null
        _isAdmin.value = false
        _statesLoaded.value = false
        lastSyncAt = 0L
        _restarting.value = false
    }

    /**
     * Sends any WebSocket command and gives its result (a JSONObject or JSONArray) to [onResult],
     * or null if it failed (for example, an admin-only command asked by a non-admin).
     */
    fun request(type: String, params: JSONObject = JSONObject(), timeoutMs: Long = 30_000L, onResult: (Any?) -> Unit) {
        val ws = socket
        if (ws == null || !authed) {
            onResult(null)
            return
        }
        val id = id()
        pending[id] = onResult
        // An answer that never comes (a slow house, a dropped line) is a failure the caller can try again, not a page left empty.
        scope.launch {
            delay(timeoutMs)
            pending.remove(id)?.invoke(null)
        }
        val msg = JSONObject(params.toString()).put("id", id).put("type", type)
        if (!ws.send(msg.toString())) pending.remove(id)?.invoke(null)
    }

    /** Shows the name remembered from last time until the live one arrives, so the account button is not a "?". */
    fun seedUserName(name: String?) {
        if (_userName.value == null && !name.isNullOrBlank()) _userName.value = name
    }

    /**
     * Asks who is signed in (which also proves the line is alive) and keeps the name and admin flag.
     * [onResult] is false when nothing answered: the connection looks open but is dead, so the caller should connect again.
     */
    fun refreshUser(onResult: (Boolean) -> Unit = {}) {
        _refreshing.value = true
        request("auth/current_user", timeoutMs = 5_000L) { result ->
            _refreshing.value = false
            val me = result as? JSONObject
            if (me != null) {
                me.optString("name").takeIf { it.isNotBlank() }?.let { _userName.value = it }
                _isAdmin.value = me.optBoolean("is_admin", false)
            }
            onResult(me != null)
        }
    }

    /**
     * Pull down to refresh: asks for every state again (which also proves the line is alive).
     * [onDone] is false when nothing answered, so the caller should connect afresh.
     */
    fun refreshStates(quiet: Boolean = false, onDone: (Boolean) -> Unit = {}) {
        if (light) { refreshUser(onDone); return }
        if (!authed) { onDone(false); return }
        // A quiet read (the five-minute check) shows no pill unless it finds that something had gone stale.
        if (!quiet) _refreshing.value = true
        request("get_states", timeoutMs = 8_000L) { result ->
            val states = result as? JSONArray
            var changed = false
            if (states != null) {
                val fresh = parseStates(states)
                changed = statesDiffer(_entities.value, fresh)
                _entities.value = fresh
                _statesLoaded.value = true
                lastSyncAt = System.currentTimeMillis()
            }
            lastRefreshChanged = changed
            if (quiet && changed) {
                // It found something stale and put it right: show the pill for a moment so that can be seen.
                _refreshing.value = true
                scope.launch { delay(900L); _refreshing.value = false }
            } else _refreshing.value = false
            onDone(states != null)
        }
    }

    /** Shows what was known last time until the live states arrive (and only until then). */
    fun seed(states: Map<String, EntityState>) {
        if (!_statesLoaded.value && _entities.value.isEmpty()) _entities.value = states
    }

    /**
     * Starts a streaming command (such as render_template) and passes each event it sends to [onEvent].
     * Returns the subscription id, or -1 when not connected. Subscriptions end when the connection drops,
     * so callers start them again after reconnecting.
     */
    fun subscribe(type: String, params: JSONObject, onEvent: (JSONObject) -> Unit): Int {
        val ws = socket ?: return -1
        if (!authed) return -1
        val id = id()
        subscriptions[id] = onEvent
        val msg = JSONObject(params.toString()).put("id", id).put("type", type)
        if (!ws.send(msg.toString())) {
            subscriptions.remove(id)
            return -1
        }
        return id
    }

    /**
     * Sends a piece of microphone audio (16 kHz, 16-bit, mono) to a running voice pipeline: a binary message that starts with the
     * pipeline's handler id. A piece with no audio ([length] 0) tells it the speech is over. False if it couldn't be sent.
     */
    fun sendAudio(handlerId: Int, chunk: ByteArray, length: Int): Boolean {
        val ws = socket ?: return false
        if (!authed) return false
        val frame = ByteArray(length + 1)
        frame[0] = handlerId.toByte()
        System.arraycopy(chunk, 0, frame, 1, length)
        return ws.send(okio.ByteString.of(*frame))
    }

    fun unsubscribe(subscriptionId: Int) {
        if (subscriptions.remove(subscriptionId) != null && authed) {
            socket?.send(
                JSONObject().put("id", id()).put("type", "unsubscribe_events").put("subscription", subscriptionId).toString(),
            )
        }
    }

    fun callService(domain: String, service: String, entityId: String, data: JSONObject = JSONObject()) {
        if (!authed) return
        val msg = JSONObject()
            .put("id", id())
            .put("type", "call_service")
            .put("domain", domain)
            .put("service", service)
            .put("service_data", data)
            .put("target", JSONObject().put("entity_id", entityId))
        socket?.send(msg.toString())
    }

    private inner class Listener(private val closed: kotlinx.coroutines.CompletableDeferred<Unit>) : WebSocketListener() {
        private var configId = -1
        private var userId = -1

        override fun onMessage(webSocket: WebSocket, text: String) {
            val msg = JSONObject(text)
            when (msg.optString("type")) {
                "auth_required" -> webSocket.send(
                    JSONObject().put("type", "auth").put("access_token", token).toString(),
                )
                "auth_ok" -> {
                    authed = true
                    // Asks still waiting from before the line dropped will not be answered: tell their askers so they can ask again.
                    val lost = pending.values.toList()
                    pending.clear()
                    lost.forEach { it(null) }
                    subscriptions.clear()
                    colourRevision = null
                    lastSyncAt = 0L
                    _statesLoaded.value = false
                    _connection.value = ConnectionState.Connected
                    if (light) return
                    webSocket.send(
                        JSONObject().put("id", id()).put("type", "subscribe_events")
                            .put("event_type", "state_changed").toString(),
                    )
                    webSocket.send(
                        JSONObject().put("id", id()).put("type", "subscribe_events")
                            .put("event_type", "lovelace_updated").toString(),
                    )
                    userId = id()
                    webSocket.send(JSONObject().put("id", userId).put("type", "auth/current_user").toString())
                    getStatesId = id()
                    webSocket.send(JSONObject().put("id", getStatesId).put("type", "get_states").toString())
                    watchStates(webSocket)
                    // The home's time zone, so times can be shown in the home's time as well as the device's when the two differ.
                    configId = id()
                    webSocket.send(JSONObject().put("id", configId).put("type", "get_config").toString())
                }
                "auth_invalid" -> {
                    _connection.value = ConnectionState.AuthFailed
                    webSocket.close(1000, null)
                }
                "result" -> {
                    val callback = pending.remove(msg.optInt("id"))
                    if (callback != null) {
                        val ok = msg.optBoolean("success")
                        lastErrorCode = if (ok) null else msg.optJSONObject("error")?.optString("code")?.takeIf { it.isNotBlank() } ?: "error"
                        callback(if (ok) msg.opt("result") else null)
                    } else if (msg.optBoolean("success")) when (msg.optInt("id")) {
                        getStatesId -> {
                            _entities.value = parseStates(msg.getJSONArray("result"))
                            _statesLoaded.value = true
                            lastSyncAt = System.currentTimeMillis()
                            _restarting.value = false
                            noteColours(_entities.value[COLOURS_ENTITY]?.state)
                        }
                        configId -> {
                            val config = msg.optJSONObject("result")
                            com.churchdrive.app.ui.HomeTime.zoneId = config?.optString("time_zone")?.takeIf { it.isNotBlank() }
                            // Still starting up (its integrations are loading): the states that arrive now are not the whole house yet.
                            val running = config?.optString("state", "RUNNING") ?: "RUNNING"
                            if (running != "RUNNING") noteRestarting()
                        }
                        userId -> {
                            val me = msg.optJSONObject("result")
                            me?.optString("name")?.takeIf { it.isNotBlank() }?.let { _userName.value = it }
                            _isAdmin.value = me?.optBoolean("is_admin", false) ?: false
                            // Home Assistant lets only administrators subscribe to this event; everyone else would be refused (and it is logged as an error).
                            if (_isAdmin.value) webSocket.send(
                                JSONObject().put("id", id()).put("type", "subscribe_events")
                                    .put("event_type", "church_drive_icons_changed").toString(),
                            )
                        }
                    }
                }
                "event" -> {
                    val subscriber = subscriptions[msg.optInt("id")]
                    if (subscriber != null) subscriber(msg.getJSONObject("event")) else applyEvent(msg.getJSONObject("event"))
                }
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            // 1001 "going away" is what Home Assistant sends when it shuts down; 1012 and 1013 are a restart and "try again later".
            if (authed && (code == 1001 || code == 1012 || code == 1013)) noteRestarting()
            webSocket.close(code, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            authed = false
            closed.complete(Unit)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            authed = false
            closed.complete(Unit)
        }
    }

    /** The palettes changed in Home Assistant (or this is the first look): fetch them and show them now. Every person gets this, not only administrators. */
    private fun noteColours(revision: String?) {
        if (revision.isNullOrBlank() || revision == "unknown" || revision == "unavailable" || revision == colourRevision) return
        colourRevision = revision
        request("church_drive/colours") { result ->
            (result as? JSONObject)?.optJSONObject("palettes")?.let { com.churchdrive.app.ui.LiveColours.apply(it) }
        }
    }

    private fun applyEvent(event: JSONObject) {
        // A dashboard was saved, or an icon was changed in the Icon Styles card: read them again.
        if (event.optString("event_type") == "lovelace_updated" || event.optString("event_type") == "church_drive_icons_changed") {
            _dashboardTick.update { it + 1 }
            return
        }
        val data = event.optJSONObject("data") ?: return
        val entityId = data.optString("entity_id")
        val new = data.optJSONObject("new_state")
        if (entityId == COLOURS_ENTITY) noteColours(new?.optString("state"))
        _entities.update { current ->
            if (new == null) current - entityId else current + (entityId to EntityState.fromJson(new))
        }
    }

    companion object {
        /** The sensor whose state is the palettes' revision (colours.py). */
        const val COLOURS_ENTITY = "sensor.church_drive_colours"

        fun websocketUrl(base: String): String {
            val trimmed = base.trim().trimEnd('/')
            val ws = when {
                trimmed.startsWith("https://") -> "wss://" + trimmed.removePrefix("https://")
                trimmed.startsWith("http://") -> "ws://" + trimmed.removePrefix("http://")
                else -> "ws://$trimmed"
            }
            return "$ws/api/websocket"
        }

        fun parseStates(arr: JSONArray): Map<String, EntityState> =
            (0 until arr.length()).map { EntityState.fromJson(arr.getJSONObject(it)) }
                .associateBy { it.entityId }
    }
}
