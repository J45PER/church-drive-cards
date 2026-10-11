package com.churchdrive.app.ui

import androidx.annotation.OptIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.CircleShape
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableFloatStateOf
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

/** Each camera's last picture shape, so its tile is the right height before the picture loads. */
private val tileShapes = mutableMapOf<String, Float>()

/** A small round status icon over a picture. */
@Composable
private fun StatusDot(icon: String, tint: Color) {
    Box(modifier = Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.5f)).padding(6.dp)) { HaIcon(icon, Icons.Filled.Info, tint, 18.dp) }
}

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
    val bars = found.signal?.let { signalBars(entities[it]?.state, entities[it]?.state) } ?: -1
    val base = LocalBaseUrl.current
    var viewing by remember { mutableStateOf(false) }
    var answeredAt by remember { mutableLongStateOf(0L) }
    var answerOnOpen by remember { mutableStateOf(false) }
    val ringing = isRinging(found.ringSensor?.let { entities[it]?.state }, found.ding?.let { entities[it]?.state }, System.currentTimeMillis(), answeredAt)
    // The tile is the picture's own shape (kept between 3:4 and 16:9), remembered so it is the right height before the picture loads.
    var shape by remember(entityId) { mutableFloatStateOf(tileShapes[entityId] ?: (16f / 9f)) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(shape)
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF5D6B7D), Color(0xFF2F3946), Color(0xFF46503C))))
            .then(if (ringing) Modifier.border(BorderStroke(2.dp, Color(0xFF43A047)), RoundedCornerShape(24.dp)) else Modifier)
            .clickable { viewing = true },
    ) {
        if (picture != null) {
            val url = base + picture.url + (if ('?' in picture.url) "&" else "?") + "t=${picture.ms}"
            AsyncImage(
                model = url, contentDescription = name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                onSuccess = { state ->
                    val d = state.result.drawable
                    val a = tileAspect(d.intrinsicWidth, d.intrinsicHeight)
                    tileShapes[entityId] = a
                    shape = a
                },
            )
        }
        // Top row: why and when on the left, what needs attention on the right. Bottom row: the name, and Answer when it rings.
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent)))
                .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 22.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(modifier = Modifier.weight(1f, fill = false).clip(RoundedCornerShape(999.dp)).background(chipBg).padding(horizontal = 10.dp, vertical = 3.dp)) {
                Text(chip, color = chipInk, fontSize = 11.sp, maxLines = 1)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (battery != null && battery < 25) StatusDot(batteryIconName(battery), Color(0xFFFFA726))
                if (bars in 0..1) StatusDot(signalIconName(bars), Color(0xFFFFA726))
            }
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
            if (ringing) {
                Row(
                    modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(Color(0xFF43A047)).clickable { answerOnOpen = true; answeredAt = System.currentTimeMillis(); viewing = true }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    HaIcon("mdi:phone", Icons.Filled.Info, Color.White, 18.dp)
                    Text("Answer", color = Color.White, fontSize = 13.sp)
                }
            }
        }
    }

    if (viewing) {
        val eventsBase = cameraBaseName(entities, entityId)
        val light = cameraLight(entities, registry, entityId, config.optString("light").takeIf { it.isNotBlank() })
        CameraViewer(name, entityId, found, eventsBase, light, entities, call, startUnmuted = answerOnOpen) { viewing = false; answerOnOpen = false }
    }
}

/** The activity a composable is shown in (a dialog's context wraps it), to turn the screen sideways. */
private fun Context.findActivity(): Activity? = generateSequence(this) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()

/**
 * Live video as a pop-up (muted to start, as on the dashboard, unless it was opened with Answer). It uses WebRTC when the
 * camera offers it (as Home Assistant's own player does), else an HLS stream.
 *
 * Three sizes: the video at its own shape under the title; **expanded**, which fills the screen between the title and the
 * buttons (remembered per camera); and **full screen**, which turns the phone to landscape and shows only the video. Battery
 * and signal icons show on the video when it is expanded or full screen. Under the buttons is the activity timeline.
 */
@Composable
internal fun CameraViewer(
    name: String,
    entityId: String,
    found: CameraEntities,
    eventsBase: String?,
    light: String?,
    entities: Map<String, EntityState>,
    call: CallService,
    startUnmuted: Boolean = false,
    onClose: () -> Unit,
) {
    val host = LocalCameraHost.current
    val base = LocalBaseUrl.current
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("camera_view", Context.MODE_PRIVATE) }
    var mode by remember { mutableStateOf<String?>(null) } // webrtc, hls or none, once known
    var link by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>("Starting live view…") }
    var muted by remember { mutableStateOf(!startUnmuted) }
    var showEvents by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(prefs.getBoolean(entityId, false)) }
    var events by remember { mutableStateOf<List<CameraEvent>>(emptyList()) }
    var playing by remember { mutableStateOf<CameraEvent?>(null) }
    var answeredAt by remember { mutableStateOf(if (startUnmuted) System.currentTimeMillis() else 0L) }
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
    // The timeline: the saved events, loaded again a few seconds after a new one (the picture is saved after the event).
    val activity = eventsBase?.let { entities["sensor.${it}_last_activity"]?.state }
    LaunchedEffect(eventsBase, activity) {
        if (eventsBase == null || host == null) return@LaunchedEffect
        if (events.isNotEmpty()) delay(8_000)
        host.events(eventsBase) { result -> if (result != null) events = parseCameraEvents(result).take(40) }
    }
    if (showEvents && eventsBase != null) CameraEventsViewer(name, eventsBase) { showEvents = false }
    // The picture's own shape (known once it plays), so a square or portrait camera isn't cropped.
    var aspect by remember { mutableStateOf(16f / 9f) }
    var full by remember { mutableStateOf(false) }
    // Full screen turns the phone to landscape; leaving it (or closing) gives the screen back.
    DisposableEffect(full) {
        val act = context.findActivity()
        if (full) act?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose { if (full) act?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }
    val battery = found.battery?.let { entities[it]?.state?.toDoubleOrNull() }
    val bars = found.signal?.let { signalBars(entities[it]?.state, entities[it]?.state) } ?: -1
    val ringing = isRinging(found.ringSensor?.let { entities[it]?.state }, found.ding?.let { entities[it]?.state }, System.currentTimeMillis(), answeredAt)
    val canListen = mode == "webrtc" || mode == "hls"
    // Back leaves full screen first, then the viewer.
    PopupWindow(onDismissRequest = onClose, handleBack = { if (full) { full = false; true } else false }) { close ->
        Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
            Column(
                modifier = Modifier.fillMaxSize().then(if (full) Modifier else Modifier.padding(8.dp)),
                verticalArrangement = Arrangement.spacedBy(if (full) 0.dp else 12.dp),
            ) {
                if (!full) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(name, color = Color.White, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).padding(start = 8.dp))
                        IconButton(onClick = close) { Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White) }
                    }
                }
                val fill = full || expanded
                // One stage for both: pinch to zoom, drag to look around, double-tap to zoom in and out.
                Box(modifier = if (fill) Modifier.weight(1f).fillMaxWidth() else Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val clipEvent = playing
                    if (clipEvent != null && !full) {
                        EventPlayer(clipEvent, base)
                    } else {
                        StageBox(aspect, fill = fill, boxAspect = aspect.coerceIn(0.75f, 16f / 9f), contain = true, maxZoom = 6f) {
                            when (mode) {
                                "webrtc" -> WebRtcPlayer(entityId, muted, Modifier.fillMaxSize(), onAspect = { aspect = it }) { status = it }
                                "hls" -> link?.let { LivePlayer(base + it, muted) { aspect = it } }
                                else -> Unit
                            }
                        }
                    }
                    status?.let { if (clipEvent == null || full) Text(it, color = Color.White.copy(alpha = 0.75f), modifier = Modifier.padding(16.dp)) }
                    // One bar over the top of the video for everything on it, so nothing can sit on anything else.
                    Row(
                        modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(8.dp).then(if (full) Modifier.statusBarsPadding() else Modifier),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top,
                    ) {
                        val live = clipEvent == null && canListen && status == null
                        val label = when {
                            clipEvent != null && !full -> "${cameraKindLabel(clipEvent.kind)} · ${timelineLabel(clipEvent.ts, System.currentTimeMillis())}"
                            live -> "● LIVE"
                            else -> ""
                        }
                        if (label.isNotEmpty()) {
                            Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(if (live) Color(0xFFE53935) else chipColours(clipEvent?.kind ?: "motion", false).first).padding(horizontal = 7.dp, vertical = 3.dp)) {
                                Text(label, color = Color.White, fontSize = 11.sp, maxLines = 1)
                            }
                        } else Spacer(Modifier.size(1.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (expanded || full) {
                                if (battery != null) {
                                    Row(
                                        modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = 8.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        HaIcon(batteryIconName(battery), Icons.Filled.Info, if (battery < 15) Color(0xFFEF5350) else if (battery < 30) Color(0xFFFFA726) else Color.White, 18.dp)
                                        Text("${battery.toInt()}%", color = Color.White, fontSize = 12.sp)
                                    }
                                }
                                if (found.signal != null) StatusDot(signalIconName(bars), if (bars in 0..1) Color(0xFFEF5350) else if (bars == 2) Color(0xFFFFA726) else Color.White)
                            }
                            if (canListen && clipEvent == null) OverlayButton(if (muted) "mdi:volume-off" else "mdi:volume-high") { muted = !muted }
                            if (!full) OverlayButton(if (expanded) "mdi:arrow-collapse-vertical" else "mdi:arrow-expand-vertical") {
                                expanded = !expanded
                                prefs.edit().putBoolean(entityId, expanded).apply()
                            }
                            OverlayButton(if (full) "mdi:fullscreen-exit" else "mdi:fullscreen") { full = !full }
                        }
                    }
                }
                if (!full) {
                    val white = ToneColors(Color.Black, Color.White, Color.White, Color.Black)
                    Column(modifier = Modifier.padding(horizontal = 8.dp)) {
                        TileRow(
                            buildList {
                                if (ringing) add(TileItem("mdi:phone", "Answer", true) { muted = false; playing = null; answeredAt = System.currentTimeMillis() })
                                if (found.button != null) add(TileItem(IconMap.of("camera", "snapshot", "mdi:camera-retake"), "New snapshot", false) { call("button", "press", found.button, data()) })
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
                    CameraTimeline(events, playing?.id, base, onLive = { playing = null }, onPick = { playing = it })
                }
            }
        }
    }
}

/** A round button over the video. */
@Composable
private fun OverlayButton(icon: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(34.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape)) {
        HaIcon(icon, Icons.Filled.Info, Color.White, 20.dp)
    }
}

/** An HLS live stream, played in the app. The player is released as soon as the viewer closes. */
@OptIn(UnstableApi::class)
@Composable
private fun LivePlayer(url: String, muted: Boolean, onAspect: (Float) -> Unit) {
    val context = LocalContext.current
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            playWhenReady = true
            addListener(object : androidx.media3.common.Player.Listener {
                override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                    if (videoSize.height > 0) onAspect(videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height)
                }
            })
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
    CameraViewer(name, entityId, found, cameraBaseName(entities, entityId), cameraLight(entities, registry, entityId, null), entities, call, onClose)
}
