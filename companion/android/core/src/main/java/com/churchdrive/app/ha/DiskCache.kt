package com.churchdrive.app.ha

import android.content.Context
import java.io.File

/**
 * What the app last read from Home Assistant (the dashboard, registries, the states), kept on the phone so the next time the app opens it
 * can show its pages at once while the live reading catches up. Small text files; any trouble reading or writing one is ignored.
 */
class DiskCache(context: Context) {
    private val dir = File(context.filesDir, "cache").also { it.mkdirs() }

    fun read(name: String): String? = runCatching { File(dir, "$name.json").takeIf { it.isFile }?.readText() }.getOrNull()

    fun write(name: String, text: String) {
        runCatching {
            // Written beside, then moved over, so a reader never sees half a file.
            val tmp = File(dir, "$name.tmp")
            tmp.writeText(text)
            tmp.renameTo(File(dir, "$name.json"))
        }
    }

    /** Forgets everything (signing out). */
    fun clear() {
        runCatching { dir.listFiles()?.forEach { it.delete() } }
    }
}
