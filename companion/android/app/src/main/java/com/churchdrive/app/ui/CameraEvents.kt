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
    Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp)).background(Color(0xFF111111)), contentAlignment = Alignment.Center) {
        when {
            event.clip != null -> ClipPlayer(baseUrl + event.clip)
            event.picture != null -> AsyncImage(model = baseUrl + event.picture, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            else -> Text("Nothing was saved for this event.", color = Color.White.copy(alpha = 0.75f))
        }
    }
}

/** A saved clip, played from the start with the usual controls. Released as soon as it's replaced or closed. */
@OptIn(UnstableApi::class)
@Composable
private fun ClipPlayer(url: String) {
    val context = LocalContext.current
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    AndroidView(
        factory = { PlayerView(it).apply { useController = true; this.player = player } },
        update = { it.player = player },
        modifier = Modifier.fillMaxSize(),
    )
}
