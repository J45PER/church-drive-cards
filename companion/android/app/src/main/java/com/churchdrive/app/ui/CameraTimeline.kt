package com.churchdrive.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/**
 * The activity timeline under the live view, as in Ring: a strip of the camera's saved events, newest first, after a red
 * Live tile. [selectedId] is the event being played (null: live).
 */
@Composable
internal fun CameraTimeline(events: List<CameraEvent>, selectedId: String?, baseUrl: String, onLive: () -> Unit, onPick: (CameraEvent) -> Unit) {
    if (events.isEmpty()) return
    val now = System.currentTimeMillis()
    LazyRow(contentPadding = PaddingValues(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item(key = "live") {
            Box(
                modifier = Modifier.width(104.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).background(Color(0xFFE53935))
                    .then(if (selectedId == null) Modifier.border(BorderStroke(2.dp, Color.White), RoundedCornerShape(10.dp)) else Modifier)
                    .clickable(onClick = onLive),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    HaIcon("mdi:video", Icons.Filled.Info, Color.White, 18.dp)
                    Text("LIVE", color = Color.White, fontSize = 13.sp)
                }
            }
        }
        items(events, key = { it.id }) { e ->
            val (bg, ink) = chipColours(e.kind, false)
            Box(
                modifier = Modifier.width(104.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).background(Color(0xFF222222))
                    .then(if (selectedId == e.id) Modifier.border(BorderStroke(2.dp, Color.White), RoundedCornerShape(10.dp)) else Modifier)
                    .clickable { onPick(e) },
            ) {
                e.picture?.let { AsyncImage(model = baseUrl + it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                if (e.clip != null) {
                    Box(modifier = Modifier.align(Alignment.TopStart).padding(4.dp)) { HaIcon("mdi:play", Icons.Filled.Info, Color.White, 16.dp) }
                }
                Box(modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).clip(RoundedCornerShape(999.dp)).background(bg).padding(horizontal = 5.dp, vertical = 1.dp)) {
                    Text(cameraKindLabel(e.kind), color = ink, fontSize = 9.sp, maxLines = 1)
                }
                Text(timelineLabel(e.ts, now), color = Color.White, fontSize = 11.sp, modifier = Modifier.align(Alignment.BottomStart).padding(5.dp))
            }
        }
    }
}
