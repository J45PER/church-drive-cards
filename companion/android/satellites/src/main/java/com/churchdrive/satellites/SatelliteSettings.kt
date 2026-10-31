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

    /** The home Wi-Fi's name, remembered so pairing the next satellite is quicker. The password is never kept. */
    var wifiName: String
        get() = prefs.getString("wifi_name", "") ?: ""
        set(v) = prefs.edit().putString("wifi_name", v).apply()

    /** The ESPHome API encryption key every satellite was built with (satellite/secrets.yaml), kept on this phone to add new ones. */
    var apiKey: String
        get() = prefs.getString("api_key", "") ?: ""
        set(v) = prefs.edit().putString("api_key", v).apply()
}
