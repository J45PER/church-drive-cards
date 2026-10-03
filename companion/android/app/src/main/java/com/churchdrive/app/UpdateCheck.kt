package com.churchdrive.app

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/** Asks GitHub whether a newer test build than the one running has been published. */
object UpdateCheck {
    const val RELEASE_API = "https://api.github.com/repos/J45PER/church-drive-cards/releases/tags/companion-android-latest"
    const val APK_URL = "https://github.com/J45PER/church-drive-cards/releases/download/companion-android-latest/church-drive.apk"

    /** The commit a release was built from, from its notes ("... (commit abc1234). Download ..."). */
    fun commitOf(body: String?): String? = body?.let { Regex("commit ([0-9a-f]{7,40})").find(it)?.groupValues?.get(1) }

    /** Whether the release was built from a different commit than [running]; false when either is unknown. */
    fun isNewer(releaseBody: String?, running: String): Boolean {
        val latest = commitOf(releaseBody) ?: return false
        if (running.isBlank() || running == "dev") return false
        return !latest.startsWith(running) && !running.startsWith(latest)
    }

    /** True when an update is available; false on any problem (no network, rate limit). Call off the main thread. */
    fun check(http: OkHttpClient, running: String): Boolean = runCatching {
        http.newCall(Request.Builder().url(RELEASE_API).header("Accept", "application/vnd.github+json").build()).execute().use { r ->
            r.isSuccessful && isNewer(JSONObject(r.body?.string().orEmpty()).optString("body"), running)
        }
    }.getOrDefault(false)
}
