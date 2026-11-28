package com.churchdrive.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.churchdrive.app.ha.CallService
import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ha.data
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import kotlin.math.hypot
import kotlin.math.atan2
import kotlin.math.roundToInt

private fun rgbColour(c: IntArray) = Color(c[0], c[1], c[2])

/**
 * The light to take a fitting's colour from. A room's group light shows whichever of its lights reported last, and for a Govee fitting that is often a
 * segment of the ring (red, say) while the main light is white, so a group with a ring takes the colour of its main light.
 */
internal fun colourSource(light: EntityState?, entities: Map<String, EntityState>?): EntityState? {
    if (light == null || entities == null) return light
    val members = light.lightMembers().mapNotNull { entities[it] }
    if (members.size < 2 || members.none { isSegmentGroup(it) }) return light
    return members.firstOrNull { !isSegmentGroup(it) && it.state == "on" } ?: members.firstOrNull { !isSegmentGroup(it) } ?: light
}

private fun brightnessOf(light: EntityState): Int =
    ((light.num("brightness") ?: 255.0) / 255.0 * 100).roundToInt().coerceIn(1, 100)

/** The colour a light is showing, for tinting its page. */
private fun tintOf(light: EntityState, fallback: Color): Color {
    if (light.state != "on") return fallback
    light.rgb()?.let { return rgbColour(it) }
    light.num("color_temp_kelvin")?.let { return rgbColour(kelvinRgb(it.toInt())) }
    return fallback
}

internal val SWATCHES = listOf(0.0 to 100.0, 28.0 to 100.0, 50.0 to 100.0, 140.0 to 75.0, 195.0 to 80.0, 235.0 to 70.0, 280.0 to 70.0, 330.0 to 70.0)

/**
 * A light's full-screen settings, opened by pressing and holding it: brightness for a dimmable light, warm to cool
 * for a white one, a colour wheel for a colour one and just On/Off for a switch. A room, zone or group also lists
 * its lights, each with its own switch; a tap on one shows that light's settings on this page, under the list.
 */
@Composable
fun LightSettings(entityId: String, title: String?, entities: Map<String, EntityState>, call: CallService, scenes: List<LightScene> = emptyList(), onClose: () -> Unit) {
    val light = entities[entityId]
    FullPage(title ?: light?.friendlyName ?: entityId, onClose) {
        if (light == null) {
            Text("Loading…")
        } else {
            LightSettingsBody(light, entities, call, scenes)
        }
    }
}

@Composable
private fun LightSettingsBody(light: EntityState, entities: Map<String, EntityState>, call: CallService, scenes: List<LightScene>) {
    val tone = toneColors(Tone.Lights)
    val members = light.lightMembers()
    val memberStates = members.mapNotNull { entities[it] }
    // A room with a ring of segments (the Cloakroom's Govee) is one device: its own light stands for it (switching the group would send a command to each segment).
    val device = colourSource(light, entities) ?: light
    val on = device.state == "on"
    val grouped = members.size > 1 && !isSegmentGroup(light) && device.entityId == light.entityId
    val tint = tintOf(device, MaterialTheme.colorScheme.onSurfaceVariant)
    var selected by rememberSaveable(light.entityId) { mutableStateOf<String?>(null) }
    val status = when {
        !device.available -> "Unavailable"
        !on -> "Off"
        grouped -> "${memberStates.count { it.state == "on" }} of ${memberStates.size} lights on"
        device.lightKind() == LightKind.Switch -> "On"
        else -> "On · ${brightnessOf(device)}%"
    }
    // The bar is a status bar: only the squircle switches the light. With a light of the room showing, tapping the bar closes it.
    PowerBar(
        device, on, tint, if (grouped && selected != null) "$status · tap to close ${entities[selected]?.friendlyName.orEmpty()}" else status, call,
        onBar = if (grouped && selected != null) ({ selected = null }) else null,
    )
    // A light a presence sensor looks after: whether someone is in, the countdown to switching it off, and the sensor. The bar is always there.
    val presence = presenceFor(light, device, entities, LocalPresence.current)
    val remaining = rememberRemaining(presence)
    if (presence != null) {
        PresenceBar(presenceFraction(presence, remaining), presenceLine(presence, remaining), tint, Modifier.height(Ui.TallTileHeight))
        Text(
            "${presence.sensor}${if (presence.delay > 0) " · off ${presence.delay} s after nobody is seen" else ""}",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    LightDetails(light, entities, call, scenes, selected) { selected = it }
}

/** What goes under a light's bar: a room's lights (with the one picked showing its own settings), a Govee dial, or the controls for one light. */
@Composable
private fun LightDetails(light: EntityState, entities: Map<String, EntityState>, call: CallService, scenes: List<LightScene>, selected: String?, select: (String?) -> Unit) {
    val tone = toneColors(Tone.Lights)
    val grey = toneColors(Tone.Grey)
    val members = light.lightMembers()
    val memberStates = members.mapNotNull { entities[it] }
    val grouped = members.size > 1 && !isSegmentGroup(light)
    val tint = tintOf(colourSource(light, entities) ?: light, MaterialTheme.colorScheme.onSurfaceVariant)
    if (!grouped) {
        LightControls(light, light.lightKind(), light.hasWhiteMode(), call, tone, grey, tint, scenes.takeIf { it.isNotEmpty() })
        LightRolesSection(listOf(light))
        return
    }
    // A room with a ring of segments (the Cloakroom's Govee) is one device: its own light is the whole thing, and the group would send a command
    // to each of the 13 segments. So it is one light, with its brightness, white tone or colour, scenes and hybrid scenes.
    val ring = memberStates.firstOrNull { isSegmentGroup(it) }
    val main = memberStates.firstOrNull { it.entityId != ring?.entityId }
    if (ring != null && main != null) {
        LightControls(main, main.lightKind(), main.hasWhiteMode(), call, tone, grey, tint, scenes.takeIf { it.isNotEmpty() })
        HybridSection(light, memberStates, call, tone, tint, true) {}
        LightRolesSection(memberStates)
        return
    } else {
        Text("LIGHTS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            memberStates.forEach { m -> MemberRow(m, call, tone, selected == m.entityId) { select(if (selected == m.entityId) null else m.entityId) } }
        }
        Text("Tap a light's sliders to show its settings here. The switch turns just that light on or off.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val picked = memberStates.firstOrNull { it.entityId == selected }
        if (picked != null) {
            // The light's own settings, here under the list; the room's scenes wait until it is closed.
            Text(picked.friendlyName.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            var nested by remember(picked.entityId) { mutableStateOf<String?>(null) }
            LightDetails(picked, entities, call, emptyList(), nested) { nested = it }
            return
        }
    }
    if (scenes.isNotEmpty()) ScenesSection(scenes, light.entityId, tint, call)
    HybridSection(light, memberStates, call, tone, tint, true) {}
    LightRolesSection(memberStates)
}

/** The page's top bar: the light's colour and its state, and beside it, on its own, the power squircle in the light's colour. Only the squircle switches the light. */
@Composable
private fun PowerBar(light: EntityState, on: Boolean, tint: Color, status: String, call: CallService, onBar: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().height(Ui.TallTileHeight), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(18.dp))
                .background(Brush.linearGradient(listOf(tint.copy(alpha = 0.30f), tint.copy(alpha = 0.06f))))
                .then(if (onBar != null) Modifier.clickable(onClickLabel = "Close the light's settings", onClick = onBar) else Modifier)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HaIcon(light.str("icon"), CdVectors.Bulb, tint, 28.dp)
            Text(status, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 2)
        }
        PowerSquircle(on, tint, enabled = light.available, label = if (on) "Turn off" else "Turn on", size = Ui.TallTileHeight) {
            call("light", if (on) "turn_off" else "turn_on", light.entityId, data())
        }
    }
}

/** A power button on its own: a squircle in [tint] when on (grey when off), with the same corners as the sliders. */
@Composable
internal fun PowerSquircle(on: Boolean, tint: Color, enabled: Boolean, label: String, size: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    val asked = rememberPending<Boolean>(on)
    val shown = asked.value(on)
    Box(
        Modifier.size(size).clip(RoundedCornerShape(18.dp))
            .background(if (shown) tint else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f))
            .clickable(enabled = enabled, onClickLabel = label) { asked.ask(!shown); onClick() },
        contentAlignment = Alignment.Center,
    ) {
        HaIcon("cd:power", CdVectors.Bulb, if (shown) Color(0xFF1B1F27) else MaterialTheme.colorScheme.onSurface, 26.dp)
    }
}

/** A titled block of settings on a rounded card: an icon, a title and a line saying what it is now. Always open; nothing hides behind it. */
@Composable
internal fun LightSection(icon: String, title: String, summary: String, tint: Color, open: Boolean = true, onToggle: () -> Unit = {}, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 14.dp, vertical = 4.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HaIcon(icon, CdVectors.Bulb, tint, 22.dp)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(Modifier.padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
    }
}

/**
 * The controls for what [kind] of light this is, brightness first and then sections that open when you edit them (the white tone, the
 * colour, the scenes). [light] is what the calls go to.
 */
@Composable
private fun LightControls(light: EntityState, kind: LightKind, whiteOk: Boolean, call: CallService, tone: ToneColors, grey: ToneColors, tint: Color, roomScenes: List<LightScene>? = null) {
    val ink = MaterialTheme.colorScheme.onSurface
    val id = light.entityId
    var colourTab by remember { mutableStateOf(if (light.str("color_mode") == "color_temp" && whiteOk) 0 else 1) }
    val min = (light.num("min_color_temp_kelvin") ?: 2000.0).toInt()
    val max = (light.num("max_color_temp_kelvin") ?: 6500.0).toInt().coerceAtLeast(min + 1)
    val kelvin = (light.num("color_temp_kelvin") ?: 2700.0).toInt().coerceIn(min, max)
    val whiteSummary = "$kelvin K · ${kelvinWord(kelvin)}"
    val looks = LocalSceneLooks.current
    when (kind) {
        LightKind.Switch -> {
            Text("This light only switches on and off, so there is nothing else to set.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LightKind.Dim -> {
            BrightnessBar(light, call)
            TileRow(listOf(10, 25, 50, 75, 100).map { p -> TileItem("mdi:brightness-6", "$p%", brightnessOf(light) == p && light.state == "on", showLabel = true) { call("light", "turn_on", id, data("brightness_pct" to p)) } }, tone, ink)
        }
        LightKind.White -> {
            BrightnessBar(light, call)
            LightSection("cd:sun", "White tone", whiteSummary, tint) {
                WhiteControls(light, call, tone)
            }
        }
        LightKind.Colour -> {
            BrightnessBar(light, call)
            // A gradient light (a Hue gradient strip) has a third tab: a gradient of its own along its length.
            val canGradient = id in LocalHybrids.current.gradientLights
            val gradientTab = canGradient && colourTab == 2
            val colourOpen = !gradientTab && (colourTab == 1 || !whiteOk)
            var gradient by remember(id) { mutableStateOf(HYBRID_GRADIENTS[1].second) }
            LightSection(
                if (gradientTab) "cd:bulb-group" else if (colourOpen) "cd:palette" else "cd:sun",
                if (gradientTab) "Gradient" else if (colourOpen) "Colour" else "White tone",
                if (gradientTab) "Colours along the light" else if (colourOpen) "Tap to change the colour" else whiteSummary, tint,
            ) {
                ColourTabs(light, whiteOk, canGradient, colourTab, { colourTab = it }, gradient, { gradient = it }, call, tone, ink)
            }
            // With no room list (a light picked from a room's list) it offers every scene; with one, exactly the scenes the room's card shows.
            if (roomScenes == null) {
                val scenes = (WHITE_SCENES.map { LightScene(it, id) } + looks.library.values.filter { it.hex.isNotEmpty() }.sortedBy { it.name }.map { LightScene(it.key, id) })
                ScenesSection(scenes, id, tint, call, "Applies to this light")
            }
        }
    }
    if (roomScenes != null && kind != LightKind.Switch) ScenesSection(roomScenes.map { it.copy(target = id) }, id, tint, call)
    // A Hue light's own effects (candle, fire, prism...): the ones the light says it has, and None to stop one.
    val effects = lightEffects(light)
    if (kind != LightKind.Switch && effects.isNotEmpty()) {
        val current = light.str("effect")?.takeIf { it != "off" && light.state == "on" }
        LightSection("cd:animation-play", "Effect", current?.let(::effectLabel) ?: "None", tint) {
            TileRow(
                listOf(TileItem("cd:close", "None", current == null && light.state == "on") { call("light", "turn_on", id, data("effect" to "off")) }) +
                    effects.map { e -> TileItem("cd:animation-play", effectLabel(e), current == e) { call("light", "turn_on", id, data("effect" to e)) } },
                tone, ink, perRow = 3, column = true,
            )
        }
    }
}

/** The effects a light offers, other than "off" (Hue lights list candle, fire, prism and the like); none for a light that has none. */
fun lightEffects(light: EntityState): List<String> =
    light.attributes.optJSONArray("effect_list")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty().filter { it.isNotBlank() && it != "off" }

/** "underwater" as "Underwater". */
fun effectLabel(effect: String): String = effect.replace('_', ' ').replaceFirstChar { it.uppercase() }

/**
 * Scene tiles three to a row, as on the room card; a tap plays the scene on [LightScene.target]. Pressing and holding one of [holdable]'s keys
 * calls [onHold], and [extra] is one more cell at the end (the New scene tile).
 */
@Composable
private fun SceneGrid(
    scenes: List<LightScene>, call: CallService, holdable: Set<String> = emptySet(), onHold: (LightScene) -> Unit = {},
    extra: (@Composable (Modifier) -> Unit)? = null,
) {
    val cells: List<@Composable (Modifier) -> Unit> = scenes.map { scene ->
        @Composable { m: Modifier ->
            SceneTile(scene, active = false, dimmed = false, modifier = m, h = Ui.TallTileHeight, onHold = if (scene.key in holdable) ({ onHold(scene) }) else null) {
                call("church_drive", "apply_scene", scene.target, data("scene" to scene.key))
            }
        }
    } + listOfNotNull(extra)
    Column(verticalArrangement = Arrangement.spacedBy(Ui.TileGap)) {
        cells.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Ui.TileGap)) {
                row.forEach { cell -> cell(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * The Scenes block of a light's or room's settings: its scenes, then the ones made in the app (yours), and for an administrator a New scene tile.
 * Pressing and holding one of yours changes or deletes it.
 */
@Composable
private fun ScenesSection(scenes: List<LightScene>, target: String, tint: Color, call: CallService, summary: String? = null) {
    val looks = LocalSceneLooks.current
    val admin = LocalIsAdmin.current
    val mine = looks.library.values.filter { it.custom }.sortedBy { it.name }
    val all = scenes + mine.filter { m -> scenes.none { it.key == m.key } }.map { LightScene(it.key, target) }
    var editing by remember { mutableStateOf<SceneDraft?>(null) }
    var menu by remember { mutableStateOf<LibraryScene?>(null) }
    val tone = toneColors(Tone.Lights)
    val ink = MaterialTheme.colorScheme.onSurface
    LightSection("cd:palette", "Scenes", summary ?: "${all.size} for this room${if (mine.isNotEmpty()) " · ${mine.size} yours" else ""}", tint) {
        SceneGrid(
            all, call,
            holdable = if (admin) mine.map { it.key }.toSet() else emptySet(),
            onHold = { sc -> menu = mine.firstOrNull { it.key == sc.key } },
            extra = if (admin) (@Composable { m: Modifier ->
                Box(
                    m.height(Ui.TallTileHeight).clip(RoundedCornerShape(18.dp)).background(ink.copy(alpha = 0.10f)).clickable(onClickLabel = "Make a new scene") { editing = newSceneDraft() },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        HaIcon("cd:plus", CdVectors.Bulb, ink, 20.dp)
                        CentredText("New scene", ink, 10.sp, Modifier.padding(horizontal = 3.dp), lineHeight = 12.sp)
                    }
                }
            }) else null,
        )
        val m = menu
        if (m != null) {
            Text(m.name, style = MaterialTheme.typography.titleSmall)
            TileRow(
                listOf(
                    TileItem("cd:pencil", "Change", false) { editing = draftOf(m); menu = null },
                    TileItem("cd:close", "Close", false) { menu = null },
                ),
                tone, ink, perRow = 2,
            )
        } else if (admin && mine.isNotEmpty()) {
            Text("Press and hold one of yours to change or delete it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    editing?.let { SceneEditorPage(it) { editing = null } }
}

/** The White, Colour and (for a gradient light) Gradient tabs and what each shows. [colourTab] is 0 white, 1 colour, 2 gradient. */
@Composable
internal fun ColourTabs(
    light: EntityState, whiteOk: Boolean, canGradient: Boolean, colourTab: Int, setTab: (Int) -> Unit,
    gradient: List<String>, setGradient: (List<String>) -> Unit, call: CallService, tone: ToneColors, ink: Color,
) {
    val gradientTab = canGradient && colourTab == 2
    val colourOpen = !gradientTab && (colourTab == 1 || !whiteOk)
    if (whiteOk || canGradient) {
        TileRow(
            listOfNotNull(
                if (whiteOk) TileItem("cd:sun", "White", !gradientTab && !colourOpen) { setTab(0) } else null,
                TileItem("cd:palette", "Colour", colourOpen) { setTab(1) },
                if (canGradient) TileItem("cd:bulb-group", "Gradient", gradientTab) { setTab(2) } else null,
            ),
            tone, ink,
        )
    }
    if (gradientTab) GradientEditor(gradient, tone, ink) { next -> setGradient(next); call("church_drive", "apply_gradient", light.entityId, data("colours" to JSONArray(next))) }
    else if (colourOpen) ColourPanel(light, call, tone) else WhiteControls(light, call, tone)
}

/** A white light's tone: the warm-to-cool bar and, under it, the white scenes (Bright, Cool bright, Dimmed, Relax, Nightlight...). */
@Composable
private fun WhiteControls(light: EntityState, call: CallService, tone: ToneColors) {
    val min = (light.num("min_color_temp_kelvin") ?: 2000.0).toInt()
    val max = (light.num("max_color_temp_kelvin") ?: 6500.0).toInt().coerceAtLeast(min + 1)
    val k = (light.num("color_temp_kelvin") ?: 2700.0).toInt().coerceIn(min, max)
    val brush = Brush.horizontalGradient((0..8).map { i -> rgbColour(kelvinRgb(min + (max - min) * i / 8)) })
    BarSlider(
        "Colour temperature", "$k K · ${kelvinWord(k)}", (k - min).toFloat() / (max - min), brush, thumbOnly = true,
        onSet = { f -> call("light", "turn_on", light.entityId, data("color_temp_kelvin" to (min + (max - min) * f).roundToInt())) },
    )
    SceneGrid(WHITE_SCENES.map { LightScene(it, light.entityId) }, call)
}

/** The brightness bar: moves in 5% steps, and shows the step while you drag. */
@Composable
private fun BrightnessBar(light: EntityState, call: CallService) {
    val pct = brightnessOf(light)
    val tint = tintOf(light, CliveColours.Lights.colour)
    BarSlider(
        "Brightness", "${if (light.state == "on") pct else 0}%", if (light.state == "on") pct / 100f else 0f,
        Brush.horizontalGradient(listOf(tint.copy(alpha = 0.35f), tint.copy(alpha = 0.8f))), thumbOnly = false,
        snap = { f -> snapBrightness(f) / 100f }, valueOf = { f -> "${snapBrightness(f)}%" },
        onSet = { f -> call("light", "turn_on", light.entityId, data("brightness_pct" to snapBrightness(f))) },
    )
}

/** One colour as a round swatch: ringed when it is the light's colour, with a dashed edge when it is one of the saved ones. */
@Composable
private fun Swatch(colour: Pair<Double, Double>, current: Pair<Double, Double>?, saved: Boolean, onClick: () -> Unit) {
    val ink = MaterialTheme.colorScheme.onSurface
    val selected = current != null && sameColour(colour, current)
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(rgbColour(hsToRgb(colour.first, colour.second)))
            .then(if (selected) Modifier.border(3.dp, ink, CircleShape) else Modifier)
            .then(
                if (saved) Modifier.drawBehind {
                    drawCircle(ink, radius = size.minDimension / 2f - 1.dp.toPx(), style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))))
                } else Modifier,
            )
            .clickable(onClick = onClick),
    )
}

/**
 * A colour light's colours: presets, the colours you saved (up to eight), a button to save the current colour (or take it out of the
 * saved ones) and the eyedropper button that opens the colour wheel.
 */
@Composable
private fun ColourPanel(light: EntityState, call: CallService, tone: ToneColors) {
    val ink = MaterialTheme.colorScheme.onSurface
    val current = light.attributes.optJSONArray("hs_color")?.let { it.optDouble(0) to it.optDouble(1) }
    val savedColours = LocalSavedColours.current
    val saved = savedColours.colours
    val setSaved = savedColours.set
    var picker by remember { mutableStateOf(false) }
    fun send(c: Pair<Double, Double>) = call("light", "turn_on", light.entityId, data("hs_color" to JSONArray(listOf(c.first, c.second))))
    val label = MaterialTheme.typography.labelMedium
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Text("Presets", style = label, color = muted)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        SWATCHES.forEach { c -> Swatch(c, current, false) { send(c) } }
    }
    Text("Saved", style = label, color = muted)
    val items: List<Pair<Double, Double>?> = saved + (if (saved.size < MAX_SAVED_COLOURS) listOf<Pair<Double, Double>?>(null) else emptyList())
    items.chunked(6).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            row.forEach { c ->
                if (c != null) Swatch(c, current, true) { send(c) }
                else Box(
                    Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable(enabled = current != null, onClickLabel = "Save this colour") { current?.let { setSaved(toggleSavedColour(saved, it)) } },
                    contentAlignment = Alignment.Center,
                ) { HaIcon("cd:plus", CdVectors.Bulb, ink, 18.dp) }
            }
        }
    }
    val isSaved = current != null && saved.any { sameColour(it, current) }
    TileRow(
        listOf(
            TileItem(if (isSaved) "cd:bin" else "cd:plus", if (isSaved) "Remove saved" else "Save this colour", false) { current?.let { setSaved(toggleSavedColour(saved, it)) } },
            TileItem("cd:eyedropper", "Colour picker", picker) { picker = !picker },
        ),
        tone, ink, perRow = 2,
    )
    if (picker) ColourWheel(light, call)
}

/** A hue and saturation wheel for a light: drag or tap to pick a colour; it is sent to the light when you let go. */
@Composable
private fun ColourWheel(light: EntityState, call: CallService) {
    val current = light.attributes.optJSONArray("hs_color")?.let { it.optDouble(0) to it.optDouble(1) } ?: (30.0 to 60.0)
    HsWheel(current) { pick -> call("light", "turn_on", light.entityId, data("hs_color" to JSONArray(listOf(pick.first, pick.second)))) }
}

/** A hue and saturation wheel: drag or tap to pick a colour; [onPick] is called when you let go. It starts on [initial] and follows it when that changes. */
@Composable
internal fun HsWheel(initial: Pair<Double, Double>, onPick: (Pair<Double, Double>) -> Unit) {
    var pick by remember(initial) { mutableStateOf(initial) }
    var sizePx by remember { mutableStateOf(1f) }
    fun at(o: Offset): Pair<Double, Double> {
        val r = sizePx / 2.0
        return wheelHueSat(o.x - r, o.y - r, r)
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Canvas(
            modifier = Modifier.size(260.dp)
                .onSizeChanged { sizePx = it.width.toFloat().coerceAtLeast(1f) }
                .pointerInput(Unit) { detectTapGestures { pick = at(it); onPick(pick) } }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { pick = at(it) },
                        onDragEnd = { onPick(pick) },
                    ) { change, _ -> pick = at(change.position) }
                },
        ) {
            val r = size.minDimension / 2f
            drawCircle(Brush.sweepGradient((0..6).map { Color.hsv((it * 60f) % 360f, 1f, 1f) }, center))
            drawCircle(Brush.radialGradient(listOf(Color.White, Color.Transparent), center = center, radius = r))
            val (px, py) = wheelPoint(pick.first, pick.second)
            val at = Offset(center.x + (px * r).toFloat(), center.y + (py * r).toFloat())
            drawCircle(rgbColour(hsToRgb(pick.first, pick.second)), radius = 16.dp.toPx(), center = at)
            drawCircle(Color.White, radius = 16.dp.toPx(), center = at, style = Stroke(width = 3.dp.toPx()))
        }
    }
}

/** One light inside a group: its icon in its own colour, name and state, its switch, and its sliders icon that shows its settings under the list. */
@Composable
private fun MemberRow(light: EntityState, call: CallService, tone: ToneColors, selected: Boolean, onOpen: () -> Unit) {
    val on = light.state == "on"
    val tint = tintOf(light, MaterialTheme.colorScheme.onSurfaceVariant)
    val opens = opensSettings(light)
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .then(if (selected) Modifier.border(2.dp, tone.accent, RoundedCornerShape(14.dp)) else Modifier)
            .clickable { if (opens) onOpen() else if (light.available) call("light", if (on) "turn_off" else "turn_on", light.entityId, data()) }
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HaIcon(light.str("icon"), CdVectors.Bulb, tint, 26.dp)
        Column(Modifier.weight(1f)) {
            Text(light.friendlyName, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            Text(
                lightKindLabel(light) + " · " + when { !light.available -> "Unavailable"; !on -> "Off"; light.lightKind() == LightKind.Switch -> "On"; else -> "${brightnessOf(light)}%" },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AppSwitch(checked = on, enabled = light.available, onCheckedChange = { call("light", if (on) "turn_off" else "turn_on", light.entityId, data()) })
        if (opens) HaIcon("cd:sliders", CdVectors.Info, if (selected) tone.accent else MaterialTheme.colorScheme.onSurfaceVariant, 22.dp)
    }
}

/** A group made of a ring's segments (Govee's backlight): one light with a colour per piece, so it is edited as one light. */
internal fun isSegmentGroup(light: EntityState): Boolean = light.lightMembers().let { m -> m.size > 1 && m.all { "_segment_" in it } }

/** What a light is, for the line under its name in a room's list: "White tones", "Full colour", "13 segments"... */
internal fun lightKindLabel(light: EntityState): String = when {
    isSegmentGroup(light) -> "${light.lightMembers().size} segments"
    light.lightMembers().size > 1 -> "${light.lightMembers().size} lights"
    else -> when (light.lightKind()) {
        LightKind.Switch -> "On and off"
        LightKind.Dim -> "Single colour"
        LightKind.White -> "White tones"
        LightKind.Colour -> "Full colour"
    }
}
