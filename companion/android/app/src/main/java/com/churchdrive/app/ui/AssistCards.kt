package com.churchdrive.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
    "score", "scores", "fixture" -> Color(0xFF1C2C5B)
    "car" -> Color(0xFF00838F)
    else -> null
}

private fun JSONObject.side(key: String): JSONObject = optJSONObject(key) ?: JSONObject()

private fun JSONObject.number(key: String): Double? = optDouble(key).takeIf { !it.isNaN() }

private fun short(side: JSONObject): String = side.optString("short").ifBlank { side.optString("name").take(3).uppercase() }

/** The tone and word for where a person is, as the Manager's People card shows it: Home green, a named place teal, Away grey, unknown amber. */
fun personPill(state: String): Pair<String, Tone> = when (state.trim().lowercase()) {
    "home" -> "Home" to Tone.Green
    "not_home", "away", "" -> "Away" to Tone.Grey
    "unknown", "unavailable" -> "Unknown" to Tone.Amber
    else -> state.trim() to Tone.Teal
}

/** A colour given as #rrggbb (a light's own colour), or null. */
private fun hexColour(v: String): Color? = v.trim().removePrefix("#").takeIf { it.length == 6 }?.toLongOrNull(16)?.let { Color(0xFF000000 or it) }

/** A club badge: its crest, with the club's three letters behind it for while it loads or when there is none. */
@Composable
private fun CardBadge(side: JSONObject, size: Dp) {
    val given = side.optString("badge_url").ifBlank { null }
    // No address from the house: find the club's badge from its name.
    val url by androidx.compose.runtime.produceState<String?>(given, given, side.optString("name")) {
        value = given ?: side.optString("name").takeIf { it.isNotBlank() }?.let { n -> kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { TeamBadges.lookup(n) } }
    }
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        // The club's crest as it is (they are transparent pictures); its three letters show until it arrives, or if it can't be had.
        val letters: @Composable () -> Unit = { Text(short(side), color = Color.White, fontSize = (size.value * 0.3f).sp, fontWeight = FontWeight.Bold, maxLines = 1) }
        if (url == null) letters()
        else coil.compose.SubcomposeAsyncImage(
            model = url, contentDescription = side.optString("name"), contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize(),
            loading = { Box(contentAlignment = Alignment.Center) { letters() } }, error = { Box(contentAlignment = Alignment.Center) { letters() } },
        )
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
    // Mostly see-through, like frosted glass over the bubble: a faint wash of the card's colour and a hairline edge.
    val ink = scheme.onSurface
    val soft = scheme.onSurfaceVariant
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier.fillMaxWidth().clip(shape)
            .background(tint?.copy(alpha = 0.16f) ?: Color.White.copy(alpha = 0.05f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), shape)
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
            "scores" -> {
                if (f.optString("title").isNotBlank()) Text(f.optString("title"), style = MaterialTheme.typography.titleSmall, color = soft, modifier = Modifier.fillMaxWidth())
                val rows = f.optJSONArray("rows")
                for (i in 0 until (rows?.length() ?: 0)) {
                    val r = rows?.optJSONObject(i) ?: continue
                    val home = r.side("home")
                    val away = r.side("away")
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(short(home), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = ink, textAlign = TextAlign.End, maxLines = 1)
                        Box(Modifier.padding(horizontal = 6.dp)) { CardBadge(home, 28.dp) }
                        Text("${r.optInt("home_score")} – ${r.optInt("away_score")}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = ink)
                        Box(Modifier.padding(horizontal = 6.dp)) { CardBadge(away, 28.dp) }
                        Text(short(away), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = ink, maxLines = 1)
                    }
                    if (r.optString("status").isNotBlank() && r.optString("status") != "Full time") Text(r.optString("status"), style = MaterialTheme.typography.labelSmall, color = soft)
                }
            }
            "people" -> {
                val rows = f.optJSONArray("rows")
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (i in 0 until (rows?.length() ?: 0)) {
                        val r = rows?.optJSONObject(i) ?: continue
                        val (word, tone) = personPill(r.optString("state"))
                        val t = toneColors(tone)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(38.dp).clip(CircleShape).background(t.accent), contentAlignment = Alignment.Center) {
                                Text(r.optString("name").take(1).uppercase(), color = t.onAccent, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            }
                            Column(Modifier.weight(1f)) {
                                Text(r.optString("name"), style = MaterialTheme.typography.titleSmall, color = ink, maxLines = 1)
                                if (r.optString("detail").isNotBlank()) Text(r.optString("detail"), style = MaterialTheme.typography.bodySmall, color = soft, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text(word, color = t.accent, style = MaterialTheme.typography.labelMedium, maxLines = 1, modifier = Modifier.background(t.accent.copy(alpha = 0.18f), CircleShape).padding(horizontal = 10.dp, vertical = 3.dp))
                        }
                    }
                }
            }
            "done" -> {
                val failed = f.has("ok") && !f.optBoolean("ok", true)
                val given = f.optString("colour")
                val tone = if (failed) Tone.Red else toneFromColour(given) ?: Tone.Green
                val t = toneColors(tone)
                val live = hexColour(given)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(32.dp).clip(CircleShape).background((live ?: t.accent).copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
                        HaIcon(f.optString("icon").ifBlank { if (failed) "mdi:close" else "mdi:check" }, Icons.Filled.Info, live ?: t.accent, 20.dp)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(f.optString("name"), style = MaterialTheme.typography.titleSmall, color = ink)
                        if (f.optString("detail").isNotBlank()) Text(f.optString("detail"), style = MaterialTheme.typography.bodySmall, color = soft)
                    }
                }
                f.number("percent")?.let { p ->
                    Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f))) {
                        Box(Modifier.fillMaxWidth((p / 100.0).coerceIn(0.0, 1.0).toFloat()).height(6.dp).clip(CircleShape).background(live ?: t.accent))
                    }
                }
            }
            "car" -> {
                val teal = toneColors(Tone.Teal)
                val level = f.number("battery")
                val colour = carBatteryColour(level, teal.accent)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.size(84.dp), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxSize()) {
                            val w = 9.dp.toPx()
                            val inset = Offset(w / 2, w / 2)
                            val s = Size(size.width - w, size.height - w)
                            drawArc(Color.White.copy(alpha = 0.12f), 0f, 360f, false, inset, s, style = Stroke(w))
                            drawArc(colour, -90f, 360f * ((level ?: 0.0) / 100.0).coerceIn(0.0, 1.0).toFloat(), false, inset, s, style = Stroke(w, cap = StrokeCap.Round))
                        }
                        Text(level?.let { "${Math.round(it)}%" } ?: "–", color = colour, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(f.optString("name").ifBlank { "Car" }, style = MaterialTheme.typography.titleMedium, color = ink, maxLines = 1)
                        if (f.optString("state").isNotBlank()) Text(f.optString("state"), color = teal.accent, style = MaterialTheme.typography.labelMedium, maxLines = 1, modifier = Modifier.background(teal.accent.copy(alpha = 0.18f), CircleShape).padding(horizontal = 8.dp, vertical = 2.dp))
                        listOf(f.optString("range"), f.optString("power")).filter { it.isNotBlank() }.joinToString(" · ").takeIf { it.isNotEmpty() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = soft) }
                        listOf(f.optString("until"), f.optString("rate")).filter { it.isNotBlank() }.joinToString(" · ").takeIf { it.isNotEmpty() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = soft) }
                    }
                }
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
                val place = f.optString("location")
                if (place.isNotBlank()) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    HaIcon("mdi:map-marker", Icons.Filled.Info, soft, 14.dp)
                    Text(place, style = MaterialTheme.typography.labelMedium, color = soft)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    HaIcon(weatherIcon(f.optString("symbol")), Icons.Filled.Info, ink, 52.dp)
                    Column(Modifier.weight(1f)) {
                        Text(f.number("temp")?.let { "${Math.round(it)}°" } ?: "–", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = ink)
                        if (f.optString("desc").isNotBlank()) Text(f.optString("desc"), style = MaterialTheme.typography.titleSmall, color = ink)
                    }
                    val side = listOfNotNull(f.number("high")?.let { "High ${Math.round(it)}°" }, f.number("low")?.let { "Low ${Math.round(it)}°" }, f.number("rain")?.let { "${Math.round(it)}% rain" })
                    if (side.isNotEmpty()) Column(horizontalAlignment = Alignment.End) { side.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = soft) } }
                }
                val hours = f.optJSONArray("hourly")
                if (hours != null && hours.length() > 0) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(1.dp).background(Color.White.copy(alpha = 0.10f)))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        for (i in 0 until minOf(hours.length(), 6)) {
                            val h = hours.optJSONObject(i) ?: continue
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(h.optString("t"), style = MaterialTheme.typography.labelSmall, color = soft)
                                HaIcon(weatherIcon(h.optString("symbol")), Icons.Filled.Info, ink, 20.dp)
                                Text(h.number("temp")?.let { "${Math.round(it)}°" } ?: "–", style = MaterialTheme.typography.labelLarge, color = ink)
                            }
                        }
                    }
                }
            }
            "music" -> {
                Text(f.optString("title"), style = MaterialTheme.typography.titleMedium, color = ink, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (f.optString("artist").isNotBlank()) Text(f.optString("artist"), style = MaterialTheme.typography.bodyMedium, color = soft)
                if (f.optString("device").isNotBlank()) Text("Playing on ${f.optString("device")}", style = MaterialTheme.typography.bodySmall, color = soft)
            }
        }
    }
}
