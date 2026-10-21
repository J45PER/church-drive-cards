package com.churchdrive.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
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
fun JarvisLogo(color: Color, size: androidx.compose.ui.unit.Dp = 30.dp) {
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
