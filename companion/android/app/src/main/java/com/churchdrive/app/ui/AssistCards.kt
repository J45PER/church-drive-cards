package com.churchdrive.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.graphicsLayer
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
import coil.imageLoader
import kotlinx.coroutines.async
import org.json.JSONObject

private fun JSONObject.side(key: String): JSONObject = optJSONObject(key) ?: JSONObject()

private fun JSONObject.number(key: String): Double? = optDouble(key).takeIf { !it.isNaN() }

/** The most rows a list card draws, so a card fits on the screen with its answer and nothing inside it needs scrolling; the rest are counted. */
private const val CARD_MAX_ROWS = 7

/** The control under a long list: "Show 5 more", and "Show less" once it is open. A plain tappable line, not a Material button (see UI-STANDARDS.md). */
@Composable
private fun ShowMore(more: Int, open: Boolean, onToggle: () -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            if (open) "Show less" else "Show $more more", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clip(CircleShape).clickable(onClick = onToggle).padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

private fun short(side: JSONObject): String = side.optString("short").ifBlank { side.optString("name").take(3).uppercase() }

/** The tone and word for where a person is, as the Manager's People card shows it: Home green, a named place teal, Away grey, unknown amber. */
fun personPill(state: String): Pair<String, Tone> = when (state.trim().lowercase()) {
    "home" -> "Home" to Tone.Green
    "not_home", "away", "" -> "Away" to Tone.Grey
    "unknown", "unavailable" -> "Unknown" to Tone.Amber
    else -> state.trim() to Tone.Teal
}

/** The word and tone for a heating zone, as the dashboard's climate card colours it: heating (or idle in heat mode) orange, Eco green, cooling blue, off grey. */
fun climatePill(state: String): Pair<String, Tone> = when (state.trim().lowercase()) {
    "heating", "idle", "heat" -> state.trim().replaceFirstChar { it.uppercase() } to Tone.Orange
    "eco" -> "Eco" to Tone.Green
    "cooling", "cool" -> "Cooling" to Tone.Blue
    "off", "" -> "Off" to Tone.Grey
    else -> state.trim() to Tone.Teal
}

/** A colour given as #rrggbb (a light's own colour), or null. */
private fun hexColour(v: String): Color? = v.trim().removePrefix("#").takeIf { it.length == 6 }?.toLongOrNull(16)?.let { Color(0xFF000000 or it) }

/** Every club in a card (a score, a table of scores or a fixture), whose badge the card draws. */
private fun cardSides(card: AssistantCard): List<JSONObject> {
    val f = card.fields
    return when (card.kind) {
        "score", "fixture" -> listOf(f.side("home"), f.side("away"))
        "scores", "fixtures" -> (0 until (f.optJSONArray("rows")?.length() ?: 0)).flatMap { i -> f.optJSONArray("rows")?.optJSONObject(i)?.let { listOf(it.side("home"), it.side("away")) }.orEmpty() }
        else -> emptyList()
    }
}

/** Whether a card draws pictures that have to be fetched. */
fun cardHasPictures(card: AssistantCard): Boolean = cardSides(card).isNotEmpty()

/**
 * Finds and downloads every badge in [card] before it is shown, so the card appears finished and nothing pops in afterwards. Gives up after a few
 * seconds (a slow or missing picture then falls back to the club's letters when it fails).
 */
suspend fun preloadCardImages(context: android.content.Context, card: AssistantCard) {
    kotlinx.coroutines.withTimeoutOrNull(4_000) {
        kotlinx.coroutines.coroutineScope {
            cardSides(card).map { side ->
                async(kotlinx.coroutines.Dispatchers.IO) {
                    val url = side.optString("badge_url").ifBlank { null } ?: side.optString("name").takeIf { it.isNotBlank() }?.let { TeamBadges.lookup(it) }
                    if (url != null) context.imageLoader.execute(coil.request.ImageRequest.Builder(context).data(url).build())
                }
            }.forEach { it.await() }
        }
    }
}

/** The logo of a service (Spotify, Netflix...) in one colour, or a music note when there is none for it. */
@Composable
fun ServiceLogo(name: String, tint: Color, size: Dp) {
    val context = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(Unit) { SimpleIcons.load(context) }
    val has = SimpleIcons.paths?.containsKey(SimpleIcons.slugFor(name)) == true
    HaIcon(if (has) "si:$name" else "mdi:music-note", Icons.Filled.Info, tint, size)
}

/** A club badge: its crest, with the club's three letters behind it for while it loads or when there is none. */
@Composable
private fun CardBadge(side: JSONObject, size: Dp) {
    val given = side.optString("badge_url").ifBlank { null }
    val name = side.optString("name")
    // No address from the house: find the club's badge from its name. Anything already found (the answer waits for it, see [preloadCardImages]) shows at once.
    val start = if (given != null) true to given else if (name.isBlank()) true to null else TeamBadges.peek(name)
    val found by androidx.compose.runtime.produceState(start, given, name) {
        if (!start.first) value = true to kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { TeamBadges.lookup(name) }
    }
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        // The club's crest as it is (they are transparent pictures). Its three letters show only when there is no picture to be had, never while one is on its way.
        val letters: @Composable () -> Unit = { Text(short(side), color = Color.White, fontSize = (size.value * 0.3f).sp, fontWeight = FontWeight.Bold, maxLines = 1) }
        val url = found.second
        if (found.first && url == null) letters()
        else if (url != null) coil.compose.SubcomposeAsyncImage(
            model = url, contentDescription = name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize(),
            loading = {}, error = { Box(contentAlignment = Alignment.Center) { letters() } },
        )
    }
}

/**
 * What Clive sends for an answer worth seeing (the house's `clive_show_card`): the weather, a score or fixture with the clubs' badges,
 * what is playing. Drawn under his written answer, the phone's version of the watch's card.
 */
@Composable
fun AssistantCardView(card: AssistantCard, modifier: Modifier = Modifier, morph: Float = 1f, showBody: Boolean = true) {
    val scheme = MaterialTheme.colorScheme
    val f = card.fields
    val ink = scheme.onSurface
    val soft = scheme.onSurfaceVariant
    // A long table shows its first rows and a button for the rest; it closes again on the next answer.
    var showAll by androidx.compose.runtime.saveable.rememberSaveable(card.toString()) { androidx.compose.runtime.mutableStateOf(false) }
    val limit = if (showAll) Int.MAX_VALUE else CARD_MAX_ROWS
    // No background of its own: the card sits in his speech bubble. Its icon is always in the same place at the same size, and the figures arrive under it.
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        CardIconZone(card, ink, morph)
        AnimatedVisibility(showBody, enter = fadeIn(tween(350)) + expandVertically(tween(450))) {
        Column(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 2.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                val total = rows?.length() ?: 0
                for (i in 0 until minOf(total, limit)) {
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
                if (total > CARD_MAX_ROWS) ShowMore(total - CARD_MAX_ROWS, showAll) { showAll = !showAll }
            }
            "fixtures" -> {
                if (f.optString("title").isNotBlank()) Text(f.optString("title"), style = MaterialTheme.typography.titleSmall, color = soft, modifier = Modifier.fillMaxWidth())
                val rows = f.optJSONArray("rows")
                val total = rows?.length() ?: 0
                for (i in 0 until minOf(total, limit)) {
                    val r = rows?.optJSONObject(i) ?: continue
                    val home = r.side("home")
                    val away = r.side("away")
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.width(76.dp)) {
                            Text(r.optString("when"), style = MaterialTheme.typography.labelMedium, color = soft, maxLines = 1)
                            Text(r.optString("time"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = ink, maxLines = 1)
                        }
                        Text(short(home), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = ink, textAlign = TextAlign.End, maxLines = 1)
                        Box(Modifier.padding(horizontal = 6.dp)) { CardBadge(home, 28.dp) }
                        Text("v", style = MaterialTheme.typography.labelMedium, color = soft)
                        Box(Modifier.padding(horizontal = 6.dp)) { CardBadge(away, 28.dp) }
                        Text(short(away), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = ink, maxLines = 1)
                    }
                }
                if (total > CARD_MAX_ROWS) ShowMore(total - CARD_MAX_ROWS, showAll) { showAll = !showAll }
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
            "climate" -> {
                val rows = f.optJSONArray("rows")
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (i in 0 until (rows?.length() ?: 0)) {
                        val r = rows?.optJSONObject(i) ?: continue
                        val (word, tone) = climatePill(r.optString("state"))
                        val t = toneColors(tone)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(38.dp).clip(CircleShape).background(t.accent.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
                                HaIcon("mdi:thermometer", Icons.Filled.Info, t.accent, 22.dp)
                            }
                            Column(Modifier.weight(1f)) {
                                Text(r.optString("name"), style = MaterialTheme.typography.titleSmall, color = ink, maxLines = 1)
                                val sub = listOfNotNull(r.number("target")?.let { "Target ${"%.1f".format(it)}°" }, r.number("humidity")?.let { "${Math.round(it)}% humidity" })
                                if (sub.isNotEmpty()) Text(sub.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = soft, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text(r.number("temp")?.let { "${"%.1f".format(it)}°" } ?: "–", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = ink)
                            Text(word, color = t.accent, style = MaterialTheme.typography.labelMedium, maxLines = 1, modifier = Modifier.background(t.accent.copy(alpha = 0.18f), CircleShape).padding(horizontal = 10.dp, vertical = 3.dp))
                        }
                    }
                }
                val foot = listOfNotNull(f.number("outside")?.let { "Outside ${"%.1f".format(it)}°" }, f.optString("quality").takeIf { it.isNotBlank() })
                if (foot.isNotEmpty()) Text(foot.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = soft, modifier = Modifier.fillMaxWidth())
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
                if (place.isNotBlank()) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)) {
                    HaIcon("mdi:map-marker", Icons.Filled.Info, soft, 14.dp)
                    Text(place, style = MaterialTheme.typography.labelMedium, color = soft)
                }
                Text(f.number("temp")?.let { "${Math.round(it)}°" } ?: "–", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = ink)
                if (f.optString("desc").isNotBlank()) Text(f.optString("desc"), style = MaterialTheme.typography.titleSmall, color = ink)
                val side = listOfNotNull(f.number("high")?.let { "High ${Math.round(it)}°" }, f.number("low")?.let { "Low ${Math.round(it)}°" }, f.number("rain")?.let { "${Math.round(it)}% rain" })
                if (side.isNotEmpty()) Text(side.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = soft)
                val hours = f.optJSONArray("hourly")
                if (hours != null && hours.length() > 0) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(1.dp).background(Color.White.copy(alpha = 0.10f)))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        for (i in 0 until minOf(hours.length(), 6)) {
                            val h = hours.optJSONObject(i) ?: continue
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(h.optString("t"), style = MaterialTheme.typography.labelSmall, color = soft)
                                HaIcon(weatherIconName(h.optString("symbol")), Icons.Filled.Info, ink, 20.dp)
                                Text(h.number("temp")?.let { "${Math.round(it)}°" } ?: "–", style = MaterialTheme.typography.labelLarge, color = ink)
                            }
                        }
                    }
                }
            }
            "gregg" -> {
                val level = f.number("battery")
                val state = greggStateFor(f.optString("state"), level)
                val tone = toneColors(if (state == GreggState.Error) Tone.Red else Tone.Blue)
                Text(f.optString("name").ifBlank { "Gregg" }, style = MaterialTheme.typography.titleMedium, color = ink)
                Text(state.word(), color = tone.accent, style = MaterialTheme.typography.labelMedium, maxLines = 1, modifier = Modifier.background(tone.accent.copy(alpha = 0.18f), CircleShape).padding(horizontal = 10.dp, vertical = 3.dp))
                val line = listOfNotNull(level?.let { "Battery ${Math.round(it)}%" }, f.number("area")?.let { "${Math.round(it)} m² last clean" }, f.number("minutes")?.let { "${Math.round(it)} min" })
                if (line.isNotEmpty()) Text(line.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = soft, textAlign = TextAlign.Center)
                if (f.optString("detail").isNotBlank()) Text(f.optString("detail"), style = MaterialTheme.typography.bodySmall, color = soft, textAlign = TextAlign.Center)
            }
            "air" -> {
                val score = f.number("score")
                val tone = toneColors(qualityTone(score?.toInt()))
                Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        val w = 10.dp.toPx()
                        val inset = Offset(w / 2, w / 2)
                        val s = Size(size.width - w, size.height - w)
                        drawArc(Color.White.copy(alpha = 0.12f), 0f, 360f, false, inset, s, style = Stroke(w))
                        drawArc(tone.accent, -90f, 360f * ((score ?: 0.0) / 100.0).coerceIn(0.0, 1.0).toFloat(), false, inset, s, style = Stroke(w, cap = StrokeCap.Round))
                    }
                    Text(score?.let { "${Math.round(it)}" } ?: "–", color = tone.accent, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                }
                Text(f.optString("word").ifBlank { "Air quality" }, style = MaterialTheme.typography.titleMedium, color = ink)
                val line = listOfNotNull(f.number("pm25")?.let { "PM2.5 ${"%.0f".format(it)} µg/m³" }, f.number("humidity")?.let { "${Math.round(it)}% humidity" })
                if (line.isNotEmpty()) Text(line.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = soft, textAlign = TextAlign.Center)
                val kit = listOf(f.optString("purifier").takeIf { it.isNotBlank() }?.let { "Purifier $it" }, f.optString("fan").takeIf { it.isNotBlank() }?.let { "Fan $it" }).filterNotNull()
                if (kit.isNotEmpty()) Text(kit.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = soft, textAlign = TextAlign.Center)
                if (f.optString("place").isNotBlank()) Text(f.optString("place"), style = MaterialTheme.typography.labelSmall, color = soft)
            }
            "music" -> {
                Text(f.optString("title"), style = MaterialTheme.typography.titleMedium, color = ink, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (f.optString("artist").isNotBlank()) Text(f.optString("artist"), style = MaterialTheme.typography.bodyMedium, color = soft)
                if (f.optString("device").isNotBlank()) Text("Playing on ${f.optString("device")}", style = MaterialTheme.typography.bodySmall, color = soft)
            }
            else -> ListCardBody(card, ink, soft)
        }
        }
        }
    }
}

/** Where a card's icon sits: the same box at the top of every card, so the loading mark and the icon it becomes never move or change size. */
val CARD_ICON_ZONE = 88.dp
val CARD_ICON_SIZE = 72.dp

/** The card's icon, or its logo (a music service, a car's make, a competition) when the app has one. */
@Composable
fun CardIcon(card: AssistantCard, tint: Color, size: Dp) {
    val context = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(Unit) { SimpleIcons.load(context); OwnLogos.load(context) }
    if (card.kind == "gregg") {
        val level = card.fields.optDouble("battery").takeIf { !it.isNaN() }
        GreggIcon(greggStateFor(card.fields.optString("state"), level), tint, size, level)
        return
    }
    val brand = cardBrand(card)
    val key = brand?.let { SimpleIcons.slugFor(it) }
    // Simple Icons first, then our own logos (the ones it does not have), else the card's own icon.
    val icon = when {
        key != null && SimpleIcons.paths?.containsKey(key) == true -> "si:$key"
        key != null && OwnLogos.paths?.containsKey(key) == true -> "ol:$key"
        else -> cardIcon(card)
    }
    HaIcon(icon, Icons.Filled.Info, tint, size)
}

/**
 * The top of every card: Clive's thinking mark turning (by [morph], 0 to 1) into the card's own icon, in the middle of a box that is always the same size.
 */
@Composable
fun CardIconZone(card: AssistantCard, tint: Color, morph: Float = 1f) {
    Box(Modifier.fillMaxWidth().height(CARD_ICON_ZONE), contentAlignment = Alignment.Center) {
        if (morph < 1f) Box(Modifier.graphicsLayer { alpha = 1f - morph; val k = 1f - 0.35f * morph; scaleX = k; scaleY = k; rotationZ = 25f * morph }) {
            AssistantGlyph(AssistantGlyphPose.Thinking, tint, CARD_ICON_SIZE)
        }
        Box(Modifier.graphicsLayer { alpha = morph; val k = 0.65f + 0.35f * morph; scaleX = k; scaleY = k }) { CardIcon(card, tint, CARD_ICON_SIZE) }
    }
}

/** A tone by its lower-case name ("amber"), or null. */
private fun toneByName(name: String): Tone? = Tone.values().firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }

/** The layout the list-style cards share: a big figure, a title, a line under it, a pill, rows with markers, and a footer. */
@Composable
private fun ListCardBody(card: AssistantCard, ink: Color, soft: Color) {
    val f = card.fields
    val base = toneColors(toneByName(f.optString("tone")) ?: Tone.Indigo)
    if (f.optString("big").isNotBlank()) Text(f.optString("big"), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = ink, textAlign = TextAlign.Center)
    if (f.optString("title").isNotBlank()) Text(f.optString("title"), style = MaterialTheme.typography.titleMedium, color = ink, textAlign = TextAlign.Center)
    if (f.optString("sub").isNotBlank()) Text(f.optString("sub"), style = MaterialTheme.typography.bodySmall, color = soft, textAlign = TextAlign.Center)
    if (f.optString("pill").isNotBlank()) {
        val t = toneColors(toneByName(f.optString("pill_tone")) ?: Tone.Indigo)
        Text(f.optString("pill"), color = t.accent, style = MaterialTheme.typography.labelMedium, maxLines = 1, modifier = Modifier.background(t.accent.copy(alpha = 0.18f), CircleShape).padding(horizontal = 10.dp, vertical = 3.dp))
    }
    val allRows = cardRows(f)
    var showAll by androidx.compose.runtime.saveable.rememberSaveable(card.toString()) { androidx.compose.runtime.mutableStateOf(false) }
    val rows = allRows.take(if (showAll) Int.MAX_VALUE else CARD_MAX_ROWS)
    if (rows.isNotEmpty()) Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        for (r in rows) {
            val t = toneColors(toneByName(r.tone) ?: Tone.Grey)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                when (r.marker) {
                    "ok" -> HaIcon("mdi:check-circle", Icons.Filled.Info, toneColors(Tone.Green).accent, 20.dp)
                    "warn" -> HaIcon("mdi:alert-circle", Icons.Filled.Info, toneColors(Tone.Amber).accent, 20.dp)
                    "todo" -> HaIcon("mdi:checkbox-blank-outline", Icons.Filled.Info, soft, 20.dp)
                    "done" -> HaIcon("mdi:checkbox-marked", Icons.Filled.Info, toneColors(Tone.Green).accent, 20.dp)
                    "dot" -> Box(Modifier.size(10.dp).clip(CircleShape).background(t.accent))
                }
                Column(Modifier.weight(1f)) {
                    Text(r.name, style = MaterialTheme.typography.titleSmall, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (r.detail.isNotBlank()) Text(r.detail, style = MaterialTheme.typography.bodySmall, color = soft, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (r.value.isNotBlank()) Text(r.value, style = MaterialTheme.typography.labelLarge, color = soft, maxLines = 1)
                if (r.pill.isNotBlank()) Text(r.pill, color = t.accent, style = MaterialTheme.typography.labelMedium, maxLines = 1, modifier = Modifier.background(t.accent.copy(alpha = 0.18f), CircleShape).padding(horizontal = 10.dp, vertical = 3.dp))
            }
        }
    }
    if (allRows.size > CARD_MAX_ROWS) ShowMore(allRows.size - CARD_MAX_ROWS, showAll) { showAll = !showAll }
    f.number("percent")?.let { p ->
        Box(Modifier.fillMaxWidth().padding(top = 4.dp).height(6.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f))) {
            Box(Modifier.fillMaxWidth((p / 100.0).coerceIn(0.0, 1.0).toFloat()).height(6.dp).clip(CircleShape).background(base.accent))
        }
    }
    if (f.optString("foot").isNotBlank()) Text(f.optString("foot"), style = MaterialTheme.typography.bodySmall, color = soft, modifier = Modifier.fillMaxWidth().padding(top = 2.dp))
}
