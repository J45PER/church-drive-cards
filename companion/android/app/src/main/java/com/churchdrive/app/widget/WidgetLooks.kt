package com.churchdrive.app.widget

import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ui.ALARM_ENTITY
import com.churchdrive.app.ui.CLIMATE_ENTITY
import com.churchdrive.app.ui.CLIMATE_QUALITY_ENTITY
import com.churchdrive.app.ui.alarmLabel
import com.churchdrive.app.ui.qualityWord

/** What a home-screen card shows: a small title, the big value, a line under it, and the card's colour (ARGB). */
data class WidgetLook(val title: String, val value: String, val sub: String, val colour: Int)

/** The cards' colours, dark enough for white text. They match the app's: green, blue, red, purple, amber, orange, grey. */
object WidgetColours {
    const val GREEN = 0xFF2E7D32.toInt()
    const val BLUE = 0xFF1565C0.toInt()
    const val RED = 0xFFC62828.toInt()
    const val PURPLE = 0xFF6A4BB5.toInt()
    const val AMBER = 0xFFB26A00.toInt()
    const val ORANGE = 0xFFD84315.toInt()
    const val GREY = 0xFF546E7A.toInt()
}

object WidgetLooks {
    /** The alarm: its state in words, in the colour the app uses (green off, blue home, red away, purple night). */
    fun alarm(states: Map<String, EntityState>?): WidgetLook {
        val a = states?.get(ALARM_ENTITY) ?: return unavailable("Security")
        val colour = when (a.state) {
            "disarmed" -> WidgetColours.GREEN
            "armed_home" -> WidgetColours.BLUE
            "armed_away", "triggered" -> WidgetColours.RED
            "armed_night" -> WidgetColours.PURPLE
            "arming", "pending" -> WidgetColours.ORANGE
            else -> WidgetColours.GREY
        }
        return WidgetLook("Security", alarmLabel(a.state), if (a.state == "triggered") "Open the app" else "Tap to open", colour)
    }

    /** Lights: how many are on. */
    fun lights(states: Map<String, EntityState>?): WidgetLook {
        if (states == null) return unavailable("Lights")
        val lights = states.values.filter { it.entityId.startsWith("light.") && it.available }
        val on = lights.count { it.state == "on" }
        return WidgetLook(
            "Lights",
            if (on == 0) "All off" else "$on on",
            if (on == 0) "${lights.size} lights" else "of ${lights.size} lights",
            if (on == 0) WidgetColours.GREY else WidgetColours.AMBER,
        )
    }

    /** Climate: the home's quality score when there is one, and the main thermostat's temperature. */
    fun climate(states: Map<String, EntityState>?): WidgetLook {
        if (states == null) return unavailable("Climate")
        val score = states[CLIMATE_QUALITY_ENTITY]?.state?.toIntOrNull()
        val temp = states[CLIMATE_ENTITY]?.num("current_temperature")?.let { "%.1f °C".format(it) }
        val colour = when {
            score == null -> WidgetColours.GREY
            score >= 85 -> WidgetColours.GREEN
            score >= 70 -> 0xFF558B2F.toInt()
            score >= 50 -> WidgetColours.AMBER
            score >= 30 -> WidgetColours.ORANGE
            else -> WidgetColours.RED
        }
        return WidgetLook(
            "Climate",
            score?.let { "$it/100" } ?: (temp ?: "–"),
            listOfNotNull(score?.let { qualityWord(it) }, temp.takeIf { score != null }).joinToString(" · "),
            colour,
        )
    }

    fun unavailable(title: String) = WidgetLook(title, "–", "Can't reach the house", WidgetColours.GREY)
}
