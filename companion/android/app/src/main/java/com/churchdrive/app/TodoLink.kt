package com.churchdrive.app

import android.content.Context
import android.content.Intent
import com.churchdrive.app.ui.TodoDeepLink

/**
 * Church Drive To-Do, the separate to-do app, when it is on this phone. The To-do page of this app lives in the account panel and
 * opens the app instead where it is installed, unless the person has turned that off in Settings (then the To-do page opens inside this
 * app). A task opened from the Home page opens that task in the To-Do app. (When the app isn't installed this app still shows its own
 * To-do page; a link to the app's Play listing can replace that once there is one.)
 */
object TodoLink {
    const val PACKAGE = "com.churchdrive.todo"
    private const val PREFS = "church_drive_todo_link"
    private const val KEY = "open_app"

    /** The intent that opens the To-Do app, or null when it isn't installed. */
    fun intent(context: Context): Intent? = runCatching {
        context.packageManager.getLaunchIntentForPackage(PACKAGE)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }.getOrNull()

    fun installed(context: Context): Boolean = intent(context) != null

    /** Opens Church Drive To Do's Google Play listing (the Play app, else the web page). */
    fun openStore(context: Context) {
        val play = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=$PACKAGE")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val web = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://play.google.com/store/apps/details?id=$PACKAGE")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(play) }.onFailure { runCatching { context.startActivity(web) } }
    }

    /** Whether the person wants the To-Do app opened (the default) rather than this app's own To-do page. */
    fun preferApp(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, true)

    fun setPreferApp(context: Context, on: Boolean) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply()

    /** Whether tapping To-do opens the To-Do app: it is installed and the person hasn't turned that off. */
    fun opensApp(context: Context): Boolean = preferApp(context) && installed(context)

    /**
     * Opens the To-Do app, on one task when [list] and [uid] name it. False (and nothing happens) when it isn't installed or the
     * person prefers this app's own page, so the caller opens that instead.
     */
    fun open(context: Context, list: String? = null, uid: String? = null): Boolean {
        if (!preferApp(context)) return false
        val intent = intent(context) ?: return false
        if (list != null && uid != null) intent.putExtra(TodoDeepLink.EXTRA_LIST, list).putExtra(TodoDeepLink.EXTRA_UID, uid)
        return runCatching { context.startActivity(intent) }.isSuccess
    }
}
