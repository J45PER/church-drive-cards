package com.churchdrive.app.ui

import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.paddingFromBaseline
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The ring of the agreed header: [sizeCells] across (1.5 on an open card, 1 folded), its arc [fraction] full. Draws the ring and puts [content] (an icon) in the middle. */
@Composable
fun GridRing(sizeCells: Float, cell: Dp, fraction: Float, ring: Color, content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Box(Modifier.size(cell * sizeCells), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.size(cell * sizeCells)) {
            val stroke = 5.dp.toPx() * sizeCells / 1.5f
            val inset = stroke / 2
            val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
            drawArc(ring.copy(alpha = 0.2f), 0f, 360f, false, androidx.compose.ui.geometry.Offset(inset, inset), arcSize, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
            drawArc(ring, -90f, 360f * fraction.coerceIn(0f, 1f), false, androidx.compose.ui.geometry.Offset(inset, inset), arcSize, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round))
        }
        content()
    }
}

/**
 * The header most cards share: a ring with an icon (1.5 cells, from the half line of column A, centred on the line between rows 2 and 3), two lines from the
 * centre of column C, and a figure at the right on the same line.
 */
@Composable
fun GridScope.RingHead(icon: String, fraction: Float, accent: Color, ink: Color, line1: String, line2: String, fig: String? = null, figSub: String? = null) {
    Box(Modifier.atCentre(1.25f, 2f, 1.5f, 1.5f), contentAlignment = Alignment.Center) { GridRing(1.5f, cell, fraction, accent) { HaIcon(icon, CdVectors.Info, accent, 38.dp) } }
    androidx.compose.foundation.layout.Column(Modifier.at(2.5f, 1f, if (fig != null) 3f else 5f, 2f), verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
        Text(line1, style = MaterialTheme.typography.titleMedium, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (line2.isNotBlank()) Text(line2, style = MaterialTheme.typography.bodyMedium, color = ink.copy(alpha = 0.85f), maxLines = 2)
    }
    if (fig != null) {
        androidx.compose.foundation.layout.Column(Modifier.at(5.5f, 1f, 2f, 2f), verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center, horizontalAlignment = Alignment.End) {
            Text(fig, style = MaterialTheme.typography.headlineMedium, color = ink)
            if (figSub != null) Text(figSub, style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.8f))
        }
    }
}

/** The folded band: two lines of text from [left] cells, and small buttons centred on the column lines from [firstLine] to the right edge. Band centred on the middle of row 2. */
@Composable
fun GridScope.FoldBand(left: Float, firstLine: Float, ink: Color, a: String, b: String?, buttons: List<TileItem>, tone: ToneColors, enabled: Boolean) {
    androidx.compose.foundation.layout.Column(Modifier.at(left, 0.9f, firstLine - left - 0.5f, 1.2f), verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
        Text(a, style = MaterialTheme.typography.bodyMedium, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (!b.isNullOrBlank()) Text(b, style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    buttons.forEachIndexed { i, bt -> SmallModeButton(bt.icon, bt.selected, tone, ink, false, Modifier.atCentre(firstLine + i, 1.5f, 0.8f, 0.8f), enabled, bt.label, bt.onClick) }
}
