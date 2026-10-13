package com.churchdrive.app.house

import android.content.Context
import com.churchdrive.app.Session
import com.churchdrive.app.ha.MobileApp
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
    /** Tells the house this phone's token. Safe to call often: the house only keeps a changed one. */
    fun register(context: Context) {
        val app = context.applicationContext
        runCatching { FirebaseMessaging.getInstance().token.addOnSuccessListener { tell(app, it) } }
    }

    fun tell(context: Context, token: String) {
        thread {
            val session = Session(context)
            val url = session.url ?: return@thread
            val service = MobileApp.notifyService(session.deviceName ?: return@thread)
            val webhook = House.ensureRegistered(context) ?: return@thread
            MobileApp.post(url, webhook, MobileApp.fcmTokenBody(service, token))
        }
    }
}
