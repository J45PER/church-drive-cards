package com.churchdrive.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

// Clive's colours, cloud and round button, shared by the phone app and the to-do app so the button looks the same in both
// (it used to live in the phone app, and the to-do app drew its own plain circle). The phone app chooses the palette
// (Material You or the original indigo, purple and green); apps with no such setting pass [AssistClassicPalette].

/**
 * The three colours of the assistant, a gradient like Siri's or Gemini's. They come from the phone's own theme (Material You: the
 * primary, tertiary and secondary colours of the wallpaper palette), so Clive matches the rest of the phone.
 */
@androidx.compose.runtime.Immutable
class AssistPalette(val first: Color, val second: Color, val third: Color) {
    val across: Brush get() = Brush.horizontalGradient(listOf(first, second, third))
}

/** The original Church Drive look: indigo, purple and green. */
val AssistClassicPalette = AssistPalette(Color(0xFF5C6BC0), Color(0xFFAB47BC), Color(0xFF4FD08A))

/**
 * The cloud: three soft clouds (the phone theme's three accent colours) that drift slowly round each other and blend where they meet. Calm when it
 * is waiting, a little livelier and a gentle swell with the voice when listening, thinking or speaking.
 */
@Composable
fun AssistOrb(state: AssistState, level: Float, size: androidx.compose.ui.unit.Dp, pal: AssistPalette) {
    val t = rememberInfiniteTransition(label = "orb")
    // One drift cycle, always the same length (changing it with the state made the clouds jump); the multipliers below are whole numbers so the end of a cycle meets its start.
    val phase by t.animateFloat(0f, (2 * Math.PI).toFloat(), infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "phase")
    val breathe by t.animateFloat(-1f, 1f, infiniteRepeatable(tween(4500), RepeatMode.Reverse), label = "breathe")
    // One 12 second clock: the bloom (2 s), heartbeat (1.5 s) and wave (1.2 s) repeat a whole number of times in it, so nothing jumps.
    val clock by t.animateFloat(0f, 1f, infiniteRepeatable(tween(12000, easing = LinearEasing)), label = "clock")
    val orb = size
    // Everything below is measured in the design mock-up's pixels (the cloud is 120 wide) and scaled to the real size, so it looks like the mock-up.
    val small = orb < 80.dp
    // The soft edges and the rings reach well past the cloud, so the drawing area is twice its size (centred, not taking room): a smaller one cut the glow off in a square.
    Box(modifier = Modifier.size(orb * 1.5f), contentAlignment = Alignment.Center) {
    Canvas(modifier = Modifier.requiredSize(orb * 2.2f).graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)) {
        val c = center
        val twoPi = 2f * Math.PI.toFloat()
        // The whole cloud swells or beats as one: idle breathes, listening swells with the bloom, thinking has a heartbeat.
        val group = when (state) {
            AssistState.Listening -> 1f + 0.06f * sin(clock * 5f * twoPi)
            AssistState.Thinking -> {
                val x = (clock * 8f) % 1f
                val pts = floatArrayOf(0f, 0.15f, 0.30f, 0.45f, 1f)
                val vals = floatArrayOf(0.94f, 1.14f, 0.98f, 1.08f, 0.94f)
                var i = 0
                while (i < 3 && x > pts[i + 1]) i++
                vals[i] + (vals[i + 1] - vals[i]) * ((x - pts[i]) / (pts[i + 1] - pts[i]))
            }
            AssistState.Speaking -> 1f
            else -> 1f + 0.05f * breathe
        }
        val u = orb.toPx() / 120f * group
        // Idle and thinking drift gently; listening and speaking hold still so the bloom and the wave read clearly.
        val drift = if (state == AssistState.Listening || state == AssistState.Speaking) 0f else 11f * u
        val spread = if (small) 1.5f else 1f
        fun cloud(colour: Color, dx: Float, dy: Float, disc: Float, a: Float, b: Float, wavePhase: Float) {
            val at = Offset(c.x + dx * u * spread + cos(phase * a) * drift, c.y + dy * u * spread + sin(phase * b) * drift)
            var radius = disc * u
            var alpha = 1f
            if (state == AssistState.Speaking) {
                // A wave: each colour swells in turn.
                val w = 0.5f - 0.5f * cos(((clock * 10f + wavePhase) % 1f) * twoPi)
                radius *= 0.85f + 0.4f * w
                alpha = 0.7f + 0.3f * w
            }
            val outer = radius + 26f * u
            if (small) radius *= 0.9f
            drawCircle(
                Brush.radialGradient(
                    0f to colour.copy(alpha = alpha), ((radius - 26f * u).coerceAtLeast(0f) / outer) to colour.copy(alpha = alpha),
                    (radius / outer) to colour.copy(alpha = 0.5f * alpha), 1f to Color.Transparent,
                    center = at, radius = outer,
                ),
                radius = outer, center = at, blendMode = BlendMode.Screen,
            )
        }
        cloud(pal.first, -13f, -1f, 42f, 1f, 2f, 0f)
        cloud(pal.second, 15.5f, -13.5f, 39.5f, 2f, 1f, 0.33f)
        cloud(pal.third, -3f, 15f, 37f, 1f, 1f, 0.66f)
        // Listening: soft rings bloom outwards from the cloud and melt away, one colour after another.
        if (state == AssistState.Listening) {
            listOf(pal.first, pal.second, pal.third).forEachIndexed { i, colour ->
                val x = ((clock * 6f) + i / 3f) % 1f
                val ringR = (35f + 35f * x) * u
                val outer = ringR + 19f * u
                val a = 0.47f * (1f - x) * (0.8f + 0.4f * level.coerceIn(0f, 1f))
                drawCircle(
                    Brush.radialGradient(
                        0f to Color.Transparent, ((ringR - 19f * u).coerceAtLeast(0f) / outer) to Color.Transparent,
                        (ringR / outer) to colour.copy(alpha = a), 1f to Color.Transparent,
                        center = c, radius = outer,
                    ),
                    radius = outer, center = c,
                )
            }
        }
    }
    }
}

/**
 * The round button at the bottom right: his coloured orb on a circle the colour of the bottom bar, with his white mark over it (animated: see AssistantGlyph): the drifting logo when he is shut, a microphone when he waits, a squircle to stop while he listens. Same size and place everywhere, above the bottom bar.
 */
@Composable
fun AssistantRound(label: String, onClick: () -> Unit, pal: AssistPalette, state: AssistState = AssistState.Idle, level: Float = 0f, content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier.size(58.dp).shadow(6.dp, CircleShape).clip(CircleShape)
            .background(androidx.compose.material3.NavigationBarDefaults.containerColor)
            // The same outline as the typing box next to it.
            .border(1.dp, scheme.outlineVariant, CircleShape)
            .clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // The coloured orb behind the mark moves with what he is doing, as before; the white mark on top has its own poses (see AssistantGlyph).
        // The orb's own box is one and a half times this size, so it stays inside the 54 dp the ring leaves (with a little room to spare).
        AssistOrb(state, level, 32.dp, pal)
        // While he listens a ripple spreads from the middle of the button, over and over: it is a stop button, and it is live.
        if (state == AssistState.Listening) {
            val ripple = androidx.compose.animation.core.rememberInfiniteTransition(label = "ripple")
            val p by ripple.animateFloat(0f, 1f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(1400, easing = androidx.compose.animation.core.LinearOutSlowInEasing)), label = "rp")
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension / 2f
                drawCircle(Color.White.copy(alpha = 0.55f * (1f - p)), radius = r * (0.4f + 0.6f * p), style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
            }
        }
        content()
    }
}
