package com.churchdrive.app

import com.churchdrive.app.ui.IceServerSpec
import com.churchdrive.app.ui.RtcEvent
import com.churchdrive.app.ui.candidateJson
import com.churchdrive.app.ui.mediaOrder
import com.churchdrive.app.ui.parseIceServers
import com.churchdrive.app.ui.parseRtcEvent
import com.churchdrive.app.ui.withoutVideoFormats
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RtcDataTest {
    private fun parse(json: String) = parseRtcEvent(JSONObject(json))

    @Test
    fun readsTheSetUpEvents() {
        assertEquals(RtcEvent.Session("abc"), parse("""{"type":"session","session_id":"abc"}"""))
        assertEquals(RtcEvent.Answer("v=0"), parse("""{"type":"answer","answer":"v=0"}"""))
        assertEquals(RtcEvent.Error("Stream not found"), parse("""{"type":"error","code":"x","message":"Stream not found"}"""))
        assertEquals(RtcEvent.Error("webrtc_offer_failed"), parse("""{"type":"error","code":"webrtc_offer_failed"}"""))
    }

    @Test
    fun aCandidateComesAsAnObjectOrJustTheLine() {
        assertEquals(
            RtcEvent.Candidate("candidate:1 1 udp 1 10.0.0.1 5000 typ host", "1", 1),
            parse("""{"type":"candidate","candidate":{"candidate":"candidate:1 1 udp 1 10.0.0.1 5000 typ host","sdpMid":"1","sdpMLineIndex":1}}"""),
        )
        assertEquals(
            RtcEvent.Candidate("candidate:2", "0", 0),
            parse("""{"type":"candidate","candidate":"candidate:2"}"""),
        )
        assertNull(parse("""{"type":"candidate","candidate":""}"""))
    }

    @Test
    fun otherEventsAreIgnored() {
        assertNull(parse("""{"type":"something_else"}"""))
        assertNull(parse("""{"type":"session"}"""))
        assertNull(parse("""{}"""))
    }

    @Test
    fun ourCandidateIsSentInHomeAssistantsShape() {
        val c = candidateJson("candidate:9", "0", 0)
        assertEquals("candidate:9", c.getString("candidate"))
        assertEquals("0", c.getString("sdpMid"))
        assertEquals(0, c.getInt("sdpMLineIndex"))
        assertEquals("0", candidateJson("x", null, 3).getString("sdpMid"))
    }

    @Test
    fun readsTheServersHomeAssistantGives() {
        val servers = parseIceServers(
            JSONArray(
                """[{"urls":["stun:a:3478","stun:b:80"]},
                    {"urls":"turn:relay:3478","username":"u","credential":"p"},
                    {"urls":[]},{"nothing":true}]""",
            ),
        )
        assertEquals(
            listOf(
                IceServerSpec(listOf("stun:a:3478", "stun:b:80"), null, null),
                IceServerSpec(listOf("turn:relay:3478"), "u", "p"),
            ),
            servers,
        )
        assertEquals(emptyList<IceServerSpec>(), parseIceServers(null))
    }

    @Test
    fun readsTheOrderOfMediaSections() {
        val sdp = "v=0\r\nm=application 9 UDP/DTLS/SCTP webrtc-datachannel\r\nm=audio 9 UDP/TLS/RTP/SAVPF 111\r\nm=video 9 UDP/TLS/RTP/SAVPF 96\r\n"
        assertEquals(listOf("application", "audio", "video"), mediaOrder(sdp))
    }

    @Test
    fun dropsH265AndItsRetransmissionFromTheOffer() {
        val sdp = listOf(
            "v=0", "m=audio 9 UDP/TLS/RTP/SAVPF 111", "a=rtpmap:111 opus/48000/2",
            "m=video 9 UDP/TLS/RTP/SAVPF 96 97 98 99 100",
            "a=rtpmap:96 H265/90000", "a=rtcp-fb:96 nack", "a=rtpmap:97 rtx/90000", "a=fmtp:97 apt=96",
            "a=rtpmap:98 H264/90000", "a=fmtp:98 profile-level-id=42e01f", "a=rtpmap:99 rtx/90000", "a=fmtp:99 apt=98",
            "a=rtpmap:100 VP8/90000", "",
        ).joinToString("\r\n")
        val out = withoutVideoFormats(sdp)
        assertEquals(true, out.contains("m=video 9 UDP/TLS/RTP/SAVPF 98 99 100"))
        assertEquals(false, out.contains("H265"))
        assertEquals(false, out.contains("apt=96"))
        assertEquals(false, out.contains("rtcp-fb:96"))
        assertEquals(true, out.contains("a=rtpmap:98 H264/90000"))
        assertEquals(true, out.contains("a=rtpmap:111 opus/48000/2"))
        assertEquals(sdp, withoutVideoFormats(sdp, setOf("nothing")))
    }
}
