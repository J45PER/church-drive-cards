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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
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
    "bright" -> CdVectors.Sun
    "cool_bright" -> CdVectors.Sun
    "dimmed" -> CdVectors.SunHalf
    "relax" -> CdVectors.Flower
    "nightlight" -> CdVectors.Moon
    "energise" -> CdVectors.Bolt
    else -> CdVectors.Palette
}

/** A room's title: the card's own name, else its area's name in Home Assistant, else the group light's name. */
private fun roomTitle(room: LightRoom, head: EntityState?, areaNames: Map<String, String>): String =
    room.title ?: room.area?.let { areaNames[it] } ?: head?.friendlyName ?: room.head

/** A room that opens out to its zones, lights and scenes when tapped. */
@Composable
fun LightRoomCard(room: LightRoom, entities: Map<String, EntityState>, areaNames: Map<String, String>, call: CallService) {
    val head = entities[room.head]
    val on = head?.state == "on"
    val tone = toneColors(if (on) Tone.Lights else Tone.Grey)
    val expandable = room.rows.isNotEmpty() || room.scenes.isNotEmpty()

    // On the card grid: the room's pill is 1.5 rows tall, half a row down, with the arrow in column 8; opened, each light is a pill the same height two rows
    // apart (a nested one starts half a cell further in), then the scenes in rows of up to three with a quarter cell between them (a short last row's tiles stretch).
    val state = rememberFold("lights|${room.head}", true)
    val collapsed = state
    val open = !collapsed.value && expandable
    val hintContext = LocalContext.current
    val showHint = opensSettings(head) && remember(room.head) { LightHoldHint.wants(hintContext, room.head) }
    val lightsEnd = 2f + 1.5f * (if (open) room.rows.size else 0)
    val sceneRows = if (open && room.scenes.isNotEmpty()) room.scenes.chunked(3) else emptyList()
    val rows = when {
        !open -> if (showHint) 2.5f else 2f
        sceneRows.isEmpty() -> lightsEnd + 0.5f
        else -> lightsEnd + 1f + 1.25f * sceneRows.size - 0.25f + 0.5f
    }
    GridCard(tone.container, tone.onContainer, rows, "", deviceEntity = room.head, fold = if (expandable) collapsed else null, arrowTop = 0.5f) {
        LightPill(head, room.head, roomTitle(room, head, areaNames), tone, call, Modifier.at(0.5f, 0.5f, if (expandable) 6.5f else 7f, 1f), entities,
            tintFrom = room.rows.filter { it.entity.contains("ceiling", true) || (it.name ?: "").contains("ceiling", true) }.map { it.entity }, pillHeight = cell)
        if (!open && showHint) {
            Text("Hold for colours", style = MaterialTheme.typography.labelSmall, modifier = Modifier.at(0.5f, 1.55f, 7f, 0.5f), color = tone.onContainer.copy(alpha = 0.7f))
        }
        if (open) {
            room.rows.forEachIndexed { k, row ->
                val x = 0.5f + 0.5f * row.level
                LightPill(entities[row.entity], row.entity, row.name, tone, call, Modifier.at(x, 2f + 1.5f * k, 7.5f - x, 1f), entities, pillHeight = cell)
            }
            if (sceneRows.isNotEmpty()) {
                Text("Scenes", style = MaterialTheme.typography.labelLarge, modifier = Modifier.at(0.5f, lightsEnd + 0.25f, 3f, 0.5f))
                val active = room.scenes.filter { entities[it.selectEntity]?.state.equals(it.name, ignoreCase = true) }
                sceneRows.forEachIndexed { r, rowScenes ->
                    val n = rowScenes.size
                    val gap = 0.25f
                    val w = (7f - gap * (n - 1)) / n
                    rowScenes.forEachIndexed { c, scene ->
                        SceneTile(
                            scene,
                            active = scene in active,
                            dimmed = active.isNotEmpty() && scene !in active,
                            modifier = Modifier.at(0.5f + c * (w + gap), lightsEnd + 1f + 1.25f * r, w, 1f),
                            h = cell,
                        ) {
                            scene.haScene?.let { call("scene", "turn_on", it, data()) }
                            else call("church_drive", "apply_scene", scene.target, data("scene" to scene.key))
                        }
                    }
                }
            }
        }
    }
}

/** A scene as a tile: its colours as a gradient, an icon and its name; the playing scene has a ring. */
@Composable
private fun SceneTile(scene: LightScene, active: Boolean, dimmed: Boolean, modifier: Modifier, h: androidx.compose.ui.unit.Dp = Ui.TallTileHeight, onClick: () -> Unit) {
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
            .height(h)
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

private val COLOUR_MODES = setOf("hs", "rgb", "rgbw", "rgbww", "xy")

private const val SECURITY_ACTIVE = "input_boolean.clive_away_lights_active"
private val SECURITY_LABELS = setOf("away_security_light", "away_tv_simulation")
private fun securityLabelled(registry: com.churchdrive.app.ha.Registry, id: String) = registry.labelsOf(id).any { it in SECURITY_LABELS }

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
    entities: Map<String, EntityState>? = null,
    /** For a room: its lights. The room's own colour is the group's last one, which lingers after a room turns to plain white or has only switches on, so the pill takes the colour of a light that is on and showing one, else the theme. */
    tintFrom: List<String>? = null,
    pillHeight: androidx.compose.ui.unit.Dp = Ui.TallTileHeight,
) {
    val on = light?.state == "on"
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    var settings by remember { mutableStateOf(false) }
    val dimmable = light?.dimmable() == true
    val pct = if (on) brightnessPct(light) else 0
    var drag by remember(pct) { mutableStateOf<Float?>(null) }
    var widthPx by remember { mutableIntStateOf(1) }
    var pressedAt by remember { mutableStateOf(0L) }
    val dragged = drag ?: (pct / 100f)
    val rgb = if (tintFrom != null && entities != null) {
        tintFrom.firstNotNullOfOrNull { id -> entities[id]?.takeIf { it.state == "on" && it.str("color_mode") in COLOUR_MODES }?.rgb() }
    } else light?.rgb()
    // Away "security mode": the lights Clive keeps on (or flickers like a television) while the house is empty are labelled, and show a steady full bar in the
    // Armed Home blue with the words Security mode, not every colour they pass through.
    val registry = LocalRegistry.current
    val security = on && entities?.get(SECURITY_ACTIVE)?.state == "on" &&
        (securityLabelled(registry, entityId) || light?.list("entity_id").orEmpty().any { securityLabelled(registry, it) })
    val tint = if (security) toneColors(Tone.AlarmHome).accent else if (on && rgb != null) Color(rgb[0], rgb[1], rgb[2]) else tone.accent
    val shown = if (security) 1f else dragged
    val shape = RoundedCornerShape(18.dp)
    val status = when {
        light == null -> "Loading…"
        !light.available -> "Unavailable"
        security -> "Security mode"
        drag != null -> "${(shown * 100).roundToInt()}%"
        !on -> "Off"
        dimmable -> "$pct%"
        else -> "On"
    }

    fun level(x: Float) = (x / widthPx).coerceIn(0.01f, 1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(pillHeight)
            .clip(shape)
            .background(tone.onContainer.copy(alpha = 0.10f))
            .onSizeChanged { widthPx = it.width.coerceAtLeast(1) }
            .pointerInput(entityId, light?.available, on) {
                detectTapGestures(
                    onPress = { pressedAt = System.currentTimeMillis(); tryAwaitRelease() },
                    // Press and hold opens the light's full-screen settings, when there is more to set than on and off.
                    onLongPress = {
                        if (entities != null && opensSettings(light)) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            LightHoldHint.markSeen(context)
                            settings = true
                        }
                    },
                    onTap = { if (light?.available == true && System.currentTimeMillis() - pressedAt >= 120L) call("light", if (on) "turn_off" else "turn_on", entityId, data()) },
                )
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
                light?.str("icon"), CdVectors.Bulb,
                tint = if (on) tint else tone.onContainer.copy(alpha = 0.55f),
                size = 26.dp,
            )
            Text(
                name ?: light?.friendlyName ?: entityId,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
                // Its own text colour: the card it sits in (a zone's purple, say) is not readable over the light's colour.
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
    if (settings && entities != null) LightSettings(entityId, name, entities, call) { settings = false }
}

/** The one-line "Hold for colours" hint: shown under the first light that has settings, until someone has held one. */
object LightHoldHint {
    private const val PREFS = "church_drive_ui"
    private const val KEY = "light_hold_seen"
    private var shownOn: String? = null

    fun markSeen(context: android.content.Context) {
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).edit().putBoolean(KEY, true).apply()
    }

    /** Whether the hint should show under [id]: not yet seen, and [id] is the first card to ask. */
    fun wants(context: android.content.Context, id: String): Boolean {
        if (context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).getBoolean(KEY, false)) return false
        if (shownOn == null) shownOn = id
        return shownOn == id
    }
}

/** The Lighting page: a panel per floor, a card per room. */
@Composable
fun LightingPage(layout: LightLayout, entities: Map<String, EntityState>, areaNames: Map<String, String>, call: CallService) {
    layout.floors.forEach { (floor, rooms) ->
        val look = layout.looks[floor]
        SectionPanel(
            floor,
            icon = if (look?.icon.isNullOrBlank()) CdVectors.Bulb else null,
            iconName = look?.icon?.takeIf { it.isNotBlank() },
            tone = toneFromColour(look?.color) ?: Tone.Lights,
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
fun sceneSwatch(key: String): Color = SCENE_COLOURS[key]?.firstOrNull() ?: CliveColours.AlarmHome.colour
