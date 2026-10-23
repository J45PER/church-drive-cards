package com.churchdrive.app.wear

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.wear.compose.material.Text
import com.churchdrive.app.ui.AssistantGlyph
import com.churchdrive.app.ui.AssistantGlyphPose

/** How much of the circle the arc covers, from its faded tail to its bright head. */
private const val ARC_SWEEP = 120f

/**
 * Shown over whatever the watch remembered while the house is being read (or the line comes back): the page dims a little, a white arc
 * circles the edge of the glass, and a small pill with his mark trading size says what is going on. It takes no touches,
 * so the remembered page can be used meanwhile, and it fades away when the live answer is in.
 */
@Composable
fun BoxScope.LoadingRing(visible: Boolean, label: String = "Updating") {
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(350), label = "loading")
    if (alpha <= 0.01f) return
    val ui = LocalUi.current
    val spin by rememberInfiniteTransition(label = "spin").animateFloat(0f, 360f, infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart), label = "degrees")
    Box(Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha }) {
        Box(Modifier.fillMaxSize().background(Color(0x4D05060C)))
        Canvas(Modifier.fillMaxSize()) {
            val half = size.minDimension / 2f
            val r = half * (128f / 140f)
            val stroke = Stroke(half * (10f / 140f), cap = StrokeCap.Round)
            val centre = Offset(size.width / 2f, size.height / 2f)
            val topLeft = Offset(centre.x - r, centre.y - r)
            val box = Size(r * 2, r * 2)
            // White, brightest at the head and fading smoothly to nothing along the tail, so it reads as turning.
            val fade = Brush.sweepGradient(0f to Color.White.copy(alpha = 0f), (ARC_SWEEP / 360f) to Color.White, center = centre)
            rotate(spin, centre) {
                drawArc(fade, 0f, ARC_SWEEP, false, topLeft, box, style = stroke)
            }
        }
        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = ui.d(34f)).clip(RoundedCornerShape(50)).background(Color(0xD1090A14))
                .padding(start = ui.d(8f), end = ui.d(12f), top = ui.d(4f), bottom = ui.d(4f)),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ui.d(6f)),
        ) {
            AssistantGlyph(AssistantGlyphPose.Thinking, Color.White, ui.d(18f))
            Text(label, color = Color(0xFFEEF0FF), fontSize = ui.sp(11f), fontWeight = FontWeight.SemiBold)
        }
    }
}
