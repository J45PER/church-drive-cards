package com.churchdrive.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.churchdrive.app.UpdateCheck
import com.churchdrive.app.house.PhoneSetup
import com.churchdrive.app.house.PhoneSetupStore

/**
 * A newer build is out: the banner at the top of Home (it stays until the app is updated), and a question that opens by itself at
 * most once a day, so the update is hard to miss. Nothing when the app is up to date.
 */
@Composable
fun UpdatePrompt(available: Boolean) {
    if (!available) return
    val context = LocalContext.current
    val store = remember { PhoneSetupStore(context) }
    var showing by remember { mutableStateOf(PhoneSetup.updateDialogDue(store.updateAskedAt, System.currentTimeMillis())) }
    LaunchedEffect(showing) { if (showing) store.updateAskedAt = System.currentTimeMillis() }
    Box {
        UpdateBanner()
        if (showing) {
            AlertDialog(
                onDismissRequest = { showing = false },
                title = { Text("A new version is ready") },
                text = { Text("Updating keeps your location and notifications working properly, so the house knows who is home. It takes a minute: Android asks to install it.") },
                confirmButton = {
                    TextButton(onClick = {
                        showing = false
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(UpdateCheck.APK_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }) { Text("Update") }
                },
                dismissButton = { TextButton(onClick = { showing = false }) { Text("Later") } },
            )
        }
    }
}
