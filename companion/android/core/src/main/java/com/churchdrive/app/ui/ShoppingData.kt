package com.churchdrive.app.ui

import org.json.JSONArray
import org.json.JSONObject

/** One thing to buy. [status] is "open", "bought" or "unavailable". */
data class ShoppingItem(val id: String, val text: String, val status: String)

/** A shopping list: one task on each person's to-do list, with these items inside. */
data class ShoppingList(
    val id: String,
    val name: String,
    val people: List<String>,
    val date: String,
    val time: String,
    val places: List<ShoppingPlace>,
    val done: Boolean,
    val items: List<ShoppingItem>,
    /** "shopping" (the default) or "steps": a checklist that works the same way with different words. */
    val kind: String = "shopping",
) {
    val open: Int get() = items.count { it.status == "open" }
    val isSteps: Boolean get() = kind == "steps"
    val title: String get() = name.ifBlank { if (isSteps) "Steps" else "Shopping" }
}

/** Somewhere a list will be bought, found with a Google Maps search (any shop anywhere), kept with its coordinates. */
data class ShoppingPlace(val name: String, val address: String, val lat: Double, val lon: Double) {
    fun toJson(): JSONObject = JSONObject().put("name", name).put("address", address).put("lat", lat).put("lon", lon)
}

fun parsePlace(o: JSONObject): ShoppingPlace? =
    if (o.has("lat") && o.has("lon")) ShoppingPlace(o.optString("name"), o.optString("address"), o.optDouble("lat"), o.optDouble("lon")) else null

/** The Google Maps link that starts directions to [p], for a browser if no maps app takes it. */
fun mapsWebUrl(p: ShoppingPlace): String = "https://www.google.com/maps/dir/?api=1&destination=${p.lat},${p.lon}"

/** The link the Google Maps app opens straight into turn-by-turn navigation. */
fun navigationUri(p: ShoppingPlace): String = "google.navigation:q=${p.lat},${p.lon}"

/** Everything `church_drive/shopping` returns. [mine] is the signed-in person's first name, [missed] what wasn't available last time. */
data class ShoppingState(
    val lists: List<ShoppingList>,
    val mine: String,
    val missed: List<String>,
    val people: List<String>,
)

private fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else (0 until length()).map { optString(it) }.filter { it.isNotEmpty() }
private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

fun parseShoppingList(o: JSONObject): ShoppingList = ShoppingList(
    id = o.optString("id"),
    name = o.optString("name"),
    people = o.optJSONArray("people").strings(),
    date = o.optString("date"),
    time = o.optString("time"),
    places = o.optJSONArray("places").objects().mapNotNull { parsePlace(it) },
    done = o.optBoolean("done"),
    items = o.optJSONArray("items").objects().map { ShoppingItem(it.optString("id"), it.optString("text"), it.optString("status", "open")) },
    kind = if (o.optString("kind") == "steps") "steps" else "shopping",
)

/** The reply of `church_drive/shopping`, or null if it isn't one. */
fun parseShopping(result: Any?): ShoppingState? {
    val o = result as? JSONObject ?: return null
    val lists = o.optJSONArray("lists") ?: return null
    return ShoppingState(
        lists = lists.objects().map { parseShoppingList(it) }.filter { it.id.isNotEmpty() },
        mine = o.optString("mine"),
        missed = o.optJSONArray("missed").objects().map { it.optString("text") }.filter { it.isNotEmpty() },
        people = o.optJSONArray("people").strings(),
    )
}

/** The lists still to do, soonest date first and undated last, optionally only those [first] is on. */
fun openLists(state: ShoppingState, first: String? = null): List<ShoppingList> =
    state.lists.filter { !it.isSteps && !it.done && (first.isNullOrBlank() || first in it.people) }
        .sortedWith(compareBy<ShoppingList>({ it.date.isEmpty() }, { it.date }))

private val MARKER = Regex("church-drive-shopping:([0-9a-f]{6,32})")

/** The shopping list a to-do task stands for, from the marker in its description, or "". */
fun shoppingIdOf(description: String?): String = MARKER.find(description.orEmpty())?.groupValues?.get(1).orEmpty()

private val STEPS_MARKER = Regex("church-drive-shopping:[0-9a-f]{6,32} steps")

/** Whether a to-do task's description marks a steps list (the marker is followed by " steps"). */
fun isStepsTask(description: String?): Boolean = STEPS_MARKER.containsMatchIn(description.orEmpty())

/** What to show under a shopping or steps task instead of its raw description: how many are left. */
fun shoppingNote(description: String?): String {
    val n = description.orEmpty().lines().count { it.trimStart().startsWith("•") }
    return if (isStepsTask(description)) {
        if (n == 0) "Steps" else "$n to do"
    } else {
        if (n == 0) "Shopping list" else "$n to get"
    }
}

/** The list id at the end of a reminder's link (`/dashboard-mobile/shopping?list=<id>`), or null. */
fun shoppingIdFromLink(url: String?): String? =
    Regex("""[?&]list=([0-9a-f]{6,32})""").find(url.orEmpty())?.groupValues?.get(1)

/** What a person typed into the add box: one item per line, comma or semicolon. */
fun splitItems(text: String): List<String> = text.split('\n', ',', ';').map { it.trim() }.filter { it.isNotEmpty() }

/** One of the lists a batch of shared items could join. */
data class ShoppingOption(val id: String, val name: String, val date: String, val open: Int)

/** For a batch with shopping in it: the lists it could join, the one it would by default ("new" for none) and what wasn't available. */
data class ShoppingChoice(val lists: List<ShoppingOption>, val default: String, val missed: List<String>)

fun parseShoppingChoice(o: JSONObject?): ShoppingChoice? {
    o ?: return null
    return ShoppingChoice(
        lists = o.optJSONArray("lists").objects().map { ShoppingOption(it.optString("id"), it.optString("name"), it.optString("date"), it.optInt("open")) },
        default = o.optString("default", "new").ifEmpty { "new" },
        missed = o.optJSONArray("missed").strings(),
    )
}

/** The words for a list in the "add these to…" choice: "Weekly shop · tomorrow (5)". */
fun optionLabel(o: ShoppingOption): String =
    listOf(o.name, whenText(o.date.ifEmpty { null })).filter { it.isNotBlank() }.joinToString(" · ") + " (${o.open})"

const val NEW_LIST = "New list"

/** The `shopping` part of `church_drive/inbox/confirm`: which list the shared items join, or a new one. */
fun shoppingConfirm(target: String, newName: String): JSONObject =
    JSONObject().put("target", target).also { if (target == "new" && newName.isNotBlank()) it.put("name", newName.trim()) }
