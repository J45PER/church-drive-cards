package com.churchdrive.app.ui

import androidx.annotation.OptIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

/**
 * A camera's saved events (doorbell presses, motion, snapshots) from the last few days, as pictures by day.
 * Tap one to play its clip above the list (or show its picture if there is no clip). Filter by kind with the chips.
 */
@Composable
fun CameraEventsViewer(name: String, base: String, onClose: () -> Unit) {
    val host = LocalCameraHost.current
    val baseUrl = LocalBaseUrl.current
    var events by remember { mutableStateOf<List<CameraEvent>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf<String?>(null) }
    var playing by remember { mutableStateOf<CameraEvent?>(null) }
    LaunchedEffect(base) {
        if (host == null) {
            failed = true
            return@LaunchedEffect
        }
        host.events(base) { result -> if (result == null) failed = true else events = parseCameraEvents(result) }
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
            Column(modifier = Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("$name events", color = Color.White, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).padding(start = 8.dp))
                    IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White) }
                }
                playing?.let { EventPlayer(it, baseUrl) }
                val all = events
                when {
                    failed -> Message("Saved events need the latest Church Drive integration (and a restart of Home Assistant).")
                    all == null -> Message("Loading events…")
                    all.isEmpty() -> Message("No saved events yet.")
                    else -> {
                        val kinds = eventFilters(all)
                        if (kinds.size > 1) {
                            Row(modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text("All") })
                                kinds.forEach { k -> FilterChip(selected = filter == k, onClick = { filter = k }, label = { Text(k) }) }
                            }
                        }
                        val groups = groupByDay(filterEvents(all, filter), System.currentTimeMillis())
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            groups.forEach { (day, list) ->
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Text(day, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
                                }
                                items(list, key = { it.id }) { e -> EventThumb(e, baseUrl, playing?.id == e.id) { playing = e } }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Message(text: String) {
    Text(text, color = Color.White.copy(alpha = 0.75f), modifier = Modifier.padding(16.dp))
}

@Composable
private fun EventThumb(event: CameraEvent, baseUrl: String, selected: Boolean, onClick: () -> Unit) {
    val (bg, ink) = chipColours(event.kind, false)
    Box(
        modifier = Modifier
            .aspectRatio(4f / 3f)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF222222))
            .then(if (selected) Modifier.border(BorderStroke(2.dp, Color.White), RoundedCornerShape(12.dp)) else Modifier)
            .clickable(onClick = onClick),
    ) {
        event.picture?.let { AsyncImage(model = baseUrl + it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        if (event.clip != null) {
            Box(modifier = Modifier.align(Alignment.Center).clip(RoundedCornerShape(999.dp)).background(Color.Black.copy(alpha = 0.5f)).padding(6.dp)) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "Has a clip", tint = Color.White)
            }
        }
        Row(
            modifier = Modifier.align(Alignment.BottomStart).padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(bg).padding(horizontal = 5.dp, vertical = 1.dp)) {
                Text(cameraKindLabel(event.kind), color = ink, fontSize = 10.sp, maxLines = 1)
            }
            Text(eventClock(event.ts), color = Color.White, fontSize = 11.sp)
        }
    }
}

/** The chosen event: its clip with controls, or its picture when it has no clip. */
@Composable
private fun EventPlayer(event: CameraEvent, baseUrl: String) {
    when {
        event.clip != null -> ClipPlayer(baseUrl + event.clip)
        event.picture != null -> StillPicture(baseUrl + event.picture)
        else -> Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp)).background(Color(0xFF111111)),
            contentAlignment = Alignment.Center,
        ) { Text("Nothing was saved for this event.", color = Color.White.copy(alpha = 0.75f)) }
    }
}

/** What fills a box of [boxW] x [boxH] with media of [aspect] (width / height): as wide as the box for a tall picture, as tall for a wide one. */
fun coverSize(aspect: Float, boxW: Float, boxH: Float): Pair<Float, Float> =
    if (aspect < boxW / boxH) boxW to boxW / aspect else boxH * aspect to boxH

/** What fits inside a box of [boxW] x [boxH] with media of [aspect] (width / height), whole: the opposite of [coverSize]. */
fun fitSize(aspect: Float, boxW: Float, boxH: Float): Pair<Float, Float> =
    if (aspect > boxW / boxH) boxW to boxW / aspect else boxH * aspect to boxH

/** Keeps one axis of a picture of [size] (after zooming) in a box of [box]: edge to edge when it is bigger, centred when it is smaller. */
private fun keepIn(offset: Float, box: Float, size: Float): Float =
    if (size <= box) (box - size) / 2f else offset.coerceIn(box - size, 0f)

/**
 * Moves a picture by [pan] and zooms it by [zoom] (up to [maxZoom]) about [centre], keeping it in the box: covering it when the picture
 * is bigger than the box, centred when it is smaller. Returns the new zoom and offset.
 */
fun panZoom(
    zoom: Float, offsetX: Float, offsetY: Float, centreX: Float, centreY: Float, panX: Float, panY: Float, zoomBy: Float,
    boxW: Float, boxH: Float, coverW: Float, coverH: Float, maxZoom: Float = 4f,
): Triple<Float, Float, Float> {
    val z = (zoom * zoomBy).coerceIn(1f, maxZoom)
    val x = centreX - (centreX - offsetX) * z / zoom + panX
    val y = centreY - (centreY - offsetY) * z / zoom + panY
    return Triple(z, keepIn(x, boxW, coverW * z), keepIn(y, boxH, coverH * z))
}

/**
 * A 16:9 box with media of its own shape inside, covering the box, as on the dashboard: a square clip fills the
 * width and can be dragged up and down to look around. Pinch or double-tap to zoom.
 */
@Composable
fun StageBox(
    aspect: Float,
    modifier: Modifier = Modifier,
    /** Fill whatever space it is given (full screen), instead of being a 16:9 box. */
    fill: Boolean = false,
    /** Show the whole picture (a live view) rather than covering the box with it (a clip). */
    contain: Boolean = false,
    maxZoom: Float = 4f,
    content: @Composable () -> Unit,
) {
    val shape = modifier.then(if (fill) Modifier.fillMaxSize() else Modifier.fillMaxWidth().aspectRatio(16f / 9f))
        .clip(RoundedCornerShape(if (fill) 0.dp else 14.dp)).background(if (fill) Color.Black else Color(0xFF111111))
    BoxWithConstraints(shape) {
        val boxW = constraints.maxWidth.toFloat()
        val boxH = constraints.maxHeight.toFloat()
        val (coverW, coverH) = if (contain) fitSize(aspect, boxW, boxH) else coverSize(aspect, boxW, boxH)
        var view by remember(aspect, boxW, boxH) { mutableStateOf(Triple(1f, (boxW - coverW) / 2f, (boxH - coverH) / 2f)) }
        val density = androidx.compose.ui.platform.LocalDensity.current
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(aspect, boxW, boxH) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val (z, x, y) = view
                        view = panZoom(z, x, y, centroid.x, centroid.y, pan.x, pan.y, zoom, boxW, boxH, coverW, coverH, maxZoom)
                    }
                }
                .pointerInput(aspect, boxW, boxH) {
                    detectTapGestures(onDoubleTap = { at ->
                        val (z, x, y) = view
                        view = panZoom(z, x, y, at.x, at.y, 0f, 0f, if (z > 1.5f) 1f / z else 2f, boxW, boxH, coverW, coverH, maxZoom)
                    })
                },
        ) {
            Box(
                Modifier
                    .requiredSize(with(density) { coverW.toDp() }, with(density) { coverH.toDp() })
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0f, 0f)
                        scaleX = view.first
                        scaleY = view.first
                        translationX = view.second
                        translationY = view.third
                    },
            ) { content() }
        }
    }
}

/** An event's picture at its own shape in the stage. */
@Composable
private fun StillPicture(url: String) {
    var aspect by remember(url) { mutableFloatStateOf(16f / 9f) }
    StageBox(aspect) {
        AsyncImage(
            model = url, contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize(),
            onSuccess = { state ->
                val d = state.result.drawable
                if (d.intrinsicHeight > 0) aspect = d.intrinsicWidth.toFloat() / d.intrinsicHeight
            },
        )
    }
}

/** A saved clip at its own shape in the stage, played from the start with a play button and a seek bar. Released as soon as it's replaced or closed. */
@OptIn(UnstableApi::class)
@Composable
private fun ClipPlayer(url: String) {
    val context = LocalContext.current
    var aspect by remember(url) { mutableFloatStateOf(16f / 9f) }
    var playing by remember(url) { mutableStateOf(true) }
    var progress by remember(url) { mutableFloatStateOf(0f) }
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(size: VideoSize) {
                if (size.height > 0) aspect = size.width * size.pixelWidthHeightRatio / size.height
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    LaunchedEffect(player) {
        while (true) {
            val d = player.duration
            if (d > 0) progress = (player.currentPosition.toFloat() / d).coerceIn(0f, 1f)
            kotlinx.coroutines.delay(250)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        StageBox(aspect) {
            AndroidView(
                factory = { PlayerView(it).apply { useController = false; this.player = player } },
                update = { it.player = player },
                modifier = Modifier.fillMaxSize(),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
                if (player.isPlaying) player.pause() else player.play()
            }) {
                Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = if (playing) "Pause" else "Play", tint = Color.White)
            }
            Slider(
                value = progress,
                onValueChange = {
                    progress = it
                    if (player.duration > 0) player.seekTo((player.duration * it).toLong())
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}
