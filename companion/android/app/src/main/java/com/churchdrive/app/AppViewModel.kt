package com.churchdrive.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.churchdrive.app.ha.ConnectionState
import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ha.HaClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val session = Session(app)
    private val client = HaClient(viewModelScope)

    val connection: StateFlow<ConnectionState> = client.connection
    val entities: StateFlow<Map<String, EntityState>> = client.entities

    private val _signedIn = MutableStateFlow(session.signedIn)
    val signedIn: StateFlow<Boolean> = _signedIn

    init {
        if (session.signedIn) client.connect(session.url!!, session.token!!)
    }

    fun signIn(url: String, token: String) {
        session.save(url, token)
        client.connect(url, token)
        _signedIn.value = true
    }

    fun signOut() {
        client.disconnect()
        session.clear()
        _signedIn.value = false
    }

    fun alarm(service: String, code: String? = null) {
        val data = org.json.JSONObject()
        if (!code.isNullOrBlank()) data.put("code", code)
        client.callService("alarm_control_panel", service, ALARM_ENTITY, data)
    }

    companion object {
        const val ALARM_ENTITY = "alarm_control_panel.church_drive_alarm"
    }
}
