package com.churchdrive.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.churchdrive.app.ha.EntityState

/** What Gregg (the robot vacuum) is doing, as the status icon shows it. */
enum class GreggState {
    /** Not doing anything and not on the dock. */
    Idle,
    /** On the dock and still charging: the ring fills to the battery level and a bolt shows. */
    Charging,
    /** On the dock and full: the house, with the ring closed. */
    Charged,
    /** Cleaning: the ring in three parts turns round him. */
    Cleaning,
    /** Paused: the same ring, stopped, with pause bars. */
    Paused,
    /** Heading for the dock: the house, with two parts of the ring running backwards. */
    Returning,
    /** Stuck or faulted: the ring closes and blinks, with an exclamation mark. */
    Error,
}

/** The status for Gregg's vacuum entity and battery sensor: docked is Charging until the battery reads 100, then Charged. */
fun greggState(vacuum: EntityState?, battery: EntityState?): GreggState = greggStateFor(vacuum?.state, battery?.state?.toDoubleOrNull())

/** The same from the vacuum's state word and the battery level (a card sent by the house carries these). */
fun greggStateFor(vacuumState: String?, level: Double?): GreggState {
    return when (vacuumState) {
        "cleaning" -> GreggState.Cleaning
        "paused" -> GreggState.Paused
        "returning" -> GreggState.Returning
        "error" -> GreggState.Error
        "docked" -> if (level != null && level < 100.0) GreggState.Charging else GreggState.Charged
        else -> GreggState.Idle
    }
}

/**
 * The `cd:` icon for one option of Gregg's suction level (Quiet, Standard, Turbo, Max, Boost_IQ) or water level (Low, Medium, High) and cleaning mode (vacuum, mop, both), drawn like the
 * Eufy app's: a pinwheel of blades for suction (the Max and Boost IQ ones carry a badge), a drop that fills for water. Null for any other entity or option.
 */
fun greggLevelIcon(entityId: String, option: String): String? {
    val key = option.lowercase().filter { it.isLetter() }
    return when {
        entityId.endsWith("_suction_level") -> when (key) {
            "quiet" -> "cd:suction-quiet"
            "standard" -> "cd:suction-standard"
            "turbo" -> "cd:suction-turbo"
            "max" -> "cd:suction-max"
            "boostiq" -> "cd:suction-boost"
            else -> null
        }
        entityId.endsWith("_cleaning_mode") -> when (key) {
            "vacuum" -> "cd:vac"
            "mop" -> "cd:mop"
            "vacuumandmop", "moppingaftersweeping" -> "cd:both"
            else -> null
        }
        entityId.endsWith("_water_level") -> when (key) {
            "low" -> "cd:water-low"
            "medium" -> "cd:water-medium"
            "high" -> "cd:water-high"
            else -> null
        }
        else -> null
    }
}

/** The icon name a Cleaning page title carries to mean Gregg's resting (idle) icon, which never changes with what he is doing. */
const val GREGG_IDLE_ICON = "gregg:idle"

/** The house's own icon for one of Gregg's dock buttons or his find switch, or null for any other entity. */
fun greggEntityIcon(entityId: String): String? = when (entityId) {
    "button.gregg_empty_dust_bin" -> "cd:empty"
    "button.gregg_wash_mop" -> "cd:wash"
    "button.gregg_dry_mop" -> "cd:dry"
    "switch.gregg_find_robot" -> "cd:find"
    else -> null
}

/** The word for a status pill. */
fun GreggState.word(): String = when (this) {
    GreggState.Idle -> "Idle"
    GreggState.Charging -> "Charging"
    GreggState.Charged -> "Docked"
    GreggState.Cleaning -> "Cleaning"
    GreggState.Paused -> "Paused"
    GreggState.Returning -> "Heading home"
    GreggState.Error -> "Needs help"
}

/** The part of the disc glyph that is cut out of the disc, in the icon board's drawing (the disc is drawn at 46 and scaled up to 57). */
private fun glyphFor(state: GreggState): String = when (state) {
    GreggState.Idle -> IDLE
    GreggState.Charging -> CHARGING
    GreggState.Charged, GreggState.Returning -> HOUSE
    GreggState.Cleaning -> TURRET
    GreggState.Paused -> PAUSED
    GreggState.Error -> ERROR
}

private const val DISC_SCALE = 1.239130f
private const val RING_THICKNESS = 11f
private const val RING_RADIUS = 84.5f   // the middle of the ring: 79 to 90, a gap of twice its thickness to the disc

/**
 * Gregg's status icon: one disc with the glyph cut out and one ring round it, as on the icon board. The ring never changes size: it turns in
 * parts while he cleans, backwards while he returns, fills to the battery level while he charges, and blinks closed on an error.
 * [batteryPercent] only matters while charging.
 */
@Composable
fun GreggIcon(state: GreggState, tint: Color, size: Dp = 44.dp, batteryPercent: Double? = null, modifier: Modifier = Modifier) {
    val glyph = remember(state) { PathParser().parsePathString(glyphFor(state)).toPath().apply { fillType = PathFillType.EvenOdd } }
    val t = rememberInfiniteTransition(label = "gregg")
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "spin")
    val back by t.animateFloat(0f, 360f, infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "back")
    val blink by t.animateFloat(1f, 0.35f, infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Reverse), label = "blink")
    Canvas(modifier.size(size)) {
        val unit = this.size.minDimension / 200f
        scale(unit, unit, pivot = Offset.Zero) {
            scale(DISC_SCALE, pivot = Offset(100f, 100f)) { drawPath(glyph, tint) }
            when (state) {
                GreggState.Idle, GreggState.Charged -> ring(tint)
                GreggState.Error -> ring(tint, blink)
                GreggState.Charging -> {
                    ring(tint, 0.22f)
                    val sweep = ((batteryPercent ?: 100.0).coerceIn(0.0, 100.0) * 3.6).toFloat().coerceAtLeast(10f)
                    arcs(tint, -90f, sweep)
                }
                GreggState.Cleaning -> parts(tint, 3, 24f, spin)
                GreggState.Paused -> parts(tint, 3, 24f, 20f)
                GreggState.Returning -> parts(tint, 2, 90f, -back)
            }
        }
    }
}

private fun DrawScope.ring(tint: Color, alpha: Float = 1f) {
    drawCircle(tint, radius = RING_RADIUS, center = Offset(100f, 100f), alpha = alpha, style = Stroke(RING_THICKNESS))
}

/** The ring in [count] parts with a gap of [gap] degrees between them, turned by [rotation] (round ends are drawn by the stroke's caps, so the arcs are shortened to match). */
private fun DrawScope.parts(tint: Color, count: Int, gap: Float, rotation: Float) {
    val span = 360f / count
    for (i in 0 until count) arcs(tint, rotation + i * span, span - gap)
}

private fun DrawScope.arcs(tint: Color, start: Float, sweep: Float) {
    val cap = Math.toDegrees((RING_THICKNESS / 2f / RING_RADIUS).toDouble()).toFloat()
    val s = sweep - 2 * cap
    if (s <= 0f) return
    drawArc(
        tint, startAngle = start + cap, sweepAngle = s, useCenter = false,
        topLeft = Offset(100f - RING_RADIUS, 100f - RING_RADIUS), size = Size(RING_RADIUS * 2, RING_RADIUS * 2),
        style = Stroke(RING_THICKNESS, cap = StrokeCap.Round),
    )
}

private const val IDLE = "M54,100 a46,46 0 1,0 92,0 a46,46 0 1,0 -92,0Z M87.5,88 a12.5,12.5 0 1,0 25,0 a12.5,12.5 0 1,0 -25,0Z M95,118 a5,5 0 1,0 10,0 a5,5 0 1,0 -10,0Z M65.23,78.27 A41,41 0 0,1 134.77,78.27 L130.53,80.92 A36,36 0 0,0 69.47,80.92Z"
private const val CHARGING = "M54,100 a46,46 0 1,0 92,0 a46,46 0 1,0 -92,0Z M104.8,79.2 L88.8,104.8 L100,104.8 L95.2,124 L114.4,93.6 L103.2,93.6 L109.6,79.2Z"
private const val HOUSE = "M54,100 a46,46 0 1,0 92,0 a46,46 0 1,0 -92,0Z M95.5,78.25 Q100,74.04 104.5,78.25 L122.78,95.35 Q127.28,99.56 127.28,105.72 L127.28,119.8 Q127.28,125.96 121.12,125.96 L107.92,125.96 Q107.04,125.96 107.04,125.08 L107.04,109.68 Q107.04,104.84 102.2,104.84 L97.8,104.84 Q92.96,104.84 92.96,109.68 L92.96,125.08 Q92.96,125.96 92.08,125.96 L78.88,125.96 Q72.72,125.96 72.72,119.8 L72.72,105.72 Q72.72,99.56 77.22,95.35 Z"
private const val TURRET = "M54,100 a46,46 0 1,0 92,0 a46,46 0 1,0 -92,0Z M87.5,88 a12.5,12.5 0 1,0 25,0 a12.5,12.5 0 1,0 -25,0Z M95,118 a5,5 0 1,0 10,0 a5,5 0 1,0 -10,0Z M65.23,78.27 A41,41 0 0,1 134.77,78.27 L130.53,80.92 A36,36 0 0,0 69.47,80.92Z"
private const val PAUSED = "M54,100 a46,46 0 1,0 92,0 a46,46 0 1,0 -92,0Z M87,76 H87 A7,7 0 0 1 94,83 V117 A7,7 0 0 1 87,124 H87 A7,7 0 0 1 80,117 V83 A7,7 0 0 1 87,76Z M113,76 H113 A7,7 0 0 1 120,83 V117 A7,7 0 0 1 113,124 H113 A7,7 0 0 1 106,117 V83 A7,7 0 0 1 113,76Z"
private const val ERROR = "M54,100 a46,46 0 1,0 92,0 a46,46 0 1,0 -92,0Z M100,72 H100 A8,8 0 0 1 108,80 V98 A8,8 0 0 1 100,106 H100 A8,8 0 0 1 92,98 V80 A8,8 0 0 1 100,72Z M91.5,122 a8.5,8.5 0 1,0 17,0 a8.5,8.5 0 1,0 -17,0Z"
