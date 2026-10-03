package com.churchdrive.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.churchdrive.app.ha.CallService
import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ha.data
import kotlin.math.roundToInt

private val SCENE_NAMES = mapOf(
    "bright" to "Bright", "dimmed" to "Dimmed", "relax" to "Relax", "nightlight" to "Nightlight",
    "cool_bright" to "Cool bright", "energise" to "Energise", "cyber_fidelity" to "Cyber fidelity",
    "lake_placid" to "Lake Placid", "emerald_isle" to "Emerald isle", "soho" to "Soho",
    "phantom" to "Phantom", "city_blue" to "City Blue", "dreamy_dusk" to "Dreamy dusk",
    "spellbound" to "Spellbound",
)

/**
 * A scene tile: a Church Drive scene [key] played on [target], or (when [haScene] is set) a Home Assistant
 * scene entity. [label] is a name the card gives it.
 */
data class LightScene(val key: String, val target: String, val label: String? = null, val haScene: String? = null) {
    val name: String get() = label ?: SCENE_NAMES[key] ?: key.replace('_', ' ').replaceFirstChar { it.uppercase() }

    /** The scene select entity Church Drive keeps for a Hue room or zone, e.g. select.kitchen_scene. */
    val selectEntity: String get() = "select.${target.removePrefix("light.")}_scene"
}

/** A zone or light under a room. [level] is how far it is indented (1 = directly under the room). */
data class LightRowSpec(val entity: String, val name: String? = null, val level: Int = 1)

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

private fun room(title: String, head: String, rowIds: List<String>, scenes: List<LightScene>) =
    LightRoom(title, null, head, rowIds.map { LightRowSpec(it) }, scenes)

private fun scenes(vararg specs: String, room: String): List<LightScene> =
    specs.map { spec ->
        val (key, target) = spec.split("@").let { it[0] to (it.getOrNull(1) ?: room) }
        LightScene(key, target)
    }

private val WHITE = arrayOf("bright", "dimmed", "relax", "nightlight")

/** What the app shows until (or unless) it can read the dashboard: copied from the dashboard when the app was built. */
private val FALLBACK_HOME_ROOMS = listOf(
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

private fun plain(title: String, head: String) = room(title, head, emptyList(), scenes(*WHITE, room = head))

private val FALLBACK_FLOORS: List<Pair<String, List<LightRoom>>> = listOf(
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

/** The rooms on the Home page, and the floors of rooms on the Lighting page. */
data class LightLayout(val home: List<LightRoom>, val floors: List<Pair<String, List<LightRoom>>>) {
    companion object {
        val Fallback = LightLayout(FALLBACK_HOME_ROOMS, FALLBACK_FLOORS)
    }
}

fun roomsOn(rooms: List<LightRoom>, entities: Map<String, EntityState>) = rooms.count { entities[it.head]?.state == "on" }

fun lightsSummary(rooms: List<LightRoom>, entities: Map<String, EntityState>): String {
    val n = roomsOn(rooms, entities)
    return if (n == 0) "All off" else "$n room${if (n > 1) "s" else ""} on"
}

private val SCENE_COLOURS = mapOf(
    "bright" to listOf(0xFFFFF1D6, 0xFFFFD9A0), "cool_bright" to listOf(0xFFE3F2FF, 0xFFB7D9FF),
    "dimmed" to listOf(0xFFA8793A, 0xFF5C3D1A), "relax" to listOf(0xFFFFB36B, 0xFFE07A3A),
    "nightlight" to listOf(0xFF7A2E10, 0xFF3A1408), "energise" to listOf(0xFFBFE3FF, 0xFF6EC1FF),
    "soho" to listOf(0xFFE8639C, 0xFF7B4FD8, 0xFFF2A24C), "cyber_fidelity" to listOf(0xFF2E7BE8, 0xFFB14FE0),
    "lake_placid" to listOf(0xFFF0A36B, 0xFF3C6FC8), "emerald_isle" to listOf(0xFF3CCB7A, 0xFF16A085),
    "phantom" to listOf(0xFF4A6BE0, 0xFF8A5BD8), "city_blue" to listOf(0xFF2D5BE3, 0xFF1B9AE8),
    "dreamy_dusk" to listOf(0xFFFF9A62, 0xFFC95A9A), "spellbound" to listOf(0xFF5A4BE0, 0xFF2FC6D6),
).mapValues { (_, v) -> v.map { Color(it) } }

private fun sceneIcon(key: String): ImageVector = when (key) {
    "bright" -> Icons.Filled.WbSunny
    "cool_bright" -> Icons.Filled.AcUnit
    "dimmed" -> Icons.Filled.Brightness4
    "relax" -> Icons.Filled.Spa
    "nightlight" -> Icons.Filled.Nightlight
    "energise" -> Icons.Filled.Bolt
    else -> Icons.Filled.Palette
}

/** A room's title: the card's own name, else its area's name in Home Assistant, else the group light's name. */
private fun roomTitle(room: LightRoom, head: EntityState?, areaNames: Map<String, String>): String =
    room.title ?: room.area?.let { areaNames[it] } ?: head?.friendlyName ?: room.head

/** A room that opens out to its zones, lights and scenes when tapped. */
@Composable
fun LightRoomCard(room: LightRoom, entities: Map<String, EntityState>, areaNames: Map<String, String>, call: CallService) {
    val head = entities[room.head]
    val on = head?.state == "on"
    val tone = toneColors(if (on) Tone.Amber else Tone.Grey)
    var expanded by rememberSaveable(room.head) { mutableStateOf(false) }
    val expandable = room.rows.isNotEmpty() || room.scenes.isNotEmpty()

    EntityCard(tone.container, tone.onContainer) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            LightPill(head, room.head, roomTitle(room, head, areaNames), tone, call, Modifier.weight(1f))
            if (expandable) {
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                    )
                }
            }
        }

        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                room.rows.forEach { row ->
                    LightPill(entities[row.entity], row.entity, row.name, tone, call, Modifier.padding(start = 16.dp * row.level))
                }
                if (room.scenes.isNotEmpty()) {
                    Text(
                        "Scenes",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(start = 4.dp, top = 6.dp),
                    )
                    val active = room.scenes.filter {
                        entities[it.selectEntity]?.state.equals(it.name, ignoreCase = true)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        room.scenes.chunked(4).forEach { rowScenes ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                rowScenes.forEach { scene ->
                                    SceneTile(
                                        scene,
                                        active = scene in active,
                                        dimmed = active.isNotEmpty() && scene !in active,
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        if (scene.haScene != null) call("scene", "turn_on", scene.haScene, data())
                                        else call("church_drive", "apply_scene", scene.target, data("scene" to scene.key))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A scene as a tile: its colours as a gradient, an icon and its name; the playing scene has a ring. */
@Composable
private fun SceneTile(scene: LightScene, active: Boolean, dimmed: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val looks = LocalSceneLooks.current
    // The look set on the dashboards, then the scene's own colours; then a built-in look, then one worked out
    // from a white scene's colour temperature; and last, a colour made from its name so tiles aren't all alike.
    val colours = (looks.colours(scene.key, scene.name)?.map { Color(it) }
        ?: SCENE_COLOURS[scene.key]
        ?: looks.whiteColours(scene.key, scene.name)?.map { Color(it) }
        ?: nameColours(scene.key))
    // Dark or white text, whichever has the better contrast on this tile's colours.
    val ink = if (SceneLooks.darkInkOn(colours.map { it.toArgb() })) Color(0xFF202124) else Color.White
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = modifier
            .height(Ui.TallTileHeight)
            .alpha(if (dimmed) 0.55f else 1f)
            .clip(shape)
            .background(Brush.linearGradient(colours))
            .then(if (active) Modifier.border(2.5.dp, Color.White, shape) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            HaIcon(looks.icon(scene.key, scene.name), sceneIcon(scene.key), ink, 20.dp)
            CentredText(scene.name, ink, 10.sp, Modifier.padding(horizontal = 3.dp), lineHeight = 12.sp)
        }
    }
}

/** A steady pair of colours made from a scene's key, for a scene nothing else describes. */
private fun nameColours(key: String): List<Color> {
    var h = 0
    for (c in key) h = (h * 31 + c.code) % 360
    return listOf(Color.hsv(h.toFloat(), 0.55f, 0.85f), Color.hsv(((h + 50) % 360).toFloat(), 0.6f, 0.55f))
}

private fun brightnessPct(light: EntityState?): Int =
    ((light?.num("brightness") ?: 255.0) / 255.0 * 100).roundToInt().coerceIn(1, 100)

/**
 * The dashboard's combined control: a pill filled to the light's brightness and tinted with its live
 * colour. Tap to switch it on or off; drag across it to set brightness (lights that can't dim just switch).
 * [name] is the title, or the light's own name when null.
 */
@Composable
fun LightPill(
    light: EntityState?,
    entityId: String,
    name: String?,
    tone: ToneColors,
    call: CallService,
    modifier: Modifier = Modifier,
) {
    val on = light?.state == "on"
    val dimmable = light?.dimmable() == true
    val pct = if (on) brightnessPct(light) else 0
    var drag by remember(pct) { mutableStateOf<Float?>(null) }
    var widthPx by remember { mutableIntStateOf(1) }
    val shown = drag ?: (pct / 100f)
    val rgb = light?.rgb()
    val tint = if (on && rgb != null) Color(rgb[0], rgb[1], rgb[2]) else tone.accent
    val shape = RoundedCornerShape(20.dp)
    val status = when {
        light == null -> "Loading…"
        !light.available -> "Unavailable"
        drag != null -> "${(shown * 100).roundToInt()}%"
        !on -> "Off"
        dimmable -> "$pct%"
        else -> "On"
    }

    fun level(x: Float) = (x / widthPx).coerceIn(0.01f, 1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(Ui.TallTileHeight)
            .clip(shape)
            .background(tone.onContainer.copy(alpha = 0.10f))
            .onSizeChanged { widthPx = it.width.coerceAtLeast(1) }
            .pointerInput(entityId, light?.available, on) {
                detectTapGestures {
                    if (light?.available == true) call("light", if (on) "turn_off" else "turn_on", entityId, data())
                }
            }
            .pointerInput(entityId, dimmable) {
                if (dimmable) {
                    detectHorizontalDragGestures(
                        onDragStart = { drag = level(it.x) },
                        onDragEnd = {
                            drag?.let { call("light", "turn_on", entityId, data("brightness_pct" to (it * 100).roundToInt())) }
                            drag = null
                        },
                        onDragCancel = { drag = null },
                    ) { change, _ -> drag = level(change.position.x) }
                }
            },
    ) {
        if (on || drag != null) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(shown).background(tint.copy(alpha = 0.45f)))
        }
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HaIcon(
                light?.str("icon"), Icons.Filled.Lightbulb,
                tint = if (on) tint else tone.onContainer.copy(alpha = 0.55f),
                size = 26.dp,
            )
            Text(
                name ?: light?.friendlyName ?: entityId,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(status, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** The Lighting page: a panel per floor, a card per room. */
@Composable
fun LightingPage(layout: LightLayout, entities: Map<String, EntityState>, areaNames: Map<String, String>, call: CallService) {
    layout.floors.forEach { (floor, rooms) ->
        SectionPanel(
            floor, icon = Icons.Filled.Lightbulb, tone = Tone.Amber,
            summary = lightsSummary(rooms, entities),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                rooms.forEach { LightRoomCard(it, entities, areaNames, call) }
            }
        }
    }
}

/** Every room grouped for a picker: the Home panel's rooms first, then each floor's (a room is listed once). */
fun allRoomsByFloor(layout: LightLayout): List<Pair<String, List<LightRoom>>> {
    val seen = mutableSetOf<String>()
    val groups = buildList {
        add("Home" to layout.home)
        addAll(layout.floors)
    }
    return groups.map { (name, rooms) -> name to rooms.filter { seen.add(it.head) } }.filter { it.second.isNotEmpty() }
}

/** A colour that stands for a scene on a button: its own first colour, else a neutral blue. */
fun sceneSwatch(key: String): Color = SCENE_COLOURS[key]?.firstOrNull() ?: Color(0xFF8AB4F8)
