package com.churchdrive.app

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri

/** Hands the stored sign-in to the Church Drive To-Do app (see [SessionShare]); readable only with its signature permission. */
class SessionProvider : ContentProvider() {
    override fun onCreate() = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val cursor = MatrixCursor(arrayOf("url", "token", "refresh", "person"))
        val session = Session(context ?: return cursor)
        // A fresh access token where the sign-in can make one, so the other app doesn't start with an expired one.
        val token = session.freshToken()
        val url = session.url
        if (url != null && token != null) cursor.addRow(arrayOf(url, token, session.refreshToken.orEmpty(), session.personName.orEmpty()))
        return cursor
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
}
