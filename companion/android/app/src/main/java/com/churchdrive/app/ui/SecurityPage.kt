package com.churchdrive.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
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

/** Whether the signed-in person is an administrator: who's home is for them only. */
val LocalIsAdmin = compositionLocalOf { false }

/** The app's template watcher, set once in MainActivity. */
val LocalTemplates = compositionLocalOf<Templates?> { null }

/** HA's rendering of a template (kept up to date), or null until it arrives or if there's no template. */
@Composable
fun rememberTemplate(template: String?): String? {
    val templates = LocalTemplates.current ?: return null
    if (template.isNullOrBlank()) return null
    // Templates may use the signed-in person's `my_list` and `my_name`, as the dashboard's do.
    val me = LocalUserName.current
    val full = if (Regex("\\bmy_(list|name)\\b").containsMatchIn(template)) {
        "{% set my_name = '${me?.trim()?.substringBefore(' ').orEmpty().replace("'", "")}' %}" +
            "{% set my_list = '${myTodoList(me).orEmpty()}' %}" + template
    } else template
    LaunchedEffect(full) { templates.watch(full) }
    return templates.value(full)
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
fun DashboardPanel(panel: PanelSpec, toneOverride: Tone? = null, content: @Composable () -> Unit) {
    val summary = rememberTemplate(panel.summaryTemplate)
    val colour = rememberTemplate(panel.colorTemplate) ?: panel.color
    // A fixed tone (the alarm's own) wins over the dashboard colour, which is only the fallback until Home Assistant renders it.
    val tone = toneOverride ?: toneFromColour(colour) ?: Tone.Grey
    SectionPanel(
        panel.title,
        iconName = panel.icon,
        tone = tone,
        summary = summary,
        content = { androidx.compose.runtime.CompositionLocalProvider(LocalPanelTone provides tone) { content() } },
    )
}

/** The Security page: the panels of the dashboard's Security page, in order. */
@Composable
fun SecurityPage(panels: List<PanelSpec>, entities: Map<String, EntityState>, registry: Registry, call: CallService) {
    panels.ifEmpty { FALLBACK_SECURITY }.forEach { PanelView(it, entities, registry, call) }
}

/** Cards that draw their own folded strip (see [CompactStrip]). */
private val OWN_STRIP = setOf(
    "custom:alarm-panel-card", "custom:fan-card", "custom:air-purifier-card", "custom:purifier-schedule-card", "custom:co-alarm-card",
    "custom:cover-card", "custom:ev-charger-card", "custom:people-card", "custom:device-health-card", "custom:security-zone-card",
    "custom:car-card", "custom:safety-card", "custom:overnight-heating-card",
)

/**
 * Cards that do not fold: pictures (cameras), lights and the room and zone thermostats (they already open and close), headings and
 * stacks, small cards (plain tiles, the Octopus plan, Gregg) and the manager pages.
 */
private val NO_FOLD = setOf(
    "heading", "markdown", "vertical-stack", "tile", "custom:camera-card", "custom:camera-links-card", "custom:light-control-card",
    "custom:climate-card", "custom:climate-zone-card", "custom:octopus-plan-card", "custom:people-manager-card", "custom:zone-map-card",
    "custom:notifications-card", "custom:icon-styles-card", "custom:watch-settings-card",
)

private val PLAIN_TITLES = mapOf(
    "entities" to "Settings", "todo-list" to "To-do", "custom:task-list-card" to "Tasks", "custom:shopping-card" to "Shopping",
    "custom:house-tasks-card" to "House tasks", "custom:octopus-card" to "Octopus", "custom:system-card" to "System",
    "custom:media-card" to "Media", "custom:overnight-heating-card" to "Overnight heating", "custom:auto-entities" to "Lists",
)

/** One dashboard card, as a native card, by its type, which the person can fold down to a strip. */
@Composable
fun CardView(card: CardSpec, entities: Map<String, EntityState>, registry: Registry, call: CallService) {
    val type = card.type.removeSuffix("-beta")
    if (card.type.startsWith("header:") || type in NO_FOLD) {
        CardContent(card, entities, registry, call)
        return
    }
    val c = card.config
    val entity = c.optString("entity").ifBlank { if (type == "custom:alarm-panel-card") ALARM_ENTITY else "" }
    val title = listOf("name", "title", "heading").map { c.optString(it) }.firstOrNull { it.isNotBlank() }
        ?: entities[entity]?.friendlyName ?: PLAIN_TITLES[type] ?: "Card"
    CollapsibleCard("$type|${entity.ifBlank { title }}", type in OWN_STRIP, title, entities[entity]?.state?.let { presetLabel(it) }.orEmpty()) {
        CardContent(card, entities, registry, call)
    }
}

@Composable
private fun CardContent(card: CardSpec, entities: Map<String, EntityState>, registry: Registry, call: CallService) {
    // A page's header information that the app has moved into a panel (the weather in Climate).
    if (card.type.startsWith("header:")) {
        HeaderView(card, entities, call)
        return
    }
    when (card.type.removeSuffix("-beta")) {
        "custom:alarm-panel-card" -> {
            val id = card.config.optString("entity").ifBlank { ALARM_ENTITY }
            AlarmCard(entities[id], call, id, entities[ALARM_ACTOR_ENTITY], people = if (LocalIsAdmin.current) peopleRows(entities, registry) else emptyList())
        }
        "custom:safety-card" -> SafetyCard(card.config, entities, registry)
        "tile" -> TileCard(card.config, entities, call)
        "custom:camera-card" -> CameraCard(card.config, entities, registry, call)
        "custom:security-zone-card" -> ZoneCard(card.config, entities, call)
        "custom:climate-card" -> ClimateRoomCard(card.config, entities, call)
        "custom:climate-zone-card" -> ClimateZoneCard(card.config, entities, call)
        "custom:overnight-heating-card" -> OvernightHeatingCard(entities, call)
        "custom:fan-card" -> FanCard(card.config, entities, call)
        "custom:air-purifier-card" -> AirPurifierCard(card.config, entities, call)
        "custom:purifier-schedule-card" -> PurifierScheduleCard(card.config)
        "custom:co-alarm-card" -> CoAlarmCard(card.config, entities, call)
        "custom:cover-card" -> CoverCard(card.config, entities, call)
        "entities" -> SettingsEntitiesCard(card.config, entities, call)
        "custom:people-card" -> PeopleStatusCard(entities, registry)
        "custom:device-health-card" -> DeviceHealthCard(card.config, entities, call)
        "heading" -> HeadingCard(card.config)
        "markdown" -> MarkdownCard(card.config)
        "vertical-stack" -> StackCard(card.config, entities, registry, call)
        "custom:auto-entities" -> PeopleListsCard(entities, call)
        "todo-list" -> TaskListCard(card.config, entities, call)
        "custom:people-manager-card" -> PeoplePage(entities, registry)
        "custom:zone-map-card" -> LocationsPage(entities)
        "custom:notifications-card" -> NotificationsPage(entities)
        "custom:camera-links-card" -> CameraLinksPage(entities)
        "custom:icon-styles-card" -> IconStylesPage()
        "custom:watch-settings-card" -> WatchSettingsPage(entities)
        "custom:task-list-card" -> TaskListCard(card.config, entities, call)
        "custom:shopping-card" -> ShoppingCard(card.config)
        "custom:house-tasks-card" -> HouseTasksCard(card.config, entities)
        "custom:octopus-card" -> OctopusCard(card.config, entities)
        "custom:system-card" -> SystemCard(entities)
        "custom:ev-charger-card" -> ChargerCard(entities, call)
        "custom:light-control-card" -> DashboardLights.room(card.config)?.let { LightRoomCard(it, entities, LocalAreaNames.current, call) }
        "custom:media-card" -> MediaCard(card.config, entities, registry, call)
        "custom:car-card" -> CarCard(card.config, entities, registry, call)
        "custom:octopus-plan-card" -> OctopusPlanCard(entities)
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
    // Green while everything is clear, red while one is sounding.
    val sounding = items.filter { it.on }
    val cardTone = toneColors(if (sounding.isNotEmpty()) Tone.Red else Tone.Green)
    val ink = cardTone.onContainer
    val fold = rememberFold("grid-fold|safety")
    fun itemTone(item: SafetyItem) = toneColors(if (item.on) Tone.Red else if (item.unavailable) Tone.Grey else Tone.Green)
    if (items.isEmpty()) {
        GridCard(cardTone.container, ink, 2.5f, "Smoke & CO Alarms") {
            Text("No smoke, heat or CO alarms found.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.at(0.5f, 1f, 7f, 1f))
        }
        return
    }
    if (fold.value) {
        // Folded: the icons on one row in their status colour, each with its name and battery under it.
        val lines = items.chunked(5)
        GridCard(cardTone.container, ink, 1f + 1.75f * lines.size + 0.25f, "Smoke & CO Alarms", fold = fold) {
            lines.forEachIndexed { r, line ->
                line.forEachIndexed { c, item ->
                    val tone = itemTone(item)
                    Column(Modifier.at(0.5f + c * 1.4f, 0.9f + r * 1.75f, 1.4f, 1.75f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Box(Modifier.size(26.dp).background(if (item.on) tone.accent else tone.accent.copy(alpha = 0.18f), RoundedCornerShape(30)), contentAlignment = Alignment.Center) {
                            HaIcon(SAFETY_ICONS[item.kind] ?: "mdi:alarm-light", CdVectors.ShieldOutline, if (item.on) tone.onAccent else tone.accent, 18.dp)
                        }
                        Text(item.name.substringBefore(' '), style = MaterialTheme.typography.bodySmall, color = ink, maxLines = 1)
                        item.battery?.let { Text("$it%", style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.9f), maxLines = 1) }
                    }
                }
            }
        }
        return
    }
    GridCard(cardTone.container, ink, 1.5f * items.size + 1.5f, "Smoke & CO Alarms", fold = fold) {
        items.forEachIndexed { k, item ->
            val tone = itemTone(item)
            val y = 1f + 1.5f * k
            val word = if (item.on) "Alarm" else if (item.unavailable) "Not responding" else "All clear"
            Row(Modifier.at(0.5f, y, 7f, 1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(cell * 0.35f)) {
                // A squircle, not a circle, behind a safety device's icon.
                Box(Modifier.size(cell * 0.9f).background(if (item.on) tone.accent else tone.accent.copy(alpha = 0.18f), RoundedCornerShape(30)), contentAlignment = Alignment.Center) {
                    HaIcon(SAFETY_ICONS[item.kind] ?: "mdi:alarm-light", CdVectors.ShieldOutline, if (item.on) tone.onAccent else tone.accent, 22.dp)
                }
                Column(Modifier.weight(1f)) {
                    Text(item.name, style = MaterialTheme.typography.bodyLarge, color = ink, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    item.battery?.let { b ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            HaIcon(if (b < 30) "mdi:battery-alert" else "mdi:battery-70", CdVectors.Info, ink, 16.dp)
                            Text("$b%", style = MaterialTheme.typography.bodySmall, color = ink)
                            LevelBar(b / 100f, batteryFade(b / 100f), ink, Modifier.width(cell * 1.5f), 5.dp)
                        }
                    }
                }
                val pill = @Composable { StatusPill(word, tone) }
                if (item.ppm != null) Column(horizontalAlignment = Alignment.End) {
                    Text("${if (item.ppm % 1.0 == 0.0) item.ppm.toInt().toString() else item.ppm.toString()} ppm", style = MaterialTheme.typography.headlineMedium, color = ink)
                    pill()
                } else pill()
            }
        }
    }
}

// ---- Tile ----

/**
 * A plain entity tile (Home Assistant's own `tile` card): its icon, name and state; a number gets - and +, a vacuum
 * its Start, Stop and Dock buttons, and a choice (select) its options.
 */
@Composable
fun TileCard(config: JSONObject, entities: Map<String, EntityState>, call: CallService) {
    val id = config.optString("entity")
    val entity = entities[id]
    val isVacuum = id.startsWith("vacuum.")
    val tone = if (isVacuum) toneColors(vacuumTone(entity)) else toneColors(Tone.Grey)
    val name = config.optString("name").ifBlank { entity?.friendlyName ?: id }
    val unit = entity?.str("unit_of_measurement")?.let { " $it" }.orEmpty()
    val value = when {
        entity == null -> "Loading…"
        id.startsWith("input_text.") -> entity.state
        else -> entity.state.replace('_', ' ').replaceFirstChar { it.uppercase() } + unit
    }
    val features = config.optJSONArray("features")
    fun hasFeature(type: String) = (0 until (features?.length() ?: 0)).any { features?.optJSONObject(it)?.optString("type") == type }
    val numeric = id.startsWith("number.") && hasFeature("numeric-input")

    EntityCard(tone.container, tone.onContainer, deviceEntity = id) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (isVacuum) GreggIcon(greggState(entity, entities[VACUUM_BATTERY]), tone.accent, 44.dp, entities[VACUUM_BATTERY]?.state?.toDoubleOrNull())
            else ToneIconName(greggEntityIcon(id) ?: config.optString("icon").ifBlank { entity?.str("icon") ?: "" }, CdVectors.Info, tone)
            Column(modifier = Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3)
            }
            if (numeric && entity != null) {
                val step = entity.num("step") ?: 1.0
                val current = entity.state.toDoubleOrNull()
                fun nudge(by: Double) {
                    val next = ((current ?: return) + by).coerceIn(entity.num("min") ?: Double.NEGATIVE_INFINITY, entity.num("max") ?: Double.POSITIVE_INFINITY)
                    call("number", "set_value", id, data("value" to next))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StepButton(CdVectors.Minus, "Lower", tone, current != null) { nudge(-step) }
                    StepButton(CdVectors.Plus, "Raise", tone, current != null) { nudge(step) }
                }
            }
        }
        if (isVacuum && entity != null && hasFeature("vacuum-commands")) {
            val cleaning = entity.state == "cleaning"
            IconRow(
                listOf(
                    IconItem(if (cleaning) "cd:pause" else "cd:play", if (cleaning) "Pause" else if (entity.state == "paused") "Resume" else "Start", true) {
                        call("vacuum", if (cleaning) "pause" else "start", id, data())
                    },
                    IconItem("cd:stop", "Stop", false) { call("vacuum", "stop", id, data()) },
                    IconItem("cd:home", "Dock", false) { call("vacuum", "return_to_base", id, data()) },
                ),
                tone, tone.onContainer, entity.available,
            )
        }
        if (id.startsWith("select.") && entity != null && hasFeature("select-options")) {
            val levels = entity.options().map { it to greggLevelIcon(id, it) }
            if (levels.isNotEmpty() && levels.all { it.second != null }) {
                // Gregg's suction and water levels: the Eufy-style icons, as tiles.
                TileRow(
                    levels.map { (o, icon) -> TileItem(icon.orEmpty(), o.replace('_', ' '), o == entity.state) { call("select", "select_option", id, data("option" to o)) } },
                    tone, tone.onContainer, entity.available,
                )
            } else OptionRow(entity.options(), entity.state, tone, entity.available) { o ->
                call("select", "select_option", id, data("option" to o))
            }
        }
    }
}
