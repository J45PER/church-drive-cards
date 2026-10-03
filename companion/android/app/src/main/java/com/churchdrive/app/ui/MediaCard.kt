package com.churchdrive.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.churchdrive.app.ha.CallService
import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ha.Registry
import com.churchdrive.app.ha.data
import org.json.JSONObject

// The dashboard's Media card: the house's TVs and speakers, a row each; tap one for its remote.

private val PLAYING = setOf("playing", "paused", "buffering")

private object Feature { const val PAUSE = 1; const val VOLUME_SET = 4; const val PREVIOUS = 16; const val NEXT = 32; const val TURN_ON = 128; const val TURN_OFF = 256; const val SELECT_SOURCE = 2048; const val PLAY = 16384 }

private fun EntityState.supports(flag: Int) = (attributes.optInt("supported_features", 0) and flag) != 0

/** The players to show: those the card names, else every one; whatever is playing first, then by name. */
fun mediaPlayers(config: JSONObject, entities: Map<String, EntityState>): List<EntityState> {
    val named = config.optJSONArray("entities")?.let { a -> (0 until a.length()).map { i -> a.opt(i).let { (it as? JSONObject)?.optString("entity") ?: it.toString() } } }.orEmpty()
    val all = if (named.isNotEmpty()) named.mapNotNull { entities[it] } else entities.values.filter { it.entityId.startsWith("media_player.") }
    return all.sortedWith(compareBy({ it.state !in PLAYING }, { it.friendlyName.lowercase() }))
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

/** How a direction pad is driven: a `remote.` on the same device, or an LG webOS TV's own buttons. */
sealed class Pad { data class Remote(val entity: String) : Pad(); object Webos : Pad() }

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

@Composable
fun MediaCard(config: JSONObject, entities: Map<String, EntityState>, registry: Registry, call: CallService) {
    val players = mediaPlayers(config, entities)
    val tone = toneColors(toneFromColour(config.optString("color")) ?: Tone.Purple)
    var open by remember { mutableStateOf<String?>(null) }
    EntityCard(tone.container, tone.onContainer) {
        config.optString("title").takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
        if (players.isEmpty()) Text("No TVs or speakers found.", style = MaterialTheme.typography.bodyMedium)
        players.forEach { p ->
            val on = p.state !in setOf("off", "unavailable", "unknown")
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.weight(1f).clickable { open = p.entityId }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ToneIconName(mediaIcon(p), Icons.Filled.Info, tone, 38)
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
                        HaIcon(if (playing) (if (p.state == "playing") "mdi:pause" else "mdi:play") else "mdi:power", Icons.Filled.Info, if (on) tone.accent else tone.onContainer, 24.dp)
                    }
                }
            }
        }
    }
    entities[open]?.let { player -> MediaRemote(player, registry, tone, call) { open = null } }
}

@Composable
private fun MediaRemote(player: EntityState, registry: Registry, tone: ToneColors, call: CallService, onClose: () -> Unit) {
    val id = player.entityId
    val on = player.state !in setOf("off", "unavailable", "unknown")
    val pad = padFor(player, registry)
    Dialog(onDismissRequest = onClose) {
        Card {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ToneIconName(mediaIcon(player), Icons.Filled.Info, tone, 44)
                    Column(Modifier.weight(1f)) {
                        Text(player.friendlyName, style = MaterialTheme.typography.titleMedium)
                        Text(mediaLine(player), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onClose) { HaIcon("mdi:close", Icons.Filled.Info, MaterialTheme.colorScheme.onSurface, 22.dp) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    if (player.supports(Feature.TURN_ON) || player.supports(Feature.TURN_OFF)) IconButton(onClick = { call("media_player", if (on) "turn_off" else "turn_on", id, data()) }) { HaIcon("mdi:power", Icons.Filled.Info, if (on) tone.accent else MaterialTheme.colorScheme.onSurface, 26.dp) }
                    if (player.supports(Feature.PREVIOUS)) IconButton(onClick = { call("media_player", "media_previous_track", id, data()) }) { HaIcon("mdi:skip-previous", Icons.Filled.Info, MaterialTheme.colorScheme.onSurface, 26.dp) }
                    if (player.supports(Feature.PAUSE) || player.supports(Feature.PLAY)) IconButton(onClick = { call("media_player", "media_play_pause", id, data()) }) { HaIcon(if (player.state == "playing") "mdi:pause" else "mdi:play", Icons.Filled.Info, tone.accent, 34.dp) }
                    if (player.supports(Feature.NEXT)) IconButton(onClick = { call("media_player", "media_next_track", id, data()) }) { HaIcon("mdi:skip-next", Icons.Filled.Info, MaterialTheme.colorScheme.onSurface, 26.dp) }
                }
                if (player.supports(Feature.VOLUME_SET)) {
                    var volume by remember(id, player.num("volume_level")) { mutableStateOf((player.num("volume_level") ?: 0.0).toFloat()) }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        HaIcon("mdi:volume-high", Icons.Filled.Info, MaterialTheme.colorScheme.onSurfaceVariant, 22.dp)
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

@Composable
private fun PadKey(key: String, icon: String, pad: Pad, player: String, call: CallService) {
    IconButton(onClick = { padPress(pad, player, key, call) }) { HaIcon(icon, Icons.Filled.Info, MaterialTheme.colorScheme.onSurface, 28.dp) }
}
