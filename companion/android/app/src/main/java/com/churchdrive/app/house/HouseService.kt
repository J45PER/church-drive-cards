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
 * Keeps the house in touch with the phone while the app is closed: it receives the house's notifications (over its own
 * connection to Home Assistant) and reports where the phone is, so the house knows who is home, at work or out.
 * Android shows a quiet "connected" notification while it runs.
 */
class HouseService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var work: Job? = null
    private var client: HaClient? = null
    private var locationListener: LocationListener? = null
    private var lastPost = 0L
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
        locationListener?.let { l -> runCatching { getSystemService(LocationManager::class.java).removeUpdates(l) } }
        locationListener = null
        client?.disconnect()
        client = null
    }

    /** Starts again from what the person has chosen (called whenever the settings change). */
    private fun restart() {
        stopAll()
        val session = Session(this)
        if (!session.signedIn || !(session.notifyOn || session.locationOn)) {
            stopSelf()
            return
        }
        work = scope.launch { run(session) }
    }

    private fun foregroundNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Church Drive is connected")
            .setContentText("Keeping in touch with the house")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
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
        var webhook = session.webhookId
        while (webhook == null) {
            val token = freshToken(session)
            if (token != null) {
                val name = MobileApp.deviceName(session.personName, Build.MODEL)
                val body = MobileApp.registrationBody(
                    session.deviceId, name, BuildConfig.VERSION_NAME, Build.VERSION.RELEASE, Build.MANUFACTURER, Build.MODEL,
                )
                webhook = MobileApp.register(url, token, body)?.also { session.saveWebhook(it, name) }
            }
            if (webhook == null) delay(60_000)
        }
        if (session.locationOn) withContext(Dispatchers.Main) { startLocation(session, url, webhook) }
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

    private fun show(event: JSONObject) {
        val push = MobileApp.parsePush(event) ?: return
        if (!Permissions.canNotify(this)) return
        ensureChannel(this, push.channel)
        val n = NotificationCompat.Builder(this, push.channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(push.title)
            .setContentText(push.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(push.message))
            .setAutoCancel(true)
            .setContentIntent(openApp(this))
            .build()
        // The same tag replaces the earlier notification, as in the Home Assistant app.
        runCatching { NotificationManagerCompat.from(this).notify(push.tag, push.tag?.hashCode() ?: System.currentTimeMillis().toInt(), n) }
    }

    @SuppressLint("MissingPermission")
    private fun startLocation(session: Session, url: String, webhook: String) {
        if (!Permissions.canLocate(this)) return
        val manager = getSystemService(LocationManager::class.java)
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) = report(session, url, webhook, location)
        }
        locationListener = listener
        // Network and passive fixes are cheap; GPS is asked only for a fix every few minutes, so the battery isn't drained.
        for ((provider, minTime, minDistance) in listOf(
            Triple(LocationManager.NETWORK_PROVIDER, 5 * 60_000L, 100f),
            Triple(LocationManager.GPS_PROVIDER, 10 * 60_000L, 200f),
            Triple(LocationManager.PASSIVE_PROVIDER, 2 * 60_000L, 50f),
        )) {
            runCatching {
                if (manager.isProviderEnabled(provider)) {
                    manager.requestLocationUpdates(provider, minTime, minDistance, listener, Looper.getMainLooper())
                    manager.getLastKnownLocation(provider)?.let { report(session, url, webhook, it) }
                }
            }
        }
    }

    private fun report(session: Session, url: String, webhook: String, location: Location) {
        // A fix that could be anywhere in a few streets isn't worth sending; and not more than one a minute.
        if (location.accuracy > 500f) return
        val now = System.currentTimeMillis()
        if (now - lastPost < 60_000) return
        lastPost = now
        val battery = getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val body = MobileApp.locationBody(
            location.latitude, location.longitude, location.accuracy,
            location.altitude.takeIf { location.hasAltitude() },
            location.speed.takeIf { location.hasSpeed() },
            location.bearing.takeIf { location.hasBearing() },
            battery,
        )
        scope.launch {
            // Home Assistant has forgotten this phone (someone deleted it there): register it again.
            if (MobileApp.post(url, webhook, body) == WebhookResult.Gone) {
                session.clearWebhook()
                withContext(Dispatchers.Main) { restart() }
            }
        }
    }

    companion object {
        private const val FOREGROUND_ID = 4201
        private const val CHANNEL_SERVICE = "service"

        private fun openApp(context: Context): PendingIntent =
            PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

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

        /** Starts the service when the person has asked for notifications or location, stops it when neither. */
        fun sync(context: Context) {
            val session = Session(context)
            val intent = Intent(context, HouseService::class.java)
            if (session.signedIn && (session.notifyOn || session.locationOn)) ContextCompat.startForegroundService(context, intent)
            else context.stopService(intent)
        }
    }
}

/** Starts the service again when the phone starts, if it was on. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) runCatching { HouseService.sync(context) }
    }
}
