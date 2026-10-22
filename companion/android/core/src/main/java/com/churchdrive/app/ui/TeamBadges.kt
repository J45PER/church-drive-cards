package com.churchdrive.app.ui

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/**
 * Club badges, found by the screen itself from the club's name (a model is bad at finding a real picture address, and a wrong one is
 * a blank badge). Uses TheSportsDB's free team search; the answer is remembered, and a club it doesn't know is not asked about again.
 * Call from a background thread.
 */
object TeamBadges {
    private val found = ConcurrentHashMap<String, String>()
    private val missing = ConcurrentHashMap.newKeySet<String>()

    /** The https address of [name]'s badge, or null. */
    fun lookup(name: String): String? {
        val key = name.trim().lowercase()
        if (key.length < 3) return null
        found[key]?.let { return it }
        if (key in missing) return null
        val url = runCatching { search(name.trim()) }.getOrNull()
        if (url != null) found[key] = url else missing += key
        return url
    }

    private fun search(name: String): String? {
        val c = URL("https://www.thesportsdb.com/api/v1/json/3/searchteams.php?t=" + URLEncoder.encode(name, "UTF-8")).openConnection() as HttpURLConnection
        c.connectTimeout = 6_000
        c.readTimeout = 6_000
        c.setRequestProperty("User-Agent", "ChurchDrive")
        try {
            if (c.responseCode !in 200..299) return null
            val text = c.inputStream.use { it.readBytes() }.toString(Charsets.UTF_8)
            if (text.length > 400_000) return null
            val teams = JSONObject(text).optJSONArray("teams") ?: return null
            // Football first: a name like "Man City" or "Arsenal" can also be a club in another sport.
            for (pass in 0..1) {
                for (i in 0 until teams.length()) {
                    val t = teams.optJSONObject(i) ?: continue
                    if (pass == 0 && !t.optString("strSport").equals("Soccer", ignoreCase = true)) continue
                    httpsOnly(t.optString("strBadge").ifBlank { t.optString("strTeamBadge") })?.let { return it }
                }
            }
            return null
        } finally {
            c.disconnect()
        }
    }
}
