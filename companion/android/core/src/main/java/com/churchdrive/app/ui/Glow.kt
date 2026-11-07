package com.churchdrive.app.ui

import androidx.compose.animation.core.animateFloat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/** A glow colour for a card ground: the ground's hue, bright and clear, turned [shift] degrees. A grey ground (off, unavailable) has none. */
fun glowColour(ground: Color, shift: Float): Color? {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(ground.toArgb(), hsv)
    if (hsv[1] < 0.18f) return null
    hsv[0] = (hsv[0] + shift + 360f) % 360f
    hsv[1] = hsv[1].coerceIn(0.55f, 0.9f)
    hsv[2] = 0.9f
    return Color(android.graphics.Color.HSVToColor(hsv))
}

/** The Clive glow behind a card, as on the watch and satellite: two soft blobs of the card's colour turning slowly. Quiet (alpha 0.30) so text stays readable. */
@Composable
fun Modifier.glowBehind(ground: Color): Modifier {
    val a = remember(ground) { glowColour(ground, 0f) }
    val b = remember(ground) { glowColour(ground, 28f) }
    if (a == null || b == null) return this
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "glow")
    val phase by transition.animateFloat(
        // The second blob turns 1.3 times as fast the other way, so the angle runs 3600 degrees (ten turns for the first, thirteen for the second):
        // both end where they began and the loop has no jump. The pace is Clive's, one turn in 16 seconds.
        initialValue = 0f,
        targetValue = 3600f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(160_000, easing = androidx.compose.animation.core.LinearEasing)),
        label = "glowPhase",
    )
    return this.drawBehind {
        val w = size.width
        val r = maxOf(size.width, size.height) * 0.62f
        fun blob(color: Color, deg: Float) {
            val rad = Math.toRadians(deg.toDouble())
            val c = androidx.compose.ui.geometry.Offset(
                size.center.x + (Math.cos(rad) * 0.28 * w).toFloat(),
                size.center.y + (Math.sin(rad) * 0.28 * size.height).toFloat(),
            )
            drawCircle(
                brush = androidx.compose.ui.graphics.Brush.radialGradient(listOf(color.copy(alpha = 0.30f), Color.Transparent), center = c, radius = r),
                radius = r,
                center = c,
            )
        }
        blob(a, phase + 210f)
        blob(b, -phase * 1.3f + 330f)
    }
}
