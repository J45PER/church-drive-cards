package com.churchdrive.satellites

import android.content.Context

/** The person's choices in this app, kept on the phone. */
class SatelliteSettings(context: Context) {
    private val prefs = context.getSharedPreferences("church_drive_satellites", Context.MODE_PRIVATE)

    /** Open on the satellite last looked at, instead of always on the list. */
    var rememberSatellite: Boolean
        get() = prefs.getBoolean("remember_satellite", false)
        set(v) = prefs.edit().putBoolean("remember_satellite", v).apply()

    var lastSatellite: String?
        get() = prefs.getString("last_satellite", null)
        set(v) = prefs.edit().putString("last_satellite", v).apply()
}
