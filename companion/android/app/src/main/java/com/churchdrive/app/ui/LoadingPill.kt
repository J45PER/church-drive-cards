package com.churchdrive.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A pill under the title while the house is being read: "Updating" (or "Connecting") with Clive's mark drifting beside it. What the app
 * remembered from last time stays on screen underneath, and the pill goes as soon as the live answer is in. Never takes a tap.
 */
@Composable
fun LoadingPill(visible: Boolean, connecting: Boolean) {
    Box(Modifier.fillMaxSize().statusBarsPadding().padding(top = 70.dp), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(visible = visible, enter = fadeIn() + slideInVertically { -it / 2 }, exit = fadeOut() + slideOutVertically { -it / 2 }) {
            Surface(
                shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shadowElevation = 4.dp,
            ) {
                Row(Modifier.padding(start = 10.dp, end = 16.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistantGlyph(AssistantGlyphPose.Idle, MaterialTheme.colorScheme.onSurface, 22.dp)
                    Text(if (connecting) "Connecting" else "Updating", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}
