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
class WidgetData(
    val entities: Map<String, EntityState>,
    val lights: LightLayout,
    val areaNames: Map<String, String>,
    /** The answers to what the widget asked for besides the states (to-do items, a forecast), by key. */
    val extras: Map<String, Any?> = emptyMap(),
)

/** Something a widget needs besides the states: a command's answer, or the first event of a subscription (the forecast). */
sealed class Ask(val key: String, val type: String, val params: (Map<String, EntityState>) -> JSONObject?) {
    class Request(key: String, type: String, params: JSONObject) : Ask(key, type, { params })

    /** [params] may use the states (to find which weather entity there is); null skips the ask. */
    class FirstEvent(key: String, type: String, params: (Map<String, EntityState>) -> JSONObject?) : Ask(key, type, params)
}

/** Reads the house for the widgets (a short connection to Home Assistant, as the app has) and sends their button presses. */
object WidgetSource {
    private val http = OkHttpClient()

    /** The house now, or null when signed out or unreachable (gives up after 20 seconds). */
    suspend fun load(context: Context, asks: List<Ask> = emptyList()): WidgetData? = withTimeoutOrNull(20_000) {
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
            val extras = mutableMapOf<String, Any?>()
            for (a in asks) {
                val params = a.params(client.entities.value) ?: continue
                extras[a.key] = when (a) {
                    is Ask.Request -> ask(client, a.type, params)
                    is Ask.FirstEvent -> withTimeoutOrNull(6_000) { firstEvent(client, a.type, params) }
                }
            }
            WidgetData(
                client.entities.value,
                (lovelace as? JSONObject)?.let { DashboardLights.parse(it) } ?: LightLayout.Fallback,
                DashboardLights.areaNames(areas),
                extras,
            )
        } finally {
            client.disconnect()
            scope.cancel()
        }
    }

    private suspend fun firstEvent(client: HaClient, type: String, params: JSONObject): JSONObject? =
        suspendCancellableCoroutine { cont ->
            val id = client.subscribe(type, params) { event -> if (cont.isActive) cont.resume(event) }
            cont.invokeOnCancellation { if (id >= 0) client.unsubscribe(id) }
            if (id < 0 && cont.isActive) cont.resume(null)
        }

    private suspend fun ask(client: HaClient, type: String, params: JSONObject): Any? =
        suspendCancellableCoroutine { cont -> client.request(type, params) { if (cont.isActive) cont.resume(it) } }

    /** A picture from Home Assistant (a camera's latest), scaled down to about [maxPx] wide, or null. Blocking. */
    fun fetchImage(context: Context, path: String, maxPx: Int = 640): android.graphics.Bitmap? = runCatching {
        val session = Session(context)
        val base = session.url?.trim()?.trimEnd('/') ?: return null
        val token = session.freshToken() ?: return null
        val request = Request.Builder().url(base + path).header("Authorization", "Bearer $token").build()
        http.newCall(request).execute().use { r ->
            if (!r.isSuccessful) return null
            val bytes = r.body?.bytes() ?: return null
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= maxPx) sample *= 2
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }.getOrNull()

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
