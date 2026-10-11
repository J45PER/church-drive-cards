package com.churchdrive.app

import com.churchdrive.app.ui.AssistEvent
import com.churchdrive.app.ui.assistAudioUrl
import com.churchdrive.app.ui.introDue
import com.churchdrive.app.ui.isNotUnderstood
import com.churchdrive.app.ui.parseAssistEvent
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
        assertNull(parseAssistEvent(JSONObject("""{"type":"run-start","data":{}}""")))
    }

    @Test
    fun theIntroductionShowsOncePerVersion() {
        assertTrue(introDue(null, "0.8.2"))
        assertTrue(introDue("0.8.1", "0.8.2"))
        assertFalse(introDue("0.8.2", "0.8.2"))
    }

    @Test
    fun aSorryAnswerCountsAsNotUnderstood() {
        assertTrue(isNotUnderstood("Sorry, I couldn't understand that"))
        assertFalse(isNotUnderstood("Done. The lounge is heating to 21 degrees."))
    }
}
