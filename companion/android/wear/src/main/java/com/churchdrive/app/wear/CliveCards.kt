package com.churchdrive.app.wear

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.wear.compose.material.Text
import com.churchdrive.app.ui.AssistantCard
import com.churchdrive.app.ui.cardIcon
import com.churchdrive.app.ui.cardRows
import org.json.JSONObject

private val SOFT = Color(0xFFE8EAFF)

/** The cards drawn from the shared layout (a big figure, a title, a line, a few rows). */
private val GENERIC_KINDS = setOf("energy", "secure", "doors", "tasks", "devices", "lights", "heat", "shop", "event", "reminder", "media", "answer")

/** The symbol Clive's mark turns into for a card, or null for a card the watch has no layout for. */
fun cardSymbol(card: AssistantCard?): String? {
    card ?: return null
    if (card.kind !in GENERIC_KINDS && card.kind !in setOf("weather", "score", "scores", "fixture", "fixtures", "music")) return null
    return cardIcon(card).removePrefix("mdi:").takeIf { hasWatchIcon(it) } ?: "message-text"
}

/** The glow's colours for a card: its own `palette` when the house sent one, else the weather's or a sport's, else Clive's. */
fun cardPalette(card: AssistantCard?): GlowPalette {
    card ?: return GlowPalette.Base
    card.fields.optJSONArray("palette")?.strings()?.let { GlowPalette.from(it) }?.let { return it }
    return when (card.kind) {
        "weather" -> when (cardSymbol(card)) {
            "weather-sunny", "weather-partly-cloudy" -> GlowPalette.Clear
            "weather-night" -> GlowPalette.Night
            "weather-snowy" -> GlowPalette.Snow
            "weather-lightning" -> GlowPalette.Storm
            else -> GlowPalette.Rain
        }
        "score", "scores", "fixture", "fixtures" -> GlowPalette.Sport
        else -> GlowPalette.Base
    }
}

private fun JSONObject.side(key: String): JSONObject = optJSONObject(key) ?: JSONObject()

private fun short(side: JSONObject): String = side.optString("short").ifBlank { side.optString("name").take(3).uppercase() }

/** The figures of a card, shown under its symbol. */
@Composable
fun CardBlock(card: AssistantCard) {
    val ui = LocalUi.current
    val f = card.fields
    when (card.kind) {
        "weather" -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            val place = f.optString("location")
            if (place.isNotBlank()) Text(place, color = SOFT.copy(alpha = 0.85f), fontSize = ui.sp(11f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.Top) {
                Text(f.optDouble("temp").takeIf { !it.isNaN() }?.let { "${Math.round(it)}" } ?: "–", color = Color.White, fontSize = ui.sp(40f), fontWeight = FontWeight.Bold)
                Text("°", color = Color.White, fontSize = ui.sp(20f), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = ui.d(4f)))
            }
            Text(f.optString("desc"), color = SOFT, fontSize = ui.sp(12f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            val hi = f.optDouble("high").takeIf { !it.isNaN() }?.let { "H ${Math.round(it)}°" }
            val lo = f.optDouble("low").takeIf { !it.isNaN() }?.let { "L ${Math.round(it)}°" }
            val line = listOfNotNull(hi, lo).joinToString(" · ")
            if (line.isNotEmpty()) Text(line, color = SOFT.copy(alpha = 0.85f), fontSize = ui.sp(11f), maxLines = 1)
            // The next few hours: time over temperature.
            val hours = f.optJSONArray("hourly")
            if (hours != null && hours.length() > 0) Row(Modifier.padding(top = ui.d(4f)), horizontalArrangement = Arrangement.spacedBy(ui.d(10f))) {
                for (i in 0 until minOf(hours.length(), 4)) {
                    val h = hours.optJSONObject(i) ?: continue
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(h.optString("t").substringBefore(':'), color = SOFT.copy(alpha = 0.8f), fontSize = ui.sp(10f), maxLines = 1)
                        Text(h.optDouble("temp").takeIf { !it.isNaN() }?.let { "${Math.round(it)}°" } ?: "–", color = Color.White, fontSize = ui.sp(12f), fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                }
            }
        }
        "score" -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            val home = f.side("home")
            val away = f.side("away")
            Row(horizontalArrangement = Arrangement.spacedBy(ui.d(10f)), verticalAlignment = Alignment.CenterVertically) {
                Badge(home.optString("badge_url").ifBlank { null }, short(home), ui.d(40f), clubName = home.optString("name"))
                Text("${f.optInt("home_score")} – ${f.optInt("away_score")}", color = Color.White, fontSize = ui.sp(34f), fontWeight = FontWeight.Bold, maxLines = 1)
                Badge(away.optString("badge_url").ifBlank { null }, short(away), ui.d(40f), clubName = away.optString("name"))
            }
            Text(f.optString("status"), color = Color.White, fontSize = ui.sp(13f), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = ui.d(4f)))
            val scorers = f.optString("scorers")
            if (scorers.isNotBlank()) Text(scorers, color = SOFT.copy(alpha = 0.85f), fontSize = ui.sp(11f), textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        "scores" -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(ui.d(4f))) {
            val rows = f.optJSONArray("rows")
            for (i in 0 until minOf(rows?.length() ?: 0, 6)) {
                val r = rows?.optJSONObject(i) ?: continue
                val home = r.side("home")
                val away = r.side("away")
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ui.d(5f))) {
                    Badge(home.optString("badge_url").ifBlank { null }, short(home), ui.d(20f), clubName = home.optString("name"))
                    Text("${r.optInt("home_score")} – ${r.optInt("away_score")}", color = Color.White, fontSize = ui.sp(18f), fontWeight = FontWeight.Bold, maxLines = 1)
                    Badge(away.optString("badge_url").ifBlank { null }, short(away), ui.d(20f), clubName = away.optString("name"))
                }
            }
        }
        "fixtures" -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(ui.d(4f))) {
            val rows = f.optJSONArray("rows")
            for (i in 0 until minOf(rows?.length() ?: 0, 6)) {
                val r = rows?.optJSONObject(i) ?: continue
                val home = r.side("home")
                val away = r.side("away")
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ui.d(5f))) {
                    Badge(home.optString("badge_url").ifBlank { null }, short(home), ui.d(20f), clubName = home.optString("name"))
                    Text("v", color = SOFT, fontSize = ui.sp(11f))
                    Badge(away.optString("badge_url").ifBlank { null }, short(away), ui.d(20f), clubName = away.optString("name"))
                    Text(listOf(r.optString("when").substringBefore(' ').ifBlank { r.optString("when") }, r.optString("time")).filter { it.isNotBlank() }.joinToString(" "), color = Color.White, fontSize = ui.sp(12f), fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
        "fixture" -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            val home = f.side("home")
            val away = f.side("away")
            if (home.has("name") || away.has("name") || home.has("badge_url") || away.has("badge_url")) Row(horizontalArrangement = Arrangement.spacedBy(ui.d(8f)), verticalAlignment = Alignment.CenterVertically) {
                Badge(home.optString("badge_url").ifBlank { null }, short(home), ui.d(26f), clubName = home.optString("name"))
                Text("v", color = SOFT, fontSize = ui.sp(12f))
                Badge(away.optString("badge_url").ifBlank { null }, short(away), ui.d(26f), clubName = away.optString("name"))
            }
            Text(f.optString("title"), color = Color.White, fontSize = ui.sp(14f), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            Text(f.optString("when"), color = Color.White, fontSize = ui.sp(26f), fontWeight = FontWeight.Bold, maxLines = 1)
            Text(listOf(f.optString("time"), f.optString("in")).filter { it.isNotBlank() }.joinToString(" · "), color = Color.White, fontSize = ui.sp(13f), maxLines = 1)
            Text(listOf(f.optString("where"), f.optString("comp")).filter { it.isNotBlank() }.joinToString(" · "), color = SOFT.copy(alpha = 0.85f), fontSize = ui.sp(11f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        "music" -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (hasServiceLogo(f.optString("app"))) ServiceLogo(f.optString("app"), Color.White, ui.d(22f), Modifier.padding(bottom = ui.d(3f)))
            Text(f.optString("title"), color = Color.White, fontSize = ui.sp(16f), fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            Text(f.optString("artist"), color = SOFT, fontSize = ui.sp(12f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            val device = f.optString("device")
            if (device.isNotBlank()) Text("Playing on $device", color = SOFT.copy(alpha = 0.85f), fontSize = ui.sp(11f), modifier = Modifier.padding(top = ui.d(4f)), maxLines = 1)
        }
        in GENERIC_KINDS -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (f.optString("big").isNotBlank()) Text(f.optString("big"), color = Color.White, fontSize = ui.sp(30f), fontWeight = FontWeight.Bold, maxLines = 1)
            if (f.optString("title").isNotBlank()) Text(f.optString("title"), color = Color.White, fontSize = ui.sp(15f), fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            if (f.optString("sub").isNotBlank()) Text(f.optString("sub"), color = SOFT, fontSize = ui.sp(12f), maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            for (r in cardRows(f).take(3)) Text(listOf(r.name, r.value.ifBlank { r.pill }).filter { it.isNotBlank() }.joinToString(" · "), color = SOFT.copy(alpha = 0.9f), fontSize = ui.sp(11f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
