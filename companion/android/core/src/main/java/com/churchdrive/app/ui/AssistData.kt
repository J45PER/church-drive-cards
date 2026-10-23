package com.churchdrive.app.ui

import org.json.JSONObject

/** What the voice assistant is doing now. */
/** [Waiting] is listening for the wake word, with nothing on screen; the others are as they say. */
enum class AssistState { Idle, Waiting, Listening, Thinking, Speaking }

/** One line of the conversation: what the person said, or what the house answered. */
data class AssistMessage(val fromUser: Boolean, val text: String, val show: AssistantShow? = null)

/** A picture and/or a link Clive wants to show with his answer (the house's `church_drive_assistant_show` event, fired by the `clive_show` script). */
data class AssistantShow(val image: String?, val link: String?, val linkLabel: String?, val card: AssistantCard? = null)

/**
 * A card for a screen that can draw one (the watch): the weather, a score or fixture (with club badges), what is playing. The house's
 * `clive_show_card` script fires it in the same event as `card` ({kind, ...fields}); [fields] keeps only what is safe to use (every
 * address is https).
 */
data class AssistantCard(val kind: String, val fields: org.json.JSONObject)

/** The kinds of card the screens know how to draw. */
val ASSISTANT_CARD_KINDS = setOf(
    "weather", "score", "scores", "fixture", "fixtures", "music", "people", "done", "car", "climate", "gregg", "air",
    // Cards built from one shared layout (a big figure, a title, rows, a footer): the house sends `big`, `title`, `sub`, `pill`, `rows` and `foot`.
    "energy", "secure", "doors", "tasks", "devices", "lights", "heat", "shop", "event", "reminder", "media", "answer",
)

/** The Material icon for a card's weather symbol ("partlycloudy" is the partly cloudy sun and cloud). */
fun weatherIconName(symbol: String): String = when (symbol.lowercase().replace('_', '-')) {
    "sunny", "clear", "clear-day" -> "mdi:weather-sunny"
    "cloudy" -> "mdi:weather-cloudy"
    "rainy", "rain" -> "mdi:weather-rainy"
    "pouring" -> "mdi:weather-pouring"
    "snowy", "snow" -> "mdi:weather-snowy"
    "lightning", "lightning-rainy" -> "mdi:weather-lightning"
    "fog", "foggy" -> "mdi:weather-fog"
    "night", "clear-night" -> "mdi:weather-night"
    else -> "mdi:weather-partly-cloudy"
}

/** The icon for a sport (the card's `sport` field): a football when it is not named, a tennis ball for tennis, a golf club for golf. */
fun sportIcon(sport: String): String = when (sport.trim().lowercase()) {
    "tennis" -> "mdi:tennis-ball"
    "golf" -> "mdi:golf"
    "cricket" -> "mdi:cricket"
    "rugby", "rugby union", "rugby league" -> "mdi:rugby"
    "basketball" -> "mdi:basketball"
    "baseball" -> "mdi:baseball"
    "hockey", "ice hockey" -> "mdi:hockey-puck"
    "american football", "nfl" -> "mdi:football"
    "f1", "formula 1", "formula one", "motorsport", "racing" -> "mdi:racing-helmet"
    "boxing" -> "mdi:boxing-glove"
    "darts" -> "mdi:bullseye-arrow"
    "cycling" -> "mdi:bike"
    "swimming" -> "mdi:swim"
    "athletics" -> "mdi:run-fast"
    "snooker", "pool" -> "mdi:billiards"
    else -> "mdi:soccer"
}

/**
 * The Material icon in the middle of a card (the icon the loading mark turns into). Sport cards show their sport; the rest show what they are
 * about. [cardBrand] names a logo that replaces it when the app has one.
 */
fun cardIcon(card: AssistantCard): String {
    val f = card.fields
    return when (card.kind) {
        "weather" -> weatherIconName(f.optString("symbol"))
        "score", "scores", "fixture", "fixtures" -> sportIcon(f.optString("sport"))
        "music" -> "mdi:music-note"
        "people" -> "mdi:account-multiple"
        "done" -> if (f.has("ok") && !f.optBoolean("ok", true)) "mdi:close-circle" else "mdi:check-circle"
        "car" -> "mdi:car-electric"
        "climate" -> "mdi:thermometer"
        "gregg" -> "mdi:robot-vacuum"
        "air" -> "mdi:air-filter"
        "energy" -> "mdi:lightning-bolt"
        "secure" -> "mdi:shield-check"
        "doors" -> "mdi:door"
        "tasks" -> "mdi:format-list-checks"
        "devices" -> "mdi:alert-circle"
        "lights" -> "mdi:lightbulb"
        "heat" -> "mdi:radiator"
        "shop" -> "mdi:cart"
        "event" -> "mdi:calendar-check"
        "reminder" -> "mdi:bell-ring"
        "media" -> "mdi:television-play"
        else -> "mdi:message-text"
    }
}

/** Competitions whose one-colour logo stands in for the sport icon, by the name the house gives (the logo is used only when the app has it). */
private val COMPETITION_LOGOS = linkedMapOf(
    "premier league" to "premierleague", "conference league" to "conferenceleague", "champions league" to "championsleague", "europa league" to "europaleague",
    "nations league" to "nationsleague", "world cup" to "fifa", "fa cup" to "facup", "carabao" to "carabaocup", "efl cup" to "carabaocup", "league cup" to "carabaocup",
    "scottish premiership" to "scottishpremiership", "european championship" to "europeanchampionship", "championship" to "eflleague", "league one" to "eflleague", "league two" to "eflleague", "efl" to "eflleague",
)

/** The name of a logo that stands in for a card's icon, or null: a music card's service (Spotify), a car's make (Vauxhall), a competition's logo. */
fun cardBrand(card: AssistantCard): String? {
    val f = card.fields
    return when (card.kind) {
        "music" -> f.optString("app").takeIf { it.isNotBlank() }
        "car" -> f.optString("brand").ifBlank { f.optString("name").trim().substringBefore(' ') }.takeIf { it.isNotBlank() }
        "energy" -> f.optString("brand").takeIf { it.isNotBlank() }
        "score", "scores", "fixture", "fixtures" -> f.optString("comp").trim().lowercase().let { c -> COMPETITION_LOGOS.entries.firstOrNull { c.contains(it.key) }?.value }
        else -> null
    }
}

/** One row of a list-style card: [name] and [detail] on the left, a [value] and/or a [pill] on the right, and a [marker] at the start (ok, warn, todo, done or dot). */
data class CardRow(val name: String, val detail: String, val value: String, val pill: String, val tone: String, val marker: String)

/** The rows of a list-style card ([JSONObject] `rows`), each tolerant of missing fields. */
fun cardRows(fields: JSONObject): List<CardRow> {
    val rows = fields.optJSONArray("rows") ?: return emptyList()
    return (0 until rows.length()).mapNotNull { i ->
        val r = rows.optJSONObject(i) ?: return@mapNotNull null
        CardRow(r.optString("name"), r.optString("detail"), r.optString("value"), r.optString("pill"), r.optString("tone"), r.optString("marker"))
    }
}

/** An https address, or null. */
fun httpsOnly(url: String?): String? = url?.trim()?.takeIf { it.startsWith("https://") }

/** The card in an event's data, made safe (any address that is not https is dropped), or null when there isn't a drawable one. */
fun parseAssistantCard(card: JSONObject?): AssistantCard? {
    val kind = card?.optString("kind")?.lowercase().orEmpty()
    if (card == null || kind !in ASSISTANT_CARD_KINDS) return null
    fun clean(o: JSONObject): JSONObject {
        val out = JSONObject()
        for (k in o.keys()) {
            val v = o.opt(k)
            when {
                v is JSONObject -> out.put(k, clean(v))
                // A list (a table's rows): each object in it cleaned the same way.
                v is org.json.JSONArray -> out.put(k, org.json.JSONArray().also { a -> for (i in 0 until v.length()) a.put(v.opt(i).let { e -> if (e is JSONObject) clean(e) else e }) })
                v is String && (k.endsWith("_url") || k == "badge" || k == "image") -> httpsOnly(v)?.let { out.put(k, it) }
                // Names written the way the voice says them ("Die Ann") are shown as they are really spelt.
                v is String -> out.put(k, forDisplay(v))
                v != null && v != JSONObject.NULL -> out.put(k, v)
            }
        }
        return out
    }
    return AssistantCard(kind, clean(card))
}

/** The event name the `clive_show` script fires. */
const val ASSISTANT_SHOW_EVENT = "church_drive_assistant_show"

/**
 * What a `church_drive_assistant_show` event asks this screen to show, or null when it is for someone else or has nothing safe to show.
 * [userId] is this person's Home Assistant user id: the script runs as the person who asked, so the event carries their id, and
 * a phone shows only its own person's pictures. Only https addresses are accepted.
 */
fun parseAssistantShow(event: JSONObject, userId: String?): AssistantShow? {
    val from = event.optJSONObject("context")?.optString("user_id").orEmpty()
    if (userId.isNullOrBlank() || from != userId) return null
    val data = event.optJSONObject("data") ?: return null
    fun https(key: String) = data.optString(key).trim().takeIf { it.startsWith("https://") }
    val image = https("image_url")
    val link = https("link_url")
    val card = parseAssistantCard(data.optJSONObject("card"))
    if (image == null && link == null && card == null) return null
    return AssistantShow(image, link, data.optString("link_label").trim().takeIf { it.isNotEmpty() }, card)
}

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

/** What kind of entry the Clive log holds: something he can now do, something that works differently, or something that was put right. */
enum class AssistantChange { Feature, Change, Fix }

/**
 * One entry of the Clive log: a feature, change or fix. [id] never changes and is never reused: it is how the app remembers
 * that this person has been told. [example] is a sentence to try ("Say: ..."); [detail] says what changed when there is nothing to say.
 */
data class AssistFeature(
    val icon: String,
    val title: String,
    val example: String,
    val id: String = title,
    val detail: String = "",
    val kind: AssistantChange = AssistantChange.Feature,
)

/** What an introduction shows: which kind, and the log entries it lists. */
data class IntroDue(val kind: IntroKind, val features: List<AssistFeature>)

/** What to do about the introduction: what is due now (or null), and the ids the person has now been told about. */
data class IntroResult(val due: IntroDue?, val told: Set<String>)

/**
 * Decides the introduction from the Clive log, not from the app's version: the first time ever is the full introduction (what he can
 * do: the log's features); after that only log entries the person has not been told about, each once, and nothing when there are none.
 * [told] is null for someone introduced before the log existed: they are taken to know everything in it so far.
 */
fun introDue(introducedEver: Boolean, told: Set<String>?, log: List<AssistFeature>): IntroResult {
    val all = log.map { it.id }.toSet()
    if (!introducedEver) return IntroResult(IntroDue(IntroKind.First, log.filter { it.kind == AssistantChange.Feature }), all)
    if (told == null) return IntroResult(null, all)
    val fresh = log.filter { it.id !in told }
    return if (fresh.isEmpty()) IntroResult(null, told) else IntroResult(IntroDue(IntroKind.Update, fresh), told + all)
}

/**
 * The Clive log, newest first: every feature, change and fix to the assistant. Add an entry at the top whenever Clive gains or
 * changes something (a new, never-reused [AssistFeature.id]); each person is told about each entry once, and only the first time
 * ever do they get the full introduction. Features also make up that introduction's "what I can do".
 */
private val RAW_ASSIST_FEATURES = listOf(
    AssistFeature("mdi:calendar-month-outline", "A league's games", "What are the Championship games this weekend?", id = "league-fixtures", detail = "Asked for the games of a league, {name} now shows them as a list with each club's badge, the date and the kick off time, under the league's logo."),
    AssistFeature("mdi:shield-star-outline", "Logos on the cards", "", id = "card-logos", detail = "When {name} shows a score or fixture the card now carries the competition's logo (the Premier League, the EFL, the FA Cup, the Champions League, the Europa and Conference Leagues and more), Disney+ and the other streaming services show theirs, and cars show their make (Mercedes-Benz, Land Rover and Cupra added).", kind = AssistantChange.Change),
    AssistFeature("mdi:card-multiple-outline", "Cards that line up", "", id = "cards-in-place", detail = "Every card now has its icon in the same place at the same size: {name}'s thinking mark turns into it and the figures open out underneath. Sports cards show their sport, music shows its service's logo and the car its make's, and the watch has the same icons.", kind = AssistantChange.Change),
    AssistFeature("mdi:card-text-outline", "A card the moment you ask", "What's the weather like?", id = "card-while-thinking", detail = "As soon as you have asked, {name} shows an empty card with his thinking mark in the middle, and the answer's card appears in its place. The weather card now has its icon in the middle too.", kind = AssistantChange.Change),
    AssistFeature("mdi:robot-vacuum", "Cards for Gregg and the air", "What's the status of Gregg?", id = "gregg-air-cards", detail = "Ask how Gregg is and {name} shows his status icon, the battery and his last clean. Ask about the air quality and he shows the score, the particles and the purifier.", kind = AssistantChange.Change),
    AssistFeature("mdi:watch-variant", "The watch goes back to idle", "", id = "watch-rests", detail = "On the watch, a few seconds after an answer the screen clears, ready for a new question. If he is listening for a follow-up he goes straight to listening instead.", kind = AssistantChange.Fix),
    AssistFeature("mdi:thermometer", "A card for the house's climate", "What's the climate like in the house?", id = "climate-card", detail = "Asked about the temperature in the house, {name} now shows a card with each heating zone: its temperature, target, humidity and whether it is heating, idle, on Eco or off.", kind = AssistantChange.Change),
    AssistFeature("mdi:keyboard-off-outline", "Keyboard stays away", "", id = "keyboard-on-return", detail = "Leaving the app while the typing box was open no longer brings the keyboard back up over the page when you return.", kind = AssistantChange.Fix),
    AssistFeature("mdi:thermostat-auto", "Looks after the heating", "Resume the heating", id = "runs-the-heating", detail = "{name} now keeps the heating comfortable by himself: on when a room is cold, down or off when it is hot. If you change a thermostat yourself he leaves it alone for a couple of hours, tells you when he takes over again, and you can say \"resume the heating\" or tap Resume on the Home page or in his notification.", kind = AssistantChange.Change),
    AssistFeature("mdi:home-account", "Cards for the house", "Who's home?", id = "house-cards", detail = "{name} now answers who is home, how the car's charging, and what he has just done with a card in the house's colours: green for home, teal away at a place, red and orange for a low battery.", kind = AssistantChange.Change),
    AssistFeature("mdi:table", "Score tables", "What were the Champions League scores last night?", id = "score-tables", detail = "Asked for several scores at once, {name} now shows them as a table with the club badges, and keeps his words short.", kind = AssistantChange.Change),
    AssistFeature("mdi:card-text-outline", "Cards on screen", "What's the weather?", id = "watch-cards", detail = "On your watch, and in this app, {name} can answer with a card: the weather, a score with the club badges, the next game, a picture or what is playing, and his background takes on the colours of the answer."),
    AssistFeature("mdi:image-text", "Pictures and links", "Show me what a Nottingham Forest shirt looks like", id = "pictures-and-links", detail = "{name} can now show a picture or a link under his answer on this screen, when it helps.", kind = AssistantChange.Change),
    AssistFeature("mdi:account-voice", "A new name: Clive", "", id = "renamed-clive", detail = "Jarvis is now Clive, with a new logo. You can still wake him with \"Hey Jarvis\" for now.", kind = AssistantChange.Change),
    AssistFeature("mdi:watch-variant", "{name} on your watch", "", id = "jarvis-on-watch", detail = "The Church Drive app on a Pixel Watch has {name}: speak or tap a sentence and he answers on the watch, aloud too if you like.", kind = AssistantChange.Change),
    AssistFeature("mdi:timer-outline", "How long he took", "", id = "response-timing", detail = "Settings shows how long his last answer took, step by step, so a slow part can be found.", kind = AssistantChange.Change),
    AssistFeature("mdi:timer-sand", "Quicker, and stays with you", "", id = "quicker-follow-up", detail = "{name} answers sooner, and when he asks \"anything else?\" he keeps listening instead of closing.", kind = AssistantChange.Fix),
    AssistFeature("mdi:chat-plus-outline", "New chat, Voice and Settings", "", id = "jarvis-bar", detail = "{name}'s bottom bar has five buttons: Close, New chat, Type in the middle, Voice on or off, and Settings.", kind = AssistantChange.Change),
    AssistFeature("mdi:keyboard-outline", "Type or talk", "", id = "type-or-talk", detail = "Open {name} with his logo button on any page, ready to type or ready to listen (your choice in Settings)."),
    AssistFeature("mdi:microphone-message", "Hands-free with the wake word", "{wake}, who is home?", id = "wake-word"),
    AssistFeature("mdi:chat-processing-outline", "Natural conversation", "Is it going to rain tomorrow?", id = "conversation"),
    AssistFeature("mdi:cart-outline", "Shopping lists", "Add washing powder to the Lidl list", id = "shopping-lists"),
    AssistFeature("mdi:lightbulb-outline", "Lights, heating and the alarm", "Turn the lounge lights down", id = "lights-heating-alarm"),
)

/** The assistant log with `{name}` and `{wake}` filled in, so a new name shows everywhere without touching it. */
val ASSIST_FEATURES: List<AssistFeature>
    get() = RAW_ASSIST_FEATURES.map { it.copy(title = branded(it.title), example = branded(it.example), detail = branded(it.detail)) }

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
 * The assistant's name from its wake word, as Home Assistant's pipeline names it: "okay_nabu" is Nabu. Only a fallback: the house's own name for the assistant (see [AssistantBrand]) wins.
 * Empty when there is no wake word, so the assistant is just "your house assistant".
 */
fun assistantName(wakeWordId: String?): String {
    val skip = setOf("hey", "hi", "ok", "okay", "oh")
    val words = wakeWordId.orEmpty().split('_', '-', ' ').filter { it.isNotBlank() }
    val rest = words.dropWhile { it.lowercase() in skip }.ifEmpty { words }
    val name = rest.joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
    return name
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

/** When (in milliseconds on one clock) each step of the last answer happened; null for a step that didn't happen. */
data class AssistTimes(
    val start: Long? = null, // the person stopped speaking, or the sentence was sent
    val heard: Long? = null, // the house had the words
    val answer: Long? = null, // the written answer was ready
    val audio: Long? = null, // the spoken answer was ready
    val playing: Long? = null, // the voice began
)

/** The Settings line for how long the last answer took, step by step ("Heard 0.4s, thinking 1.9s, voice 0.6s, starts 0.2s: 3.1s in all"). */
fun formatTimings(t: AssistTimes): String {
    fun secs(ms: Long) = "%.1fs".format(java.util.Locale.ROOT, ms / 1000.0)
    val parts = mutableListOf<String>()
    fun step(label: String, from: Long?, to: Long?) { if (from != null && to != null && to >= from) parts += "$label ${secs(to - from)}" }
    step("heard", t.start, t.heard)
    step("thinking", t.heard ?: t.start, t.answer)
    step("voice made", t.answer, t.audio)
    step("voice starts", t.audio, t.playing)
    val first = t.start ?: return parts.joinToString(", ")
    val last = t.playing ?: t.answer ?: return parts.joinToString(", ")
    return parts.joinToString(", ") + if (last >= first) ": ${secs(last - first)} in all" else ""
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
        .let { spoken -> AssistantBrand.spokenNames().fold(spoken) { acc, n -> acc.removePrefix("$n ").removeSuffix(" $n") } }
        .removeSuffix(" please").trim()
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
