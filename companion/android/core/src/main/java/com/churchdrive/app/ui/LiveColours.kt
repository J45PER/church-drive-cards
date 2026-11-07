package com.churchdrive.app.ui

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.Color
import org.json.JSONArray
import org.json.JSONObject

/**
 * The palettes as Home Assistant has them now. The colours built into the app ([CliveColours]) are the starting point; any palette changed
 * in Home Assistant replaces its built-in one the moment the house says so, without an app update. The last answer is kept on the phone
 * so the colours are right before the house has answered, and when it cannot be reached.
 *
 * Home Assistant's `church_drive/colours` answers `{palettes: {key: {name, colours: [five #rrggbb]}}}`; [apply] takes that `palettes` object.
 * Only the palettes the app knows by name are used (a palette added in Home Assistant shows on the dashboards, and in the app after a release).
 */
object LiveColours {
    private const val PREFS = "church_drive_live_colours"
    private const val KEY = "palettes"

    @Volatile
    private var overrides: Map<String, EntityColours> = emptyMap()

    @Volatile
    private var saved = ""

    @Volatile
    private var appContext: Context? = null

    /** Read while drawing, so what is on screen redraws when the colours change. */
    val version = mutableIntStateOf(0)

    /** The built-in palette, or the changed one from Home Assistant. */
    fun resolve(set: EntityColours): EntityColours {
        version.intValue
        val key = CliveColours.keyOf[set] ?: return set
        return overrides[key] ?: set
    }

    /** The changed palettes in [palettes] (those that differ from the built-in ones, with five valid colours), by key. */
    fun parse(palettes: JSONObject?): Map<String, EntityColours> {
        if (palettes == null) return emptyMap()
        val out = LinkedHashMap<String, EntityColours>()
        for (key in palettes.keys()) {
            val builtin = CliveColours.byKey[key] ?: continue
            val list = palettes.optJSONObject(key)?.optJSONArray("colours") ?: continue
            val c = colours(list) ?: continue
            val b = builtin
            if (c[0] == b.colour && c[1] == b.dark && c[2] == b.accent && c[3] == b.light && c[4] == b.deep) continue
            out[key] = EntityColours(c[0], c[1], c[2], c[3], c[4])
        }
        return out
    }

    private fun colours(list: JSONArray): List<Color>? {
        if (list.length() != 5) return null
        return (0 until 5).map { i ->
            val h = list.optString(i)
            if (!Regex("^#[0-9A-Fa-f]{6}$").matches(h)) return null
            Color(0xFF000000L or h.substring(1).toLong(16))
        }
    }

    /** Loads the last palettes Home Assistant sent (once), so the app starts with them. */
    fun load(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        val text = appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.getString(KEY, null) ?: return
        val next = runCatching { parse(JSONObject(text)) }.getOrDefault(emptyMap())
        if (next.isNotEmpty()) {
            overrides = next
            saved = text
            version.intValue++
        }
    }

    /** New palettes from Home Assistant: used at once and kept for next time. */
    fun apply(palettes: JSONObject) {
        val text = palettes.toString()
        if (text == saved) return
        val next = parse(palettes)
        saved = text
        overrides = next
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.putString(KEY, text)?.apply()
        version.intValue++
    }
}
