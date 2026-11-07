package com.churchdrive.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.churchdrive.app.UpdateCheck
import com.churchdrive.app.house.PlayUpdate
import com.google.android.play.core.appupdate.AppUpdateInfo

/**
 * The update notice of the smaller apps (To-Do and Satellites), the same as the main app's: a banner that stays until the app is
 * updated and a question that opens by itself at most once a day. A Play install asks Google Play (the update runs in Play's own
 * screen); a test build from GitHub is told by [githubAvailable] and downloads the APK from [apkUrl]. Nothing when up to date.
 */
@Composable
fun UpdateNotice(play: Boolean, githubAvailable: Boolean, apkUrl: String, appName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember { context.applicationContext.getSharedPreferences("church_drive_update_notice", Context.MODE_PRIVATE) }
    val player = remember(play) { if (play) PlayUpdate(context) else null }
    var playInfo by remember { mutableStateOf<AppUpdateInfo?>(null) }
    val playScreen = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { }

    // Play is asked each time the app comes to the front (it also resumes an update that was cut short).
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, player) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) player?.check { playInfo = it }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    val available = if (player != null) playInfo != null else githubAvailable
    var showing by remember { mutableStateOf(false) }
    LaunchedEffect(available) {
        val now = System.currentTimeMillis()
        val last = prefs.getLong("asked_at", 0L)
        if (available && (last == 0L || now - last >= DAY_MS)) {
            prefs.edit().putLong("asked_at", now).apply()
            showing = true
        }
        if (!available) showing = false
    }
    if (!available) return

    val update: () -> Unit = {
        val info = playInfo
        if (player != null) {
            if (info == null || !player.start(info, playScreen)) UpdateCheck.openPlayPage(context, context.packageName)
        } else {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }
    val tone = toneColors(Tone.Grey)
    Box(modifier) {
        Surface(
            shape = RoundedCornerShape(28.dp), color = tone.container, contentColor = tone.onContainer,
            modifier = Modifier.fillMaxWidth().clickable(onClick = update),
        ) {
            Row(Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("A newer version of $appName is ready", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                TextButton(onClick = update) { Text("Update") }
            }
        }
        if (showing) {
            AlertDialog(
                onDismissRequest = { showing = false },
                title = { Text("A new version is ready") },
                text = { Text("Updating keeps $appName working properly with the house. It takes a minute.") },
                confirmButton = { TextButton(onClick = { showing = false; update() }) { Text("Update") } },
                dismissButton = { TextButton(onClick = { showing = false }) { Text("Later") } },
            )
        }
    }
}

private const val DAY_MS = 24 * 60 * 60 * 1000L
