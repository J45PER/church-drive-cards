package com.churchdrive.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

private fun weatherIcon(symbol: String): String = when (symbol.lowercase().replace('_', '-')) {
    "sunny", "clear", "clear-day" -> "mdi:weather-sunny"
    "cloudy" -> "mdi:weather-cloudy"
    "rainy", "rain" -> "mdi:weather-rainy"
    "pouring" -> "mdi:weather-pouring"
    "snowy", "snow" -> "mdi:weather-snowy"
    "lightning", "lightning-rainy" -> "mdi:weather-lightning"
    "fog", "foggy" -> "mdi:weather-fog"
    "night", "clear-night" -> "mdi:weather-night"
    else -> "mdi:weather-partly-cloudy"
}

/** The colour a card takes: the weather's, a sport's navy, or none (null) for what is playing. */
private fun cardTint(card: AssistantCard): Color? = when (card.kind) {
    "weather" -> when (card.fields.optString("symbol").lowercase().replace('_', '-')) {
        "sunny", "clear", "clear-day", "partlycloudy", "partly-cloudy" -> Color(0xFF1E6FB8)
        "night", "clear-night" -> Color(0xFF1A237E)
        "snowy", "snow" -> Color(0xFF6F8794)
        "lightning", "lightning-rainy" -> Color(0xFF4527A0)
        else -> Color(0xFF455A64)
    }
    "score", "fixture" -> Color(0xFF1C2C5B)
    else -> null
}

private fun JSONObject.side(key: String): JSONObject = optJSONObject(key) ?: JSONObject()

private fun JSONObject.number(key: String): Double? = optDouble(key).takeIf { !it.isNaN() }

private fun short(side: JSONObject): String = side.optString("short").ifBlank { side.optString("name").take(3).uppercase() }

/** A club badge: its picture on a white disc, with the club's three letters behind it for while it loads or when there is none. */
@Composable
private fun CardBadge(side: JSONObject, size: Dp) {
    val given = side.optString("badge_url").ifBlank { null }
    // No address from the house: find the club's badge from its name.
    val url by androidx.compose.runtime.produceState<String?>(given, given, side.optString("name")) {
        value = given ?: side.optString("name").takeIf { it.isNotBlank() }?.let { n -> kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { TeamBadges.lookup(n) } }
    }
    Box(Modifier.size(size).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
        Text(short(side), color = Color(0xFF1C2C5B), fontSize = (size.value * 0.3f).sp, fontWeight = FontWeight.Bold, maxLines = 1)
        url?.let { u -> coil.compose.AsyncImage(model = u, contentDescription = side.optString("name"), contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().clip(CircleShape)) }
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
    val tint = cardTint(card)
    val ink = if (tint != null) Color.White else ink
    val soft = if (tint != null) Color(0xFFDDE3FF) else soft
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(if (tint != null) androidx.compose.ui.graphics.Brush.verticalGradient(listOf(tint, tint.copy(alpha = 0.55f).compositeOver(Color(0xFF0B0C14)))) else androidx.compose.ui.graphics.SolidColor(scheme.surfaceContainerHighest))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        when (card.kind) {
            "score" -> {
                val home = f.side("home")
                val away = f.side("away")
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    CardBadge(home, 48.dp)
                    Text("${f.optInt("home_score")} – ${f.optInt("away_score")}", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = ink)
                    CardBadge(away, 48.dp)
                }
                Text(listOf(home.optString("name"), away.optString("name")).filter { it.isNotBlank() }.joinToString(" v "), style = MaterialTheme.typography.bodySmall, color = soft, textAlign = TextAlign.Center)
                if (f.optString("status").isNotBlank()) Text(f.optString("status"), style = MaterialTheme.typography.titleSmall, color = ink)
                if (f.optString("scorers").isNotBlank()) Text(f.optString("scorers"), style = MaterialTheme.typography.bodySmall, color = soft, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            "fixture" -> {
                val home = f.side("home")
                val away = f.side("away")
                if (home.has("name") || away.has("name") || home.has("badge_url") || away.has("badge_url")) Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CardBadge(home, 44.dp)
                    Text("v", color = soft)
                    CardBadge(away, 44.dp)
                }
                if (f.optString("title").isNotBlank()) Text(f.optString("title"), style = MaterialTheme.typography.titleMedium, color = ink, textAlign = TextAlign.Center)
                if (f.optString("when").isNotBlank()) Text(f.optString("when"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = ink)
                val line = listOf(f.optString("time"), f.optString("in")).filter { it.isNotBlank() }.joinToString(" · ")
                if (line.isNotEmpty()) Text(line, style = MaterialTheme.typography.bodyMedium, color = ink)
                val where = listOf(f.optString("where"), f.optString("comp")).filter { it.isNotBlank() }.joinToString(" · ")
                if (where.isNotEmpty()) Text(where, style = MaterialTheme.typography.bodySmall, color = soft, textAlign = TextAlign.Center)
            }
            "weather" -> {
                HaIcon(weatherIcon(f.optString("symbol")), androidx.compose.material.icons.Icons.Filled.Info, Color.White, 44.dp)
                Text(f.number("temp")?.let { "${Math.round(it)}°" } ?: "–", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = ink)
                if (f.optString("desc").isNotBlank()) Text(f.optString("desc"), style = MaterialTheme.typography.titleSmall, color = ink)
                val line = listOfNotNull(f.number("high")?.let { "High ${Math.round(it)}°" }, f.number("low")?.let { "Low ${Math.round(it)}°" }, f.number("rain")?.let { "${Math.round(it)}% rain" }).joinToString(" · ")
                if (line.isNotEmpty()) Text(line, style = MaterialTheme.typography.bodySmall, color = soft)
            }
            "music" -> {
                Text(f.optString("title"), style = MaterialTheme.typography.titleMedium, color = ink, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (f.optString("artist").isNotBlank()) Text(f.optString("artist"), style = MaterialTheme.typography.bodyMedium, color = soft)
                if (f.optString("device").isNotBlank()) Text("Playing on ${f.optString("device")}", style = MaterialTheme.typography.bodySmall, color = soft)
            }
        }
    }
}
