package com.churchdrive.app.wear

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.Text
import com.churchdrive.app.Session
import com.churchdrive.app.ui.ASSIST_SUGGESTIONS
import com.churchdrive.app.ui.AssistEvent
import com.churchdrive.app.ui.AssistState
import com.churchdrive.app.ui.assistAudioUrl
import com.churchdrive.app.ui.forDisplay
import com.churchdrive.app.ui.isDismissal
import com.churchdrive.app.ui.parseAssistEvent
import org.json.JSONObject

/**
 * Clive on the wrist: say or tap a sentence, it goes to the house's Assist pipeline (the same one the phone's Clive uses, so anything
 * Home Assistant can do by voice works) and the answer is shown and, unless turned off, spoken from the watch.
 */
class AssistantActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Signed { connected -> AssistantScreen(connected) } }
    }
}

/** One conversation with the house; follow-ups carry on from the last answer. */
private class AssistantModel(private val context: Context) {
    var state by mutableStateOf(AssistState.Idle)
    var heard by mutableStateOf("")
    var answer by mutableStateOf("")
    var notice by mutableStateOf("")
    var voice by mutableStateOf(true)

    private val main = Handler(Looper.getMainLooper())
    private var conversationId: String? = null
    private var subscription = -1
    private var player: MediaPlayer? = null

    fun send(text: String) {
        val sentence = text.trim()
        if (sentence.isEmpty()) return
        stop()
        heard = sentence
        answer = ""
        notice = ""
        // "No thanks" and the like end the conversation: the next sentence starts a fresh one.
        val closing = isDismissal(sentence)
        state = AssistState.Thinking
        val params = JSONObject()
            .put("start_stage", "intent")
            .put("end_stage", if (voice) "tts" else "intent")
            .put("input", JSONObject().put("text", sentence))
            .put("timeout", 30)
        conversationId?.let { params.put("conversation_id", it) }
        subscription = WatchHa.api.subscribe("assist_pipeline/run", params) { event -> main.post { onEvent(event, closing) } }
        if (subscription < 0) {
            state = AssistState.Idle
            notice = "Can't reach the house right now."
        }
    }

    private fun onEvent(event: JSONObject, closing: Boolean) {
        when (val e = parseAssistEvent(event)) {
            is AssistEvent.Answer -> {
                if (e.text.isNotBlank()) answer = forDisplay(e.text)
                conversationId = if (closing) null else e.conversationId
            }
            is AssistEvent.Audio -> if (voice) play(assistAudioUrl(Session(context).url.orEmpty(), e.path))
            is AssistEvent.Failed -> {
                notice = if (e.noMatch) "I didn't understand that." else e.message
                state = AssistState.Idle
                closeSubscription()
            }
            AssistEvent.Done -> {
                if (state == AssistState.Thinking) state = AssistState.Idle
                closeSubscription()
            }
            else -> Unit
        }
    }

    private fun closeSubscription() {
        if (subscription >= 0) WatchHa.api.close(subscription)
        subscription = -1
    }

    private fun play(url: String) {
        stopSpeaking()
        val p = MediaPlayer()
        player = p
        try {
            p.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            p.setDataSource(url)
            p.setOnPreparedListener { it.start() }
            p.setOnCompletionListener { finishedSpeaking(p) }
            p.setOnErrorListener { _, _, _ -> finishedSpeaking(p); true }
            state = AssistState.Speaking
            p.prepareAsync()
        } catch (e: Exception) {
            finishedSpeaking(p)
        }
    }

    private fun finishedSpeaking(p: MediaPlayer) {
        if (player === p) {
            player = null
            state = AssistState.Idle
        }
        runCatching { p.release() }
    }

    private fun stopSpeaking() {
        val p = player ?: return
        player = null
        runCatching { p.stop() }
        runCatching { p.release() }
    }

    /** Stops listening for the answer and speaking it. */
    fun stop() {
        closeSubscription()
        stopSpeaking()
        state = AssistState.Idle
    }
}

@Composable
private fun AssistantScreen(connected: Boolean) {
    val context = LocalContext.current
    val model = remember { AssistantModel(context) }
    DisposableEffect(Unit) { onDispose { model.stop() } }
    val listen = rememberListener("Ask Clive") { model.send(it) }

    ScalingLazyColumn(modifier = Modifier.fillMaxWidth()) {
        item { ListHeader { Text("Clive") } }
        if (model.heard.isNotEmpty()) item { Text(model.heard, textAlign = TextAlign.Center) }
        when {
            model.state == AssistState.Thinking -> item { Text("Thinking…", textAlign = TextAlign.Center) }
            model.notice.isNotEmpty() -> item { Text(model.notice, textAlign = TextAlign.Center) }
            model.answer.isNotEmpty() -> item { Text(model.answer, textAlign = TextAlign.Center) }
            !connected -> item { Text("Connecting…", textAlign = TextAlign.Center) }
        }
        if (model.state == AssistState.Speaking) row("Stop") { model.stop() }
        row("Talk to Clive") { listen() }
        for (suggestion in ASSIST_SUGGESTIONS) row(suggestion) { model.send(suggestion) }
        row(if (model.voice) "Voice: on" else "Voice: off") { model.voice = !model.voice; if (!model.voice) model.stop() }
    }
}
