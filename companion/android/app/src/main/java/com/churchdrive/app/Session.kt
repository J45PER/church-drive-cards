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
    val signedIn: Boolean get() = url != null && token != null

    fun save(url: String, token: String) {
        prefs.edit().putString("url", url.trim()).putString("token", token.trim()).apply()
    }

    fun clear() = prefs.edit().clear().apply()
}
