package com.churchdrive.app.widget

import android.content.Context
import com.churchdrive.app.Session
import com.churchdrive.app.freshToken
import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ha.HaClient
import com.churchdrive.app.ha.data
import com.churchdrive.app.ui.DashboardLights
import com.churchdrive.app.ui.LightLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import kotlin.coroutines.resume

/** What a widget draws from: the house's states, the lights' rooms from the dashboard, and the areas' names. */
class WidgetData(val entities: Map<String, EntityState>, val lights: LightLayout, val areaNames: Map<String, String>)

/** Reads the house for the widgets (a short connection to Home Assistant, as the app has) and sends their button presses. */
object WidgetSource {
    private val http = OkHttpClient()

    /** The house now, or null when signed out or unreachable (gives up after 20 seconds). */
    suspend fun load(context: Context): WidgetData? = withTimeoutOrNull(20_000) {
        val session = Session(context)
        val url = session.url ?: return@withTimeoutOrNull null
        if (!session.signedIn) return@withTimeoutOrNull null
        val token = kotlinx.coroutines.withContext(Dispatchers.IO) { session.freshToken() } ?: return@withTimeoutOrNull null
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val client = HaClient(scope)
        try {
            client.connect(url, token)
            // Every entity's state has to have arrived: before that, the list holds only what happened to change.
            client.statesLoaded.first { it }
            val lovelace = ask(client, "lovelace/config", data("url_path" to DashboardLights.DASHBOARD))
            val areas = ask(client, "config/area_registry/list", JSONObject())
            WidgetData(
                client.entities.value,
                (lovelace as? JSONObject)?.let { DashboardLights.parse(it) } ?: LightLayout.Fallback,
                DashboardLights.areaNames(areas),
            )
        } finally {
            client.disconnect()
            scope.cancel()
        }
    }

    private suspend fun ask(client: HaClient, type: String, params: JSONObject): Any? =
        suspendCancellableCoroutine { cont -> client.request(type, params) { if (cont.isActive) cont.resume(it) } }

    /** Calls a Home Assistant service on an entity (a widget button). Blocking: call off the main thread. */
    fun callService(context: Context, domain: String, service: String, entity: String, extra: JSONObject): Boolean = runCatching {
        val session = Session(context)
        val base = session.url?.trim()?.trimEnd('/') ?: return false
        val token = session.freshToken() ?: return false
        val body = JSONObject(extra.toString()).put("entity_id", entity)
        val request = Request.Builder()
            .url("$base/api/services/$domain/$service")
            .header("Authorization", "Bearer $token")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(request).execute().use { it.isSuccessful }
    }.getOrDefault(false)
}
