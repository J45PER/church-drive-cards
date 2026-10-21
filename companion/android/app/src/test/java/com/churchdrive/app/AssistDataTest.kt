package com.churchdrive.app

import com.churchdrive.app.ui.AssistEvent
import com.churchdrive.app.ui.AssistTimes
import com.churchdrive.app.ui.formatTimings
import com.churchdrive.app.ui.assistAudioUrl
import com.churchdrive.app.ui.forDisplay
import com.churchdrive.app.ui.forSpeech
import com.churchdrive.app.ui.isDismissal
import com.churchdrive.app.ui.isNotUnderstood
import com.churchdrive.app.ui.parseAssistEvent
import com.churchdrive.app.ui.parseAssistantShow
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistDataTest {
    @Test
    fun anAnswerCarriesTheWordsAndTheConversation() {
        val e = parseAssistEvent(
            JSONObject(
                """{"type":"intent-end","data":{"intent_output":{"conversation_id":"c1","response":{"speech":{"plain":{"speech":"Done."}}}}}}""",
            ),
        ) as AssistEvent.Answer
        assertEquals("Done.", e.text)
        assertEquals("c1", e.conversationId)
    }

    @Test
    fun theSpokenAnswerIsAnAddressOnTheHouse() {
        val e = parseAssistEvent(JSONObject("""{"type":"tts-end","data":{"tts_output":{"url":"/api/tts_proxy/abc.mp3"}}}""")) as AssistEvent.Audio
        assertEquals("/api/tts_proxy/abc.mp3", e.path)
        assertEquals("https://ha.example/api/tts_proxy/abc.mp3", assistAudioUrl("https://ha.example/", e.path))
        assertEquals("https://x.test/a.mp3", assistAudioUrl("https://ha.example", "https://x.test/a.mp3"))
    }

    @Test
    fun errorsAndTheEndAreRecognisedAndTheRestIgnored() {
        val failed = parseAssistEvent(JSONObject("""{"type":"error","data":{"code":"intent-failed","message":"nope"}}""")) as AssistEvent.Failed
        assertTrue(failed.noMatch)
        val other = parseAssistEvent(JSONObject("""{"type":"error","data":{"code":"stt-no-text-recognized","message":"quiet"}}""")) as AssistEvent.Failed
        assertFalse(other.noMatch)
        assertEquals(AssistEvent.Done, parseAssistEvent(JSONObject("""{"type":"run-end"}""")))
        assertNull(parseAssistEvent(JSONObject("""{"type":"intent-start","data":{}}""")))
    }

    @Test
    fun aSorryAnswerCountsAsNotUnderstood() {
        assertTrue(isNotUnderstood("Sorry, I couldn't understand that"))
        assertFalse(isNotUnderstood("Done. The lounge is heating to 21 degrees."))
    }

    @Test
    fun theAssistantIsNamedAfterItsWakeWord() {
        assertEquals("Jarvis", com.churchdrive.app.ui.assistantName("hey_jarvis"))
        assertEquals("Clive", com.churchdrive.app.ui.assistantName("hey_clive"))
        assertEquals("Nabu", com.churchdrive.app.ui.assistantName("okay_nabu"))
        assertEquals("Alexa", com.churchdrive.app.ui.assistantName("alexa"))
        assertEquals("", com.churchdrive.app.ui.assistantName(null))
        assertEquals("Hey Jarvis", com.churchdrive.app.ui.wakePhrase("hey_jarvis"))
    }

    @Test
    fun theWakeWordIsThePreferredPipelinesOwn() {
        val r = JSONObject(
            """{"preferred_pipeline":"b","pipelines":[{"id":"a","wake_word_id":"alexa"},{"id":"b","wake_word_id":"hey_jarvis"}]}""",
        )
        assertEquals("hey_jarvis", com.churchdrive.app.ui.preferredWakeWord(r))
        assertNull(com.churchdrive.app.ui.preferredWakeWord(JSONObject("""{"preferred_pipeline":"a","pipelines":[{"id":"a","wake_word_id":null}]}""")))
        assertNull(com.churchdrive.app.ui.preferredWakeWord(null))
    }

    @Test
    fun theWakeWordAndSpeechEventsAreRecognised() {
        val start = parseAssistEvent(JSONObject("""{"type":"run-start","data":{"runner_data":{"stt_binary_handler_id":1,"timeout":300}}}""")) as AssistEvent.Started
        assertEquals(1, start.handlerId)
        assertNull((parseAssistEvent(JSONObject("""{"type":"run-start","data":{}}""")) as AssistEvent.Started).handlerId)
        val woke = parseAssistEvent(JSONObject("""{"type":"wake_word-end","data":{"wake_word_output":{"wake_word_id":"hey_jarvis"}}}""")) as AssistEvent.Woke
        assertEquals("hey_jarvis", woke.word)
        assertEquals(AssistEvent.SpeechEnded, parseAssistEvent(JSONObject("""{"type":"stt-vad-end","data":{}}""")))
        val heard = parseAssistEvent(JSONObject("""{"type":"stt-end","data":{"stt_output":{"text":"turn the lights off"}}}""")) as AssistEvent.Heard
        assertEquals("turn the lights off", heard.text)
        val timeout = parseAssistEvent(JSONObject("""{"type":"error","data":{"code":"wake-word-timeout","message":"x"}}""")) as AssistEvent.Failed
        assertEquals("wake-word-timeout", timeout.code)
    }

    @Test
    fun theMicrophoneLevelIsLoudnessFromSilenceToShouting() {
        val quiet = ByteArray(200)
        assertEquals(0f, com.churchdrive.app.ui.MicStream.levelOf(quiet, quiet.size), 0.001f)
        val loud = ByteArray(200) { if (it % 2 == 0) 0x00 else 0x40 }
        assertTrue(com.churchdrive.app.ui.MicStream.levelOf(loud, loud.size) > 0.9f)
    }

    private fun entry(id: String, kind: com.churchdrive.app.ui.AssistantChange = com.churchdrive.app.ui.AssistantChange.Feature) =
        com.churchdrive.app.ui.AssistFeature("mdi:x", id, "say $id", id = id, kind = kind)

    @Test
    fun theFirstIntroductionIsFullAndThenOnlyUntoldLogEntriesCome() {
        val fix = entry("fix-1", com.churchdrive.app.ui.AssistantChange.Fix)
        val log = listOf(fix, entry("b"), entry("a"))
        // Never introduced: the full introduction, features only, and everything in the log counts as told.
        val first = com.churchdrive.app.ui.introDue(false, null, log)
        assertEquals(com.churchdrive.app.ui.IntroKind.First, first.due?.kind)
        assertEquals(listOf("b", "a"), first.due?.features?.map { it.id })
        assertEquals(setOf("fix-1", "b", "a"), first.told)
        // Told about everything: nothing, however many app versions have gone by.
        assertNull(com.churchdrive.app.ui.introDue(true, setOf("fix-1", "b", "a"), log).due)
        // A new entry (a feature, change or fix): just that, once.
        val update = com.churchdrive.app.ui.introDue(true, setOf("b", "a"), log)
        assertEquals(com.churchdrive.app.ui.IntroKind.Update, update.due?.kind)
        assertEquals(listOf("fix-1"), update.due?.features?.map { it.id })
        assertNull(com.churchdrive.app.ui.introDue(true, update.told, log).due)
        // Introduced before the log existed: taken to know it all, then told about what is added.
        val migrated = com.churchdrive.app.ui.introDue(true, null, log)
        assertNull(migrated.due)
        assertEquals(setOf("fix-1", "b", "a"), migrated.told)
    }

    @Test
    fun theAssistantLogHasUniqueIds() {
        val ids = com.churchdrive.app.ui.ASSIST_FEATURES.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun theSpokenIntroductionNamesTheAssistantOnlyTheFirstTime() {
        val feats = listOf(
            com.churchdrive.app.ui.AssistFeature("mdi:x", "Hands-free with the wake word", "a"),
            com.churchdrive.app.ui.AssistFeature("mdi:y", "Natural conversation", "b"),
        )
        val first = com.churchdrive.app.ui.introSpeech(com.churchdrive.app.ui.IntroKind.First, "Jamie Power", "Jarvis", feats)
        assertTrue(first.startsWith("Hello Jamie. I'm Jarvis."))
        assertTrue("hands-free with the wake word and natural conversation" in first)
        val update = com.churchdrive.app.ui.introSpeech(com.churchdrive.app.ui.IntroKind.Update, "Jamie", "Jarvis", feats)
        assertTrue(update.startsWith("Welcome back Jamie. Here's what's new:"))
        assertFalse("I'm Jarvis" in update)
        assertEquals("a, b and c", com.churchdrive.app.ui.spokenList(listOf("a", "b", "c")))
    }

    @Test
    fun aQuestionRunHasNoAudioHandlerSoNoMicrophoneIsStreamed() {
        // Home Assistant sends runner_data with stt_binary_handler_id null for text-only runs: that is no handler, not handler 0.
        val e = parseAssistEvent(JSONObject("""{"type":"run-start","data":{"runner_data":{"stt_binary_handler_id":null,"timeout":30}}}""")) as AssistEvent.Started
        assertNull(e.handlerId)
    }

    @Test
    fun anAnswerSaysWhetherTheHouseWantsToKeepTalking() {
        val a = parseAssistEvent(
            JSONObject("""{"type":"intent-end","data":{"intent_output":{"continue_conversation":true,"response":{"speech":{"plain":{"speech":"Which room?"}}}}}}"""),
        ) as AssistEvent.Answer
        assertTrue(a.continueConversation)
        val b = parseAssistEvent(
            JSONObject("""{"type":"intent-end","data":{"intent_output":{"response":{"speech":{"plain":{"speech":"Done."}}}}}}"""),
        ) as AssistEvent.Answer
        assertFalse(b.continueConversation)
    }

    @Test
    fun sayingThatsItEndsTheConversation() {
        listOf("That's it", "that\u2019s it, thanks", "No thanks", "nope", "That's all Jarvis", "Okay thanks", "Nothing else", "bye", "Never mind", "Thank you").forEach {
            assertTrue(it, isDismissal(it))
        }
    }

    @Test
    fun anythingElseKeepsTheConversationGoing() {
        listOf("Turn off the lights", "Is the alarm on", "What time is it", "Turn on the lamp and then tell me that is it done", "", "no one is home, is that right and who is in the house now").forEach {
            assertFalse(it, isDismissal(it))
        }
    }

    @Test
    fun dianeIsRespelledForTheVoiceAndRestoredOnScreen() {
        assertEquals("Hello Die Ann. Welcome back.", forSpeech("Hello Diane. Welcome back."))
        assertEquals("Die Ann's lights are off", forSpeech("Diane's lights are off"))
        assertEquals("Jamie and Diane are home", forDisplay("Jamie and Die Ann are home"))
        assertEquals("Diane's phone", forDisplay("Die-Ann's phone"))
        assertEquals("Diana is not Diane", forDisplay(forSpeech("Diana is not Diane")))
        assertEquals("Hayley is out", forDisplay("Hayley is out"))
    }

    @Test
    fun theTimingLineSaysHowLongEachStepTook() {
        val t = AssistTimes(start = 1_000, heard = 1_400, answer = 3_300, audio = 3_900, playing = 4_100)
        assertEquals("heard 0.4s, thinking 1.9s, voice made 0.6s, voice starts 0.2s: 3.1s in all", formatTimings(t))
        assertEquals("thinking 1.5s: 1.5s in all", formatTimings(AssistTimes(start = 0, answer = 1_500)))
        assertEquals("", formatTimings(AssistTimes()))
    }

    @Test
    fun aShowEventIsOnlyForTheAskerAndOnlyHttps() {
        fun ev(user: String, data: String) = JSONObject("""{"event_type":"church_drive_assistant_show","data":$data,"context":{"user_id":"$user"}}""")
        val ok = parseAssistantShow(ev("u1", """{"image_url":"https://x.test/a.jpg","link_url":"","link_label":" Report "}"""), "u1")!!
        assertEquals("https://x.test/a.jpg", ok.image)
        assertNull(ok.link)
        assertEquals("Report", ok.linkLabel)
        assertNull(parseAssistantShow(ev("u2", """{"image_url":"https://x.test/a.jpg"}"""), "u1"))
        assertNull(parseAssistantShow(ev("u1", """{"image_url":"http://x.test/a.jpg","link_url":"javascript:alert(1)"}"""), "u1"))
        assertNull(parseAssistantShow(ev("u1", """{"link_url":"https://x.test"}"""), null))
    }

    @Test
    fun theHousesNameIsUsedEverywhereAndEarlierNamesStillWork() {
        val brand = com.churchdrive.app.ui.AssistantBrand
        try {
            brand.useForTest("Jeeves", "Hey Jeeves")
            assertEquals("Jeeves can show it. Say Hey Jeeves.", com.churchdrive.app.ui.branded("{name} can show it. Say {wake}."))
            assertTrue(com.churchdrive.app.ui.ASSIST_FEATURES.any { it.title == "Jeeves on your watch" })
            assertTrue(com.churchdrive.app.ui.ASSIST_FEATURES.none { "{" in it.title + it.detail + it.example })
            assertTrue(isDismissal("That's all Jeeves"))
            assertTrue(isDismissal("Jarvis, no thanks"))
            assertEquals(setOf("jarvis", "clive", "jeeves"), brand.spokenNames())
            brand.useForTest("")
            assertEquals("Assistant can show it. Say Hey Assistant.", com.churchdrive.app.ui.branded("{name} can show it. Say {wake}."))
        } finally {
            brand.useForTest("")
        }
    }
}
