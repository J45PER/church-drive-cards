package com.churchdrive.todo

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.churchdrive.app.HomeAssistantLink

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
     * own device list opens in the browser. A job that only Home Assistant can do (an update) opens Home Assistant instead.
     */
    fun openHouseJob(context: Context, summary: String, page: String?, haPath: String?, houseAddress: String?): Boolean {
        // A job only Home Assistant itself can do (an update waiting) opens Home Assistant (its app, else the browser).
        if (haPath != null && HomeAssistantLink.open(context, houseAddress, haPath)) return true
        val opened = open(context) { if (page != null) putExtra("page", page) else putExtra("house_task", summary) }
        if (opened || houseAddress.isNullOrBlank()) return opened
        return runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(houseAddress.trimEnd('/') + "/config/devices/dashboard")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
    }
}
