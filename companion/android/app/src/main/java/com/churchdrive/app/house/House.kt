package com.churchdrive.app.house

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.Location
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkRequest
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
        session.webhookId?.let { rename(context, session, it); return it }
        val url = session.url ?: return null
        val token = session.freshToken() ?: return null
        val name = desiredName(context, session)
        val body = MobileApp.registrationBody(
            session.deviceId, name, "${BuildConfig.VERSION_NAME} (${BuildConfig.BUILD})", Build.VERSION.RELEASE, Build.MANUFACTURER, Build.MODEL,
        )
        return MobileApp.register(url, token, body)?.also {
            session.saveWebhook(it, name)
            PhoneSetupStore(context).registeredVersion = appVersion()
        }
    }

    /** The version text the house is told: "0.98.0 (123)". The house compares the part before the bracket with its own. */
    private fun appVersion() = "${BuildConfig.VERSION_NAME} (${BuildConfig.BUILD})"

    /**
     * The app's own connection to the house (and its small "connected" notice) is only for phones Google push doesn't reach:
     * once the house has this phone's Google token, notifications come through Google and the connection is stopped.
     */
    fun syncNotifications(context: Context) {
        val session = Session(context)
        HouseService.sync(context, com.churchdrive.app.ha.connectionNeeded(session.signedIn, session.notifyOn, session.googlePush))
    }

    /** The phone's own name (the one the Home Assistant app on it uses), else the person and model, with "-CDA" after it. */
    private fun desiredName(context: Context, session: Session): String {
        val phoneName = runCatching { android.provider.Settings.Global.getString(context.contentResolver, "device_name") }.getOrNull()
        return MobileApp.cdaDeviceName(phoneName, session.personName, Build.MODEL)
    }

    @Volatile
    private var lastRename = 0L

    /** A phone registered under an older name is renamed in Home Assistant (at most every ten minutes until it works). Blocking. */
    private fun rename(context: Context, session: Session, webhook: String) {
        val name = desiredName(context, session)
        val store = PhoneSetupStore(context)
        // The house keeps the version the phone registered with until it is told otherwise, so a new version tells it too.
        if (session.deviceName == name && store.registeredVersion == appVersion()) return
        val now = System.currentTimeMillis()
        if (now - lastRename < 10 * 60_000L) return
        lastRename = now
        val url = session.url ?: return
        val body = MobileApp.updateRegistrationBody(appVersion(), name, Build.VERSION.RELEASE, Build.MANUFACTURER, Build.MODEL)
        if (MobileApp.post(url, webhook, body) == WebhookResult.Ok) {
            session.saveWebhook(webhook, name)
            store.registeredVersion = appVersion()
        }
    }

    /** How often an unchanged set-up report is sent again (a changed one is sent at once). */
    private const val REPORT_AGAIN_MS = 6 * 3_600_000L

    /**
     * Tells the house whether this phone allows what it needs (see [PhoneSetup]): a "Church Drive phone setup" problem sensor on
     * the phone's device. Only for a phone that is already registered, so a person who has switched everything off is not
     * registered by it. Also tells the house a new app version. Blocking.
     */
    fun reportSetup(context: Context) {
        val session = Session(context)
        if (!session.signedIn || session.webhookId == null) return
        val url = session.url ?: return
        val webhook = ensureRegistered(context) ?: return
        val state = PhoneSetup.read(context, session.notifyOn, session.locationOn)
        val missing = PhoneSetup.missing(state).map { it.key }
        val body = MobileApp.phoneSetupBody(
            missing,
            linkedMapOf(
                "notifications" to (state.notifyOn && state.canNotify),
                "location_shared" to (state.locationOn && state.canLocate),
                "precise_location" to state.precise,
                "location_all_the_time" to state.always,
                "battery_unrestricted" to state.batteryFree,
            ),
            appVersion(),
        )
        val store = PhoneSetupStore(context)
        val text = body.toString()
        val now = System.currentTimeMillis()
        if (text == store.lastReport && now - store.lastReportAt < REPORT_AGAIN_MS) return
        when (MobileApp.post(url, webhook, body)) {
            WebhookResult.Ok -> { store.lastReport = text; store.lastReportAt = now }
            WebhookResult.Gone -> session.clearWebhook()
            WebhookResult.Failed -> Unit
        }
    }

    /** Starts or stops everything from what the person has chosen. Called when the choices change, when the app opens and after a reboot. */
    fun sync(context: Context) {
        val session = Session(context)
        val on = session.signedIn
        syncNotifications(context)
        // Google push: give the house this phone's token (it keeps the app's own connection only until then).
        if (on && session.notifyOn) GooglePush.register(context)
        syncLocation(context, on && session.locationOn)
        syncWifi(context, on && session.locationOn)
        // A phone that isn't registered yet is registered now, in the background.
        if (on && (session.notifyOn || session.locationOn) && session.webhookId == null) thread { ensureRegistered(context) }
        if (on && session.webhookId != null) thread { reportSetup(context) }
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

    /** A fix less certain than this is not reported: the house counts a position's uncertainty as room to be inside the zone, so a coarse one near home reads as home. */
    private const val MAX_ACCURACY_M = 200f

    private fun batteryPercent(context: Context): Int =
        context.getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

    /** The home zone, asked of the house at most once a day (and again a quarter of an hour after a failed ask). Blocking. */
    private fun homeZone(url: String, webhook: String, store: HomeWifiStore): HomeZone? {
        val now = System.currentTimeMillis()
        val have = store.zone
        if (have != null && now - store.zoneAt < DAY_MS) return have
        val found = HomeWifi.parseHome(MobileApp.ask(url, webhook, MobileApp.getZonesBody()))
        if (found != null) {
            store.zone = found
            store.zoneAt = now
        } else {
            store.zoneAt = now - DAY_MS + 15 * 60_000L
        }
        return found ?: have
    }

    private const val DAY_MS = 24 * 3_600_000L

    /** Counts a sighting of the Wi-Fi the phone is on while GPS says it is inside the home zone (see [HomeWifi]). */
    private fun learnWifi(store: HomeWifiStore, ssid: String?, away: Double, accuracy: Float, zone: HomeZone) {
        val now = System.currentTimeMillis()
        if (ssid == null || now - store.lastLearnAt < HomeWifi.LEARN_GAP_MS) return
        val before = store.counts
        val after = HomeWifi.learn(before, ssid, away, accuracy, zone.radius)
        if (after != before) {
            store.counts = after
            store.lastLearnAt = now
        }
    }

    /** Sends a position to Home Assistant, at most one a minute and not a fix that could be anywhere in a few streets. Blocking. */
    fun report(context: Context, location: Location) {
        if (location.accuracy > MAX_ACCURACY_M) return
        val store = HomeWifiStore(context)
        val now = System.currentTimeMillis()
        // The first fix after leaving the home Wi-Fi is wanted at once, whatever was sent a moment ago.
        val urgent = store.takeForceFix()
        if (!urgent && now - lastPost < 60_000) return
        lastPost = now
        val session = Session(context)
        val url = session.url ?: return
        val webhook = ensureRegistered(context) ?: return
        val battery = batteryPercent(context)
        // On the home Wi-Fi and near home, the phone is home: its GPS wandering a few tens of metres must not say otherwise.
        val zone = homeZone(url, webhook, store)
        var body = MobileApp.locationBody(
            location.latitude, location.longitude, location.accuracy,
            location.altitude.takeIf { location.hasAltitude() },
            location.speed.takeIf { location.hasSpeed() },
            location.bearing.takeIf { location.hasBearing() },
            battery,
        )
        if (zone != null) {
            val away = HomeWifi.distanceM(zone.latitude, zone.longitude, location.latitude, location.longitude)
            val ssid = currentWifiName(context)
            learnWifi(store, ssid, away, location.accuracy, zone)
            val onHome = ssid != null && ssid in store.networks()
            if (HomeWifi.decide(onHome, away) == WifiDecision.Home) body = homeBody(zone, battery)
        }
        // Home Assistant has forgotten this phone (someone deleted it there): register it again next time.
        if (MobileApp.post(url, webhook, body) == WebhookResult.Gone) session.clearWebhook()
    }

    /** The house's own position, certain to a few metres: what a phone on the home Wi-Fi reports. */
    private fun homeBody(zone: HomeZone, battery: Int) =
        MobileApp.locationBody(zone.latitude, zone.longitude, 5f, null, null, null, battery)

    // ---- Home Wi-Fi: say home when the phone joins it, and look where the phone is the moment it leaves it ----

    private fun wifiIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context, 8, Intent(context, WifiReceiver::class.java),
            // Android adds to the intent, so it has to be mutable.
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )

    /** Asks Android to tell the app when the phone's Wi-Fi connects, changes or drops (or stops asking). The request outlasts the app being closed. */
    fun syncWifi(context: Context, on: Boolean) {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return
        val pending = wifiIntent(context)
        runCatching { manager.unregisterNetworkCallback(pending) }
        if (!on) return
        val request = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()
        runCatching { manager.registerNetworkCallback(request, pending) }
    }

    @SuppressLint("MissingPermission")
    private fun lastKnown(context: Context): Location? {
        if (!Permissions.canLocate(context)) return null
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        return listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
            .maxByOrNull { it.time }
    }

    /** One fresh position from each provider, to the same receiver as the others. */
    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun askFreshFix(context: Context) {
        if (!Permissions.canLocate(context)) return
        val manager = context.getSystemService(LocationManager::class.java) ?: return
        val pending = locationIntent(context)
        for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            runCatching { if (manager.isProviderEnabled(provider)) manager.requestSingleUpdate(provider, pending) }
        }
    }

    /**
     * The phone's Wi-Fi changed. Joining the home Wi-Fi says "home" at once (unless the last position was clearly somewhere else);
     * leaving it asks for a fresh position straight away, so a departure is seen when it happens and not at the next movement report.
     * Blocking.
     */
    fun wifiChanged(context: Context) {
        val session = Session(context)
        if (!session.signedIn || !session.locationOn) return
        val store = HomeWifiStore(context)
        val ssid = currentWifiName(context)
        val onHome = ssid != null && ssid in store.networks()
        val was = store.onHomeWifi
        if (onHome == was) return
        store.onHomeWifi = onHome
        if (onHome) {
            val url = session.url ?: return
            val webhook = ensureRegistered(context) ?: return
            val zone = homeZone(url, webhook, store) ?: return
            val last = lastKnown(context)?.takeIf { System.currentTimeMillis() - it.time < 15 * 60_000L }
            val away = last?.let { HomeWifi.distanceM(zone.latitude, zone.longitude, it.latitude, it.longitude) }
            if (HomeWifi.decide(true, away) != WifiDecision.Home) return
            if (MobileApp.post(url, webhook, homeBody(zone, batteryPercent(context))) == WebhookResult.Gone) session.clearWebhook()
        } else {
            store.wantFreshFix()
            askFreshFix(context)
        }
    }
}

/** Android's message that the phone's Wi-Fi changed: see [House.wifiChanged]. */
class WifiReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        thread {
            try {
                runCatching { House.wifiChanged(context.applicationContext) }
            } finally {
                pending.finish()
            }
        }
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
        // An app update ends the location and Wi-Fi requests the old version had made, so they are made again.
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) runCatching { House.sync(context) }
    }
}
