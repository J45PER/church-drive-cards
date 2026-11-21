package com.churchdrive.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.churchdrive.app.ha.CallService
import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ha.data
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Asks the house for the hybrid scenes again; set while [rememberHybridList] is on screen. */
object HybridRefresh {
    var trigger: (() -> Unit)? = null
}

/** Every hybrid scene and which one is playing where, kept up to date by [rememberHybridList]; empty until the house answers. */
val LocalHybrids = compositionLocalOf { HybridList() }

/** The house's hybrid scenes, asked for once and asked again whenever they change. */
@Composable
fun rememberHybridList(api: HaApi?): HybridList {
    val state = remember { mutableStateOf(HybridList()) }
    var changed by remember { mutableIntStateOf(0) }
    // The first ask can come before the line to the house is up; try again until it answers, so the list is never left empty.
    LaunchedEffect(api, changed) {
        var tries = 0
        while (tries < 8) {
            val list = parseHybrids(api?.ask("church_drive/hybrids", JSONObject()))
            if (list != null) { state.value = list; break }
            tries++
            kotlinx.coroutines.delay(1500L * tries)
        }
    }
    // Saving or deleting a hybrid asks again at once, without waiting for the house to say it changed.
    DisposableEffect(Unit) {
        HybridRefresh.trigger = { changed++ }
        onDispose { HybridRefresh.trigger = null }
    }
    DisposableEffect(api) {
        val sub = api?.subscribe("subscribe_events", JSONObject().put("event_type", "church_drive_hybrids_changed")) { changed++ } ?: -1
        onDispose { if (sub >= 0) api?.close(sub) }
    }
    return state.value
}

/** Plays [hybrid] on its room: the house gives each light its look. */
fun playHybrid(call: CallService, hybrid: Hybrid) = call("church_drive", "apply_hybrid", hybrid.target, data("id" to hybrid.id))

/**
 * A hybrid scene as a tile: split in two of its colours, its icon and name; the playing one has a ring. A tap plays it and, where
 * [onEdit] is given (administrators), pressing and holding opens it to change.
 */
@Composable
internal fun HybridTile(hybrid: Hybrid, active: Boolean, dimmed: Boolean, modifier: Modifier, h: Dp = Ui.TallTileHeight, onEdit: (() -> Unit)? = null, onClick: () -> Unit) {
    val colours = hybridTileColours(hybrid, LocalSceneLooks.current)
    val a = Color(colours[0])
    val b = Color(colours[1])
    val ink = if (SceneLooks.darkInkOn(colours)) Color(0xFF202124) else Color.White
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = modifier.height(h).alpha(if (dimmed) 0.55f else 1f).clip(shape)
            .background(Brush.linearGradient(0f to a, 0.48f to a, 0.52f to b, 1f to b))
            .then(if (active) Modifier.border(2.5.dp, Color.White, shape) else Modifier)
            .pointerInput(onEdit) { detectTapGestures(onTap = { onClick() }, onLongPress = { onEdit?.invoke() }) },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            HaIcon(hybrid.icon, CdVectors.Palette, ink, 20.dp)
            CentredText(hybrid.name, ink, 10.sp, Modifier.padding(horizontal = 3.dp), lineHeight = 12.sp)
        }
    }
}

/** The "make a new one" tile at the end of the hybrid scenes. */
@Composable
private fun NewHybridTile(modifier: Modifier, onClick: () -> Unit) {
    val ink = MaterialTheme.colorScheme.onSurface
    Box(
        modifier = modifier.height(Ui.TallTileHeight).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).clickable(onClickLabel = "New hybrid scene", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            HaIcon("cd:plus", CdVectors.Palette, ink, 20.dp)
            CentredText("New", ink, 10.sp, lineHeight = 12.sp)
        }
    }
}

/** Hybrid scene tiles three to a row; [onEdit] and [onNew] are given to administrators. */
@Composable
internal fun HybridTileGrid(hybrids: List<Hybrid>, activeId: String?, onPlay: (Hybrid) -> Unit, onEdit: ((Hybrid) -> Unit)?, onNew: (() -> Unit)?) {
    val items: List<Hybrid?> = hybrids + (if (onNew != null) listOf<Hybrid?>(null) else emptyList())
    Column(verticalArrangement = Arrangement.spacedBy(Ui.TileGap)) {
        items.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Ui.TileGap)) {
                row.forEach { hy ->
                    if (hy != null) {
                        HybridTile(hy, hy.id == activeId, activeId != null && hy.id != activeId, Modifier.weight(1f), onEdit = onEdit?.let { e -> { e(hy) } }) { onPlay(hy) }
                    } else {
                        NewHybridTile(Modifier.weight(1f)) { onNew?.invoke() }
                    }
                }
            }
        }
    }
}

/**
 * A room's hybrid scenes, on the room's page: its tiles (a tap plays one) and, for an administrator, a tile to make a new one and pressing
 * and holding a tile to change it. Opens in place into the editor.
 */
@Composable
internal fun HybridSection(head: EntityState, members: List<EntityState>, call: CallService, tone: ToneColors, tint: Color, open: Boolean, onToggle: () -> Unit) {
    val all = LocalHybrids.current
    val mine = all.of(head.entityId)
    val admin = LocalIsAdmin.current
    var draft by remember { mutableStateOf<HybridDraft?>(null) }
    val playing = mine.firstOrNull { it.id == all.active[head.entityId] }
    LightSection(
        "cd:animation-play", "Hybrid scenes", playing?.let { "Playing · ${it.name}" } ?: if (mine.isEmpty()) "One tap, a different look on each light" else "${mine.size} saved",
        tint, open, onToggle,
    ) {
        val d = draft
        if (d != null) {
            HybridEditor(d, head.entityId, members, tone) { draft = null }
        } else {
            if (mine.isEmpty() && !admin) {
                Text("No hybrid scenes yet. An administrator can make them.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HybridTileGrid(
                mine, playing?.id, { playHybrid(call, it) },
                onEdit = if (admin) ({ hy: Hybrid -> draft = HybridDraft(hy.id, hy.name, hy.icon, hy.looks) }) else null,
                onNew = if (admin) ({ draft = HybridDraft(null, "", "cd:palette", members.associate { it.entityId to HybridLook("leave") }) }) else null,
            )
            Text(
                "A hybrid scene gives each light in the room its own look in one tap.${if (admin) " Press and hold a tile to change it." else ""}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun sceneNameOf(looks: SceneLooks, key: String): String = looks.library[key]?.name ?: LightScene(key, "").name

private fun hexOf(c: Pair<Double, Double>): String = hsToRgb(c.first, c.second).let { "#%02x%02x%02x".format(it[0], it[1], it[2]) }

/** One colour to pick for a light: a round swatch, ringed when chosen. */
@Composable
private fun PickSwatch(hex: String, selected: Boolean, onClick: () -> Unit) {
    val argb = hexToArgb(hex) ?: return
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(Color(argb))
            .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
            .clickable(onClickLabel = "Colour $hex", onClick = onClick),
    )
}

/**
 * Chooses the colours of a gradient, as drawn in the approved mockup: a bar showing the gradient with each colour as a round point on it (Start, Middle, End),
 * the presets and saved colours to give the chosen point, the colour wheel, and add, remove and reverse. Every change calls [onChange] with the new list of
 * "#rrggbb" colours. Used for a hybrid scene's gradient look and a gradient light's own page.
 */
@Composable
internal fun GradientEditor(colours: List<String>, tone: ToneColors, ink: Color, onChange: (List<String>) -> Unit) {
    val points = colours.ifEmpty { HYBRID_GRADIENTS[1].second }
    var sel by remember { mutableIntStateOf(0) }
    var wheel by remember { mutableStateOf(false) }
    val at = sel.coerceIn(0, points.lastIndex)
    val saved = LocalSavedColours.current
    val argb = points.map { hexToArgb(it) ?: 0xFFFFFFFF.toInt() }
    fun give(hex: String) = onChange(points.mapIndexed { i, c -> if (i == at) hex else c })
    Box(Modifier.fillMaxWidth().height(Ui.TallTileHeight)) {
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp)).background(Brush.horizontalGradient(argb.map { Color(it) }.let { if (it.size == 1) it + it else it })))
        Row(Modifier.fillMaxSize().padding(horizontal = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            points.forEachIndexed { i, _ ->
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(Color(argb[i]))
                        .border(if (i == at) 4.dp else 2.dp, if (i == at) ink else Color.White, CircleShape)
                        .clickable(onClickLabel = "Colour ${i + 1}") { sel = i },
                )
            }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        points.indices.forEach { i ->
            Text(
                when { points.size == 3 && i == 1 -> "Middle"; i == 0 -> "Start"; i == points.lastIndex -> "End"; else -> "${i + 1}" },
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Text("Presets", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        SWATCHES.map(::hexOf).forEach { hex -> PickSwatch(hex, points[at].equals(hex, ignoreCase = true)) { give(hex) } }
    }
    Text("Saved", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val current = hexToHs(points[at])
    val isSaved = current != null && saved.colours.any { sameColour(it, current) }
    saved.colours.map(::hexOf).chunked(6).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { row.forEach { hex -> PickSwatch(hex, points[at].equals(hex, ignoreCase = true)) { give(hex) } } }
    }
    TileRow(
        listOf(
            TileItem(if (isSaved) "cd:bin" else "cd:plus", if (isSaved) "Remove saved" else "Save this colour", false) { current?.let { saved.set(toggleSavedColour(saved.colours, it)) } },
            TileItem("cd:eyedropper", "Colour picker", wheel) { wheel = !wheel },
        ),
        tone, ink, perRow = 2,
    )
    if (wheel) HsWheel(current ?: (200.0 to 60.0)) { picked -> give(hexOf(picked)) }
    TileRow(
        listOf(
            TileItem("cd:minus", "Remove colour", false) { if (points.size > MIN_GRADIENT_POINTS) { onChange(points.filterIndexed { i, _ -> i != at }); sel = 0 } },
            TileItem("cd:plus", "Add colour", false) { if (points.size < MAX_GRADIENT_POINTS) { onChange(points + points.last()); sel = points.size } },
            TileItem("cd:arrow-right", "Reverse", false) { onChange(points.reversed()); sel = points.lastIndex - at },
        ),
        tone, ink,
    )
    Text("Start from", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    TileRow(HYBRID_GRADIENTS.map { (name, preset) -> TileItem("cd:bulb-group", name, colours == preset) { onChange(preset); sel = 0 } }, tone, ink, perRow = 2)
}

/**
 * Makes or changes a hybrid scene in place: a name, an icon from the short list, then a row for each light that opens to the looks it can
 * do (Leave, Off, a white scene, a colour, a gradient, a dynamic scene) and the choices for the one picked. Nothing reaches the house until Save.
 */
@Composable
private fun HybridEditor(start: HybridDraft, head: String, members: List<EntityState>, tone: ToneColors, onDone: () -> Unit) {
    val api = LocalHaApi.current
    val scope = rememberCoroutineScope()
    val looks = LocalSceneLooks.current
    val ink = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    var draft by remember(start) { mutableStateOf(start) }
    var openLight by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val saved = LocalSavedColours.current.colours
    val all = LocalHybrids.current

    OutlinedTextField(
        value = draft.name, onValueChange = { draft = draft.copy(name = it.take(20)); message = null }, singleLine = true,
        label = { Text("Name") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
    )
    Text("Icon", style = MaterialTheme.typography.labelMedium, color = muted)
    TileRow(HYBRID_ICONS.map { icon -> TileItem(icon, icon.removePrefix("cd:"), draft.icon == icon, showLabel = false) { draft = draft.copy(icon = icon) } }, tone, ink, perRow = 6)
    Text("Each light", style = MaterialTheme.typography.labelMedium, color = muted)
    members.forEach { m ->
        val look = draft.looks[m.entityId] ?: HybridLook("leave")
        val isOpen = openLight == m.entityId
        fun setLook(next: HybridLook) { draft = draft.copy(looks = draft.looks + (m.entityId to next)); message = null }
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().clickable(onClickLabel = if (isOpen) "Close ${m.friendlyName}" else "Open ${m.friendlyName}") { openLight = if (isOpen) null else m.entityId }.padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                HaIcon(m.str("icon"), CdVectors.Bulb, ink, 22.dp)
                Column(Modifier.weight(1f)) {
                    Text(m.friendlyName, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                    Text(hybridLookText(look) { sceneNameOf(looks, it) }, style = MaterialTheme.typography.bodySmall, color = muted)
                }
                HaIcon("cd:chevron", CdVectors.Bulb, muted, 20.dp, Modifier.rotate(if (isOpen) 180f else 0f))
            }
            if (isOpen) {
                Column(Modifier.padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TileRow(
                        hybridKindsFor(m.lightKind(), m.entityId in all.gradientLights, m.entityId in all.hueLights, ring = isSegmentGroup(m)).map { kind -> TileItem(hybridKindIcon(kind), hybridKindLabel(kind), look.kind == kind) { if (look.kind != kind) setLook(hybridDefaultLook(kind)) } },
                        tone, ink, perRow = 3,
                    )
                    when (look.kind) {
                        "white" -> TileRow(
                            WHITE_SCENES.map { key -> TileItem(looks.icon(key, sceneNameOf(looks, key)) ?: "cd:sun", sceneNameOf(looks, key), look.value == key) { setLook(HybridLook("white", key)) } },
                            tone, ink, perRow = 3, column = true,
                        )
                        "colour" -> {
                            val choices = SWATCHES.map(::hexOf) + saved.map(::hexOf)
                            choices.chunked(8).forEach { row ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    row.forEach { hex -> PickSwatch(hex, look.value.equals(hex, ignoreCase = true)) { setLook(HybridLook("colour", hex)) } }
                                }
                            }
                            var wheel by remember(m.entityId) { mutableStateOf(false) }
                            TileRow(listOf(TileItem("cd:eyedropper", "Custom colour", wheel) { wheel = !wheel }), tone, ink)
                            if (wheel) HsWheel(look.value?.let(::hexToHs) ?: (30.0 to 60.0)) { setLook(HybridLook("colour", hexOf(it))) }
                        }
                        "gradient" -> GradientEditor(look.colours, tone, ink) { setLook(HybridLook("gradient", colours = it)) }
                        "dynamic" -> {
                            val scenes = looks.library.values.filter { it.hex.isNotEmpty() }.sortedBy { it.name }
                            TileRow(scenes.map { s -> TileItem(s.icon ?: "cd:animation-play", s.name, look.value == s.key) { setLook(HybridLook("dynamic", s.key)) } }, tone, ink, perRow = 2)
                        }
                    }
                }
            }
        }
    }
    Text("Each light only offers what it can do. Leave keeps the light as it is.", style = MaterialTheme.typography.bodySmall, color = muted)
    message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
    TileRow(
        listOf(
            TileItem("cd:close", "Cancel", false) { onDone() },
            TileItem("cd:check", "Save", false) {
                val problem = hybridProblem(draft)
                if (problem != null) message = problem
                else scope.launch {
                    val result = api?.ask("church_drive/hybrid/save", hybridSaveBody(draft, head))
                    if (result != null) { HybridRefresh.trigger?.invoke(); onDone() } else message = "The house didn't accept that${api?.lastErrorCode?.let { " ($it)" }.orEmpty()}."
                }
            },
        ) + (if (draft.id != null) listOf(TileItem("cd:bin", "Delete", false) { scope.launch { if (api?.ask("church_drive/hybrid/delete", JSONObject().put("hybrid_id", draft.id)) != null) { HybridRefresh.trigger?.invoke(); onDone() } else message = "The house didn't delete it." } }) else emptyList()),
        tone, ink, perRow = 3,
    )
}
