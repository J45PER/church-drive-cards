package com.churchdrive.app.ui

import android.content.Context
import com.churchdrive.app.BuildConfig

/**
 * Which "all is well" lines of a page header have already been shown since the app last updated. A good line is news once
 * per update; after that it stays away until it warns.
 */
object HeaderSeen {
    private const val PREFS = "header_lines"
    private const val KEY_BUILD = "build"
    private const val KEY_SEEN = "seen"

    private fun build() = "${BuildConfig.VERSION_NAME}.${BuildConfig.BUILD}"

    /** The lines seen so far: none once the app has been updated since they were stored. */
    fun seenFor(storedBuild: String?, build: String, stored: Set<String>): Set<String> = if (storedBuild == build) stored else emptySet()

    fun load(context: Context): Set<String> {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return seenFor(p.getString(KEY_BUILD, null), build(), p.getStringSet(KEY_SEEN, emptySet()).orEmpty())
    }

    fun add(context: Context, key: String) {
        val seen = load(context) + key
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_BUILD, build()).putStringSet(KEY_SEEN, seen).apply()
    }
}
