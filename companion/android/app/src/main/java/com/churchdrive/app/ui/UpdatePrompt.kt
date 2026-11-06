package com.churchdrive.app.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.churchdrive.app.BuildConfig
import com.churchdrive.app.UpdateCheck
import com.churchdrive.app.house.PhoneSetup
import com.churchdrive.app.house.PhoneSetupStore
import com.churchdrive.app.house.PlayUpdate
import com.google.android.play.core.appupdate.AppUpdateInfo

/**
 * A newer version is out: the banner at the top of Home (it stays until the app is updated), and a question that opens by itself at
 * most once a day, so the update is hard to miss. Nothing when the app is up to date.
 *
 * A Play install asks Google Play (the update runs in Play's own screen, like other Play apps); a test build from GitHub is told by
 * [githubAvailable] and downloads the APK.
 */
@Composable
fun UpdatePrompt(githubAvailable: Boolean) {
    val context = LocalContext.current
    val store = remember { PhoneSetupStore(context) }
    val play = remember { if (BuildConfig.PLAY) PlayUpdate(context) else null }
    var playInfo by remember { mutableStateOf<AppUpdateInfo?>(null) }
    val playScreen = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { }

    // Play is asked each time the app comes to the front (it also resumes an update that was cut short).
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, play) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) play?.check { playInfo = it }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    val available = if (play != null) playInfo != null else githubAvailable
    var showing by remember { mutableStateOf(false) }
    LaunchedEffect(available) {
        if (available && PhoneSetup.updateDialogDue(store.updateAskedAt, System.currentTimeMillis())) {
            store.updateAskedAt = System.currentTimeMillis()
            showing = true
        }
        if (!available) showing = false
    }
    if (!available) return

    val update: () -> Unit = {
        val info = playInfo
        if (play != null) {
            if (info == null || !play.start(info, playScreen)) UpdateCheck.openPlayPage(context, context.packageName)
        } else {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(UpdateCheck.APK_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }
    Box {
        UpdateBanner(update)
        if (showing) {
            AlertDialog(
                onDismissRequest = { showing = false },
                title = { Text("A new version is ready") },
                text = { Text("Updating keeps your location and notifications working properly, so the house knows who is home. It takes a minute.") },
                confirmButton = { TextButton(onClick = { showing = false; update() }) { Text("Update") } },
                dismissButton = { TextButton(onClick = { showing = false }) { Text("Later") } },
            )
        }
    }
}
