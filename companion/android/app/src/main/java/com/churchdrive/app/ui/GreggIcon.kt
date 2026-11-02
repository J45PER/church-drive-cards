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
private const val CHARGING = "M146.0,98.9 L145.7,94.4 L144.9,89.9 L143.7,85.6 L142.1,81.4 L140.0,77.3 L137.6,73.5 L134.8,70.0 L131.7,66.7 L128.3,63.7 L124.6,61.1 L120.7,58.9 L116.6,57.1 L112.3,55.7 L107.9,54.7 L103.4,54.1 L98.9,54.0 L94.4,54.3 L89.9,55.1 L85.6,56.3 L81.4,57.9 L77.3,60.0 L73.5,62.4 L70.0,65.2 L66.7,68.3 L63.7,71.7 L61.1,75.4 L58.9,79.3 L57.1,83.4 L55.7,87.7 L54.7,92.1 L54.1,96.6 L54.0,101.1 L54.3,105.6 L55.1,110.1 L56.3,114.4 L57.9,118.6 L60.0,122.7 L62.4,126.5 L65.2,130.0 L68.3,133.3 L71.7,136.3 L75.4,138.9 L79.3,141.1 L83.4,142.9 L87.7,144.3 L92.1,145.3 L96.6,145.9 L101.1,146.0 L105.6,145.7 L110.1,144.9 L114.4,143.7 L118.6,142.1 L122.7,140.0 L126.5,137.6 L130.0,134.8 L133.3,131.7 L136.3,128.3 L138.9,124.6 L141.1,120.7 L142.9,116.6 L144.3,112.3 L145.3,107.9 L145.9,103.4 Z M99.1,109.1 L89.0,109.1 L88.4,108.9 L88.0,108.5 L87.7,108.0 L87.6,107.4 L87.8,106.8 L101.1,80.1 L101.8,79.4 L102.7,79.2 L103.6,79.6 L104.1,80.6 L104.1,94.1 L114.2,94.1 L114.8,94.3 L115.2,94.7 L115.5,95.2 L115.6,95.8 L115.4,96.4 L102.1,123.1 L101.6,123.7 L100.8,124.0 L100.2,123.9 L99.7,123.7 L99.3,123.2 L99.1,122.6 Z"
private const val HOUSE = "M146.0,98.9 L145.7,94.4 L144.9,89.9 L143.7,85.6 L142.1,81.4 L140.0,77.3 L137.6,73.5 L134.8,70.0 L131.7,66.7 L128.3,63.7 L124.6,61.1 L120.7,58.9 L116.6,57.1 L112.3,55.7 L107.9,54.7 L103.4,54.1 L98.9,54.0 L94.4,54.3 L89.9,55.1 L85.6,56.3 L81.4,57.9 L77.3,60.0 L73.5,62.4 L70.0,65.2 L66.7,68.3 L63.7,71.7 L61.1,75.4 L58.9,79.3 L57.1,83.4 L55.7,87.7 L54.7,92.1 L54.1,96.6 L54.0,101.1 L54.3,105.6 L55.1,110.1 L56.3,114.4 L57.9,118.6 L60.0,122.7 L62.4,126.5 L65.2,130.0 L68.3,133.3 L71.7,136.3 L75.4,138.9 L79.3,141.1 L83.4,142.9 L87.7,144.3 L92.1,145.3 L96.6,145.9 L101.1,146.0 L105.6,145.7 L110.1,144.9 L114.4,143.7 L118.6,142.1 L122.7,140.0 L126.5,137.6 L130.0,134.8 L133.3,131.7 L136.3,128.3 L138.9,124.6 L141.1,120.7 L142.9,116.6 L144.3,112.3 L145.3,107.9 L145.9,103.4 Z M95.9,77.9 L97.8,76.7 L99.6,76.2 L101.5,76.4 L103.4,77.3 L122.8,95.4 L124.2,96.8 L125.3,98.4 L126.0,99.6 L126.6,101.4 L127.2,104.2 L127.3,119.8 L127.1,121.7 L126.6,123.2 L126.0,124.2 L124.9,125.1 L123.4,125.7 L121.6,126.0 L107.9,126.0 L107.3,125.8 L107.0,125.2 L107.0,109.7 L106.9,108.2 L106.5,107.0 L105.6,105.9 L104.3,105.1 L102.6,104.8 L97.4,104.8 L96.0,105.1 L94.6,105.7 L94.0,106.3 L93.5,107.0 L93.1,108.2 L93.0,109.3 L93.0,125.1 L92.9,125.6 L92.6,125.8 L92.1,126.0 L77.4,125.9 L76.2,125.6 L75.1,125.1 L74.3,124.4 L73.6,123.6 L73.0,122.1 L72.7,120.3 L72.7,105.2 L73.1,102.3 L74.0,99.6 L75.2,97.6 L76.9,95.7 Z"
private const val TURRET = "M54,100 a46,46 0 1,0 92,0 a46,46 0 1,0 -92,0Z M87.5,88 a12.5,12.5 0 1,0 25,0 a12.5,12.5 0 1,0 -25,0Z M95,118 a5,5 0 1,0 10,0 a5,5 0 1,0 -10,0Z M65.23,78.27 A41,41 0 0,1 134.77,78.27 L130.53,80.92 A36,36 0 0,0 69.47,80.92Z"
private const val PAUSED = "M146.0,98.9 L145.7,94.4 L144.9,89.9 L143.7,85.6 L142.1,81.4 L140.0,77.3 L137.6,73.5 L134.8,70.0 L131.7,66.7 L128.3,63.7 L124.6,61.1 L120.7,58.9 L116.6,57.1 L112.3,55.7 L107.9,54.7 L103.4,54.1 L98.9,54.0 L94.4,54.3 L89.9,55.1 L85.6,56.3 L81.4,57.9 L77.3,60.0 L73.5,62.4 L70.0,65.2 L66.7,68.3 L63.7,71.7 L61.1,75.4 L58.9,79.3 L57.1,83.4 L55.7,87.7 L54.7,92.1 L54.1,96.6 L54.0,101.1 L54.3,105.6 L55.1,110.1 L56.3,114.4 L57.9,118.6 L60.0,122.7 L62.4,126.5 L65.2,130.0 L68.3,133.3 L71.7,136.3 L75.4,138.9 L79.3,141.1 L83.4,142.9 L87.7,144.3 L92.1,145.3 L96.6,145.9 L101.1,146.0 L105.6,145.7 L110.1,144.9 L114.4,143.7 L118.6,142.1 L122.7,140.0 L126.5,137.6 L130.0,134.8 L133.3,131.7 L136.3,128.3 L138.9,124.6 L141.1,120.7 L142.9,116.6 L144.3,112.3 L145.3,107.9 L145.9,103.4 Z M86.5,76.0 L90.5,76.1 L91.7,76.3 L92.8,76.8 L94.0,77.8 L95.0,79.1 L95.5,80.2 L95.8,81.8 L95.8,118.6 L95.5,119.8 L95.0,120.9 L93.7,122.5 L92.0,123.5 L90.1,124.0 L85.5,123.9 L84.3,123.7 L83.2,123.2 L82.3,122.5 L81.2,121.2 L80.5,119.8 L80.2,118.2 L80.2,81.4 L80.8,79.5 L81.4,78.4 L82.3,77.5 L83.2,76.8 L84.3,76.3 Z M110.5,76.0 L114.5,76.1 L115.7,76.3 L116.8,76.8 L118.0,77.8 L118.8,78.8 L119.5,80.2 L119.8,81.8 L119.8,118.6 L119.5,119.8 L119.0,120.9 L117.7,122.5 L116.0,123.5 L114.1,124.0 L109.5,123.9 L108.3,123.7 L107.2,123.2 L106.3,122.5 L105.2,121.2 L104.5,119.8 L104.2,118.2 L104.2,81.4 L104.8,79.5 L105.4,78.4 L106.3,77.5 L107.2,76.8 L108.3,76.3 Z"
private const val ERROR = "M146.0,98.9 L145.7,94.4 L144.9,89.9 L143.7,85.6 L142.1,81.4 L140.0,77.3 L137.6,73.5 L134.8,70.0 L131.7,66.7 L128.3,63.7 L124.6,61.1 L120.7,58.9 L116.6,57.1 L112.3,55.7 L107.9,54.7 L103.4,54.1 L98.9,54.0 L94.4,54.3 L89.9,55.1 L85.6,56.3 L81.4,57.9 L77.3,60.0 L73.5,62.4 L70.0,65.2 L66.7,68.3 L63.7,71.7 L61.1,75.4 L58.9,79.3 L57.1,83.4 L55.7,87.7 L54.7,92.1 L54.1,96.6 L54.0,101.1 L54.3,105.6 L55.1,110.1 L56.3,114.4 L57.9,118.6 L60.0,122.7 L62.4,126.5 L65.2,130.0 L68.3,133.3 L71.7,136.3 L75.4,138.9 L79.3,141.1 L83.4,142.9 L87.7,144.3 L92.1,145.3 L96.6,145.9 L101.1,146.0 L105.6,145.7 L110.1,144.9 L114.4,143.7 L118.6,142.1 L122.7,140.0 L126.5,137.6 L130.0,134.8 L133.3,131.7 L136.3,128.3 L138.9,124.6 L141.1,120.7 L142.9,116.6 L144.3,112.3 L145.3,107.9 L145.9,103.4 Z M106.8,78.8 L106.8,100.0 L106.4,102.2 L105.9,103.4 L105.1,104.5 L104.1,105.4 L103.0,106.1 L101.8,106.6 L100.4,106.8 L99.1,106.8 L97.8,106.5 L95.9,105.4 L94.6,104.2 L93.7,102.6 L93.3,100.9 L93.2,78.8 L93.6,76.6 L94.6,74.6 L95.5,73.7 L96.6,72.9 L98.7,72.1 L100.9,72.1 L103.0,72.7 L104.1,73.4 L105.1,74.3 L105.9,75.4 L106.4,76.6 Z M106.8,123.7 L106.6,125.1 L106.3,126.3 L105.6,127.5 L104.8,128.5 L103.8,129.4 L102.6,130.0 L101.3,130.4 L100.0,130.5 L98.7,130.4 L97.4,130.0 L96.2,129.4 L95.2,128.5 L94.4,127.5 L93.7,126.3 L93.4,125.1 L93.2,123.7 L93.4,122.4 L93.7,121.1 L94.4,120.0 L95.2,118.9 L96.2,118.1 L97.4,117.5 L98.7,117.1 L100.0,117.0 L101.3,117.1 L102.6,117.5 L103.8,118.1 L104.8,118.9 L105.6,120.0 L106.3,121.1 L106.6,122.4 Z"
