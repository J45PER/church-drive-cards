package com.churchdrive.app

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/** Asks GitHub whether a newer test build than the one running has been published. */
object UpdateCheck {
    const val RELEASE_API = "https://api.github.com/repos/J45PER/church-drive-cards/releases/tags/companion-android-latest"
    const val APK_URL = "https://github.com/J45PER/church-drive-cards/releases/download/companion-android-latest/church-drive.apk"

    /** Church Drive To-Do has its own releases. */
    const val TODO_RELEASE_API = "https://api.github.com/repos/J45PER/church-drive-cards/releases/tags/todo-latest"
    const val TODO_APK_URL = "https://github.com/J45PER/church-drive-cards/releases/download/todo-latest/church-drive-todo.apk"

    /** The CI build number a release was made from, from its notes ("..., build 42, from the ..."). */
    fun buildOf(body: String?): Int? = body?.let { Regex("build (\\d+)").find(it)?.groupValues?.get(1)?.toIntOrNull() }

    /**
     * Whether the release is a later build than [running] (this app's build number). Builds count up with every CI run, so a
     * different build of the same version, or a preview build that is ahead of the release, is not offered as an update.
     * False when either build is unknown (a local build has number 0).
     */
    fun isNewer(releaseBody: String?, running: Int): Boolean {
        val latest = buildOf(releaseBody) ?: return false
        if (running <= 0) return false
        return latest > running
    }

    /** Not more than one check every ten minutes: GitHub allows an app that isn't signed in only 60 questions an hour. */
    const val MIN_GAP_MS = 10 * 60_000L

    /** Whether it is time to ask again: never asked yet, or the last time was at least [gap] ago. */
    fun due(now: Long, last: Long, gap: Long = MIN_GAP_MS): Boolean = last == 0L || now - last >= gap

    /** True when an update is available; false on any problem (no network, rate limit). Call off the main thread. */
    fun check(http: OkHttpClient, running: Int, api: String = RELEASE_API): Boolean = runCatching {
        http.newCall(Request.Builder().url(api).header("Accept", "application/vnd.github+json").build()).execute().use { r ->
            r.isSuccessful && isNewer(JSONObject(r.body?.string().orEmpty()).optString("body"), running)
        }
    }.getOrDefault(false)
}
