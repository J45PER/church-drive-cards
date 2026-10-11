package com.churchdrive.app.ui

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject

/**
 * The voice assistant's brain, drawn by AssistScreen. What is said is turned into text by the phone's own recogniser (shown
 * as it is heard, inside the app, no pop-up box), then run through the house's Assist, so anything Home Assistant can do by
 * voice works: lights, heating, the alarm, lists, and your own automations. The answer is shown and spoken in the voice
 * the house's preferred Assist pipeline uses. Must be made, used and released on the main thread.
 */
class AssistModel(private val context: Context, private val api: HaApi, private val base: String) {
    var state by mutableStateOf(AssistState.Idle)
        private set

    /** What is being heard right now, before it is final. */
    var partial by mutableStateOf("")
        private set

    /** How loud the voice is (0 to 1), for the waveform. */
    var level by mutableFloatStateOf(0f)
        private set

    /** A problem to tell the person (no recogniser, the house didn't answer). */
    var notice by mutableStateOf("")
        private set

    /** The last sentence the house said it didn't understand, so it can be offered as a task instead. */
    var notUnderstood by mutableStateOf<String?>(null)
        private set

    val messages = mutableStateListOf<AssistMessage>()

    private val main = Handler(Looper.getMainLooper())
    private var conversationId: String? = null
    private var recogniser: SpeechRecognizer? = null
    private var player: MediaPlayer? = null
    private var subscription = -1
    private var answered = false

    /** Shows a message to the person (such as the microphone not being allowed). */
    fun say(text: String) {
        notice = text
    }

    /** Starts listening (or stops, if it already is). */
    fun listen() {
        if (state == AssistState.Listening) {
            recogniser?.stopListening()
            return
        }
        stopSpeaking()
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            notice = "This phone has no speech recogniser."
            return
        }
        val r = recogniser ?: SpeechRecognizer.createSpeechRecognizer(context).also { recogniser = it }
        r.setRecognitionListener(listener)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-GB")
        partial = ""
        notice = ""
        notUnderstood = null
        state = AssistState.Listening
        r.startListening(intent)
    }

    /** Sends a sentence (spoken or typed) to the house's Assist and shows and speaks the answer. */
    fun send(text: String) {
        val said = text.trim()
        if (said.isEmpty()) return
        stopSpeaking()
        closeRun()
        messages += AssistMessage(true, said)
        partial = ""
        notice = ""
        notUnderstood = null
        answered = false
        state = AssistState.Thinking
        val params = JSONObject()
            .put("start_stage", "intent")
            .put("end_stage", "tts")
            .put("input", JSONObject().put("text", said))
            .put("timeout", 30)
        conversationId?.let { params.put("conversation_id", it) }
        subscription = api.subscribe("assist_pipeline/run", params) { event -> main.post { onEvent(event, said) } }
        if (subscription < 0) {
            state = AssistState.Idle
            notice = "Can't reach the house right now."
        }
    }

    private fun onEvent(event: JSONObject, said: String) {
        when (val e = parseAssistEvent(event)) {
            is AssistEvent.Answer -> {
                if (e.conversationId != null) conversationId = e.conversationId
                answered = true
                if (e.text.isNotBlank()) messages += AssistMessage(false, e.text)
                if (isNotUnderstood(e.text)) notUnderstood = said
            }
            is AssistEvent.Audio -> play(assistAudioUrl(base, e.path))
            is AssistEvent.Failed -> {
                if (!answered) {
                    messages += AssistMessage(false, if (e.noMatch) "Sorry, I didn't understand that." else e.message)
                    if (e.noMatch) notUnderstood = said
                } else {
                    notice = "The house answered, but couldn't speak it."
                }
                if (state != AssistState.Speaking) state = AssistState.Idle
                closeRun()
            }
            AssistEvent.Done -> {
                if (state == AssistState.Thinking) state = AssistState.Idle
                closeRun()
            }
            null -> Unit
        }
    }

    private fun play(url: String) {
        stopSpeaking()
        val p = MediaPlayer()
        player = p
        try {
            p.setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build(),
            )
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

    /** Stops the voice and the listening. */
    fun stopSpeaking() {
        player?.let { runCatching { it.stop() }; runCatching { it.release() } }
        player = null
        if (state == AssistState.Speaking) state = AssistState.Idle
    }

    fun stop() {
        recogniser?.cancel()
        stopSpeaking()
        if (state != AssistState.Thinking) state = AssistState.Idle
        partial = ""
        level = 0f
    }

    private fun closeRun() {
        if (subscription >= 0) api.close(subscription)
        subscription = -1
    }

    /** Frees the recogniser, the player and the line to the house. */
    fun release() {
        closeRun()
        stopSpeaking()
        recogniser?.destroy()
        recogniser = null
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) {
            level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
        }
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() {
            level = 0f
        }
        override fun onError(error: Int) {
            level = 0f
            state = AssistState.Idle
            notice = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "I didn't hear anything. Tap the microphone to try again."
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Allow the microphone to talk to the house."
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "The speech recogniser needs a connection."
                else -> "I couldn't listen. Tap the microphone to try again."
            }
        }
        override fun onResults(results: Bundle?) {
            level = 0f
            val said = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            if (said.isBlank()) {
                state = AssistState.Idle
                notice = "I didn't hear anything. Tap the microphone to try again."
            } else {
                send(said)
            }
        }
        override fun onPartialResults(partialResults: Bundle?) {
            partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
        }
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }
}
