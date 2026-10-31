package com.churchdrive.app.house

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import com.churchdrive.app.ui.HomeTime
import android.content.Intent
import android.graphics.Bitmap
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
import com.churchdrive.app.R
import com.churchdrive.app.Session
import com.churchdrive.app.ha.ConnectionState
import com.churchdrive.app.ha.HaAuth
import com.churchdrive.app.ha.HaClient
import com.churchdrive.app.ha.NoticeButton
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
                    ) { event -> show(event) }.also { catchUp(c) }
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

    /** Asks the house for anything sent while this phone was offline and shows it (a first run only notes where things are up to). */
    private fun catchUp(c: HaClient) {
        val last = NoticeSeen.last(this)
        c.request("church_drive/notices/missed", JSONObject().put("since", last)) { result ->
            val r = result as? JSONObject ?: return@request
            if (last == 0L) {
                NoticeSeen.save(this, r.optLong("latest", 0))
                return@request
            }
            val list = r.optJSONArray("notices") ?: return@request
            for (i in 0 until list.length()) list.optJSONObject(i)?.let { postNotice(this, missedNoticeEvent(it)) }
        }
    }

    companion object {
        /** Shows a notification from the house, as the person chose (Account > Settings > Notification styles), else as the house asked. */
        fun postNotice(context: Context, event: JSONObject, dedupe: Boolean = true) {
            val parsed = MobileApp.parsePush(event) ?: return
            // Times in a notice are the house's: shown as this phone's time (with the home's after it) when the phone is in another zone.
            // The home's zone is remembered, since a notice can arrive before the app has connected.
            val zones = context.getSharedPreferences("home_time", Context.MODE_PRIVATE)
            HomeTime.zoneId?.let { zones.edit().putString("zone", it).apply() } ?: run { HomeTime.zoneId = zones.getString("zone", null) }
            val push = parsed.copy(title = HomeTime.textBoth(parsed.title), message = HomeTime.textBoth(parsed.message))
            // One the phone has already shown (it arrived live, then again as a catch-up) isn't shown twice.
            if (dedupe && push.seq > 0 && push.seq <= NoticeSeen.last(context)) return
            NoticeSeen.save(context, push.seq)
            if (!Permissions.canNotify(context)) return
            val style = NoticeStyles.styleFor(context, push)
            if (style == NoticeStyle.Off) return
            // A kind muted for a while stays quiet, but a safety alarm never is.
            if (style != NoticeStyle.Alert && NoticeMutes.isMuted(context, push.kind)) return
            NoticeStyles.ensureChannels(context)
            val noticeId = push.tag?.hashCode() ?: System.currentTimeMillis().toInt()
            val builder = NotificationCompat.Builder(context, channelFor(style, push.kind))
                .setCategory(if (style == NoticeStyle.Alert) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_MESSAGE)
                // The assistant's own notices carry his mark (small, and large in colour); everyone else's keep the Manager's.
                .setSmallIcon(if (isAssistantNotice(push.kind)) R.drawable.ic_notification_assistant else R.drawable.ic_notification)
                .setColor(0xFF6B6BBF.toInt())
                .setContentTitle(push.title)
                .setContentText(push.message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(push.message))
                .setAutoCancel(true)
                .setContentIntent(openApp(context, push.url))
            if (isAssistantNotice(push.kind)) {
                runCatching { android.graphics.BitmapFactory.decodeResource(context.resources, R.drawable.assistant_notification_logo) }.getOrNull()?.let { builder.setLargeIcon(it) }
            }
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
            NoticePicture.path(push.image)?.let { addPicture(context, builder, push.tag, noticeId, push.message, it) }
        }

        /**
         * Puts the camera's snapshot on the notification, above its buttons, a moment after it first shows (so the pop-up is not
         * held up). The snapshot may still be the previous one, so it is fetched again a few seconds later. The notification
         * is not put back if it was dismissed meanwhile, and the update makes no second sound.
         */
        private fun addPicture(context: Context, builder: NotificationCompat.Builder, tag: String?, noticeId: Int, message: String, path: String) {
            val app = context.applicationContext
            kotlin.concurrent.thread(name = "notice-picture") {
                var last: Bitmap? = null
                for (wait in longArrayOf(1_500, 4_500)) {
                    Thread.sleep(wait)
                    val manager = app.getSystemService(NotificationManager::class.java)
                    if (manager.activeNotifications.none { it.id == noticeId && it.tag == tag }) return@thread
                    val picture = NoticePicture.load(app, path) ?: continue
                    if (last != null && last.sameAs(picture)) continue
                    last = picture
                    builder.setOnlyAlertOnce(true)
                        .setLargeIcon(picture)
                        .setStyle(NotificationCompat.BigPictureStyle().bigPicture(picture).bigLargeIcon(null as Bitmap?).setSummaryText(message))
                    runCatching { NotificationManagerCompat.from(app).notify(tag, noticeId, builder.build()) }
                }
            }
        }

        /** A Link button: it opens the same place a tap on the notification would (a camera, a page, a device, Device Health). */
        private fun linkIntent(context: Context, uri: String, code: Int): PendingIntent {
            val link = parseNoticeLink(uri)
            return PendingIntent.getActivity(context, code xor noticeLinkCode(link), noticeIntent(context, link), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        private const val FOREGROUND_ID = 4201
        /** The channel of the small "connected" notice Android requires while the connection runs. */
        const val CHANNEL_SERVICE = "service_hidden"

        /** Opens the app at the notification's link (each link its own request code, so the extras don't mix). */
        private fun openApp(context: Context, url: String? = null): PendingIntent {
            val link = parseNoticeLink(url)
            return PendingIntent.getActivity(
                context, noticeLinkCode(link), noticeIntent(context, link),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        fun createChannels(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            // The connection's notice is created blocked (no notice, no dot), so it never shows; the old visible channel is removed.
            runCatching { manager.deleteNotificationChannel("service") }
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_SERVICE, "Connected to the house", NotificationManager.IMPORTANCE_NONE),
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
