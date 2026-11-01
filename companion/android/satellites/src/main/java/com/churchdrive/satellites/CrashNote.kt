package com.churchdrive.satellites

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import java.io.File

/**
 * Keeps the error of a crash so the next launch can show it (a phone gives no other way to see one): the error in the app's own code, and
 * Android's own note of why the app last died. [take] hands it over once; MainActivity shows it on a plain screen before anything else starts.
 */
object CrashNote {
    private fun file(context: Context) = File(context.filesDir, "last-crash.txt")
    private fun seenFile(context: Context) = File(context.filesDir, "exit-seen.txt")

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { file(app).writeText("Version ${BuildConfig.VERSION_NAME} (build ${BuildConfig.BUILD})\nThread ${thread.name}\n" + error.stackTraceToString().take(7000)) }
            previous?.uncaughtException(thread, error)
        }
    }

    /** What to show about the last time the app died, if it did, and forget it. */
    fun take(context: Context): String? {
        val parts = mutableListOf<String>()
        val f = file(context)
        if (f.exists()) {
            runCatching { f.readText() }.getOrNull()?.let { parts += it }
            f.delete()
        }
        exitInfo(context)?.let { parts += it }
        return parts.takeIf { it.isNotEmpty() }?.joinToString("\n\n")
    }

    private fun exitInfo(context: Context): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return runCatching {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val last = manager.getHistoricalProcessExitReasons(context.packageName, 0, 1).firstOrNull() ?: return null
            val seen = seenFile(context).takeIf { it.exists() }?.readText()?.toLongOrNull() ?: 0L
            if (last.timestamp <= seen) return null
            seenFile(context).writeText(last.timestamp.toString())
            val name = when (last.reason) {
                ApplicationExitInfo.REASON_CRASH_NATIVE -> "crash in native code"
                ApplicationExitInfo.REASON_CRASH -> "crash"
                ApplicationExitInfo.REASON_ANR -> "not responding"
                ApplicationExitInfo.REASON_SIGNALED -> "killed by a signal"
                ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "failed to start"
                else -> return null
            }
            "Android says the app last stopped with a $name. ${last.description.orEmpty()}".trim()
        }.getOrNull()
    }
}
