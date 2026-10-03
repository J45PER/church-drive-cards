package com.churchdrive.app.house

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/** What the person has allowed the app to do on the phone. */
object Permissions {
    private fun has(context: Context, permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /** Android 13 and later ask before an app may show notifications; older ones allow them. */
    fun canNotify(context: Context) =
        Build.VERSION.SDK_INT < 33 || has(context, Manifest.permission.POST_NOTIFICATIONS)

    fun canLocate(context: Context) =
        has(context, Manifest.permission.ACCESS_FINE_LOCATION) || has(context, Manifest.permission.ACCESS_COARSE_LOCATION)

    /** "Allow all the time": needed to report where the phone is while the app is closed. Older Androids have no separate question. */
    fun canLocateInBackground(context: Context) =
        Build.VERSION.SDK_INT < 29 || has(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
}
