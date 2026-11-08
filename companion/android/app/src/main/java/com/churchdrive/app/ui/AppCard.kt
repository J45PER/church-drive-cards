package com.churchdrive.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * The common layout every card type is built on. Blank on purpose: a title row, then whatever the card puts in [content].
 *
 * - **Title row**: the card's [title] in [CardTitleStyle] (the size of the alarm card's title), one line, with the expand and collapse
 *   button at the right end, in the top right corner of the card. The row is 40 dp high so the button and the title line up.
 * - **Body**: [content], 16 dp between its parts, shown while the card is [expanded].
 * - **Folded**: the title row stays exactly where it is and the body is replaced by [summary] (the one thing worth keeping on show),
 *   or by nothing. A card that does not fold passes no [onExpandedChange] and has no button.
 *
 * Hold the state with [rememberCardExpanded]. The frame is [EntityCard] (28 dp corners, 20 dp padding, the card's tone and glow).
 */
@Composable
fun AppCard(
    title: String,
    tone: ToneColors,
    modifier: Modifier = Modifier,
    /** The title's colour: the card's strong colour by default, as on the alarm card. */
    titleColor: Color = tone.accent,
    expanded: Boolean = true,
    /** Called with the new state when the button is pressed; null for a card that cannot fold (no button). */
    onExpandedChange: ((Boolean) -> Unit)? = null,
    /** Shown in place of [content] while the card is folded. */
    summary: (@Composable ColumnScope.() -> Unit)? = null,
    /** An entity of the device the card shows: pressing and holding opens that device's health page. */
    deviceEntity: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    EntityCard(tone.container, tone.onContainer, modifier.animateContentSize(), deviceEntity) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = CARD_TITLE_ROW_HEIGHT),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = CardTitleStyle, color = titleColor, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (onExpandedChange != null) ExpandButton(expanded, tone.onContainer) { onExpandedChange(!expanded) }
        }
        if (expanded) Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { content() }
        else if (summary != null) Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { summary() }
    }
}

/** The title row's height: the title's line (32 dp) with room, and the button's size, so the two sit on one centre line. */
private val CARD_TITLE_ROW_HEIGHT = 40.dp

/** The expand and collapse button: a chevron in a 40 dp round target, nudged out so it sits in the card's top right corner. */
@Composable
private fun ExpandButton(expanded: Boolean, ink: Color, onClick: () -> Unit) {
    val label = if (expanded) "Collapse" else "Expand"
    Box(
        modifier = Modifier.offset(x = 8.dp).size(CARD_TITLE_ROW_HEIGHT).clip(CircleShape)
            .clickable(onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (expanded) CdVectors.ChevronUp else CdVectors.ChevronDown, contentDescription = null,
            tint = ink.copy(alpha = 0.6f), modifier = Modifier.size(24.dp),
        )
    }
}

/** Whether a card is open, remembered for [key] while the app is open (and across a rotation). Open to begin with. */
@Composable
fun rememberCardExpanded(key: String, default: Boolean = true): MutableState<Boolean> = rememberSaveable(key) { mutableStateOf(default) }
