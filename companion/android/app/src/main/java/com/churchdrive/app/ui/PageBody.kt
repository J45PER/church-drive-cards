package com.churchdrive.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.churchdrive.app.ha.CallService
import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ha.Registry
import org.json.JSONObject

/** A page made of the dashboard's panels (Climate, Cleaning, To-do), each with its cards. */
@Composable
fun DashboardPage(panels: List<PanelSpec>, entities: Map<String, EntityState>, registry: Registry, call: CallService) {
    if (panels.isEmpty()) {
        LoadingNote("Loading this page from Home Assistant…")
        return
    }
    panels.forEach { PanelView(it, entities, registry, call) }
}

/** One panel of a page: its cards in a titled panel, or, for a page's header, the header itself. */
@Composable
fun PanelView(panel: PanelSpec, entities: Map<String, EntityState>, registry: Registry, call: CallService) {
    val header = panel.cards.firstOrNull()?.takeIf { panel.title.isBlank() && it.type.startsWith("header:") }
    if (header != null) {
        // A page's header (the weather, the lines of what is happening) takes the whole width above the columns of cards.
        androidx.compose.foundation.layout.Box(Modifier.fullSpan()) { HeaderView(header, entities, call) }
        return
    }
    // A card marked `admin_only` is for administrators; a panel left with none of its cards is not shown to anyone else.
    val cards = visibleCards(panel.cards, LocalIsAdmin.current)
    if (panel.cards.isNotEmpty() && cards.isEmpty()) return
    // The panel holding the alarm is the alarm's colour (blue home, red away), as on Home, whatever the dashboard says.
    val alarm = if (panel.cards.any { it.type.removeSuffix("-beta") == "custom:alarm-panel-card" }) {
        alarmTone(entities[panel.cards.first { it.type.removeSuffix("-beta") == "custom:alarm-panel-card" }.config.optString("entity").ifBlank { ALARM_ENTITY }]?.state)
    } else null
    DashboardPanel(panel, toneOverride = alarm) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            cards.forEach { CardView(it, entities, registry, call) }
        }
    }
}

/** The cards this person is shown: the ones marked `admin_only` (in the dashboard's own config) only for an administrator. */
fun visibleCards(cards: List<CardSpec>, isAdmin: Boolean): List<CardSpec> = cards.filter { isAdmin || !it.config.optBoolean("admin_only") }

/** A page's header: the weather, the lines of what's happening, a list, or the to-do summary. */
@Composable
fun HeaderView(card: CardSpec, entities: Map<String, EntityState>, call: CallService) {
    when (card.type) {
        "header:forecast" -> ForecastHeader(card.config, entities)
        "header:lines" -> HeaderLines(card.config)
        "header:todo_summary" -> TodoSummaryHeader(entities)
        "header:list" -> {
            val list = card.config.optString("header_list")
            if (list.isNotBlank()) {
                val config = JSONObject().put("entity", list).put("color", card.config.optString("list_color")).put("icons", true)
                TaskListCard(config, entities, call)
            }
        }
        else -> Unit
    }
}

