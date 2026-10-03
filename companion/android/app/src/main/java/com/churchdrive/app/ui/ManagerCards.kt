package com.churchdrive.app.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.churchdrive.app.ha.CallService
import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ha.Registry
import com.churchdrive.app.ha.data
import org.json.JSONArray
import org.json.JSONObject

// The cards of the Manager dashboard that an administrator sees in the app: each of its panels is a page in the account panel.

// ---------------------------------------------------------------------------------------------- Pure bits

/** One person for the Who's home card. */
data class PersonRow(val id: String, val name: String, val state: String, val place: String?, val zone: String?, val picture: String?, val phones: List<Pair<String, Int?>>)

/** The word and colour for where a person is: Home, a named place (At work · School), Away or Unknown. */
fun presencePill(state: String, place: String?, zone: String?): Pair<String, Tone> = when {
    state == "home" -> "Home" to Tone.Green
    !place.isNullOrBlank() && !zone.isNullOrBlank() -> "${presetLabel(place)} · $zone" to Tone.Teal
    state == "not_home" -> "Away" to Tone.Grey
    state == "unknown" || state == "unavailable" -> "Unknown" to Tone.Amber
    else -> presetLabel(state) to Tone.Teal
}

/** Everyone Home Assistant has as a person, by name, with their phones (companion app devices) and each phone's battery. */
fun peopleRows(entities: Map<String, EntityState>, registry: Registry): List<PersonRow> {
    val known = (entities["sensor.church_drive_people"]?.attributes?.optJSONArray("people")) ?: JSONArray()
    fun knownFor(id: String): JSONObject? = (0 until known.length()).mapNotNull { known.optJSONObject(it) }.firstOrNull { it.optString("entity_id") == id }
    return entities.values.filter { it.entityId.startsWith("person.") }.sortedBy { it.friendlyName }.map { person ->
        val trackers = person.list("device_trackers")
        val phones = trackers.mapNotNull { t ->
            val device = registry.deviceName(t) ?: return@mapNotNull null
            val battery = registry.siblings(t).firstOrNull { it.startsWith("sensor.") && it.endsWith("battery_level") }
                ?.let { entities[it]?.state?.toDoubleOrNull() }?.let { Math.round(it).toInt() }
            device to battery
        }
        val k = knownFor(person.entityId)
        PersonRow(person.entityId, person.friendlyName, person.state, k?.optString("place")?.takeIf { it.isNotBlank() }, k?.optString("zone")?.takeIf { it.isNotBlank() }, person.str("entity_picture"), phones)
    }
}

/** A watched device for the Device Health card. */
data class WatchedDevice(val id: String, val name: String, val ok: Boolean, val reason: String, val lastHeard: String, val usual: String, val fixes: List<String>)

fun heardAgo(iso: String?, now: Long = System.currentTimeMillis()): String {
    val at = parseMillis(iso) ?: return "not heard yet"
    val minutes = Math.round((now - at) / 60_000.0)
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        else -> Math.round(minutes / 60.0).let { h -> if (h < 48) "$h h ago" else "${Math.round(h / 24.0)} days ago" }
    }
}

fun everyText(seconds: Double?): String = when {
    seconds == null || seconds <= 0 -> ""
    seconds < 90 -> "every ${Math.round(seconds)} s"
    seconds < 5400 -> "every ${Math.round(seconds / 60)} min"
    else -> "every ${Math.round(seconds / 3600)} h"
}

/** The devices Church Drive watches (`sensor.church_drive_device_health`), those needing attention first. */
fun watchedDevices(entities: Map<String, EntityState>, now: Long = System.currentTimeMillis()): List<WatchedDevice> {
    val devices = entities["sensor.church_drive_device_health"]?.attributes?.optJSONObject("devices") ?: return emptyList()
    return devices.keys().asSequence().map { id ->
        val d = devices.optJSONObject(id) ?: JSONObject()
        val fixes = d.optJSONArray("fixes")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty()
        WatchedDevice(id, d.optString("name").ifBlank { id }, d.optString("status") == "ok", d.optString("reason"), heardAgo(d.optString("last_heard").takeIf { it.isNotBlank() }, now), everyText(d.optDouble("usual_gap").takeIf { !it.isNaN() }), fixes)
    }.sortedWith(compareBy({ it.ok }, { it.name })).toList()
}

/** A person's to-do list for the Automatic to-dos panel: each person Church Drive knows who has a list. */
fun peopleLists(entities: Map<String, EntityState>): List<Pair<String, String>> {
    val people = entities["sensor.church_drive_people"]?.attributes?.optJSONArray("people") ?: return emptyList()
    return (0 until people.length()).mapNotNull { people.optJSONObject(it) }.mapNotNull { p ->
        val list = p.optString("list")
        val first = p.optString("first").ifBlank { p.optString("name") }
        if (list.isBlank() || entities[list]?.state.let { it == null || it == "unknown" || it == "unavailable" }) null else first to list
    }
}

/** Plain text of a markdown line: the stars and backticks of bold, italic and code taken out. */
fun plainMarkdown(text: String): String = text.replace("**", "").replace("__", "").replace("`", "").replace(Regex("(?<!\\w)[*_](\\S.*?\\S|\\S)[*_](?!\\w)"), "$1")

// ---------------------------------------------------------------------------------------------- Cards

/** The Who's home card: each person, where they are, their phones and the phones' batteries. */
@Composable
fun PeopleStatusCard(entities: Map<String, EntityState>, registry: Registry) {
    val rows = peopleRows(entities, registry)
    val neutral = toneColors(Tone.Grey)
    EntityCard(neutral.container, neutral.onContainer) {
        if (rows.isEmpty()) Text("No people set up yet.", style = MaterialTheme.typography.bodyMedium)
        rows.forEach { p ->
            val (word, tone) = presencePill(p.state, p.place, p.zone)
            val colours = toneColors(tone)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(40.dp).background(colours.accent.copy(alpha = 0.18f), CircleShape), contentAlignment = Alignment.Center) {
                    Text(p.name.trim().firstOrNull()?.uppercase() ?: "?", color = colours.accent, style = MaterialTheme.typography.titleMedium)
                }
                Column(Modifier.weight(1f)) {
                    Text(p.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                    val phones = if (p.phones.isEmpty()) "No companion app"
                    else p.phones.joinToString(" · ") { (n, b) -> n + (b?.let { " $it%" } ?: "") } + if (p.state == "unknown") " · location not shared" else ""
                    Text(phones, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                }
                Text(word, color = colours.accent, style = MaterialTheme.typography.labelLarge, maxLines = 1,
                    modifier = Modifier.background(colours.accent.copy(alpha = 0.18f), CircleShape).padding(horizontal = 10.dp, vertical = 4.dp))
            }
        }
    }
}

/** The Device Health card: each watched device, responding or stale, when it was last heard from, and a Fix now button. */
@Composable
fun DeviceHealthCard(config: JSONObject, entities: Map<String, EntityState>, call: CallService) {
    val devices = watchedDevices(entities)
    val showOk = if (config.has("show_ok")) config.optBoolean("show_ok", true) else true
    val shown = devices.filter { showOk || !it.ok }
    val neutral = toneColors(Tone.Grey)
    EntityCard(neutral.container, neutral.onContainer) {
        config.optString("title").takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
        if (shown.isEmpty()) Text(if (devices.isEmpty()) "No devices are being watched yet." else "Every device is responding.", style = MaterialTheme.typography.bodyMedium)
        shown.forEach { d ->
            val tone = toneColors(if (d.ok) Tone.Green else Tone.Amber)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ToneIconName(if (d.ok) "mdi:check-circle-outline" else "mdi:alert-circle-outline", Icons.Filled.Info, tone, 36)
                    Column(Modifier.weight(1f)) {
                        Text(d.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                        Text(listOf("Heard ${d.lastHeard}", d.usual).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (!d.ok) {
                    if (d.reason.isNotBlank()) Text(d.reason, style = MaterialTheme.typography.bodySmall)
                    Text(if (d.fixes.isEmpty()) "Fixing automatically…" else "Tried: ${d.fixes.joinToString(" · ")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TileRow(listOf(TileItem("mdi:wrench", "Fix now", false) { call("church_drive", "health_fix", d.id, data("action" to if (d.id.startsWith("fan.")) "nudge" else "resync")) }), tone, tone.onContainer)
                }
            }
        }
    }
}

/** A subtitle-style heading card. */
@Composable
fun HeadingCard(config: JSONObject) {
    val text = config.optString("heading").ifBlank { config.optString("title") }
    if (text.isBlank()) return
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        config.optString("icon").takeIf { it.isNotBlank() }?.let { HaIcon(it, Icons.Filled.Info, MaterialTheme.colorScheme.primary, 22.dp) }
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

/** A markdown card as plain text. */
@Composable
fun MarkdownCard(config: JSONObject) {
    val neutral = toneColors(Tone.Grey)
    EntityCard(neutral.container, neutral.onContainer) {
        Text(plainMarkdown(config.optString("content")), style = MaterialTheme.typography.bodyMedium)
    }
}

/** A vertical stack: its cards one under another. */
@Composable
fun StackCard(config: JSONObject, entities: Map<String, EntityState>, registry: Registry, call: CallService) {
    val cards = config.optJSONArray("cards") ?: return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        for (i in 0 until cards.length()) {
            val c = cards.optJSONObject(i) ?: continue
            CardView(CardSpec(c.optString("type"), c), entities, registry, call)
        }
    }
}

/** The Automatic to-dos panel's generated cards: a to-do list for each person Church Drive knows. */
@Composable
fun PeopleListsCard(entities: Map<String, EntityState>, call: CallService) {
    peopleLists(entities).forEach { (first, list) ->
        TaskListCard(JSONObject().put("entity", list).put("title", first), entities, call)
    }
}

/** A plain list of entities where an input (a choice, a time, a number) can be changed in place; other rows just show their value. */
@Composable
fun SettingsEntitiesCard(config: JSONObject, entities: Map<String, EntityState>, call: CallService) {
    val neutral = toneColors(Tone.Grey)
    val rows = config.optJSONArray("entities")
    EntityCard(neutral.container, neutral.onContainer) {
        config.optString("title").takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
        for (i in 0 until (rows?.length() ?: 0)) {
            val raw = rows?.opt(i)
            val spec = raw as? JSONObject ?: JSONObject().put("entity", raw?.toString().orEmpty())
            val id = spec.optString("entity")
            val entity = entities[id]
            val name = spec.optString("name").ifBlank { entity?.friendlyName ?: id }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                when (id.substringBefore('.')) {
                    "input_select" -> if (entity != null) SelectInput(entity, call)
                    "input_number" -> if (entity != null) NumberInput(entity, call)
                    "input_datetime" -> if (entity != null) TimeInput(entity, call)
                    else -> Text(entity?.let { presetLabel(it.state) + (it.str("unit_of_measurement")?.let { u -> " $u" } ?: "") } ?: "–", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun SelectInput(entity: EntityState, call: CallService) {
    var open by remember { mutableStateOf(false) }
    Box {
        Text(entity.state, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.clip(CircleShape).clickable { open = true }.padding(horizontal = 12.dp, vertical = 6.dp))
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            entity.options().forEach { o ->
                DropdownMenuItem(text = { Text(o) }, onClick = { open = false; call("input_select", "select_option", entity.entityId, data("option" to o)) })
            }
        }
    }
}

@Composable
private fun NumberInput(entity: EntityState, call: CallService) {
    val step = entity.num("step") ?: 1.0
    val min = entity.num("min") ?: Double.NEGATIVE_INFINITY
    val max = entity.num("max") ?: Double.POSITIVE_INFINITY
    val value = entity.state.toDoubleOrNull()
    fun set(v: Double) = call("input_number", "set_value", entity.entityId, data("value" to v.coerceIn(min, max)))
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { value?.let { set(it - step) } }) { Icon(Icons.Filled.Remove, contentDescription = "Less") }
        Text(value?.let { if (it % 1.0 == 0.0) it.toInt().toString() else "%.1f".format(it) } ?: "–", style = MaterialTheme.typography.bodyLarge)
        IconButton(onClick = { value?.let { set(it + step) } }) { Icon(Icons.Filled.Add, contentDescription = "More") }
    }
}

@Composable
private fun TimeInput(entity: EntityState, call: CallService) {
    val context = LocalContext.current
    val time = entity.state.take(5)
    Text(time, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.clip(CircleShape).clickable {
            val hour = time.substringBefore(':').toIntOrNull() ?: 0
            val minute = time.substringAfter(':', "0").toIntOrNull() ?: 0
            TimePickerDialog(context, { _, h, m -> call("input_datetime", "set_datetime", entity.entityId, data("time" to "%02d:%02d:00".format(h, m))) }, hour, minute, true).show()
        }.padding(horizontal = 12.dp, vertical = 6.dp))
}
