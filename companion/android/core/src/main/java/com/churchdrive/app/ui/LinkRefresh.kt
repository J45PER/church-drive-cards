package com.churchdrive.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PullToRefreshBox
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.churchdrive.app.ha.ConnectionState
import com.churchdrive.app.ha.LinkStatus
import com.churchdrive.app.ha.PillKind
import com.churchdrive.app.ha.pillKind
import kotlinx.coroutines.delay

/** Everything the pill can say. The first five follow the line (see [PillKind]); the rest belong to a pull down to refresh. */
enum class PillFace(val label: String) {
    Hidden(""),
    Connecting("Connecting"),
    Updating("Updating"),
    NoConnection("No connection"),
    Restarting("Home Assistant restarting"),
    Pull("Pull to refresh"),
    Release("Release to refresh"),
    Done("Up to date"),
}

/** The pill for a line that is not being pulled: connecting, updating, no connection or restarting. */
fun faceOf(kind: PillKind): PillFace = when (kind) {
    PillKind.None -> PillFace.Hidden
    PillKind.Connecting -> PillFace.Connecting
    PillKind.Updating -> PillFace.Updating
    PillKind.NoConnection -> PillFace.NoConnection
    PillKind.Restarting -> PillFace.Restarting
}

/** How far through a pull the pill has got, from 0 (not pulled) to 1 (far enough to refresh on release). */
private const val PULL_FULL = 1f

/**
 * Wraps a page's scrolling content with pull-down-to-refresh and the status pill that every Church Drive app shows at its top:
 * Connecting, Updating, No connection and Home Assistant restarting. Pulling shows the same pill ("Pull to refresh", "Release to
 * refresh"), which turns into "Updating" while [onRefresh] runs and "Up to date" when it is done, so the pull and the line's own
 * states are one thing to look at. Tapping No connection or Restarting tries again.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkRefresh(link: LinkStatus, onRefresh: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val current by rememberUpdatedState(link)
    // How long the line has been down, so a blip stays "Connecting" and only a real outage says "No connection".
    var offlineFor by remember { mutableStateOf(0L) }
    val connected = link.connection == ConnectionState.Connected
    LaunchedEffect(connected) {
        offlineFor = 0L
        while (!connected) {
            delay(1_000L)
            offlineFor += 1_000L
        }
    }
    val kind = pillKind(link, offlineFor)

    // The pull: Idle, then Refreshing while we wait for the house to answer, then Done for a moment.
    var phase by remember { mutableStateOf(0) } // 0 idle, 1 refreshing, 2 done
    LaunchedEffect(phase) {
        if (phase == 1) {
            // Long enough to see, short enough not to nag; give up waiting after twelve seconds.
            delay(900L)
            var waited = 0L
            while ((current.refreshing || current.connection != ConnectionState.Connected || !current.statesLoaded) && waited < 12_000L) {
                delay(100L)
                waited += 100L
            }
            if (current.connection == ConnectionState.Connected) {
                phase = 2
                delay(1_300L)
            }
            phase = 0
        }
    }
    val state = rememberPullToRefreshState()
    val pull = state.distanceFraction
    val face = when {
        phase == 2 -> PillFace.Done
        phase == 1 -> if (kind == PillKind.None || kind == PillKind.Updating) PillFace.Updating else faceOf(kind)
        pull > 0f && kind != PillKind.Restarting -> if (pull >= PULL_FULL) PillFace.Release else PillFace.Pull
        else -> faceOf(kind)
    }
    PullToRefreshBox(
        isRefreshing = phase == 1,
        onRefresh = { phase = 1; onRefresh() },
        modifier = modifier,
        state = state,
        indicator = {},
    ) {
        content()
        Box(Modifier.align(Alignment.TopCenter).padding(top = 8.dp)) {
            StatusPill(
                face = face,
                pull = if (face == PillFace.Pull || face == PillFace.Release) pull else 0f,
                onTap = if (face == PillFace.NoConnection || face == PillFace.Restarting) onRefresh else null,
            )
        }
    }
}

/** The pill itself. [pull] (0 to 1.3) slides it down and turns its mark while it is being pulled. A tap runs [onTap] when it is given. */
@Composable
fun StatusPill(face: PillFace, pull: Float = 0f, onTap: (() -> Unit)? = null) {
    val scheme = MaterialTheme.colorScheme
    val (container, ink) = when (face) {
        PillFace.NoConnection -> scheme.errorContainer to scheme.onErrorContainer
        PillFace.Restarting -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        else -> scheme.surfaceContainerHigh to scheme.onSurface
    }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val travel = with(density) { 36.dp.toPx() }
    AnimatedVisibility(
        visible = face != PillFace.Hidden,
        enter = fadeIn() + slideInVertically { -it },
        exit = fadeOut() + slideOutVertically { -it },
    ) {
        Surface(
            modifier = Modifier
                .graphicsLayer { if (pull > 0f) translationY = (pull.coerceAtMost(1.3f) - 1f) * travel }
                .semantics { liveRegion = LiveRegionMode.Polite }
                .let { if (onTap != null) it.clickable(onClickLabel = "Try again", onClick = onTap) else it },
            shape = RoundedCornerShape(50), color = container, contentColor = ink,
            border = BorderStroke(1.dp, scheme.outlineVariant), shadowElevation = 4.dp,
        ) {
            AnimatedContent(
                targetState = face,
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                modifier = Modifier.animateContentSize(),
                label = "pill",
            ) { f ->
                Row(
                    Modifier.padding(start = 10.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PillIcon(f, ink, pull)
                    Text(f.label, style = MaterialTheme.typography.labelLarge, color = ink)
                }
            }
        }
    }
}

/** The small picture at the pill's left: Clive's mark (turning as you pull), an offline signal, a restart spinner or a tick. */
@Composable
private fun PillIcon(face: PillFace, ink: Color, pull: Float) {
    val spin = rememberInfiniteTransition(label = "spin")
    val turn by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(1_100, easing = LinearEasing)), label = "turn")
    val pulse by spin.animateFloat(0.35f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse")
    when (face) {
        PillFace.Connecting, PillFace.Pull, PillFace.Release, PillFace.Updating ->
            Box(
                Modifier.size(22.dp).graphicsLayer {
                    rotationZ = when (face) {
                        PillFace.Pull -> pull * 180f
                        PillFace.Release -> 180f
                        PillFace.Updating -> turn
                        else -> 0f
                    }
                },
            ) { AssistantGlyph(AssistantGlyphPose.Idle, ink, 22.dp) }
        PillFace.Restarting -> CircularProgressIndicator(Modifier.size(20.dp), color = ink, strokeWidth = 2.dp)
        PillFace.NoConnection -> Canvas(Modifier.size(22.dp)) {
            // Three signal bars with a line through them, breathing so it reads as "still trying".
            val w = size.width
            val h = size.height
            val bar = w * 0.16f
            listOf(0.35f, 0.6f, 0.85f).forEachIndexed { i, top ->
                val x = w * (0.18f + i * 0.27f)
                drawLine(ink.copy(alpha = pulse), Offset(x, h * 0.9f), Offset(x, h * (1f - top) + h * 0.1f), strokeWidth = bar, cap = StrokeCap.Round)
            }
            drawLine(ink, Offset(w * 0.08f, h * 0.08f), Offset(w * 0.92f, h * 0.92f), strokeWidth = bar * 0.9f, cap = StrokeCap.Round)
        }
        PillFace.Done -> {
            val draw by androidx.compose.animation.core.animateFloatAsState(1f, tween(380), label = "tick")
            Canvas(Modifier.size(22.dp)) {
                val w = size.width
                val h = size.height
                val a = Offset(w * 0.2f, h * 0.55f)
                val b = Offset(w * 0.42f, h * 0.76f)
                val c = Offset(w * 0.82f, h * 0.28f)
                val stroke = Stroke(width = w * 0.12f, cap = StrokeCap.Round)
                // The tick draws itself: the short stroke first, then the long one.
                val first = (draw * 2f).coerceAtMost(1f)
                val second = ((draw - 0.5f) * 2f).coerceIn(0f, 1f)
                drawLine(ink, a, Offset(a.x + (b.x - a.x) * first, a.y + (b.y - a.y) * first), strokeWidth = stroke.width, cap = stroke.cap)
                if (second > 0f) drawLine(ink, b, Offset(b.x + (c.x - b.x) * second, b.y + (c.y - b.y) * second), strokeWidth = stroke.width, cap = stroke.cap)
            }
        }
        PillFace.Hidden -> Unit
    }
}
