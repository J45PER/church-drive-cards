package com.churchdrive.app.ui

import androidx.compose.runtime.compositionLocalOf
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/** A scene's look chosen on the dashboards (the Scene Styles card on the Design Presets dashboard). */
data class SceneStyle(val colours: List<Int>, val icon: String?)

/** A scene in Church Drive's library: its colours (colour scenes), or colour temperature or colour (white scenes). */
data class LibraryScene(
    val key: String,
    val name: String,
    val hex: List<Int>,
    val kelvin: Int?,
    val xy: Pair<Double, Double>?,
    val icon: String?,
    val dynamic: Boolean,
)

/**
 * Where a scene tile gets its colours and icon, in the order the dashboard cards use: the look set in the
 * Scene Styles card, then the scene's own colours from the library. So a new scene, including one built in
 * the Scene Builder, looks right without the app being updated. Colours are ARGB ints.
 */
class SceneLooks(val library: Map<String, LibraryScene>, val styles: Map<String, SceneStyle>) {
    private fun lookup(key: String, name: String): LibraryScene? =
        library[key] ?: library.values.firstOrNull { sceneKey(it.name) == sceneKey(name) }

    /** A style's or the library's own colours for the scene, or null when neither has any. */
    fun colours(key: String, name: String): List<Int>? {
        styles[sceneKey(name)]?.colours?.takeIf { it.isNotEmpty() }?.let { return pair(it) }
        val scene = lookup(key, name) ?: return null
        if (scene.hex.isNotEmpty()) return pair(scene.hex)
        return null
    }

    /** A gradient for a white scene, from its colour temperature or colour: a lighter and a deeper shade. */
    fun whiteColours(key: String, name: String): List<Int>? {
        val scene = lookup(key, name) ?: return null
        val base = scene.kelvin?.let { kelvinToRgb(it) } ?: scene.xy?.let { xyToRgb(it.first, it.second) } ?: return null
        return listOf(shade(base, 1.0), shade(base, 0.55))
    }

    fun icon(key: String, name: String): String? = styles[sceneKey(name)]?.icon ?: lookup(key, name)?.icon

    companion object {
        val Empty = SceneLooks(emptyMap(), emptyMap())

        /** "Natural light 2" -> "natural light": how the cards match a style to a scene. */
        fun sceneKey(name: String) = name.lowercase().replace(Regex("\\s+\\d+$"), "").trim()

        fun parse(libraryResult: Any?, dashboard: JSONObject?): SceneLooks {
            val library = (libraryResult as? JSONObject)?.optJSONArray("scenes")?.let { a ->
                (0 until a.length()).mapNotNull { a.optJSONObject(it) }.mapNotNull { libraryScene(it) }
            }.orEmpty().associateBy { it.key }
            return SceneLooks(library, styles(dashboard))
        }

        private fun libraryScene(o: JSONObject): LibraryScene? {
            val key = o.optString("key").takeIf { it.isNotBlank() } ?: return null
            val hex = o.optJSONArray("hex")?.let { a -> (0 until a.length()).mapNotNull { parseColour(a.opt(it)) } }.orEmpty()
            val xy = o.optJSONArray("xy_color")?.takeIf { it.length() == 2 }?.let { it.optDouble(0) to it.optDouble(1) }
            return LibraryScene(
                key, o.optString("name", key), hex,
                kelvin = if (o.has("color_temp_kelvin")) o.optInt("color_temp_kelvin") else null,
                xy = xy,
                icon = o.optString("icon").takeIf { it.isNotBlank() },
                dynamic = o.optBoolean("dynamic", false),
            )
        }

        private fun styles(dashboard: JSONObject?): Map<String, SceneStyle> {
            val out = mutableMapOf<String, SceneStyle>()
            fun walk(node: Any?) {
                when (node) {
                    is JSONArray -> for (i in 0 until node.length()) walk(node.opt(i))
                    is JSONObject -> {
                        if (Regex("custom:scene-styles-card(-beta)?").matches(node.optString("type"))) {
                            val list = node.optJSONArray("styles")
                            for (i in 0 until (list?.length() ?: 0)) {
                                val s = list?.optJSONObject(i) ?: continue
                                val name = s.optString("scene").takeIf { it.isNotBlank() } ?: continue
                                val colours = listOf("colour_1", "colour_2", "colour_3").mapNotNull { parseColour(s.opt(it)) }
                                val icon = s.optString("icon").takeIf { it.isNotBlank() }
                                // The released card wins over a beta one, as in the cards.
                                if (colours.isNotEmpty() || icon != null) out.putIfAbsent(sceneKey(name), SceneStyle(colours, icon))
                            }
                        } else node.keys().forEach { walk(node.opt(it)) }
                    }
                }
            }
            walk(dashboard)
            return out
        }

        /** "#rrggbb", "#rgb" or [r, g, b] to an ARGB int. */
        fun parseColour(v: Any?): Int? = when (v) {
            is String -> {
                val h = v.trim().removePrefix("#")
                val full = if (h.length == 3) h.map { "$it$it" }.joinToString("") else h
                if (full.length == 6) full.toIntOrNull(16)?.let { 0xFF000000.toInt() or it } else null
            }
            is JSONArray -> if (v.length() >= 3) argb(v.optInt(0), v.optInt(1), v.optInt(2)) else null
            else -> null
        }

        private fun pair(c: List<Int>) = if (c.size == 1) listOf(c[0], c[0]) else c

        private fun argb(r: Int, g: Int, b: Int) =
            (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

        private fun shade(c: Int, by: Double): Int = argb(
            ((c shr 16 and 0xFF) * by).roundToInt(), ((c shr 8 and 0xFF) * by).roundToInt(), ((c and 0xFF) * by).roundToInt(),
        )

        /** A colour temperature as an sRGB colour (Tanner Helland's approximation). */
        fun kelvinToRgb(kelvin: Int): Int {
            val t = kelvin.coerceIn(1000, 40000) / 100.0
            val r = if (t <= 66) 255.0 else 329.698727446 * (t - 60).pow(-0.1332047592)
            val g = if (t <= 66) 99.4708025861 * ln(t) - 161.1195681661 else 288.1221695283 * (t - 60).pow(-0.0755148492)
            val b = if (t >= 66) 255.0 else if (t <= 19) 0.0 else 138.5177312231 * ln(t - 10) - 305.0447927307
            return argb(r.roundToInt(), g.roundToInt(), b.roundToInt())
        }

        /** A CIE xy colour as an sRGB colour at full brightness: the same sums as the integration's xy_to_hex. */
        fun xyToRgb(x: Double, yIn: Double): Int {
            val y = max(yIn, 1e-6)
            val bigX = x / y
            val bigZ = (1 - x - y) / y
            val r = bigX * 1.656492 - 0.354851 - bigZ * 0.255038
            val g = -bigX * 0.707196 + 1.655397 + bigZ * 0.036152
            val b = bigX * 0.051713 - 0.121364 + bigZ * 1.011530
            val top = max(max(r, g), max(b, 1e-6))
            fun gamma(v: Double): Int {
                val c = max(v / top, 0.0)
                val s = if (c <= 0.0031308) 12.92 * c else 1.055 * c.pow(1 / 2.4) - 0.055
                return (min(s, 1.0) * 255).roundToInt()
            }
            return argb(gamma(r), gamma(g), gamma(b))
        }
    }
}

/** The scene looks for the whole app; set once in MainActivity. */
val LocalSceneLooks = compositionLocalOf { SceneLooks.Empty }
