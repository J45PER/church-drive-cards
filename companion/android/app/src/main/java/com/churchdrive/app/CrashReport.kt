package com.churchdrive.app

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Keeps what's needed to see a crash the next time the app opens (there's no other way to see it on a phone):
 * the error of a crash in the app's own code, Android's record of why the app last died (this is the only way to
 * see a crash inside the video library), and a short trail of what the app was doing. Temporary, for test builds.
 */
object CrashReport {
    private fun file(context: Context) = File(context.filesDir, "last-crash.txt")
    private fun trailFile(context: Context) = File(context.filesDir, "trail.txt")
    private fun seenFile(context: Context) = File(context.filesDir, "exit-seen.txt")

    private var app: Context? = null
    private val clock = SimpleDateFormat("HH:mm:ss", Locale.UK)

    fun install(context: Context) {
        val app = context.applicationContext
        this.app = app
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { file(app).writeText("Thread ${thread.name}\n" + error.stackTraceToString().take(6000)) }
            previous?.uncaughtException(thread, error)
        }
    }

    /** Adds a step to the trail (kept on disk straight away, as a native crash gives no chance to save it later). */
    @Synchronized
    fun note(message: String) {
        val context = app ?: return
        runCatching {
            val f = trailFile(context)
            val lines = (if (f.exists()) f.readLines() else emptyList()).takeLast(39) + "${clock.format(Date())} $message"
            f.writeText(lines.joinToString("\n"))
        }
    }

    /** What to show about the last time the app died, if it did, and forget it. */
    fun take(context: Context): String? {
        val parts = mutableListOf<String>()
        val java = file(context)
        if (java.exists()) {
            runCatching { java.readText() }.getOrNull()?.let { parts += "Error in the app:\n$it" }
            java.delete()
        }
        exitInfo(context)?.let { parts += it }
        if (parts.isEmpty()) return null
        val trail = trailFile(context)
        if (trail.exists()) runCatching { trail.readText() }.getOrNull()?.let { parts += "Steps before:\n$it" }
        trail.delete()
        return parts.joinToString("\n\n")
    }

    private fun exitInfo(context: Context): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return runCatching {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val last = manager.getHistoricalProcessExitReasons(context.packageName, 0, 1).firstOrNull() ?: return null
            val seen = seenFile(context).takeIf { it.exists() }?.readText()?.toLongOrNull() ?: 0L
            if (last.timestamp <= seen) return null
            seenFile(context).writeText(last.timestamp.toString())
            val crashed = last.reason == ApplicationExitInfo.REASON_CRASH_NATIVE ||
                last.reason == ApplicationExitInfo.REASON_CRASH ||
                last.reason == ApplicationExitInfo.REASON_ANR ||
                last.reason == ApplicationExitInfo.REASON_SIGNALED
            if (!crashed) return null
            val name = when (last.reason) {
                ApplicationExitInfo.REASON_CRASH_NATIVE -> "crash in native code"
                ApplicationExitInfo.REASON_CRASH -> "crash"
                ApplicationExitInfo.REASON_ANR -> "not responding"
                else -> "killed by a signal"
            }
            val trace = runCatching { last.traceInputStream?.use { it.readBytes().take(600_000).toByteArray() } }.getOrNull()
            "Android says: $name\n${last.description.orEmpty()}" + (trace?.let { "\n\nWhere:\n${printable(it)}" } ?: "")
        }.getOrNull()
    }

    /** The readable text in a crash dump (its function and library names), which is what says where it crashed. */
    private fun printable(bytes: ByteArray): String {
        val out = StringBuilder()
        val run = StringBuilder()
        fun flush() {
            if (run.length >= 6) out.append(run).append('\n')
            run.clear()
        }
        for (b in bytes) {
            val c = b.toInt().toChar()
            if (b in 32..126) run.append(c) else flush()
        }
        flush()
        return out.lines().take(120).joinToString("\n").take(5000)
    }
}
