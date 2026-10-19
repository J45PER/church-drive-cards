package com.churchdrive.app.ha

import com.churchdrive.app.ui.HaApi
import org.json.JSONObject

/** Plain access to Home Assistant's commands over this client, for the watch and to-do apps (the phone app has its own, in AppViewModel). */
fun HaClient.asApi(): HaApi = object : HaApi {
    override fun request(type: String, params: JSONObject, done: (Any?) -> Unit) = this@asApi.request(type, params) { done(it) }

    override fun subscribe(type: String, params: JSONObject, onEvent: (JSONObject) -> Unit): Int = this@asApi.subscribe(type, params, onEvent)

    override fun close(subscription: Int) = unsubscribe(subscription)

    override val connected: Boolean get() = connection.value == ConnectionState.Connected

    override val lastErrorCode: String? get() = this@asApi.lastErrorCode
}
