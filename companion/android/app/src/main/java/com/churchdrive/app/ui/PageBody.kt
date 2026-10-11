package com.churchdrive.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
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
        HeaderView(header, entities, call)
        return
    }
    DashboardPanel(panel) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            panel.cards.forEach { CardView(it, entities, registry, call) }
        }
    }
}

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

/** The Security page's lines: doors open, the last doorbell or movement, who's home. Each is a template Home Assistant renders. */
@Composable
fun HeaderLines(config: JSONObject) {
    val lines = config.optJSONArray("header_lines")
    Column {
        for (i in 0 until (lines?.length() ?: 0)) {
            val line = lines?.optJSONObject(i) ?: continue
            HeaderLine(line)
        }
    }
}

/** How long an "all is well" line stays before it fades away. */
private const val FADE_AFTER_MS = 8_000L

/**
 * A line of the page header. A good (green) line is only news for a few seconds: it fades away by itself, or swipes off
 * sooner. A line that warns (amber, red...) stays, can't be swiped away, and a good line comes back when it starts to warn.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun HeaderLine(line: JSONObject) {
    val text = rememberTemplate(line.optString("text"))
    val alert = rememberTemplate(line.optString("alert_when"))?.trim()?.lowercase() == "true"
    val colour = if (alert) line.optString("alert_color") else line.optString("color")
    val tone = toneColors(toneFromColour(colour) ?: Tone.Green)
    val icon = if (alert) line.optString("alert_icon").ifBlank { line.optString("icon") } else line.optString("icon")
    if (text.isNullOrBlank()) return
    var gone by androidx.compose.runtime.saveable.rememberSaveable(line.optString("text")) { mutableStateOf(false) }
    // The line is good (not a warning) and has been seen: put it away. A warning always brings it back.
    LaunchedEffect(alert) {
        if (alert) gone = false else if (!gone) {
            kotlinx.coroutines.delay(FADE_AFTER_MS)
            gone = true
        }
    }
    val body = @Composable {
        Row(
            modifier = Modifier.fillMaxWidth().background(tone.container, RoundedCornerShape(20.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ToneIconName(icon, Icons.Filled.Info, tone, 32)
            Text(styledLine(text), color = tone.onContainer, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        }
    }
    androidx.compose.animation.AnimatedVisibility(
        visible = !gone,
        enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically(),
        exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(900)) + androidx.compose.animation.shrinkVertically(androidx.compose.animation.core.tween(900)),
    ) {
        if (alert) {
            Box(Modifier.padding(bottom = 8.dp)) { body() }
        } else {
            val state = androidx.compose.material3.rememberSwipeToDismissBoxState(
                confirmValueChange = { if (it != androidx.compose.material3.SwipeToDismissBoxValue.Settled) gone = true; true },
            )
            androidx.compose.material3.SwipeToDismissBox(state = state, backgroundContent = { }, modifier = Modifier.padding(bottom = 8.dp)) { body() }
        }
    }
}
