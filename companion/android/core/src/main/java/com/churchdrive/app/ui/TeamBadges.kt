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
        // Europe's big clubs (Champions and Europa League), by the names they are written with. TheSportsDB's search kept missing some of them
        // (Barcelona, Paris Saint-Germain) or finding another sport's club of the same name (Bayern's basketball team, Inter's "Intercity").
        "real madrid" to 86, "barcelona" to 81, "atletico madrid" to 78, "atletico" to 78, "athletic club" to 77, "athletic bilbao" to 77,
        "valencia" to 95, "sevilla" to 559, "villarreal" to 94, "real betis" to 90, "betis" to 90, "real sociedad" to 92,
        "bayern munich" to 5, "bayern munchen" to 5, "bayern" to 5, "borussia dortmund" to 4, "dortmund" to 4, "bayer leverkusen" to 3,
        "leverkusen" to 3, "rb leipzig" to 721, "leipzig" to 721, "vfb stuttgart" to 10, "stuttgart" to 10, "eintracht frankfurt" to 19,
        "inter" to 108, "inter milan" to 108, "internazionale" to 108, "ac milan" to 98, "milan" to 98, "juventus" to 109, "napoli" to 113,
        "ssc napoli" to 113, "roma" to 100, "as roma" to 100, "lazio" to 110, "ss lazio" to 110, "atalanta" to 102, "fiorentina" to 99,
        "paris saint germain" to 524, "paris sg" to 524, "psg" to 524, "marseille" to 516, "olympique marseille" to 516, "monaco" to 548,
        "as monaco" to 548, "lille" to 521, "lille osc" to 521, "lyon" to 523, "olympique lyonnais" to 523, "nice" to 522, "ogc nice" to 522,
        "benfica" to 1903, "sl benfica" to 1903, "sporting cp" to 498, "sporting lisbon" to 498, "sporting" to 498, "porto" to 503,
        "ajax" to 678, "psv" to 674, "psv eindhoven" to 674, "feyenoord" to 675, "club brugge" to 851, "celtic" to 732,
    )

    private fun clean(name: String) = java.text.Normalizer.normalize(name.lowercase(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
        .replace("&", " and ").replace(".", " ").replace("-", " ").replace("manchester ", "man ")
        .replace(Regex("\\b(a?fc|cf)\\b"), " ").replace(Regex("\\s+"), " ").trim()

    private val found = ConcurrentHashMap<String, String>()
    private val missing = ConcurrentHashMap.newKeySet<String>()

    /** What is already known about [name]'s badge without asking anyone: (true, its address), (true, null) for a club known to have none, or (false, null) when not yet found. */
    fun peek(name: String): Pair<Boolean, String?> {
        val key = name.trim().lowercase()
        if (key.length < 3) return true to null
        found[key]?.let { return true to it }
        KNOWN[clean(name)]?.let { id -> return true to "https://crests.football-data.org/$id.png" }
        return (key in missing) to null
    }

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
