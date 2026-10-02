package com.churchdrive.app.ui

import org.json.JSONArray
import org.json.JSONObject

/**
 * Builds the app's lighting layout from the dashboard's own light cards, so the app follows what is set in
 * Home Assistant: the rooms, their zones and lights, and each card's scene list.
 *
 * Home: the "Lights" section panel on the view with path `home`. Lighting: every section panel on the view
 * with path `lighting`, each a floor. Anything it can't read falls back to the built-in layout.
 */
object DashboardLights {
    const val DASHBOARD = "dashboard-mobile"
    private const val DEFAULT_MAX_SCENES = 8
    private val WHITE = listOf("bright", "dimmed", "relax", "nightlight")

    fun parse(config: JSONObject): LightLayout {
        val views = config.optJSONArray("views") ?: return LightLayout.Fallback
        var home: List<LightRoom>? = null
        var floors: List<Pair<String, List<LightRoom>>>? = null
        for (i in 0 until views.length()) {
            val view = views.optJSONObject(i) ?: continue
            when (view.optString("path")) {
                "home" -> home = panels(view).firstOrNull { it.optString("title").equals("Lights", ignoreCase = true) }
                    ?.let { rooms(it, views) }
                "lighting" -> floors = panels(view).map { it.optString("title") to rooms(it, views) }.filter { it.second.isNotEmpty() }
            }
        }
        return LightLayout(
            home = home?.takeIf { it.isNotEmpty() } ?: LightLayout.Fallback.home,
            floors = floors?.takeIf { it.isNotEmpty() } ?: LightLayout.Fallback.floors,
        )
    }

    /** All objects under [node] whose type matches, in order, not looking inside a match. */
    private fun findAll(node: Any?, found: MutableList<JSONObject> = mutableListOf(), match: (String) -> Boolean): List<JSONObject> {
        when (node) {
            is JSONObject -> if (match(node.optString("type"))) found += node
            else node.keys().forEach { findAll(node.opt(it), found, match) }
            is JSONArray -> for (i in 0 until node.length()) findAll(node.opt(i), found, match)
        }
        return found
    }

    private fun panels(view: JSONObject) = findAll(view) { it == "custom:section-panel-card" }

    private val MIRRORS = setOf("custom:mirror-card", "custom:mirror-card-beta")

    /**
     * The rooms in a panel, in order. A mirror card stands for the card it points at (another page's light
     * card), so it's replaced by that card's room: the original is set up once in Home Assistant.
     */
    private fun rooms(panel: JSONObject, views: JSONArray): List<LightRoom> {
        val cards = findAll(panel.opt("cards")) { it == "custom:light-control-card" || it in MIRRORS }
        return cards.mapNotNull { card ->
            if (card.optString("type") in MIRRORS) resolveMirror(card, views)?.let { room(it) } else room(card)
        }
    }

    /** The card a mirror points at: its page is `view` (a path, or a position), and it's found by key. */
    fun resolveMirror(mirror: JSONObject, views: JSONArray): JSONObject? {
        // Only mirrors of this dashboard can be followed (the app reads one dashboard).
        val dashboard = mirror.optString("dashboard", "this")
        if (dashboard != "this" && dashboard != DASHBOARD) return null
        val ref = mirror.optString("view")
        val view = (0 until views.length()).mapNotNull { views.optJSONObject(it) }.firstOrNull { it.optString("path") == ref }
            ?: ref.toIntOrNull()?.let { views.optJSONObject(it) }
            ?: return null
        return mirrorable(view).firstOrNull { it.first == mirror.optString("source") }?.second
    }

    private val CONTAINERS = setOf(
        "custom:auto-layout-card", "custom:section-panel-card", "custom:nav-bar-card", "custom:section-title-card",
        "vertical-stack", "horizontal-stack", "grid", "conditional", "custom:stack-in-card",
    )

    /** What a card is about, as the first part of its key: the same rules as the Mirror Card (mirror.js). */
    fun identity(card: JSONObject): String {
        card.optString("area").takeIf { it.isNotBlank() }?.let { return "area:${it.trim()}" }
        (card.opt("entity") as? String)?.takeIf { it.isNotBlank() }?.let { return "entity:${it.trim()}" }
        (card.optString("name").takeIf { it.isNotBlank() } ?: card.optString("title").takeIf { it.isNotBlank() })
            ?.let { return "name:${it.trim()}" }
        return "type:${card.optString("type", "card").trim()}"
    }

    /** Every card on a page that can be mirrored, in page order, as (key, card). Repeats get "#2", "#3". */
    fun mirrorable(view: JSONObject): List<Pair<String, JSONObject>> {
        val found = mutableListOf<Pair<String, JSONObject>>()
        val seen = mutableMapOf<String, Int>()
        fun walk(node: Any?) {
            when (node) {
                is JSONArray -> for (i in 0 until node.length()) walk(node.opt(i))
                is JSONObject -> {
                    val type = node.optString("type")
                    if (type.isNotEmpty() && type !in CONTAINERS && type !in MIRRORS) {
                        val base = identity(node)
                        val n = (seen[base] ?: 0) + 1
                        seen[base] = n
                        found += (if (n > 1) "$base#$n" else base) to node
                    }
                    listOf("cards", "card", "sections").forEach { k -> node.opt(k)?.let { walk(it) } }
                }
            }
        }
        walk(view.opt("sections") ?: view.opt("cards"))
        return found
    }

    private fun entityOf(item: Any?): String? = when (item) {
        is String -> item
        is JSONObject -> item.optString("entity").takeIf { it.isNotBlank() }
        else -> null
    }

    fun room(card: JSONObject): LightRoom? {
        val items = card.optJSONArray("entities")?.let { a -> (0 until a.length()).map { a.opt(it) } } ?: emptyList()
        val mode = card.optString("mode", "room")
        // A room or group card lists its rows (the first is the group light on top). A single light is just its entity.
        val head = if (mode == "light") card.optString("entity").takeIf { it.isNotBlank() } else items.firstNotNullOfOrNull { entityOf(it) }
            ?: card.optString("entity").takeIf { it.isNotBlank() && mode == "group" }
        if (head == null) return null
        val rows = if (mode == "light") emptyList() else items.drop(1).mapNotNull { item ->
            val id = entityOf(item) ?: return@mapNotNull null
            val o = item as? JSONObject
            LightRowSpec(id, o?.optString("name")?.takeIf { it.isNotBlank() }, o?.optInt("level", 1) ?: 1)
        }
        val title = card.optString("name").takeIf { it.isNotBlank() }
            ?: (items.firstOrNull() as? JSONObject)?.optString("name")?.takeIf { it.isNotBlank() }
        return LightRoom(title, card.optString("area").takeIf { it.isNotBlank() }, head, rows, scenes(card, head))
    }

    private fun scenes(card: JSONObject, head: String): List<LightScene> {
        val max = if (card.has("max_scenes")) card.optInt("max_scenes", DEFAULT_MAX_SCENES) else DEFAULT_MAX_SCENES
        val list = card.optJSONArray("scenes")?.let { a -> (0 until a.length()).mapNotNull { scene(a.opt(it), head) } }
            // A card with no list shows the four default scenes for its room.
            ?: WHITE.map { LightScene(it, head) }
        return list.take(max.coerceAtLeast(0))
    }

    /** A scene entry: `universal:KEY@TARGET` (a Church Drive scene), or a Home Assistant scene entity. */
    private fun scene(item: Any?, head: String): LightScene? {
        val entity = entityOf(item) ?: return null
        val label = (item as? JSONObject)?.optString("name")?.takeIf { it.isNotBlank() }
        if (!entity.startsWith("universal:")) return LightScene(entity, head, label, haScene = entity)
        val ref = entity.removePrefix("universal:")
        return LightScene(ref.substringBefore('@'), ref.substringAfter('@', head), label)
    }

    /** Area id to name, from the area registry. */
    fun areaNames(result: Any?): Map<String, String> {
        val a = result as? JSONArray ?: return emptyMap()
        return (0 until a.length()).mapNotNull { a.optJSONObject(it) }
            .filter { it.has("area_id") && it.has("name") }
            .associate { it.getString("area_id") to it.getString("name") }
    }
}
