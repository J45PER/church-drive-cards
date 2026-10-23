package com.churchdrive.app.ui

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import org.json.JSONObject
import kotlin.concurrent.thread

/**
 * Logos that Simple Icons does not have (the competitions, Disney+, Cupra, Octopus Energy), flattened to one colour from the originals and kept in
 * assets/own-logos.json on a 24 x 24 grid, named the way [SimpleIcons.slugFor] names things ("Champions League" is championsleague). They are drawn
 * with the even-odd rule, so holes stay holes. For our own use only; the logos belong to their owners.
 */
object OwnLogos {
    private val loaded = mutableStateOf<Map<String, String>?>(null)
    @Volatile private var started = false

    /** Every logo's path by name, or null until the set has been read. Reading it from a composable redraws when it arrives. */
    val paths: Map<String, String>? get() = loaded.value

    fun load(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        thread(name = "own-logos", isDaemon = true) {
            runCatching {
                val json = app.assets.open("own-logos.json").bufferedReader().use { JSONObject(it.readText()) }
                val map = HashMap<String, String>(json.length() * 2)
                for (name in json.keys()) map[name] = json.getString(name)
                loaded.value = map
            }
        }
    }
}
