package com.churchdrive.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.churchdrive.app.ha.CallService
import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ha.Registry
import com.churchdrive.app.ha.data
import com.churchdrive.app.ha.text
import org.json.JSONArray
import org.json.JSONObject

val WHITE = arrayOf("bright", "dimmed", "relax", "nightlight")

val SCENE_NAMES = mapOf(
    "bright" to "Bright", "dimmed" to "Dimmed", "relax" to "Relax", "nightlight" to "Nightlight",
    "cool_bright" to "Cool bright", "energise" to "Energise", "cyber_fidelity" to "Cyber fidelity",
    "lake_placid" to "Lake Placid", "emerald_isle" to "Emerald isle", "soho" to "Soho",
    "phantom" to "Phantom", "city_blue" to "City Blue", "dreamy_dusk" to "Dreamy dusk",
    "spellbound" to "Spellbound",
)

val CLIVE_FLAGS = listOf("voice" to "Spoken answers", "follow" to "Follow-up listening", "visual" to "Visual answers", "buzz" to "Buzz on answer")

val LIGHTS_TOP = listOf("floors" to "Floors", "rooms" to "Rooms", "zones" to "Zones")

val WATCH_PAGES = listOf(
    WatchPageInfo("alarm", "Alarm", "mdi:shield-home", "The alarm and its modes"),
    WatchPageInfo("climate", "Climate", "mdi:leaf", "Thermostats, air con, fans and air purifiers"),
    WatchPageInfo("lights", "Lights", "mdi:lightbulb", "Rooms and zones: scenes and brightness"),
    WatchPageInfo("cleaning", "Cleaning", "mdi:robot-vacuum", "Start, stop, home and the vacuum's settings"),
    WatchPageInfo("car", "Car charger", "mdi:ev-station", "Charger modes, speed and smart charge"),
)

class WatchPageInfo(val id: String, val label: String, val icon: String, val what: String)

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

fun JSONObject.hiddenList(page: String): List<String> = optJSONObject("hidden")?.optJSONArray(page)?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList()

fun JSONObject.pageList(): List<Pair<String, Boolean>> {
    val a = optJSONArray("pages") ?: JSONArray()
    val out = (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map { it.optString("id") to it.optBoolean("on", true) }.toMutableList()
    WATCH_PAGES.forEach { p -> if (out.none { it.first == p.id }) out += p.id to true }
    return out
}

fun JSONObject.withItem(page: String, item: String, shown: Boolean): JSONObject {
    val copy = JSONObject(toString())
    val hidden = copy.optJSONObject("hidden") ?: JSONObject().also { copy.put("hidden", it) }
    val list = hiddenList(page).filter { it != item }.toMutableList()
    if (!shown) list += item
    if (list.isEmpty()) hidden.remove(page) else hidden.put(page, JSONArray(list))
    return copy
}

fun JSONObject.withPages(list: List<Pair<String, Boolean>>): JSONObject {
    val copy = JSONObject(toString())
    copy.put("pages", JSONArray(list.map { JSONObject().put("id", it.first).put("on", it.second) }))
    return copy
}

/** Whether a card is folded, and how to fold or unfold it: provided by [CollapsibleCard] around the card it holds. */
class CardFold(val collapsed: Boolean, val toggle: () -> Unit)

/** A thin level bar (a battery): [fraction] of the track filled in [colour]. */
@Composable
fun CompactBar(fraction: Float, colour: Color, ink: Color) {
    Box(Modifier.fillMaxWidth().height(6.dp).background(ink.copy(alpha = 0.18f), RoundedCornerShape(3.dp))) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(6.dp).background(colour, RoundedCornerShape(3.dp)))
    }
}

/** One level on a folded card (a battery, a filter): its label, a short text on the right, and how full it is. */
class CompactLevel(val label: String, val text: String, val fraction: Float, val colour: Color)

/**
 * A card folded down. Either a smaller card, about as tall as Gregg's on Home (name and key figure, status, detail, a bar for each level,
 * and the card's main buttons as tiles), or, with [dense], one row of name, status and small buttons. Titles keep their open size
 * ([titleStyle], [valueStyle]). Tapping it opens the card again; the chevron is [CollapsibleCard]'s, in the same place open or folded.
 */
@Composable
fun CompactStrip(
    tone: ToneColors,
    name: String,
    value: String? = null,
    status: String = "",
    buttons: List<StripButton> = emptyList(),
    enabled: Boolean = true,
    container: Color = tone.container,
    content: Color = tone.onContainer,
    accent: Color = tone.accent,
    detail: String? = null,
    levels: List<CompactLevel> = emptyList(),
    dense: Boolean = false,
    titleStyle: androidx.compose.ui.text.TextStyle? = null,
    valueStyle: androidx.compose.ui.text.TextStyle? = null,
    body: (@Composable () -> Unit)? = null,
) {
    val fold = LocalCardFold.current
    val title = titleStyle ?: MaterialTheme.typography.titleMedium
    val figure = valueStyle ?: MaterialTheme.typography.titleMedium
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
    ) {
        Box(modifier = Modifier.glowBehind(container)) {
            val tap = if (fold != null) Modifier.clickable(onClickLabel = "Expand", onClick = fold.toggle) else Modifier
            if (dense) {
                Row(
                    modifier = Modifier.fillMaxWidth().then(tap).padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 18.dp).heightIn(min = 40.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(name, style = title, color = accent, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                            if (!value.isNullOrBlank()) Text(value, style = figure, maxLines = 1)
                        }
                        if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodyMedium, color = content.copy(alpha = 0.9f), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        if (!detail.isNullOrBlank()) Text(detail, style = MaterialTheme.typography.bodySmall, color = content.copy(alpha = 0.75f), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                    buttons.forEach { b ->
                        val ink = if (b.on) tone.onAccent else content
                        Box(
                            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                                .background(if (b.on) accent else content.copy(alpha = 0.10f))
                                .clickable(enabled = enabled, onClickLabel = b.description, onClick = b.onClick)
                                .semantics { contentDescription = b.description },
                            contentAlignment = Alignment.Center,
                        ) {
                            val numeric = b.label != null && b.label.length <= 2 && b.label.all { it.isDigit() }
                            if (numeric) CentredText(b.label.orEmpty(), ink, Ui.TileText)
                            else HaIcon(b.icon ?: "mdi:numeric", CdVectors.Info, ink, 20.dp)
                        }
                    }
                    if (fold != null) Spacer(Modifier.width(28.dp))
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth().then(tap).padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(name, style = title, color = accent, maxLines = 1, modifier = Modifier.weight(1f))
                        if (!value.isNullOrBlank()) Text(value, style = figure, maxLines = 1)
                        if (fold != null) Spacer(Modifier.width(26.dp))
                    }
                    if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodyMedium, color = content.copy(alpha = 0.9f), maxLines = 2)
                    if (!detail.isNullOrBlank()) Text(detail, style = MaterialTheme.typography.bodySmall, color = content.copy(alpha = 0.75f), maxLines = 2)
                    levels.forEach { l ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth()) {
                                Text(l.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1)
                                Text(l.text, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                            }
                            CompactBar(l.fraction, l.colour, content)
                        }
                    }
                    body?.invoke()
                    if (buttons.isNotEmpty()) {
                        TileRow(buttons.map { b -> TileItem(b.icon ?: "mdi:numeric", b.label ?: b.description, b.on) { b.onClick() } }, tone, content, enabled)
                    }
                }
            }
        }
    }
}

val LocalCardFold = androidx.compose.runtime.compositionLocalOf<CardFold?> { null }

/** One small button on a [CompactStrip]: an icon, or a short label (a fan speed's number). */
class StripButton(val icon: String?, val label: String?, val on: Boolean, val description: String, val onClick: () -> Unit)

/**
 * Builds the app's lighting layout from the dashboard's own light cards, so the app follows what is set in
 * Home Assistant: the rooms, their zones and lights, and each card's scene list.
 *
 * Home: the "Lights" section panel on the view with path `home`. Lighting: every section panel on the view
 * with path `lighting`, each a floor. Anything it can't read falls back to the built-in layout.
 */
object DashboardLights {
    const val DASHBOARD = "dashboard-mobile"
    private const val DEFAULT_MAX_SCENES = 8
    private val WHITE = listOf("bright", "dimmed", "relax", "nightlight")

    fun parse(config: JSONObject): LightLayout {
        val views = config.optJSONArray("views") ?: return LightLayout.Fallback
        var home: List<LightRoom>? = null
        var floors: List<Pair<String, List<LightRoom>>>? = null
        val looks = mutableMapOf<String, FloorLook>()
        for (i in 0 until views.length()) {
            val view = views.optJSONObject(i) ?: continue
            when (view.optString("path")) {
                "home" -> home = panels(view).firstOrNull { it.optString("title").equals("Lights", ignoreCase = true) }
                    ?.let { rooms(it, views) }
                "lighting" -> {
                    val all = panels(view)
                    floors = all.map { it.optString("title") to rooms(it, views) }.filter { it.second.isNotEmpty() }
                    all.forEach { looks[it.optString("title")] = FloorLook(it.optString("icon").takeIf { s -> s.isNotBlank() }, it.optString("color").takeIf { s -> s.isNotBlank() }) }
                }
            }
        }
        return LightLayout(
            home = home?.takeIf { it.isNotEmpty() } ?: LightLayout.Fallback.home,
            floors = floors?.takeIf { it.isNotEmpty() } ?: LightLayout.Fallback.floors,
            looks = looks,
        )
    }

    /** All objects under [node] whose type matches, in order, not looking inside a match. */
    private fun findAll(node: Any?, found: MutableList<JSONObject> = mutableListOf(), match: (String) -> Boolean): List<JSONObject> {
        when (node) {
            is JSONObject -> if (match(node.optString("type"))) found += node
            else node.keys().forEach { findAll(node.opt(it), found, match) }
            is JSONArray -> for (i in 0 until node.length()) findAll(node.opt(i), found, match)
        }
        return found
    }

    private fun panels(view: JSONObject) = findAll(view) { it == "custom:section-panel-card" }

    private val MIRRORS = setOf("custom:mirror-card", "custom:mirror-card-beta")

    /**
     * The rooms in a panel, in order. A mirror card stands for the card it points at (another page's light
     * card), so it's replaced by that card's room: the original is set up once in Home Assistant.
     */
    private fun rooms(panel: JSONObject, views: JSONArray): List<LightRoom> {
        val cards = findAll(panel.opt("cards")) { it == "custom:light-control-card" || it in MIRRORS }
        return cards.mapNotNull { card ->
            if (card.optString("type") in MIRRORS) resolveMirror(card, views)?.let { room(it) } else room(card)
        }
    }

    /** The card a mirror points at: its page is `view` (a path, or a position), and it's found by key. */
    fun resolveMirror(mirror: JSONObject, views: JSONArray): JSONObject? {
        // Only mirrors of this dashboard can be followed (the app reads one dashboard).
        val dashboard = mirror.optString("dashboard", "this")
        if (dashboard != "this" && dashboard != DASHBOARD) return null
        val ref = mirror.optString("view")
        val view = (0 until views.length()).mapNotNull { views.optJSONObject(it) }.firstOrNull { it.optString("path") == ref }
            ?: ref.toIntOrNull()?.let { views.optJSONObject(it) }
            ?: return null
        return mirrorable(view).firstOrNull { it.first == mirror.optString("source") }?.second
    }

    private val CONTAINERS = setOf(
        "custom:auto-layout-card", "custom:section-panel-card", "custom:nav-bar-card", "custom:section-title-card",
        "vertical-stack", "horizontal-stack", "grid", "conditional", "custom:stack-in-card",
    )

    /** What a card is about, as the first part of its key: the same rules as the Mirror Card (mirror.js). */
    fun identity(card: JSONObject): String {
        card.optString("area").takeIf { it.isNotBlank() }?.let { return "area:${it.trim()}" }
        (card.opt("entity") as? String)?.takeIf { it.isNotBlank() }?.let { return "entity:${it.trim()}" }
        (card.optString("name").takeIf { it.isNotBlank() } ?: card.optString("title").takeIf { it.isNotBlank() })
            ?.let { return "name:${it.trim()}" }
        return "type:${card.optString("type", "card").trim()}"
    }

    /** Every card on a page that can be mirrored, in page order, as (key, card). Repeats get "#2", "#3". */
    fun mirrorable(view: JSONObject): List<Pair<String, JSONObject>> {
        val found = mutableListOf<Pair<String, JSONObject>>()
        val seen = mutableMapOf<String, Int>()
        fun walk(node: Any?) {
            when (node) {
                is JSONArray -> for (i in 0 until node.length()) walk(node.opt(i))
                is JSONObject -> {
                    val type = node.optString("type")
                    if (type.isNotEmpty() && type !in CONTAINERS && type !in MIRRORS) {
                        val base = identity(node)
                        val n = (seen[base] ?: 0) + 1
                        seen[base] = n
                        found += (if (n > 1) "$base#$n" else base) to node
                    }
                    listOf("cards", "card", "sections").forEach { k -> node.opt(k)?.let { walk(it) } }
                }
            }
        }
        walk(view.opt("sections") ?: view.opt("cards"))
        return found
    }

    private fun entityOf(item: Any?): String? = when (item) {
        is String -> item
        is JSONObject -> item.optString("entity").takeIf { it.isNotBlank() }
        else -> null
    }

    fun room(card: JSONObject): LightRoom? {
        val items = card.optJSONArray("entities")?.let { a -> (0 until a.length()).map { a.opt(it) } } ?: emptyList()
        val mode = card.optString("mode", "room")
        // A room or group card lists its rows (the first is the group light on top). A single light is just its entity.
        val head = if (mode == "light") card.optString("entity").takeIf { it.isNotBlank() } else items.firstNotNullOfOrNull { entityOf(it) }
            ?: card.optString("entity").takeIf { it.isNotBlank() && mode == "group" }
        if (head == null) return null
        val rows = if (mode == "light") emptyList() else items.drop(1).mapNotNull { item ->
            val id = entityOf(item) ?: return@mapNotNull null
            val o = item as? JSONObject
            LightRowSpec(id, o?.optString("name")?.takeIf { it.isNotBlank() }, o?.optInt("level", 1) ?: 1)
        }
        val title = card.optString("name").takeIf { it.isNotBlank() }
            ?: (items.firstOrNull() as? JSONObject)?.optString("name")?.takeIf { it.isNotBlank() }
        return LightRoom(title, card.optString("area").takeIf { it.isNotBlank() }, head, rows, scenes(card, head))
    }

    private fun scenes(card: JSONObject, head: String): List<LightScene> {
        val max = if (card.has("max_scenes")) card.optInt("max_scenes", DEFAULT_MAX_SCENES) else DEFAULT_MAX_SCENES
        val list = card.optJSONArray("scenes")?.let { a -> (0 until a.length()).mapNotNull { scene(a.opt(it), head) } }
            // A card with no list shows the four default scenes for its room.
            ?: WHITE.map { LightScene(it, head) }
        return list.take(max.coerceAtLeast(0))
    }

    /** A scene entry: `universal:KEY@TARGET` (a Church Drive scene), or a Home Assistant scene entity. */
    private fun scene(item: Any?, head: String): LightScene? {
        val entity = entityOf(item) ?: return null
        val label = (item as? JSONObject)?.optString("name")?.takeIf { it.isNotBlank() }
        if (!entity.startsWith("universal:")) return LightScene(entity, head, label, haScene = entity)
        val ref = entity.removePrefix("universal:")
        return LightScene(ref.substringBefore('@'), ref.substringAfter('@', head), label)
    }

    /** Area id to name, from the area registry. */
    fun areaNames(result: Any?): Map<String, String> {
        val a = result as? JSONArray ?: return emptyMap()
        return (0 until a.length()).mapNotNull { a.optJSONObject(it) }
            .filter { it.has("area_id") && it.has("name") }
            .associate { it.getString("area_id") to it.getString("name") }
    }
}

/** The Device Health card: each watched device, responding or stale, when it was last heard from, and a Fix now button. */
@Composable
fun DeviceHealthCard(config: JSONObject, entities: Map<String, EntityState>, call: CallService) {
    val devices = watchedDevices(entities)
    val showOk = if (config.has("show_ok")) config.optBoolean("show_ok", true) else true
    val shown = devices.filter { showOk || !it.ok }
    val neutral = panelNeutral()
    if (LocalCompactCards.current) {
        val bad = devices.filter { !it.ok }
        val amber = toneColors(Tone.Amber)
        CompactStrip(
            if (bad.isEmpty()) neutral else amber, config.optString("title").ifBlank { "Device health" }, null,
            when { devices.isEmpty() -> "None watched yet"; bad.isEmpty() -> "All responding"; else -> bad.joinToString(", ") { it.name } },
            container = if (bad.isEmpty()) neutral.container else amber.container, content = if (bad.isEmpty()) neutral.onContainer else amber.onContainer, dense = true,
        )
        return
    }
    EntityCard(neutral.container, neutral.onContainer) {
        config.optString("title").takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
        if (shown.isEmpty()) Text(if (devices.isEmpty()) "No devices are being watched yet." else "Every device is responding.", style = MaterialTheme.typography.bodyMedium)
        shown.forEach { d ->
            val tone = toneColors(if (d.ok) Tone.Green else Tone.Amber)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ToneIconName(if (d.ok) "mdi:check-circle-outline" else "mdi:alert-circle-outline", CdVectors.Info, tone, 36)
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

/** The Who's home card: each person, where they are, their phones and the phones' batteries. */
@Composable
fun PeopleStatusCard(entities: Map<String, EntityState>, registry: Registry) {
    val rows = peopleRows(entities, registry)
    val neutral = panelNeutral()
    if (LocalCompactCards.current) {
        val home = rows.count { it.state == "home" }
        CompactStrip(
            neutral, "Who's home", null, if (rows.isEmpty()) "Nobody set up" else "$home home · ${rows.size - home} away",
            detail = rows.joinToString(" · ") { p -> p.name.trim().substringBefore(' ') + " " + presencePill(p.state, p.place, p.zone).first.lowercase() }, dense = true,
        )
        return
    }
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

/** One person for the Who's home card. */
data class PersonRow(val id: String, val name: String, val state: String, val place: String?, val zone: String?, val picture: String?, val phones: List<Pair<String, Int?>>)

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

/** The word and colour for where a person is: Home, a named place (At work · School), Away or Unknown. */
fun presencePill(state: String, place: String?, zone: String?): Pair<String, Tone> = when {
    state == "home" -> "Home" to Tone.Green
    !place.isNullOrBlank() && !zone.isNullOrBlank() -> "${presetLabel(place)} · $zone" to Tone.Teal
    state == "not_home" -> "Away" to Tone.Grey
    state == "unknown" || state == "unavailable" -> "Unknown" to Tone.Amber
    else -> presetLabel(state) to Tone.Teal
}

val FALLBACK_FLOORS: List<Pair<String, List<LightRoom>>> = listOf(
    "Ground Floor" to listOf(
        room(
            "Kitchen", "light.kitchen", listOf("light.kitchen_spotlights", "light.kitchen_ambience"),
            scenes(*WHITE, "cool_bright", "cyber_fidelity", "lake_placid", "emerald_isle", room = "light.kitchen"),
        ),
        room(
            "Living room", "light.living_room",
            listOf("light.living_room_table_lights", "light.living_room_ambience", "light.living_room_centris", "light.tv_lightstrip"),
            scenes(
                *WHITE, "soho@light.living_room_ambience", "phantom@light.living_room_ambience",
                "cyber_fidelity@light.living_room_ambience", room = "light.living_room",
            ),
        ),
        plain("Entrance", "light.entrance"),
    ),
    "Middle Floor" to listOf(
        plain("Middle floor", "light.middle_floor"),
        plain("Second bedroom", "light.second_bedroom"),
        plain("Spare bedroom", "light.spare_bedroom"),
    ),
    "Top Floor" to listOf(
        room(
            "Hayley's landing", "light.hayley_s_landing_main", listOf("light.hayley_s_landing_ambience"),
            scenes("cyber_fidelity@light.hayley_s_landing_ambience", room = "light.hayley_s_landing_main"),
        ),
        plain("Office", "light.office"),
        room(
            "Hayley's bedroom", "light.hayleys_bedroom", listOf("light.hayley_s_bedroom_main", "light.hayley_s_bedroom_ambiance"),
            scenes(
                "bright", "cool_bright", "dimmed@light.hayley_s_bedroom_main", "nightlight", "city_blue",
                "dreamy_dusk@light.hayley_s_bedroom_ambiance", "soho@light.hayley_s_bedroom_ambiance",
                "spellbound@light.hayley_s_bedroom_ambiance", room = "light.hayleys_bedroom",
            ),
        ),
        plain("Hayley's en suite", "light.hayley_s_en_suite"),
    ),
    "Garden" to listOf(
        room(
            "Garden", "light.garden", listOf("light.patio_light_strip", "light.outside"),
            scenes(
                *WHITE, "cool_bright", "cyber_fidelity@light.patio_ambience", "emerald_isle", "city_blue",
                room = "light.garden",
            ),
        ),
    ),
    "Front Garden" to listOf(plain("Front garden", "light.front")),
)

/** What the app shows until (or unless) it can read the dashboard: copied from the dashboard when the app was built. */
val FALLBACK_HOME_ROOMS = listOf(
    room(
        "Kitchen", "light.kitchen", listOf("light.kitchen_spotlights", "light.kitchen_ambience"),
        scenes(*WHITE, "cool_bright", "energise", "soho@light.kitchen_ambience", "emerald_isle", room = "light.kitchen"),
    ),
    room(
        "Living room", "light.living_room",
        listOf("light.living_room_centris", "light.tv_lightstrip", "light.tv_table_lamp", "light.living_room_lamp"),
        scenes(
            *WHITE, "cool_bright", "soho@light.living_room_ambience", "cyber_fidelity@light.living_room_ambience",
            room = "light.living_room",
        ),
    ),
    room(
        "Middle floor", "light.middle_floor", emptyList(),
        scenes(*WHITE, room = "light.middle_floor"),
    ),
)

/** The rooms on the Home page, and the floors of rooms on the Lighting page. */
/** A floor panel's own icon (`cd:floor-0`) and colour (a palette name or an HA colour) as the dashboard sets them; either may be blank. */
data class FloorLook(val icon: String?, val color: String?)

data class LightLayout(
    val home: List<LightRoom>,
    val floors: List<Pair<String, List<LightRoom>>>,
    /** Each floor's look by its title, from the dashboard; a floor with none is drawn with the bulb in the lights colour. */
    val looks: Map<String, FloorLook> = emptyMap(),
) {
    companion object {
        val Fallback = LightLayout(FALLBACK_HOME_ROOMS, FALLBACK_FLOORS)
    }
}

/**
 * A room card: its group light on top, the zones and lights under it, and its scenes. [title] is the
 * card's own name for it, or null to use the area's name, then the group light's name.
 */
data class LightRoom(
    val title: String?,
    val area: String?,
    val head: String,
    val rows: List<LightRowSpec>,
    val scenes: List<LightScene>,
)

/** A zone or light under a room. [level] is how far it is indented (1 = directly under the room). */
data class LightRowSpec(val entity: String, val name: String? = null, val level: Int = 1)

/**
 * A scene tile: a Church Drive scene [key] played on [target], or (when [haScene] is set) a Home Assistant
 * scene entity. [label] is a name the card gives it.
 */
data class LightScene(val key: String, val target: String, val label: String? = null, val haScene: String? = null) {
    val name: String get() = label ?: SCENE_NAMES[key] ?: key.replace('_', ' ').replaceFirstChar { it.uppercase() }

    /** The scene select entity Church Drive keeps for a Hue room or zone, e.g. select.kitchen_scene. */
    val selectEntity: String get() = "select.${target.removePrefix("light.")}_scene"
}

fun room(title: String, head: String, rowIds: List<String>, scenes: List<LightScene>) =
    LightRoom(title, null, head, rowIds.map { LightRowSpec(it) }, scenes)

fun scenes(vararg specs: String, room: String): List<LightScene> =
    specs.map { spec ->
        val (key, target) = spec.split("@").let { it[0] to (it.getOrNull(1) ?: room) }
        LightScene(key, target)
    }

fun plain(title: String, head: String) = room(title, head, emptyList(), scenes(*WHITE, room = head))

/** A list attribute such as `preset_modes`, as text. */
fun EntityState.list(key: String): List<String> {
    val a = attributes.optJSONArray(key) ?: return emptyList()
    return (0 until a.length()).filter { !a.isNull(it) }.map { a.optString(it) }.filter { it.isNotBlank() }
}
