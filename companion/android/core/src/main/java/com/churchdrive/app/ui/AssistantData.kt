package com.churchdrive.app.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

/**
 * How the icon of a "done" card moves while the card is up: the bulb lights or goes out, the blind's slab comes down or goes up, the fan turns, the
 * lock shuts or opens, the volume steps up. Which one a card plays is decided by rules the house sends (see [AssistantData]).
 */
sealed interface DoneMotion {
    /** One icon gives way to another. */
    data class Swap(val from: String, val to: String, val ms: Int) : DoneMotion
    /** [to] is uncovered over [from]: from the top down ([down], a blind's slab coming down under its rail) or from the bottom up. */
    data class Wipe(val from: String, val to: String, val down: Boolean, val ms: Int) : DoneMotion
    data class Spin(val icon: String, val ms: Int, val turns: Int) : DoneMotion
    /** Each icon in turn, ending on the last. */
    data class Cycle(val icons: List<String>, val ms: Int) : DoneMotion
}

/**
 * One rule for choosing a done card's motion. It fits a card whose icon name contains any of [icon] or is one of [exact], contains all of [also] and
 * none of [not], and (when [says] is given) whose detail has a word starting with one of them. [motion] null is a plain tick.
 */
data class DoneRule(val icon: List<String>, val exact: List<String>, val also: List<String>, val not: List<String>, val says: List<String>, val motion: DoneMotion?)

private fun JSONObject.words(key: String): List<String> =
    optJSONArray(key)?.let { a -> (0 until a.length()).mapNotNull { i -> a.optString(i).trim().lowercase().takeIf { it.isNotEmpty() } } }.orEmpty()

private fun parseMotion(o: JSONObject?): DoneMotion? {
    if (o == null) return null
    val ms = o.optInt("ms", 600).coerceIn(50, 10_000)
    val from = o.optString("from")
    val to = o.optString("to")
    return when (o.optString("type")) {
        "swap" -> if (from.isBlank() || to.isBlank()) null else DoneMotion.Swap(from, to, ms)
        "wipe" -> if (from.isBlank() || to.isBlank()) null else DoneMotion.Wipe(from, to, o.optString("dir") != "up", ms)
        "spin" -> o.optString("icon").takeIf { it.isNotBlank() }?.let { DoneMotion.Spin(it, ms, o.optInt("turns", 3).coerceIn(1, 20)) }
        "cycle" -> o.words("icons").takeIf { it.size >= 2 }?.let { DoneMotion.Cycle(it, ms) }
        else -> null
    }
}

/** The rules as the house (or the app's own copy) lists them; anything it cannot read is left out. */
fun parseDoneRules(list: JSONArray?): List<DoneRule> {
    if (list == null) return emptyList()
    return (0 until list.length()).mapNotNull { i ->
        val o = list.optJSONObject(i) ?: return@mapNotNull null
        val rule = DoneRule(o.words("icon"), o.words("is"), o.words("also"), o.words("not"), o.words("says"), parseMotion(o.optJSONObject("motion")))
        rule.takeIf { it.icon.isNotEmpty() || it.exact.isNotEmpty() }
    }
}

/** The misheard words (lower case) and the names that were said. */
fun parseHeard(o: JSONObject?): Map<String, String> {
    if (o == null) return emptyMap()
    val out = LinkedHashMap<String, String>()
    for (key in o.keys()) {
        val wrong = key.trim().lowercase()
        val right = o.optString(key).trim()
        if (wrong.isNotEmpty() && right.isNotEmpty() && wrong.all { it.isLetter() || it == ' ' || it == '-' || it == '\'' }) out[wrong] = right
    }
    return out
}

/** The motion for a "done" card, from the icon and words the house sent with it: the first rule that fits. Null for a plain tick (or a cross, when it failed). */
fun doneMotion(card: AssistantCard, rules: List<DoneRule> = AssistantData.rules): DoneMotion? {
    val f = card.fields
    if (card.kind != "done" || (f.has("ok") && !f.optBoolean("ok", true))) return null
    val icon = f.optString("icon").trim().lowercase()
    if (icon.isEmpty()) return null
    val words = f.optString("detail").lowercase().split(Regex("[^a-z']+")).filter { it.isNotEmpty() }
    val rule = rules.firstOrNull { r ->
        (r.icon.any { it in icon } || icon in r.exact) && r.also.all { it in icon } && r.not.none { it in icon } &&
            (r.says.isEmpty() || r.says.any { s -> words.any { it.startsWith(s) } })
    }
    return rule?.motion
}

/** What was heard, with misheard household names put right ("Hailey's bedroom" is "Hayley's bedroom"). For speech only, never for what is typed. */
fun correctHeard(text: String, heard: Map<String, String> = AssistantData.heard): String {
    if (heard.isEmpty()) return text
    val pattern = Regex("\\b(" + heard.keys.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) } + ")\\b", RegexOption.IGNORE_CASE)
    return pattern.replace(text) { m -> heard[m.value.lowercase()] ?: m.value }
}

/**
 * What the house tells the apps about the assistant besides his name (`church_drive/assist/info`): the rules for a done card's motion, the misheard
 * names to put right and the household's first names. So a fix to any of them is made on the house and needs no new app. Until the house has
 * answered, the last answer kept on the phone is used, and before that the copy built into the app (`clive-assistant.json`, made from
 * design/clive/cards.json by tools/export-clive-assets.mjs).
 */
object AssistantData {
    private const val PREFS = "assistant_data"
    private const val KEY = "served"
    private const val ASSET = "clive-assistant.json"

    var rules by mutableStateOf<List<DoneRule>>(emptyList())
        private set
    var heard by mutableStateOf<Map<String, String>>(emptyMap())
        private set

    /** The household's first names, given to the phone's speech recogniser as words to expect. */
    var names by mutableStateOf<List<String>>(emptyList())
        private set

    private var loaded = false

    private fun use(o: JSONObject) {
        // A house that is older than this (no motions or corrections in its answer) leaves what is already here.
        if (o.has("motions")) rules = parseDoneRules(o.optJSONArray("motions"))
        if (o.has("heard")) heard = parseHeard(o.optJSONObject("heard"))
        if (o.has("names")) names = o.optJSONArray("names")?.let { a -> (0 until a.length()).mapNotNull { i -> a.optString(i).trim().takeIf { it.isNotEmpty() } } }.orEmpty()
    }

    /** The app's own copy, then the house's last answer over it. Called once at start-up. */
    fun load(context: Context) {
        if (loaded) return
        loaded = true
        runCatching { use(JSONObject(context.assets.open(ASSET).bufferedReader().use { it.readText() })) }
        runCatching { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)?.let { use(JSONObject(it)) } }
    }

    /** Uses what the house said ([info] is its `church_drive/assist/info` answer) and keeps it for next time. */
    fun update(context: Context, info: JSONObject) {
        if (!info.has("motions") && !info.has("heard") && !info.has("names")) return
        load(context)
        runCatching { use(info) }
        val keep = JSONObject()
        for (key in listOf("motions", "heard", "names")) if (info.has(key)) keep.put(key, info.opt(key))
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, keep.toString()).apply()
    }

    /** For tests: uses [o] without keeping it. */
    fun useForTest(o: JSONObject) = use(o)
}
