package com.churchdrive.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * The one on/off switch, for a setting that stays as set: off is a faint track with a light thumb, on is the strong colour with the dark ink
 * as the thumb, no outline. [tone] is the surrounding card's colour; without one the app's own accent is used.
 */
@Composable
fun AppSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true, tone: ToneColors? = null) {
    val ink = tone?.onContainer ?: MaterialTheme.colorScheme.onSurface
    val accent = tone?.accent ?: MaterialTheme.colorScheme.primary
    val onAccent = tone?.onAccent ?: MaterialTheme.colorScheme.onPrimary
    Switch(
        checked = checked, onCheckedChange = onCheckedChange, modifier = modifier, enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = onAccent, checkedTrackColor = accent, checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = ink.copy(alpha = 0.7f), uncheckedTrackColor = ink.copy(alpha = 0.14f), uncheckedBorderColor = Color.Transparent,
            disabledCheckedThumbColor = onAccent.copy(alpha = 0.6f), disabledCheckedTrackColor = accent.copy(alpha = 0.4f), disabledCheckedBorderColor = Color.Transparent,
            disabledUncheckedThumbColor = ink.copy(alpha = 0.4f), disabledUncheckedTrackColor = ink.copy(alpha = 0.08f), disabledUncheckedBorderColor = Color.Transparent,
        ),
    )
}
