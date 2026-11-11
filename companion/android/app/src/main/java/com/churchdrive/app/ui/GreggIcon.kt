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

private const val RING_THICKNESS = 11f
private const val RING_RADIUS = 84.5f   // the middle of the ring: 79 to 90, a gap of twice its thickness to the disc

