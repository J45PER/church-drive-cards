package com.churchdrive.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

private fun JSONObject.side(key: String): JSONObject = optJSONObject(key) ?: JSONObject()

private fun JSONObject.number(key: String): Double? = optDouble(key).takeIf { !it.isNaN() }

private fun short(side: JSONObject): String = side.optString("short").ifBlank { side.optString("name").take(3).uppercase() }

/** A club badge: its picture on a white disc, with the club's three letters behind it for while it loads or when there is none. */
@Composable
private fun CardBadge(side: JSONObject, size: Dp) {
    val url = side.optString("badge_url").ifBlank { null }
    Box(Modifier.size(size).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
        Text(short(side), color = Color(0xFF1C2C5B), fontSize = (size.value * 0.3f).sp, fontWeight = FontWeight.Bold, maxLines = 1)
        if (url != null) coil.compose.AsyncImage(model = url, contentDescription = side.optString("name"), contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().clip(CircleShape))
    }
}

/**
 * What Clive sends for an answer worth seeing (the house's `clive_show_card`): the weather, a score or fixture with the clubs' badges,
 * what is playing. Drawn under his written answer, the phone's version of the watch's card.
 */
@Composable
fun AssistantCardView(card: AssistantCard, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val f = card.fields
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(scheme.surfaceContainerHighest).padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        when (card.kind) {
            "score" -> {
                val home = f.side("home")
                val away = f.side("away")
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    CardBadge(home, 48.dp)
                    Text("${f.optInt("home_score")} – ${f.optInt("away_score")}", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = scheme.onSurface)
                    CardBadge(away, 48.dp)
                }
                Text(listOf(home.optString("name"), away.optString("name")).filter { it.isNotBlank() }.joinToString(" v "), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center)
                if (f.optString("status").isNotBlank()) Text(f.optString("status"), style = MaterialTheme.typography.titleSmall, color = scheme.onSurface)
                if (f.optString("scorers").isNotBlank()) Text(f.optString("scorers"), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            "fixture" -> {
                val home = f.side("home")
                val away = f.side("away")
                if (home.has("badge_url") || away.has("badge_url")) Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CardBadge(home, 44.dp)
                    Text("v", color = scheme.onSurfaceVariant)
                    CardBadge(away, 44.dp)
                }
                if (f.optString("title").isNotBlank()) Text(f.optString("title"), style = MaterialTheme.typography.titleMedium, color = scheme.onSurface, textAlign = TextAlign.Center)
                if (f.optString("when").isNotBlank()) Text(f.optString("when"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = scheme.onSurface)
                val line = listOf(f.optString("time"), f.optString("in")).filter { it.isNotBlank() }.joinToString(" · ")
                if (line.isNotEmpty()) Text(line, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface)
                val where = listOf(f.optString("where"), f.optString("comp")).filter { it.isNotBlank() }.joinToString(" · ")
                if (where.isNotEmpty()) Text(where, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
            "weather" -> {
                Text(f.number("temp")?.let { "${Math.round(it)}°" } ?: "–", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = scheme.onSurface)
                if (f.optString("desc").isNotBlank()) Text(f.optString("desc"), style = MaterialTheme.typography.titleSmall, color = scheme.onSurface)
                val line = listOfNotNull(f.number("high")?.let { "High ${Math.round(it)}°" }, f.number("low")?.let { "Low ${Math.round(it)}°" }, f.number("rain")?.let { "${Math.round(it)}% rain" }).joinToString(" · ")
                if (line.isNotEmpty()) Text(line, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
            "music" -> {
                Text(f.optString("title"), style = MaterialTheme.typography.titleMedium, color = scheme.onSurface, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (f.optString("artist").isNotBlank()) Text(f.optString("artist"), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                if (f.optString("device").isNotBlank()) Text("Playing on ${f.optString("device")}", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
        }
    }
}
