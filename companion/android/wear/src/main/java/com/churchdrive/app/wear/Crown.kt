package com.churchdrive.app.wear

import android.view.View
import androidx.compose.foundation.focusable
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalView
import kotlin.math.abs

/**
 * The watch's crown (or rotating bezel): each notch calls [onNotch] with 1 for clockwise (down) and -1 for the other way (up). A notch needs a good
 * turn (small nudges gather up rather than each counting), so a page does not race past. The crown only scrolls: it never changes a value.
 */
@Composable
fun Modifier.crown(onNotch: (Int) -> Unit): Modifier {
    val latest = rememberUpdatedState(onNotch)
    val focus = remember { FocusRequester() }
    val gathered = remember { floatArrayOf(0f) }
    val lastAt = remember { longArrayOf(0L) }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    return this
        .onRotaryScrollEvent { event ->
            // A little turn that was left unfinished is forgotten after a moment, so it never adds to the next one by surprise.
            if (event.uptimeMillis - lastAt[0] > FORGET_MS) gathered[0] = 0f
            lastAt[0] = event.uptimeMillis
            gathered[0] += event.verticalScrollPixels
            while (abs(gathered[0]) >= NOTCH_PIXELS) {
                val way = if (gathered[0] > 0f) 1 else -1
                latest.value(way)
                gathered[0] -= way * NOTCH_PIXELS
            }
            true
        }
        .focusRequester(focus)
        .focusable()
}

private const val NOTCH_PIXELS = 48f
private const val FORGET_MS = 300L

/** Keeps the glass lit while [on] is true (a watch turns its screen off after a few seconds otherwise, even in the middle of waiting for an answer). */
@Composable
fun KeepAwake(on: Boolean) {
    val view: View = LocalView.current
    DisposableEffect(view, on) {
        view.keepScreenOn = on
        onDispose { view.keepScreenOn = false }
    }
}

/**
 * Moves [n] pages from where the pager is heading (not from where it last settled): a quick turn of several notches goes several pages
 * in one glide, instead of each notch starting from the old page and cancelling the one before it.
 */
suspend fun PagerState.stepBy(n: Int, count: Int) {
    if (count <= 0) return
    val from = if (isScrollInProgress) targetPage else currentPage
    animateScrollToPage((from + n).coerceIn(0, count - 1))
}
