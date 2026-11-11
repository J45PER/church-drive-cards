package com.churchdrive.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ha.Registry
import com.churchdrive.app.ha.data
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

// The Manager's People, Locations, Notifications, Camera links and Icons, built on the integration's `church_drive/*` commands.

// ---------------------------------------------------------------------------------------------- Pure bits

private fun JSONArray?.objects(): List<JSONObject> = (0 until (this?.length() ?: 0)).mapNotNull { this?.optJSONObject(it) }

// ---------------------------------------------------------------------------------------------- Notifications

/** Each person: their phones (switch alerts on and off) and their places, with the names they give them. */
@Composable
fun PeoplePage(entities: Map<String, EntityState>, registry: Registry) {
    val rev = entities["sensor.church_drive_people"]?.let { "${it.attributes.opt("rev")}|${it.state}" }
    val view = rememberWs("church_drive/people", rev)
    Waiting(view, "people") { d ->
        val teal = toneColors(Tone.Teal)
        val zones = entities.values.filter { it.entityId.startsWith("zone.") && it.entityId != "zone.home" }.sortedBy { it.friendlyName }
        d.optJSONArray("people").objects().forEach { p ->
            val id = p.optString("entity_id")
            val neutral = toneColors(Tone.Grey)
            val places = p.optJSONArray("places").objects().map { it.optString("zone") to it.optString("name") }
            var editing by remember(id) { mutableStateOf<List<Pair<String, String>>?>(null) }
            EntityCard(neutral.container, neutral.onContainer) {
                val (word, tone) = presencePill(p.optString("home"), p.optString("place").takeIf { it.isNotBlank() }, p.optString("zone").takeIf { it.isNotBlank() })
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(p.optString("name"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(word, color = toneColors(tone).accent, style = MaterialTheme.typography.labelLarge)
                }
                Text("Phones", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val phones = p.optJSONArray("phones").objects()
                if (phones.isEmpty()) Text("No phones yet. They join by themselves when the Home Assistant app signs in as ${p.optString("first")}.", style = MaterialTheme.typography.bodySmall)
                Choices(phones.map { ph ->
                    TileItem("mdi:cellphone", ph.optString("name"), ph.optBoolean("on")) {
                        view.send("church_drive/people/phone", data("person" to id, "service" to ph.optString("service"), "on" to !ph.optBoolean("on")))
                    }
                }, teal, perRow = 2)
                Text("Places", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val draft = editing
                if (draft == null) {
                    if (places.isEmpty()) Text("Home is automatic. Add the places ${p.optString("first")} goes to.", style = MaterialTheme.typography.bodySmall)
                    places.forEach { (zone, name) ->
                        Text("${entities[zone]?.friendlyName ?: zone} · $name", style = MaterialTheme.typography.bodyMedium)
                    }
                    Choices(listOf(TileItem("mdi:pencil", "Edit places", false) { editing = places }), teal, perRow = 1)
                } else {
                    draft.forEachIndexed { i, (zone, name) ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(entities[zone]?.friendlyName ?: zone, style = MaterialTheme.typography.bodyMedium)
                                OutlinedTextField(name, { v -> editing = draft.toMutableList().also { it[i] = zone to v } }, singleLine = true, label = { Text("Called") }, modifier = Modifier.fillMaxWidth())
                            }
                            IconButton(onClick = { editing = draft.toMutableList().also { it.removeAt(i) } }) { Icon(CdVectors.Minus, contentDescription = "Take this place out") }
                        }
                    }
                    val free = zones.filter { z -> draft.none { it.first == z.entityId } }
                    if (free.isNotEmpty()) {
                        Text("Add a place", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Choices(free.map { z -> TileItem("mdi:map-marker-outline", z.friendlyName, false) { editing = draft + (z.entityId to z.friendlyName) } }, teal, perRow = 2)
                    }
                    Choices(listOf(
                        TileItem("mdi:check", "Save", true) {
                            val list = JSONArray().also { a -> draft.forEach { (z, n) -> a.put(JSONObject().put("zone", z).put("name", n.trim())) } }
                            view.send("church_drive/people/places", JSONObject().put("person", id).put("places", list))
                            editing = null
                        },
                        TileItem("mdi:close", "Cancel", false) { editing = null },
                    ), teal, perRow = 2)
                }
                // Cars: the ones this person has (a car can belong to several people; one nobody has is everyone's).
                val cars = registry.devicesOfPlatforms(CAR_PLATFORMS).filter { (_, ids) -> carEntities(ids, entities).battery.isNotEmpty() }.keys.toList()
                if (cars.isNotEmpty()) {
                    Text("Cars", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val mine = p.optJSONArray("cars")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty()
                    Choices(cars.map { device ->
                        TileItem("mdi:car", registry.deviceNameById(device) ?: "Car", device in mine) {
                            val next = if (device in mine) mine - device else mine + device
                            view.send("church_drive/people/cars", JSONObject().put("person", id).put("cars", JSONArray(next)))
                        }
                    }, teal, perRow = 2)
                }
                // Alerts and to-dos: each kind this person gets, grouped as on the dashboard.
                val kinds = d.optJSONArray("kinds").objects()
                val todoGroups = setOf("To-dos", "House jobs")
                fun kindTiles(list: List<JSONObject>) = list.filter { it.optBoolean("available", true) && canTick(it, p) }.map { k ->
                    TileItem("mdi:bell-outline", k.optString("name"), kindGoesTo(k, id)) {
                        view.send("church_drive/people/assign", data("kind" to k.optString("key"), "person" to id, "on" to !kindGoesTo(k, id)))
                    }
                }
                val alerts = kinds.filter { !(it.optString("group") == "People" && it.optString("key").startsWith("arrivals:")) && it.optString("group") !in todoGroups }
                kindGroups(JSONArray(alerts)).forEach { (group, list) ->
                    Text("Alerts · $group", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Choices(kindTiles(list), tone = toneColors(Tone.Purple), perRow = 2)
                }
                val todos = kinds.filter { it.optString("group") in todoGroups }
                if (todos.isNotEmpty()) {
                    Text("To-dos", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Choices(kindTiles(todos), tone = toneColors(Tone.Purple), perRow = 2)
                }
                // Arrivals: who is told when this person gets home or leaves.
                val arrivals = kinds.firstOrNull { it.optString("key") == "arrivals:$id" }
                val others = d.optJSONArray("people").objects().filter { it.optString("entity_id") != id }
                if (arrivals != null && others.isNotEmpty()) {
                    Text("Told when ${p.optString("first")} gets home or leaves", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Choices(others.map { o ->
                        val oid = o.optString("entity_id")
                        TileItem("mdi:account", o.optString("first").ifBlank { o.optString("name") }, kindGoesTo(arrivals, oid)) {
                            view.send("church_drive/people/assign", data("kind" to "arrivals:$id", "person" to oid, "on" to !kindGoesTo(arrivals, oid)))
                        }
                    }, toneColors(Tone.Purple))
                }
            }
        }
        OpenInHomeAssistant("Change pictures", "Pictures are Home Assistant's own person pictures, so they're changed there.")
    }
}

// ---------------------------------------------------------------------------------------------- Locations

