package com.churchdrive.app.ha

import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/** Where a Home Assistant sign-in has got to. */
sealed interface LoginStep {
    /** Signed in: [code] is swapped for tokens. */
    data class Done(val code: String) : LoginStep

    /** Waiting for the person's username and password ([error] says why the last try failed). */
    data class Credentials(val flowId: String, val error: String?) : LoginStep

    /** Waiting for their two-step code. */
    data class Mfa(val flowId: String, val error: String?) : LoginStep

    data class Failed(val message: String) : LoginStep
}

/** The tokens Home Assistant gives: a short-lived access token, and a refresh token that lasts. */
data class Tokens(val access: String, val refresh: String?, val expiresInSeconds: Long)

/** Home Assistant's own sign-in (the login flow, then the token endpoint), so people use their normal login, not a pasted token. */
object HaAuth {
    private val json = "application/json".toMediaType()
    private val http = OkHttpClient()

    /** The address as the sign-in's client id: Home Assistant wants a URL with a path. */
    fun clientId(baseUrl: String) = baseUrl.trim().trimEnd('/') + "/"

    fun parseStep(fallbackFlowId: String, body: JSONObject): LoginStep {
        val flowId = body.optString("flow_id").ifBlank { fallbackFlowId }
        return when (body.optString("type")) {
            "create_entry" -> body.text("result")?.let { LoginStep.Done(it) } ?: LoginStep.Failed("Sign-in gave no code.")
            "form" -> {
                val error = body.optJSONObject("errors")?.text("base")?.let(::errorWords)
                if (body.optString("step_id") == "mfa") LoginStep.Mfa(flowId, error) else LoginStep.Credentials(flowId, error)
            }
            "abort" -> LoginStep.Failed("Sign-in was refused (${body.optString("reason")}).")
            else -> LoginStep.Failed("Home Assistant gave an answer the app doesn't know.")
        }
    }

    fun errorWords(code: String) = when (code) {
        "invalid_auth" -> "Wrong username or password."
        "invalid_code" -> "That code didn't work."
        "login_expired" -> "That took too long. Start again."
        else -> code.replace('_', ' ').replaceFirstChar { it.uppercase() } + "."
    }

    fun parseTokens(body: JSONObject): Tokens? {
        val access = body.text("access_token") ?: return null
        return Tokens(access, body.text("refresh_token"), body.optLong("expires_in", 1800))
    }

    /** Opens a sign-in. Blocking: call off the main thread. */
    fun start(baseUrl: String): LoginStep = runCatching {
        val client = clientId(baseUrl)
        val body = JSONObject()
            .put("client_id", client)
            .put("handler", org.json.JSONArray().put("homeassistant").put(JSONObject.NULL))
            .put("redirect_uri", client + "?auth_callback=1")
        parseStep("", post("${client}auth/login_flow", body))
    }.getOrElse { LoginStep.Failed("Couldn't reach Home Assistant at that address.") }

    /** Sends the username and password, or the two-step code, for the open sign-in. Blocking. */
    fun submit(baseUrl: String, flowId: String, fields: Map<String, String>): LoginStep = runCatching {
        val client = clientId(baseUrl)
        val body = JSONObject().put("client_id", client)
        fields.forEach { body.put(it.key, it.value) }
        parseStep(flowId, post("${client}auth/login_flow/$flowId", body))
    }.getOrElse { LoginStep.Failed("Couldn't reach Home Assistant.") }

    /** Swaps the sign-in's code for tokens. Blocking. */
    fun exchange(baseUrl: String, code: String): Tokens? = runCatching {
        parseTokens(form(baseUrl, FormBody.Builder().add("grant_type", "authorization_code").add("code", code).add("client_id", clientId(baseUrl)).build()))
    }.getOrNull()

    /** A fresh access token from the refresh token. [Refresh.Rejected] means it was revoked: sign in again. Blocking. */
    fun refresh(baseUrl: String, refreshToken: String): Refresh = runCatching {
        val form = FormBody.Builder().add("grant_type", "refresh_token").add("refresh_token", refreshToken).add("client_id", clientId(baseUrl)).build()
        val request = Request.Builder().url("${clientId(baseUrl)}auth/token").post(form).build()
        http.newCall(request).execute().use { r ->
            val text = r.body?.string().orEmpty()
            when {
                r.isSuccessful -> parseTokens(JSONObject(text))?.let { Refresh.Fresh(it) } ?: Refresh.Unreachable
                r.code in 400..403 -> Refresh.Rejected
                else -> Refresh.Unreachable
            }
        }
    }.getOrElse { Refresh.Unreachable }

    /** Tells Home Assistant to forget this phone's sign-in. Best effort. Blocking. */
    fun revoke(baseUrl: String, refreshToken: String) {
        runCatching { form(baseUrl, FormBody.Builder().add("token", refreshToken).add("action", "revoke").build()) }
    }

    private fun post(url: String, body: JSONObject): JSONObject {
        val request = Request.Builder().url(url).post(body.toString().toRequestBody(json)).build()
        http.newCall(request).execute().use { return JSONObject(it.body?.string().orEmpty()) }
    }

    private fun form(baseUrl: String, form: FormBody): JSONObject {
        val request = Request.Builder().url("${clientId(baseUrl)}auth/token").post(form).build()
        http.newCall(request).execute().use { return JSONObject(it.body?.string().orEmpty().ifBlank { "{}" }) }
    }
}

sealed interface Refresh {
    data class Fresh(val tokens: Tokens) : Refresh
    object Rejected : Refresh
    object Unreachable : Refresh
}
