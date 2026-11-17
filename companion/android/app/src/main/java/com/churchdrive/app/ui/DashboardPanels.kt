package com.churchdrive.app.ui

import com.churchdrive.app.ha.text
import org.json.JSONArray
import org.json.JSONObject

/** A card inside a panel: its type (such as `custom:camera-card`) and its whole config. */
data class CardSpec(val type: String, val config: JSONObject)

/**
 * A Section Panel from the dashboard: title, icon and colour, the Home Assistant templates for its summary and
 * live colour, and its cards. The app asks Home Assistant to render the templates, so the panel says exactly
 * what the dashboard says.
 */
data class PanelSpec(
    val title: String,
    val icon: String?,
    val color: String?,
    val colorTemplate: String?,
    val summaryTemplate: String?,
    val cards: List<CardSpec>,
)

/** Reads the panels of each page of a dashboard, by the page's path (`security`, `climate`...). */
object DashboardPanels {
    fun parse(config: JSONObject): Map<String, List<PanelSpec>> {
        val views = config.optJSONArray("views") ?: return emptyMap()
        val out = mutableMapOf<String, List<PanelSpec>>()
        for (i in 0 until views.length()) {
            val view = views.optJSONObject(i) ?: continue
            val path = view.optString("path").takeIf { it.isNotBlank() } ?: continue
            val all = listOfNotNull(header(view)) + panels(view).map { panel(it, views) }
            if (path == "security") out[SECURITY_DETAILS] = securityDetails(all)
            out[path] = arrange(path, all)
        }
        return out
    }

    /** Under this key: what the Security page doesn't show itself but its Details pages do (see [securityDetails]). */
    const val SECURITY_DETAILS = "security:details"

    /**
     * What moves from the Security page onto Details pages (decided 2026-11-17): the header's status lines (doors, movement, who's
     * home, smoke and CO, batteries) go on the alarm's Details page, and the plain tiles of the Safety panel (the smoke and CO base
     * station's mode and volume) on the smoke and CO alarms' Details page. As two panels: "lines", then "safety".
     */
    private fun securityDetails(all: List<PanelSpec>): List<PanelSpec> {
        val lines = all.firstOrNull { it.title.isBlank() && it.cards.firstOrNull()?.type == "header:lines" }
        val safety = all.firstOrNull { p -> p.cards.any { it.type == "custom:safety-card" } }
        return listOf(
            PanelSpec("lines", null, null, null, null, lines?.cards.orEmpty()),
            PanelSpec("safety", null, null, null, null, safety?.cards.orEmpty().filter { it.type == "tile" }),
        )
    }

    /**
     * The dashboard's page has a header above its panels; in the app the header's information goes where it belongs:
     * the weather leads the Climate section, with the temperatures; the Security page keeps the dashboard's order: the notices (when there are any), then the alarm.
     */
    fun arrange(path: String, panels: List<PanelSpec>): List<PanelSpec> {
        if (path == "security") {
            // The status lines and the base station's tiles are on the Details pages (press and hold the alarm, or the smoke and CO alarms).
            return panels.filterNot { it.title.isBlank() && it.cards.firstOrNull()?.type == "header:lines" }
                .map { p -> if (p.cards.any { it.type == "custom:safety-card" }) p.copy(cards = p.cards.filter { it.type != "tile" }) else p }
        }
        fun isHeader(p: PanelSpec, kind: String) = p.title.isBlank() && p.cards.firstOrNull()?.type == kind
        if (path == "climate") {
            val weather = panels.firstOrNull { isHeader(it, "header:forecast") } ?: return panels
            val climate = panels.firstOrNull { it.title == "Climate" } ?: return panels
            return panels.filter { it !== weather }.map { if (it === climate) it.copy(cards = weather.cards + it.cards) else it }
        }
        return panels
    }

    /** A page's header (`header_content`: forecast, lines, list, todo_summary), as a panel with no title and one `header:` card. */
    private fun header(node: Any?): PanelSpec? {
        when (node) {
            is JSONObject -> {
                val kind = node.text("header_content")
                if (kind != null && kind != "none") return PanelSpec("", null, null, null, null, listOf(CardSpec("header:$kind", node)))
                for (key in node.keys()) header(node.opt(key))?.let { return it }
            }
            is JSONArray -> for (i in 0 until node.length()) header(node.opt(i))?.let { return it }
        }
        return null
    }

    private fun panels(node: Any?, found: MutableList<JSONObject> = mutableListOf()): List<JSONObject> {
        when (node) {
            is JSONObject ->
                if (node.optString("type") == "custom:section-panel-card") found += node
                else node.keys().forEach { panels(node.opt(it), found) }
            is JSONArray -> for (i in 0 until node.length()) panels(node.opt(i), found)
        }
        return found
    }

    private fun panel(o: JSONObject, views: JSONArray) = PanelSpec(
        title = o.text("title").orEmpty(),
        icon = o.text("icon"),
        color = o.text("color"),
        colorTemplate = o.text("color_template"),
        summaryTemplate = o.text("summary"),
        cards = cards(o.opt("cards"), views),
    )

    /** The cards in a panel, in order; a stack's cards are taken out of it. */
    private fun cards(node: Any?, views: JSONArray): List<CardSpec> {
        val out = mutableListOf<CardSpec>()
        val a = node as? JSONArray ?: return out
        for (i in 0 until a.length()) {
            val c = a.optJSONObject(i) ?: continue
            val type = c.optString("type")
            when {
                type == "vertical-stack" || type == "horizontal-stack" || type == "grid" -> out += cards(c.opt("cards"), views)
                type == "custom:mirror-card" || type == "custom:mirror-card-beta" ->
                    // A mirror stands for the card it points at, on this dashboard: set up once in Home Assistant.
                    DashboardLights.resolveMirror(c, views)?.let { target ->
                        val t = target.optString("type")
                        if (t.isNotEmpty()) out += CardSpec(t, target)
                    }
                type.isNotEmpty() -> out += CardSpec(type, c)
            }
        }
        return out
    }
}
