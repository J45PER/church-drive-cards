package com.churchdrive.app.ui

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

/** One button on the grid: icon and label (side by side, or stacked when [stacked]), filled in the strong colour when selected. Corners 18 dp, as every agreed button. */
@Composable
fun GridButton(item: TileItem, tone: ToneColors, content: Color, enabled: Boolean, stacked: Boolean, modifier: Modifier) {
    val ink = (if (item.selected) tone.onAccent else content).copy(alpha = if (enabled) 1f else 0.4f)
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier.clip(shape).background(if (item.selected) tone.accent else content.copy(alpha = 0.10f), shape)
            .clickable(enabled = enabled, onClickLabel = item.label, onClick = item.onClick).semantics { contentDescription = item.label },
        contentAlignment = Alignment.Center,
    ) {
        if (stacked) {
            androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
                HaIcon(item.icon, CdVectors.Info, ink, 24.dp)
                if (item.showLabel) CentredText(item.label, ink, 12.sp, Modifier.padding(top = 4.dp), lineHeight = 14.sp)
            }
        } else {
            androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                HaIcon(item.icon, CdVectors.Info, ink, Ui.TileIcon)
                if (item.showLabel) CentredText(item.label, ink, Ui.TileText, Modifier.padding(end = 2.dp))
            }
        }
    }
}

/**
 * A row of buttons on the grid at row line [y], [h] cells tall: three are 2 cells wide with half a cell between, four are 1.5 wide with a third
 * between, two are 3.25 wide, one fills the width; any other number shares the width with a quarter cell between.
 */
@Composable
fun GridScope.GridTiles(items: List<TileItem>, y: Float, h: Float, tone: ToneColors, content: Color, enabled: Boolean, stacked: Boolean = h > 1.2f) {
    val n = items.size
    if (n == 0) return
    val (w, gap) = when (n) {
        1 -> 7f to 0f
        2 -> 3.25f to 0.5f
        3 -> 2f to 0.5f
        4 -> 1.5f to (1f / 3f)
        else -> ((7f - 0.25f * (n - 1)) / n) to 0.25f
    }
    items.forEachIndexed { i, it -> GridButton(it, tone, content, enabled, stacked, Modifier.at(0.5f + i * (w + gap), y, w, h)) }
}

/** The level bar's colour as it empties: blue when full, then green, yellow, amber, red. One colour for the whole fill. */
fun levelFade(f: Float): Color {
    val stops = listOf(0f to Color(0xFFE5534B), 0.25f to Color(0xFFE8A33B), 0.5f to Color(0xFFE6C93A), 0.75f to Color(0xFF4CC38A), 1f to Color(0xFF5B8CFF))
    val x = f.coerceIn(0f, 1f)
    for (i in 0 until stops.size - 1) {
        val (f0, c0) = stops[i]; val (f1, c1) = stops[i + 1]
        if (x <= f1) return androidx.compose.ui.graphics.lerp(c0, c1, (x - f0) / (f1 - f0))
    }
    return stops.last().second
}

/** A battery's bar colour: green when full, then yellow, amber and red as it empties. */
fun batteryFade(f: Float): Color {
    val x = f.coerceIn(0f, 1f)
    return if (x >= 0.75f) Color(0xFF4CC38A) else levelFade(x)
}

/** A thin level bar (8 dp), [fraction] of its track filled in [colour]. */
@Composable
fun LevelBar(fraction: Float, colour: Color, ink: Color, modifier: Modifier = Modifier, thickness: Dp = 8.dp) {
    Box(modifier.height(thickness).background(ink.copy(alpha = 0.25f), RoundedCornerShape(thickness / 2))) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(thickness).background(colour, RoundedCornerShape(thickness / 2)))
    }
}
