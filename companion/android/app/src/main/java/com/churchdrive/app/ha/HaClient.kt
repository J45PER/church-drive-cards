package com.churchdrive.app.ha

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

    /** Whether the signed-in person is a Home Assistant administrator (`auth/current_user`). */
    private val _isAdmin = MutableStateFlow(false)
    val isAdmin: StateFlow<Boolean> = _isAdmin

    /** Counts `lovelace_updated` events (a dashboard was saved in Home Assistant) and icon changes. */
    private val _dashboardTick = MutableStateFlow(0)
    val dashboardTick: StateFlow<Int> = _dashboardTick

    private val pending = java.util.concurrent.ConcurrentHashMap<Int, (Any?) -> Unit>()
    private val subscriptions = java.util.concurrent.ConcurrentHashMap<Int, (JSONObject) -> Unit>()

    private var socket: WebSocket? = null
    private var job: Job? = null
    private var nextId = 1
    private var url = ""
    private var token = ""

    /** A light connection (the background service's): no entity states, no dashboard events, just what it asks for. */
    private var light = false

    @Synchronized
    private fun id() = nextId++

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
        socket?.close(1000, null)
        socket = null
        _connection.value = ConnectionState.Disconnected
        _entities.value = emptyMap()
        _userName.value = null
        _isAdmin.value = false
        _statesLoaded.value = false
    }

    /**
     * Sends any WebSocket command and gives its result (a JSONObject or JSONArray) to [onResult],
     * or null if it failed (for example, an admin-only command asked by a non-admin).
     */
    fun request(type: String, params: JSONObject = JSONObject(), timeoutMs: Long = 30_000L, onResult: (Any?) -> Unit) {
        val ws = socket
        if (ws == null) {
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
        val id = id()
        subscriptions[id] = onEvent
        val msg = JSONObject(params.toString()).put("id", id).put("type", type)
        if (!ws.send(msg.toString())) {
            subscriptions.remove(id)
            return -1
        }
        return id
    }

    fun unsubscribe(subscriptionId: Int) {
        if (subscriptions.remove(subscriptionId) != null) {
            socket?.send(
                JSONObject().put("id", id()).put("type", "unsubscribe_events").put("subscription", subscriptionId).toString(),
            )
        }
    }

    fun callService(domain: String, service: String, entityId: String, data: JSONObject = JSONObject()) {
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
        private var getStatesId = -1
        private var userId = -1

        override fun onMessage(webSocket: WebSocket, text: String) {
            val msg = JSONObject(text)
            when (msg.optString("type")) {
                "auth_required" -> webSocket.send(
                    JSONObject().put("type", "auth").put("access_token", token).toString(),
                )
                "auth_ok" -> {
                    // Asks still waiting from before the line dropped will not be answered: tell their askers so they can ask again.
                    val lost = pending.values.toList()
                    pending.clear()
                    lost.forEach { it(null) }
                    subscriptions.clear()
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
                }
                "auth_invalid" -> {
                    _connection.value = ConnectionState.AuthFailed
                    webSocket.close(1000, null)
                }
                "result" -> {
                    val callback = pending.remove(msg.optInt("id"))
                    if (callback != null) {
                        callback(if (msg.optBoolean("success")) msg.opt("result") else null)
                    } else if (msg.optBoolean("success")) when (msg.optInt("id")) {
                        getStatesId -> {
                            _entities.value = parseStates(msg.getJSONArray("result"))
                            _statesLoaded.value = true
                        }
                        userId -> {
                            val me = msg.optJSONObject("result")
                            _userName.value = me?.optString("name")?.takeIf { it.isNotBlank() }
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

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            closed.complete(Unit)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            closed.complete(Unit)
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
        _entities.update { current ->
            if (new == null) current - entityId else current + (entityId to EntityState.fromJson(new))
        }
    }

    companion object {
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
