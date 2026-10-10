package com.churchdrive.app.ha

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/** A message the house sent to this phone (from `notify.mobile_app_...`). */
data class Push(val title: String, val message: String, val tag: String?, val channel: String, val actions: List<PushAction> = emptyList())

/** A button on a notification: [id] is what comes back when it is pressed, [title] what it says. */
data class PushAction(val id: String, val title: String)

/** How a post to the phone's webhook went. [Gone] means Home Assistant no longer knows this phone: register again. */
enum class WebhookResult { Ok, Gone, Failed }

/**
 * This app as one of Home Assistant's "mobile app" devices: it registers the phone, reports where it is (so the house
 * knows who is home, at work or out) and receives notifications, the same way the Home Assistant app does.
 */
object MobileApp {
    private val json = "application/json".toMediaType()
    private val http = OkHttpClient()

    /** The registration Home Assistant wants. `push_websocket_channel` lets notifications arrive over the app's own connection. */
    fun registrationBody(deviceId: String, deviceName: String, appVersion: String, osVersion: String, manufacturer: String, model: String): JSONObject =
        JSONObject()
            .put("device_id", deviceId)
            .put("app_id", "com.churchdrive.app")
            .put("app_name", "Church Drive")
            .put("app_version", appVersion)
            .put("device_name", deviceName)
            .put("manufacturer", manufacturer)
            .put("model", model)
            .put("os_name", "Android")
            .put("os_version", osVersion)
            .put("supports_encryption", false)
            .put("app_data", JSONObject().put("push_websocket_channel", true))

    /** Registers the phone and gives its webhook id, or null (offline, refused). Blocking. */
    fun register(baseUrl: String, accessToken: String, body: JSONObject): String? = runCatching {
        val request = Request.Builder()
            .url(baseUrl.trim().trimEnd('/') + "/api/mobile_app/registrations")
            .header("Authorization", "Bearer $accessToken")
            .post(body.toString().toRequestBody(json))
            .build()
        http.newCall(request).execute().use { r ->
            if (r.isSuccessful) JSONObject(r.body?.string().orEmpty()).text("webhook_id") else null
        }
    }.getOrNull()

    /** The webhook message for a new position. */
    fun locationBody(latitude: Double, longitude: Double, accuracy: Float?, altitude: Double?, speed: Float?, course: Float?, battery: Int?): JSONObject {
        val data = JSONObject().put("gps", JSONArray().put(latitude).put(longitude))
        if (accuracy != null) data.put("gps_accuracy", accuracy.toDouble().coerceAtLeast(0.0))
        if (altitude != null) data.put("altitude", altitude)
        if (speed != null) data.put("speed", speed.toDouble())
        if (course != null) data.put("course", course.toDouble())
        if (battery != null && battery in 0..100) data.put("battery", battery)
        return JSONObject().put("type", "update_location").put("data", data)
    }

    /** Sends a webhook message. The webhook id is its own key, so there's no token. Blocking. */
    fun post(baseUrl: String, webhookId: String, body: JSONObject): WebhookResult = runCatching {
        val request = Request.Builder()
            .url(baseUrl.trim().trimEnd('/') + "/api/webhook/$webhookId")
            .post(body.toString().toRequestBody(json))
            .build()
        http.newCall(request).execute().use { r ->
            when {
                r.isSuccessful -> WebhookResult.Ok
                r.code == 410 || r.code == 404 -> WebhookResult.Gone
                else -> WebhookResult.Failed
            }
        }
    }.getOrElse { WebhookResult.Failed }

    /** A notification from the push channel's event, or null if it has no words. */
    fun parsePush(event: JSONObject): Push? {
        val message = event.text("message") ?: return null
        val data = event.optJSONObject("data")
        return Push(
            title = event.text("title") ?: "Church Drive",
            message = message,
            tag = data?.text("tag"),
            channel = data?.text("channel") ?: "house",
            // Only the house's own task buttons (Done, Snooze): anything else would have nothing to do here.
            actions = (data?.optJSONArray("actions")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) } } ?: emptyList())
                .mapNotNull { o ->
                    val id = o.text("action")
                    val title = o.text("title")
                    if (id != null && title != null && parseTaskAction(id) != null) PushAction(id, title) else null
                },
        )
    }

    /** The webhook message that tells the house a task button was pressed (it arrives there as the `church_drive_task_action` event). */
    fun taskActionBody(action: TaskAction, kind: String, minutes: Int? = null): JSONObject {
        val event = JSONObject().put("action", kind).put("list", action.todo).put("uid", action.uid).put("who", action.who).put("zone", action.zone)
        if (minutes != null) event.put("minutes", minutes)
        return JSONObject().put("type", "fire_event")
            .put("data", JSONObject().put("event_type", "church_drive_task_action").put("event_data", event))
    }

    /** The name of the notify service for a phone, as Home Assistant makes it: `notify.mobile_app_<name>`. */
    fun notifyService(deviceName: String): String =
        "notify.mobile_app_" + deviceName.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')

    /** The name the phone is registered under: the person's name and the phone's model. */
    fun deviceName(personName: String?, model: String): String =
        listOfNotNull(personName?.trim()?.substringBefore(' ')?.takeIf { it.isNotEmpty() }, model.takeIf { it.isNotBlank() })
            .joinToString(" ").ifBlank { "Church Drive phone" }
}
