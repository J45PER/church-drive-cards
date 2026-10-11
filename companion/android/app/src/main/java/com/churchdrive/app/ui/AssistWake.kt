package com.churchdrive.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.churchdrive.app.AssistActivity

/** What the wake word listening is doing, for the top bar's microphone and the Settings line: listening, or why not. */
object AssistWakeStatus {
    var waiting by mutableStateOf(false)
    var phrase by mutableStateOf("")
    var problem by mutableStateOf("")
}

/** Whether the person has switched on "listen for the wake word while the app is open" (Account > Settings). */
object AssistWakeSetting {
    private const val KEY = "wake_in_app"
    var enabled by mutableStateOf(false)
        private set

    fun load(context: Context) {
        enabled = context.getSharedPreferences(AssistActivity.PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)
    }

    fun set(context: Context, on: Boolean) {
        context.getSharedPreferences(AssistActivity.PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply()
        enabled = on
    }
}

fun hasMicrophone(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

/**
 * While the app is open (and the setting is on), listens for the house's wake word ("Hey Jarvis") by streaming the microphone to
 * Home Assistant, which does the listening, exactly as a Voice Assistant satellite does. When the wake word is heard, the assistant
 * opens over the app (as the sheet, or the whole screen if that is the person's choice) and the rest of the sentence is its command.
 * It listens only while the app is on screen, and stops the moment the app is left.
 */
@Composable
fun AssistWakeHost(connected: Boolean, base: String, api: HaApi?, onTask: (String) -> Unit) {
    if (api == null) return
    val context = LocalContext.current
    val enabled = AssistWakeSetting.enabled
    val model = remember(api) { AssistModel(context, api, base) }
    var open by remember { mutableStateOf(false) }
    var full by remember { mutableStateOf(false) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(connected) { if (connected) model.loadName() }
    LaunchedEffect(model.state) { AssistWakeStatus.waiting = model.state == AssistState.Waiting }
    LaunchedEffect(model.wakeWord) { AssistWakeStatus.phrase = wakePhrase(model.wakeWord) }
    LaunchedEffect(enabled) { if (!enabled) { AssistWakeStatus.waiting = false; AssistWakeStatus.problem = "" } }
    DisposableEffect(model) {
        model.onWake = {
            full = context.getSharedPreferences(AssistActivity.PREFS, Context.MODE_PRIVATE).getBoolean(AssistActivity.KEY_FULL, false)
            model.panelOpened()
            open = true
        }
        onDispose { model.release() }
    }
    DisposableEffect(owner, model, enabled, connected) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START && enabled && connected && hasMicrophone(context)) model.startWake()
            if (event == Lifecycle.Event.ON_STOP) model.stopWake()
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            model.stopWake()
        }
    }
    if (open) {
        val close = {
            open = false
            model.panelClosed()
        }
        Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            AssistPanel(model = model, full = full, onFull = { full = it }, onClose = close, onTask = { said -> close(); onTask(said) })
        }
    }
}
