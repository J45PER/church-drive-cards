package com.churchdrive.app.house

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.churchdrive.app.BuildConfig
import com.churchdrive.app.MainActivity
import com.churchdrive.app.R
import com.churchdrive.app.Session
import com.churchdrive.app.ha.ConnectionState
import com.churchdrive.app.ha.HaAuth
import com.churchdrive.app.ha.HaClient
import com.churchdrive.app.ha.NoticeButton
import com.churchdrive.app.ha.pageFromLink
import com.churchdrive.app.ha.parseNoticeButton
import com.churchdrive.app.ha.parseTaskAction
import com.churchdrive.app.ha.MobileApp
import com.churchdrive.app.ha.Refresh
import com.churchdrive.app.ha.WebhookResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Receives the house's notifications while the app is closed, over its own connection to Home Assistant. Android requires
 * a notice while an app keeps a connection like this; it's a quiet one the person can swipe away. (Location doesn't
 * need this: see [House].)
 */
class HouseService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var work: Job? = null
    private var client: HaClient? = null
    private var lastRefresh = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannels(this)
        ServiceCompat.startForeground(
            this, FOREGROUND_ID, foregroundNotification(),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )
        restart()
        return START_STICKY
    }

    override fun onDestroy() {
        stopAll()
        scope.coroutineContext[Job]?.cancel()
        super.onDestroy()
    }

    private fun stopAll() {
        work?.cancel()
        work = null
        client?.disconnect()
        client = null
    }

    /** Starts again from what the person has chosen (called whenever the settings change). */
    private fun restart() {
        stopAll()
        val session = Session(this)
        if (!session.signedIn || !session.notifyOn) {
            stopSelf()
            return
        }
        work = scope.launch { run(session) }
    }

    private fun foregroundNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF6B6BBF.toInt())
            .setContentTitle("Church Drive is connected")
            .setContentText("Keeping in touch with the house")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            // The person can swipe this away (Android 13 and later); the service carries on.
            .setOngoing(false)
            .setContentIntent(openApp(this))
            .build()

    /** A fresh access token (from the refresh token when there is one), or the stored one. Blocking. */
    private fun freshToken(session: Session): String? {
        val url = session.url ?: return null
        val refresh = session.refreshToken ?: return session.token
        return when (val r = HaAuth.refresh(url, refresh)) {
            is Refresh.Fresh -> r.tokens.access.also { session.saveAccess(it) }
            else -> session.token
        }
    }

    private suspend fun run(session: Session) {
        val url = session.url ?: return
        // Register this phone with Home Assistant once; it keeps the id.
        var webhook = House.ensureRegistered(this)
        while (webhook == null) {
            delay(60_000)
            webhook = House.ensureRegistered(this)
        }
        if (session.notifyOn) listen(session, url, webhook)
    }

    /** The notification channel: Home Assistant sends each notification down this connection. */
    private suspend fun listen(session: Session, url: String, webhook: String) {
        val c = HaClient(scope)
        client = c
        fun connect() {
            freshToken(session)?.let { c.connect(url, it, light = true) }
        }
        connect()
        c.connection.collect { state ->
            when (state) {
                ConnectionState.Connected ->
                    c.subscribe(
                        "mobile_app/push_notification_channel",
                        JSONObject().put("webhook_id", webhook).put("support_confirm", false),
                    ) { event -> show(event) }
                // The access token ran out: make a new one and connect again (not more than twice a minute).
                ConnectionState.AuthFailed ->
                    if (session.refreshToken != null && System.currentTimeMillis() - lastRefresh > 30_000) {
                        lastRefresh = System.currentTimeMillis()
                        connect()
                    }
                else -> Unit
            }
        }
    }

    private fun show(event: JSONObject) = postNotice(this, event)

    companion object {
        /** Shows a notification from the house, as the person chose (Account > Settings > Notification styles), else as the house asked. */
        fun postNotice(context: Context, event: JSONObject) {
            val push = MobileApp.parsePush(event) ?: return
            if (!Permissions.canNotify(context)) return
            val style = NoticeStyles.styleFor(context, push)
            if (style == NoticeStyle.Off) return
            // A kind muted for a while stays quiet, but a safety alarm never is.
            if (style != NoticeStyle.Alert && NoticeMutes.isMuted(context, push.kind)) return
            NoticeStyles.ensureChannels(context)
            val noticeId = push.tag?.hashCode() ?: System.currentTimeMillis().toInt()
            val builder = NotificationCompat.Builder(context, style.channel)
                .setCategory(if (style == NoticeStyle.Alert) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_MESSAGE)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(0xFF6B6BBF.toInt())
                .setContentTitle(push.title)
                .setContentText(push.message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(push.message))
                .setAutoCancel(true)
                .setContentIntent(openApp(context, push.url))
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            push.actions.forEach { button ->
                val code = button.id.hashCode() xor noticeId xor (button.uri?.hashCode() ?: 0)
                val common = { i: Intent -> i.putExtra(EXTRA_NOTICE_TAG, push.tag).putExtra(EXTRA_NOTICE_ID, noticeId) }
                val task = parseTaskAction(button.id)
                if (task != null) {
                    // A task reminder's buttons: Done ticks it off, Snooze asks how long.
                    val extras = { i: Intent -> common(i.putExtra(EXTRA_TASK_ACTION, button.id)) }
                    val pending = if (task.kind == "done") {
                        PendingIntent.getBroadcast(context, code, extras(Intent(context, TaskActionReceiver::class.java)), flags)
                    } else {
                        PendingIntent.getActivity(context, code, extras(Intent(context, SnoozeActivity::class.java)), flags)
                    }
                    builder.addAction(0, button.title, pending)
                    return@forEach
                }
                when (val action = parseNoticeButton(button.id, button.uri)) {
                    is NoticeButton.Link -> builder.addAction(0, button.title, linkIntent(context, action.uri, code))
                    is NoticeButton.House -> builder.addAction(
                        NotificationCompat.Action.Builder(
                            0, button.title,
                            PendingIntent.getBroadcast(context, code, common(Intent(context, NoticeActionReceiver::class.java).putExtra(EXTRA_NOTICE_VERB, action.verb)), flags),
                        ).setAuthenticationRequired(true).build(),
                    )
                    is NoticeButton.Mute -> if (push.kind != null && style != NoticeStyle.Alert) builder.addAction(
                        0, button.title,
                        PendingIntent.getBroadcast(context, code, common(Intent(context, NoticeMuteReceiver::class.java).putExtra(EXTRA_NOTICE_KIND, push.kind).putExtra(EXTRA_NOTICE_MINUTES, action.minutes)), flags),
                    )
                    is NoticeButton.Snooze -> builder.addAction(
                        0, button.title,
                        PendingIntent.getBroadcast(context, code, common(Intent(context, NoticeSnoozeReceiver::class.java).putExtra(EXTRA_NOTICE_EVENT, event.toString()).putExtra(EXTRA_NOTICE_MINUTES, action.minutes)), flags),
                    )
                    null -> Unit
                }
            }
            // The same tag replaces the earlier notification, as in the Home Assistant app.
            runCatching { NotificationManagerCompat.from(context).notify(push.tag, noticeId, builder.build()) }
        }

        /** A Link button: a camera opens that camera (answering for a doorbell), a page opens that page. */
        private fun linkIntent(context: Context, uri: String, code: Int): PendingIntent {
            val camera = com.churchdrive.app.ui.cameraOpenFromLink(uri)
            val page = pageFromLink(uri)
            val intent = Intent(context, MainActivity::class.java).also {
                if (camera != null) it.putExtra("camera", camera.entityId).putExtra("answer", camera.answer)
                if (page != null) it.putExtra("page", page)
            }
            return PendingIntent.getActivity(context, code, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        private const val FOREGROUND_ID = 4201
        private const val CHANNEL_SERVICE = "service"

        /** Opens the app; a shopping reminder's link opens that list (each list its own request code, so the extras don't mix). */
        private fun openApp(context: Context, url: String? = null): PendingIntent {
            val list = com.churchdrive.app.ui.shoppingIdFromLink(url)
            // A doorbell notification's link opens that camera, answering.
            val camera = com.churchdrive.app.ui.cameraOpenFromLink(url)
            val intent = Intent(context, MainActivity::class.java).also {
                if (list != null) it.putExtra(MainActivity.EXTRA_SHOPPING_LIST, list)
                if (camera != null) it.putExtra("camera", camera.entityId).putExtra("answer", camera.answer)
            }
            return PendingIntent.getActivity(
                context, list?.hashCode() ?: camera?.entityId?.hashCode() ?: 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        fun createChannels(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_SERVICE, "Connected to the house", NotificationManager.IMPORTANCE_MIN),
            )
            ensureChannel(context, "house")
        }

        private fun ensureChannel(context: Context, id: String) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(id) == null) {
                val name = if (id == "house") "Messages from the house" else id.replaceFirstChar { it.uppercase() }
                manager.createNotificationChannel(NotificationChannel(id, name, NotificationManager.IMPORTANCE_DEFAULT))
            }
        }

        /** Starts the service when the person has asked for notifications, stops it when not. */
        fun sync(context: Context, on: Boolean) {
            val intent = Intent(context, HouseService::class.java)
            if (on) ContextCompat.startForegroundService(context, intent) else context.stopService(intent)
        }
    }
}
