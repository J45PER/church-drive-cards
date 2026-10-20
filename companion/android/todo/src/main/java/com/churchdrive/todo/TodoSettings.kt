package com.churchdrive.todo

import android.content.Context

/** The person's choices in this app, kept on the phone. */
class TodoSettings(context: Context) {
    private val prefs = context.getSharedPreferences("church_drive_todo", Context.MODE_PRIVATE)

    /** Finished tasks listed open, instead of folded away until asked for. */
    var showDone: Boolean
        get() = prefs.getBoolean("show_done", false)
        set(v) = prefs.edit().putBoolean("show_done", v).apply()

    /** Open on the page last looked at, instead of always on My to-do. */
    var rememberPage: Boolean
        get() = prefs.getBoolean("remember_page", false)
        set(v) = prefs.edit().putBoolean("remember_page", v).apply()

    var lastPage: String
        get() = prefs.getString("last_page", null) ?: TodoTab.Mine.name
        set(v) = prefs.edit().putString("last_page", v).apply()
}
