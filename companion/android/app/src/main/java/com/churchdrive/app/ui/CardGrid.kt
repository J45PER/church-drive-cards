package com.churchdrive.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
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

/**
 * The card grid agreed on the card review page: a card is 8 equal columns wide with square cells, so its height is a number of rows
 * (whole or half). Row 1 is the title line: the title across columns 1 to 7 and the fold arrow in column 8. Everything else is placed
 * in cell units on the grid's lines (see the `at` helpers), so every card lines up the same way.
 */
class GridScope(val cell: Dp) {
    /** A box [w] by [h] cells whose top left corner is at ([x], [y]) cells from the card's top left. */
    fun Modifier.at(x: Float, y: Float, w: Float, h: Float): Modifier = offset(cell * x, cell * y).size(cell * w, cell * h)

    /** A box [w] by [h] cells centred on the point ([cx], [cy]) cells from the card's top left. */
    fun Modifier.atCentre(cx: Float, cy: Float, w: Float, h: Float): Modifier = at(cx - w / 2f, cy - h / 2f, w, h)

    /** A box from [x] cells to the right edge of column 8 minus [rightGap] cells, [h] tall, at [y]. */
    fun Modifier.across(x: Float, y: Float, h: Float, rightGap: Float = 0.5f): Modifier =
        offset(cell * x, cell * y).width(cell * (8f - x - rightGap)).height(cell * h)
}

/** Each word of a title starts with a capital letter. */
fun gridTitle(title: String): String = title.split(" ").joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }

/** Whether a grid card is folded, kept by [key] while the app is open. */
@Composable
fun rememberFold(key: String): androidx.compose.runtime.MutableState<Boolean> = rememberSaveable(key) { mutableStateOf(false) }

/**
 * A card on the grid. [rows] is its height in rows. The title sits on row 1 with the top of its lowercase letters on the middle of the
 * row and its left edge on the half line of column A. [fold] (when the card can fold) draws the arrow in column 8 of row 1.
 */
@Composable
fun GridCard(
    container: Color,
    content: Color,
    rows: Float,
    title: String,
    modifier: Modifier = Modifier,
    deviceEntity: String? = null,
    titleColor: Color = content,
    fold: androidx.compose.runtime.MutableState<Boolean>? = null,
    body: @Composable GridScope.() -> Unit,
) {
    val openDevice = LocalOpenDevice.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    Card(
        modifier = modifier.fillMaxWidth().then(
            if (deviceEntity == null) Modifier
            else Modifier.androidxLongPress {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                openDevice(deviceEntity)
            },
        ),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
    ) {
        Box(modifier = Modifier.glowBehind(container)) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val cell = maxWidth / 8f
                Box(Modifier.fillMaxWidth().height(cell * rows)) {
                    val scope = GridScope(cell)
                    if (title.isNotBlank()) {
                        Text(
                            gridTitle(title), style = MaterialTheme.typography.headlineSmall, color = titleColor, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = cell * 0.5f, end = cell).fillMaxWidth().paddingFromBaseline(top = cell * 0.5f + 12.dp),
                        )
                    }
                    if (fold != null) {
                        val folded = fold.value
                        Box(
                            Modifier.offset(cell * 7f, 0.dp).size(cell).clip(RoundedCornerShape(50))
                                .clickable(onClickLabel = if (folded) "Expand" else "Collapse") { fold.value = !folded }
                                .semantics { contentDescription = if (folded) "Expand" else "Collapse" },
                            contentAlignment = Alignment.Center,
                        ) {
                            androidx.compose.material3.Icon(
                                if (folded) CdVectors.ChevronDown else CdVectors.ChevronUp, contentDescription = null,
                                tint = content.copy(alpha = 0.6f), modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                    scope.body()
                }
            }
        }
    }
}

private fun Modifier.androidxLongPress(onLongPress: () -> Unit): Modifier =
    this.then(Modifier.pointerInput(Unit) { detectTapGestures(onLongPress = { onLongPress() }) })
