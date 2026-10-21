package com.churchdrive.app.wear

import org.json.JSONArray
import org.json.JSONObject

/** The pages of the companion app, in the order the manager's Watch panel starts them. */
enum class WatchPage(val id: String, val label: String) {
    Alarm("alarm", "Alarm"),
    Climate("climate", "Climate"),
    Lights("lights", "Lights"),
    Cleaning("cleaning", "Cleaning"),
    Car("car", "Car charger"),
}

/** How the Lights page starts: a list of floors, of rooms or of zones. */
enum class LightsTop(val id: String) { Floors("floors"), Rooms("rooms"), Zones("zones") }

/** A car the charger page may take a battery from: its own battery sensor, and the sensor that says it is plugged in. */
data class WatchCar(val name: String, val battery: String, val plugged: String)

/** What Clive's toggles start as on a watch that has never set them. */
data class CliveDefaults(val voice: Boolean = true, val visual: Boolean = true, val follow: Boolean = false, val buzz: Boolean = true)

/**
 * What the manager chose in the Watch panel of the Manager dashboard (`church_drive/watch`): the pages shown and their order, what is
 * hidden on each, how the Lights page starts, the cars, and Clive's starting toggles. A house that has chosen nothing gets everything.
 */
data class WatchConfig(
    val pages: List<WatchPage>,
    val hidden: Map<WatchPage, Set<String>>,
    val lightsTop: LightsTop,
    val cars: List<WatchCar>,
    val clive: CliveDefaults,
) {
    /** Whether an item (a thermostat, a room, a face such as "suction") has been hidden on [page]. */
    fun hides(page: WatchPage, item: String): Boolean = hidden[page]?.contains(item) == true

    companion object {
        val Default = WatchConfig(WatchPage.entries.toList(), emptyMap(), LightsTop.Rooms, emptyList(), CliveDefaults())

        /** The house's answer, or the defaults when it is missing or malformed. Unknown pages are ignored; pages it leaves out are shown at the end. */
        fun parse(raw: Any?): WatchConfig {
            val o = raw as? JSONObject ?: return Default
            val listed = mutableListOf<Pair<WatchPage, Boolean>>()
            val pages = o.optJSONArray("pages")
            for (i in 0 until (pages?.length() ?: 0)) {
                val item = pages?.opt(i)
                val id = when (item) {
                    is String -> item
                    is JSONObject -> item.optString("id")
                    else -> ""
                }
                val page = WatchPage.entries.firstOrNull { it.id == id } ?: continue
                if (listed.none { it.first == page }) listed += page to ((item as? JSONObject)?.optBoolean("on", true) ?: true)
            }
            WatchPage.entries.filter { p -> listed.none { it.first == p } }.forEach { listed += it to true }
            val shown = listed.filter { it.second }.map { it.first }
            val hiddenObj = o.optJSONObject("hidden")
            val hidden = WatchPage.entries.mapNotNull { p ->
                val a = hiddenObj?.optJSONArray(p.id) ?: return@mapNotNull null
                val set = (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() }.toSet()
                if (set.isEmpty()) null else p to set
            }.toMap()
            val top = LightsTop.entries.firstOrNull { it.id == o.optString("lights_top") } ?: LightsTop.Rooms
            val cars = o.optJSONArray("cars")?.let { a ->
                (0 until a.length()).mapNotNull { a.optJSONObject(it) }.mapNotNull { c ->
                    val name = c.optString("name")
                    val battery = c.optString("battery")
                    val plugged = c.optString("plugged")
                    if (name.isBlank() || battery.isBlank() || plugged.isBlank()) null else WatchCar(name, battery, plugged)
                }
            }.orEmpty()
            val c = o.optJSONObject("clive")
            val clive = CliveDefaults(
                voice = c?.optBoolean("voice", true) ?: true,
                visual = c?.optBoolean("visual", true) ?: true,
                follow = c?.optBoolean("follow", false) ?: false,
                buzz = c?.optBoolean("buzz", true) ?: true,
            )
            return WatchConfig(shown, hidden, top, cars, clive)
        }
    }
}

/** The strings in a JSON array. */
internal fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else (0 until length()).map { optString(it) }
