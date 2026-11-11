package com.churchdrive.app

import android.content.Context
import android.content.Intent

/**
 * Clive Admin, the separate app for administrators (Manager, Energy, Device Health, colours and more), when it is on this phone. The account
 * panel has a button that opens it, or its Google Play page when it is not installed yet.
 */
object AdminLink {
    const val PACKAGE = "com.churchdrive.admin"

    /** The intent that opens Clive Admin, or null when it isn't installed. */
    fun intent(context: Context): Intent? = runCatching {
        context.packageManager.getLaunchIntentForPackage(PACKAGE)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }.getOrNull()

    fun installed(context: Context): Boolean = intent(context) != null

    /** Opens Clive Admin (false when it isn't installed). */
    fun open(context: Context): Boolean {
        val intent = intent(context) ?: return false
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    /** Opens Clive Admin's Google Play listing (the Play app, else the web page). */
    fun openStore(context: Context) = UpdateCheck.openPlayPage(context, PACKAGE)

    /** Opens Clive Admin if it is installed, else its Play page. */
    fun openOrStore(context: Context) { if (!open(context)) openStore(context) }
}
