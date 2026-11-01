package com.churchdrive.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// The look of the main app's cards, for the smaller apps (To-Do, Satellites) to share instead of each keeping its own copy: a tone icon in a
// tinted circle for a section's heading, a 28dp card tinted by a tone, a pill, a 48dp choice tile, and the round button with the person's
// initial. (The phone app has its own richer versions in ui/Cards.kt and ui/UiKit.kt; these are named Tone... so they never clash with them.)

val ToneCardShape = RoundedCornerShape(28.dp)
val ToneTileShape = RoundedCornerShape(12.dp)
val ToneTallTileShape = RoundedCornerShape(18.dp)

/** A section's heading: its tone icon in a tinted circle, the title, and a short summary at the right. */
@Composable
fun ToneSectionHeader(title: String, icon: ImageVector, tone: Tone, summary: String? = null) {
    val colors = toneColors(tone)
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(36.dp).background(colors.accent.copy(alpha = 0.18f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = colors.accent, modifier = Modifier.size(20.dp))
        }
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (!summary.isNullOrBlank()) Text(summary, style = MaterialTheme.typography.bodyMedium, color = colors.accent)
    }
}

/** The shared card: rounded 28dp, tinted in a tone (or the neutral surface), 20dp inside, 16dp between things. */
@Composable
fun ToneCard(tone: Tone?, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, body: @Composable ColumnScope.(ink: Color, soft: Color) -> Unit) {
    val colors = tone?.let { toneColors(it) }
    val container = colors?.container ?: MaterialTheme.colorScheme.surfaceContainer
    val ink = colors?.onContainer ?: MaterialTheme.colorScheme.onSurface
    val soft = ink.copy(alpha = 0.75f)
    Column(
        modifier.fillMaxWidth().clip(ToneCardShape).background(container).let { if (onClick != null) it.clickable(onClick = onClick) else it }.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) { body(ink, soft) }
}

@Composable
fun TonePill(text: String, tone: Tone, onClick: (() -> Unit)? = null) {
    val accent = toneColors(tone).accent
    Text(
        text, color = accent, style = MaterialTheme.typography.labelMedium, maxLines = 1,
        modifier = Modifier.clip(CircleShape).background(accent.copy(alpha = 0.18f)).let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

/** A choice tile: 48dp high, the chosen one filled in the tone's strong colour (as the main app's tile rows). */
@Composable
fun ToneTile(label: String, selected: Boolean, tone: Tone, ink: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = toneColors(tone)
    Box(
        modifier.height(48.dp).clip(ToneTileShape).background(if (selected) colors.accent else ink.copy(alpha = 0.10f)).clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = if (selected) colors.onAccent else ink, fontSize = 13.sp, maxLines = 1) }
}

@Composable
fun PersonButton(name: String?, onClick: () -> Unit) {
    Box(
        Modifier.padding(horizontal = 6.dp).size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClickLabel = "Account and settings", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(name?.trim()?.firstOrNull()?.uppercase() ?: "?", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}
