package com.churchdrive.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp

/**
 * A pop-up menu of actions (more than there is room for), in the pickers' grey card: 28 dp corners, [AppMenuItem] rows. Not for choosing a
 * value: that is a picker dialog ([PickDialog]) or a row of tiles.
 */
@Composable
fun AppMenu(expanded: Boolean, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val tone = toneColors(Tone.Grey)
    DropdownMenu(
        expanded = expanded, onDismissRequest = onDismiss, modifier = Modifier.width(250.dp),
        shape = RoundedCornerShape(28.dp), containerColor = lerp(tone.container, tone.accent, 0.07f), tonalElevation = 0.dp,
    ) {
        androidx.compose.foundation.layout.Column(Modifier.padding(horizontal = 6.dp)) { content() }
    }
}

/** One row of an [AppMenu]: a 24 dp icon, then the words; [selected] washes it, [destructive] makes it red. */
@Composable
fun AppMenuItem(icon: String, label: String, onClick: () -> Unit, selected: Boolean = false, destructive: Boolean = false) {
    val tone = toneColors(Tone.Grey)
    val ink = if (destructive) Color(0xFFF28B82) else tone.onContainer
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(18.dp))
            .background(if (selected) tone.onContainer.copy(alpha = 0.10f) else Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        HaIcon(icon, CdVectors.Info, if (destructive) ink else tone.accent, 24.dp)
        Text(label, style = MaterialTheme.typography.bodyLarge, color = ink)
    }
}
