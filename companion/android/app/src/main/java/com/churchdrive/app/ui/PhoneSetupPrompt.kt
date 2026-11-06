package com.churchdrive.app.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.churchdrive.app.BuildConfig
import com.churchdrive.app.house.House
import com.churchdrive.app.house.Permissions
import com.churchdrive.app.house.PhoneSetup
import com.churchdrive.app.house.PhoneSetupStore
import com.churchdrive.app.house.SetupStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Checks, every time the app is on screen, that this phone allows what the house needs to know where the person is: notifications,
 * location that is shared, precise and "all the time", and unrestricted battery use (see [PhoneSetup]). Whatever is missing shows as
 * a banner at the top of Home, and a question that walks through each in turn opens by itself once for each version of the app, so it
 * is part of using a new version. The banner stays until it is put right. How the phone is set up is also reported to the house.
 */
@Composable
fun PhoneSetupPrompt(settings: AccountSettings) {
    val context = LocalContext.current
    val store = remember { PhoneSetupStore(context) }

    // Android's answers come back while the app is on screen, and a trip to its settings ends back here: look again each time.
    var look by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) look++ }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val state = remember(look, settings.notifyOn, settings.locationOn) { PhoneSetup.read(context, settings.notifyOn, settings.locationOn) }
    val missing = PhoneSetup.missing(state)
    var showing by remember { mutableStateOf(false) }
    // How many times each step has been asked; one that is still refused after being asked is sent to the app's settings instead.
    val asked = remember { mutableStateMapOf<SetupStep, Int>() }

    // The house is told how the phone is set up, so it can see who has allowed what (it is sent again only when it changes).
    LaunchedEffect(state) { withContext(Dispatchers.IO) { runCatching { House.reportSetup(context) } } }

    // Opens by itself once for each version of the app that finds something missing.
    LaunchedEffect(missing.isNotEmpty()) {
        if (PhoneSetup.dialogDue(store.askedVersion, BuildConfig.VERSION_NAME, missing)) {
            store.askedVersion = BuildConfig.VERSION_NAME
            showing = true
        }
    }
    LaunchedEffect(missing.isEmpty()) {
        if (missing.isEmpty()) {
            showing = false
            asked.clear()
        }
    }

    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) settings.onNotify(true)
        look++
    }
    val location = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
        if (Permissions.canLocate(context)) settings.onLocation(true)
        look++
    }
    val background = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> look++ }
    val locationAsk = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

    fun launchIntent(intent: Intent) {
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    fun ask(step: SetupStep) {
        val times = asked[step] ?: 0
        asked[step] = times + 1
        if (PhoneSetup.needsSettings(times)) {
            launchIntent(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
            return
        }
        when (step) {
            SetupStep.Notifications -> if (!state.canNotify) notifications.launch(Manifest.permission.POST_NOTIFICATIONS) else settings.onNotify(true)
            SetupStep.Location -> if (!state.canLocate) location.launch(locationAsk) else settings.onLocation(true)
            SetupStep.PreciseLocation -> location.launch(locationAsk)
            // Android asks for "all the time" only once location while in use is allowed; it then takes the person to its settings page.
            SetupStep.AlwaysLocation -> if (state.canLocate) background.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION) else location.launch(locationAsk)
            SetupStep.Battery -> launchIntent(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    if (missing.isEmpty()) return
    val tone = toneColors(Tone.Amber)
    Box {
        EntityCard(tone.container, tone.onContainer) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(PhoneSetup.summary(missing), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                TextButton(onClick = { showing = true }) { Text("Fix") }
            }
        }
        if (showing) {
            val step = missing.first()
            val settingsNeeded = PhoneSetup.needsSettings(asked[step] ?: 0)
            AlertDialog(
                onDismissRequest = { showing = false },
                title = { Text(PhoneSetup.title(step)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(PhoneSetup.why(step))
                        if (settingsNeeded) Text("Android won't ask again here, so it is set in the app's settings.", style = MaterialTheme.typography.bodySmall)
                        if (missing.size > 1) Text(
                            "${missing.size - 1} more to check after this.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                confirmButton = { TextButton(onClick = { ask(step) }) { Text(PhoneSetup.button(step, settingsNeeded)) } },
                dismissButton = { TextButton(onClick = { showing = false }) { Text("Not now") } },
            )
        }
    }
}
