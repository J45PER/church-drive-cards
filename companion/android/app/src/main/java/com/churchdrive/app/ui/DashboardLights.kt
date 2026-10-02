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
                    ?.let { rooms(it) }
                "lighting" -> floors = panels(view).map { it.optString("title") to rooms(it) }.filter { it.second.isNotEmpty() }
            }
        }
        return LightLayout(
            home = home?.takeIf { it.isNotEmpty() } ?: LightLayout.Fallback.home,
            floors = floors?.takeIf { it.isNotEmpty() } ?: LightLayout.Fallback.floors,
        )
    }

    /** All objects of [type] under [node], not looking inside a match. */
    private fun find(node: Any?, type: String, found: MutableList<JSONObject> = mutableListOf()): List<JSONObject> {
        when (node) {
            is JSONObject -> if (node.optString("type") == type) found += node
            else node.keys().forEach { find(node.opt(it), type, found) }
            is JSONArray -> for (i in 0 until node.length()) find(node.opt(i), type, found)
        }
        return found
    }

    private fun panels(view: JSONObject) = find(view, "custom:section-panel-card")

    private fun rooms(panel: JSONObject) = find(panel.opt("cards"), "custom:light-control-card").mapNotNull { room(it) }

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
