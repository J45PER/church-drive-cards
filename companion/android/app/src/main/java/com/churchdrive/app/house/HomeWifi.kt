package com.churchdrive.app.house

import android.content.Context
import android.net.wifi.WifiManager
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** The house's home zone as the house reports it. */
data class HomeZone(val latitude: Double, val longitude: Double, val radius: Double)

/** What to report for a position: the position as it is, or the house itself. */
enum class WifiDecision { Raw, Home }

/**
 * Home Wi-Fi as a second opinion on who is home. GPS wanders by tens of metres and a phone walking along the edge of the home zone
 * goes in and out of it; a phone that is on the home Wi-Fi is, for the house's purposes, home. The app learns which network is
 * the home one by itself: a name seen several times, well apart, while GPS says the phone is inside the home zone. Nothing is
 * set up, and nothing leaves the phone but the position it already reports.
 */
object HomeWifi {
    /** Sightings (at least [LEARN_GAP_MS] apart) inside the home zone before a network counts as home. */
    const val LEARN_SIGHTINGS = 3
    const val LEARN_GAP_MS = 10 * 60_000L

    /** A network's name is only believed to be the home one when the phone is not further than this from home: the same name elsewhere is another network. */
    const val SNAP_RADIUS_M = 300.0

    /** The most networks that count as home (a router's two bands and a guest network). */
    const val MAX_NETWORKS = 3

    /** Metres between two positions. */
    fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dp = p2 - p1
        val dl = Math.toRadians(lon2 - lon1)
        val h = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
        return 2 * r * asin(sqrt(h))
    }

    /** The network name from what Android gives (quoted; "<unknown ssid>" when it may not say), or null. */
    fun cleanSsid(raw: String?): String? {
        val name = raw?.trim()?.removeSurrounding("\"")?.trim() ?: return null
        return if (name.isEmpty() || name == "<unknown ssid>" || name == "0x") null else name
    }

    /** [counts] after one more sighting of [ssid], only when the phone is well placed inside the home zone. */
    fun learn(counts: Map<String, Int>, ssid: String?, distanceFromHome: Double, accuracy: Float, radius: Double): Map<String, Int> {
        if (ssid == null || accuracy > 50f || distanceFromHome > radius) return counts
        return counts + (ssid to ((counts[ssid] ?: 0) + 1))
    }

    /** The networks seen often enough to be home. */
    fun learned(counts: Map<String, Int>): Set<String> =
        counts.filterValues { it >= LEARN_SIGHTINGS }.entries.sortedByDescending { it.value }.take(MAX_NETWORKS).map { it.key }.toSet()

    /** On the home Wi-Fi and not clearly somewhere else (or nowhere known yet): home. Otherwise the position stands. */
    fun decide(onHomeWifi: Boolean, distanceFromHome: Double?): WifiDecision =
        if (onHomeWifi && (distanceFromHome == null || distanceFromHome <= SNAP_RADIUS_M)) WifiDecision.Home else WifiDecision.Raw

    /** The home zone from the house's answer to `get_zones` (a list of zone states), or null. */
    fun parseHome(answer: String?): HomeZone? = runCatching {
        val zones = JSONArray(answer.orEmpty())
        var found: HomeZone? = null
        for (i in 0 until zones.length()) {
            val zone = zones.optJSONObject(i) ?: continue
            if (zone.optString("entity_id") != "zone.home") continue
            val a = zone.optJSONObject("attributes") ?: continue
            if (!a.has("latitude") || !a.has("longitude")) continue
            found = HomeZone(a.getDouble("latitude"), a.getDouble("longitude"), a.optDouble("radius", 100.0))
            break
        }
        found
    }.getOrNull()
}

/** The name of the Wi-Fi network the phone is on, or null (not on Wi-Fi, or Android won't say without location permission and location switched on). */
@Suppress("DEPRECATION")
fun currentWifiName(context: Context): String? {
    if (!Permissions.canLocate(context)) return null
    val wifi = context.applicationContext.getSystemService(WifiManager::class.java) ?: return null
    return HomeWifi.cleanSsid(runCatching { wifi.connectionInfo?.ssid }.getOrNull())
}

/** What this install has learned about home Wi-Fi: plain preferences. */
class HomeWifiStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("church_drive_home_wifi", Context.MODE_PRIVATE)

    /** How often each network name has been seen with the phone inside the home zone. */
    var counts: Map<String, Int>
        get() = runCatching {
            val o = JSONObject(prefs.getString("counts", "{}").orEmpty())
            o.keys().asSequence().associateWith { o.getInt(it) }
        }.getOrDefault(emptyMap())
        set(v) = prefs.edit().putString("counts", JSONObject(v).toString()).apply()

    fun networks(): Set<String> = HomeWifi.learned(counts)

    var lastLearnAt: Long
        get() = prefs.getLong("last_learn_at", 0L)
        set(v) = prefs.edit().putLong("last_learn_at", v).apply()

    /** The home zone as last heard from the house, and when. */
    var zone: HomeZone?
        get() = if (prefs.contains("zone_lat")) HomeZone(
            Double.fromBits(prefs.getLong("zone_lat", 0L)), Double.fromBits(prefs.getLong("zone_lon", 0L)), Double.fromBits(prefs.getLong("zone_radius", 0L)),
        ) else null
        set(v) {
            if (v == null) prefs.edit().remove("zone_lat").remove("zone_lon").remove("zone_radius").apply()
            else prefs.edit().putLong("zone_lat", v.latitude.toRawBits()).putLong("zone_lon", v.longitude.toRawBits()).putLong("zone_radius", v.radius.toRawBits()).apply()
        }
    var zoneAt: Long
        get() = prefs.getLong("zone_at", 0L)
        set(v) = prefs.edit().putLong("zone_at", v).apply()

    /** Whether the phone was on the home Wi-Fi the last time it looked, so a change is noticed once. */
    var onHomeWifi: Boolean
        get() = prefs.getBoolean("on_home_wifi", false)
        set(v) = prefs.edit().putBoolean("on_home_wifi", v).apply()

    /** The next position should be reported at once, not held back by the one-a-minute limit (the phone has just left the home Wi-Fi). */
    private var forceFix: Boolean
        get() = prefs.getBoolean("force_fix", false)
        set(v) = prefs.edit().putBoolean("force_fix", v).apply()

    fun wantFreshFix() { forceFix = true }

    fun takeForceFix(): Boolean = forceFix.also { if (it) forceFix = false }
}
