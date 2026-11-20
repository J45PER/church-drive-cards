package com.churchdrive.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.churchdrive.app.ha.CallService
import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ha.Registry

/** The colour of an own section left on Auto: the colour of what is inside it (its first card). */
fun autoTone(cards: List<CardSpec>): Tone = when (cards.firstOrNull()?.type?.removeSuffix("-beta")) {
    "custom:light-control-card" -> Tone.Lights
    "custom:climate-card", "custom:climate-zone-card", "custom:overnight-heating-card" -> Tone.Heating
    "custom:fan-card", "custom:air-purifier-card" -> Tone.Air
    "custom:alarm-panel-card", "custom:security-zone-card", "custom:safety-card", "custom:co-alarm-card", "custom:camera-card" -> Tone.Blue
    "custom:cover-card" -> Tone.Blinds
    "custom:ev-charger-card", "custom:car-card", "custom:octopus-card" -> Tone.Car
    "custom:people-card" -> Tone.People
    "custom:task-list-card", "todo-list" -> Tone.Tasks
    "custom:shopping-card" -> Tone.Shopping
    else -> Tone.Grey
}

/** The colour of an own section set to Dynamic: it follows the state of its first card (the alarm's colour, lights on, heating), and is Auto's for any other. */
fun dynamicTone(cards: List<CardSpec>, entities: Map<String, EntityState>): Tone {
    val first = cards.firstOrNull() ?: return Tone.Grey
    val c = first.config
    return when (first.type.removeSuffix("-beta")) {
        "custom:alarm-panel-card" -> alarmTone(entities[c.optString("entity").ifBlank { ALARM_ENTITY }]?.state)
        "custom:light-control-card" -> DashboardLights.room(c)?.head?.let { if (entities[it]?.state == "on") Tone.Lights else Tone.Grey } ?: Tone.Grey
        "custom:climate-card", "custom:climate-zone-card" -> climateTone(entities[c.optString("entity")])
        "custom:fan-card", "custom:air-purifier-card" -> if (entities[c.optString("entity")]?.state == "on") Tone.Air else Tone.Grey
        else -> autoTone(cards)
    }
}

/** An own section's colour: a palette name it was given, "dynamic" to follow state, or (none) the colour of what is inside. */
fun ownTone(colour: String?, cards: List<CardSpec>, entities: Map<String, EntityState>): Tone = when (colour) {
    null, "auto" -> autoTone(cards)
    "dynamic" -> dynamicTone(cards, entities)
    else -> Tone.entries.firstOrNull { it.name.equals(colour, ignoreCase = true) } ?: autoTone(cards)
}

/** One of a person's own sections on the Home page: a panel of the cards they chose, in the colour they chose. */
@Composable
fun OwnHomePanel(panel: PanelSpec, entities: Map<String, EntityState>, registry: Registry, call: CallService) {
    val cards = visibleCards(panel.cards, LocalIsAdmin.current)
    if (cards.isEmpty()) return
    DashboardPanel(panel.copy(color = null), toneOverride = ownTone(panel.color, cards, entities)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            cards.forEach { CardView(it, entities, registry, call) }
        }
    }
}
