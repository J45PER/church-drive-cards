package com.churchdrive.app.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.churchdrive.app.ha.CallService
import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ha.data
import kotlinx.coroutines.delay

/** A button of the live view that isn't on the call row: Light, Snapshot, Events. */
internal data class LiveAction(val icon: String, val label: String, val run: () -> Unit)

/** The activity a composable is shown in (a dialog's context wraps it), to turn the screen sideways. */
private fun Context.findActivity(): Activity? = generateSequence(this) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()

/** Seconds as "0:12" or "1:05:03". */
internal fun liveClock(seconds: Long): String {
    val s = seconds.coerceAtLeast(0)
    return if (s >= 3600) "${s / 3600}:${(s / 60 % 60).toString().padStart(2, '0')}:${(s % 60).toString().padStart(2, '0')}" else "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}

/**
 * The experimental live view ("layout C").
 *
 * - **Normal**: the video in a 16:9 box (a tall picture such as the doorbell's is cropped to it: drag up and down). Only two small
 *   icons sit on it (bigger, full screen). Under it: a timer and Hang up / Talk / Sound, then Light, Snapshot and Events.
 * - **Expanded** (remembered per camera): the picture fills the space between the title and the buttons. Tap it to show or hide the
 *   buttons; they hide by themselves after 3 s. Battery and signal stay on the picture.
 * - **Full screen**: the phone turns to landscape. Same tap. Hang up / Talk / Sound bottom left, + (Light, Snapshot, Events) bottom
 *   right, exit top right with the battery and signal beside it.
 *
 * No activity timeline here: Events opens the saved events. When the doorbell rings the row under the picture is one big Answer
 * button; answering turns on the sound and the microphone.
 */
@Composable
internal fun CameraViewerC(
    name: String,
    entityId: String,
    found: CameraEntities,
    eventsBase: String?,
    light: String?,
    entities: Map<String, EntityState>,
    call: CallService,
    startAnswered: Boolean = false,
    onClose: () -> Unit,
) {
    val host = LocalCameraHost.current
    val base = LocalBaseUrl.current
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("camera_view", Context.MODE_PRIVATE) }
    var mode by remember { mutableStateOf<String?>(null) } // webrtc, hls or none, once known
    var link by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>("Starting live view…") }
    var sound by remember { mutableStateOf(startAnswered) }
    var talking by remember { mutableStateOf(false) }
    var micDenied by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(prefs.getBoolean(entityId, false)) }
    var full by remember { mutableStateOf(false) }
    var aspect by remember { mutableStateOf(16f / 9f) }
    var showEvents by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var answeredAt by remember { mutableLongStateOf(if (startAnswered) System.currentTimeMillis() else 0L) }
    val startedAt = remember { System.currentTimeMillis() }
    var elapsed by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            elapsed = (System.currentTimeMillis() - startedAt) / 1000
            delay(1000)
        }
    }

    // Two-way audio: the microphone goes to the camera over the same WebRTC connection (only WebRTC can carry it).
    val micAsk = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) { talking = true; sound = true } else micDenied = true
    }
    fun startTalking() {
        micDenied = false
        if (hasMicrophone(context)) {
            talking = true
            sound = true
        } else {
            micAsk.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    // Answer opens the live view already talking.
    LaunchedEffect(Unit) { if (startAnswered) startTalking() }

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

    // Full screen turns the phone to landscape; leaving it (or closing) gives the screen back.
    DisposableEffect(full) {
        val act = context.findActivity()
        if (full) act?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose { if (full) act?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }

    // Expanded and full screen: a tap shows or hides the buttons, and they hide by themselves after 3 seconds.
    val over = expanded || full
    var shown by remember { mutableStateOf(true) }
    var touched by remember { mutableIntStateOf(0) }
    LaunchedEffect(over) { shown = true }
    LaunchedEffect(over, shown, touched, menuOpen) {
        if (over && shown && !menuOpen) {
            delay(3000)
            shown = false
        }
    }

    val battery = found.battery?.let { entities[it]?.state?.toDoubleOrNull() }
    val bars = found.signal?.let { signalBars(entities[it]?.state, entities[it]?.state) } ?: -1
    val ringing = isRinging(found.ringSensor?.let { entities[it]?.state }, found.ding?.let { entities[it]?.state }, System.currentTimeMillis(), answeredAt)
    val canTalk = mode == "webrtc"
    val canListen = mode == "webrtc" || mode == "hls"
    val live = canListen && status == null

    // The call row: Answer while it rings, else Hang up / Talk / Sound. [close] ends the live view.
    val callRow: @Composable (Boolean, () -> Unit) -> Unit = { labels, close ->
        CallRow(
            ringing = ringing, labels = labels, canTalk = canTalk, talking = talking && canTalk, sound = sound,
            onHang = close,
            onTalk = { if (talking) talking = false else startTalking() },
            onSound = { sound = !sound },
            onAnswer = {
                answeredAt = System.currentTimeMillis()
                sound = true
                if (canTalk) startTalking()
            },
        )
    }
    val actions = buildList {
        if (light != null) {
            val on = entities[light]?.state == "on"
            val lightName = entities[light]?.friendlyName?.replace(Regex(" light$", RegexOption.IGNORE_CASE), "") ?: "Light"
            add(LiveAction(if (on) "mdi:lightbulb-on" else "mdi:lightbulb-outline", "$lightName ${if (on) "on" else "off"}") { call("light", "toggle", light, data()) })
        }
        if (found.button != null) add(LiveAction(IconMap.of("camera", "snapshot", "mdi:camera-retake"), "Snapshot") { call("button", "press", found.button, data()) })
        if (eventsBase != null) add(LiveAction(IconMap.of("camera", "events", "mdi:history"), "Events") { showEvents = true })
    }

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
                Box(modifier = if (fill) Modifier.weight(1f).fillMaxWidth() else Modifier.fillMaxWidth()) {
                    // A 16:9 box that the picture covers: a tall picture is cropped and can be dragged up and down.
                    StageBox(aspect, fill = fill, contain = false, maxZoom = 6f, onTap = { if (over) { shown = !shown; touched++ } }) {
                        when (mode) {
                            "webrtc" -> WebRtcPlayer(entityId, !sound, Modifier.fillMaxSize(), talking = talking, onAspect = { aspect = it }) { status = it }
                            "hls" -> link?.let { LivePlayer(base + it, !sound) { aspect = it } }
                            else -> Unit
                        }
                    }
                    status?.let { Text(it, color = Color.White.copy(alpha = 0.75f), modifier = Modifier.align(Alignment.Center).padding(16.dp)) }

                    // Top left: what this is. Top right: battery and signal when expanded or full screen, and the way out of full screen.
                    Row(
                        modifier = Modifier.align(Alignment.TopStart).padding(8.dp).then(if (full) Modifier.statusBarsPadding() else Modifier),
                    ) {
                        Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(if (live) Color(0xFFE53935) else Color(0xFF616875)).padding(horizontal = 7.dp, vertical = 3.dp)) {
                            Text(if (!live) "● Connecting…" else if (talking && canTalk) "● LIVE · talking" else "● LIVE", color = Color.White, fontSize = 11.sp, maxLines = 1)
                        }
                    }
                    Row(
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).then(if (full) Modifier.statusBarsPadding() else Modifier),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (over) {
                            if (battery != null) {
                                Row(
                                    modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 8.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    HaIcon(batteryIconName(battery), Icons.Filled.Info, if (battery < 15) Color(0xFFEF5350) else if (battery < 30) Color(0xFFFFA726) else Color.White, 18.dp)
                                    Text("${battery.toInt()}%", color = Color.White, fontSize = 12.sp)
                                }
                            }
                            if (found.signal != null) {
                                Box(modifier = Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)).padding(6.dp)) {
                                    HaIcon(signalIconName(bars), Icons.Filled.Info, if (bars in 0..1) Color(0xFFEF5350) else if (bars == 2) Color(0xFFFFA726) else Color.White, 18.dp)
                                }
                            }
                        }
                        if (full) Fade(visible = shown) {
                            OverlayButton("mdi:fullscreen-exit") { full = false; touched++ }
                        }
                    }

                    // Bottom right: bigger and full screen on the picture (always in the normal view); in full screen, + for Light, Snapshot and Events.
                    if (!full) {
                        Fade(visible = !over || shown, modifier = Modifier.align(Alignment.BottomEnd)) {
                            Row(modifier = Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OverlayButton(if (expanded) "mdi:arrow-collapse-vertical" else "mdi:arrow-expand-vertical") {
                                    expanded = !expanded
                                    prefs.edit().putBoolean(entityId, expanded).apply()
                                }
                                OverlayButton("mdi:fullscreen") { full = true }
                            }
                        }
                    } else {
                        Fade(visible = shown, modifier = Modifier.align(Alignment.BottomEnd)) {
                            Box(modifier = Modifier.padding(10.dp)) {
                                CircleButton("mdi:plus", Color.White, Color(0xFF3C4248), 44) { menuOpen = true; touched++ }
                                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                    actions.forEach { (icon, label, run) ->
                                        DropdownMenuItem(
                                            text = { Text(label) },
                                            leadingIcon = { HaIcon(icon, Icons.Filled.Info, MaterialTheme.colorScheme.onSurface, 20.dp) },
                                            onClick = { menuOpen = false; run() },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Expanded and full screen: the call row sits on the picture (bottom centre; bottom left in full screen).
                    if (over) {
                        Fade(
                            visible = shown,
                            modifier = if (full) Modifier.align(Alignment.BottomStart) else Modifier.align(Alignment.BottomCenter),
                        ) {
                            Box(modifier = if (full) Modifier.padding(10.dp) else Modifier.fillMaxWidth().padding(bottom = 56.dp), contentAlignment = Alignment.Center) {
                                callRow(false) { touched++; close() }
                            }
                        }
                    }
                    if (micDenied) {
                        Text(
                            "The microphone is off for this app. Allow it in Android's settings to talk.",
                            color = Color(0xFFFFA726), fontSize = 12.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                        )
                    }
                }
                if (!full) {
                    // Normal view: the timer and the call row under the picture.
                    if (!over) {
                        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (!ringing) Text(liveClock(elapsed), color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
                            callRow(true, close)
                        }
                    }
                    val white = ToneColors(Color.Black, Color.White, Color.White, Color.Black)
                    Column(modifier = Modifier.padding(horizontal = 8.dp)) {
                        if (actions.isNotEmpty()) TileRow(actions.map { (icon, label, run) -> TileItem(icon, label, false) { run() } }, white, Color.White)
                    }
                }
            }
        }
    }
}

/** Hang up / Talk / Sound (with labels under the picture), or one big Answer button while the doorbell rings. */
@Composable
private fun CallRow(
    ringing: Boolean,
    labels: Boolean,
    canTalk: Boolean,
    talking: Boolean,
    sound: Boolean,
    onHang: () -> Unit,
    onTalk: () -> Unit,
    onSound: () -> Unit,
    onAnswer: () -> Unit,
) {
    if (ringing && labels) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).clip(RoundedCornerShape(999.dp)).background(Color(0xFF2E9D4F)).clickable(onClick = onAnswer).padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HaIcon("mdi:phone", Icons.Filled.Info, Color.White, 22.dp)
            Text("  Answer", color = Color.White, fontSize = 16.sp)
        }
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(if (labels) 22.dp else 12.dp), verticalAlignment = Alignment.Top) {
        CircleButton("mdi:phone-hangup", Color.White, Color(0xFFD93025), 46, if (labels) "Hang up" else null, onHang)
        if (canTalk) {
            CircleButton(
                if (talking) "mdi:microphone" else "mdi:microphone-off",
                if (talking) Color.Black else Color.White, if (talking) Color.White else Color(0xFF3C4248), 46, if (labels) (if (talking) "Talking" else "Talk") else null, onTalk,
            )
        }
        CircleButton(
            if (sound) "mdi:volume-high" else "mdi:volume-off",
            if (sound) Color.Black else Color.White, if (sound) Color.White else Color(0xFF3C4248), 46, if (labels) "Sound" else null, onSound,
        )
    }
}

/** A round button, with its label under it when [label] is given. */
@Composable
private fun CircleButton(icon: String, tint: Color, background: Color, size: Int, label: String? = null, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier.size(size.dp).clip(CircleShape).background(background).clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { HaIcon(icon, Icons.Filled.Info, tint, 22.dp) }
        if (label != null) Text(label, color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
    }
}

/** A small round button on the picture. */
@Composable
private fun OverlayButton(icon: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(34.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { HaIcon(icon, Icons.Filled.Info, Color.White, 20.dp) }
}

/** Fades [content] in and out. Not an extension of a layout scope, so it can be called from inside any Box, Row or Column. */
@Composable
private fun Fade(visible: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    androidx.compose.animation.AnimatedVisibility(visible = visible, modifier = modifier, enter = fadeIn(), exit = fadeOut()) { content() }
}
