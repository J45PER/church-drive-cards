package com.churchdrive.app.ui

import org.json.JSONObject

/** What the voice assistant is doing now. */
/** [Waiting] is listening for the wake word, with nothing on screen; the others are as they say. */
enum class AssistState { Idle, Waiting, Listening, Thinking, Speaking }

/** One line of the conversation: what the person said, or what the house answered. */
data class AssistMessage(val fromUser: Boolean, val text: String)

/** What one event of Home Assistant's `assist_pipeline/run` means for the screen. */
sealed interface AssistEvent {
    /** The house's written answer. */
    data class Answer(val text: String, val conversationId: String?, val continueConversation: Boolean = false) : AssistEvent

    /** The spoken answer is ready to play at [path] (an address on the house). */
    data class Audio(val path: String) : AssistEvent

    /** The run failed; [message] is what to show, [noMatch] when the house simply didn't understand the sentence. */
    data class Failed(val message: String, val noMatch: Boolean, val code: String = "") : AssistEvent

    /** The run is over. */
    data object Done : AssistEvent

    /** The run has started; microphone audio goes to the pipeline under [handlerId]. */
    data class Started(val handlerId: Int?) : AssistEvent

    /** The wake word was heard (null [word] means it timed out without one). */
    data class Woke(val word: String?) : AssistEvent

    /** The house's own speech-to-text heard [text]. */
    data class Heard(val text: String) : AssistEvent

    /** The person has stopped speaking: no more audio is wanted. */
    data object SpeechEnded : AssistEvent
}

/** The meaning of one pipeline event, or null for the ones the screen doesn't need (run-start, intent-start, tts-start...). */
fun parseAssistEvent(event: JSONObject): AssistEvent? {
    val data = event.optJSONObject("data")
    return when (event.optString("type")) {
        "intent-end" -> {
            val out = data?.optJSONObject("intent_output")
            val speech = out?.optJSONObject("response")?.optJSONObject("speech")?.optJSONObject("plain")?.optString("speech").orEmpty()
            AssistEvent.Answer(speech, out?.optString("conversation_id")?.takeIf { it.isNotBlank() }, out?.optBoolean("continue_conversation", false) ?: false)
        }
        "tts-end" -> data?.optJSONObject("tts_output")?.optString("url")?.takeIf { it.isNotBlank() }?.let { AssistEvent.Audio(it) }
        "error" -> {
            val code = data?.optString("code").orEmpty()
            AssistEvent.Failed(data?.optString("message").orEmpty().ifBlank { "That didn't work." }, code == "intent-failed" || code.contains("no_intent_match"), code)
        }
        "run-end" -> AssistEvent.Done
        "run-start" -> AssistEvent.Started(data?.optJSONObject("runner_data")?.let { if (it.isNull("stt_binary_handler_id")) null else it.optInt("stt_binary_handler_id") })
        "wake_word-end" -> AssistEvent.Woke(data?.optJSONObject("wake_word_output")?.optString("wake_word_id")?.takeIf { it.isNotBlank() })
        "stt-end" -> AssistEvent.Heard(data?.optJSONObject("stt_output")?.optString("text").orEmpty())
        "stt-vad-end" -> AssistEvent.SpeechEnded
        else -> null
    }
}

/** The address of a spoken answer: the house's address and the path it gave (which may already be a whole address). */
fun assistAudioUrl(base: String, path: String): String =
    if (path.startsWith("http://") || path.startsWith("https://")) path else base.trimEnd('/') + "/" + path.trimStart('/')

/** A sentence the house answered "I didn't understand" to, as a failure of the sentence and not of the line. */
fun isNotUnderstood(answer: String): Boolean {
    val a = answer.lowercase()
    return "didn't understand" in a || "sorry, i" in a && "understand" in a || "couldn't understand" in a
}

/** Which introduction is due: [First] the first time ever (the assistant introduces itself), [Update] after a new version (what is new). */
enum class IntroKind { First, Update }

/** The introduction that is due, or null: never introduced is [IntroKind.First]; a version not seen yet is [IntroKind.Update]. */
fun introKind(introducedEver: Boolean, seenVersion: String?, current: String): IntroKind? = when {
    !introducedEver -> IntroKind.First
    seenVersion != current -> IntroKind.Update
    else -> null
}

/** Whether any introduction is due. */
fun introDue(seenVersion: String?, current: String): Boolean = seenVersion != current

/** One thing the assistant can do, shown in the introduction and as an example sentence. */
data class AssistFeature(val icon: String, val title: String, val example: String)

/**
 * What is new, newest first, shown (and spoken) in the introduction. Add the new things of each release to the top and drop the
 * oldest when it is long; the introduction comes again once for every new version of the app.
 */
val ASSIST_FEATURES = listOf(
    AssistFeature("mdi:microphone-message", "Hands-free with the wake word", "Hey Jarvis, who is home?"),
    AssistFeature("mdi:chat-processing-outline", "Natural conversation", "Is it going to rain tomorrow?"),
    AssistFeature("mdi:cart-outline", "Shopping lists", "Add washing powder to the Lidl list"),
    AssistFeature("mdi:lightbulb-outline", "Lights, heating and the alarm", "Turn the lounge lights down"),
)

/** "A, B and C". */
fun spokenList(items: List<String>): String = when (items.size) {
    0 -> ""
    1 -> items[0]
    else -> items.dropLast(1).joinToString(", ") + " and " + items.last()
}

/**
 * What the assistant says out loud in the introduction. The first time: who it is and what it can do, then what is new. After an
 * update: just what is new.
 */
fun introSpeech(kind: IntroKind, person: String, assistant: String, features: List<AssistFeature>): String {
    val who = assistant.ifBlank { "your house assistant" }
    val news = spokenList(features.take(4).map { it.title.replaceFirstChar { c -> c.lowercase() } })
    val name = person.trim().substringBefore(' ')
    return when (kind) {
        IntroKind.First ->
            "Hello${if (name.isNotEmpty()) " $name" else ""}. I'm $who. I can run anything Home Assistant can: the lights, the heating, the cleaning, the alarm, your lists, and your own automations. " +
                "Here's what I can do for you now: $news. Just ask whenever you need me."
        IntroKind.Update ->
            "Welcome back${if (name.isNotEmpty()) " $name" else ""}. Here's what's new: $news."
    }
}

/** Sentences to try, shown under the microphone. */
val ASSIST_SUGGESTIONS = listOf("Who is home?", "Turn off all the lights", "Any doors open?", "Good night")

/**
 * The assistant's name from its wake word, as Home Assistant's pipeline names it: "hey_jarvis" is Jarvis, "okay_nabu" is Nabu.
 * Empty when there is no wake word, so the assistant is just "your house assistant".
 */
fun assistantName(wakeWordId: String?): String {
    val skip = setOf("hey", "hi", "ok", "okay", "oh")
    val words = wakeWordId.orEmpty().split('_', '-', ' ').filter { it.isNotBlank() }
    val rest = words.dropWhile { it.lowercase() in skip }.ifEmpty { words }
    return rest.joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
}

/** What to say to wake it: "hey_jarvis" is "Hey Jarvis". */
fun wakePhrase(wakeWordId: String?): String =
    wakeWordId.orEmpty().split('_', '-', ' ').filter { it.isNotBlank() }.joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }

/** The preferred pipeline's wake word id from `assist_pipeline/pipeline/list`, or null when it has none. */
fun preferredWakeWord(result: Any?): String? {
    val o = result as? JSONObject ?: return null
    val preferred = o.optString("preferred_pipeline")
    val list = o.optJSONArray("pipelines") ?: return null
    for (i in 0 until list.length()) {
        val p = list.optJSONObject(i) ?: continue
        if (p.optString("id") == preferred) return p.optString("wake_word_id").takeIf { it.isNotBlank() && !p.isNull("wake_word_id") }
    }
    return null
}

/** What the screen says for each state. */
fun assistStatus(state: AssistState): String = when (state) {
    AssistState.Idle -> "Tap the microphone to talk"
    AssistState.Waiting -> "Waiting for the wake word"
    AssistState.Listening -> "Listening"
    AssistState.Thinking -> "Thinking"
    AssistState.Speaking -> "Speaking"
}


private val DISMISS_PHRASES = listOf(
    "that's it", "that is it", "that's all", "that is all", "that will be all", "that'll be all", "that's everything",
    "no thanks", "no thank you", "nothing else", "nothing more", "i'm done", "i am done", "we're done",
    "never mind", "nevermind", "goodbye", "good bye", "stop listening", "go away",
)
private val DISMISS_WORDS = setOf("no", "nope", "nah", "stop", "cancel", "bye", "thanks", "thank you", "cheers", "ok thanks", "okay thanks", "ok thank you")

/** Whether what was said ends the conversation ("that's it", "no thanks", "bye"): the assistant then answers and closes instead of listening again. */
fun isDismissal(text: String): Boolean {
    val t = text.lowercase().replace('\u2019', '\'').replace(Regex("[^a-z' ]"), " ").replace(Regex("\\s+"), " ").trim()
        .removePrefix("jarvis ").removeSuffix(" jarvis").removeSuffix(" please").trim()
    if (t.isEmpty() || t.split(' ').size > 8) return false
    return t in DISMISS_WORDS || DISMISS_PHRASES.any { it in t }
}


/** Names the voice says wrongly, and what to write instead so it says them right ("Die Ann" for Diane). */
private val NAME_RESPELLINGS = listOf("Diane" to "Die Ann")

/** [text] with names respelled so the voice pronounces them properly. */
fun forSpeech(text: String): String =
    NAME_RESPELLINGS.fold(text) { t, (name, spoken) -> t.replace(Regex("\\b$name\\b", RegexOption.IGNORE_CASE), spoken) }

/** [text] with the respellings turned back into the real names, for showing on screen. */
fun forDisplay(text: String): String =
    NAME_RESPELLINGS.fold(text) { t, (name, spoken) -> t.replace(Regex("\\b" + spoken.replace(" ", "[ -]") + "\\b", RegexOption.IGNORE_CASE), name) }
