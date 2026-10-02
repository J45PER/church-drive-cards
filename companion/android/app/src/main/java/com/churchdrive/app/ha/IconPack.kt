package com.churchdrive.app.ha

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * The `phu:` icons (the Hue-style set from the Custom Brand Icons HACS pack that Home Assistant
 * already serves). The pack is one JS file of icon names and SVG path data; it is fetched once
 * from the user's own Home Assistant and cached, so nothing is bundled and the app always shows
 * the same icon the dashboards do.
 */
object IconPack {
    private const val FILE = "custom-brand-icons.js"
    private const val PATH = "/hacsfiles/custom-brand-icons/custom-brand-icons.js"
    private const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000

    /** The whole pack once loaded; read by icons so they redraw when it arrives. */
    var text: String? by mutableStateOf(null)
        private set

    private val http = OkHttpClient()

    fun load(scope: CoroutineScope, dir: File, baseUrl: String, token: String) {
        if (text != null) return
        scope.launch(Dispatchers.IO) {
            val cached = File(dir, FILE)
            if (cached.exists()) text = runCatching { cached.readText() }.getOrNull()
            val stale = !cached.exists() || System.currentTimeMillis() - cached.lastModified() > MAX_AGE_MS
            if (stale) {
                val fresh = runCatching { download(baseUrl, token) }.getOrNull()
                if (fresh != null && fresh.contains("var icons")) {
                    runCatching { cached.writeText(fresh) }
                    withContext(Dispatchers.Main) { text = fresh }
                }
            }
        }
    }

    private fun download(baseUrl: String, token: String): String? {
        val request = Request.Builder()
            .url(baseUrl.trim().trimEnd('/') + PATH)
            .header("Authorization", "Bearer $token")
            .build()
        http.newCall(request).execute().use { r -> return if (r.isSuccessful) r.body?.string() else null }
    }

    /** An icon's viewBox size and SVG path data, or null when it isn't in the pack. */
    fun find(name: String): Pair<Float, String>? {
        val t = text ?: return null
        return parse(t, name)
    }

    /** `"name":[0,0,24,24,"path…"]`: pulled out of the pack text without parsing all of it. */
    fun parse(pack: String, name: String): Pair<Float, String>? {
        val key = "\"$name\":["
        val start = pack.indexOf(key)
        if (start < 0) return null
        val numbersStart = start + key.length
        val quote = pack.indexOf('"', numbersStart)
        if (quote < 0) return null
        val box = pack.substring(numbersStart, quote).trim().trimEnd(',').split(',').mapNotNull { it.trim().toFloatOrNull() }
        val end = pack.indexOf("\"]", quote + 1)
        if (end < 0) return null
        return (box.getOrNull(2) ?: 24f) to pack.substring(quote + 1, end)
    }
}
