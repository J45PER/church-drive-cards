package com.churchdrive.app.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.churchdrive.app.ha.CallService
import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ha.data
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** The camera links of one alarm mode: trigger entity ids with their cameras and recording seconds. */
data class CameraLink(val trigger: String, val cams: List<String>, val secs: Int)

/** Which cameras record when each trigger fires, per alarm mode. */
@Composable
fun CameraLinksPage(entities: Map<String, EntityState>) {
    val view = rememberWs("church_drive/camera/links")
    Waiting(view, "camera links") { d ->
        val cameras = d.optJSONArray("cameras").objects().map { it.optString("base") to it.optString("name") }
        val modes = d.optJSONArray("modes")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: listOf("disarmed", "home", "away")
        val current = d.optString("mode")
        var mode by remember { mutableStateOf(current.takeIf { it in modes } ?: modes.first()) }
        val tone = toneColors(Tone.Alarm)
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
                    IconButton(onClick = { view.send("church_drive/camera/links/set", JSONObject().put("mode", mode).put("trigger", link.trigger).put("cams", JSONArray(link.cams)).put("secs", (link.secs - 5).coerceAtLeast(5))) }) { Icon(CdVectors.Minus, contentDescription = "Shorter") }
                    Text("${link.secs} s", style = MaterialTheme.typography.bodyLarge)
                    IconButton(onClick = { view.send("church_drive/camera/links/set", JSONObject().put("mode", mode).put("trigger", link.trigger).put("cams", JSONArray(link.cams)).put("secs", (link.secs + 5).coerceAtMost(120))) }) { Icon(CdVectors.Plus, contentDescription = "Longer") }
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
                IconButton(onClick = { view.send("church_drive/camera/settings", data("cooldown" to (cooldown - 30).coerceAtLeast(30))) }) { Icon(CdVectors.Minus, contentDescription = "Shorter") }
                Text("$cooldown s", style = MaterialTheme.typography.bodyLarge)
                IconButton(onClick = { view.send("church_drive/camera/settings", data("cooldown" to (cooldown + 30).coerceAtMost(3600))) }) { Icon(CdVectors.Plus, contentDescription = "Longer") }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------- Icons

/** A group of choices as the kit's tiles: the ones in [on] selected. */
@Composable
fun Choices(items: List<TileItem>, tone: ToneColors, perRow: Int = 3, enabled: Boolean = true) =
    TileRow(items, tone, tone.onContainer, enabled, perRow)

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
                            ToneIconName(icon, CdVectors.Info, tone, 36)
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
                ToneIconName(if (z.home) "mdi:home" else "mdi:map-marker-outline", CdVectors.Info, tone, 36)
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

@Composable
fun ManagerNote(text: String) {
    val neutral = toneColors(Tone.Grey)
    EntityCard(neutral.container, neutral.onContainer) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

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

/** A card with a button that opens the Manager page in Home Assistant, for what the app doesn't do itself. */
@Composable
fun OpenInHomeAssistant(label: String, why: String, path: String = "dashboard-manager/system") {
    val base = LocalBaseUrl.current
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

@Composable
fun Waiting(view: WsView, what: String, body: @Composable (JSONObject) -> Unit) {
    val d = view.data
    when {
        d != null -> body(d)
        view.failed -> ManagerNote("$what couldn't be read from the house. Church Drive may not be running, or the connection dropped.")
        else -> LoadingNote("Loading $what…")
    }
}

/** An integration command's answer, kept; [send] runs a change and then asks again. Null [data] until the first answer. */
class WsView(val data: JSONObject?, val failed: Boolean, val send: (String, JSONObject) -> Unit)

/** A zone for the Locations page: its name, radius and the people in it. */
data class ZoneRow(val id: String, val name: String, val radius: Int?, val people: List<String>, val home: Boolean)

/** Whether a person may be ticked for a kind: costs and the like are for administrators only. */
fun canTick(kind: JSONObject, person: JSONObject): Boolean = !kind.optBoolean("admin_only") || person.optBoolean("admin")

/** Whether a kind goes to a person: everyone, or ticked. */
fun kindGoesTo(kind: JSONObject, personId: String): Boolean {
    if (kind.optBoolean("all")) return true
    val people = kind.optJSONArray("people") ?: return false
    return (0 until people.length()).any { people.optString(it) == personId }
}

/** The notification kinds by group, in the order the integration sends them. */
fun kindGroups(kinds: JSONArray?): List<Pair<String, List<JSONObject>>> {
    val all = (0 until (kinds?.length() ?: 0)).mapNotNull { kinds?.optJSONObject(it) }
    return all.map { it.optString("group") }.distinct().map { g -> g to all.filter { it.optString("group") == g } }
}

fun linksOf(links: JSONObject?, mode: String): List<CameraLink> {
    val table = links?.optJSONObject(mode) ?: return emptyList()
    return table.keys().asSequence().map { t ->
        val rule = table.optJSONObject(t) ?: JSONObject()
        val cams = rule.optJSONArray("cams")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty()
        CameraLink(t, cams, rule.optInt("secs", 30))
    }.sortedBy { it.trigger }.toList()
}

fun modeIcon(mode: String): String = when (mode) {
    "disarmed" -> "mdi:shield-off-outline"
    "home" -> "mdi:shield-home-outline"
    "away" -> "mdi:shield-lock-outline"
    else -> "mdi:shield-outline"
}

/** What a mode is called. */
fun modeLabel(mode: String): String = when (mode) {
    "disarmed" -> "Disarmed"
    "home" -> "Home"
    "away" -> "Away"
    else -> presetLabel(mode)
}

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

/** Things that can start a recording: movement, doors, doorbell and the like. */
fun triggerChoices(entities: Map<String, EntityState>, taken: Set<String>): List<EntityState> {
    val classes = setOf("motion", "occupancy", "door", "window", "opening", "garage_door", "sound", "vibration", "moving", "presence", "running")
    return entities.values.filter { it.entityId.startsWith("binary_sensor.") && it.str("device_class") in classes && it.entityId !in taken }.sortedBy { it.friendlyName }
}

/** An icon name Home Assistant accepts: `mdi:` and lower-case words with dashes. */
fun validIconName(name: String): Boolean = Regex("^mdi:[a-z0-9]+(-[a-z0-9]+)*$").matches(name.trim())

fun zoneRows(entities: Map<String, EntityState>): List<ZoneRow> =
    entities.values.filter { it.entityId.startsWith("zone.") }.map { z ->
        val inside = entities.values.filter { it.entityId.startsWith("person.") && (it.state == z.friendlyName || (z.entityId == "zone.home" && it.state == "home")) }.map { it.friendlyName }
        ZoneRow(z.entityId, z.friendlyName, z.num("radius")?.toInt(), inside.sorted(), z.entityId == "zone.home")
    }.sortedWith(compareBy({ !it.home }, { it.name }))

// ---------------------------------------------------------------------------------------------- Loading and sending

/** A subtitle-style heading card. */
@Composable
fun HeadingCard(config: JSONObject) {
    val text = config.optString("heading").ifBlank { config.optString("title") }
    if (text.isBlank()) return
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        config.optString("icon").takeIf { it.isNotBlank() }?.let { HaIcon(it, CdVectors.Info, MaterialTheme.colorScheme.primary, 22.dp) }
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

/** A markdown card as plain text. */
@Composable
fun MarkdownCard(config: JSONObject) {
    val neutral = panelNeutral()
    EntityCard(neutral.container, neutral.onContainer) {
        Text(plainMarkdown(config.optString("content")), style = MaterialTheme.typography.bodyMedium)
    }
}

/** A number with what it counts, so a setting reads "30 %", "24 hours" or "60 mins": the unit is the helper's own, spelled out when short. */
fun numberWithUnit(value: Double, unit: String?): String {
    val n = if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value)
    val u = unit?.trim().orEmpty()
    if (u.isEmpty()) return n
    val one = value == 1.0
    val word = when (u.lowercase()) {
        "s", "sec", "secs" -> if (one) "sec" else "secs"
        "min", "mins" -> if (one) "min" else "mins"
        "h", "hr", "hrs" -> if (one) "hour" else "hours"
        "d" -> if (one) "day" else "days"
        else -> u
    }
    return "$n $word"
}

@Composable
fun NumberInput(entity: EntityState, call: CallService) {
    val step = entity.num("step") ?: 1.0
    val min = entity.num("min") ?: Double.NEGATIVE_INFINITY
    val max = entity.num("max") ?: Double.POSITIVE_INFINITY
    val value = entity.state.toDoubleOrNull()
    fun set(v: Double) = call("input_number", "set_value", entity.entityId, data("value" to v.coerceIn(min, max)))
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { value?.let { set(it - step) } }) { Icon(CdVectors.Minus, contentDescription = "Less") }
        Text(
            value?.let { numberWithUnit(it, entity.str("unit_of_measurement")) } ?: "–",
            style = MaterialTheme.typography.bodyLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.widthIn(min = 72.dp),
        )
        IconButton(onClick = { value?.let { set(it + step) } }) { Icon(CdVectors.Plus, contentDescription = "More") }
    }
}

@Composable
fun SelectInput(entity: EntityState, call: CallService) {
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

/** A plain list of entities where an input (a choice, a time, a number) can be changed in place; other rows just show their value. */
@Composable
fun SettingsEntitiesCard(config: JSONObject, entities: Map<String, EntityState>, call: CallService) {
    val neutral = panelNeutral()
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
                    "input_boolean" -> if (entity != null) {
                        val on = entity.state == "on"
                        Switch(checked = on, enabled = entity.available, onCheckedChange = { call("input_boolean", if (on) "turn_off" else "turn_on", entity.entityId, data()) })
                    }
                    else -> Text(entity?.let { presetLabel(it.state) + (it.str("unit_of_measurement")?.let { u -> " $u" } ?: "") } ?: "–", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun TimeInput(entity: EntityState, call: CallService) {
    val context = LocalContext.current
    val time = entity.state.take(5)
    Text(time, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.clip(CircleShape).clickable {
            val hour = time.substringBefore(':').toIntOrNull() ?: 0
            val minute = time.substringAfter(':', "0").toIntOrNull() ?: 0
            TimePickerDialog(context, { _, h, m -> call("input_datetime", "set_datetime", entity.entityId, data("time" to "%02d:%02d:00".format(h, m))) }, hour, minute, true).show()
        }.padding(horizontal = 12.dp, vertical = 6.dp))
}

/** Plain text of a markdown line: the stars and backticks of bold, italic and code taken out. */
fun plainMarkdown(text: String): String = text.replace("**", "").replace("__", "").replace("`", "").replace(Regex("(?<!\\w)[*_](\\S.*?\\S|\\S)[*_](?!\\w)"), "$1")

// ---------------------------------------------------------------------------------------------- Cards

/**
 * The dashboard's `custom:house-tasks-card`: the jobs the house has spotted (a low battery, a filter due), which clear
 * themselves once sorted. It shows the signed-in person's and everyone's.
 */
@Composable
fun HouseTasksCard(config: JSONObject, entities: Map<String, EntityState>) {
    val me = LocalUserName.current
    val first = me?.trim()?.substringBefore(' ')
    val listId = config.optString("entity").ifBlank { "todo.priorities_automatic" }
    val showAll = config.optString("show") == "all"
    val tone = toneColors(Tone.TodoHouse)
    val items = rememberTodoItems(listId, entities)
    EntityCard(tone.container, tone.onContainer) {
        val tasks = sortedHouseTasks(items.orEmpty().filter { !it.done }.map { it to houseTask(it) }.filter { showAll || houseTaskFor(it.second, first) })
        when {
            items == null -> Text("Loading…", style = MaterialTheme.typography.bodyMedium)
            tasks.isEmpty() -> Text("Nothing from the house right now.", style = MaterialTheme.typography.bodyMedium)
        }
        tasks.forEachIndexed { i, (item, task) ->
            // With everyone's jobs shown, each person's (and Everyone's) come under their own name.
            if (showAll && (i == 0 || tasks[i - 1].second.who != task.who)) {
                Text(task.who, style = MaterialTheme.typography.labelLarge, color = tone.onContainer.copy(alpha = 0.75f))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ToneIconName(task.icon, CdVectors.Info, tone)
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.summary, style = MaterialTheme.typography.bodyLarge)
                    val sub = listOf(task.kind, task.detail).filter { it.isNotBlank() }.joinToString(" · ")
                    if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, color = tone.onContainer.copy(alpha = 0.75f))
                }
            }
        }
    }
}

/** A to-do list's items, kept up to date: fetched again whenever the list's count changes. Null until they arrive. */
@Composable
fun rememberTodoItems(listId: String, entities: Map<String, EntityState>): List<TodoItem>? {
    val api = LocalHaApi.current
    var items by remember(listId) { mutableStateOf<List<TodoItem>?>(null) }
    val count = entities[listId]?.state
    LaunchedEffect(listId, count) {
        if (api != null && listId.isNotBlank()) {
            // A failed or slow answer is asked for again (a few times); it is not taken for an empty list.
            var tries = 0
            while (tries < 5) {
                val result = api.ask("todo/item/list", data("entity_id" to listId))
                if (result != null) {
                    items = parseTodoItems(result)
                    break
                }
                tries++
                kotlinx.coroutines.delay(2_000L * tries)
            }
        }
    }
    return items
}

/** Home Assistant's address, for pictures and video (they're fetched with a token in the link). Set in MainActivity. */
val LocalBaseUrl = compositionLocalOf { "" }

/** When each camera last had a snapshot asked for from here, so the app doesn't ask again straight away. */

@Composable
fun Tile(item: TileItem, tone: ToneColors, content: Color, enabled: Boolean, column: Boolean, modifier: Modifier) {
    val ink = (if (item.selected) tone.onAccent else content).copy(alpha = if (enabled) 1f else 0.4f)
    Box(
        modifier = modifier
            .height(Ui.TileHeight)
            .clip(Ui.TileShape)
            .background(if (item.selected) tone.accent else content.copy(alpha = 0.10f), Ui.TileShape)
            .clickable(enabled = enabled, onClickLabel = item.label, onClick = item.onClick)
            .semantics { contentDescription = item.label },
        contentAlignment = Alignment.Center,
    ) {
        if (column) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                HaIcon(item.icon, CdVectors.Info, ink, Ui.TileIcon)
                if (item.showLabel) CentredText(item.label, ink, Ui.TileTextStacked, Modifier.padding(horizontal = 4.dp))
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                HaIcon(item.icon, CdVectors.Info, ink, Ui.TileIcon)
                if (item.showLabel) CentredText(item.label, ink, Ui.TileText, Modifier.padding(end = 2.dp))
            }
        }
    }
}

/** One tile: an icon and a label (as on the dashboard's cards), filled when it's the current choice. */
data class TileItem(val icon: String, val label: String, val selected: Boolean, val showLabel: Boolean = true, val onClick: () -> Unit)

/**
 * Tiles in rows of up to [perRow], shared out evenly by [tileRowSizes] and, as on the dashboard, going onto more rows
 * rather than scrolling. Every row fills the width: a short last row's tiles are wider, not left with a gap.
 * [column] puts the label under the icon. All tiles are the same height ([Ui.TileHeight]).
 */
@Composable
fun TileRow(items: List<TileItem>, tone: ToneColors, content: Color, enabled: Boolean = true, perRow: Int = Ui.TilesPerRow, column: Boolean = false) {
    if (items.isEmpty()) return
    var next = 0
    Column(verticalArrangement = Arrangement.spacedBy(Ui.TileGap)) {
        tileRowSizes(items.size, perRow).forEach { size ->
            val row = items.subList(next, next + size)
            next += size
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Ui.TileGap)) {
                row.forEach { Tile(it, tone, content, enabled, column, Modifier.weight(1f)) }
            }
        }
    }
}

/** "speed_1" as "Speed 1". */
fun presetLabel(value: String): String = value.replace('_', ' ').trim().replaceFirstChar { it.uppercase() }

/** The comfortable range of a room by its type, in °C, as on the dashboard's Climate panel. */

private fun JSONArray?.objects(): List<JSONObject> = (0 until (this?.length() ?: 0)).mapNotNull { this?.optJSONObject(it) }
