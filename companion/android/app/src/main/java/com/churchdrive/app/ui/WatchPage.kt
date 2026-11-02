package com.churchdrive.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.churchdrive.app.ha.EntityState
import org.json.JSONArray
import org.json.JSONObject

private class WatchPageInfo(val id: String, val label: String, val icon: String, val what: String)

private val WATCH_PAGES = listOf(
    WatchPageInfo("alarm", "Alarm", "mdi:shield-home", "The alarm and its modes"),
    WatchPageInfo("climate", "Climate", "mdi:leaf", "Thermostats, air con, fans and air purifiers"),
    WatchPageInfo("lights", "Lights", "mdi:lightbulb", "Rooms and zones: scenes and brightness"),
    WatchPageInfo("cleaning", "Cleaning", "mdi:robot-vacuum", "Start, stop, home and the vacuum's settings"),
    WatchPageInfo("car", "Car charger", "mdi:ev-station", "Charger modes, speed and smart charge"),
)

private val LIGHTS_TOP = listOf("floors" to "Floors", "rooms" to "Rooms", "zones" to "Zones")

private val CLIVE_FLAGS = listOf("voice" to "Spoken answers", "follow" to "Follow-up listening", "visual" to "Visual answers", "buzz" to "Buzz on answer")

private fun JSONObject.hiddenList(page: String): List<String> = optJSONObject("hidden")?.optJSONArray(page)?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList()

private fun JSONObject.withItem(page: String, item: String, shown: Boolean): JSONObject {
    val copy = JSONObject(toString())
    val hidden = copy.optJSONObject("hidden") ?: JSONObject().also { copy.put("hidden", it) }
    val list = hiddenList(page).filter { it != item }.toMutableList()
    if (!shown) list += item
    if (list.isEmpty()) hidden.remove(page) else hidden.put(page, JSONArray(list))
    return copy
}

private fun JSONObject.pageList(): List<Pair<String, Boolean>> {
    val a = optJSONArray("pages") ?: JSONArray()
    val out = (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map { it.optString("id") to it.optBoolean("on", true) }.toMutableList()
    WATCH_PAGES.forEach { p -> if (out.none { it.first == p.id }) out += p.id to true }
    return out
}

private fun JSONObject.withPages(list: List<Pair<String, Boolean>>): JSONObject {
    val copy = JSONObject(toString())
    copy.put("pages", JSONArray(list.map { JSONObject().put("id", it.first).put("on", it.second) }))
    return copy
}

/** The Watch page of the Manager: which pages the watch has and in what order, what is on each, how Lights starts, and Clive's toggles. Saved in the house (`church_drive/watch`). */
@Composable
fun WatchSettingsPage(entities: Map<String, EntityState>) {
    val view = rememberWs("church_drive/watch")
    val api = LocalHaApi.current
    var layout by remember { mutableStateOf(LightLayout.Fallback) }
    LaunchedEffect(api) {
        (api?.ask("lovelace/config", JSONObject().put("url_path", DashboardLights.DASHBOARD)) as? JSONObject)?.let { layout = DashboardLights.parse(it) }
    }
    Waiting(view, "watch settings") { cfg ->
        val tone = toneColors(Tone.Indigo)
        val neutral = toneColors(Tone.Grey)
        fun save(next: JSONObject) = view.send("church_drive/watch/set", JSONObject().put("config", next))
        @Composable
        fun items(page: String, rows: List<Pair<String, String>>) {
            if (rows.isEmpty()) {
                Text("Nothing to choose here yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                return
            }
            val hidden = cfg.hiddenList(page)
            Choices(rows.map { (id, label) ->
                val shown = id !in hidden
                TileItem(if (shown) "mdi:eye" else "mdi:eye-off", label, shown) { save(cfg.withItem(page, id, !shown)) }
            }, tone, perRow = 2)
        }
        val pages = cfg.pageList()
        EntityCard(neutral.container, neutral.onContainer) {
            Text("Pages on the watch, in order", style = MaterialTheme.typography.titleMedium)
            Text("Switch a page off to hide it, or move it up or down. Under each, untick anything you don't want there: new devices show up by themselves.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        pages.forEachIndexed { index, (id, on) ->
            val info = WATCH_PAGES.firstOrNull { it.id == id } ?: return@forEachIndexed
            EntityCard(neutral.container, neutral.onContainer) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ToneIconName(info.icon, CdVectors.Info, tone, 36)
                    Column(Modifier.weight(1f)) {
                        Text(info.label, style = MaterialTheme.typography.titleMedium)
                        Text(info.what, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Choices(listOf(
                    TileItem(if (on) "mdi:toggle-switch" else "mdi:toggle-switch-off-outline", if (on) "On" else "Off", on) {
                        save(cfg.withPages(pages.map { if (it.first == id) it.first to !on else it }))
                    },
                    TileItem("mdi:chevron-up", "Up", false) {
                        if (index > 0) save(cfg.withPages(pages.toMutableList().also { java.util.Collections.swap(it, index, index - 1) }))
                    },
                    TileItem("mdi:chevron-down", "Down", false) {
                        if (index < pages.lastIndex) save(cfg.withPages(pages.toMutableList().also { java.util.Collections.swap(it, index, index + 1) }))
                    },
                ), tone, perRow = 3)
                if (on) when (id) {
                    "alarm" -> items("alarm", listOf("armed_home" to "Home", "armed_away" to "Away", "armed_night" to "Night"))
                    "climate" -> items(
                        "climate",
                        entities.values.filter { it.entityId.startsWith("climate.") || it.entityId.startsWith("fan.") }
                            .sortedBy { it.friendlyName }.map { it.entityId to it.friendlyName },
                    )
                    "lights" -> {
                        val top = cfg.optString("lights_top", "rooms")
                        Text("Starts with", style = MaterialTheme.typography.labelLarge)
                        Choices(LIGHTS_TOP.map { (key, label) ->
                            TileItem("mdi:view-list", label, top == key) { save(JSONObject(cfg.toString()).put("lights_top", key)) }
                        }, tone, perRow = 3)
                        val rows = layout.floors.flatMap { (_, rooms) -> rooms }.flatMap { room ->
                            val name = room.title ?: entities[room.head]?.friendlyName ?: room.head
                            listOf(room.head to name) + room.rows.filter { it.level == 1 }.map { it.entity to ("${name}: " + (it.name ?: entities[it.entity]?.friendlyName ?: it.entity)) }
                        }
                        items("lights", rows)
                    }
                    "cleaning" -> items("cleaning", listOf("control" to "Start, stop, home", "suction" to "Suction", "intensity" to "Cleaning intensity", "mop" to "Mop intensity"))
                    "car" -> {
                        items("car", listOf("modes" to "Modes", "stats" to "Speed and amount", "smart" to "Smart charge"))
                        val cars = entities.keys.mapNotNull { Regex("^binary_sensor\\.(.+)_battery_plugged$").find(it)?.groupValues?.get(1) }
                            .filter { entities.containsKey("sensor.${it}_battery") }
                        if (cars.isNotEmpty()) {
                            Text("Cars whose battery can show on the ring (only when exactly one is plugged in)", style = MaterialTheme.typography.labelLarge)
                            val chosen = cfg.optJSONArray("cars") ?: JSONArray()
                            fun isChosen(slug: String) = (0 until chosen.length()).any { chosen.optJSONObject(it)?.optString("battery") == "sensor.${slug}_battery" }
                            Choices(cars.sorted().map { slug ->
                                val on = isChosen(slug)
                                val name = entities["sensor.${slug}_battery"]?.friendlyName?.replace(Regex("\\s+battery$", RegexOption.IGNORE_CASE), "") ?: slug
                                TileItem("mdi:car-electric", name, on) {
                                    val next = JSONObject(cfg.toString())
                                    val list = (0 until chosen.length()).mapNotNull { chosen.optJSONObject(it) }.filter { it.optString("battery") != "sensor.${slug}_battery" }.toMutableList()
                                    if (!on) list += JSONObject().put("name", name).put("battery", "sensor.${slug}_battery").put("plugged", "binary_sensor.${slug}_battery_plugged")
                                    next.put("cars", JSONArray(list))
                                    save(next)
                                }
                            }, tone, perRow = 2)
                        }
                    }
                }
            }
        }
        EntityCard(neutral.container, neutral.onContainer) {
            Text("Clive on the watch", style = MaterialTheme.typography.titleMedium)
            Text("The starting point for these; each person can still change them on the watch.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val clive = cfg.optJSONObject("clive") ?: JSONObject()
            Choices(CLIVE_FLAGS.map { (key, label) ->
                val on = clive.optBoolean(key, key != "follow")
                TileItem(if (on) "mdi:toggle-switch" else "mdi:toggle-switch-off-outline", label, on) {
                    val next = JSONObject(cfg.toString())
                    next.put("clive", JSONObject(clive.toString()).put(key, !on))
                    save(next)
                }
            }, tone, perRow = 2)
        }
    }
}
