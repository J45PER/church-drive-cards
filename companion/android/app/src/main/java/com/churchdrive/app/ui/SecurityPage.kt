package com.churchdrive.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.churchdrive.app.ha.CallService
import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ha.Registry
import com.churchdrive.app.ha.Templates
import com.churchdrive.app.ha.data
import org.json.JSONObject

/** The app's template watcher, set once in MainActivity. */
val LocalTemplates = compositionLocalOf<Templates?> { null }

/** HA's rendering of a template (kept up to date), or null until it arrives or if there's no template. */
@Composable
fun rememberTemplate(template: String?): String? {
    val templates = LocalTemplates.current ?: return null
    if (template.isNullOrBlank()) return null
    LaunchedEffect(template) { templates.watch(template) }
    return templates.value(template)
}

/** What the page shows if the dashboard can't be read: the alarm, as on the dashboard's Security page. */
val FALLBACK_SECURITY = listOf(
    PanelSpec(
        "Alarm", "mdi:shield-home", "green", null, null,
        listOf(CardSpec("custom:alarm-panel-card", JSONObject().put("entity", ALARM_ENTITY))),
    ),
)

/**
 * A panel from the dashboard: its icon, title, colour and summary line (templates rendered by Home Assistant,
 * so the colour follows the alarm and the summary says what the dashboard says), around its cards.
 */
@Composable
fun DashboardPanel(panel: PanelSpec, content: @Composable () -> Unit) {
    val summary = rememberTemplate(panel.summaryTemplate)
    val colour = rememberTemplate(panel.colorTemplate) ?: panel.color
    SectionPanel(
        panel.title,
        iconName = panel.icon,
        tone = toneFromColour(colour) ?: Tone.Grey,
        summary = summary,
        content = content,
    )
}

/** The Security page: the panels of the dashboard's Security page, in order. */
@Composable
fun SecurityPage(panels: List<PanelSpec>, entities: Map<String, EntityState>, registry: Registry, call: CallService) {
    panels.ifEmpty { FALLBACK_SECURITY }.forEach { panel ->
        DashboardPanel(panel) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                panel.cards.forEach { CardView(it, entities, registry, call) }
            }
        }
    }
}

/** One dashboard card, as a native card, by its type. */
@Composable
fun CardView(card: CardSpec, entities: Map<String, EntityState>, registry: Registry, call: CallService) {
    when (card.type.removeSuffix("-beta")) {
        "custom:alarm-panel-card" -> {
            val id = card.config.optString("entity").ifBlank { ALARM_ENTITY }
            AlarmCard(entities[id], call, id)
        }
        "custom:safety-card" -> SafetyCard(card.config, entities, registry)
        "tile" -> TileCard(card.config, entities, call)
        "custom:camera-card" -> CameraCard(card.config, entities, registry, call)
        "custom:security-zone-card" -> ZoneCard(card.config, entities, call)
        else -> NotBuiltCard()
    }
}

// ---- Safety ----

private val SAFETY_ICONS = mapOf(
    "smoke" to "mdi:smoke-detector-variant",
    "heat" to "mdi:fire",
    "carbon_monoxide" to "mdi:molecule-co",
)

/** Every smoke, heat and CO alarm: all clear or ALARM, battery, last check-in and the CO reading. */
@Composable
fun SafetyCard(config: JSONObject, entities: Map<String, EntityState>, registry: Registry) {
    val chosen = config.optJSONArray("entities")?.let { a -> (0 until a.length()).mapNotNull { a.optString(it).takeIf { s -> s.isNotBlank() } } }.orEmpty()
    val items = safetyItems(entities, registry, chosen)
    val neutral = toneColors(Tone.Grey)
    EntityCard(neutral.container, neutral.onContainer) {
        if (items.isEmpty()) {
            Text("No smoke, heat or CO alarms found.", style = MaterialTheme.typography.bodyMedium)
        }
        items.forEach { item ->
            val tone = toneColors(if (item.on) Tone.Red else if (item.unavailable) Tone.Grey else Tone.Green)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(if (item.on) tone.accent else tone.accent.copy(alpha = 0.18f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    HaIcon(SAFETY_ICONS[item.kind] ?: "mdi:alarm-light", Icons.Filled.Security, if (item.on) tone.onAccent else tone.accent, 22.dp)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    Text(
                        safetySubtitle(item),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (item.on) tone.accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item.ppm?.let {
                    Text(
                        "${if (it % 1.0 == 0.0) it.toInt().toString() else it.toString()} ppm",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (item.on) tone.accent else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

// ---- Tile ----

/** A plain entity tile (Home Assistant's own `tile` card): its icon, name and state; a number gets − and +. */
@Composable
fun TileCard(config: JSONObject, entities: Map<String, EntityState>, call: CallService) {
    val id = config.optString("entity")
    val entity = entities[id]
    val neutral = toneColors(Tone.Grey)
    val name = config.optString("name").ifBlank { entity?.friendlyName ?: id }
    val unit = entity?.str("unit_of_measurement")?.let { " $it" }.orEmpty()
    val value = when {
        entity == null -> "Loading…"
        else -> entity.state.replace('_', ' ').replaceFirstChar { it.uppercase() } + unit
    }
    val features = config.optJSONArray("features")
    val numeric = id.startsWith("number.") && (0 until (features?.length() ?: 0)).any { features?.optJSONObject(it)?.optString("type") == "numeric-input" }

    EntityCard(neutral.container, neutral.onContainer) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ToneIconName(config.optString("icon").ifBlank { entity?.str("icon") ?: "" }, Icons.Filled.Info, neutral)
            Column(modifier = Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (numeric && entity != null) {
                val step = entity.num("step") ?: 1.0
                val current = entity.state.toDoubleOrNull()
                fun nudge(by: Double) {
                    val next = ((current ?: return) + by).coerceIn(entity.num("min") ?: Double.NEGATIVE_INFINITY, entity.num("max") ?: Double.POSITIVE_INFINITY)
                    call("number", "set_value", id, data("value" to next))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StepButton(Icons.Filled.Remove, "Lower", neutral, current != null) { nudge(-step) }
                    StepButton(Icons.Filled.Add, "Raise", neutral, current != null) { nudge(step) }
                }
            }
        }
    }
}
