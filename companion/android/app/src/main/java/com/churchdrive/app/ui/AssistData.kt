package com.churchdrive.app.ui

import org.json.JSONObject

/** What the voice assistant is doing now. */
enum class AssistState { Idle, Listening, Thinking, Speaking }

/** One line of the conversation: what the person said, or what the house answered. */
data class AssistMessage(val fromUser: Boolean, val text: String)

/** What one event of Home Assistant's `assist_pipeline/run` means for the screen. */
sealed interface AssistEvent {
    /** The house's written answer. */
    data class Answer(val text: String, val conversationId: String?) : AssistEvent

    /** The spoken answer is ready to play at [path] (an address on the house). */
    data class Audio(val path: String) : AssistEvent

    /** The run failed; [message] is what to show, [noMatch] when the house simply didn't understand the sentence. */
    data class Failed(val message: String, val noMatch: Boolean) : AssistEvent

    /** The run is over. */
    data object Done : AssistEvent
}

/** The meaning of one pipeline event, or null for the ones the screen doesn't need (run-start, intent-start, tts-start...). */
fun parseAssistEvent(event: JSONObject): AssistEvent? {
    val data = event.optJSONObject("data")
    return when (event.optString("type")) {
        "intent-end" -> {
            val out = data?.optJSONObject("intent_output")
            val speech = out?.optJSONObject("response")?.optJSONObject("speech")?.optJSONObject("plain")?.optString("speech").orEmpty()
            AssistEvent.Answer(speech, out?.optString("conversation_id")?.takeIf { it.isNotBlank() })
        }
        "tts-end" -> data?.optJSONObject("tts_output")?.optString("url")?.takeIf { it.isNotBlank() }?.let { AssistEvent.Audio(it) }
        "error" -> {
            val code = data?.optString("code").orEmpty()
            AssistEvent.Failed(data?.optString("message").orEmpty().ifBlank { "That didn't work." }, code == "intent-failed" || code.contains("no_intent_match"))
        }
        "run-end" -> AssistEvent.Done
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

/** Whether the introduction is due: the first time the assistant opens after a new version. */
fun introDue(seenVersion: String?, current: String): Boolean = seenVersion != current

/** One thing the assistant can do, shown in the introduction and as an example sentence. */
data class AssistFeature(val icon: String, val title: String, val example: String)

/**
 * What is new in the assistant's introduction. Add the new things of each release to the top, and drop the oldest when it
 * is long; the introduction shows again once for every new version of the app.
 */
val ASSIST_FEATURES = listOf(
    AssistFeature("mdi:cart-outline", "Shopping lists", "Add washing powder to the Lidl list"),
    AssistFeature("mdi:map-marker-outline", "Places on a map", "Where is the shop on my list?"),
    AssistFeature("mdi:chart-bar", "Last 7 days of energy", "How much did we spend this week?"),
    AssistFeature("mdi:lightbulb-outline", "Lights, heating and the alarm", "Turn the lounge lights down"),
)

/** Sentences to try, shown under the microphone. */
val ASSIST_SUGGESTIONS = listOf("Who is home?", "Turn off all the lights", "Any doors open?", "Good night")

/** What the screen says for each state. */
fun assistStatus(state: AssistState): String = when (state) {
    AssistState.Idle -> "Tap the microphone to talk"
    AssistState.Listening -> "Listening"
    AssistState.Thinking -> "Thinking"
    AssistState.Speaking -> "Speaking"
}
