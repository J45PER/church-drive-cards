package com.churchdrive.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.churchdrive.app.ha.ConnectionState
import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ha.HaClient
import com.churchdrive.app.ha.IconPack
import com.churchdrive.app.ha.data
import com.churchdrive.app.ui.DashboardLights
import com.churchdrive.app.ui.LightLayout
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

    private val _lights = MutableStateFlow(LightLayout.Fallback)
    val lights: StateFlow<LightLayout> = _lights

    private val _areaNames = MutableStateFlow<Map<String, String>>(emptyMap())
    val areaNames: StateFlow<Map<String, String>> = _areaNames

    private val _signedIn = MutableStateFlow(session.signedIn)
    val signedIn: StateFlow<Boolean> = _signedIn

    private val appContext = app

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
            (config as? org.json.JSONObject)?.let { _lights.value = DashboardLights.parse(it) }
        }
        client.request("config/area_registry/list") { _areaNames.value = DashboardLights.areaNames(it) }
    }

    init {
        if (session.signedIn) start(session.url!!, session.token!!)
    }

    private fun start(url: String, token: String) {
        client.connect(url, token)
        IconPack.load(viewModelScope, appContext.filesDir, url, token)
    }

    fun signIn(url: String, token: String) {
        session.save(url, token)
        start(url, token)
        _signedIn.value = true
    }

    fun signOut() {
        client.disconnect()
        session.clear()
        _signedIn.value = false
    }

    fun call(domain: String, service: String, entityId: String, data: org.json.JSONObject) =
        client.callService(domain, service, entityId, data)

    companion object {
    }
}
