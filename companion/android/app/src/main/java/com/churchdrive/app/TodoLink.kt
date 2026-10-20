package com.churchdrive.app

import android.content.Context
import android.content.Intent

/**
 * Church Drive To-Do, the separate to-do app, when it is on this phone. The To-do page of this app lives in the account panel and
 * opens the app instead where it is installed. (When it isn't installed this app still shows its own To-do page; a link to the
 * app's Play listing can replace that once there is one.)
 */
object TodoLink {
    const val PACKAGE = "com.churchdrive.todo"

    /** The intent that opens the To-Do app, or null when it isn't installed. */
    fun intent(context: Context): Intent? = runCatching {
        context.packageManager.getLaunchIntentForPackage(PACKAGE)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }.getOrNull()

    fun installed(context: Context): Boolean = intent(context) != null

    /** Opens the To-Do app; false (and nothing happens) when it isn't installed. */
    fun launch(context: Context): Boolean {
        val intent = intent(context) ?: return false
        return runCatching { context.startActivity(intent) }.isSuccess
    }
}
