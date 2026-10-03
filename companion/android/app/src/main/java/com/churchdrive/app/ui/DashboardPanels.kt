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
            out[path] = listOfNotNull(header(view)) + panels(view).map { panel(it) }
        }
        return out
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

    private fun panel(o: JSONObject) = PanelSpec(
        title = o.text("title").orEmpty(),
        icon = o.text("icon"),
        color = o.text("color"),
        colorTemplate = o.text("color_template"),
        summaryTemplate = o.text("summary"),
        cards = cards(o.opt("cards")),
    )

    /** The cards in a panel, in order; a stack's cards are taken out of it. */
    private fun cards(node: Any?): List<CardSpec> {
        val out = mutableListOf<CardSpec>()
        val a = node as? JSONArray ?: return out
        for (i in 0 until a.length()) {
            val c = a.optJSONObject(i) ?: continue
            val type = c.optString("type")
            if (type == "vertical-stack" || type == "horizontal-stack") out += cards(c.opt("cards"))
            else if (type.isNotEmpty()) out += CardSpec(type, c)
        }
        return out
    }
}
