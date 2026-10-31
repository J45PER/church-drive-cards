package com.churchdrive.satellites

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
import com.churchdrive.app.ha.data
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Sign-in, the line to Home Assistant, the satellites in the house and the changes made to them. */
class SatelliteModel(app: Application) : AndroidViewModel(app) {
    private val session = Session(app)
    private val client = HaClient(viewModelScope)
    private val settings = SatelliteSettings(app)
    private val presetStore = SatellitePresets(app)

    var signedIn by mutableStateOf(session.signedIn)
        private set
    var connected by mutableStateOf(false)
        private set
    /** Signed in through the Church Drive app: signing out is done there. */
    var borrowed by mutableStateOf(session.borrowed)
        private set
    var personName by mutableStateOf(session.personName)
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

    /** The satellites found in the house, newest state. */
    var satellites by mutableStateOf<List<Satellite>>(emptyList())
        private set

    /** The slug of the satellite whose page is open, or null for the list. */
    var openSlug by mutableStateOf<String?>(if (settings.rememberSatellite) settings.lastSatellite else null)
    var previewState by mutableStateOf(PreviewState.Resting)
    /** A design tapped but not yet confirmed by the house, so the preview follows the finger at once. */
    var pendingDesign by mutableStateOf<SatDesign?>(null)
        private set

    /** Settings > Open on the satellite I left. */
    var rememberSatellite by mutableStateOf(settings.rememberSatellite)
        private set

    fun changeRememberSatellite(on: Boolean) {
        rememberSatellite = on
        settings.rememberSatellite = on
        settings.lastSatellite = if (on) openSlug else null
    }

    /** Called as pages change, so the last one looked at can be reopened. */
    fun noteOpen(slug: String?) { if (rememberSatellite) settings.lastSatellite = slug }

    val open: Satellite? get() = satellites.firstOrNull { it.slug == openSlug }

    init {
        viewModelScope.launch {
            client.userName.collect {
                if (it != null) {
                    session.savePerson(it)
                    personName = it
                }
            }
        }
        viewModelScope.launch {
            client.entities.collect { states -> satellites = findSatellites(states) }
        }
        viewModelScope.launch {
            var failures = 0
            client.connection.collect { state ->
                connected = state == ConnectionState.Connected
                if (connected) failures = 0
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

    /** The app came to the front: pick up the Church Drive app's sign-in, and connect again if the line has gone. */
    fun onForeground() {
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
        satellites = emptyList()
        openSlug = null
    }

    // ---- Changing a satellite: each is one Home Assistant service call on one of its entities ----

    fun chooseDesign(sat: Satellite, design: SatDesign) {
        pendingDesign = design
        client.callService("select", "select_option", sat.select("design"), data("option" to design.label))
        viewModelScope.launch {
            delay(4_000)
            pendingDesign = null
        }
    }

    fun chooseGlance(sat: Satellite, slot: Int, option: String) =
        client.callService("select", "select_option", sat.select("glance_${slot + 1}"), data("option" to option))

    fun setNumber(sat: Satellite, suffix: String, value: Float) =
        client.callService("number", "set_value", sat.number(suffix), data("value" to value.toDouble()))

    /** Presses one of the satellite's buttons (restart, identify, clean_screen). */
    fun press(sat: Satellite, suffix: String) = client.callService("button", "press", sat.button(suffix))

    /** Makes the satellite say a short line, to hear it and to find out which one it is. */
    fun sayHello(sat: Satellite) =
        client.callService("assist_satellite", "announce", "assist_satellite.${sat.slug}", data("message" to "Hello, this is ${sat.name}. Can you hear me?", "preannounce" to false))

    fun installUpdate(sat: Satellite) {
        val u = sat.update ?: return
        client.callService("update", "install", u.entity)
    }

    /** A short line to show the person after something that may be refused, such as renaming without administrator rights. */
    var notice by mutableStateOf<String?>(null)

    /** The rooms (areas) in the house, by id and name, and which one each satellite is in; loaded when a satellite is opened. */
    var areas by mutableStateOf<List<Pair<String, String>>>(emptyList())
        private set
    var roomOf by mutableStateOf<Map<String, String?>>(emptyMap())
        private set
    private val deviceOf = mutableMapOf<String, String>()

    /** Finds the satellite's device and area in Home Assistant's registries (they need an administrator). */
    fun loadRoom(sat: Satellite) {
        client.request("config/area_registry/list") { res ->
            val arr = res as? org.json.JSONArray ?: return@request
            areas = (0 until arr.length()).map { arr.getJSONObject(it) }.map { it.optString("area_id") to it.optString("name") }.sortedBy { it.second.lowercase() }
        }
        client.request("config/entity_registry/get", org.json.JSONObject().put("entity_id", sat.select("design"))) { res ->
            val device = (res as? org.json.JSONObject)?.optString("device_id").orEmpty().ifBlank { return@request }
            deviceOf[sat.slug] = device
            client.request("config/device_registry/list") { list ->
                val arr = list as? org.json.JSONArray ?: return@request
                val dev = (0 until arr.length()).map { arr.getJSONObject(it) }.firstOrNull { it.optString("id") == device } ?: return@request
                roomOf = roomOf + (sat.slug to dev.optString("area_id").ifBlank { null })
            }
        }
    }

    fun setRoom(sat: Satellite, areaId: String?) {
        val device = deviceOf[sat.slug] ?: run { notice = "Home Assistant wouldn't say which device this is."; return }
        client.request("config/device_registry/update", org.json.JSONObject().put("device_id", device).put("area_id", areaId ?: org.json.JSONObject.NULL)) { res ->
            if (res == null) notice = "Home Assistant refused the change. Moving a satellite to a room needs an administrator."
            else roomOf = roomOf + (sat.slug to areaId)
        }
    }

    fun rename(sat: Satellite, name: String) {
        val device = deviceOf[sat.slug] ?: run { notice = "Home Assistant wouldn't say which device this is."; return }
        client.request("config/device_registry/update", org.json.JSONObject().put("device_id", device).put("name_by_user", name.trim())) { res ->
            if (res == null) notice = "Home Assistant refused the change. Renaming needs an administrator."
            else notice = "Renamed. The entity ids stay the same, so nothing else changes."
        }
    }

    // ---- Pairing: scan, send the Wi-Fi over Bluetooth, add to Home Assistant, wait for it to appear ----

    sealed interface PairStep {
        data object Idle : PairStep
        data class Scanned(val code: PairCode) : PairStep
        data class Working(val message: String) : PairStep
        data class Done(val slug: String?, val note: String?) : PairStep
        data class Failed(val message: String) : PairStep
    }

    var pairStep by mutableStateOf<PairStep>(PairStep.Idle)
        private set
    var wifiName by mutableStateOf(settings.wifiName)
    var apiKey by mutableStateOf(settings.apiKey)

    fun pairScanned(code: PairCode) { pairStep = PairStep.Scanned(code) }
    fun pairReset() { pairStep = PairStep.Idle }

    fun pair(code: PairCode, ssid: String, password: String, key: String) {
        settings.wifiName = ssid.trim()
        settings.apiKey = key.trim()
        wifiName = ssid.trim()
        apiKey = key.trim()
        val known = satellites.map { it.slug }.toSet()
        viewModelScope.launch(Dispatchers.IO) {
            fun say(m: String) { pairStep = PairStep.Working(m) }
            say("Starting…")
            val ble = withContext(Dispatchers.Main) { ImprovBle(getApplication()) }
            val sent = ble.provision(code.device, ssid.trim(), password, ::say)
            if (sent.error != null) { pairStep = PairStep.Failed(sent.error); return@launch }

            say("Adding ${code.device} to Home Assistant…")
            val url = session.url
            var added = AddOutcome("Not signed in.")
            // The satellite needs a little time to join the Wi-Fi and announce itself, so the first tries may not find it.
            for (attempt in 1..6) {
                val token = session.freshToken()
                if (url == null || token == null) break
                added = AddToHouse.add(url, token, "${code.device}.local", key.trim())
                if (added.error == null) break
                if (!(added.error!!.contains("find") || added.error!!.contains("reach"))) break
                delay(8_000)
            }
            if (added.error != null) { pairStep = PairStep.Failed(added.error!!); return@launch }

            say("Waiting for it to appear…")
            var slug: String? = null
            for (i in 1..30) {
                slug = satellites.firstOrNull { it.slug !in known }?.slug
                if (slug != null) break
                delay(2_000)
            }
            pairStep = PairStep.Done(
                slug,
                if (added.alreadyThere) "Home Assistant already had it. Its Wi-Fi is updated." else if (slug == null) "Added. It will show on the list when it has finished starting." else null,
            )
        }
    }

    /** Removes the satellite from Home Assistant (its ESPHome entry), and from the list. */
    fun remove(sat: Satellite) {
        client.request("config/entity_registry/get", org.json.JSONObject().put("entity_id", sat.select("design").takeIf { sat.hasScreen } ?: "switch.${sat.slug}_follow_up_listening")) { res ->
            val entry = (res as? org.json.JSONObject)?.optString("config_entry_id").orEmpty()
            val url = session.url
            if (entry.isBlank() || url == null) { notice = "Home Assistant wouldn't say how this satellite was added."; return@request }
            viewModelScope.launch(Dispatchers.IO) {
                val token = session.freshToken()
                val ok = token != null && AddToHouse.remove(url, token, entry)
                withContext(Dispatchers.Main) {
                    if (ok) { notice = "${sat.name} was removed from Home Assistant."; openSlug = null }
                    else notice = "Home Assistant refused. Removing a device needs an administrator."
                }
            }
        }
    }

    fun chooseVoice(setting: VoiceSetting, option: String) =
        client.callService("select", "select_option", setting.entity, data("option" to option))

    // ---- Presets, and one satellite's settings on others ----

    var presets by mutableStateOf(presetStore.all())
        private set

    fun savePreset(name: String, from: Satellite) {
        if (name.isBlank()) return
        presets = presetStore.save(Preset(name.trim(), SatSettings.of(from)))
        notice = "Saved \"${name.trim()}\"."
    }

    fun deletePreset(name: String) {
        presets = presetStore.delete(name)
    }

    /** Applies settings to a satellite, only the parts it has (a satellite without a screen ignores the design and backlight). */
    fun apply(settings: SatSettings, to: Satellite) {
        if (to.hasScreen) {
            settings.design?.let { client.callService("select", "select_option", to.select("design"), data("option" to it.label)) }
            settings.glances.forEachIndexed { i, g -> if (g.isNotBlank() && to.glances.getOrNull(i) != null) chooseGlance(to, i, g) }
            settings.backlightDay?.let { setNumber(to, "backlight_day", it) }
            settings.backlightNight?.let { setNumber(to, "backlight_night", it) }
            settings.nightStart?.let { setNumber(to, "night_starts_hour", it.toFloat()) }
            settings.nightEnd?.let { setNumber(to, "night_ends_hour", it.toFloat()) }
        }
        settings.volume?.let { if (to.volume != null) setNumber(to, "volume", it) }
        settings.spokenAnswers?.let { setSwitch(to, "spoken_answers", it) }
        settings.followUp?.let { setSwitch(to, "follow_up_listening", it) }
    }

    fun applyPreset(preset: Preset, targets: List<Satellite>) {
        targets.forEach { apply(preset.settings, it) }
        notice = "Applied \"${preset.name}\" to ${targets.size} satellite${if (targets.size == 1) "" else "s"}."
    }

    fun copySettings(from: Satellite, targets: List<Satellite>) {
        val settings = SatSettings.of(from)
        targets.forEach { apply(settings, it) }
        notice = "Copied ${from.name}'s settings to ${targets.size} satellite${if (targets.size == 1) "" else "s"}."
    }

    fun setSwitch(sat: Satellite, suffix: String, on: Boolean) =
        client.callService("switch", if (on) "turn_on" else "turn_off", sat.switch(suffix))
}
