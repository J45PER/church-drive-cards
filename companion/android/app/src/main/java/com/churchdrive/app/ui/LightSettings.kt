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
import androidx.compose.material3.Switch
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
    val on = light.state == "on"
    val members = light.lightMembers()
    val memberStates = members.mapNotNull { entities[it] }
    val grouped = members.size > 1 && !isSegmentGroup(light)
    val tint = tintOf(colourSource(light, entities) ?: light, MaterialTheme.colorScheme.onSurfaceVariant)
    var selected by rememberSaveable(light.entityId) { mutableStateOf<String?>(null) }
    val status = when {
        !light.available -> "Unavailable"
        !on -> "Off"
        grouped -> "${memberStates.count { it.state == "on" }} of ${memberStates.size} lights on"
        light.lightKind() == LightKind.Switch -> "On"
        else -> "On · ${brightnessOf(light)}%"
    }
    // The bar is a status bar: only the squircle switches the light. With a light of the room showing, tapping the bar closes it.
    PowerBar(
        light, on, tint, if (grouped && selected != null) "$status · tap to close ${entities[selected]?.friendlyName.orEmpty()}" else status, call,
        onBar = if (grouped && selected != null) ({ selected = null }) else null,
    )
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
        LightControls(light, light.lightKind(), light.hasWhiteMode(), call, tone, grey, tint)
        return
    }
    // A room with a ring of segments (the Cloakroom's Up light) is a dial: the main light in the middle, a piece of the ring for each segment.
    val ring = memberStates.firstOrNull { isSegmentGroup(it) }
    val main = memberStates.firstOrNull { it.entityId != ring?.entityId }
    val segments = ring?.lightMembers()?.mapNotNull { entities[it] }?.sortedBy { it.entityId }.orEmpty()
    if (ring != null && main != null && segments.isNotEmpty()) {
        GoveeParts(light.entityId, main, ring, segments, call, tone, tint)
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
private fun LightControls(light: EntityState, kind: LightKind, whiteOk: Boolean, call: CallService, tone: ToneColors, grey: ToneColors, tint: Color) {
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
            val scenes = (WHITE_SCENES.map { LightScene(it, id) } + looks.library.values.filter { it.hex.isNotEmpty() }.sortedBy { it.name }.map { LightScene(it.key, id) })
            ScenesSection(scenes, id, tint, call, "Applies to this light")
        }
    }
}

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

/**
 * A wide bar you drag across (or tap) to set a level; it calls [onSet] when you let go. The slider is part of the bar: the fill is the
 * level and a white handle sits at its end. [snap] moves the level to its nearest step and [valueOf] words the level while you drag.
 */
@Composable
internal fun BarSlider(
    label: String, value: String, fraction: Float, fill: Brush, thumbOnly: Boolean, onSet: (Float) -> Unit,
    snap: ((Float) -> Float)? = null, valueOf: ((Float) -> String)? = null,
) {
    var drag by remember(fraction) { mutableStateOf<Float?>(null) }
    val asked = rememberPending<Float>(fraction)
    var width by remember { mutableStateOf(1) }
    val shown = drag ?: asked.asked ?: fraction
    fun at(x: Float): Float = (x / width).coerceIn(0f, 1f).let { snap?.invoke(it) ?: it }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(if (drag != null && valueOf != null) valueOf(shown) else value, style = MaterialTheme.typography.bodyMedium)
        }
        Box(
            modifier = Modifier.fillMaxWidth().height(60.dp).clip(RoundedCornerShape(18.dp))
                .background(if (thumbOnly) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHigh)
                .onSizeChanged { width = it.width.coerceAtLeast(1) }
                .pointerInput(Unit) { detectTapGestures { asked.ask(at(it.x)); onSet(at(it.x)) } }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { drag = at(it.x) },
                        onDragEnd = { drag?.let { asked.ask(it); onSet(it) }; drag = null },
                        onDragCancel = { drag = null },
                    ) { change, _ -> drag = at(change.position.x) }
                },
        ) {
            if (thumbOnly) Box(Modifier.fillMaxSize().background(fill))
            else Box(Modifier.fillMaxHeight().fillMaxWidth(shown).background(fill))
            Box(Modifier.align(Alignment.CenterStart).fillMaxWidth(shown.coerceAtLeast(0.04f)), contentAlignment = Alignment.CenterEnd) {
                Box(Modifier.padding(end = 8.dp).size(width = 4.dp, height = 36.dp).clip(RoundedCornerShape(2.dp)).background(Color.White))
            }
        }
    }
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
        Switch(checked = on, enabled = light.available, onCheckedChange = { call("light", if (on) "turn_off" else "turn_on", light.entityId, data()) })
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

private const val SEGMENT_DARK = 8

/** A Govee segment has no power of its own, so it is "off" when it is set to black. */
private fun segmentOn(s: EntityState): Boolean = s.state == "on" && (s.rgb()?.let { it.max() > SEGMENT_DARK } ?: true)

private enum class DialZone { Centre, Gauge, Rim }

/**
 * The parts of a light with a ring of segments (the Cloakroom's Govee), as one circle: the main light in the middle (tap to pick it, press
 * and hold to switch it, drag round its ring for brightness) and a rounded wedge for each segment. What is picked stays bright and the rest dims; an
 * off part is plain acrylic. A bar under the dial shows the picked part's level (drag it) and, for the segments, a power squircle; the picked part's
 * colours open under it. The dial draws the house's own record of the fitting ([LocalFittings]) when it has one, and changes go through it, so the main
 * light's white and the ring's colours are set together in the order the Govee needs.
 */
@Composable
private fun GoveeParts(room: String, main: EntityState, ring: EntityState, segments: List<EntityState>, call: CallService, tone: ToneColors, tint: Color) {
    val ink = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val surface = MaterialTheme.colorScheme.surfaceContainer
    val acrylic = ink.copy(alpha = 0.22f).compositeOver(surface)
    val haptic = LocalHapticFeedback.current
    val fit = LocalFittings.current.byRoom[room]
    val api = LocalHaApi.current
    val scope = rememberCoroutineScope()
    var sel by rememberSaveable(ring.entityId) { mutableStateOf<String?>(null) }
    var dragPct by remember { mutableStateOf<Int?>(null) }
    var colourTab by remember { mutableIntStateOf(1) }
    var gradient by remember { mutableStateOf(HYBRID_GRADIENTS[1].second) }
    var keepRing by rememberSaveable(ring.entityId) { mutableStateOf(false) }
    val n = segments.size
    fun rgbOf(l: List<Int>): Color = if (l.size == 3) Color(l[0], l[1], l[2]) else tint

    val mainOn = fit?.mainOn ?: (main.state == "on")
    val mainBase = fit?.let { rgbOf(it.mainShown) } ?: tintOf(main, tint)
    val mainColour = if (mainOn) mainBase else acrylic
    val mainLevel = fit?.mainBrightness ?: brightnessOf(main)
    val mainPct = dragPct ?: if (mainOn) mainLevel else 0
    fun segOn(i: Int): Boolean = if (fit != null) fit.ringOn && fit.ringSegments.getOrNull(i)?.dark == false else segmentOn(segments[i])
    fun segColour(i: Int): Color = fit?.ringSegments?.getOrNull(i)?.let { rgbOf(it.rgb) } ?: tintOf(segments[i], tint)
    fun segLevel(i: Int): Int = fit?.ringSegments?.getOrNull(i)?.brightness ?: brightnessOf(segments[i])
    val ringOn = fit?.ringOn ?: segments.any { segmentOn(it) }
    val remembered = remember(ring.entityId) { mutableMapOf<Int, List<Int>>() }

    fun fitSet(main: JSONObject? = null, ring: JSONObject? = null, keep: Boolean = false) {
        scope.launch { api?.ask("church_drive/fitting/set", fittingBody(room, main, ring, keep)) }
    }
    fun setLevel(id: String, pct: Int) = call("light", "turn_on", id, data("brightness_pct" to pct))
    fun setMainLevel(pct: Int) = if (fit != null) fitSet(main = mainRequest(on = true, brightness = pct)) else setLevel(main.entityId, pct)
    fun toggleMain() = if (fit != null) fitSet(main = mainRequest(on = !mainOn)) else call("light", if (mainOn) "turn_off" else "turn_on", main.entityId, data())
    fun setRingLevel(pct: Int) = if (fit != null) fitSet(ring = ringLevelRequest(pct)) else setLevel(ring.entityId, pct)
    fun setSegLevel(i: Int, pct: Int) =
        if (fit != null) fitSet(ring = ringSegmentsRequest(fit.ringSegments.replaced(i, fit.ringSegments[i].copy(brightness = pct)))) else setLevel(segments[i].entityId, pct)
    fun toggleSegment(i: Int) {
        if (fit != null) {
            val cur = fit.ringSegments[i]
            val next = if (segOn(i)) { remembered[i] = cur.rgb; cur.copy(rgb = listOf(0, 0, 0)) } else cur.copy(rgb = remembered[i] ?: listOf(255, 200, 140))
            fitSet(ring = ringSegmentsRequest(fit.ringSegments.replaced(i, next)))
        } else {
            val s = segments[i]
            if (segmentOn(s)) {
                s.rgb()?.let { remembered[i] = it.toList() }
                call("light", "turn_on", s.entityId, data("rgb_color" to JSONArray(listOf(0, 0, 0))))
            } else {
                call("light", "turn_on", s.entityId, data("rgb_color" to JSONArray(remembered[i] ?: listOf(255, 200, 140))))
            }
        }
    }
    fun allSegmentsOn() {
        if (fit != null) fitSet(ring = ringSegmentsRequest(fit.ringSegments.mapIndexed { i, s -> if (s.dark) s.copy(rgb = remembered[i] ?: listOf(255, 200, 140)) else s }))
        else segments.indices.filter { !segmentOn(segments[it]) }.forEach { toggleSegment(it) }
    }
    fun toggleRing() = if (fit != null) fitSet(ring = JSONObject().put("on", !fit.ringOn)) else segments.indices.forEach { if (ringOn == segmentOn(segments[it])) toggleSegment(it) }

    LightSection("cd:bulb-group", "Parts", "Govee · ${segments.size} segments", tint) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).padding(4.dp), contentAlignment = Alignment.Center) {
            var ringDrag = false
            fun zone(size: androidx.compose.ui.unit.IntSize, p: Offset): DialZone {
                val half = minOf(size.width, size.height) / 2f
                val u = half / 120f
                val d = hypot(p.x - size.width / 2f, p.y - size.height / 2f) / u
                return if (d <= 42f) DialZone.Centre else if (d <= 74f) DialZone.Gauge else DialZone.Rim
            }
            fun angleOf(size: androidx.compose.ui.unit.IntSize, p: Offset): Double =
                (Math.toDegrees(atan2((p.y - size.height / 2f).toDouble(), (p.x - size.width / 2f).toDouble())) + 90.0 + 360.0) % 360.0
            Canvas(
                Modifier.fillMaxSize()
                    .pointerInput(segments, main, sel, fit) {
                        detectTapGestures(
                            onTap = { pos ->
                                when (zone(size, pos)) {
                                    DialZone.Rim -> if (n > 0) {
                                        val id = (angleOf(size, pos) / (360.0 / n)).toInt().coerceIn(0, n - 1).toString()
                                        sel = if (sel == id) null else id
                                    }
                                    else -> sel = if (sel == "main") null else "main"
                                }
                            },
                            onLongPress = { pos ->
                                if (zone(size, pos) == DialZone.Centre && main.available) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    toggleMain()
                                }
                            },
                        )
                    }
                    .pointerInput(main, fit) {
                        detectDragGestures(
                            onDragStart = { pos -> ringDrag = zone(size, pos) == DialZone.Gauge; if (ringDrag) { sel = "main"; dragPct = ((angleOf(size, pos) / 360.0 * 20).roundToInt() * 5).coerceIn(5, 100) } },
                            onDragEnd = { if (ringDrag) dragPct?.let { setMainLevel(it) }; dragPct = null; ringDrag = false },
                            onDragCancel = { dragPct = null; ringDrag = false },
                        ) { change, _ -> if (ringDrag) dragPct = ((angleOf(size, change.position) / 360.0 * 20).roundToInt() * 5).coerceIn(5, 100) }
                    },
            ) {
                val u = size.minDimension / 2f / 120f
                val c = Offset(size.width / 2f, size.height / 2f)
                val sweep = 360f / n
                val gap = 3.5f
                val rc = 5f * u
                val r1 = 80f * u + rc
                val r2 = 108f * u - rc
                val pickedDim = if (sel == null) 1f else 0.62f
                for (i in 0 until n) {
                    val isSel = sel == i.toString()
                    val dim = if (sel == null || isSel || sel == "ring") 1f else pickedDim
                    val on = segOn(i)
                    val base = if (on) segColour(i).copy(alpha = (0.5f + segLevel(i) / 100f * 0.5f) * dim).compositeOver(surface) else acrylic.copy(alpha = dim).compositeOver(surface)
                    val a0 = -90f + i * sweep + gap / 2f
                    val span = sweep - gap
                    val da = Math.toDegrees((rc / r1).toDouble()).toFloat()
                    val db = Math.toDegrees((rc / r2).toDouble()).toFloat()
                    val outer = androidx.compose.ui.geometry.Rect(c.x - r2, c.y - r2, c.x + r2, c.y + r2)
                    val inner = androidx.compose.ui.geometry.Rect(c.x - r1, c.y - r1, c.x + r1, c.y + r1)
                    val path = androidx.compose.ui.graphics.Path().apply {
                        arcTo(outer, a0 + db, span - 2 * db, true)
                        arcTo(inner, a0 + span - da, -(span - 2 * da), false)
                        close()
                    }
                    drawPath(path, base)
                    drawPath(path, base, style = Stroke(width = 2f * rc, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                }
                val mainDim = if (sel == null || sel == "main") 1f else 0.55f
                drawCircle(ink.copy(alpha = 0.08f * mainDim), radius = 72f * u, center = c)
                val gr = 56f * u
                drawCircle(ink.copy(alpha = 0.18f * mainDim), radius = gr, center = c, style = Stroke(12f * u))
                val gc = mainColour.copy(alpha = mainDim)
                if (mainOn) {
                    drawArc(gc, -90f, 360f * mainPct / 100f, false, Offset(c.x - gr, c.y - gr), Size(gr * 2, gr * 2), style = Stroke(12f * u, cap = androidx.compose.ui.graphics.StrokeCap.Round))
                    val ang = Math.toRadians((-90.0 + 360.0 * mainPct / 100.0))
                    val tp = Offset(c.x + (gr * Math.cos(ang)).toFloat(), c.y + (gr * Math.sin(ang)).toFloat())
                    drawCircle(Color.White.copy(alpha = mainDim), radius = 8f * u, center = tp)
                    drawCircle(gc, radius = 8f * u, center = tp, style = Stroke(3f * u))
                }
                drawCircle(gc, radius = 36f * u, center = c)
            }
            HaIcon("cd:power", CdVectors.Bulb, if (mainOn) Color(0xFF1B1F27) else ink, 28.dp, Modifier.alpha(if (sel == null || sel == "main") 1f else 0.55f))
        }
        Text(
            "Tap a segment to pick it, or the ring to pick the main light; tap again to let go. Press and hold the middle to switch the main light.",
            style = MaterialTheme.typography.bodySmall, color = muted, modifier = Modifier.fillMaxWidth(),
        )
        // Pick every segment at once, or put back any that were switched off.
        TileRow(
            listOf(
                TileItem("cd:bulb-group", "Whole ring", sel == "ring") { sel = if (sel == "ring") null else "ring" },
                TileItem("cd:sun", "All segments on", false) { allSegmentsOn() },
            ),
            tone, ink, perRow = 2,
        )
        val pickedIndex = sel?.toIntOrNull()
        val isMain = sel == "main"
        val isRing = sel == "ring"
        if (isMain || isRing || (pickedIndex != null && pickedIndex in 0 until n)) {
            val i = pickedIndex ?: 0
            val name = if (isMain) "Main light" else if (isRing) "Backlight ring" else "Segment ${i + 1}"
            val on = if (isMain) mainOn else if (isRing) ringOn else segOn(i)
            val level = if (isMain) mainPct else if (isRing) segLevel(0) else segLevel(i)
            val col = if (isMain) mainBase else if (isRing) (segments.indices.firstOrNull { segOn(it) }?.let { segColour(it) } ?: segColour(0)) else segColour(i)
            PartBar(name, if (isMain) "cd:bulb-lit" else "cd:bulb-group", on, level, col, onLevel = { pct -> if (isMain) setMainLevel(pct) else if (isRing) setRingLevel(pct) else setSegLevel(i, pct) })
            if (!isMain) {
                // Segments have no power of their own: the squircle sets them to black and puts their colour back.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PowerSquircle(on, col, true, if (on) "Turn off" else "Turn on", Ui.TallTileHeight) { if (isRing) toggleRing() else toggleSegment(i) }
                    Text(if (on) "Switch off (goes dark)" else "Switch on", Modifier.weight(1f).align(Alignment.CenterVertically), style = MaterialTheme.typography.bodyMedium, color = muted)
                }
            }
            LightSection(
                if (isMain) "cd:bulb-lit" else "cd:bulb-group", name,
                if (isMain) "White tone or whole-fitting colour" else if (isRing) "${segments.size} segments together" else "One segment of the ring", col,
            ) {
                if (fit != null && isMain) {
                    MainToneControls(fit, keepRing, { keepRing = it }, { req, keep -> fitSet(main = req, keep = keep) }, tone, ink)
                } else if (fit != null) {
                    FittingColours(if (isRing) fit.ringSegments.firstOrNull { !it.dark }?.rgb else fit.ringSegments.getOrNull(i)?.rgb) { rgb ->
                        if (isRing) fitSet(ring = ringColourRequest(rgb))
                        else fitSet(ring = ringSegmentsRequest(fit.ringSegments.replaced(i, fit.ringSegments[i].copy(rgb = rgb))))
                    }
                } else if (isMain) {
                    // Without the house's record, the Govee sends a colour to the whole fitting, so the down light is white only.
                    WhiteControls(main, call, tone)
                    Text("The down light is white only. Colour the ring or its segments for colour.", style = MaterialTheme.typography.bodySmall, color = muted)
                } else {
                    ColourTabs(if (isRing) ring else segments[i], false, false, 1, { colourTab = it }, gradient, { gradient = it }, call, tone, ink)
                }
            }
        }
    }
}

/** The main light's controls from the house's record: a white tone (down light only, a coloured ring keeps its colours) or a colour for the whole fitting. */
@Composable
private fun MainToneControls(fit: Fitting, keepRing: Boolean, setKeep: (Boolean) -> Unit, set: (JSONObject, Boolean) -> Unit, tone: ToneColors, ink: Color) {
    var tab by remember { mutableIntStateOf(if (fit.mainMode == "colour") 1 else 0) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    TileRow(listOf(TileItem("cd:sun", "White", tab == 0) { tab = 0 }, TileItem("cd:palette", "Colour", tab == 1) { tab = 1 }), tone, ink, perRow = 2)
    if (tab == 0) {
        val min = 2200
        val max = 6500
        val k = fit.mainKelvin.coerceIn(min, max)
        BarSlider(
            "Colour temperature", "$k K · ${kelvinWord(k)}", (k - min).toFloat() / (max - min),
            Brush.horizontalGradient((0..8).map { rgbColour(kelvinRgb(min + (max - min) * it / 8)) }), thumbOnly = true,
            onSet = { f -> set(mainRequest(on = true, mode = "white", kelvin = (min + (max - min) * f).roundToInt()), false) },
        )
        TileRow(
            whitePresets(min, max).map { (name, k2) -> TileItem("cd:sun", name, fit.mainMode == "white" && kotlin.math.abs(k - k2) < 150) { set(mainRequest(on = true, mode = "white", kelvin = k2), false) } },
            tone, ink, perRow = 3,
        )
        Text("White lights the down light only. A coloured ring keeps its colours.", style = MaterialTheme.typography.bodySmall, color = muted)
    } else {
        Text("A colour on the down light paints the whole fitting, ring included.", style = MaterialTheme.typography.bodySmall, color = muted)
        FittingColours(fit.mainRgb.takeIf { fit.mainMode == "colour" }) { rgb -> set(mainRequest(on = true, mode = "colour", rgb = rgb), keepRing) }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Keep the ring's colours", style = MaterialTheme.typography.bodyLarge)
                Text("Puts them back after, which takes longer for a ring of many colours.", style = MaterialTheme.typography.bodySmall, color = muted)
            }
            Switch(keepRing, setKeep)
        }
    }
}

/** Preset swatches and a colour wheel for a part of the fitting; [onPick] gets the colour as red, green and blue. */
@Composable
private fun FittingColours(current: List<Int>?, onPick: (List<Int>) -> Unit) {
    val ink = MaterialTheme.colorScheme.onSurface
    val tone = toneColors(Tone.Lights)
    val currentHs = current?.takeIf { it.size == 3 }?.let { hexToHs("#%02x%02x%02x".format(it[0], it[1], it[2])) }
    Text("Presets", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        SWATCHES.forEach { c -> Swatch(c, currentHs, false) { onPick(hsToRgb(c.first, c.second).toList()) } }
    }
    var wheel by remember { mutableStateOf(false) }
    TileRow(listOf(TileItem("cd:eyedropper", "Custom colour", wheel) { wheel = !wheel }), tone, ink, perRow = 1)
    if (wheel) HsWheel(currentHs ?: (30.0 to 60.0)) { onPick(hsToRgb(it.first, it.second).toList()) }
}

/** A level bar for the picked part: a pill filled to its level that you drag across, with its name and level on it. */
@Composable
private fun PartBar(name: String, icon: String, on: Boolean, level: Int, colour: Color, onLevel: (Int) -> Unit) {
    var drag by remember(level) { mutableStateOf<Float?>(null) }
    val asked = rememberPending<Float>(level)
    var width by remember { mutableIntStateOf(1) }
    val shown = drag ?: asked.asked ?: (if (on) level / 100f else 0f)
    fun at(x: Float) = ((x / width * 20).roundToInt() * 5).coerceIn(5, 100) / 100f
    Box(
        Modifier.fillMaxWidth().height(Ui.TallTileHeight).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = if (on) 0.10f else 0.06f))
            .onSizeChanged { width = it.width.coerceAtLeast(1) }
            .pointerInput(on) {
                detectHorizontalDragGestures(
                    onDragStart = { drag = at(it.x) },
                    onDragEnd = { drag?.let { asked.ask(it); onLevel((it * 100).roundToInt()) }; drag = null },
                    onDragCancel = { drag = null },
                ) { change, _ -> drag = at(change.position.x) }
            },
    ) {
        if (on || drag != null) Box(Modifier.fillMaxHeight().fillMaxWidth(shown).background(colour.copy(alpha = 0.45f)))
        Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HaIcon(icon, CdVectors.Bulb, if (on) colour else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f), 26.dp)
            Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
            Text(if (on || drag != null) "${(shown * 100).roundToInt()}%" else "Off", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
