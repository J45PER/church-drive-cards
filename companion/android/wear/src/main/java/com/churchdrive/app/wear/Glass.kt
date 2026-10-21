package com.churchdrive.app.wear

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * The size of the watch's glass in one number. Every screen is laid out on a 280 unit square (the size of the mock-ups), and [u] is how
 * big one unit is on this watch, so a 41 mm and a 45 mm Pixel Watch look the same.
 */
@Immutable
class Ui(val u: Dp) {
    fun d(v: Float): Dp = u * v
    fun sp(v: Float): TextUnit = (v * u.value).sp
}

val LocalUi = compositionLocalOf { Ui(1.dp) }

/** Puts a child [x], [y] units from the top left of the glass; with [w] and [h] it also gets that size (just [w] gives only a width). */
fun Modifier.at(ui: Ui, x: Float, y: Float, w: Float? = null, h: Float? = null): Modifier {
    val moved = this.offset(ui.d(x), ui.d(y))
    return when {
        w != null && h != null -> moved.size(ui.d(w), ui.d(h))
        w != null -> moved.width(ui.d(w))
        else -> moved
    }
}

// ---------------------------------------------------------------------------------------------------- what the glass shows

/** The coloured part of the ring, from its start round to [f] (0 to 1). */
data class RingFill(val f: Float, val color: Color, val alpha: Float = 1f)

/** The round handle that marks a target on the ring. */
data class RingDot(val f: Float, val color: Color)

/** Ring cut into segments (a fan's speeds, a vacuum's suction): one alpha for each, so the ones that are lit are bright. */
data class RingSegs(val color: Color, val alphas: List<Float>)

/** What turning the ring does: [onValue] gets the new position (0 to 1) as a finger moves round it. */
class RingDrag(val onValue: (Float) -> Unit)

/**
 * What the glass (the face's colour and the ring round its edge) shows for one face. The ring and the colour stay put while faces slide
 * over them, and animate from one face's spec to the next. Angles are in degrees clockwise from three o'clock; the usual ring starts
 * at the lower left and runs [sweep] degrees over the top.
 */
data class RingSpec(
    val bg: Color,
    val a0: Float = 150f,
    val sweep: Float = 240f,
    val trackColor: Color = Color.White,
    val trackAlpha: Float = 0.12f,
    val fill: RingFill? = null,
    val dot: RingDot? = null,
    val segs: RingSegs? = null,
    /** Air quality: a green to red gradient round the ring, bright up to this fraction. */
    val gradient: Float? = null,
    val drag: RingDrag? = null,
    /** Follow the finger at once instead of gliding (while a ring is being turned). */
    val instant: Boolean = false,
)

/** One screen of a list: what the glass shows for it, and its content. */
class Face(val id: String, val spec: RingSpec, val content: @Composable () -> Unit)

// ---------------------------------------------------------------------------------------------------- drawing

/** The air-quality gradient: green through amber and orange to red, the same colours as the dashboard's PM2.5 bands. */
private val GRADIENT = listOf(
    0f to Color(0xFF4CAF50), 0.2f to Color(0xFF4CAF50), 0.37f to Color(0xFFFFA726), 0.63f to Color(0xFFFF7043), 0.87f to Color(0xFFE53935), 1f to Color(0xFFE53935),
)

fun gradientColour(f: Float): Color {
    val x = f.coerceIn(0f, 1f)
    for (i in 1 until GRADIENT.size) {
        if (x <= GRADIENT[i].first) {
            val (p0, c0) = GRADIENT[i - 1]
            val (p1, c1) = GRADIENT[i]
            val t = (x - p0) / (p1 - p0).coerceAtLeast(0.0001f)
            return androidx.compose.ui.graphics.lerp(c0, c1, t)
        }
    }
    return GRADIENT.last().second
}

/** Remembers what each optional part of the ring last showed, so that a part that goes away fades out where it was instead of jumping. */
private class RingMemory {
    var fill = RingFill(0f, Color.White, 0f)
    var dot = RingDot(0f, Color.White)
    var segs = RingSegs(Color.White, listOf(0f, 0f, 0f, 0f, 0f))
    var gradient = 0f
}

private const val SEG_COUNT = 5

/** The glass: the face's colour, and the ring on it. Everything animates to the next face's values. */
@Composable
fun Glass(spec: RingSpec, modifier: Modifier = Modifier) {
    val mem = remember { RingMemory() }
    spec.fill?.let { mem.fill = it }
    spec.dot?.let { mem.dot = it }
    spec.segs?.let { mem.segs = it.copy(alphas = (it.alphas + List(SEG_COUNT) { 0f }).take(SEG_COUNT)) }
    spec.gradient?.let { mem.gradient = it }

    val anim: androidx.compose.animation.core.FiniteAnimationSpec<Float> = if (spec.instant) snap() else tween(420, easing = FastOutSlowInEasing)
    val animColour: androidx.compose.animation.core.FiniteAnimationSpec<Color> = if (spec.instant) snap() else tween(420, easing = FastOutSlowInEasing)
    val bg by animateColorAsState(spec.bg, tween(450), label = "bg")
    val a0 by animateFloatAsState(spec.a0, anim, label = "a0")
    val sweep by animateFloatAsState(spec.sweep, anim, label = "sweep")
    val trackAlpha by animateFloatAsState(spec.trackAlpha, anim, label = "trackAlpha")
    val trackColor by animateColorAsState(spec.trackColor, animColour, label = "trackColor")
    val fillF by animateFloatAsState(mem.fill.f, anim, label = "fillF")
    val fillAlpha by animateFloatAsState(if (spec.fill != null) mem.fill.alpha else 0f, anim, label = "fillAlpha")
    val fillColor by animateColorAsState(mem.fill.color, animColour, label = "fillColor")
    val dotF by animateFloatAsState(mem.dot.f, anim, label = "dotF")
    val dotAlpha by animateFloatAsState(if (spec.dot != null) 1f else 0f, anim, label = "dotAlpha")
    val dotColor by animateColorAsState(mem.dot.color, animColour, label = "dotColor")
    val segColor by animateColorAsState(mem.segs.color, animColour, label = "segColor")
    val s0 by animateFloatAsState(if (spec.segs != null) mem.segs.alphas[0] else 0f, anim, label = "s0")
    val s1 by animateFloatAsState(if (spec.segs != null) mem.segs.alphas[1] else 0f, anim, label = "s1")
    val s2 by animateFloatAsState(if (spec.segs != null) mem.segs.alphas[2] else 0f, anim, label = "s2")
    val s3 by animateFloatAsState(if (spec.segs != null) mem.segs.alphas[3] else 0f, anim, label = "s3")
    val s4 by animateFloatAsState(if (spec.segs != null) mem.segs.alphas[4] else 0f, anim, label = "s4")
    // The segments share the sweep out evenly, and how many there are changes between faces, so the edges between them glide too.
    val segCount by animateFloatAsState((spec.segs?.alphas?.size ?: SEG_COUNT).toFloat().coerceIn(1f, SEG_COUNT.toFloat()), anim, label = "segCount")
    val gradAlpha by animateFloatAsState(if (spec.gradient != null) 1f else 0f, anim, label = "gradAlpha")
    val gradF by animateFloatAsState(mem.gradient, anim, label = "gradF")

    Box(modifier.fillMaxSize().background(bg)) {
        Canvas(Modifier.fillMaxSize()) {
            val half = size.minDimension / 2f
            val r = half * (128f / 140f)
            val stroke = half * (10f / 140f)
            val centre = Offset(size.width / 2f, size.height / 2f)
            val box = Size(r * 2, r * 2)
            val tl = Offset(centre.x - r, centre.y - r)
            val sw = sweep.coerceAtMost(359.9f)

            fun arc(color: Color, from: Float, length: Float, alpha: Float, cap: StrokeCap = StrokeCap.Round) {
                if (alpha <= 0.01f || length <= 0.2f) return
                drawArc(color.copy(alpha = alpha.coerceIn(0f, 1f)), from, length, false, tl, box, style = Stroke(stroke, cap = cap))
            }

            arc(trackColor, a0, sw, trackAlpha)
            if (gradAlpha > 0.01f) {
                val n = 96
                for (i in 0 until n) {
                    val f0 = i / n.toFloat()
                    arc(gradientColour((f0 + 0.5f / n)), a0 + sweep * f0 - 0.3f, sweep / n + 0.6f, (if (f0 < gradF) 1f else 0.3f) * gradAlpha, StrokeCap.Butt)
                }
            }
            val alphas = floatArrayOf(s0, s1, s2, s3, s4)
            if (alphas.any { it > 0.01f }) {
                val gap = 3f
                for (i in 0 until SEG_COUNT) {
                    // Segment i runs from i/segCount to (i+1)/segCount of the sweep (clamped to the end); the unused ones collapse to nothing.
                    val e0 = (i / segCount).coerceAtMost(1f)
                    val e1 = ((i + 1) / segCount).coerceAtMost(1f)
                    val from = a0 + e0 * sweep + (if (i > 0) gap / 2 else 0f)
                    val to = a0 + e1 * sweep - (if (e1 < 0.999f) gap / 2 else 0f)
                    if (to - from > 3f) arc(segColor, from, to - from, alphas[i])
                }
            }
            if (fillAlpha > 0.01f && fillF > 0f) arc(fillColor, a0, (sweep * fillF).coerceIn(0.5f, 359.9f), fillAlpha)
            if (dotAlpha > 0.01f) {
                val ang = Math.toRadians((a0 + sweep * dotF).toDouble())
                val c = Offset(centre.x + r * Math.cos(ang).toFloat(), centre.y + r * Math.sin(ang).toFloat())
                val dr = half * (10f / 140f)
                drawCircle(Color.White.copy(alpha = dotAlpha), dr, c)
                drawCircle(dotColor.copy(alpha = dotAlpha), dr, c, style = Stroke(half * 0.021f))
            }
        }
    }
}

/**
 * Lets a finger turn the ring: a touch on the ring (not in the strip at the left edge, which is the swipe back) and what follows it
 * round gives [RingSpec.drag] a position from 0 to 1. Touches anywhere else are left alone, so the pages still scroll.
 */
@Composable
fun Modifier.ringDrag(spec: RingSpec): Modifier {
    val drag = rememberUpdatedState(spec.drag)
    val a0 = rememberUpdatedState(spec.a0)
    val sweep = rememberUpdatedState(spec.sweep)
    return this.pointerInput(spec.drag != null) {
        if (!(drag.value != null)) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Main)
            val half = minOf(size.width, size.height) / 2f
            val centre = Offset(size.width / 2f, size.height / 2f)
            val d = hypot(down.position.x - centre.x, down.position.y - centre.y)
            val onRing = d > half * 0.74f
            val leftEdge = down.position.x < 30.dp.toPx()
            if (!onRing || leftEdge) return@awaitEachGesture
            fun apply(p: Offset) {
                val sw = sweep.value
                var ang = Math.toDegrees(atan2((p.y - centre.y).toDouble(), (p.x - centre.x).toDouble())).toFloat()
                if (ang < 0f) ang += 360f
                var rel = (ang - a0.value + 720f) % 360f
                if (rel > sw) rel = if (rel > sw + (360f - sw) / 2f) 0f else sw
                drag.value?.onValue?.invoke(rel / sw)
            }
            apply(down.position)
            down.consume()
            do {
                val event = awaitPointerEvent(PointerEventPass.Main)
                val change = event.changes.firstOrNull() ?: break
                if (change.pressed) {
                    apply(change.position)
                    change.consume()
                }
            } while (event.changes.any { it.pressed })
        }
    }
}
