package com.churchdrive.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import kotlinx.coroutines.delay

/** The timings every pop-up uses, so they all open and close the same way. */
object PopupMotion {
    const val EnterMs = 280
    const val ExitMs = 240
    const val ScrimMs = 240

    /** How dark the page behind a card or sheet goes. */
    const val ScrimAlpha = 0.34f
}

/** Where a pop-up sits: the whole screen, a sheet from the bottom, or a card in the middle. */
enum class PopupShape { Full, Sheet, Card }

/**
 * The one way every pop-up opens and closes. The page behind fades dark by itself, in place; the pop-up rises from below and fades in,
 * and leaves the same way backwards. The window has no animation of its own (it would drag the dimmed page along with the card).
 *
 * Close it with the `close` passed to [content] (or Back, or a tap outside a card or sheet): it plays the exit and then calls
 * [onDismissRequest]. [handleBack] can take a Back press first (leaving a sub-screen, say) by returning true.
 */
@Composable
fun PopupWindow(
    onDismissRequest: () -> Unit,
    shape: PopupShape = PopupShape.Full,
    handleBack: () -> Boolean = { false },
    content: @Composable (close: () -> Unit) -> Unit,
) {
    var shown by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val close: () -> Unit = { if (!leaving) { leaving = true; shown = false } }
    LaunchedEffect(leaving) { if (leaving) { delay(PopupMotion.ExitMs + 30L); onDismissRequest() } }
    Dialog(
        onDismissRequest = { if (!handleBack()) close() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val view = LocalView.current
        SideEffect {
            (view.parent as? DialogWindowProvider)?.window?.let {
                it.setWindowAnimations(-1)
                it.setDimAmount(0f)
            }
        }
        val dim by animateFloatAsState(if (shown && shape != PopupShape.Full) PopupMotion.ScrimAlpha else 0f, tween(PopupMotion.ScrimMs), label = "scrim")
        Box(Modifier.fillMaxSize()) {
            if (shape != PopupShape.Full) {
                Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim))
                        .clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { close() },
                )
            }
            AnimatedVisibility(
                visible = shown,
                modifier = when (shape) {
                    PopupShape.Full -> Modifier.fillMaxSize()
                    PopupShape.Sheet -> Modifier.align(Alignment.BottomCenter)
                    PopupShape.Card -> Modifier.align(Alignment.Center)
                },
                enter = slideInVertically(tween(PopupMotion.EnterMs)) { if (shape == PopupShape.Card) it / 6 else it } + fadeIn(tween(PopupMotion.EnterMs - 80)),
                exit = slideOutVertically(tween(PopupMotion.ExitMs)) { if (shape == PopupShape.Card) it / 6 else it } + fadeOut(tween(PopupMotion.ExitMs - 40)),
            ) {
                content(close)
            }
        }
    }
}
