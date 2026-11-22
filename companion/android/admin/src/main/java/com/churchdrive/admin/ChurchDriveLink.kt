package com.churchdrive.admin

import android.content.Context
import android.content.Intent

/** The Church Drive app, which gives this app its sign-in and has the house's other pages. */
object ChurchDriveLink {
    const val PACKAGE = "com.churchdrive.manager"

    /** Opens the Church Drive app (on its [page], by the name of its Page, e.g. "Security"); false when it isn't installed. */
    fun open(context: Context, page: String? = null): Boolean {
        val intent = runCatching {
            context.packageManager.getLaunchIntentForPackage(PACKAGE)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.also { if (page != null) it.putExtra("page", page) }
        }.getOrNull() ?: return false
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    /** Opens Church Drive To-Do, on one task when [list] and [uid] name it; false when it isn't installed. */
    fun openTodo(context: Context, list: String? = null, uid: String? = null): Boolean {
        val intent = runCatching { context.packageManager.getLaunchIntentForPackage("com.churchdrive.todo")?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }.getOrNull() ?: return false
        if (list != null && uid != null) intent.putExtra(com.churchdrive.app.ui.TodoDeepLink.EXTRA_LIST, list).putExtra(com.churchdrive.app.ui.TodoDeepLink.EXTRA_UID, uid)
        return runCatching { context.startActivity(intent) }.isSuccess
    }
}
