package com.churchdrive.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.ParentDataModifier
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How the app fits the screen it is on: a phone upright (one column, the bar at the bottom, as always), a phone on its side or a tablet
 * (a rail down the side instead of the bar, cards in as many columns as fit), and a short screen (a slim title bar).
 */
object Adaptive {
    /** From this width (dp) the navigation is a rail down the side: a phone on its side, a tablet in either direction. */
    const val RAIL_FROM_DP = 600

    /** Under this height (dp) the title bar is the slim one: a phone on its side. */
    const val SHORT_BELOW_DP = 480

    /** A column of cards is never narrower than this (about a small phone's width), so the card grid keeps its proportions. */
    const val MIN_COLUMN_DP = 320

    /** ...nor wider than this: past it the card would be stretched, so more columns (or empty sides) instead. */
    const val MAX_COLUMN_DP = 520

    const val MAX_COLUMNS = 4
    const val COLUMN_GAP_DP = 16

    fun rail(widthDp: Int) = widthDp >= RAIL_FROM_DP

    fun short(heightDp: Int) = heightDp < SHORT_BELOW_DP

    /** How many columns of cards fit in [widthDp] of room (at least one, at most [MAX_COLUMNS]). */
    fun columns(widthDp: Float): Int = ((widthDp + COLUMN_GAP_DP) / (MIN_COLUMN_DP + COLUMN_GAP_DP)).toInt().coerceIn(1, MAX_COLUMNS)

    /** The width (dp) of each of [columns] columns in [widthDp] of room: they share it, up to [MAX_COLUMN_DP]; one column takes it all. */
    fun columnWidth(widthDp: Float, columns: Int): Float =
        if (columns <= 1) widthDp else minOf((widthDp - COLUMN_GAP_DP * (columns - 1)) / columns, MAX_COLUMN_DP.toFloat())
}

/** The size of the window, read where it changes (turning the phone, a tablet's split screen). */
class AdaptiveWindow(val widthDp: Int, val heightDp: Int) {
    /** A rail down the side in place of the bottom bar. */
    val rail: Boolean get() = Adaptive.rail(widthDp)

    /** A slim title bar in place of the tall one that folds away. */
    val short: Boolean get() = Adaptive.short(heightDp)
}

@Composable
fun rememberAdaptiveWindow(): AdaptiveWindow {
    val configuration = LocalConfiguration.current
    return AdaptiveWindow(configuration.screenWidthDp, configuration.screenHeightDp)
}

private object FullSpanMarker

private object FullSpanModifier : ParentDataModifier {
    override fun Density.modifyParentData(parentData: Any?): Any = FullSpanMarker
}

/** In [AdaptiveColumns]: this child takes the whole width, below everything above it (a page's header). */
fun Modifier.fullSpan(): Modifier = this.then(FullSpanModifier)

/**
 * Lays its children out in as many columns as the width holds, each child going under the shortest column so far, in the order given.
 * With room for one column (a phone upright) it is a plain column, spaced by [spacing], as the pages were before. A child marked
 * [fullSpan] takes the whole width. Children are measured at a fixed column width, so use it inside a scrolling column, not a lazy one.
 */
@Composable
fun AdaptiveColumns(modifier: Modifier = Modifier, spacing: Dp = 28.dp, content: @Composable () -> Unit) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val avail = constraints.maxWidth
        val gap = spacing.roundToPx()
        val columnGap = Adaptive.COLUMN_GAP_DP.dp.roundToPx()
        val n = Adaptive.columns(avail.toDp().value)
        val columnWidth = if (n == 1) avail else minOf((avail - columnGap * (n - 1)) / n, Adaptive.MAX_COLUMN_DP.dp.roundToPx())
        // Columns that stop short of the full width sit in the middle.
        val left = if (n == 1) 0 else (avail - (columnWidth * n + columnGap * (n - 1))) / 2
        val tops = IntArray(n)
        val placed = ArrayList<Triple<Placeable, Int, Int>>()
        // Measure everything first, so a run of groups can be shared between the columns evenly (in order, each column a run of neighbours) and no
        // column ends up much longer than the others.
        val items = measurables.map { m ->
            if (m.parentData === FullSpanMarker) m to m.measure(Constraints(minWidth = avail, maxWidth = avail, minHeight = 0, maxHeight = constraints.maxHeight))
            else m to m.measure(Constraints(minWidth = columnWidth, maxWidth = columnWidth, minHeight = 0, maxHeight = constraints.maxHeight))
        }
        var k = 0
        while (k < items.size) {
            val (m, p) = items[k]
            if (m.parentData === FullSpanMarker) {
                val y = tops.max()
                placed.add(Triple(p, 0, y))
                tops.fill(if (p.height > 0) y + p.height + gap else y)
                k++
                continue
            }
            // The run of ordinary groups up to the next full-width one.
            var end = k
            while (end < items.size && items[end].first.parentData !== FullSpanMarker) end++
            val run = items.subList(k, end).map { it.second }
            val base = tops.max()
            val hs = run.map { if (it.height > 0) it.height + gap else 0 }
            // The smallest column height that lets the run fit in n columns, found by trying heights.
            fun fits(limit: Int): Boolean {
                var cols = 1; var cur = 0
                for (h in hs) { if (cur + h > limit && cur > 0) { cols++; cur = 0 }; if (h > limit) return false; cur += h }
                return cols <= n
            }
            var lo = hs.maxOrNull() ?: 0; var hi = hs.sum().coerceAtLeast(lo)
            while (lo < hi) { val mid = (lo + hi) / 2; if (fits(mid)) hi = mid else lo = mid + 1 }
            var c = 0; var cur = 0
            run.forEachIndexed { i, pl ->
                if (cur + hs[i] > lo && cur > 0 && c < n - 1) { c++; cur = 0 }
                placed.add(Triple(pl, left + c * (columnWidth + columnGap), base + cur))
                cur += hs[i]
                tops[c] = base + cur
            }
            for (i in 0 until n) if (tops[i] < base) tops[i] = base
            k = end
        }
        val height = (tops.max() - gap).coerceAtLeast(0).coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(avail, height) { placed.forEach { (p, x, y) -> p.place(x, y) } }
    }
}
