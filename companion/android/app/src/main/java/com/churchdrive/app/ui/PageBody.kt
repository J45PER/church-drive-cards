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
        HeaderView(header, entities, call)
        return
    }
    // The panel holding the alarm is the alarm's colour (blue home, red away), as on Home, whatever the dashboard says.
    val alarm = if (panel.cards.any { it.type.removeSuffix("-beta") == "custom:alarm-panel-card" }) {
        alarmTone(entities[panel.cards.first { it.type.removeSuffix("-beta") == "custom:alarm-panel-card" }.config.optString("entity").ifBlank { ALARM_ENTITY }]?.state)
    } else null
    DashboardPanel(panel, toneOverride = alarm) {
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
    val context = androidx.compose.ui.platform.LocalContext.current
    val seen = androidx.compose.runtime.remember { HeaderSeen.load(context) }
    Column {
        for (i in 0 until (lines?.length() ?: 0)) {
            val line = lines?.optJSONObject(i) ?: continue
            HeaderLine(line, i, seen)
        }
    }
}

/** The header line that says who is home: its template reads the people. */
fun isPeopleLine(template: String) = "states.person" in template

/** How long an "all is well" line stays before it slides away. */
private const val FADE_AFTER_MS = 8_000L

/** The pause between one line leaving and the next. */
private const val LEAVE_STAGGER_MS = 180

/**
 * A line of the page header. A good (green) line is only news once after the app updates: it shows for a few seconds, then
 * slides away to the side (each a moment after the one above), or swipes off sooner, and stays away on later visits. A line
 * that warns (amber, red...) stays, can't be swiped away, and a good line comes back when it starts to warn.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun HeaderLine(line: JSONObject, index: Int, seen: Set<String>) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val key = line.optString("text")
    val text = rememberTemplate(key)
    val alert = rememberTemplate(line.optString("alert_when"))?.trim()?.lowercase() == "true"
    val colour = if (alert) line.optString("alert_color") else line.optString("color")
    val tone = toneColors(toneFromColour(colour) ?: Tone.Green)
    val icon = if (alert) line.optString("alert_icon").ifBlank { line.optString("icon") } else line.optString("icon")
    if (text.isNullOrBlank()) return
    // Who's home is part of the alarm card now (for administrators); its line only speaks up when everyone is out and the alarm is off.
    if (isPeopleLine(key) && (!LocalIsAdmin.current || !alert)) return
    var gone by androidx.compose.runtime.remember { mutableStateOf(!alert && key in seen) }
    // The line is good (not a warning): note that it has been shown, and put it away. A warning always brings it back.
    LaunchedEffect(alert) {
        if (alert) gone = false else if (!gone) {
            HeaderSeen.add(context, key)
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
            ToneIconName(icon, CdVectors.Info, tone, 32)
            Text(styledLine(text), color = tone.onContainer, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        }
    }
    val leave = androidx.compose.animation.core.tween<Float>(500, delayMillis = index * LEAVE_STAGGER_MS)
    androidx.compose.animation.AnimatedVisibility(
        visible = !gone,
        enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically(),
        exit = androidx.compose.animation.slideOutHorizontally(androidx.compose.animation.core.tween(500, delayMillis = index * LEAVE_STAGGER_MS)) { it } +
            androidx.compose.animation.fadeOut(leave) +
            androidx.compose.animation.shrinkVertically(androidx.compose.animation.core.tween(300, delayMillis = index * LEAVE_STAGGER_MS + 400)),
    ) {
        if (alert) {
            Box(Modifier.padding(bottom = 8.dp)) { body() }
        } else {
            val state = androidx.compose.material3.rememberSwipeToDismissBoxState(
                confirmValueChange = { if (it != androidx.compose.material3.SwipeToDismissBoxValue.Settled) { HeaderSeen.add(context, key); gone = true }; true },
            )
            androidx.compose.material3.SwipeToDismissBox(state = state, backgroundContent = { }, modifier = Modifier.padding(bottom = 8.dp)) { body() }
        }
    }
}
