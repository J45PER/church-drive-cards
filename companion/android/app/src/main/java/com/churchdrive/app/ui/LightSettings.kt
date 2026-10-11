package com.churchdrive.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.churchdrive.app.ha.CallService
import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ha.data
import org.json.JSONArray
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

private val SWATCHES = listOf(0.0 to 100.0, 28.0 to 100.0, 50.0 to 100.0, 140.0 to 75.0, 195.0 to 80.0, 235.0 to 70.0, 280.0 to 70.0, 330.0 to 70.0)

/**
 * A light's full-screen settings, opened by pressing and holding it: brightness for a dimmable light, warm to cool
 * for a white one, a colour wheel for a colour one and just On/Off for a switch. A room, zone or group also lists
 * its lights, each with its own switch, and a tap on one opens that light's own settings.
 */
@Composable
fun LightSettings(entityId: String, title: String?, entities: Map<String, EntityState>, call: CallService, onClose: () -> Unit) {
    val light = entities[entityId]
    var member by remember { mutableStateOf<String?>(null) }
    FullPage(title ?: light?.friendlyName ?: entityId, onClose) {
        if (light == null) {
            Text("Loading…")
        } else {
            LightSettingsBody(light, entities, call) { member = it }
        }
    }
    member?.let { LightSettings(it, null, entities, call) { member = null } }
}

@Composable
private fun LightSettingsBody(light: EntityState, entities: Map<String, EntityState>, call: CallService, openMember: (String) -> Unit) {
    val tone = toneColors(Tone.Amber)
    val grey = toneColors(Tone.Grey)
    val on = light.state == "on"
    val members = light.lightMembers()
    var tab by remember { mutableStateOf(1) }
    val tint = tintOf(light, MaterialTheme.colorScheme.onSurfaceVariant)

    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(tint.copy(alpha = 0.30f), tint.copy(alpha = 0.06f))))
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.size(52.dp).clip(CircleShape).background(tint.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
            HaIcon(light.str("icon"), Icons.Filled.Lightbulb, tint, 28.dp)
        }
        Text(
            when {
                !light.available -> "Unavailable"
                !on -> "Off"
                light.lightKind() == LightKind.Switch -> "On"
                else -> "On · ${brightnessOf(light)}%"
            },
            style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f),
        )
    }
    TileRow(
        listOf(TileItem("mdi:power", if (on) "Turn off" else "Turn on", on) { call("light", if (on) "turn_off" else "turn_on", light.entityId, data()) }),
        tone, MaterialTheme.colorScheme.onSurface, enabled = light.available,
    )

    val memberStates = members.mapNotNull { entities[it] }
    val grouped = members.size > 1
    if (grouped) {
        TileRow(
            listOf(TileItem("mdi:lightbulb-group", "All lights", tab == 0) { tab = 0 }, TileItem("mdi:lightbulb-outline", "Each light", tab == 1) { tab = 1 }),
            tone, MaterialTheme.colorScheme.onSurface,
        )
    }
    if (grouped && tab == 1) {
        memberStates.forEach { m -> MemberRow(m, call, tone) { openMember(m.entityId) } }
        Text("Tap a light to open its own settings.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        val kind = if (grouped) maxOf(light.lightKind(), groupKind(memberStates)) else light.lightKind()
        val whiteOk = light.hasWhiteMode() || memberStates.any { it.hasWhiteMode() }
        LightControls(light, kind, whiteOk, call, tone, grey)
        if (grouped && memberStates.any { it.lightKind() == LightKind.Switch }) {
            Text("Lights that only switch follow on and off.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The controls for what [kind] of light this is. [light] is what the calls go to (a group's own entity for a whole group). */
@Composable
private fun LightControls(light: EntityState, kind: LightKind, whiteOk: Boolean, call: CallService, tone: ToneColors, grey: ToneColors) {
    val ink = MaterialTheme.colorScheme.onSurface
    val id = light.entityId
    var colourTab by remember { mutableStateOf(if (light.str("color_mode") == "color_temp" && whiteOk) 0 else 1) }
    when (kind) {
        LightKind.Switch -> {
            TileRow(
                listOf(
                    TileItem("mdi:lightbulb-on", "On", light.state == "on") { call("light", "turn_on", id, data()) },
                    TileItem("mdi:lightbulb-off-outline", "Off", light.state != "on") { call("light", "turn_off", id, data()) },
                ),
                tone, ink,
            )
            Text("This light only switches on and off, so there is nothing else to set.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LightKind.Dim -> {
            BrightnessBar(light, call)
            TileRow(listOf(10, 25, 50, 75, 100).map { p -> TileItem("mdi:brightness-6", "$p%", brightnessOf(light) == p && light.state == "on", showLabel = true) { call("light", "turn_on", id, data("brightness_pct" to p)) } }, tone, ink)
        }
        LightKind.White -> {
            WhiteControls(light, call, tone)
            BrightnessBar(light, call)
        }
        LightKind.Colour -> {
            if (whiteOk) {
                TileRow(
                    listOf(TileItem("mdi:white-balance-sunny", "White", colourTab == 0) { colourTab = 0 }, TileItem("mdi:palette", "Colour", colourTab == 1) { colourTab = 1 }),
                    tone, ink,
                )
            }
            if (colourTab == 1 || !whiteOk) {
                ColourWheel(light, call)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    SWATCHES.forEach { (h, s) ->
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).background(rgbColour(hsToRgb(h, s)))
                                .clickable { call("light", "turn_on", id, data("hs_color" to JSONArray(listOf(h, s)))) },
                        )
                    }
                }
            } else {
                WhiteControls(light, call, tone)
            }
            BrightnessBar(light, call)
        }
    }
}

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
    val presets = whitePresets(min, max)
    TileRow(
        presets.map { (name, kelvin) -> TileItem("mdi:white-balance-sunny", name, kotlin.math.abs(k - kelvin) < 150) { call("light", "turn_on", light.entityId, data("color_temp_kelvin" to kelvin)) } },
        tone, MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun BrightnessBar(light: EntityState, call: CallService) {
    val pct = brightnessOf(light)
    val tint = tintOf(light, Color(0xFFE8B34A))
    BarSlider(
        "Brightness", "${if (light.state == "on") pct else 0}%", if (light.state == "on") pct / 100f else 0f,
        Brush.horizontalGradient(listOf(tint.copy(alpha = 0.35f), tint.copy(alpha = 0.8f))), thumbOnly = false,
        onSet = { f -> call("light", "turn_on", light.entityId, data("brightness_pct" to (f * 100).roundToInt().coerceIn(1, 100))) },
    )
}

/** A wide pill you drag across (or tap) to set a level; it calls [onSet] when you let go. */
@Composable
private fun BarSlider(label: String, value: String, fraction: Float, fill: Brush, thumbOnly: Boolean, onSet: (Float) -> Unit) {
    var drag by remember(fraction) { mutableStateOf<Float?>(null) }
    var width by remember { mutableStateOf(1) }
    val shown = drag ?: fraction
    fun at(x: Float) = (x / width).coerceIn(0f, 1f)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
        Box(
            modifier = Modifier.fillMaxWidth().height(60.dp).clip(RoundedCornerShape(30.dp))
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
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable { if (opens) onOpen() else if (light.available) call("light", if (on) "turn_off" else "turn_on", light.entityId, data()) }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(tint.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
            HaIcon(light.str("icon"), Icons.Filled.Lightbulb, tint, 22.dp)
        }
        Column(Modifier.weight(1f)) {
            Text(light.friendlyName, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            Text(
                when { !light.available -> "Unavailable"; !on -> "Off"; light.lightKind() == LightKind.Switch -> "On"; else -> "${brightnessOf(light)}%" },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = on, enabled = light.available, onCheckedChange = { call("light", if (on) "turn_off" else "turn_on", light.entityId, data()) })
    }
}
