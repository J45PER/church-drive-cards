package com.churchdrive.app

import com.churchdrive.app.ui.CameraOpenLink
import com.churchdrive.app.ui.RtcEvent
import com.churchdrive.app.ui.cameraOpenFromLink
import com.churchdrive.app.ui.go2rtcCandidate
import com.churchdrive.app.ui.go2rtcOffer
import com.churchdrive.app.ui.parseGo2rtcMessage
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Go2rtcTest {
    @Test
    fun readsGo2rtcMessages() {
        assertEquals(RtcEvent.Answer("v=0"), parseGo2rtcMessage(JSONObject("""{"type":"webrtc/answer","value":"v=0"}""")))
        assertEquals(RtcEvent.Candidate("candidate:1 1 udp 1 10.0.0.2 8555 typ host", "0", 0), parseGo2rtcMessage(JSONObject("""{"type":"webrtc/candidate","value":"candidate:1 1 udp 1 10.0.0.2 8555 typ host"}""")))
        assertEquals(RtcEvent.Error("nope"), parseGo2rtcMessage(JSONObject("""{"type":"error","value":"nope"}""")))
        assertNull(parseGo2rtcMessage(JSONObject("""{"type":"webrtc/candidate","value":""}""")))
        assertNull(parseGo2rtcMessage(JSONObject("""{"type":"other"}""")))
    }

    @Test
    fun buildsGo2rtcMessages() {
        assertEquals("webrtc/offer", go2rtcOffer("v=0").getString("type"))
        assertEquals("v=0", go2rtcOffer("v=0").getString("value"))
        assertEquals("candidate:1", go2rtcCandidate("candidate:1").getString("value"))
    }

    @Test
    fun readsCameraLinks() {
        assertEquals(CameraOpenLink("camera.front_door_live_view", false), cameraOpenFromLink("church-drive-camera:camera.front_door_live_view"))
        assertEquals(CameraOpenLink("camera.front_door_live_view", true), cameraOpenFromLink("church-drive-camera:camera.front_door_live_view?answer"))
        assertNull(cameraOpenFromLink("church-drive-camera:light.kitchen"))
        assertNull(cameraOpenFromLink("https://example.com"))
        assertNull(cameraOpenFromLink(null))
    }
}
