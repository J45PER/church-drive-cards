package com.churchdrive.app.wear

import org.json.JSONArray
import org.json.JSONObject

/**
 * The house's lights as the watch shows them, read from the Mobile dashboard's own light cards (the same cards the phone app reads), so
 * the watch has the same rooms, zones and scene lists as everything else and a change in Home Assistant is a change on the watch.
 *
 * A room is a card: its group light, the zones under it (rows indented one level; individual lights further in are left out, because the
 * watch only sets brightness and scenes), and its scenes. A floor is a panel of rooms.
 */
data class WScene(val key: String, val target: String, val label: String?, val haScene: String?) {
    val name: String get() = label ?: key.replace('_', ' ').replaceFirstChar { it.uppercase() }

    /** The scene select entity Church Drive keeps for a Hue room or zone (e.g. select.kitchen_scene): its state is the scene playing there. */
    val selectEntity: String get() = "select.${target.removePrefix("light.")}_scene"
}

data class WZone(val entity: String, val name: String?)

data class WRoom(val head: String, val title: String?, val area: String?, val zones: List<WZone>, val scenes: List<WScene>) {
    /**
     * The scenes a face offers. The whole room shows the card's whole list (each scene plays where the card aims it). A zone shows the
     * scenes aimed at it, or, when none are, the room's own scenes played on that zone.
     */
    fun scenesFor(zone: WZone?): List<WScene> {
        if (zone == null) return scenes
        val own = scenes.filter { it.target == zone.entity }
        return own.ifEmpty { scenes.filter { it.target == head }.map { it.copy(target = zone.entity) } }
    }
}

data class WFloor(val name: String, val rooms: List<WRoom>)

object WatchLights {
    const val DASHBOARD = "dashboard-mobile"
    private const val DEFAULT_MAX_SCENES = 8
    private val WHITE = listOf("bright", "dimmed", "relax", "nightlight")

    /** The floors of the Lighting view, or the Lights panel of the Home view as one floor; empty if the dashboard has neither. */
    fun parse(config: JSONObject?): List<WFloor> {
        val views = config?.optJSONArray("views") ?: return emptyList()
        var floors: List<WFloor>? = null
        var home: List<WRoom>? = null
        for (i in 0 until views.length()) {
            val view = views.optJSONObject(i) ?: continue
            when (view.optString("path")) {
                "lighting" -> floors = panels(view).map { WFloor(it.optString("title"), rooms(it)) }.filter { it.rooms.isNotEmpty() }
                "home" -> home = panels(view).firstOrNull { it.optString("title").equals("Lights", ignoreCase = true) }?.let { rooms(it) }
            }
        }
        return floors?.takeIf { it.isNotEmpty() } ?: home?.takeIf { it.isNotEmpty() }?.let { listOf(WFloor("Home", it)) } ?: emptyList()
    }

    private fun findAll(node: Any?, found: MutableList<JSONObject> = mutableListOf(), match: (String) -> Boolean): List<JSONObject> {
        when (node) {
            is JSONObject -> if (match(node.optString("type"))) found += node else node.keys().forEach { findAll(node.opt(it), found, match) }
            is JSONArray -> for (i in 0 until node.length()) findAll(node.opt(i), found, match)
        }
        return found
    }

    private fun panels(view: JSONObject) = findAll(view) { it == "custom:section-panel-card" }

    private fun rooms(panel: JSONObject): List<WRoom> =
        findAll(panel.opt("cards")) { it == "custom:light-control-card" || it == "custom:light-control-card-beta" }.mapNotNull { room(it) }

    private fun entityOf(item: Any?): String? = when (item) {
        is String -> item
        is JSONObject -> item.optString("entity").takeIf { it.isNotBlank() }
        else -> null
    }

    internal fun room(card: JSONObject): WRoom? {
        val items = card.optJSONArray("entities")?.let { a -> (0 until a.length()).map { a.opt(it) } } ?: emptyList()
        val mode = card.optString("mode", "room")
        val head = (if (mode == "light") card.optString("entity").takeIf { it.isNotBlank() } else items.firstNotNullOfOrNull { entityOf(it) })
            ?: card.optString("entity").takeIf { it.isNotBlank() && mode == "group" }
            ?: return null
        val zones = if (mode == "light") emptyList() else items.drop(1).mapNotNull { item ->
            val id = entityOf(item) ?: return@mapNotNull null
            val o = item as? JSONObject
            // Only the level directly under the room: the lights inside a zone are not the watch's business.
            if ((o?.optInt("level", 1) ?: 1) != 1) null else WZone(id, o?.optString("name")?.takeIf { it.isNotBlank() })
        }
        val title = card.optString("name").takeIf { it.isNotBlank() } ?: (items.firstOrNull() as? JSONObject)?.optString("name")?.takeIf { it.isNotBlank() }
        return WRoom(head, title, card.optString("area").takeIf { it.isNotBlank() }, zones, scenes(card, head))
    }

    private fun scenes(card: JSONObject, head: String): List<WScene> {
        val max = if (card.has("max_scenes")) card.optInt("max_scenes", DEFAULT_MAX_SCENES) else DEFAULT_MAX_SCENES
        val list = card.optJSONArray("scenes")?.let { a -> (0 until a.length()).mapNotNull { scene(a.opt(it), head) } } ?: WHITE.map { WScene(it, head, null, null) }
        return list.take(max.coerceAtLeast(0))
    }

    private fun scene(item: Any?, head: String): WScene? {
        val entity = entityOf(item) ?: return null
        val label = (item as? JSONObject)?.optString("name")?.takeIf { it.isNotBlank() }
        if (!entity.startsWith("universal:")) return WScene(entity, head, label, haScene = entity)
        val ref = entity.removePrefix("universal:")
        return WScene(ref.substringBefore('@'), ref.substringAfter('@', head), label, null)
    }

    /** Every area id the cards name, so the house can be asked for their names. */
    fun areaIds(floors: List<WFloor>): Set<String> = floors.flatMap { f -> f.rooms.mapNotNull { it.area } }.toSet()
}
