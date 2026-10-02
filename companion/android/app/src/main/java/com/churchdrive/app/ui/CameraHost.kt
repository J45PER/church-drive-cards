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
}

val LocalCameraHost = compositionLocalOf<CameraHost?> { null }
