package com.churchdrive.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.churchdrive.app.ha.ConnectionState
import com.churchdrive.app.ha.HaAuth
import com.churchdrive.app.ha.HaClient
import com.churchdrive.app.ha.Refresh
import com.churchdrive.app.ui.AssistIntroStore
import com.churchdrive.app.ui.AssistModel
import com.churchdrive.app.ui.AssistPanel
import com.churchdrive.app.ui.AssistWakeSetting
import com.churchdrive.app.ui.hasMicrophone
import com.churchdrive.app.ui.ChurchDriveTheme
import com.churchdrive.app.ui.HaApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * The voice assistant. It opens over whatever is on the screen (the home screen, when started from the widget) as a bottom
 * sheet, without opening the app: swipe it up for the whole screen. It has its own line to the house, so the app need not be
 * running, and it closes when you leave it. The first time after each new version it introduces itself and what is new.
 * Reached from the widget, the microphone in the app, the Quick Settings tile and the icon's long-press menu.
 */
class AssistActivity : ComponentActivity() {
    private var client: HaClient? = null
    private var model: AssistModel? = null
    private var intro by mutableStateOf<com.churchdrive.app.ui.IntroDue?>(null)

    private var enableWakeAfterPermission = false

    private val askMicrophone = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && enableWakeAfterPermission) { enableWakeAfterPermission = false; AssistWakeSetting.set(this, true) }
        else if (granted) model?.listen() else model?.say("Allow the microphone to talk to the house (Settings > Apps > Church Drive > Permissions).")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val session = Session(this)
        if (!session.signedIn) {
            Toast.makeText(this, "Open Church Drive and sign in first", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        AssistWakeSetting.load(this)
        val base = session.url?.trim()?.trimEnd('/').orEmpty()
        val c = HaClient(lifecycleScope)
        client = c
        val api = object : HaApi {
            override fun request(type: String, params: JSONObject, done: (Any?) -> Unit) = c.request(type, params) { done(it) }
            override fun subscribe(type: String, params: JSONObject, onEvent: (JSONObject) -> Unit): Int = c.subscribe(type, params, onEvent)
            override fun close(subscription: Int) = c.unsubscribe(subscription)
            override fun sendAudio(handlerId: Int, chunk: ByteArray, length: Int): Boolean = c.sendAudio(handlerId, chunk, length)
            override val connected: Boolean get() = c.connection.value == ConnectionState.Connected
        }
        val m = AssistModel(this, api, base)
        model = m
        m.followUp = true
        lifecycleScope.launch {
            val token = withContext(Dispatchers.IO) { freshToken(session) }
            if (token != null) c.connect(base, token, light = true)
            c.connection.first { it == ConnectionState.Connected }
            m.loadName()
        }
        intro = AssistIntroStore.take(this)
        setContent {
            ChurchDriveTheme {
                AssistPanel(
                    model = m, onClose = { finish() },
                    onTask = { said ->
                        startActivity(
                            Intent(this, MainActivity::class.java)
                                .putExtra(MainActivity.EXTRA_SHARE_TEXT, said)
                                .putExtra(MainActivity.EXTRA_SHARE_SOURCE, "Voice")
                                .putExtra(MainActivity.EXTRA_SHARE_AUTO_ADD, true)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                        )
                        finish()
                    },
                    intro = intro?.kind, introFeatures = intro?.features.orEmpty(), person = session.personName.orEmpty(),
                    onIntroDone = { m.stopSpeaking(); intro = null },
                    onIntroTry = { m.stopSpeaking(); intro = null; talk() },
                    wakeOn = AssistWakeSetting.enabled,
                    onEnableWake = { if (hasMicrophone(this)) AssistWakeSetting.set(this, true) else { enableWakeAfterPermission = true; askMicrophone.launch(Manifest.permission.RECORD_AUDIO) } },
                )
            }
        }
        if (intro == null) talk()
    }

    /** Listens, asking for the microphone first if it hasn't been allowed yet. */
    private fun talk() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            model?.listen()
        } else {
            askMicrophone.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    /** A fresh access token from the refresh token when there is one, else the stored token. Blocking. */
    private fun freshToken(session: Session): String? {
        val url = session.url ?: return null
        val refresh = session.refreshToken ?: return session.token
        return when (val r = HaAuth.refresh(url, refresh)) {
            is Refresh.Fresh -> r.tokens.access.also { session.saveAccess(it) }
            else -> session.token
        }
    }

    /** Opened again while it is already open (a second tap of the microphone): use the one that's there, don't stack another. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (this.intro == null) talk()
    }

    override fun finish() {
        super.finish()
        // No window slide or fade: the sheet fades its own backdrop.
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    override fun onStop() {
        super.onStop()
        // An overlay doesn't linger: leaving it (home, another app) ends it.
        if (!isChangingConfigurations) finish()
    }

    override fun onDestroy() {
        model?.release()
        client?.disconnect()
        super.onDestroy()
    }

    companion object {
        const val PREFS = "assist"
        const val KEY_INTRO = "intro_version"
    }
}
