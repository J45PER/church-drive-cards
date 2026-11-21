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
import kotlin.math.hypot
import kotlin.math.atan2
import kotlin.math.roundToInt

private fun rgbColour(c: IntArray) = Color(c[0], c[1], c[2])

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
 * its lights, each with its own switch, and a tap on one opens that light's own settings.
 */
@Composable
fun LightSettings(entityId: String, title: String?, entities: Map<String, EntityState>, call: CallService, scenes: List<LightScene> = emptyList(), onClose: () -> Unit) {
    val light = entities[entityId]
    var member by remember { mutableStateOf<String?>(null) }
    FullPage(title ?: light?.friendlyName ?: entityId, onClose) {
        if (light == null) {
            Text("Loading…")
        } else {
            LightSettingsBody(light, entities, call, scenes) { member = it }
        }
    }
    member?.let { LightSettings(it, null, entities, call) { member = null } }
}

@Composable
private fun LightSettingsBody(light: EntityState, entities: Map<String, EntityState>, call: CallService, scenes: List<LightScene>, openMember: (String) -> Unit) {
    val tone = toneColors(Tone.Lights)
    val grey = toneColors(Tone.Grey)
    val on = light.state == "on"
    val members = light.lightMembers()
    val memberStates = members.mapNotNull { entities[it] }
    // A ring of segments is one light with a colour per piece, not a room of lights: it opens to the colour controls.
    val grouped = members.size > 1 && !isSegmentGroup(light)
    val tint = tintOf(light, MaterialTheme.colorScheme.onSurfaceVariant)
    val status = when {
        !light.available -> "Unavailable"
        !on -> "Off"
        grouped -> "${memberStates.count { it.state == "on" }} of ${memberStates.size} lights on"
        light.lightKind() == LightKind.Switch -> "On"
        else -> "On · ${brightnessOf(light)}%"
    }
    PowerBar(light, on, tint, tone, status, call)

    if (grouped) {
        // A room with a ring of segments (the Cloakroom's Up light) shows it as a dial: the main light in the middle, a piece of the ring each segment.
        val ring = memberStates.firstOrNull { isSegmentGroup(it) }
        val main = memberStates.firstOrNull { it.entityId != ring?.entityId }
        if (ring != null && main != null) {
            val segments = ring.lightMembers().mapNotNull { entities[it] }.sortedBy { it.entityId }
            if (segments.isNotEmpty()) {
                SegmentDial(main, segments, tint, { openMember(main.entityId) }) { openMember(it.entityId) }
                Text("Tap the centre or a segment to edit it. Tap ${ring.friendlyName.removePrefix(light.friendlyName).trim().ifBlank { "the ring" }} for the whole ring.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        // A room, zone or group opens to a list of just its lights; a tap on one opens that light's own settings.
        Text("LIGHTS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            memberStates.forEach { m -> MemberRow(m, call, tone) { openMember(m.entityId) } }
        }
        Text("Tap a light to open its own settings. The switch turns just that light on or off.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        var openSection by rememberSaveable(light.entityId) { mutableStateOf<String?>(null) }
        // The room's own scenes, the same list as on its card, then its hybrid scenes.
        if (scenes.isNotEmpty()) {
            LightSection("cd:palette", "Scenes", "${scenes.size} for this room", tint, openSection == "scenes", { openSection = if (openSection == "scenes") null else "scenes" }) {
                SceneGrid(scenes, call)
            }
        }
        HybridSection(light, memberStates, call, tone, tint, openSection == "hybrid") { openSection = if (openSection == "hybrid") null else "hybrid" }
    } else {
        LightControls(light, light.lightKind(), light.hasWhiteMode(), call, tone, grey, tint)
    }
}

/** The page's top bar: the light's colour, its state and a power button at the end; the whole bar switches the light on or off. */
@Composable
private fun PowerBar(light: EntityState, on: Boolean, tint: Color, tone: ToneColors, status: String, call: CallService) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(listOf(tint.copy(alpha = 0.30f), tint.copy(alpha = 0.06f))))
            .clickable(enabled = light.available, onClickLabel = if (on) "Turn off" else "Turn on") {
                call("light", if (on) "turn_off" else "turn_on", light.entityId, data())
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(tint.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
            HaIcon(light.str("icon"), CdVectors.Bulb, tint, 28.dp)
        }
        Text(status, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(if (on) tone.accent else MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            HaIcon("cd:power", CdVectors.Bulb, if (on) tone.onAccent else MaterialTheme.colorScheme.onSurface, 22.dp)
        }
    }
}

/** A section that opens only when you go to edit it: an icon, a title and a line saying what it is now, and a chevron. */
@Composable
internal fun LightSection(icon: String, title: String, summary: String, tint: Color, open: Boolean, onToggle: () -> Unit, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 14.dp, vertical = 4.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable(onClickLabel = if (open) "Close $title" else "Open $title", onClick = onToggle).padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HaIcon(icon, CdVectors.Bulb, tint, 22.dp)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HaIcon("cd:chevron", CdVectors.Bulb, MaterialTheme.colorScheme.onSurfaceVariant, 20.dp, Modifier.rotate(if (open) 180f else 0f))
        }
        if (open) {
            Column(Modifier.padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
        }
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
    var openSection by rememberSaveable(id) { mutableStateOf<String?>(null) }
    fun toggle(name: String) { openSection = if (openSection == name) null else name }
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
            LightSection("cd:sun", "White tone", whiteSummary, tint, openSection == "white", { toggle("white") }) {
                WhiteControls(light, call, tone)
            }
        }
        LightKind.Colour -> {
            BrightnessBar(light, call)
            val colourOpen = colourTab == 1 || !whiteOk
            LightSection(
                if (colourOpen) "cd:palette" else "cd:sun", if (colourOpen) "Colour" else "White tone",
                if (colourOpen) "Tap to change the colour" else whiteSummary, tint, openSection == "colour", { toggle("colour") },
            ) {
                if (whiteOk) {
                    TileRow(
                        listOf(TileItem("cd:sun", "White", colourTab == 0) { colourTab = 0 }, TileItem("cd:palette", "Colour", colourTab == 1) { colourTab = 1 }),
                        tone, ink,
                    )
                }
                if (colourOpen) ColourPanel(light, call, tone) else WhiteControls(light, call, tone)
            }
            val scenes = (WHITE_SCENES.map { LightScene(it, id) } + looks.library.values.filter { it.hex.isNotEmpty() }.sortedBy { it.name }.map { LightScene(it.key, id) })
            LightSection("cd:palette", "Scenes", "Applies to this light", tint, openSection == "scenes", { toggle("scenes") }) {
                SceneGrid(scenes, call)
            }
        }
    }
}

/** Scene tiles three to a row, as on the room card; a tap plays the scene on [LightScene.target]. */
@Composable
private fun SceneGrid(scenes: List<LightScene>, call: CallService) {
    Column(verticalArrangement = Arrangement.spacedBy(Ui.TileGap)) {
        scenes.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Ui.TileGap)) {
                row.forEach { scene ->
                    SceneTile(scene, active = false, dimmed = false, modifier = Modifier.weight(1f), h = Ui.TallTileHeight) {
                        call("church_drive", "apply_scene", scene.target, data("scene" to scene.key))
                    }
                }
            }
        }
    }
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
private fun BarSlider(
    label: String, value: String, fraction: Float, fill: Brush, thumbOnly: Boolean, onSet: (Float) -> Unit,
    snap: ((Float) -> Float)? = null, valueOf: ((Float) -> String)? = null,
) {
    var drag by remember(fraction) { mutableStateOf<Float?>(null) }
    var width by remember { mutableStateOf(1) }
    val shown = drag ?: fraction
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
                .pointerInput(Unit) { detectTapGestures { onSet(at(it.x)) } }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { drag = at(it.x) },
                        onDragEnd = { drag?.let(onSet); drag = null },
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

/** A hue and saturation wheel: drag or tap to pick a colour; it is sent when you let go. */
@Composable
private fun ColourWheel(light: EntityState, call: CallService) {
    val current = light.attributes.optJSONArray("hs_color")?.let { it.optDouble(0) to it.optDouble(1) } ?: (30.0 to 60.0)
    var pick by remember(current) { mutableStateOf(current) }
    var sizePx by remember { mutableStateOf(1f) }
    fun at(o: Offset): Pair<Double, Double> {
        val r = sizePx / 2.0
        return wheelHueSat(o.x - r, o.y - r, r)
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Canvas(
            modifier = Modifier.size(260.dp)
                .onSizeChanged { sizePx = it.width.toFloat().coerceAtLeast(1f) }
                .pointerInput(Unit) { detectTapGestures { pick = at(it); call("light", "turn_on", light.entityId, data("hs_color" to JSONArray(listOf(pick.first, pick.second)))) } }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { pick = at(it) },
                        onDragEnd = { call("light", "turn_on", light.entityId, data("hs_color" to JSONArray(listOf(pick.first, pick.second)))) },
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

/** One light inside a group: its colour dot, name and state, a switch, and a tap to open its own settings. */
@Composable
private fun MemberRow(light: EntityState, call: CallService, tone: ToneColors, onOpen: () -> Unit) {
    val on = light.state == "on"
    val tint = tintOf(light, MaterialTheme.colorScheme.onSurfaceVariant)
    val opens = opensSettings(light)
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable { if (opens) onOpen() else if (light.available) call("light", if (on) "turn_off" else "turn_on", light.entityId, data()) }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(tint.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
            HaIcon(light.str("icon"), CdVectors.Bulb, tint, 22.dp)
        }
        Column(Modifier.weight(1f)) {
            Text(light.friendlyName, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            Text(
                lightKindLabel(light) + " · " + when { !light.available -> "Unavailable"; !on -> "Off"; light.lightKind() == LightKind.Switch -> "On"; else -> "${brightnessOf(light)}%" },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (opens) HaIcon("cd:chevron-right", CdVectors.Info, MaterialTheme.colorScheme.onSurfaceVariant, 20.dp)
        Switch(checked = on, enabled = light.available, onCheckedChange = { call("light", if (on) "turn_off" else "turn_on", light.entityId, data()) })
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

/**
 * The dial for a light with a ring of segments: the main light in the middle and a piece of the ring for each segment, each in its own colour (grey
 * when off). Tap the middle to edit the main light, or a piece to edit that segment.
 */
@Composable
private fun SegmentDial(main: EntityState, segments: List<EntityState>, tint: Color, onMain: () -> Unit, onSegment: (EntityState) -> Unit) {
    val off = MaterialTheme.colorScheme.surfaceContainerHighest
    val ink = MaterialTheme.colorScheme.onSurface
    val mainColour = if (main.state == "on") tintOf(main, tint) else off
    val n = segments.size
    Box(Modifier.fillMaxWidth().aspectRatio(1f).padding(24.dp), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier.fillMaxSize().pointerInput(segments, main) {
                detectTapGestures { pos ->
                    val c = Offset(size.width / 2f, size.height / 2f)
                    val r = hypot(pos.x - c.x, pos.y - c.y)
                    val outer = minOf(size.width, size.height) / 2f
                    if (r < outer * 0.62f) onMain()
                    else if (n > 0) {
                        val angle = (Math.toDegrees(atan2((pos.y - c.y).toDouble(), (pos.x - c.x).toDouble())) + 90.0 + 360.0) % 360.0
                        onSegment(segments[(angle / (360.0 / n)).toInt().coerceIn(0, n - 1)])
                    }
                }
            },
        ) {
            val stroke = size.minDimension * 0.10f
            val sweep = 360f / n
            val gap = 4f
            val box = Size(size.minDimension - stroke, size.minDimension - stroke)
            val topLeft = Offset((size.width - box.width) / 2f, (size.height - box.height) / 2f)
            segments.forEachIndexed { i, s ->
                val colour = if (s.state == "on") tintOf(s, tint) else off
                drawArc(colour, -90f + i * sweep + gap / 2f, sweep - gap, false, topLeft, box, style = Stroke(width = stroke))
            }
            drawCircle(mainColour, radius = size.minDimension * 0.30f, center = Offset(size.width / 2f, size.height / 2f))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Main light", style = MaterialTheme.typography.titleSmall, color = if (main.state == "on") Color.Black.copy(alpha = 0.8f) else ink)
            Text(if (main.state == "on") "${brightnessOf(main)}%" else "Off", style = MaterialTheme.typography.bodySmall, color = if (main.state == "on") Color.Black.copy(alpha = 0.7f) else ink.copy(alpha = 0.7f))
        }
    }
}
