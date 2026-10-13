package com.churchdrive.app.house

import android.content.Context
import com.churchdrive.app.Session
import com.churchdrive.app.ha.MobileApp
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import org.json.JSONObject
import kotlin.concurrent.thread

/**
 * Notifications through Google's push service. The house sends the whole notice as one data message; the app builds the
 * notification itself, so the person's styles, buttons and mutes apply exactly as for the app's own connection.
 */
class GooglePushService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        GooglePush.tell(applicationContext, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val event = message.data["event"]?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return
        HouseService.postNotice(applicationContext, event)
    }
}

object GooglePush {
    private fun prefs(context: Context) = context.getSharedPreferences("google_push", Context.MODE_PRIVATE)

    /** Where Google push has got to on this phone, in words (shown in Settings so a problem can be seen). */
    fun status(context: Context): String = prefs(context).getString("status", "Not tried yet") ?: "Not tried yet"

    private fun setStatus(context: Context, text: String) {
        prefs(context).edit().putString("status", text).apply()
    }

    /** Tells the house this phone's token. Safe to call often: the house only keeps a changed one. */
    fun register(context: Context) {
        val app = context.applicationContext
        if (runCatching { FirebaseApp.getApps(app).isEmpty() }.getOrDefault(true)) {
            setStatus(app, "Not built into this version (no Firebase settings)")
            return
        }
        setStatus(app, "Asking Google for this phone's token…")
        runCatching {
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { tell(app, it) }
                .addOnFailureListener { setStatus(app, "Google gave no token: ${it.message ?: it.javaClass.simpleName}") }
        }.onFailure { setStatus(app, "Couldn't ask Google: ${it.message ?: it.javaClass.simpleName}") }
    }

    fun tell(context: Context, token: String) {
        thread {
            val session = Session(context)
            val url = session.url
            val name = session.deviceName
            if (url == null || name == null) {
                setStatus(context, "Got a token, but this phone isn't signed in to the house yet")
                return@thread
            }
            val webhook = House.ensureRegistered(context)
            if (webhook == null) {
                setStatus(context, "Got a token, but couldn't register this phone with the house")
                return@thread
            }
            val result = MobileApp.post(url, webhook, MobileApp.fcmTokenBody(MobileApp.notifyService(name), token))
            setStatus(
                context,
                when (result) {
                    com.churchdrive.app.ha.WebhookResult.Ok -> "Token sent to the house"
                    com.churchdrive.app.ha.WebhookResult.Gone -> "The house no longer knows this phone"
                    com.churchdrive.app.ha.WebhookResult.Failed -> "Got a token, but couldn't reach the house"
                },
            )
        }
    }
}
