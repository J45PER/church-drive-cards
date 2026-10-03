package com.churchdrive.app.ui

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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

private fun JSONArray?.objects(): List<JSONObject> = (0 until (this?.length() ?: 0)).mapNotNull { this?.optJSONObject(it) }

// ---------------------------------------------------------------------------------------------- Notifications

/** Who gets each kind of notification, and which of each person's phones. */
@OptIn(ExperimentalLayoutApi::class)
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
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                val everyone = kind.optBoolean("all")
                                ChoiceButton("Everyone", everyone, purple, purple.onContainer) {
                                    view.send("church_drive/people/assign", data("kind" to key, "on" to !everyone))
                                }
                                people.forEach { p ->
                                    val id = p.optString("entity_id")
                                    ChoiceButton(p.optString("first").ifBlank { p.optString("name") }, kindGoesTo(kind, id), purple, purple.onContainer, enabled = !everyone && canTick(kind, p)) {
                                        view.send("church_drive/people/assign", data("kind" to key, "person" to id, "on" to !kindGoesTo(kind, id)))
                                    }
                                }
                            }
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
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        phones.forEach { ph ->
                            ChoiceButton(ph.optString("name"), ph.optBoolean("on"), purple, purple.onContainer) {
                                view.send("church_drive/people/phone", data("person" to p.optString("entity_id"), "service" to ph.optString("service"), "on" to !ph.optBoolean("on")))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------- People

/** Each person: their phones (switch alerts on and off) and their places, with the names they give them. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PeoplePage(entities: Map<String, EntityState>) {
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
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    phones.forEach { ph ->
                        ChoiceButton(ph.optString("name"), ph.optBoolean("on"), teal, teal.onContainer) {
                            view.send("church_drive/people/phone", data("person" to id, "service" to ph.optString("service"), "on" to !ph.optBoolean("on")))
                        }
                    }
                }
                Text("Places", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val draft = editing
                if (draft == null) {
                    if (places.isEmpty()) Text("Home is automatic. Add the places ${p.optString("first")} goes to.", style = MaterialTheme.typography.bodySmall)
                    places.forEach { (zone, name) ->
                        Text("${entities[zone]?.friendlyName ?: zone} · $name", style = MaterialTheme.typography.bodyMedium)
                    }
                    ChoiceButton("Edit places", false, teal, teal.onContainer) { editing = places }
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
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            free.forEach { z -> ChoiceButton(z.friendlyName, false, teal, teal.onContainer) { editing = draft + (z.entityId to z.friendlyName) } }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChoiceButton("Save", true, teal, teal.onContainer) {
                            val list = JSONArray().also { a -> draft.forEach { (z, n) -> a.put(JSONObject().put("zone", z).put("name", n.trim())) } }
                            view.send("church_drive/people/places", JSONObject().put("person", id).put("places", list))
                            editing = null
                        }
                        ChoiceButton("Cancel", false, teal, teal.onContainer) { editing = null }
                    }
                }
            }
        }
        ManagerNote("Pictures, cars and each person's own alerts are changed on the Manager dashboard in Home Assistant for now. Who gets what is on the Notifications page.")
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
    ManagerNote("Drawing, moving and resizing zones on the map is on the Manager dashboard in Home Assistant for now.")
}

// ---------------------------------------------------------------------------------------------- Camera links

/** Which cameras record when each trigger fires, per alarm mode. */
@OptIn(ExperimentalLayoutApi::class)
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
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                modes.forEach { m -> ChoiceButton(modeLabel(m) + if (m == current) " (now)" else "", m == mode, tone, tone.onContainer) { mode = m } }
            }
            Text("Choose what records in ${modeLabel(mode)}.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val links = linksOf(d.optJSONObject("links"), mode)
        links.forEach { link ->
            EntityCard(neutral.container, neutral.onContainer) {
                Text(entities[link.trigger]?.friendlyName ?: link.trigger, style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    cameras.forEach { (base, name) ->
                        val on = base in link.cams
                        ChoiceButton(name, on, tone, tone.onContainer) {
                            val next = if (on) link.cams - base else link.cams + base
                            view.send("church_drive/camera/links/set", JSONObject().put("mode", mode).put("trigger", link.trigger).put("cams", JSONArray(next)).put("secs", link.secs))
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Records for", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = { view.send("church_drive/camera/links/set", JSONObject().put("mode", mode).put("trigger", link.trigger).put("cams", JSONArray(link.cams)).put("secs", (link.secs - 5).coerceAtLeast(5))) }) { Icon(Icons.Filled.Remove, contentDescription = "Shorter") }
                    Text("${link.secs} s", style = MaterialTheme.typography.bodyLarge)
                    IconButton(onClick = { view.send("church_drive/camera/links/set", JSONObject().put("mode", mode).put("trigger", link.trigger).put("cams", JSONArray(link.cams)).put("secs", (link.secs + 5).coerceAtMost(120))) }) { Icon(Icons.Filled.Add, contentDescription = "Longer") }
                }
                ChoiceButton("Take out of ${modeLabel(mode)}", false, tone, tone.onContainer) {
                    view.send("church_drive/camera/links/set", JSONObject().put("mode", mode).put("trigger", link.trigger))
                }
            }
        }
        val free = triggerChoices(entities, links.map { it.trigger }.toSet())
        if (free.isNotEmpty() && cameras.isNotEmpty()) {
            EntityCard(neutral.container, neutral.onContainer) {
                Text("Add a trigger", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    free.take(40).forEach { e ->
                        ChoiceButton(e.friendlyName, false, tone, tone.onContainer) {
                            view.send("church_drive/camera/links/set", JSONObject().put("mode", mode).put("trigger", e.entityId).put("cams", JSONArray(listOf(cameras.first().first))).put("secs", 30))
                        }
                    }
                }
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
                            ChoiceButton(if (isOpen) "Close" else "Change", isOpen, tone, tone.onContainer) {
                                if (isOpen) open = null else { open = group to key; text = icon }
                            }
                        }
                        if (isOpen) {
                            OutlinedTextField(text, { text = it }, singleLine = true, label = { Text("Icon, like mdi:fan") }, isError = text.isNotBlank() && !validIconName(text), modifier = Modifier.fillMaxWidth())
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ChoiceButton("Save", true, tone, tone.onContainer, enabled = validIconName(text)) {
                                    view.send("church_drive/icons/set", data("group" to group, "key" to key, "icon" to text.trim()))
                                    open = null
                                }
                                ChoiceButton("Built-in (${defaults.optJSONObject(group)?.optString(key).orEmpty()})", false, tone, tone.onContainer) {
                                    view.send("church_drive/icons/set", JSONObject().put("group", group).put("key", key))
                                    open = null
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
