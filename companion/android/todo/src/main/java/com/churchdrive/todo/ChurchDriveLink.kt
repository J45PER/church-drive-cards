package com.churchdrive.todo

import android.content.Context
import android.content.Intent
import android.net.Uri

/** The Church Drive app, which gives this app its sign-in, delivers the house's notifications and has the device pages. */
object ChurchDriveLink {
    const val PACKAGE = "com.churchdrive.app"

    /** The intent that opens the Church Drive app, or null when it isn't installed. */
    fun intent(context: Context): Intent? = runCatching {
        context.packageManager.getLaunchIntentForPackage(PACKAGE)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }.getOrNull()

    /** Opens the Church Drive app (with [extras] on the intent); false when it isn't installed. */
    fun open(context: Context, extras: Intent.() -> Unit = {}): Boolean {
        val intent = intent(context) ?: return false
        intent.extras()
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    /**
     * A house job, taken to the relevant area. The Church Drive app has the pages for it: Cleaning or Security for a job about a
     * place, else the device's page (found by the name in the job's words) or Device Health. Without that app, Home Assistant's
     * own device list opens in the browser.
     */
    fun openHouseJob(context: Context, summary: String, page: String?, houseAddress: String?): Boolean {
        val opened = open(context) { if (page != null) putExtra("page", page) else putExtra("house_task", summary) }
        if (opened || houseAddress.isNullOrBlank()) return opened
        return runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(houseAddress.trimEnd('/') + "/config/devices/dashboard")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
    }
}
