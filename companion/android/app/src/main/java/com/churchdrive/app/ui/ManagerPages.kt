package com.churchdrive.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Remove
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

/** The notification kinds by group, in the order the integration sends them. */
fun kindGroups(kinds: JSONArray?): List<Pair<String, List<JSONObject>>> {
    val all = (0 until (kinds?.length() ?: 0)).mapNotNull { kinds?.optJSONObject(it) }
    return all.map { it.optString("group") }.distinct().map { g -> g to all.filter { it.optString("group") == g } }
}

/** Whether a kind goes to a person: everyone, or ticked. */
fun kindGoesTo(kind: JSONObject, personId: String): Boolean {
    if (kind.optBoolean("all")) return true
    val people = kind.optJSONArray("people") ?: return false
    return (0 until people.length()).any { people.optString(it) == personId }
}

/** Whether a person may be ticked for a kind: costs and the like are for administrators only. */
fun canTick(kind: JSONObject, person: JSONObject): Boolean = !kind.optBoolean("admin_only") || person.optBoolean("admin")

/** An icon name Home Assistant accepts: `mdi:` and lower-case words with dashes. */
fun validIconName(name: String): Boolean = Regex("^mdi:[a-z0-9]+(-[a-z0-9]+)*$").matches(name.trim())

/** The camera links of one alarm mode: trigger entity ids with their cameras and recording seconds. */
data class CameraLink(val trigger: String, val cams: List<String>, val secs: Int)

fun linksOf(links: JSONObject?, mode: String): List<CameraLink> {
    val table = links?.optJSONObject(mode) ?: return emptyList()
    return table.keys().asSequence().map { t ->
        val rule = table.optJSONObject(t) ?: JSONObject()
        val cams = rule.optJSONArray("cams")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty()
        CameraLink(t, cams, rule.optInt("secs", 30))
    }.sortedBy { it.trigger }.toList()
}

/** Things that can start a recording: movement, doors, doorbell and the like. */
fun triggerChoices(entities: Map<String, EntityState>, taken: Set<String>): List<EntityState> {
    val classes = setOf("motion", "occupancy", "door", "window", "opening", "garage_door", "sound", "vibration", "moving", "presence", "running")
    return entities.values.filter { it.entityId.startsWith("binary_sensor.") && it.str("device_class") in classes && it.entityId !in taken }.sortedBy { it.friendlyName }
}

/** What a mode is called. */
fun modeLabel(mode: String): String = when (mode) {
    "disarmed" -> "Disarmed"
    "home" -> "Home"
    "away" -> "Away"
    else -> presetLabel(mode)
}

fun modeIcon(mode: String): String = when (mode) {
    "disarmed" -> "mdi:shield-off-outline"
    "home" -> "mdi:shield-home-outline"
    "away" -> "mdi:shield-lock-outline"
    else -> "mdi:shield-outline"
}

/** A zone for the Locations page: its name, radius and the people in it. */
data class ZoneRow(val id: String, val name: String, val radius: Int?, val people: List<String>, val home: Boolean)

fun zoneRows(entities: Map<String, EntityState>): List<ZoneRow> =
    entities.values.filter { it.entityId.startsWith("zone.") }.map { z ->
        val inside = entities.values.filter { it.entityId.startsWith("person.") && (it.state == z.friendlyName || (z.entityId == "zone.home" && it.state == "home")) }.map { it.friendlyName }
        ZoneRow(z.entityId, z.friendlyName, z.num("radius")?.toInt(), inside.sorted(), z.entityId == "zone.home")
    }.sortedWith(compareBy({ !it.home }, { it.name }))

// ---------------------------------------------------------------------------------------------- Loading and sending

/** An integration command's answer, kept; [send] runs a change and then asks again. Null [data] until the first answer. */
class WsView(val data: JSONObject?, val failed: Boolean, val send: (String, JSONObject) -> Unit)

@Composable
fun rememberWs(type: String, rev: Any? = null): WsView {
    val api = LocalHaApi.current
    var answer by remember(type) { mutableStateOf<JSONObject?>(null) }
    var failed by remember(type) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    suspend fun load(): Boolean {
        var tries = 0
        while (api != null && tries < 4) {
            val result = api.ask(type, JSONObject())
            if (result is JSONObject) { answer = result; failed = false; return true }
            tries++
            kotlinx.coroutines.delay(1_500L * tries)
        }
        return false
    }
    LaunchedEffect(type, rev) { if (!load()) failed = true }
    return WsView(answer, failed) { t, p ->
        scope.launch {
            api?.ask(t, p)
            load()
        }
    }
}

@Composable
private fun Waiting(view: WsView, what: String, body: @Composable (JSONObject) -> Unit) {
    val d = view.data
    when {
        d != null -> body(d)
        view.failed -> ManagerNote("$what couldn't be read from the house. Church Drive may not be running, or the connection dropped.")
        else -> LoadingNote("Loading $what…")
    }
}

@Composable
private fun ManagerNote(text: String) {
    val neutral = toneColors(Tone.Grey)
    EntityCard(neutral.container, neutral.onContainer) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A card with a button that opens the Manager page in Home Assistant, for what the app doesn't do itself. */
@Composable
fun OpenInHomeAssistant(label: String, why: String, path: String = "dashboard-manager/system") {
    val base = LocalHaUrl.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val neutral = toneColors(Tone.Grey)
    EntityCard(neutral.container, neutral.onContainer) {
        Text(why, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (base.isNotBlank()) Choices(listOf(TileItem("mdi:open-in-new", label, false) {
            runCatching {
                context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("$base/$path")).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }), neutral, perRow = 1)
    }
}

/** A group of choices as the kit's tiles: the ones in [on] selected. */
@Composable
private fun Choices(items: List<TileItem>, tone: ToneColors, perRow: Int = 3, enabled: Boolean = true) =
    TileRow(items, tone, tone.onContainer, enabled, perRow)

private fun JSONArray?.objects(): List<JSONObject> = (0 until (this?.length() ?: 0)).mapNotNull { this?.optJSONObject(it) }

// ---------------------------------------------------------------------------------------------- Notifications

/** Who gets each kind of notification, and which of each person's phones. */
@Composable
fun NotificationsPage(entities: Map<String, EntityState>) {
    val rev = entities["sensor.church_drive_people"]?.let { "${it.attributes.opt("rev")}|${it.state}" }
    val view = rememberWs("church_drive/people", rev)
    Waiting(view, "notifications") { d ->
        val people = d.optJSONArray("people").objects()
        val purple = toneColors(Tone.Purple)
        kindGroups(d.optJSONArray("kinds")).forEach { (group, kinds) ->
            val neutral = toneColors(Tone.Grey)
            EntityCard(neutral.container, neutral.onContainer) {
                Text(group, style = MaterialTheme.typography.titleMedium)
                kinds.forEach { kind ->
                    val key = kind.optString("key")
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(kind.optString("name") + if (kind.optBoolean("critical")) " ⚠" else "", style = MaterialTheme.typography.bodyLarge)
                        kind.optString("note").takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        if (!kind.optBoolean("available", true)) {
                            Text("Waiting for its devices to be set up", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            val everyone = kind.optBoolean("all")
                            Choices(listOf(TileItem("mdi:account-group", "Everyone", everyone) {
                                view.send("church_drive/people/assign", data("kind" to key, "on" to !everyone))
                            }), purple, perRow = 1)
                            Choices(people.filter { canTick(kind, it) }.map { p ->
                                val id = p.optString("entity_id")
                                TileItem("mdi:account", p.optString("first").ifBlank { p.optString("name") }, kindGoesTo(kind, id)) {
                                    view.send("church_drive/people/assign", data("kind" to key, "person" to id, "on" to !kindGoesTo(kind, id)))
                                }
                            }, purple, enabled = !everyone)
                        }
                    }
                }
            }
        }
        val neutral = toneColors(Tone.Grey)
        EntityCard(neutral.container, neutral.onContainer) {
            Text("Phones", style = MaterialTheme.typography.titleMedium)
            Text("Notifications go to the phones that are switched on.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            people.forEach { p ->
                val phones = p.optJSONArray("phones").objects()
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(p.optString("name") + if (p.optBoolean("admin")) " · Admin" else "", style = MaterialTheme.typography.bodyLarge)
                    if (phones.isEmpty()) Text("No phones yet", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Choices(phones.map { ph ->
                        TileItem("mdi:cellphone", ph.optString("name"), ph.optBoolean("on")) {
                            view.send("church_drive/people/phone", data("person" to p.optString("entity_id"), "service" to ph.optString("service"), "on" to !ph.optBoolean("on")))
                        }
                    }, purple, perRow = 2)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------- People

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
                            IconButton(onClick = { editing = draft.toMutableList().also { it.removeAt(i) } }) { Icon(Icons.Filled.Remove, contentDescription = "Take this place out") }
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

/** Home Assistant's zones and who is in each. Zones are drawn and moved on the map on the Manager dashboard. */
@Composable
fun LocationsPage(entities: Map<String, EntityState>) {
    val zones = zoneRows(entities)
    val neutral = toneColors(Tone.Grey)
    EntityCard(neutral.container, neutral.onContainer) {
        if (zones.isEmpty()) Text("No zones yet.", style = MaterialTheme.typography.bodyMedium)
        zones.forEach { z ->
            val tone = toneColors(if (z.home) Tone.Green else Tone.Teal)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ToneIconName(if (z.home) "mdi:home" else "mdi:map-marker-outline", Icons.Filled.Info, tone, 36)
                Column(Modifier.weight(1f)) {
                    Text(z.name, style = MaterialTheme.typography.bodyLarge)
                    Text(listOfNotNull(z.radius?.let { "$it m across" }, if (z.people.isEmpty()) null else z.people.joinToString(", ")).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    OpenInHomeAssistant("Edit zones on the map", "Drawing, moving and resizing zones is done on the map in Home Assistant.")
}

// ---------------------------------------------------------------------------------------------- Camera links

/** Which cameras record when each trigger fires, per alarm mode. */
@Composable
fun CameraLinksPage(entities: Map<String, EntityState>) {
    val view = rememberWs("church_drive/camera/links")
    Waiting(view, "camera links") { d ->
        val cameras = d.optJSONArray("cameras").objects().map { it.optString("base") to it.optString("name") }
        val modes = d.optJSONArray("modes")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: listOf("disarmed", "home", "away")
        val current = d.optString("mode")
        var mode by remember { mutableStateOf(current.takeIf { it in modes } ?: modes.first()) }
        val tone = toneColors(Tone.Blue)
        val neutral = toneColors(Tone.Grey)
        EntityCard(neutral.container, neutral.onContainer) {
            Text("Alarm mode", style = MaterialTheme.typography.titleMedium)
            Choices(modes.map { m -> TileItem(modeIcon(m), modeLabel(m) + if (m == current) " (now)" else "", m == mode) { mode = m } }, tone)
            Text("Choose what records in ${modeLabel(mode)}.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val links = linksOf(d.optJSONObject("links"), mode)
        links.forEach { link ->
            EntityCard(neutral.container, neutral.onContainer) {
                Text(entities[link.trigger]?.friendlyName ?: link.trigger, style = MaterialTheme.typography.titleMedium)
                Choices(cameras.map { (base, name) ->
                    val on = base in link.cams
                    TileItem("mdi:cctv", name, on) {
                        val next = if (on) link.cams - base else link.cams + base
                        view.send("church_drive/camera/links/set", JSONObject().put("mode", mode).put("trigger", link.trigger).put("cams", JSONArray(next)).put("secs", link.secs))
                    }
                }, tone, perRow = 2)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Records for", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = { view.send("church_drive/camera/links/set", JSONObject().put("mode", mode).put("trigger", link.trigger).put("cams", JSONArray(link.cams)).put("secs", (link.secs - 5).coerceAtLeast(5))) }) { Icon(Icons.Filled.Remove, contentDescription = "Shorter") }
                    Text("${link.secs} s", style = MaterialTheme.typography.bodyLarge)
                    IconButton(onClick = { view.send("church_drive/camera/links/set", JSONObject().put("mode", mode).put("trigger", link.trigger).put("cams", JSONArray(link.cams)).put("secs", (link.secs + 5).coerceAtMost(120))) }) { Icon(Icons.Filled.Add, contentDescription = "Longer") }
                }
                Choices(listOf(TileItem("mdi:delete-outline", "Take out of ${modeLabel(mode)}", false) {
                    view.send("church_drive/camera/links/set", JSONObject().put("mode", mode).put("trigger", link.trigger))
                }), tone, perRow = 1)
            }
        }
        val free = triggerChoices(entities, links.map { it.trigger }.toSet())
        if (free.isNotEmpty() && cameras.isNotEmpty()) {
            EntityCard(neutral.container, neutral.onContainer) {
                Text("Add a trigger", style = MaterialTheme.typography.titleMedium)
                Choices(free.take(40).map { e ->
                    TileItem("mdi:motion-sensor", e.friendlyName, false) {
                        view.send("church_drive/camera/links/set", JSONObject().put("mode", mode).put("trigger", e.entityId).put("cams", JSONArray(listOf(cameras.first().first))).put("secs", 30))
                    }
                }, tone, perRow = 2)
            }
        }
        val cooldown = d.optInt("cooldown", 120)
        EntityCard(neutral.container, neutral.onContainer) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Wait between recordings", style = MaterialTheme.typography.bodyLarge)
                    Text("A camera won't record again for this long", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { view.send("church_drive/camera/settings", data("cooldown" to (cooldown - 30).coerceAtLeast(30))) }) { Icon(Icons.Filled.Remove, contentDescription = "Shorter") }
                Text("$cooldown s", style = MaterialTheme.typography.bodyLarge)
                IconButton(onClick = { view.send("church_drive/camera/settings", data("cooldown" to (cooldown + 30).coerceAtMost(3600))) }) { Icon(Icons.Filled.Add, contentDescription = "Longer") }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------- Icons

/** The icons the cards and the app use for modes, by group: tap one to change it, or put the built-in one back. */
@Composable
fun IconStylesPage() {
    val view = rememberWs("church_drive/icons")
    Waiting(view, "icons") { d ->
        val icons = d.optJSONObject("icons") ?: JSONObject()
        val defaults = d.optJSONObject("defaults") ?: JSONObject()
        val names = d.optJSONObject("groups") ?: JSONObject()
        val tone = toneColors(Tone.Purple)
        var open by remember { mutableStateOf<Pair<String, String>?>(null) }
        var text by remember { mutableStateOf("") }
        val neutral = toneColors(Tone.Grey)
        icons.keys().asSequence().toList().forEach { group ->
            val slots = icons.optJSONObject(group) ?: return@forEach
            EntityCard(neutral.container, neutral.onContainer) {
                Text(names.optString(group).ifBlank { presetLabel(group) }, style = MaterialTheme.typography.titleMedium)
                slots.keys().asSequence().toList().forEach { key ->
                    val icon = slots.optString(key)
                    val isOpen = open == group to key
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ToneIconName(icon, Icons.Filled.Info, tone, 36)
                            Column(Modifier.weight(1f)) {
                                Text(presetLabel(key), style = MaterialTheme.typography.bodyLarge)
                                Text(icon, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Box(Modifier.width(120.dp)) {
                                Choices(listOf(TileItem(if (isOpen) "mdi:close" else "mdi:pencil", if (isOpen) "Close" else "Change", isOpen) {
                                    if (isOpen) open = null else { open = group to key; text = icon }
                                }), tone, perRow = 1)
                            }
                        }
                        if (isOpen) {
                            OutlinedTextField(text, { text = it }, singleLine = true, label = { Text("Icon, like mdi:fan") }, isError = text.isNotBlank() && !validIconName(text), modifier = Modifier.fillMaxWidth())
                            Choices(listOf(
                                TileItem("mdi:check", "Save", validIconName(text)) {
                                    if (validIconName(text)) {
                                        view.send("church_drive/icons/set", data("group" to group, "key" to key, "icon" to text.trim()))
                                        open = null
                                    }
                                },
                                TileItem("mdi:restore", "Built-in", false) {
                                    view.send("church_drive/icons/set", JSONObject().put("group", group).put("key", key))
                                    open = null
                                },
                            ), tone, perRow = 2)
                            Text("Built-in is ${defaults.optJSONObject(group)?.optString(key).orEmpty()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
