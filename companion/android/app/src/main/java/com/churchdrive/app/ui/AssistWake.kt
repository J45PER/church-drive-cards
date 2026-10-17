package com.churchdrive.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
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
    /** The last few things the voice pipeline did, so a problem can be seen (Settings shows them). */
    val log = androidx.compose.runtime.mutableStateListOf<String>()
    /** How long the last answer took, step by step (Settings shows it), or empty before the first. */
    var timing by mutableStateOf("")
}

/** Whether the person has switched on "listen for the wake word while the app is open" (Account > Settings). */
object AssistWakeSetting {
    private const val KEY = "wake_in_app"
    var enabled by mutableStateOf(false)
        private set

    fun load(context: Context) {
        enabled = context.getSharedPreferences(AssistActivity.PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)
        AssistColourSetting.load(context)
        AssistStartSetting.load(context)
        AssistVoiceSetting.load(context)
    }

    fun set(context: Context, on: Boolean) {
        context.getSharedPreferences(AssistActivity.PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply()
        enabled = on
    }
}

/** Whether Jarvis opens ready to type (the keyboard up) instead of ready to listen (Account > Settings). */
object AssistStartSetting {
    private const val KEY = "start_typing"
    var typing by mutableStateOf(false)
        private set

    fun load(context: Context) {
        typing = context.getSharedPreferences(AssistActivity.PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)
    }

    fun set(context: Context, on: Boolean) {
        context.getSharedPreferences(AssistActivity.PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply()
        typing = on
    }
}

/** Whether Jarvis speaks his answers aloud (the bar's Voice button in Jarvis); off, he only writes them. */
object AssistVoiceSetting {
    private const val KEY = "voice_replies"
    var on by mutableStateOf(true)
        private set

    fun load(context: Context) {
        on = context.getSharedPreferences(AssistActivity.PREFS, Context.MODE_PRIVATE).getBoolean(KEY, true)
    }

    fun set(context: Context, value: Boolean) {
        context.getSharedPreferences(AssistActivity.PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, value).apply()
        on = value
    }
}

/** Whether Jarvis keeps his classic indigo, purple and green instead of following the phone's theme colours (Account > Settings). */
object AssistColourSetting {
    private const val KEY = "classic_colours"
    var classic by mutableStateOf(false)
        private set

    fun load(context: Context) {
        classic = context.getSharedPreferences(AssistActivity.PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)
    }

    fun set(context: Context, on: Boolean) {
        context.getSharedPreferences(AssistActivity.PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply()
        classic = on
    }
}

/**
 * Remembers whether the assistant has introduced itself to this person, and which entries of the Jarvis log they have been told about.
 * The introduction is taken once: the moment it is handed out it counts as told, however it is closed.
 */
object AssistIntroStore {
    private const val KEY_EVER = "introduced"
    private const val KEY_TOLD = "told_ids"
    private fun prefs(context: Context) = context.getSharedPreferences(AssistActivity.PREFS, Context.MODE_PRIVATE)

    /** The introduction due now, if any, and notes that the person has been told. */
    fun take(context: Context): IntroDue? {
        val p = prefs(context)
        val told = p.getStringSet(KEY_TOLD, null)
        val result = introDue(p.getBoolean(KEY_EVER, false), told, ASSIST_FEATURES)
        p.edit().putBoolean(KEY_EVER, true).putStringSet(KEY_TOLD, result.told).apply()
        return result.due
    }
}

fun hasMicrophone(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

/**
 * Jarvis inside the app: one controller, shared by the wake word host (which owns the microphone and the model), the overlay that
 * shows him and the round button on every page, which is the same button whether he is shut or open.
 */
class JarvisController {
    var model by mutableStateOf<AssistModel?>(null)
    var open by mutableStateOf(false)
    var intro by mutableStateOf<IntroDue?>(null)
    /** The typing box, shared with the bottom bar (whose buttons become Jarvis's while he is open). */
    val ui = JarvisTyping()
    var userName = ""
    var onTask: (String) -> Unit = {}
    /** Opens him from the button: ready to type, or listening, as Settings says. */
    var openFromButton: () -> Unit = {}
    var close: () -> Unit = {}
    var enableWake: () -> Unit = {}
}

val LocalJarvis = androidx.compose.runtime.compositionLocalOf<JarvisController?> { null }

/**
 * While the app is open (and the setting is on), listens for the house's wake word ("Hey Jarvis") by streaming the microphone to
 * Home Assistant, which does the listening, exactly as a Voice Assistant satellite does. When the wake word is heard, the assistant
 * opens over the app (see [JarvisOverlay]) and the rest of the sentence is its command.
 * It listens only while the app is on screen, and stops the moment the app is left.
 */
@Composable
fun AssistWakeHost(controller: JarvisController, connected: Boolean, base: String, api: HaApi?, userName: String, onTask: (String) -> Unit) {
    if (api == null) return
    val context = LocalContext.current
    val enabled = AssistWakeSetting.enabled
    val model = remember(api) { AssistModel(context, api, base) }
    val owner = LocalLifecycleOwner.current
    val micAsk = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) AssistWakeSetting.set(context, true)
    }
    // Opened from the button without the microphone allowed yet: ask, then listen.
    val talkAsk = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) model.listen()
    }
    androidx.compose.runtime.SideEffect {
        controller.model = model
        controller.userName = userName
        controller.onTask = onTask
        controller.close = {
            controller.open = false
            model.panelClosed()
        }
        controller.enableWake = { if (hasMicrophone(context)) AssistWakeSetting.set(context, true) else micAsk.launch(Manifest.permission.RECORD_AUDIO) }
        controller.openFromButton = {
            model.panelOpened()
            controller.ui.reset(AssistStartSetting.typing)
            controller.intro = AssistIntroStore.take(context)
            controller.open = true
            if (controller.intro == null && !AssistStartSetting.typing) {
                if (hasMicrophone(context)) model.listen() else talkAsk.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }
    LaunchedEffect(connected) { if (connected) model.loadName() }
    LaunchedEffect(model.state) { AssistWakeStatus.waiting = model.state == AssistState.Waiting }
    LaunchedEffect(model.wakeWord) { AssistWakeStatus.phrase = wakePhrase(model.wakeWord) }
    LaunchedEffect(enabled) { if (!enabled) { AssistWakeStatus.waiting = false; AssistWakeStatus.problem = "" } }
    DisposableEffect(model) {
        model.onWake = {
            model.panelOpened()
            controller.ui.reset(false)
            controller.intro = AssistIntroStore.take(context)
            controller.open = true
        }
        onDispose { model.release(); controller.model = null }
    }
    // The wake word is always on while Jarvis is open (whatever the setting says); the setting is for opening him when he is shut.
    val wake = enabled || controller.open
    DisposableEffect(owner, model, wake, connected) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START && wake && connected && hasMicrophone(context)) model.startWake()
            if (event == Lifecycle.Event.ON_STOP) model.stopWake()
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            model.stopWake()
        }
    }
}

/** Jarvis over the app (not a window of its own, so his round button can stay above him). Nothing when he is shut. */
@Composable
fun JarvisOverlay(onSettings: () -> Unit = {}) {
    val j = LocalJarvis.current ?: return
    val model = j.model ?: return
    if (!j.open) return
    // While he is open the window must not pan up to keep the typing box in view (the phone's default for this window): he places the box above the
    // keyboard himself, and a panned window would count the keyboard twice.
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val window = generateSequence(context) { (it as? android.content.ContextWrapper)?.baseContext }.filterIsInstance<android.app.Activity>().firstOrNull()?.window
        val before = window?.attributes?.softInputMode
        @Suppress("DEPRECATION")
        window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        onDispose { if (before != null) window?.setSoftInputMode(before) }
    }
    // Taps on him stay on him, not on the page underneath.
    androidx.compose.foundation.layout.Box(
        androidx.compose.ui.Modifier.fillMaxSize().padding(bottom = jarvisBottomPad(keyboard = true)).clickable(
            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null,
        ) {},
    ) {
        AssistPanel(
            model = model, onClose = j.close, onTask = { said -> j.close(); j.onTask(said) },
            intro = j.intro?.kind, introFeatures = j.intro?.features.orEmpty(), person = j.userName,
            onIntroDone = { model.stopSpeaking(); j.intro = null },
            wakeOn = AssistWakeSetting.enabled, onEnableWake = j.enableWake,
            typingState = j.ui,
            onSettings = { j.close(); onSettings() },
            embedded = true,
        )
    }
}

/**
 * The round button at the bottom right of every page: Jarvis's logo, and once he is open the same button is his microphone. It sits
 * above everything of his, so it never moves or doubles up.
 */
@Composable
fun JarvisFab(modifier: Modifier = Modifier) {
    val j = LocalJarvis.current ?: return
    val model = j.model ?: return
    val listening = model.state == AssistState.Listening
    // 16 dp above the bottom bar at the right: the place Material's own floating button takes. Over the keyboard's top when he is open and it is up.
    val pad = jarvisBottomPad(keyboard = j.open)
    Box(modifier.fillMaxSize().padding(end = 16.dp, bottom = pad + 16.dp), contentAlignment = Alignment.BottomEnd) {
        JarvisRound(if (j.open) "Talk" else "Open Jarvis", {
            when {
                !j.open -> j.openFromButton()
                // The introduction's "Try it": done with it, and listen.
                j.intro != null -> { j.intro = null; model.stopSpeaking(); model.listen() }
                else -> model.listen()
            }
        }, if (j.open) model.state else AssistState.Idle, if (j.open) model.level else 0f) {
            androidx.compose.animation.Crossfade(if (j.open) (if (listening) 2 else 1) else 0, label = "fabIcon") { v ->
                when (v) {
                    0 -> JarvisLogo(Color.White)
                    else -> HaIcon(if (v == 2) "mdi:stop" else "mdi:microphone", Icons.Filled.Info, Color.White, 28.dp)
                }
            }
        }
    }
}
