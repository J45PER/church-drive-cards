package com.churchdrive.app.ui

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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
 * The voice assistant's brain, drawn by AssistScreen. Two ways to talk to it:
 *  - tap the microphone: the phone's own recogniser turns the speech into text (shown as it is heard, inside the app, no pop-up),
 *    which goes to the house's Assist;
 *  - say the wake word (the one on the house's Assist pipeline, such as "Hey Jarvis"): the microphone is streamed to Home Assistant,
 *    which hears the wake word and then does the speech-to-text itself, exactly as a Voice Assistant satellite does.
 * Either way anything Home Assistant can do by voice works (lights, heating, the alarm, lists, your own automations) and the answer
 * is shown and spoken in the voice of the house's pipeline. Must be made, used and released on the main thread.
 */
/** How long the house waits in silence before it decides you have finished speaking (its own default is 0.7 s; every second here is a second added to every answer). */
private const val SILENCE_SECONDS = 0.9

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

    /** The wake word id on the house's preferred pipeline (such as `hey_jarvis`), or null if it has none (or isn't known yet). */
    var wakeWord by mutableStateOf<String?>(null)
        private set

    /** The assistant's name: the house's own (Church Drive > Configure > Assistant name), else the wake word's, or empty. */
    val name: String get() = AssistantBrand.name.ifBlank { assistantName(wakeWord) }

    val messages = mutableStateListOf<AssistMessage>()

    /** Called when the wake word is heard, so the screen can open. */
    var onWake: (() -> Unit)? = null

    private val main = Handler(Looper.getMainLooper())
    private var conversationId: String? = null
    private var recogniser: SpeechRecognizer? = null
    private var player: MediaPlayer? = null
    private var subscription = -1
    private var answered = false
    private var said = ""
    private var times = AssistTimes()

    private fun mark(update: (AssistTimes) -> AssistTimes) {
        if (speakingIntro) return // the introduction isn't an answer to time
        times = update(times)
        AssistWakeStatus.timing = formatTimings(times)
    }

    /** Keep the voice live: after each spoken answer, listen again for a follow-up (set while the assistant is on screen). */
    var followUp = false
    private var continueAfter = false
    private var speakingIntro = false

    /** The question in this run was typed (or tapped), so the voice does not start listening again afterwards. */
    private var typedRun = false

    /** What was just said ends the conversation ("that's it"): after the answer, close instead of listening again. */
    private var closing = false

    /** Set once a dismissed conversation's last words have been spoken: the screen then closes itself, with its usual exit. */
    var closeRequested by mutableStateOf(false)
        private set
    private var autoListening = false

    /** Quiet follow-up listens already retried for this answer: he asked "anything else?", so give the person a second go before closing. */
    private var autoRetries = 0

    // The wake word loop.
    private var wakeEnabled = false
    private var panelOpen = false
    private var mic: MicStream? = null
    private var handlerId: Int? = null
    private var audioEnded = false

    /** This person's Home Assistant user id, to know which pictures are theirs. */
    private var userId: String? = null

    /** This person's name in Home Assistant (to find their own time zone helper there). */
    private var personName: String? = null
    private var showSubscription = -1

    /** A picture or link Clive asked to show, waiting for his written answer (it is sent just before it). */
    private var pendingShow: AssistantShow? = null

    /** Listens for the pictures and links Clive shows with his answers (see `clive_show`). */
    private fun watchShow(fresh: Boolean = false) {
        // The house drops every subscription when the line to it drops (the phone slept, the network changed), so a run starts it afresh.
        if (showSubscription >= 0) { if (!fresh) return; api.close(showSubscription); showSubscription = -1 }
        if (userId == null) api.request("auth/current_user", JSONObject()) { r ->
            val me = r as? JSONObject
            userId = me?.optString("id")?.takeIf { it.isNotBlank() }
            personName = me?.optString("name")?.takeIf { it.isNotBlank() }
            main.post { TimeZoneSync.sync(api, personName) }
        }
        // Tells the house which time zone this phone is in (only when it has changed), so the assistant can give the time here as well as at home.
        TimeZoneSync.sync(api, personName)
        showSubscription = api.subscribe("subscribe_events", JSONObject().put("event_type", ASSISTANT_SHOW_EVENT)) { ev ->
            main.post {
                val show = parseAssistantShow(ev, userId) ?: return@post
                val last = messages.lastOrNull()
                if (answered && last != null && !last.fromUser) messages[messages.lastIndex] = last.copy(show = show) else pendingShow = show
            }
        }
    }

    /** Asks the house what the assistant is called (its pipeline's wake word). */
    fun loadName() {
        watchShow()
        // The house's own answer (anyone may ask it); Home Assistant's pipeline list is for administrators only.
        api.request("church_drive/assist/info", JSONObject()) { r ->
            if (r is JSONObject) {
                val word = if (r.isNull("wake_word")) null else r.optString("wake_word").takeIf { it.isNotBlank() }
                main.post {
                    wakeWord = word
                    AssistantBrand.update(context, r.optString("name"), wakePhrase(word))
                }
            } else {
                api.request("assist_pipeline/pipeline/list", JSONObject()) { list -> main.post { wakeWord = preferredWakeWord(list) } }
            }
        }
    }

    /** Starts a fresh conversation: what was said, and what the house remembers of it, is cleared. */
    fun newChat() {
        stop()
        messages.clear()
        conversationId = null
        said = ""
        answered = false
        closing = false
        closeRequested = false
        notice = ""
        notUnderstood = null
        partial = ""
    }

    /** Shows a message to the person (such as the microphone not being allowed). */
    fun say(text: String) {
        notice = text
    }

    // ---- listening by tapping the microphone ----

    /** Starts listening (or stops, if it already is). [auto] is for a follow-up the person didn't ask for: silence then isn't an error. */
    fun listen(auto: Boolean = false) {
        typedRun = false
        autoListening = auto
        closeRequested = false
        if (state == AssistState.Listening) {
            recogniser?.stopListening()
            return
        }
        stopSpeaking()
        stopVoiceRun()
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
            // Give the phone's recogniser more patience before it decides you have finished (it treats these as hints).
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1_400L)
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1_200L)
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1_500L)
        partial = ""
        notice = ""
        notUnderstood = null
        state = AssistState.Listening
        r.startListening(intent)
    }

    /** Sends a sentence (spoken or typed) to the house's Assist and shows and speaks the answer. */
    fun send(text: String, typed: Boolean = false) {
        val sentence = text.trim()
        if (sentence.isEmpty()) return
        typedRun = typed
        closeRequested = false
        stopSpeaking()
        stopVoiceRun()
        messages += AssistMessage(true, sentence)
        pendingShow = null
        watchShow(fresh = true)
        said = sentence
        closing = isDismissal(sentence)
        times = AssistTimes()
        mark { it.copy(start = SystemClock.elapsedRealtime()) }
        autoRetries = 0
        partial = ""
        notice = ""
        notUnderstood = null
        answered = false
        continueAfter = false
        speakingIntro = false
        state = AssistState.Thinking
        val params = JSONObject()
            .put("start_stage", "intent")
            .put("end_stage", "tts")
            .put("input", JSONObject().put("text", sentence))
            .put("timeout", 30)
        conversationId?.let { params.put("conversation_id", it) }
        subscription = api.subscribe("assist_pipeline/run", params) { event -> main.post { onEvent(event) } }
        if (subscription < 0) {
            state = AssistState.Idle
            notice = "Can't reach the house right now."
        }
    }

    /** Says [text] aloud in the voice of the house's Assist pipeline (without adding it to the conversation). Tries again while the line to the house comes up. */
    fun speak(text: String, attempt: Int = 0) {
        if (text.isBlank()) return
        stopSpeaking()
        stopVoiceRun()
        speakingIntro = true
        val params = JSONObject()
            .put("start_stage", "tts")
            .put("end_stage", "tts")
            .put("input", JSONObject().put("text", forSpeech(text)))
            .put("timeout", 30)
        subscription = api.subscribe("assist_pipeline/run", params) { event -> main.post { onEvent(event) } }
        if (subscription < 0 && attempt < 10) main.postDelayed({ speak(text, attempt + 1) }, 1_000)
    }

    // ---- listening for the wake word ----

    /** Starts listening for the wake word, with nothing on screen, until [stopWake]. Needs the microphone permission. */
    fun startWake() {
        wakeEnabled = true
        beginWakeRun()
    }

    fun stopWake() {
        wakeEnabled = false
        stopVoiceRun()
        if (state == AssistState.Waiting) state = AssistState.Idle
    }

    /** The screen is showing (after the wake word): don't start another wake run until it is [panelClosed]. */
    fun panelOpened() {
        closeRequested = false
        panelOpen = true
        followUp = true
        // Open, he listens for the wake word again once he is idle (the host switches the wake word on while he is open).
        if (wakeEnabled && state == AssistState.Idle) main.postDelayed({ beginWakeRun() }, 400)
    }

    fun panelClosed() {
        panelOpen = false
        followUp = false
        stop()
        if (wakeEnabled) beginWakeRun()
    }

    private fun beginWakeRun() {
        if (!wakeEnabled || subscription >= 0) return
        if (state != AssistState.Idle && state != AssistState.Waiting) return
        val params = JSONObject()
            .put("start_stage", "wake_word")
            .put("end_stage", "tts")
            // Home Assistant ends your sentence after 0.7 seconds of quiet by default, which cuts people off mid-thought: wait longer.
            .put("input", JSONObject().put("sample_rate", MicStream.RATE).put("silence_seconds", SILENCE_SECONDS))
            .put("timeout", 300)
        conversationId?.let { params.put("conversation_id", it) }
        answered = false
        said = ""
        watchShow(fresh = true)
        subscription = api.subscribe("assist_pipeline/run", params) { event -> main.post { onEvent(event) } }
        if (subscription < 0) {
            // The house isn't reachable: try again shortly.
            AssistWakeStatus.problem = "Can't reach the house"
            main.postDelayed({ beginWakeRun() }, 5_000)
            return
        }
        state = AssistState.Waiting
    }

    private fun startMic() {
        val id = handlerId ?: return
        audioEnded = false
        val m = MicStream({ buf, n -> if (!audioEnded) api.sendAudio(id, buf, n) }, { l -> main.post { level = if (state == AssistState.Listening) l else 0f } })
        if (m.start()) {
            mic = m
        } else {
            notice = "Allow the microphone to use the wake word."
            AssistWakeStatus.problem = "The microphone isn't allowed"
            stopWake()
        }
    }

    /** No more audio is wanted: tell the pipeline the speech is over and let go of the microphone. */
    private fun endAudio() {
        if (audioEnded) return
        audioEnded = true
        handlerId?.let { api.sendAudio(it, ByteArray(0), 0) }
        mic?.stop()
        mic = null
    }

    private fun stopVoiceRun() {
        mic?.stop()
        mic = null
        audioEnded = true
        handlerId = null
        closeRun()
    }

    // ---- what the house says back ----

    private fun onEvent(event: JSONObject) {
        when (val e = parseAssistEvent(event)) {
            is AssistEvent.Started -> {
                note("run-start")
                // (Copied to a local: AssistEvent is in the shared core module, where a smart cast on its property is not allowed.)
                val started = e.handlerId
                if (started != null && started > 0) {
                    AssistWakeStatus.problem = ""
                    handlerId = started
                    startMic()
                }
            }
            is AssistEvent.Woke -> if (e.word != null) {
                note("wake word heard")
                partial = ""
                notice = ""
                notUnderstood = null
                state = AssistState.Listening
                onWake?.invoke()
            }
            AssistEvent.SpeechEnded -> {
                times = AssistTimes()
                mark { it.copy(start = SystemClock.elapsedRealtime()) }
                endAudio()
                if (state == AssistState.Listening) state = AssistState.Thinking
            }
            is AssistEvent.Heard -> {
                mark { it.copy(heard = SystemClock.elapsedRealtime()) }
                endAudio()
                if (e.text.isNotBlank()) {
                    said = e.text
                    typedRun = false
                    closing = isDismissal(e.text)
                    messages += AssistMessage(true, e.text)
                    pendingShow = null
                    state = AssistState.Thinking
                }
            }
            is AssistEvent.Answer -> {
                note("answer")
                mark { it.copy(answer = SystemClock.elapsedRealtime()) }
                if (e.conversationId != null) conversationId = e.conversationId
                answered = true
                continueAfter = e.continueConversation
                if (e.text.isNotBlank()) messages += AssistMessage(false, forDisplay(e.text), pendingShow)
                pendingShow = null
                if (isNotUnderstood(e.text)) notUnderstood = said
            }
            is AssistEvent.Audio -> if (AssistVoiceSetting.on) {
                mark { it.copy(audio = SystemClock.elapsedRealtime()) }
                play(assistAudioUrl(base, e.path))
            }
            is AssistEvent.Failed -> onFailed(e)
            AssistEvent.Done -> {
                stopVoiceRun()
                if (state == AssistState.Thinking || state == AssistState.Listening) state = AssistState.Idle
                if (state == AssistState.Idle || state == AssistState.Waiting) resumeWake()
                if (closing && state == AssistState.Idle && player == null) dismiss()
            }
            null -> Unit
        }
    }

    private fun onFailed(e: AssistEvent.Failed) {
        note("error ${e.code}")
        // The house stops a wake-word run every so often when nothing was said; that is only a quiet restart, so he stays "waiting"
        // (the settings row and the top bar's microphone would otherwise flicker for the moment between the two runs).
        val quietRestart = (e.code == "wake-word-timeout" || e.code == "timeout") && wakeEnabled && state == AssistState.Waiting
        stopVoiceRun()
        when {
            e.code == "wake-word-timeout" || e.code == "timeout" -> Unit // nothing was said for a while: just listen again
            wakeEnabled && !panelOpen && state == AssistState.Waiting -> AssistWakeStatus.problem = e.message.ifBlank { e.code }
            e.code == "stt-no-text-recognized" -> notice = "I didn't catch that."
            !answered -> {
                messages += AssistMessage(false, if (e.noMatch) "Sorry, I didn't understand that." else e.message)
                if (e.noMatch) notUnderstood = said
            }
            else -> notice = "The house answered, but couldn't speak it."
        }
        if (state != AssistState.Speaking && !quietRestart) state = AssistState.Idle
        resumeWake()
    }

    /** After a run (and its speech) is over, start listening for the wake word again, if that is switched on and nothing is on screen. */
    private fun resumeWake() {
        if (wakeEnabled && state != AssistState.Speaking) main.postDelayed({ beginWakeRun() }, 400)
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
            p.setOnPreparedListener { it.start(); mark { t -> t.copy(playing = SystemClock.elapsedRealtime()) } }
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
            resumeWake()
            // Keep the voice live: after an answer (not the introduction), listen again for a follow-up.
            if (closing) dismiss()
            else if ((followUp || continueAfter) && !speakingIntro && !typedRun) {
                // He asked something ("anything else?"): listen straight away, even if the screen was closed meanwhile.
                autoRetries = 0
                main.postDelayed({ if ((followUp || continueAfter) && !typedRun && state == AssistState.Idle && !closing) listen(auto = true) }, 150)
            }
        }
        runCatching { p.release() }
    }

    private fun dismiss() {
        closing = false
        followUp = false
        closeRequested = true
    }

    /** A short note for the diagnostics line in Settings (the last few things the voice pipeline did). */
    private fun note(text: String) {
        AssistWakeStatus.log.add(text)
        while (AssistWakeStatus.log.size > 8) AssistWakeStatus.log.removeAt(0)
    }

    /** Stops the voice. */
    fun stopSpeaking() {
        player?.let { runCatching { it.stop() }; runCatching { it.release() } }
        player = null
        if (state == AssistState.Speaking) state = AssistState.Idle
    }

    fun stop() {
        recogniser?.cancel()
        stopSpeaking()
        stopVoiceRun()
        if (state != AssistState.Thinking) state = AssistState.Idle
        partial = ""
        level = 0f
    }

    private fun closeRun() {
        if (subscription >= 0) api.close(subscription)
        subscription = -1
    }

    /** Frees the recogniser, the player, the microphone and the line to the house. */
    fun release() {
        wakeEnabled = false
        followUp = false
        if (showSubscription >= 0) api.close(showSubscription)
        showSubscription = -1
        stopVoiceRun()
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
            // The phone's recogniser often gives up before the person has started to answer, or is still busy letting go of the last
            // listen: that isn't the end of the conversation. Listen once more before closing.
            val quiet = error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
                error == SpeechRecognizer.ERROR_CLIENT || error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY
            if (autoListening && quiet && autoRetries < 1 && followUp && !closing) {
                autoRetries++
                main.postDelayed({ if (followUp && state == AssistState.Idle && !closing) listen(auto = true) }, 250)
                return
            }
            resumeWake()
            // Silence after a follow-up we started ourselves isn't a problem: the conversation is simply over.
            // (The same when the microphone isn't allowed: the person typed, so nothing was asked of it.)
            if (autoListening && (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT || error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)) {
                followUp = false
                return
            }
            notice = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "I didn't hear anything. Tap the microphone to try again."
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Allow the microphone to talk to the house."
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "The speech recogniser needs a connection."
                else -> "I couldn't listen. Tap the microphone to try again."
            }
        }
        override fun onResults(results: Bundle?) {
            level = 0f
            val heard = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            if (heard.isBlank()) {
                state = AssistState.Idle
                notice = "I didn't hear anything. Tap the microphone to try again."
            } else {
                send(heard)
            }
        }
        override fun onPartialResults(partialResults: Bundle?) {
            partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
        }
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }
}
