package com.churchdrive.app.wear

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.churchdrive.app.Session
import com.churchdrive.app.ui.ASSISTANT_SHOW_EVENT
import com.churchdrive.app.ui.AssistEvent
import com.churchdrive.app.ui.AssistState
import com.churchdrive.app.ui.AssistantCard
import com.churchdrive.app.ui.ask
import com.churchdrive.app.ui.assistAudioUrl
import com.churchdrive.app.ui.forDisplay
import com.churchdrive.app.ui.isDismissal
import com.churchdrive.app.ui.parseAssistEvent
import com.churchdrive.app.ui.parseAssistantShow
import org.json.JSONObject

/** Clive's toggles on this watch (Settings face). They start as the house says (the Watch panel's Clive defaults) and are kept on the watch. */
class CliveSettings(context: Context, defaults: CliveDefaults) {
    private val prefs = context.getSharedPreferences("clive_watch", Context.MODE_PRIVATE)

    private fun flag(key: String, default: Boolean) = prefs.getBoolean(key, default)

    var voice by mutableStateOf(flag("voice", defaults.voice))
        private set
    var follow by mutableStateOf(flag("follow", defaults.follow))
        private set
    var visual by mutableStateOf(flag("visual", defaults.visual))
        private set
    var buzz by mutableStateOf(flag("buzz", defaults.buzz))
        private set

    fun toggle(key: String) {
        when (key) {
            "voice" -> voice = !voice
            "follow" -> follow = !follow
            "visual" -> visual = !visual
            "buzz" -> buzz = !buzz
        }
        prefs.edit().putBoolean("voice", voice).putBoolean("follow", follow).putBoolean("visual", visual).putBoolean("buzz", buzz).apply()
    }
}

/** What the person said into the microphone, with the words as they come and how loud the voice is. */
class SpeechInput(
    private val context: Context,
    private val onLevel: (Float) -> Unit,
    private val onPartial: (String) -> Unit,
    private val onFinal: (String) -> Unit,
    private val onFail: (heardNothing: Boolean, message: String) -> Unit,
) {
    private var recognizer: SpeechRecognizer? = null

    val available: Boolean get() = SpeechRecognizer.isRecognitionAvailable(context)

    fun hasPermission() = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun start() {
        stop()
        val r = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) = onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> onFail(true, "I didn't hear anything.")
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> onFail(false, "Allow the microphone for this app.")
                    SpeechRecognizer.ERROR_CLIENT -> {}
                    else -> onFail(false, "I couldn't listen just now.")
                }
            }

            override fun onResults(results: Bundle?) {
                val said = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()
                if (said.isEmpty()) onFail(true, "I didn't hear anything.") else onFinal(said)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { onPartial(it) }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        r.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName),
        )
    }

    fun stop() {
        recognizer?.let { runCatching { it.cancel() }; runCatching { it.destroy() } }
        recognizer = null
    }
}

/**
 * One conversation with the house, on the watch: what was heard, the written answer (spoken too, unless turned off), and the card the house
 * sent for it (`church_drive_assistant_show`). Follow-ups carry on from the last answer.
 */
class CliveModel(private val context: Context, val settings: CliveSettings) {
    var state by mutableStateOf(AssistState.Idle)
        private set
    var heard by mutableStateOf("")
        private set

    /** The words so far while listening. */
    var live by mutableStateOf("")
        private set
    var answer by mutableStateOf("")
        private set
    var notice by mutableStateOf("")
        private set

    /** How loud the voice is while listening, 0 to 1. */
    var level by mutableFloatStateOf(0.2f)
        private set

    /** The card the house sent with this answer, and a picture if it sent one. */
    var card by mutableStateOf<AssistantCard?>(null)
        private set
    var picture by mutableStateOf<String?>(null)
        private set
    var pictureCaption by mutableStateOf<String?>(null)
        private set

    private val main = Handler(Looper.getMainLooper())
    private var conversationId: String? = null
    private var subscription = -1
    private var showSubscription = -1
    private var player: MediaPlayer? = null
    private var userId: String? = null

    val speech = SpeechInput(
        context,
        onLevel = { v -> main.post { level = v } },
        onPartial = { t -> main.post { live = t } },
        onFinal = { t -> main.post { send(t) } },
        onFail = { nothing, message ->
            main.post {
                state = AssistState.Idle
                live = ""
                notice = if (nothing) message else message
            }
        },
    )

    /** Starts listening to the house's pictures and cards for this person. Call when the line is up. */
    suspend fun attach() {
        userId = (WatchHa.api.ask("auth/current_user", JSONObject()) as? JSONObject)?.optString("id")?.takeIf { it.isNotBlank() }
        if (showSubscription >= 0) WatchHa.api.close(showSubscription)
        showSubscription = WatchHa.api.subscribe("subscribe_events", JSONObject().put("event_type", ASSISTANT_SHOW_EVENT)) { event ->
            val show = parseAssistantShow(event, userId) ?: return@subscribe
            main.post {
                if (show.card != null) card = show.card
                if (show.image != null) {
                    picture = show.image
                    pictureCaption = show.linkLabel
                }
            }
        }
    }

    fun lost() {
        showSubscription = -1
    }

    fun listen() {
        restTask?.let { main.removeCallbacks(it) }
        stopAudio()
        closeSubscription()
        heard = ""
        live = ""
        answer = ""
        notice = ""
        card = null
        picture = null
        state = AssistState.Listening
        speech.start()
    }

    fun send(text: String) {
        val sentence = text.trim()
        if (sentence.isEmpty()) return
        restTask?.let { main.removeCallbacks(it) }
        stopAudio()
        closeSubscription()
        speech.stop()
        heard = sentence
        live = ""
        answer = ""
        notice = ""
        card = null
        picture = null
        val closing = isDismissal(sentence)
        state = AssistState.Thinking
        val params = JSONObject()
            .put("start_stage", "intent")
            .put("end_stage", if (settings.voice) "tts" else "intent")
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
                if (e.text.isNotBlank()) {
                    answer = forDisplay(e.text)
                    buzz()
                }
                conversationId = if (closing) null else e.conversationId
            }
            is AssistEvent.Audio -> if (settings.voice) play(assistAudioUrl(Session(context).url.orEmpty(), e.path))
            is AssistEvent.Failed -> {
                notice = if (e.noMatch) "I didn't understand that." else e.message
                state = AssistState.Idle
                closeSubscription()
            }
            AssistEvent.Done -> {
                if (state == AssistState.Thinking) finishedAnswer()
                closeSubscription()
            }
            else -> Unit
        }
    }

    /** The answer is on screen and nothing is speaking: idle, or (if asked to) listening again for a follow-up. */
    private fun finishedAnswer() {
        state = AssistState.Idle
        // With no follow-up to listen for, the result stays for a few seconds and then the screen goes back to idle, ready for a new question
        // (the conversation carries on underneath, so a later question is still a follow-up).
        if (!(settings.follow && answer.isNotBlank())) rest(3500)
        if (settings.follow && answer.isNotBlank()) main.postDelayed({
            if (state == AssistState.Idle && speech.available && speech.hasPermission()) {
                live = ""
                state = AssistState.Listening
                speech.start()
            }
        }, 600)
    }

    /** Clears the result from the screen after [ms] if nothing has started since. */
    private var restTask: Runnable? = null

    private fun rest(ms: Long) {
        restTask?.let { main.removeCallbacks(it) }
        val task = Runnable {
            if (state == AssistState.Idle) {
                heard = ""
                answer = ""
                notice = ""
                card = null
                picture = null
            }
        }
        restTask = task
        main.postDelayed(task, ms)
    }

    private fun buzz() {
        if (!settings.buzz) return
        runCatching {
            val v = context.getSystemService(Vibrator::class.java)
            v?.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    private fun closeSubscription() {
        if (subscription >= 0) WatchHa.api.close(subscription)
        subscription = -1
    }

    private fun play(url: String) {
        stopAudio()
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
            finishedAnswer()
        }
        runCatching { p.release() }
    }

    private fun stopAudio() {
        val p = player ?: return
        player = null
        runCatching { p.stop() }
        runCatching { p.release() }
    }

    /** Stops listening and speaking; what is on screen stays. */
    fun stop() {
        closeSubscription()
        stopAudio()
        speech.stop()
        state = AssistState.Idle
        live = ""
    }

    /** Clears the conversation from the screen. */
    fun clear() {
        stop()
        heard = ""
        answer = ""
        notice = ""
        card = null
        picture = null
        conversationId = null
    }
}
