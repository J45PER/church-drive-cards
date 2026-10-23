package com.churchdrive.app.wear

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints

/**
 * Holds [content] to the box it is given without scrolling: the content is laid out as tall as it likes, and when that is taller than the
 * box it is scaled down (from the top, never below [minScale]) so it all shows. Anything still too tall after that is cut off at the edge
 * rather than spilling over the ring.
 */
@Composable
fun FitBox(modifier: Modifier = Modifier, minScale: Float = 0.62f, content: @Composable () -> Unit) {
    Layout(content = content, modifier = modifier.clipToBounds()) { measurables, constraints ->
        val placeable = measurables.firstOrNull()?.measure(Constraints(maxWidth = constraints.maxWidth, maxHeight = Constraints.Infinity))
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        layout(w, h) {
            if (placeable != null) {
                val scale = if (placeable.height > h && placeable.height > 0) (h.toFloat() / placeable.height).coerceAtLeast(minScale) else 1f
                placeable.placeWithLayer((w - placeable.width) / 2, 0) {
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0.5f, 0f)
                }
            }
        }
    }
}
