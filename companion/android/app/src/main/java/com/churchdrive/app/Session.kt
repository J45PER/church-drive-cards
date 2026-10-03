package com.churchdrive.app

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Stores the Home Assistant address and token, encrypted on the device. */
class Session(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "church_drive_session",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    val url: String? get() = prefs.getString("url", null)
    val token: String? get() = prefs.getString("token", null)
    /** Set when signed in with a username and password: lasts, and makes new access tokens. Null for a pasted token. */
    val refreshToken: String? get() = prefs.getString("refresh", null)
    val signedIn: Boolean get() = url != null && token != null

    /** A pasted long-lived token. */
    fun save(url: String, token: String) {
        prefs.edit().putString("url", url.trim()).putString("token", token.trim()).remove("refresh").apply()
    }

    /** A username-and-password sign-in. */
    fun saveSignIn(url: String, access: String, refresh: String?) {
        prefs.edit().putString("url", url.trim()).putString("token", access).also {
            if (refresh != null) it.putString("refresh", refresh) else it.remove("refresh")
        }.apply()
    }

    /** A new access token from the refresh token. */
    fun saveAccess(access: String) {
        prefs.edit().putString("token", access).apply()
    }

    fun clear() = prefs.edit().clear().apply()
}
