package com.churchdrive.app

import android.content.Context
import java.io.File

/**
 * Keeps the error of a crash so it can be shown the next time the app opens (there's no other way to see it
 * on a phone). Temporary, for finding bugs in test builds.
 */
object CrashReport {
    private fun file(context: Context) = File(context.filesDir, "last-crash.txt")

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { file(app).writeText("Thread ${thread.name}\n" + error.stackTraceToString().take(6000)) }
            previous?.uncaughtException(thread, error)
        }
    }

    /** The saved crash, if any, which is forgotten once read. */
    fun take(context: Context): String? {
        val f = file(context)
        if (!f.exists()) return null
        return runCatching { f.readText() }.getOrNull().also { f.delete() }
    }
}
