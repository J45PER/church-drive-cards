package com.churchdrive.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.churchdrive.app.ha.CallService
import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ha.Registry
import com.churchdrive.app.ui.CameraLinksPage
import com.churchdrive.app.ui.DeviceHealthCard
import com.churchdrive.app.ui.PeopleStatusCard
import com.churchdrive.app.ui.WatchSettingsPage
import com.churchdrive.app.ui.HeadingCard
import com.churchdrive.app.ui.HouseTasksCard
import com.churchdrive.app.ui.IconStylesPage
import com.churchdrive.app.ui.LocationsPage
import com.churchdrive.app.ui.MarkdownCard
import com.churchdrive.app.ui.NotificationsPage
import com.churchdrive.app.ui.SettingsEntitiesCard
import com.churchdrive.app.ui.Tone
import com.churchdrive.app.ui.ToneCard
import com.churchdrive.app.ui.toneFromColour
import org.json.JSONArray
import org.json.JSONObject

/** One card of a Manager section: its type (such as `custom:notifications-card`) and its whole config. */
data class ManagerCard(val type: String, val config: JSONObject)

/** One section of the Manager (People, Locations, Notifications and so on), as the house's Manager dashboard describes it. */
data class ManagerSection(val title: String, val icon: String?, val color: String?, val cards: List<ManagerCard>, val summary: String? = null)

object ManagerSections {
    /** Reads the sections of the Manager dashboard: every Section Panel in it, with its cards (a stack's cards taken out of it). */
    fun parse(config: JSONObject): List<ManagerSection> {
        val found = mutableListOf<JSONObject>()
        fun walk(node: Any?) {
            when (node) {
                is JSONObject -> if (node.optString("type") == "custom:section-panel-card") found += node else node.keys().forEach { walk(node.opt(it)) }
                is JSONArray -> for (i in 0 until node.length()) walk(node.opt(i))
            }
        }
        walk(config.optJSONArray("views"))
        return found.map { p ->
            ManagerSection(
                title = p.optString("title"), icon = p.optString("icon").takeIf { it.isNotBlank() }, color = p.optString("color").takeIf { it.isNotBlank() },
                cards = cards(p.opt("cards")), summary = p.optString("summary").takeIf { it.isNotBlank() },
            )
        }.filter { it.title.isNotBlank() }
    }

    /** The page header of a view (`header_content` set to lines, with its `header_lines`): the object that holds them, or null. */
    fun header(node: Any?): JSONObject? {
        when (node) {
            is JSONObject -> {
                if (node.optString("header_content") == "lines") return node
                for (key in node.keys()) header(node.opt(key))?.let { return it }
            }
            is JSONArray -> for (i in 0 until node.length()) header(node.opt(i))?.let { return it }
        }
        return null
    }

    private fun cards(node: Any?): List<ManagerCard> {
        val a = node as? JSONArray ?: return emptyList()
        val out = mutableListOf<ManagerCard>()
        for (i in 0 until a.length()) {
            val c = a.optJSONObject(i) ?: continue
            val type = c.optString("type")
            if (type == "vertical-stack" || type == "horizontal-stack" || type == "grid") out += cards(c.opt("cards"))
            else if (type.isNotEmpty()) out += ManagerCard(type, c)
        }
        return out
    }

    /** Whether Clive Admin can draw every card of [section] itself (the others are opened in the Church Drive app for now). */
    fun builtHere(section: ManagerSection): Boolean = section.cards.all { it.type in BUILT }

    /** Sections left out of Clive Admin on purpose (decided with Jamie): People stays on the website, and Locations and Icons are not needed in the app for now. */
    private val LEFT_OUT = setOf("custom:zone-map-card", "custom:icon-styles-card", "custom:people-manager-card")

    /** The sections Clive Admin lists: all but those left out on purpose. */
    fun forAdmin(sections: List<ManagerSection>): List<ManagerSection> = sections.filter { s -> s.cards.isEmpty() || s.cards.any { it.type !in LEFT_OUT } }

    /** The card types Clive Admin draws itself. */
    val BUILT = setOf(
        "custom:notifications-card", "custom:zone-map-card", "custom:camera-links-card", "custom:icon-styles-card",
        "heading", "markdown", "entities", "custom:house-tasks-card",
        "custom:people-card", "custom:watch-settings-card", "custom:device-health-card",
    )
}

/** A section's cards. A card Clive Admin does not draw yet says so and points to the Church Drive app, which has it. */
@Composable
fun ManagerSectionContent(section: ManagerSection, entities: Map<String, EntityState>, registry: Registry, call: CallService) {
    androidx.compose.runtime.CompositionLocalProvider(com.churchdrive.app.ui.LocalPanelTone provides (toneFromColour(section.color) ?: Tone.Grey)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            section.cards.forEach { ManagerCardView(it, entities, registry, call) }
        }
    }
}

@Composable
private fun ManagerCardView(card: ManagerCard, entities: Map<String, EntityState>, registry: Registry, call: CallService) {
    when (card.type) {
        "custom:notifications-card" -> NotificationsPage(entities)
        "custom:zone-map-card" -> LocationsPage(entities)
        "custom:camera-links-card" -> CameraLinksPage(entities)
        "custom:icon-styles-card" -> IconStylesPage()
        "heading" -> HeadingCard(card.config)
        "markdown" -> MarkdownCard(card.config)
        "entities" -> SettingsEntitiesCard(card.config, entities, call)
        "custom:house-tasks-card" -> HouseTasksCard(card.config, entities)
        "custom:people-card" -> PeopleStatusCard(entities, registry)
        "custom:watch-settings-card" -> WatchSettingsPage(entities)
        "custom:device-health-card" -> DeviceHealthCard(card.config, entities, call)
        else -> NotHereYet()
    }
}

/** The Manager website: where the parts Clive Admin does not draw yet are. */
private const val MANAGER_SITE = "https://church-drive-smart-home.web.app"

@Composable
private fun NotHereYet() {
    val context = LocalContext.current
    ToneCard(Tone.Grey, onClick = {
        runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(MANAGER_SITE)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }) { ink, soft ->
        Text("Not in Clive Admin yet", style = MaterialTheme.typography.titleMedium, color = ink)
        Text("This part is on the Manager website (and, for some, in the Church Drive app): tap to open the website.", style = MaterialTheme.typography.bodyMedium, color = soft)
    }
}
