package com.churchdrive.app.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The sizes every card shares. A card never writes its own button height, icon size or tile gap: it uses these (the
 * standards test fails if one does), so a fan, a purifier and a thermostat look like parts of one app. The rules are in
 * `companion/android/UI-STANDARDS.md`.
 */
object Ui {
    /** A choice tile (fan speed, purifier mode, thermostat shortcut, charger mode, blind, camera button). */
    val TileHeight = 48.dp

    /** An icon above a name: the alarm's modes, a light's scenes, a light's pill. */
    val TallTileHeight = 64.dp

    val TileShape = RoundedCornerShape(12.dp)
    val TileGap = 8.dp
    val TileIcon = 20.dp

    /** The label beside an icon, and the smaller one under it. */
    val TileText = 13.sp
    val TileTextStacked = 11.sp

    /** Most tiles in a row before it wraps onto another. */
    const val TilesPerRow = 4
}

/**
 * Text that sits in the middle of whatever it's in. A line of text leaves spare space above and below the letters,
 * which makes an icon and its label look pushed up; this trims it, so they are centred. Every label on a tile or
 * button uses this rather than a bare [Text].
 */
@Composable
fun CentredText(
    text: String,
    color: Color,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    lineHeight: TextUnit = fontSize * 1.2f,
    maxLines: Int = 1,
) {
    Text(
        text, color = color, maxLines = maxLines, overflow = TextOverflow.Ellipsis, modifier = modifier,
        style = LocalTextStyle.current.copy(
            fontSize = fontSize,
            lineHeight = lineHeight,
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
        ),
    )
}

/**
 * How many tiles go in each row: shared out evenly (six is 3 and 3, five is 3 and 2, seven is 4 and 3), and every row
 * fills the width, so no row ever leaves a gap. Never more than [perRow] in a row.
 */
fun tileRowSizes(count: Int, perRow: Int = Ui.TilesPerRow): List<Int> {
    if (count <= 0) return emptyList()
    val rows = (count + perRow - 1) / perRow
    val base = count / rows
    val extra = count % rows
    // The first rows take the extra tile, so the last row is never the longest.
    return List(rows) { if (it < extra) base + 1 else base }
}
