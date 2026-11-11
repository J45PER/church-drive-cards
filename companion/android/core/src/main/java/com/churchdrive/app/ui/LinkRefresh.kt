package com.churchdrive.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.animation.EnterTransition
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.height
import androidx.compose.animation.scaleOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
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
 * states are one thing to look at. Tapping No connection or Restarting tries again. [palette] is Clive's three colours (the phone app passes
 * its own; the other apps use the original indigo, purple and green). Put it OUTSIDE the Scaffold, never inside it: the page's collapsing title bar
 * listens for scrolling from the Scaffold, and a pull inside would take the downward drag first, so the title could not expand. [pillTop] is how far
 * below the status bar the pill sits (the collapsed title bar is 64 dp tall).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkRefresh(link: LinkStatus, onRefresh: () -> Unit, modifier: Modifier = Modifier, palette: AssistPalette = AssistClassicPalette, pillTop: Dp = 72.dp, content: @Composable () -> Unit) {
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
    // One effect per phase: changing the phase restarts the effect, so each phase hands over to the next rather than finishing the job itself.
    LaunchedEffect(phase) {
        when (phase) {
            1 -> {
                // Long enough to see, short enough not to nag; give up waiting after twelve seconds.
                delay(900L)
                var waited = 0L
                while ((current.refreshing || current.connection != ConnectionState.Connected || !current.statesLoaded) && waited < 12_000L) {
                    delay(100L)
                    waited += 100L
                }
                phase = if (current.connection == ConnectionState.Connected) 2 else 0
            }
            2 -> {
                delay(1_300L)
                phase = 0
            }
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
        Box(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = pillTop)) {
            StatusPill(
                face = face,
                pull = if (face == PillFace.Pull || face == PillFace.Release) pull else 0f,
                onTap = if (face == PillFace.NoConnection || face == PillFace.Restarting) onRefresh else null,
                palette = palette,
            )
        }
    }
}

/** The pill's height, which is also the width of the circle it starts as. */
private val PILL_SIZE = 40.dp

/**
 * The pill itself. It starts as Clive's round button (his colour cloud with the white mark over it), then widens into the pill with its
 * words; the cloud keeps drifting behind the whole pill. While being pulled it stays a circle until it is far enough to release, then widens.
 * [pull] (0 to 1.3) slides it down and turns its mark. A tap runs [onTap] when it is given. [palette] is Clive's three colours.
 */
@Composable
fun StatusPill(face: PillFace, pull: Float = 0f, onTap: (() -> Unit)? = null, palette: AssistPalette = AssistClassicPalette) {
    val scheme = MaterialTheme.colorScheme
    val visible = face != PillFace.Hidden
    // The face kept while the pill slides away, so it does not go blank on the way out.
    val shownState = remember { mutableStateOf(face) }
    if (visible) shownState.value = face
    val shown = shownState.value
    // No connection is the house's Red and a restart its Amber (the same palettes as the cards); everything else is the neutral pill.
    val red = toneColors(Tone.Red)
    val amber = toneColors(Tone.Amber)
    val (container, ink) = when (shown) {
        PillFace.NoConnection -> red.container to red.onContainer
        PillFace.Restarting -> amber.container to amber.onContainer
        else -> scheme.surfaceContainerHigh to scheme.onSurface
    }
    // The cloud takes the face's colour: red for no connection, amber for a restart, Clive's own otherwise.
    val cloud = when (shown) {
        PillFace.NoConnection -> toneSet(Tone.Red)!!.let { AssistPalette(it.colour, it.accent, it.colour) }
        PillFace.Restarting -> toneSet(Tone.Amber)!!.let { AssistPalette(it.colour, it.accent, it.colour) }
        else -> palette
    }
    // A moment as a circle first, then the words; a pull stays a circle until "Release".
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        settled = false
        if (visible) {
            delay(380L)
            settled = true
        }
    }
    val expanded = visible && settled && shown != PillFace.Pull
    val markColour by androidx.compose.animation.animateColorAsState(if (expanded) ink else Color.White, tween(300), label = "mark")
    val cloudAlpha by androidx.compose.animation.core.animateFloatAsState(if (expanded) 0.5f else 0.95f, tween(300), label = "cloud")
    // The pill's width is one animated value: how much of the words is showing (0 is the circle). The pill, its edge and the cloud all follow
    // it frame by frame, so the cloud can never get ahead of the pill (measuring inside animateContentSize gave the cloud the final width at once).
    val reveal by androidx.compose.animation.core.animateFloatAsState(if (expanded) 1f else 0f, tween(320), label = "reveal")
    val wordsAlpha by androidx.compose.animation.core.animateFloatAsState(if (expanded) 1f else 0f, if (expanded) tween(320, delayMillis = 80) else tween(120), label = "words")
    // The pill is on screen from the moment there is something to say until it has gone back into a circle, so closing runs the opening backwards
    // (words go, pill narrows to the circle, then the circle shrinks away) instead of everything leaving at once.
    var showing by remember { mutableStateOf(false) }
    // Brought in by a finger: the circle grows with the pull instead of popping up (the pill was not on screen when the pull began).
    var growing by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        if (visible) {
            growing = pull > 0f && !showing
            showing = true
        } else {
            if (reveal > 0.01f) delay(320L)
            showing = false
        }
    }
    LaunchedEffect(pull > 0f) { if (pull <= 0f) growing = false }
    var width by remember { mutableStateOf(0) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val travel = with(density) { 36.dp.toPx() }
    val cloudBox = with(density) { PILL_SIZE.toPx() }
    val shape = RoundedCornerShape(50)
    AnimatedVisibility(
        visible = showing,
        enter = if (growing) EnterTransition.None else fadeIn(tween(200)) + scaleIn(tween(300), initialScale = 0.4f) + slideInVertically { -it },
        // The opening backwards: shrinks to 0.4, goes up and fades out at the end.
        exit = fadeOut(tween(200, delayMillis = 100)) + scaleOut(tween(300), targetScale = 0.4f) + slideOutVertically { -it },
    ) {
        Surface(
            modifier = Modifier
                .graphicsLayer {
                    if (pull > 0f) translationY = (pull.coerceAtMost(1.3f) - 1f) * travel
                    // Growing into the circle as the finger pulls: from 30% size and clear, to full by half way to Release.
                    if (growing && pull > 0f) {
                        val e = androidx.compose.animation.core.FastOutSlowInEasing.transform((pull / 0.5f).coerceIn(0f, 1f))
                        scaleX = 0.3f + 0.7f * e
                        scaleY = scaleX
                        alpha = (pull / 0.3f).coerceIn(0f, 1f)
                    }
                }
                .semantics { liveRegion = LiveRegionMode.Polite }
                .let { if (onTap != null) it.clickable(onClickLabel = "Try again", onClick = onTap) else it },
            shape = shape, color = container, contentColor = ink,
            border = BorderStroke(1.dp, scheme.outlineVariant), shadowElevation = 6.dp,
        ) {
            // This box is as wide as the pill is right now, because its only wide child is the words, cut to [reveal] of their width.
            Box(Modifier.height(PILL_SIZE).onSizeChanged { width = it.width }, contentAlignment = Alignment.CenterStart) {
                // Clive's cloud, stretched to fill the pill and kept inside its edge.
                Box(Modifier.matchParentSize().clip(shape), contentAlignment = Alignment.Center) {
                    Box(Modifier.graphicsLayer { scaleX = (width / cloudBox * 1.25f).coerceAtLeast(1f); alpha = cloudAlpha }) {
                        AssistOrb(AssistState.Idle, 0f, 32.dp, cloud)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(PILL_SIZE), contentAlignment = Alignment.Center) {
                        AnimatedContent(shown, transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) }, label = "icon") { f ->
                            PillIcon(f, markColour, pull)
                        }
                    }
                    Box(
                        Modifier
                            .clipToBounds()
                            .layout { measurable, constraints ->
                                val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity))
                                layout((placeable.width * reveal).roundToInt(), placeable.height) { placeable.placeRelative(0, 0) }
                            }
                            .graphicsLayer { alpha = wordsAlpha },
                    ) {
                        AnimatedContent(shown, transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) }, label = "words") { f ->
                            Text(f.label, Modifier.padding(end = 18.dp), style = MaterialTheme.typography.labelLarge, color = ink, maxLines = 1, softWrap = false)
                        }
                    }
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
    // Every picture here is the house's own: Clive's mark, and the Clive set's icons (cd:wifi-off, cd:update-arrow, cd:check), never a Material one.
    when (face) {
        // The mark turns with the finger while pulling and has made half a turn by Release. Upside down it looks the same, so every other face
        // draws it the right way up with no jump, and only its own drifting (see AssistantGlyph) moves: no spinning once the pill is loaded.
        PillFace.Connecting, PillFace.Pull, PillFace.Release, PillFace.Updating ->
            Box(
                Modifier.size(22.dp).graphicsLayer {
                    rotationZ = when (face) {
                        PillFace.Pull -> pull * 180f
                        PillFace.Release -> 180f
                        else -> 0f
                    }
                },
            ) { AssistantGlyph(AssistantGlyphPose.Idle, ink, 22.dp) }
        // A circular arrow turning once every 1.1 s: Home Assistant is starting again.
        PillFace.Restarting -> CdIcon("cd:update-arrow", ink, PILL_ICON, Modifier.graphicsLayer { rotationZ = turn })
        // The crossed Wi-Fi, breathing so it reads as "still trying".
        PillFace.NoConnection -> CdIcon("cd:wifi-off", ink.copy(alpha = pulse), PILL_ICON)
        PillFace.Done -> {
            // Starts hidden and is wiped in from the left over 380 ms, so the tick still seems to draw itself (animateFloatAsState would begin already at 1).
            val drawn = remember { androidx.compose.animation.core.Animatable(0f) }
            LaunchedEffect(Unit) { drawn.animateTo(1f, tween(380)) }
            CdIcon(
                "cd:check", ink, PILL_ICON,
                Modifier.drawWithContent { clipRect(right = size.width * FastOutSlowInEasing.transform(drawn.value)) { this@drawWithContent.drawContent() } },
            )
        }
        PillFace.Hidden -> Unit
    }
}

/** The size of the Clive set's icons in the pill: their glyphs fill about three quarters of the box, which makes them weigh the same as the mark. */
private val PILL_ICON = 28.dp
