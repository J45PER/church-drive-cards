package com.churchdrive.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

// The card every page of the phone app and of Clive Admin is built from (a rounded card tinted in a tone, with the Clive glow behind it),
// and the round icon that goes on it. They live in core so the two apps look the same.

private val CardShape = RoundedCornerShape(28.dp)

/** Opens a device's health page for an entity of that device (a long press on a card). Does nothing where the app hasn't set it. */
val LocalOpenDevice = compositionLocalOf<(String) -> Unit> { {} }

/** True while the section a card is in is folded: the card shows only its top, as a short strip. */
val LocalCompactCards = androidx.compose.runtime.compositionLocalOf { false }

@Composable
fun EntityCard(
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    /** An entity of the device this card shows: pressing and holding the card opens that device's health page. */
    deviceEntity: String? = null,
    body: @Composable () -> Unit,
) {
    val openDevice = LocalOpenDevice.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    Card(
        modifier = modifier.fillMaxWidth().then(
            if (deviceEntity == null) Modifier
            else Modifier.pointerInput(deviceEntity) {
                detectTapGestures(onLongPress = {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    openDevice(deviceEntity)
                })
            },
        ),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
    ) {
        Box(modifier = Modifier.glowBehind(container)) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) { body() }
        }
    }
}

/** An icon drawn from its name (`mdi:` or `phu:`) in a tinted circle; [fallback] shows when the name isn't known. */
@Composable
fun ToneIconName(name: String?, fallback: ImageVector, tone: ToneColors, size: Int = 40) {
    Box(
        modifier = Modifier.size(size.dp).background(tone.accent.copy(alpha = 0.18f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        HaIcon(name, fallback, tone.accent, (size * 0.55f).dp)
    }
}
