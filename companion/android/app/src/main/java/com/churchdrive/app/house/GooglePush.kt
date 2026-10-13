package com.churchdrive.app.house

import android.content.Context
import com.churchdrive.app.Session
import com.churchdrive.app.ha.MobileApp
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.firebase.FirebaseApp
import com.google.firebase.installations.FirebaseInstallations
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

    // The three steps Google push needs, so a stuck one can be seen: Google Play services, this app's install id, the token.
    private var play = "checking"
    private var install = "waiting"
    private var tokenStep = "waiting"

    private fun publish(context: Context) {
        setStatus(context, "Play services: $play · Install ID: $install · Token: $tokenStep")
    }

    /** Tells the house this phone's token. Safe to call often: the house only keeps a changed one. */
    fun register(context: Context) {
        val app = context.applicationContext
        if (runCatching { FirebaseApp.getApps(app).isEmpty() }.getOrDefault(true)) {
            setStatus(app, "Not built into this version (no Firebase settings)")
            return
        }
        val gms = runCatching { GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(app) }.getOrDefault(-1)
        play = if (gms == ConnectionResult.SUCCESS) "ok" else "problem (code $gms)"
        install = "waiting"
        tokenStep = "waiting"
        publish(app)
        runCatching {
            FirebaseInstallations.getInstance().id
                .addOnSuccessListener { install = "ok"; publish(app) }
                .addOnFailureListener { install = "failed: ${it.message ?: it.javaClass.simpleName}"; publish(app) }
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { tokenStep = "received"; publish(app); tell(app, it) }
                .addOnFailureListener { tokenStep = "failed: ${it.message ?: it.javaClass.simpleName}"; publish(app) }
        }.onFailure { tokenStep = "couldn't ask: ${it.message ?: it.javaClass.simpleName}"; publish(app) }
        // Google not answering at all is the likeliest fault, so say so rather than leave "waiting".
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (install == "waiting") install = "no answer after 20 seconds"
            if (tokenStep == "waiting") tokenStep = "no answer after 20 seconds"
            publish(app)
        }, 20_000)
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
            // With the token at the house, notifications come through Google and the app's own connection (and its notice) stops.
            if (result == com.churchdrive.app.ha.WebhookResult.Ok && !session.googlePush) {
                session.googlePush = true
                House.syncNotifications(context)
            }
            setStatus(
                context,
                when (result) {
                    com.churchdrive.app.ha.WebhookResult.Ok -> { tokenStep = "received and sent to the house"; "Play services: $play · Install ID: $install · Token: $tokenStep" }
                    com.churchdrive.app.ha.WebhookResult.Gone -> "The house no longer knows this phone"
                    com.churchdrive.app.ha.WebhookResult.Failed -> "Got a token, but couldn't reach the house"
                },
            )
        }
    }
}
