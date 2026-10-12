package com.churchdrive.app.ui

import androidx.compose.runtime.compositionLocalOf
import org.json.JSONArray
import org.json.JSONObject

/** What the live view needs from Home Assistant. Set in MainActivity from the connection. */
interface CameraHost {
    /** How the camera can be watched: `web_rtc` and/or `hls`. Empty if it can't be asked (older Home Assistant). */
    fun capabilities(entityId: String, done: (List<String>) -> Unit)

    /** A link to an HLS stream (relative to Home Assistant's address), or null. */
    fun hlsStream(entityId: String, done: (String?) -> Unit)

    /** The WebRTC servers Home Assistant wants clients to use (including a relay for use away from home). */
    fun iceServers(entityId: String, done: (JSONArray?) -> Unit)

    /** Sends our WebRTC offer; every event of the setup arrives at [onEvent]. Returns the subscription to close, or -1. */
    fun webRtcOffer(entityId: String, sdp: String, onEvent: (JSONObject) -> Unit): Int

    fun webRtcCandidate(entityId: String, sessionId: String, candidate: JSONObject)

    fun close(subscription: Int)

    /**
     * A socket to go2rtc's WebRTC signalling for [stream] (through Home Assistant's WebRTC Camera integration, signed in with our login).
     * [opened] gets the socket, or null if it can't be opened; [onMessage] every message from go2rtc; [onClosed] why it ended (null if it just closed).
     */
    fun go2rtcSocket(stream: String, onMessage: (JSONObject) -> Unit, onClosed: (String?) -> Unit, opened: (Go2rtcSocket?) -> Unit)

    /** A camera's saved events (`church_drive/camera/events`), or null if the integration can't give them. */
    fun events(base: String, done: (JSONObject?) -> Unit)
}

val LocalCameraHost = compositionLocalOf<CameraHost?> { null }

/** An open connection to go2rtc's signalling. */
interface Go2rtcSocket {
    fun send(message: JSONObject)

    fun close()
}
