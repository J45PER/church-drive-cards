package com.churchdrive.app.house

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.churchdrive.app.BuildConfig
import com.churchdrive.app.Session
import com.churchdrive.app.freshToken
import com.churchdrive.app.ha.MobileApp
import com.churchdrive.app.ha.WebhookResult
import kotlin.concurrent.thread

/**
 * What the app does for the house outside the app: it registers this phone with Home Assistant, reports where the
 * phone is, and keeps a connection for the house's notifications.
 *
 * Location is reported by Android itself, which wakes the app only when the phone has moved (no always-on service, so
 * no permanent notice). Notifications need [HouseService], whose small "connected" notice Android requires while it runs.
 */
object House {
    /** Registers the phone with Home Assistant if it isn't yet, and gives its webhook id (null when offline). Blocking. */
    fun ensureRegistered(context: Context): String? {
        val session = Session(context)
        session.webhookId?.let { return it }
        val url = session.url ?: return null
        val token = session.freshToken() ?: return null
        val name = MobileApp.deviceName(session.personName, Build.MODEL)
        val body = MobileApp.registrationBody(
            session.deviceId, name, BuildConfig.VERSION_NAME, Build.VERSION.RELEASE, Build.MANUFACTURER, Build.MODEL,
        )
        return MobileApp.register(url, token, body)?.also { session.saveWebhook(it, name) }
    }

    /** Starts or stops everything from what the person has chosen. Called when the choices change, when the app opens and after a reboot. */
    fun sync(context: Context) {
        val session = Session(context)
        val on = session.signedIn
        HouseService.sync(context, on && session.notifyOn)
        syncLocation(context, on && session.locationOn)
        // A phone that isn't registered yet is registered now, in the background.
        if (on && (session.notifyOn || session.locationOn) && session.webhookId == null) thread { ensureRegistered(context) }
    }

    private fun locationIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context, 7, Intent(context, LocationReceiver::class.java),
            // Android puts the location in the intent, so it has to be mutable.
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )

    /** Asks Android to tell the app when the phone moves (or stops asking). The request outlasts the app being closed. */
    @SuppressLint("MissingPermission")
    fun syncLocation(context: Context, on: Boolean) {
        val manager = context.getSystemService(LocationManager::class.java)
        val pending = locationIntent(context)
        runCatching { manager.removeUpdates(pending) }
        if (!on || !Permissions.canLocate(context)) return
        // Network and passive fixes are cheap; GPS is asked only for a fix every few minutes, so the battery isn't drained.
        for ((provider, minTime, minDistance) in listOf(
            Triple(LocationManager.NETWORK_PROVIDER, 5 * 60_000L, 100f),
            Triple(LocationManager.GPS_PROVIDER, 10 * 60_000L, 200f),
            Triple(LocationManager.PASSIVE_PROVIDER, 2 * 60_000L, 50f),
        )) {
            runCatching {
                if (manager.isProviderEnabled(provider)) manager.requestLocationUpdates(provider, minTime, minDistance, pending)
            }
        }
    }

    @Volatile
    private var lastPost = 0L

    /** Sends a position to Home Assistant, at most one a minute and not a fix that could be anywhere in a few streets. Blocking. */
    fun report(context: Context, location: Location) {
        if (location.accuracy > 500f) return
        val now = System.currentTimeMillis()
        if (now - lastPost < 60_000) return
        lastPost = now
        val session = Session(context)
        val url = session.url ?: return
        val webhook = ensureRegistered(context) ?: return
        val battery = context.getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val body = MobileApp.locationBody(
            location.latitude, location.longitude, location.accuracy,
            location.altitude.takeIf { location.hasAltitude() },
            location.speed.takeIf { location.hasSpeed() },
            location.bearing.takeIf { location.hasBearing() },
            battery,
        )
        // Home Assistant has forgotten this phone (someone deleted it there): register it again next time.
        if (MobileApp.post(url, webhook, body) == WebhookResult.Gone) session.clearWebhook()
    }
}

/** Android's message that the phone has moved: sends the position to the house. */
class LocationReceiver : BroadcastReceiver() {
    @Suppress("DEPRECATION")
    override fun onReceive(context: Context, intent: Intent) {
        val locations = buildList {
            intent.getParcelableArrayExtra(LocationManager.KEY_LOCATIONS)?.forEach { (it as? Location)?.let(::add) }
            intent.getParcelableExtra<Location>(LocationManager.KEY_LOCATION_CHANGED)?.let(::add)
        }
        val latest = locations.maxByOrNull { it.time } ?: return
        val pending = goAsync()
        thread {
            try {
                House.report(context.applicationContext, latest)
            } finally {
                pending.finish()
            }
        }
    }
}

/** Starts it all again when the phone starts. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) runCatching { House.sync(context) }
    }
}
