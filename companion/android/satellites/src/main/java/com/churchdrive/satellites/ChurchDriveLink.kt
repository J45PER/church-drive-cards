package com.churchdrive.satellites

import android.content.Context
import android.content.Intent

/** The Church Drive app, which gives this app its sign-in and has the house's other pages. */
object ChurchDriveLink {
    const val PACKAGE = "com.churchdrive.manager"

    /** Opens the Church Drive app; false when it isn't installed. */
    fun open(context: Context): Boolean {
        val intent = runCatching { context.packageManager.getLaunchIntentForPackage(PACKAGE)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }.getOrNull() ?: return false
        return runCatching { context.startActivity(intent) }.isSuccess
    }
}
