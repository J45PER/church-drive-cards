package com.churchdrive.app.house

import android.content.Context
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability

/**
 * Google Play's own update prompt for the build installed from Play, the same one other Play apps show: Play says whether a newer
 * version is published, and the update downloads and installs in its full-screen flow, then the app restarts. Only a Play install
 * can use it (test builds from GitHub use [com.churchdrive.app.UpdateCheck]); on anything else Play answers "not available".
 */
class PlayUpdate(context: Context) {
    private val manager = AppUpdateManagerFactory.create(context.applicationContext)

    /** Asks Play. [done] gets the update that is waiting (or one that was started and was interrupted), or null when there is none or Play can't be asked. */
    fun check(done: (AppUpdateInfo?) -> Unit) {
        manager.appUpdateInfo
            .addOnSuccessListener { info ->
                val waiting = info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE ||
                    info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS
                done(info.takeIf { waiting })
            }
            .addOnFailureListener { done(null) }
    }

    /** Starts Play's update screen. False when Play won't run it here (the caller then opens the Play page). */
    fun start(info: AppUpdateInfo, launcher: ActivityResultLauncher<IntentSenderRequest>): Boolean {
        if (!info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) return false
        return runCatching {
            manager.startUpdateFlowForResult(info, launcher, AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build())
        }.getOrDefault(false)
    }
}
