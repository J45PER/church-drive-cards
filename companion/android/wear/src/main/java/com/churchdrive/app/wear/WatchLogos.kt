package com.churchdrive.app.wear

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalContext
import org.json.JSONObject
import kotlin.concurrent.thread

/**
 * The competition logos a card shows in place of its symbol on the watch (Premier League, the EFL, the cups and the European competitions),
 * one colour on a 24 x 24 grid, kept in assets/competition-logos.json: the phone app's own set trimmed to the competitions. Drawn with the
 * even-odd rule so holes stay holes. For our own use only; the logos belong to their owners.
 */
object WatchLogos {
    private val loaded = mutableStateOf<Map<String, String>?>(null)
    @Volatile private var started = false

    /** Every logo's path by name, or null until the set has been read. */
    val paths: Map<String, String>? get() = loaded.value

    fun load(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        thread(name = "watch-logos", isDaemon = true) {
            runCatching {
                val json = app.assets.open("competition-logos.json").bufferedReader().use { JSONObject(it.readText()) }
                loaded.value = json.keys().asSequence().associateWith { json.getString(it) }
            }
        }
    }
}

/** Whether the watch has a logo of this name (once the set has loaded). */
fun hasCompetitionLogo(name: String?): Boolean = name != null && WatchLogos.paths?.containsKey(name) == true

/** One competition logo in [tint] at [size]. */
@Composable
fun CompetitionLogo(name: String, tint: Color, size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { WatchLogos.load(context) }
    val d = WatchLogos.paths?.get(name)
    val path = remember(d) { d?.let { runCatching { PathParser().parsePathString(it).toPath().apply { fillType = PathFillType.EvenOdd } }.getOrNull() } }
    if (path != null) Canvas(modifier.size(size)) {
        val s = this.size.minDimension / 24f
        scale(s, s, pivot = Offset.Zero) { drawPath(path, color = tint) }
    }
}
