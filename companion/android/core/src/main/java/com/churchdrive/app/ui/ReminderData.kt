package com.churchdrive.app.ui

import org.json.JSONObject

/** A place a task can be tied to: a Home Assistant zone and its name. */
data class Place(val zone: String, val name: String)

/** The places on offer and which task is tied to which (`church_drive/reminders`). */
data class PlaceReminders(val places: List<Place>, val zoneByTask: Map<String, String>)

const val NO_PLACE = "No place"

/** Reads `church_drive/reminders`; null if the house doesn't have place reminders (or didn't answer). */
fun parsePlaceReminders(result: Any?): PlaceReminders? {
    val o = result as? JSONObject ?: return null
    val zones = o.optJSONArray("zones") ?: return null
    val places = (0 until zones.length()).mapNotNull { zones.optJSONObject(it) }
        .mapNotNull { z -> z.optString("zone").takeIf { it.startsWith("zone.") }?.let { Place(it, z.optString("name").ifBlank { it.removePrefix("zone.") }) } }
    val byTask = mutableMapOf<String, String>()
    o.optJSONObject("reminders")?.let { r ->
        r.keys().forEach { k -> r.optJSONObject(k)?.optString("zone")?.takeIf { it.isNotEmpty() }?.let { byTask[k] = it } }
    }
    return PlaceReminders(places, byTask)
}

/** The key a task is under: its list and its uid. */
fun reminderKey(list: String, uid: String) = "$list|$uid"

/** What the choice row shows: "No place", then each place by name. */
fun placeChoices(places: List<Place>): List<String> = listOf(NO_PLACE) + places.map { it.name }

/** The choice that is the given zone ("No place" when it isn't one of the places). */
fun placeChoiceOf(places: List<Place>, zone: String?): String = places.firstOrNull { it.zone == zone }?.name ?: NO_PLACE

/** The zone a choice stands for, or null for "No place". */
fun zoneOfChoice(places: List<Place>, choice: String): String? = places.firstOrNull { it.name == choice }?.zone
