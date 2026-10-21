package com.churchdrive.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.animation.core.animateFloat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp

/**
 * The assistant's logo, drawn in one colour: two overlapping rounded squares running from bottom left to top right, with the part
 * where they overlap cut out (even-odd fill), so whatever is behind shows through. Same shape as the launcher icon's Clive mark.
 */
@Composable
fun AssistantLogo(color: Color, size: androidx.compose.ui.unit.Dp = 30.dp) {
    val mark = androidx.compose.runtime.remember {
        // Drawn around (0, 0) in a box about 99 units across: each square is 76 wide with 30 rounding, shifted 11.3 along the diagonal.
        val half = 38f
        val shift = 11.3f
        val corner = androidx.compose.ui.geometry.CornerRadius(30f, 30f)
        androidx.compose.ui.graphics.Path().apply {
            fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
            addRoundRect(androidx.compose.ui.geometry.RoundRect(-half - shift, -half + shift, half - shift, half + shift, corner))
            addRoundRect(androidx.compose.ui.geometry.RoundRect(-half + shift, -half - shift, half + shift, half - shift, corner))
        }
    }
    Canvas(Modifier.size(size)) {
        val unit = this.size.minDimension / 104f
        withTransform({
            translate(center.x, center.y)
            scale(unit, unit, pivot = Offset.Zero)
        }) { drawPath(mark, color) }
    }
}

/** What Clive's white mark shows: the pose says what he is doing, and each one has its own gentle movement. */
enum class CliveGlyphPose {
    /** Shut: the two squircles drift gently together and apart along their diagonal. */
    Idle,
    /** Open, waiting for the microphone to be pressed: a microphone (capsule over a flat bar) whose capsule slowly breathes. */
    Ready,
    /** Listening: the same microphone, its capsule and base wobbling a little, like a level meter. */
    Listening,
    /** Thinking: the two squircles trade size. */
    Thinking,
    /** Speaking: two equaliser bars. */
    Speaking,
}

/** One rounded rectangle of the mark, in the 200-unit space the icons are drawn in (centre 100, 100). */
private data class Sq(val x: Float, val y: Float, val w: Float, val h: Float, val r: Float, val dx: Float = 0f, val dy: Float = 0f)

private const val SHIFT = 11.3f
private val REST_A = Sq(62f, 62f, 76f, 76f, 30f, -SHIFT, SHIFT)
private val REST_B = Sq(62f, 62f, 76f, 76f, 30f, SHIFT, -SHIFT)

private fun poseOf(p: CliveGlyphPose): Pair<Sq, Sq> = when (p) {
    CliveGlyphPose.Idle, CliveGlyphPose.Thinking -> REST_A to REST_B
    CliveGlyphPose.Ready, CliveGlyphPose.Listening -> Sq(80f, 40f, 40f, 64f, 20f) to Sq(66f, 130f, 68f, 16f, 8f)
    CliveGlyphPose.Speaking -> Sq(64f, 52f, 30f, 96f, 15f) to Sq(106f, 52f, 30f, 96f, 15f)
}

@Composable
private fun androidx.compose.animation.core.InfiniteTransition.phase(ms: Int): androidx.compose.runtime.State<Float> =
    animateFloat(0f, 1f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(ms, easing = androidx.compose.animation.core.LinearEasing)), label = "ph$ms")

@Composable
private fun animSq(target: Sq): Sq {
    val spec = androidx.compose.animation.core.tween<Float>(600, easing = androidx.compose.animation.core.FastOutSlowInEasing)
    val x by androidx.compose.animation.core.animateFloatAsState(target.x, spec, label = "x")
    val y by androidx.compose.animation.core.animateFloatAsState(target.y, spec, label = "y")
    val w by androidx.compose.animation.core.animateFloatAsState(target.w, spec, label = "w")
    val h by androidx.compose.animation.core.animateFloatAsState(target.h, spec, label = "h")
    val r by androidx.compose.animation.core.animateFloatAsState(target.r, spec, label = "r")
    val dx by androidx.compose.animation.core.animateFloatAsState(target.dx, spec, label = "dx")
    val dy by androidx.compose.animation.core.animateFloatAsState(target.dy, spec, label = "dy")
    return Sq(x, y, w, h, r, dx, dy)
}

/**
 * Clive's white mark with its animations: only the mark moves (the round button behind it is still). Two squircles, cut out where
 * they overlap (even-odd fill), that morph between the poses of [CliveGlyphPose]. [level] (0 to 1) is how loud the voice is.
 */
@Composable
fun CliveGlyph(pose: CliveGlyphPose, color: Color, size: androidx.compose.ui.unit.Dp = 30.dp, level: Float = 0f) {
    val (ta, tb) = poseOf(pose)
    val a = animSq(ta)
    val b = animSq(tb)
    val spec = androidx.compose.animation.core.tween<Float>(500)
    fun w(p: CliveGlyphPose) = if (pose == p) 1f else 0f
    val wIdle by androidx.compose.animation.core.animateFloatAsState(w(CliveGlyphPose.Idle), spec, label = "wi")
    val wReady by androidx.compose.animation.core.animateFloatAsState(w(CliveGlyphPose.Ready), spec, label = "wr")
    val wListen by androidx.compose.animation.core.animateFloatAsState(w(CliveGlyphPose.Listening), spec, label = "ws")
    val wThink by androidx.compose.animation.core.animateFloatAsState(w(CliveGlyphPose.Thinking), spec, label = "wt")
    val wSpeak by androidx.compose.animation.core.animateFloatAsState(w(CliveGlyphPose.Speaking), spec, label = "wk")
    val smooth by androidx.compose.animation.core.animateFloatAsState(level.coerceIn(0f, 1f), androidx.compose.animation.core.tween(250), label = "lv")
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "glyph")
    val idleP by t.phase(4000)
    val readyP by t.phase(3000)
    val listenP by t.phase(1400)
    val thinkP by t.phase(1200)
    val speakP by t.phase(700)
    Canvas(Modifier.size(size)) {
        val unit = this.size.minDimension / 104f
        val tau = (2 * Math.PI).toFloat()
        fun wave(p: Float) = (Math.cos((p * tau).toDouble()).toFloat() + 1f) / 2f // 1 at the start, 0 half way
        // Idle: the squircles slide together to under half way and apart again.
        val together = 1f - wIdle * 0.55f * (1f - wave(idleP))
        // Thinking trades size; speaking trades height; ready breathes; listening wobbles like a level meter (a little more with the voice).
        val trade = Math.cos((thinkP * tau).toDouble()).toFloat()
        val sizeA = 1f + wThink * (0.025f + 0.175f * trade)
        val sizeB = 1f + wThink * (0.025f - 0.175f * trade)
        val bars = Math.cos((speakP * tau).toDouble()).toFloat()
        val barA = 1f + wSpeak * (-0.25f + 0.25f * bars)
        val barB = 1f + wSpeak * (-0.25f - 0.25f * bars)
        val breathe = 1f + wReady * 0.05f * (1f - wave(readyP))
        fun keys(p: Float, vararg k: Pair<Float, Float>): Float {
            for (i in 1 until k.size) if (p <= k[i].first) { val (p0, v0) = k[i - 1]; val (p1, v1) = k[i]; return v0 + (v1 - v0) * (p - p0) / (p1 - p0) }
            return k.last().second
        }
        val amp = 1f + smooth
        val capsule = 1f + wListen * (keys(listenP, 0f to 0.96f, 0.3f to 1.07f, 0.55f to 0.98f, 0.8f to 1.05f, 1f to 0.96f) - 1f) * amp
        val base = 1f + wListen * (keys(listenP, 0f to 0.92f, 0.35f to 1.1f, 0.6f to 0.96f, 0.85f to 1.06f, 1f to 0.92f) - 1f) * amp
        fun shape(s: Sq, sx: Float, sy: Float) = androidx.compose.ui.geometry.RoundRect(
            100f + s.dx * together + (s.x + s.w / 2f - 100f) - s.w * sx / 2f,
            100f + s.dy * together + (s.y + s.h / 2f - 100f) - s.h * sy / 2f,
            100f + s.dx * together + (s.x + s.w / 2f - 100f) + s.w * sx / 2f,
            100f + s.dy * together + (s.y + s.h / 2f - 100f) + s.h * sy / 2f,
            androidx.compose.ui.geometry.CornerRadius(s.r * minOf(sx, sy)),
        )
        val path = androidx.compose.ui.graphics.Path().apply {
            fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
            addRoundRect(shape(a, sizeA, sizeA * barA * breathe * capsule))
            if (b.w > 0.5f) addRoundRect(shape(b, sizeB * base, sizeB * barB))
        }
        withTransform({
            translate(center.x, center.y)
            scale(unit, unit, pivot = Offset.Zero)
            translate(-100f, -100f)
        }) { drawPath(path, color) }
    }
}

/** The pose that goes with what the assistant is doing: [open] is whether he is on screen, [state] what he is doing. */
fun cliveGlyphPose(open: Boolean, state: AssistState): CliveGlyphPose = when {
    !open -> CliveGlyphPose.Idle
    state == AssistState.Listening -> CliveGlyphPose.Listening
    state == AssistState.Thinking -> CliveGlyphPose.Thinking
    state == AssistState.Speaking -> CliveGlyphPose.Speaking
    else -> CliveGlyphPose.Ready
}
