package com.churchdrive.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Which app's logo the splash turns the assistant into. */
enum class LogoKind { Church, Todo, Satellites }

private class Glow(val purple: Triple<Float, Float, Float>, val green: Triple<Float, Float, Float>, val indigo: Triple<Float, Float, Float>, val white: Triple<Float, Float, Float>)

// Each logo's glow, as in its own drawable (200 unit frame). the assistant's start uses the Satellites one, which sits the same way on the same two squares.
private val SatGlow = Glow(Triple(150f, 70f, 88f), Triple(70f, 170f, 110f), Triple(40f, 40f, 130f), Triple(100f, 100f, 60f))
private val HouseGlow = Glow(Triple(144.48f, 81.4f, 79.2f), Triple(62.4f, 168.2f, 100.8f), Triple(26.4f, 60.32f, 115.2f), Triple(91.2f, 93.8f, 40.32f))
private val TodoGlow = Glow(Triple(145.2f, 83.5f, 71.5f), Triple(82.5f, 174.5f, 91f), Triple(55f, 61.4f, 104f), Triple(104.5f, 96.5f, 36.4f))

private val Base = Color(0xFF6A5BC2)
private val Purple = Color(0xFFAB47BC)
private val Green = Color(0xFF4FD08A)
private val Indigo = Color(0xFF5C6BC0)

private const val SAT_CIRCLE_X = 81.3f
private const val SAT_CIRCLE_Y = 118.7f
private val Ripples = listOf(
    "M89.37,61.26 A58,58 0 0,1 138.74,110.63 A5.5,5.5 0 0 1 127.84,112.16 A47,47 0 0,0 87.84,72.16 A5.5,5.5 0 0 1 89.37,61.26Z",
    "M89.98,39.17 A80,80 0 0,1 160.83,110.02 A5.5,5.5 0 0 1 149.89,111.21 A69,69 0 0,0 88.79,50.11 A5.5,5.5 0 0 1 89.98,39.17Z",
    "M90.66,17.13 A102,102 0 0,1 182.87,109.34 A5.5,5.5 0 0 1 171.92,110.35 A91,91 0 0,0 89.65,28.08 A5.5,5.5 0 0 1 90.66,17.13Z"
)
private val Ticks = listOf("M75.52,96 L80.56,101.04 L89.2,90.24", "M96,96 H118", "M75.52,122 L80.56,127.04 L89.2,116.24", "M96,122 H118")

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
private fun lerp(a: Triple<Float, Float, Float>, b: Triple<Float, Float, Float>, t: Float) =
    Triple(lerp(a.first, b.first, t), lerp(a.second, b.second, t), lerp(a.third, b.third, t))
private fun pathOf(d: String) = PathParser().parsePathString(d).toPath().apply { fillType = PathFillType.EvenOdd }
private val InOut = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)

/** Adds the 160 point shape that is [t] of the way from [a] to [b] to [p]. */
private fun addRing(p: Path, a: FloatArray, b: FloatArray, t: Float) {
    for (i in 0 until a.size / 2) {
        val x = lerp(a[2 * i], b[2 * i], t)
        val y = lerp(a[2 * i + 1], b[2 * i + 1], t)
        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    p.close()
}

private fun addScaledRing(p: Path, a: FloatArray, s: Float) {
    var cx = 0f
    var cy = 0f
    val n = a.size / 2
    for (i in 0 until n) { cx += a[2 * i]; cy += a[2 * i + 1] }
    cx /= n; cy /= n
    for (i in 0 until n) {
        val x = cx + (a[2 * i] - cx) * s
        val y = cy + (a[2 * i + 1] - cy) * s
        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    p.close()
}

/**
 * The splash mark that starts as the assistant's idle logo and changes shape into an app's own logo (satellite/design/splash-ideas.html, the "Together"
 * idea): the assistant's two squares become the logo's two shapes; then the Satellites ripples go out, or the To Do clip grows and its ticks are
 * drawn. It plays once and stays on the logo. With [animate] false it is just the logo. In the light theme it carries the logo's glow; in the
 * dark theme it is all white. The canvas is square, and sized so the logo ends up [height] tall at the start of its box.
 */
@Composable
fun LogoMorph(kind: LogoKind, dark: Boolean, modifier: Modifier = Modifier, height: Dp = defaultHeight(kind), animate: Boolean = true) {
    val morph = remember { Animatable(if (animate) 0f else 1f) }
    val extra = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(animate) {
        if (animate) {
            delay(350)
            morph.animateTo(1f, tween(1100, easing = InOut))
            extra.animateTo(1f, tween(1250, easing = LinearEasing))
        }
    }
    val ripples = remember { Ripples.map(::pathOf) }
    val tickPaths = remember { Ticks.map { PathParser().parsePathString(it).toPath() } }
    val tickMeasures = remember { tickPaths.map { PathMeasure().apply { setPath(it, false) } } }
    val clipCentre = remember { Offset(90f, 57f) }
    Canvas(modifier.height(height).aspectRatio(1f)) {
        val t = morph.value
        val x = extra.value * 1.25f // seconds since the shapes settled
        val end = when (kind) { LogoKind.Church -> floatArrayOf(8f, 24f, 152f); LogoKind.Todo -> floatArrayOf(14f, 18f, 170f); LogoKind.Satellites -> floatArrayOf(21f, 17f, 162f) }
        val camT = t.coerceIn(0f, 1f)
        val bx = lerp(42f, end[0], camT)
        val by = lerp(42f, end[1], camT)
        val bs = lerp(116f, end[2], camT)
        val to = when (kind) { LogoKind.Church -> LogoRings.houseA to LogoRings.houseB; LogoKind.Todo -> LogoRings.todoA to LogoRings.todoB; LogoKind.Satellites -> LogoRings.satA to LogoRings.satB }
        val glowEnd = when (kind) { LogoKind.Church -> HouseGlow; LogoKind.Todo -> TodoGlow; LogoKind.Satellites -> SatGlow }
        fun glow(g: (Glow) -> Triple<Float, Float, Float>) = lerp(g(SatGlow), g(glowEnd), camT)
        val purple = glow { it.purple }
        val green = glow { it.green }
        val indigo = glow { it.indigo }
        val white = glow { it.white }

        fun DrawScope.paint(path: Path, alpha: Float, style: androidx.compose.ui.graphics.drawscope.DrawStyle = androidx.compose.ui.graphics.drawscope.Fill) {
            if (alpha <= 0f) return
            if (dark) {
                drawPath(path, Color.White, alpha = alpha, style = style)
            } else {
                drawPath(path, Base, alpha = alpha, style = style)
                drawPath(path, Brush.radialGradient(listOf(Purple, Purple.copy(alpha = 0f)), Offset(purple.first, purple.second), purple.third), alpha = alpha, style = style)
                drawPath(path, Brush.radialGradient(listOf(Green, Green.copy(alpha = 0f)), Offset(green.first, green.second), green.third), alpha = alpha, style = style)
                drawPath(path, Brush.radialGradient(listOf(Indigo, Indigo.copy(alpha = 0f)), Offset(indigo.first, indigo.second), indigo.third), alpha = alpha, style = style)
                drawPath(path, Brush.radialGradient(listOf(Color.White.copy(alpha = 0.35f), Color.White.copy(alpha = 0f)), Offset(white.first, white.second), white.third), alpha = alpha, style = style)
            }
        }

        scale(size.width / bs, pivot = Offset.Zero) {
            translate(-bx, -by) {
                val main = Path().apply { fillType = PathFillType.EvenOdd }
                addRing(main, LogoRings.assistantA, to.first, t)
                addRing(main, LogoRings.assistantB, to.second, t)
                if (kind == LogoKind.Todo) {
                    val g = easeOut((x - 0.05f) / 0.5f)
                    if (g > 0.001f) addScaledRing(main, LogoRings.todoClip, g)
                }
                paint(main, 1f)
                when (kind) {
                    LogoKind.Satellites -> ripples.forEachIndexed { i, p ->
                        val a = easeOut((x - 0.12f - i * 0.28f) / 0.35f)
                        scale(lerp(0.82f, 1f, a), pivot = Offset(SAT_CIRCLE_X, SAT_CIRCLE_Y)) { paint(p, (a * 1.6f).coerceIn(0f, 1f)) }
                    }
                    LogoKind.Todo -> {
                        val stroke = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        tickMeasures.forEachIndexed { i, m ->
                            val a = easeOut((x - 0.5f - i * 0.17f) / 0.4f)
                            if (a > 0f) {
                                val part = Path()
                                m.getSegment(0f, m.length * a, part, true)
                                paint(part, 1f, stroke)
                            }
                        }
                    }
                    LogoKind.Church -> Unit
                }
            }
        }
    }
}

private fun easeOut(t: Float): Float { val c = t.coerceIn(0f, 1f); val k = 1f - c; return 1f - k * k * k }

/** The canvas height that puts each logo at about the size the apps' still marks had on the splash. */
fun defaultHeight(kind: LogoKind): Dp = when (kind) { LogoKind.Church -> 118.dp; LogoKind.Todo -> 136.dp; LogoKind.Satellites -> 84.dp }
