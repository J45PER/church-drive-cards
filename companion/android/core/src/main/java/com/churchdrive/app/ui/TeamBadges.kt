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
    /** Clubs whose crests football-data.org serves at a fixed address (name as [clean] makes it, then its id there). */
    private val KNOWN = mapOf(
        "arsenal" to 57, "aston villa" to 58, "chelsea" to 61, "everton" to 62, "fulham" to 63, "liverpool" to 64,
        "man city" to 65, "man united" to 66, "man utd" to 66, "newcastle" to 67, "newcastle united" to 67,
        "tottenham" to 73, "tottenham hotspur" to 73, "spurs" to 73, "wolves" to 76, "wolverhampton wanderers" to 76,
        "brighton" to 397, "brighton and hove albion" to 397, "burnley" to 328, "crystal palace" to 354, "leeds" to 341,
        "leeds united" to 341, "brentford" to 402, "nottingham forest" to 351, "nottm forest" to 351, "sunderland" to 71,
        "west ham" to 563, "west ham united" to 563, "bournemouth" to 1044, "southampton" to 340, "leicester" to 338,
        "leicester city" to 338, "ipswich" to 349, "ipswich town" to 349,
    )

    private fun clean(name: String) = name.lowercase().replace("&", " and ").replace(".", " ").replace("manchester ", "man ")
        .replace(Regex("\\b(a?fc|cf)\\b"), " ").replace(Regex("\\s+"), " ").trim()

    private val found = ConcurrentHashMap<String, String>()
    private val missing = ConcurrentHashMap.newKeySet<String>()

    /** The https address of [name]'s badge, or null. */
    fun lookup(name: String): String? {
        val key = name.trim().lowercase()
        if (key.length < 3) return null
        found[key]?.let { return it }
        KNOWN[clean(name)]?.let { id -> return "https://crests.football-data.org/$id.png".also { found[key] = it } }
        if (key in missing) return null
        val url = runCatching { search(name.trim()) }.getOrNull()
        if (url != null) found[key] = url else missing += key
        return url
    }

    private fun search(name: String): String? {
        for (apiKey in listOf("123", "3")) searchWith(apiKey, name)?.let { return it }
        return null
    }

    private fun searchWith(apiKey: String, name: String): String? {
        val c = URL("https://www.thesportsdb.com/api/v1/json/$apiKey/searchteams.php?t=" + URLEncoder.encode(name, "UTF-8")).openConnection() as HttpURLConnection
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
