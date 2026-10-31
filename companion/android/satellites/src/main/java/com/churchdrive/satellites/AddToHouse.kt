package com.churchdrive.satellites

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** How adding a satellite to Home Assistant ended: [error] is a sentence for the person, or null on success. */
data class AddOutcome(val error: String?, val alreadyThere: Boolean = false)

/**
 * Adds the satellite to Home Assistant's ESPHome integration through its configuration flow, the same steps as Settings > Devices >
 * Add integration > ESPHome: the host, then the encryption key. It needs a Home Assistant administrator.
 */
object AddToHouse {
    private val http = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    private val json = "application/json".toMediaType()

    private fun call(method: String, url: String, token: String, body: JSONObject?): Pair<Int, JSONObject?> {
        val req = Request.Builder().url(url).header("Authorization", "Bearer $token").method(method, body?.toString()?.toRequestBody(json)).build()
        http.newCall(req).execute().use { r -> return r.code to runCatching { JSONObject(r.body?.string().orEmpty()) }.getOrNull() }
    }

    /** [host] is the satellite's address, normally `<device name>.local`; [key] is the API encryption key it was built with. */
    fun add(baseUrl: String, token: String, host: String, key: String): AddOutcome = runCatching {
        val base = baseUrl.trimEnd('/')
        val flowUrl = "$base/api/config/config_entries/flow"
        var (code, step) = call("POST", flowUrl, token, JSONObject().put("handler", "esphome").put("show_advanced_options", false))
        if (code == 401 || code == 403) return AddOutcome("Home Assistant says only an administrator can add devices.")
        if (step == null || code >= 400) return AddOutcome("Home Assistant wouldn't start adding it (code $code).")
        var sentHost = false
        var sentKey = false
        repeat(5) {
            val flowId = step!!.optString("flow_id")
            when (step!!.optString("type")) {
                "create_entry" -> return AddOutcome(null)
                "abort" -> return when (step!!.optString("reason")) {
                    "already_configured" -> AddOutcome(null, alreadyThere = true)
                    else -> AddOutcome("Home Assistant stopped: ${step!!.optString("reason").replace('_', ' ')}.")
                }
                "form" -> {
                    val errors = step!!.optJSONObject("errors")?.optString("base").orEmpty()
                    val id = step!!.optString("step_id")
                    when {
                        id == "authenticate" || errors == "invalid_psk" || errors == "requires_encryption_key" -> {
                            if (errors == "invalid_psk" && sentKey) return AddOutcome("Home Assistant says that API key is wrong for this satellite.")
                            sentKey = true
                            val r = call("POST", "$flowUrl/$flowId", token, JSONObject().put("noise_psk", key))
                            step = r.second ?: return AddOutcome("Home Assistant gave no answer when sending the key.")
                        }
                        else -> {
                            if (sentHost && errors.isNotEmpty()) return AddOutcome(
                                when (errors) {
                                    "resolve_error" -> "Home Assistant can't find $host on the network yet. Wait a minute and try again."
                                    "connection_error" -> "Home Assistant can't reach $host yet. Wait a minute and try again."
                                    else -> "Home Assistant couldn't add it ($errors)."
                                },
                            )
                            sentHost = true
                            val r = call("POST", "$flowUrl/$flowId", token, JSONObject().put("host", host).put("port", 6053))
                            step = r.second ?: return AddOutcome("Home Assistant gave no answer when sending the address.")
                        }
                    }
                }
                else -> return AddOutcome("Home Assistant answered something unexpected.")
            }
        }
        AddOutcome("Home Assistant didn't finish adding it.")
    }.getOrElse { AddOutcome("Couldn't reach Home Assistant: ${it.message ?: it.javaClass.simpleName}") }

    /** Removes a device's integration entry (the satellite disappears from Home Assistant). Administrator only. */
    fun remove(baseUrl: String, token: String, entryId: String): Boolean = runCatching {
        val (code, _) = call("DELETE", "${baseUrl.trimEnd('/')}/api/config/config_entries/entry/$entryId", token, null)
        code in 200..299
    }.getOrDefault(false)
}
