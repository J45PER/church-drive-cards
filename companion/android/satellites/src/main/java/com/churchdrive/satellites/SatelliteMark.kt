package com.churchdrive.satellites

import androidx.compose.animation.core.Animatable
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
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The Satellites app's mark (satellite/design/app-icons.html): Clive's mark with its top-right square turned into a circle, and three
 * ripples going out of the circle, each as thick as the gap between them. It is the launcher icon too (`ic_launcher_foreground.xml`, from the same
 * paths). Drawn in a 200 unit box.
 */
private const val MAIN = "M50.7,103.3 H66.7 A30,30 0 0 1 96.7,133.3 V149.3 A30,30 0 0 1 66.7,179.3 H50.7 A30,30 0 0 1 20.7,149.3 V133.3 A30,30 0 0 1 50.7,103.3Z M45.3,118.7 a36,36 0 1,0 72,0 a36,36 0 1,0 -72,0Z"
private val RIPPLES = listOf(
    "M89.37,61.26 A58,58 0 0,1 138.74,110.63 A5.5,5.5 0 0 1 127.84,112.16 A47,47 0 0,0 87.84,72.16 A5.5,5.5 0 0 1 89.37,61.26Z",
    "M89.98,39.17 A80,80 0 0,1 160.83,110.02 A5.5,5.5 0 0 1 149.89,111.21 A69,69 0 0,0 88.79,50.11 A5.5,5.5 0 0 1 89.98,39.17Z",
    "M90.66,17.13 A102,102 0 0,1 182.87,109.34 A5.5,5.5 0 0 1 171.92,110.35 A91,91 0 0,0 89.65,28.08 A5.5,5.5 0 0 1 90.66,17.13Z"
)

private fun parse(d: String): Path = PathParser().parsePathString(d).toPath().apply { fillType = PathFillType.EvenOdd }

private val Base = Color(0xFF6A5BC2)
private val Purple = Color(0xFFAB47BC)
private val Green = Color(0xFF4FD08A)
private val Indigo = Color(0xFF5C6BC0)

/**
 * The mark at [height]. In the light theme it carries Clive's glow (the three blobs on the lilac base); in the dark theme it is all white.
 * With [animate] the three ripples appear one after another when it is first shown, and the rest stays still.
 */
@Composable
fun SatelliteMark(dark: Boolean, modifier: Modifier = Modifier, height: Dp = 104.dp, animate: Boolean = true) {
    val main = remember { parse(MAIN) }
    val ripples = remember { RIPPLES.map(::parse) }
    val alphas = remember { List(3) { Animatable(if (animate) 0f else 1f) } }
    LaunchedEffect(animate) {
        if (animate) {
            alphas.forEachIndexed { i, a ->
                launch {
                    delay(150L + i * 230L)
                    a.animateTo(1f, tween(260))
                }
            }
        }
    }
    Canvas(modifier.height(height).aspectRatio(1f)) {
        // The mark fills x 21 to 183 and y 17 to 179 of its box: its middle (102, 98) goes to the middle of the canvas.
        val s = size.width / 200f
        scale(s, pivot = Offset.Zero) {
            val shift = Offset(100f - 102f, 100f - 98f)
            fun draw(path: Path, alpha: Float) {
                if (alpha <= 0f) return
                translate(shift.x, shift.y) {
                    if (dark) {
                        drawPath(path, Color.White, alpha = alpha)
                    } else {
                        drawPath(path, Base, alpha = alpha)
                        drawPath(path, Brush.radialGradient(listOf(Purple, Purple.copy(alpha = 0f)), Offset(150f, 70f), 88f), alpha = alpha)
                        drawPath(path, Brush.radialGradient(listOf(Green, Green.copy(alpha = 0f)), Offset(70f, 170f), 110f), alpha = alpha)
                        drawPath(path, Brush.radialGradient(listOf(Indigo, Indigo.copy(alpha = 0f)), Offset(40f, 40f), 130f), alpha = alpha)
                        drawPath(path, Brush.radialGradient(listOf(Color.White.copy(alpha = 0.35f), Color.White.copy(alpha = 0f)), Offset(100f, 100f), 60f), alpha = alpha)
                    }
                }
            }
            draw(main, 1f)
            ripples.forEachIndexed { i, p -> draw(p, alphas[i].value) }
        }
    }
}
