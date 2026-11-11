package com.churchdrive.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.churchdrive.app.ha.CallService
import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ha.Registry
import com.churchdrive.app.ha.data
import kotlinx.coroutines.delay
import org.json.JSONObject

/** One of a row of mutually exclusive choices; the current one is filled in the tone's strong colour. */
@Composable
fun ChoiceButton(
    label: String,
    selected: Boolean,
    tone: ToneColors,
    content: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    // The selected one is filled in the strong colour; the others are a faint wash of the text colour, like the tiles, not an outline.
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 4.dp),
        colors = if (selected) ButtonDefaults.buttonColors(containerColor = tone.accent, contentColor = tone.onAccent)
        else ButtonDefaults.buttonColors(containerColor = content.copy(alpha = 0.10f), contentColor = content, disabledContainerColor = content.copy(alpha = 0.05f), disabledContentColor = content.copy(alpha = 0.4f)),
        elevation = if (selected) ButtonDefaults.buttonElevation() else null,
    ) { Text(label, maxLines = 1) }
}

// ---- Alarm ----

object Feature { const val PAUSE = 1; const val VOLUME_SET = 4; const val PREVIOUS = 16; const val NEXT = 32; const val TURN_ON = 128; const val TURN_OFF = 256; const val SELECT_SOURCE = 2048; const val PLAY = 16384 }

@Composable
fun MediaCard(config: JSONObject, entities: Map<String, EntityState>, registry: Registry, call: CallService) {
    val players = mediaPlayers(config, entities)
    val tone = toneColors(toneFromColour(config.optString("color")) ?: Tone.Media)
    var open by remember { mutableStateOf<String?>(null) }
    EntityCard(tone.container, tone.onContainer) {
        config.optString("title").takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
        if (players.isEmpty()) Text("No TVs or speakers found.", style = MaterialTheme.typography.bodyMedium)
        players.forEach { p ->
            val on = p.state !in setOf("off", "unavailable", "unknown")
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.weight(1f).clickable { open = p.entityId }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ToneIconName(mediaIcon(p), CdVectors.Info, tone, 38)
                    Column(Modifier.weight(1f)) {
                        Text(p.friendlyName, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                        Text(mediaLine(p), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
                if (p.state != "unavailable") {
                    val playing = p.state in PLAYING
                    IconButton(onClick = {
                        if (playing) call("media_player", "media_play_pause", p.entityId, data())
                        else call("media_player", if (on) "turn_off" else "turn_on", p.entityId, data())
                    }) {
                        HaIcon(if (playing) (if (p.state == "playing") "mdi:pause" else "mdi:play") else "mdi:power", CdVectors.Info, if (on) tone.accent else tone.onContainer, 24.dp)
                    }
                }
            }
        }
    }
    entities[open]?.let { player -> MediaRemote(player, registry, tone, call) { open = null } }
}

@Composable
fun MediaRemote(player: EntityState, registry: Registry, tone: ToneColors, call: CallService, onClose: () -> Unit) {
    val id = player.entityId
    val on = player.state !in setOf("off", "unavailable", "unknown")
    val pad = padFor(player, registry)
    PopupWindow(onDismissRequest = onClose, shape = PopupShape.Card) { close ->
        Card(Modifier.padding(horizontal = 24.dp)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ToneIconName(mediaIcon(player), CdVectors.Info, tone, 44)
                    Column(Modifier.weight(1f)) {
                        Text(player.friendlyName, style = MaterialTheme.typography.titleMedium)
                        Text(mediaLine(player), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = close) { HaIcon("mdi:close", CdVectors.Info, MaterialTheme.colorScheme.onSurface, 22.dp) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    if (player.supports(Feature.TURN_ON) || player.supports(Feature.TURN_OFF)) IconButton(onClick = { call("media_player", if (on) "turn_off" else "turn_on", id, data()) }) { HaIcon("mdi:power", CdVectors.Info, if (on) tone.accent else MaterialTheme.colorScheme.onSurface, 26.dp) }
                    if (player.supports(Feature.PREVIOUS)) IconButton(onClick = { call("media_player", "media_previous_track", id, data()) }) { HaIcon("mdi:skip-previous", CdVectors.Info, MaterialTheme.colorScheme.onSurface, 26.dp) }
                    if (player.supports(Feature.PAUSE) || player.supports(Feature.PLAY)) IconButton(onClick = { call("media_player", "media_play_pause", id, data()) }) { HaIcon(if (player.state == "playing") "mdi:pause" else "mdi:play", CdVectors.Info, tone.accent, 34.dp) }
                    if (player.supports(Feature.NEXT)) IconButton(onClick = { call("media_player", "media_next_track", id, data()) }) { HaIcon("mdi:skip-next", CdVectors.Info, MaterialTheme.colorScheme.onSurface, 26.dp) }
                }
                if (player.supports(Feature.VOLUME_SET)) {
                    var volume by remember(id, player.num("volume_level")) { mutableStateOf((player.num("volume_level") ?: 0.0).toFloat()) }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        HaIcon("mdi:volume-high", CdVectors.Info, MaterialTheme.colorScheme.onSurfaceVariant, 22.dp)
                        Slider(volume, { volume = it }, Modifier.weight(1f), onValueChangeFinished = { call("media_player", "volume_set", id, data("volume_level" to volume.toDouble())) })
                    }
                }
                val sources = player.attributes.optJSONArray("source_list")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty()
                if (sources.isNotEmpty() && player.supports(Feature.SELECT_SOURCE)) {
                    Text("Source", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OptionRow(sources, player.str("source"), tone) { call("media_player", "select_source", id, data("source" to it)) }
                }
                if (pad != null) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row { PadKey("up", "mdi:chevron-up", pad, id, call) }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            PadKey("left", "mdi:chevron-left", pad, id, call)
                            PadKey("ok", "mdi:circle-medium", pad, id, call)
                            PadKey("right", "mdi:chevron-right", pad, id, call)
                        }
                        Row { PadKey("down", "mdi:chevron-down", pad, id, call) }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            PadKey("back", "mdi:arrow-left", pad, id, call)
                            PadKey("home", "mdi:home-outline", pad, id, call)
                        }
                    }
                }
            }
        }
    }
}

val PLAYING = setOf("playing", "paused", "buffering")

/** How a direction pad is driven: a `remote.` on the same device, or an LG webOS TV's own buttons. */
sealed class Pad { data class Remote(val entity: String) : Pad(); object Webos : Pad() }

@Composable
fun PadKey(key: String, icon: String, pad: Pad, player: String, call: CallService) {
    IconButton(onClick = { padPress(pad, player, key, call) }) { HaIcon(icon, CdVectors.Info, MaterialTheme.colorScheme.onSurface, 28.dp) }
}

fun mediaIcon(player: EntityState): String = when (player.str("device_class")) {
    "tv" -> "mdi:television"
    "speaker" -> "mdi:speaker"
    "receiver" -> "mdi:audio-video"
    else -> "mdi:cast"
}

/** What a row says under the name: what's playing, else the state in words. */
fun mediaLine(player: EntityState): String = when {
    player.state in PLAYING -> listOfNotNull(player.str("media_title")?.takeIf { it.isNotBlank() }, player.str("app_name")?.takeIf { it.isNotBlank() }).joinToString(" · ").ifBlank { presetLabel(player.state) }
    player.state == "unavailable" -> "Unavailable"
    player.state == "off" -> "Off"
    else -> presetLabel(player.state)
}

/** The players to show: those the card names, else every one; whatever is playing first, then by name. */
fun mediaPlayers(config: JSONObject, entities: Map<String, EntityState>): List<EntityState> {
    val named = config.optJSONArray("entities")?.let { a -> (0 until a.length()).map { i -> a.opt(i).let { (it as? JSONObject)?.optString("entity") ?: it.toString() } } }.orEmpty()
    val all = if (named.isNotEmpty()) named.mapNotNull { entities[it] } else entities.values.filter { it.entityId.startsWith("media_player.") }
    return all.sortedWith(compareBy({ it.state !in PLAYING }, { it.friendlyName.lowercase() }))
}

fun padFor(player: EntityState, registry: Registry): Pad? {
    if (registry.platformOf(player.entityId) == "webostv") return Pad.Webos
    return registry.siblings(player.entityId).firstOrNull { it.startsWith("remote.") }?.let { Pad.Remote(it) }
}

fun padPress(pad: Pad, player: String, key: String, call: CallService) {
    when (pad) {
        Pad.Webos -> call("webostv", "button", player, data("button" to mapOf("up" to "UP", "down" to "DOWN", "left" to "LEFT", "right" to "RIGHT", "ok" to "ENTER", "back" to "BACK", "home" to "HOME").getValue(key)))
        is Pad.Remote -> call("remote", "send_command", pad.entity, data("command" to mapOf("up" to "DPAD_UP", "down" to "DPAD_DOWN", "left" to "DPAD_LEFT", "right" to "DPAD_RIGHT", "ok" to "DPAD_CENTER", "back" to "BACK", "home" to "HOME").getValue(key)))
    }
}

/** A row of choices that scrolls sideways when there are many. */
@Composable
fun OptionRow(options: List<String>, selected: String?, tone: ToneColors, enabled: Boolean = true, onPick: (String) -> Unit) {
    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o -> ChoiceButton(presetLabel(o), selected == o, tone, tone.onContainer, Modifier, enabled) { onPick(o) } }
    }
}

/** The timings every pop-up uses, so they all open and close the same way. */
object PopupMotion {
    const val EnterMs = 280
    const val ExitMs = 240
    const val ScrimMs = 240

    /** How dark the page behind a card or sheet goes. */
    const val ScrimAlpha = 0.34f
}

/** Where a pop-up sits: the whole screen, a sheet from the bottom, or a card in the middle. */
enum class PopupShape { Full, Sheet, Card }

/**
 * The one way every pop-up opens and closes. The page behind fades dark by itself, in place; the pop-up rises from below and fades in,
 * and leaves the same way backwards. The window has no animation of its own (it would drag the dimmed page along with the card).
 *
 * Close it with the `close` passed to [content] (or Back, or a tap outside a card or sheet): it plays the exit and then calls
 * [onDismissRequest]. [handleBack] can take a Back press first (leaving a sub-screen, say) by returning true.
 */
@Composable
fun PopupWindow(
    onDismissRequest: () -> Unit,
    shape: PopupShape = PopupShape.Full,
    handleBack: () -> Boolean = { false },
    content: @Composable (close: () -> Unit) -> Unit,
) {
    var shown by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val close: () -> Unit = { if (!leaving) { leaving = true; shown = false } }
    LaunchedEffect(leaving) { if (leaving) { delay(PopupMotion.ExitMs + 30L); onDismissRequest() } }
    Dialog(
        onDismissRequest = { if (!handleBack()) close() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val view = LocalView.current
        SideEffect {
            (view.parent as? DialogWindowProvider)?.window?.let {
                it.setWindowAnimations(-1)
                it.setDimAmount(0f)
            }
        }
        val dim by animateFloatAsState(if (shown && shape != PopupShape.Full) PopupMotion.ScrimAlpha else 0f, tween(PopupMotion.ScrimMs), label = "scrim")
        Box(Modifier.fillMaxSize()) {
            if (shape != PopupShape.Full) {
                Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim))
                        .clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { close() },
                )
            }
            AnimatedVisibility(
                visible = shown,
                modifier = when (shape) {
                    PopupShape.Full -> Modifier.fillMaxSize()
                    PopupShape.Sheet -> Modifier.align(Alignment.BottomCenter)
                    PopupShape.Card -> Modifier.align(Alignment.Center)
                },
                enter = slideInVertically(tween(PopupMotion.EnterMs)) { if (shape == PopupShape.Card) it / 6 else it } + fadeIn(tween(PopupMotion.EnterMs - 80)),
                exit = slideOutVertically(tween(PopupMotion.ExitMs)) { if (shape == PopupShape.Card) it / 6 else it } + fadeOut(tween(PopupMotion.ExitMs - 40)),
            ) {
                content(close)
            }
        }
    }
}

fun EntityState.supports(flag: Int) = (attributes.optInt("supported_features", 0) and flag) != 0
