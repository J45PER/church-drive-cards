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
enum class AssistantGlyphPose {
    /** Shut: the two squircles drift gently together and apart along their diagonal. */
    Idle,
    /** Open, waiting for the microphone to be pressed: a microphone (capsule over a flat bar) whose capsule slowly breathes. */
    Ready,
    /** Listening: a stop button, a rounded square that pulses gently and grows with the voice. */
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

private fun poseOf(p: AssistantGlyphPose): Pair<Sq, Sq> = when (p) {
    AssistantGlyphPose.Idle, AssistantGlyphPose.Thinking -> REST_A to REST_B
    // The microphone: a capsule 72 tall (approved on the icon board 2026-11-01) over a flat bar.
    AssistantGlyphPose.Ready -> Sq(80f, 40f, 40f, 72f, 20f) to Sq(66f, 130f, 68f, 16f, 8f)
    // The stop button: one rounded square (the second shape shrinks away to nothing).
    AssistantGlyphPose.Listening -> Sq(70f, 70f, 60f, 60f, 18f) to Sq(100f, 100f, 0f, 0f, 0f)
    AssistantGlyphPose.Speaking -> Sq(64f, 52f, 30f, 96f, 15f) to Sq(106f, 52f, 30f, 96f, 15f)
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
 * they overlap (even-odd fill), that morph between the poses of [AssistantGlyphPose]. [level] (0 to 1) is how loud the voice is.
 */
@Composable
fun AssistantGlyph(pose: AssistantGlyphPose, color: Color, size: androidx.compose.ui.unit.Dp = 30.dp, level: Float = 0f) {
    val (ta, tb) = poseOf(pose)
    val a = animSq(ta)
    val b = animSq(tb)
    val spec = androidx.compose.animation.core.tween<Float>(500)
    fun w(p: AssistantGlyphPose) = if (pose == p) 1f else 0f
    val wIdle by androidx.compose.animation.core.animateFloatAsState(w(AssistantGlyphPose.Idle), spec, label = "wi")
    val wReady by androidx.compose.animation.core.animateFloatAsState(w(AssistantGlyphPose.Ready), spec, label = "wr")
    val wListen by androidx.compose.animation.core.animateFloatAsState(w(AssistantGlyphPose.Listening), spec, label = "ws")
    val wThink by androidx.compose.animation.core.animateFloatAsState(w(AssistantGlyphPose.Thinking), spec, label = "wt")
    val wSpeak by androidx.compose.animation.core.animateFloatAsState(w(AssistantGlyphPose.Speaking), spec, label = "wk")
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
        // Listening: the stop square breathes, and swells a little more with the voice.
        val stop = 1f + wListen * (0.07f * (1f - wave(listenP)) + 0.16f * smooth)
        fun shape(s: Sq, sx: Float, sy: Float) = androidx.compose.ui.geometry.RoundRect(
            100f + s.dx * together + (s.x + s.w / 2f - 100f) - s.w * sx / 2f,
            100f + s.dy * together + (s.y + s.h / 2f - 100f) - s.h * sy / 2f,
            100f + s.dx * together + (s.x + s.w / 2f - 100f) + s.w * sx / 2f,
            100f + s.dy * together + (s.y + s.h / 2f - 100f) + s.h * sy / 2f,
            androidx.compose.ui.geometry.CornerRadius(s.r * minOf(sx, sy)),
        )
        val path = androidx.compose.ui.graphics.Path().apply {
            fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
            addRoundRect(shape(a, sizeA * stop, sizeA * barA * breathe * stop))
            if (b.w > 0.5f) addRoundRect(shape(b, sizeB, sizeB * barB))
        }
        withTransform({
            translate(center.x, center.y)
            scale(unit, unit, pivot = Offset.Zero)
            translate(-100f, -100f)
        }) { drawPath(path, color) }
    }
}

/** The pose that goes with what the assistant is doing: [open] is whether he is on screen, [state] what he is doing. */
fun assistantGlyphPose(open: Boolean, state: AssistState): AssistantGlyphPose = when {
    !open -> AssistantGlyphPose.Idle
    state == AssistState.Listening -> AssistantGlyphPose.Listening
    state == AssistState.Thinking -> AssistantGlyphPose.Thinking
    state == AssistState.Speaking -> AssistantGlyphPose.Speaking
    else -> AssistantGlyphPose.Ready
}
