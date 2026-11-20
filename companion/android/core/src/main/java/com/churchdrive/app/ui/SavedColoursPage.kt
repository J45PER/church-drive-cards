package com.churchdrive.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private fun swatchColour(c: Pair<Double, Double>): Color = hsToRgb(c.first, c.second).let { Color(it[0], it[1], it[2]) }

/** The colours people see as presets in a light's colour settings: the same eight hues. */
private val PRESET_COLOURS = listOf(0.0 to 100.0, 28.0 to 100.0, 50.0 to 100.0, 140.0 to 75.0, 195.0 to 80.0, 235.0 to 70.0, 280.0 to 70.0, 330.0 to 70.0)

/**
 * A hue and saturation wheel: drag or tap to choose a colour, which [onPick] gets when you let go. It draws [current] with a ring.
 * (A light's own settings draw the same wheel and send what it picks to the light.)
 */
@Composable
fun HueSatWheel(current: Pair<Double, Double>?, onPick: (Pair<Double, Double>) -> Unit) {
    var drag by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var sizePx by remember { mutableStateOf(1f) }
    fun at(o: Offset): Pair<Double, Double> {
        val r = sizePx / 2.0
        return wheelHueSat(o.x - r, o.y - r, r)
    }
    val shown = drag ?: current
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Canvas(
            modifier = Modifier.size(260.dp)
                .onSizeChanged { sizePx = it.width.toFloat().coerceAtLeast(1f) }
                .pointerInput(Unit) { detectTapGestures { onPick(at(it)) } }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { drag = at(it) },
                        onDragEnd = { drag?.let(onPick); drag = null },
                        onDragCancel = { drag = null },
                    ) { change, _ -> drag = at(change.position) }
                },
        ) {
            val r = size.minDimension / 2f
            drawCircle(Brush.sweepGradient((0..6).map { Color.hsv((it * 60f) % 360f, 1f, 1f) }, center))
            drawCircle(Brush.radialGradient(listOf(Color.White, Color.Transparent), center = center, radius = r))
            shown?.let {
                val (px, py) = wheelPoint(it.first, it.second)
                val at = Offset(center.x + (px * r).toFloat(), center.y + (py * r).toFloat())
                drawCircle(swatchColour(it), radius = 16.dp.toPx(), center = at)
                drawCircle(Color.White, radius = 16.dp.toPx(), center = at, style = Stroke(width = 3.dp.toPx()))
            }
        }
    }
}

/**
 * The saved colours page (Clive Admin, in the account panel): the colours the house keeps for every light's colour settings, each with its
 * hue and saturation and a way to take it out, and a wheel and presets to add another (up to [MAX_SAVED_COLOURS]).
 */
@Composable
fun SavedColoursPage() {
    val state = LocalSavedColours.current
    val saved = state.colours
    val ink = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val tone = toneColors(Tone.Lights)
    var chosen by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    Text(
        "The colours kept for every light's colour settings, the same on every phone. Anyone can save one from a light's settings; they can be taken out here.",
        style = MaterialTheme.typography.bodyMedium, color = muted,
    )
    if (saved.isEmpty()) Text("None saved yet.", style = MaterialTheme.typography.bodyLarge)
    saved.forEach { c ->
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(swatchColour(c)))
            Column(Modifier.weight(1f)) {
                Text("Hue ${c.first.roundToInt()}°", style = MaterialTheme.typography.bodyLarge)
                Text("Saturation ${c.second.roundToInt()}%", style = MaterialTheme.typography.bodySmall, color = muted)
            }
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable(onClickLabel = "Remove this colour") { state.set(saved.filterNot { sameColour(it, c) }) },
                contentAlignment = Alignment.Center,
            ) { HaIcon("cd:bin", CdVectors.Info, ink, 22.dp) }
        }
    }
    Text("Add a colour", style = MaterialTheme.typography.titleSmall, color = muted)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        PRESET_COLOURS.forEach { c ->
            Box(Modifier.size(40.dp).clip(CircleShape).background(swatchColour(c)).clickable(onClickLabel = "Choose this colour") { chosen = c })
        }
    }
    HueSatWheel(chosen) { chosen = it }
    val pick = chosen
    val canAdd = pick != null && saved.size < MAX_SAVED_COLOURS && saved.none { sameColour(it, pick) }
    TileRow(
        listOf(TileItem("cd:plus", if (saved.size >= MAX_SAVED_COLOURS) "Eight is the most" else "Add this colour", false) { pick?.let { state.set(toggleSavedColour(saved, it)) } }),
        tone, ink, enabled = canAdd,
    )
}
