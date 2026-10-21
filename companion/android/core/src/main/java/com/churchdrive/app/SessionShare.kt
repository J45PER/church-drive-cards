package com.churchdrive.app

import android.content.Context
import android.net.Uri

/** A Home Assistant sign-in handed from the Church Drive app to the Church Drive To-Do app on the same phone. */
data class SharedSession(val url: String, val token: String, val refresh: String?, val person: String?)

/** What asking the Church Drive app for its sign-in found. */
sealed interface SharedLookup {
    data class Found(val session: SharedSession) : SharedLookup
    /** The app answered: it is installed but not signed in. */
    object SignedOut : SharedLookup
    /** Not installed, not allowed, or no answer. */
    object Unavailable : SharedLookup
}

/**
 * The Church Drive app offers its sign-in through a content provider that only apps signed with the same key (and holding
 * [PERMISSION]) can read, so the to-do app signs in with no typing. Only the phone app declares the permission (two apps declaring it under different keys, as on
 * Google Play, can't both be installed); with different keys the read is refused and the to-do app signs in for itself.
 */
object SessionShare {
    const val AUTHORITY = "com.churchdrive.app.sessionshare"
    const val PERMISSION = "com.churchdrive.app.permission.SHARE_SESSION"
    val URI: Uri = Uri.parse("content://$AUTHORITY/session")

    /** Asks the Church Drive app for its sign-in. Blocking: call off the main thread. */
    fun read(context: Context): SharedLookup = runCatching {
        val cursor = context.contentResolver.query(URI, null, null, null, null) ?: return SharedLookup.Unavailable
        cursor.use { c ->
            if (!c.moveToFirst()) return SharedLookup.SignedOut
            fun col(name: String) = c.getColumnIndex(name).takeIf { it >= 0 }?.let { c.getString(it) }?.takeIf { it.isNotBlank() }
            val url = col("url")
            val token = col("token")
            if (url == null || token == null) SharedLookup.SignedOut else SharedLookup.Found(SharedSession(url, token, col("refresh"), col("person")))
        }
    }.getOrDefault(SharedLookup.Unavailable)
}
