package com.churchdrive.app.ui

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.churchdrive.app.ha.CallService
import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ha.Registry
import com.churchdrive.app.ha.data
import kotlinx.coroutines.delay
import org.json.JSONObject

/** Home Assistant's address, for pictures and video (they're fetched with a token in the link). Set in MainActivity. */
val LocalBaseUrl = compositionLocalOf { "" }

/** When each camera last had a snapshot asked for from here, so the app doesn't ask again straight away. */
private val askedAt = mutableMapOf<String, Long>()

internal fun chipColours(kind: String, old: Boolean): Pair<Color, Color> = when {
    old -> Color(0xFFFFA726) to Color(0xFF222211)
    kind == "ding" -> Color(0xFF29B6F6) to Color(0xFF001122)
    kind == "motion" -> Color(0xFF5C6BC0) to Color.White
    kind == "linked" -> Color(0xFF26A69A) to Color.White
    kind == "live" -> Color(0xFF78909C) to Color.White
    else -> Color.White.copy(alpha = 0.22f) to Color.White
}

/**
 * One camera: its freshest picture with its name and why and how long ago it was taken ("Doorbell · 4 min").
 * An orange chip means the picture is older than `refresh_after` minutes (default 60); then, as on the
 * dashboard, the camera's Take Snapshot button is pressed, at most once per that time for everyone. Tap for
 * live video.
 */
@Composable
fun CameraCard(config: JSONObject, entities: Map<String, EntityState>, registry: Registry, call: CallService) {
    val entityId = config.optString("entity")
    val found = cameraFind(
        entities, entityId,
        config.optString("snapshot").takeIf { it.isNotBlank() },
        config.optString("snapshot_button").takeIf { it.isNotBlank() },
    )
    val picture = cameraPicture(entities, found)
    val name = config.optString("name").ifBlank {
        entities[entityId]?.friendlyName?.replace(Regex(" (Live view|Snapshot)$", RegexOption.IGNORE_CASE), "") ?: entityId
    }
    val refreshMs = ((if (config.has("refresh_after")) config.optDouble("refresh_after", 60.0) else 60.0) * 60_000).toLong()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }
    val old = picture == null || picture.ms == 0L || now - picture.ms > refreshMs

    // An old picture on a camera with a Take Snapshot button: ask for a new one, once per refresh time for everyone.
    LaunchedEffect(picture?.ms, found.button) {
        val button = found.button ?: return@LaunchedEffect
        if (!old) return@LaunchedEffect
        val last = maxOf(parseMillis(entities[button]?.state) ?: 0L, askedAt[entityId] ?: 0L)
        if (System.currentTimeMillis() - last >= refreshMs) {
            askedAt[entityId] = System.currentTimeMillis()
            call("button", "press", button, data())
        }
    }

    val waiting = (askedAt[entityId] ?: 0L).let { it > 0 && now - it < 120_000 && (picture == null || picture.ms < it) }
    val (chipBg, chipInk) = chipColours(picture?.kind ?: "interval", old || waiting)
    val chip = when {
        picture == null || picture.ms == 0L -> "No picture yet"
        waiting -> "${cameraAge(picture.ms, now)} · updating…"
        else -> "${cameraKindLabel(picture.kind)} · ${cameraAge(picture.ms, now)}"
    }
    val battery = found.battery?.let { entities[it]?.state?.toDoubleOrNull() }
    val base = LocalBaseUrl.current
    var viewing by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF5D6B7D), Color(0xFF2F3946), Color(0xFF46503C))))
            .clickable { viewing = true },
    ) {
        if (picture != null) {
            val url = base + picture.url + (if ('?' in picture.url) "&" else "?") + "t=${picture.ms}"
            AsyncImage(model = url, contentDescription = name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f))))
                .padding(start = 14.dp, end = 14.dp, top = 28.dp, bottom = 12.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(name, color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1, modifier = Modifier.weight(1f))
            Box(modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(chipBg).padding(horizontal = 10.dp, vertical = 3.dp)) {
                Text(chip, color = chipInk, fontSize = 11.sp, maxLines = 1)
            }
        }
        if (battery != null && battery < 25) {
            Box(
                modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).clip(RoundedCornerShape(999.dp)).background(Color.Black.copy(alpha = 0.5f)).padding(6.dp),
            ) { HaIcon("mdi:battery-alert", Icons.Filled.Info, Color(0xFFFFA726), 18.dp) }
        }
    }

    if (viewing) {
        val eventsBase = cameraBaseName(entities, entityId)
        val light = cameraLight(entities, registry, entityId, config.optString("light").takeIf { it.isNotBlank() })
        CameraViewer(name, entityId, found.button, eventsBase, light, entities, call) { viewing = false }
    }
}

/**
 * Full-screen live video (muted to start, as on the dashboard), with a new-snapshot button. It uses WebRTC when
 * the camera offers it (as Home Assistant's own player does), else an HLS stream.
 */
@Composable
internal fun CameraViewer(
    name: String,
    entityId: String,
    button: String?,
    eventsBase: String?,
    light: String?,
    entities: Map<String, EntityState>,
    call: CallService,
    onClose: () -> Unit,
) {
    val host = LocalCameraHost.current
    val base = LocalBaseUrl.current
    var mode by remember { mutableStateOf<String?>(null) } // webrtc, hls or none, once known
    var link by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>("Starting live view…") }
    var muted by remember { mutableStateOf(true) }
    var showEvents by remember { mutableStateOf(false) }
    LaunchedEffect(entityId) {
        if (host == null) {
            mode = "none"
            status = "Live view isn't available."
            return@LaunchedEffect
        }
        host.capabilities(entityId) { types ->
            when {
                "web_rtc" in types -> mode = "webrtc"
                "hls" in types || types.isEmpty() -> host.hlsStream(entityId) { url ->
                    if (url == null) {
                        mode = "none"
                        status = "Live view isn't available for this camera."
                    } else {
                        link = url
                        mode = "hls"
                        status = null
                    }
                }
                else -> {
                    mode = "none"
                    status = "Live view isn't available for this camera."
                }
            }
        }
    }
    if (showEvents && eventsBase != null) CameraEventsViewer(name, eventsBase) { showEvents = false }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
            Column(modifier = Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name, color = Color.White, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).padding(start = 8.dp))
                    IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White) }
                }
                Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color(0xFF111111)), contentAlignment = Alignment.Center) {
                    when (mode) {
                        "webrtc" -> WebRtcPlayer(entityId, muted, Modifier.fillMaxSize()) { status = it }
                        "hls" -> link?.let { LivePlayer(base + it, muted) }
                        else -> Unit
                    }
                    status?.let { Text(it, color = Color.White.copy(alpha = 0.75f), modifier = Modifier.padding(16.dp)) }
                }
                val white = ToneColors(Color.Black, Color.White, Color.White, Color.Black)
                val canListen = mode == "webrtc" || mode == "hls"
                Column(modifier = Modifier.padding(horizontal = 8.dp)) {
                    TileRow(
                        buildList {
                            add(TileItem(if (muted) "mdi:volume-off" else "mdi:volume-high", if (muted) "Unmute" else "Mute", !muted && canListen) { if (canListen) muted = !muted })
                            if (button != null) add(TileItem(IconMap.of("camera", "snapshot", "mdi:camera-retake"), "New snapshot", false) { call("button", "press", button, data()) })
                            if (eventsBase != null) add(TileItem(IconMap.of("camera", "events", "mdi:history"), "Events", false) { showEvents = true })
                            if (light != null) {
                                val on = entities[light]?.state == "on"
                                val lightName = entities[light]?.friendlyName?.replace(Regex(" light$", RegexOption.IGNORE_CASE), "") ?: "Light"
                                add(TileItem(if (on) "mdi:lightbulb-on" else "mdi:lightbulb-outline", "$lightName ${if (on) "on" else "off"}", on) { call("light", "toggle", light, data()) })
                            }
                        },
                        white, Color.White,
                    )
                }
            }
        }
    }
}

/** An HLS live stream, played in the app. The player is released as soon as the viewer closes. */
@OptIn(UnstableApi::class)
@Composable
private fun LivePlayer(url: String, muted: Boolean) {
    val context = LocalContext.current
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            playWhenReady = true
        }
    }
    LaunchedEffect(muted, player) { player.volume = if (muted) 0f else 1f }
    DisposableEffect(player) { onDispose { player.release() } }
    AndroidView(
        factory = { PlayerView(it).apply { useController = false; this.player = player } },
        update = { it.player = player },
        modifier = Modifier.fillMaxSize(),
    )
}

/** The live view of a camera by its entity, for opening it from outside its card (a widget's tap). */
@Composable
fun CameraViewerFor(entityId: String, entities: Map<String, EntityState>, registry: Registry, call: CallService, onClose: () -> Unit) {
    val found = cameraFind(entities, entityId, null, null)
    val name = entities[entityId]?.friendlyName?.replace(Regex(" (Live view|Snapshot)$", RegexOption.IGNORE_CASE), "") ?: entityId
    CameraViewer(name, entityId, found.button, cameraBaseName(entities, entityId), cameraLight(entities, registry, entityId, null), entities, call, onClose)
}
