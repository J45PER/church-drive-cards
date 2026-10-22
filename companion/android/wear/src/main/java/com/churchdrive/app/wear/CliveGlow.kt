package com.churchdrive.app.wear

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text
import com.churchdrive.app.ui.AssistState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.cos
import kotlin.math.sin

/** Three colours for the three blobs of Clive's glow. */
class GlowPalette(val a: Color, val b: Color, val c: Color) {
    companion object {
        /** Clive's own: indigo, purple and green. */
        val Base = GlowPalette(Color(0xFF5C6BC0), Color(0xFFAB47BC), Color(0xFF4FD08A))
        val Clear = GlowPalette(Color(0xFF1565C0), Color(0xFF42A5F5), Color(0xFF81D4FA))
        val Rain = GlowPalette(Color(0xFF37474F), Color(0xFF5C7C99), Color(0xFF78909C))
        val Night = GlowPalette(Color(0xFF0D1B4A), Color(0xFF283593), Color(0xFF5C6BC0))
        val Snow = GlowPalette(Color(0xFF90A4AE), Color(0xFFB3E5FC), Color(0xFFE1F5FE))
        val Storm = GlowPalette(Color(0xFF37474F), Color(0xFF6A1B9A), Color(0xFF5C6BC0))
        val Sport = GlowPalette(Color(0xFF6CABDD), Color(0xFF1C2C5B), Color(0xFFA5CDEB))

        /** Up to three "#rrggbb" colours from a card's `palette`, or null. */
        fun from(hex: List<String>): GlowPalette? {
            val colours = hex.mapNotNull { parseHex(it) }
            if (colours.isEmpty()) return null
            return GlowPalette(colours[0], colours.getOrElse(1) { colours[0] }, colours.getOrElse(2) { colours[0] })
        }

        private fun parseHex(h: String): Color? {
            val s = h.trim().removePrefix("#")
            if (s.length != 6) return null
            return s.toLongOrNull(16)?.let { Color(0xFF000000 or it) }
        }
    }
}

/** How bright and how fast the glow is for what Clive is doing. */
private fun intensity(state: AssistState): Pair<Float, Float> = when (state) {
    AssistState.Listening -> 1f to 1.6f
    AssistState.Thinking -> 0.92f to 4.2f
    AssistState.Speaking -> 0.95f to 2.4f
    else -> 0.72f to 0.6f
}

/**
 * Clive's avatar glow filling the whole glass: three colour blobs turning round the middle, brighter and quicker as he listens, thinks or
 * speaks. [dim] lowers it (when a face other than Talk is showing); [palette] gives it an answer's colours.
 */
@Composable
fun CliveGlow(state: AssistState, level: Float, palette: GlowPalette, dim: Boolean, modifier: Modifier = Modifier) {
    val (bright, speed) = intensity(state)
    val speedFollow = animateFloatAsState(speed, tween(600), label = "speed")
    val alpha by animateFloatAsState(if (dim) 0.5f else bright, tween(500), label = "alpha")
    val ca by animateColorAsState(palette.a, tween(1400), label = "a")
    val cb by animateColorAsState(palette.b, tween(1400), label = "b")
    val cc by animateColorAsState(palette.c, tween(1400), label = "c")
    // The turning and the pulse are read only while drawing, so a frame redraws the glow without recomposing it.
    val phase = remember { mutableFloatStateOf(0f) }
    val pulse = remember { mutableFloatStateOf(0f) }
    val levelNow = rememberUpdatedState(level)
    val dimNow = rememberUpdatedState(dim)
    val stateNow = rememberUpdatedState(state)
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            androidx.compose.runtime.withFrameNanos { now ->
                if (last != 0L) {
                    val dt = ((now - last) / 1_000_000_000f).coerceAtMost(0.1f)
                    phase.floatValue += dt * 360f / 16f * speedFollow.value
                    pulse.floatValue += dt
                }
                last = now
            }
        }
    }
    Canvas(modifier.fillMaxSize().background(Color(0xFF090A14))) {
        val w = size.minDimension
        val c = Offset(size.width / 2f, size.height / 2f)
        val extra = when (stateNow.value) {
            AssistState.Listening -> (levelNow.value - 0.25f) * 0.25f
            AssistState.Speaking -> sin(pulse.floatValue * 9f) * 0.07f
            else -> 0f
        }
        val a = (alpha + (if (dimNow.value) 0f else extra)).coerceIn(0.1f, 1f)
        val ph = phase.floatValue
        fun blob(color: Color, angle: Float, radiusFactor: Float) {
            val rad = Math.toRadians(angle.toDouble())
            val centre = Offset(c.x + (w * radiusFactor * cos(rad)).toFloat(), c.y + (w * radiusFactor * sin(rad)).toFloat())
            drawCircle(Brush.radialGradient(listOf(color.copy(alpha = a), Color.Transparent), centre, w * 0.62f), w * 0.62f, centre)
        }
        blob(ca, ph + 210f, 0.28f)
        blob(cb, -ph * 1.3f + 330f, 0.28f)
        blob(cc, ph * 0.8f + 90f, 0.3f)
        // Darker towards the edge so the bezel and the text stay clear.
        drawCircle(Brush.radialGradient(0.45f to Color.Transparent, 1f to Color(0xB305060E), center = c, radius = w * 0.55f), w * 0.55f, c)
    }
}

// ---------------------------------------------------------------------------------------------------- pictures

private val cache = object : LruCache<String, Bitmap>(24) {}

/** A picture from an https address (a club badge, a picture the house found), or null while it loads or if it can't be had. */
@Composable
fun rememberRemoteImage(url: String?, maxPx: Int = 600): State<Bitmap?> {
    val state = remember(url) { mutableStateOf(url?.let { cache.get(it) }) }
    LaunchedEffect(url) {
        if (url == null || state.value != null || !url.startsWith("https://")) return@LaunchedEffect
        state.value = withContext(Dispatchers.IO) { download(url, maxPx) }?.also { cache.put(url, it) }
    }
    return state
}

private fun download(url: String, maxPx: Int): Bitmap? = runCatching {
    val c = URL(url).openConnection() as HttpURLConnection
    c.connectTimeout = 8_000
    c.readTimeout = 8_000
    c.instanceFollowRedirects = true
    c.setRequestProperty("User-Agent", "ChurchDriveWatch")
    try {
        if (c.responseCode !in 200..299) return@runCatching null
        // At most 2 MB, so a bad address can't fill the watch's memory.
        val bytes = c.inputStream.use { stream ->
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(8192)
            var total = 0
            while (true) {
                val n = stream.read(buf)
                if (n <= 0) break
                total += n
                if (total > 2_000_000) return@runCatching null
                out.write(buf, 0, n)
            }
            out.toByteArray()
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / sample > maxPx * 2 || bounds.outHeight / sample > maxPx * 2) sample *= 2
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    } finally {
        c.disconnect()
    }
}.getOrNull()

/** A club badge: its picture, or its three letters on a white disc while it loads or when it has none. */
@Composable
fun Badge(url: String?, initials: String, size: Dp, dim: Boolean = false) {
    val image by rememberRemoteImage(url, 160)
    val bitmap = image
    Box(Modifier.size(size).clip(CircleShape).background(Color.White.copy(alpha = if (dim) 0.2f else 0.94f)), contentAlignment = Alignment.Center) {
        if (bitmap != null) Image(bitmap.asImageBitmap(), initials, Modifier.fillMaxSize().clip(CircleShape).background(Color.White), contentScale = ContentScale.Fit)
        else Text(initials, color = if (dim) Color.White else Color(0xFF1C2C5B), fontSize = (size.value * 0.32f).sp, fontWeight = FontWeight.Bold)
    }
}
