package com.churchdrive.app.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** How many lines an automation card on the Lights page always has, filled or not, so every card is the same size. */
const val AUTOMATION_LINES = 2

/**
 * A Lights page Automations card (administrators): the title and a count, then the next [AUTOMATION_LINES] things as a name and a short note each,
 * on lines that are always there (a short list leaves them empty). It never folds; pressing and holding it opens its page.
 */
@Composable
internal fun AutomationCard(title: String, count: String, lines: List<Pair<String, String>>, empty: String, tone: ToneColors, onOpen: () -> Unit) {
    val ink = tone.onContainer
    Box(Modifier.pointerInput(Unit) { detectTapGestures(onLongPress = { onOpen() }) }) {
        EntityCard(tone.container, ink) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, color = ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                StatusPill(count, tone)
            }
            val shown = lines.take(AUTOMATION_LINES)
            repeat(AUTOMATION_LINES) { i ->
                val line = shown.getOrNull(i)
                if (line != null) {
                    Row(Modifier.fillMaxWidth().height(24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(line.first, style = MaterialTheme.typography.bodyLarge, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(line.second, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = ink.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                } else if (i == 0) {
                    Text(empty, Modifier.height(24.dp), style = MaterialTheme.typography.bodyLarge, color = ink)
                } else {
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}
