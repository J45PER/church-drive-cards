package com.churchdrive.app.ui

import org.json.JSONArray
import org.json.JSONObject

/** What Home Assistant sends while it sets up a WebRTC live view (`camera/webrtc/offer` events). */
sealed class RtcEvent {
    /** The session's id, needed to send our own candidates. */
    data class Session(val id: String) : RtcEvent()

    /** The camera's answer to our offer (SDP). */
    data class Answer(val sdp: String) : RtcEvent()

    /** A network address the camera can be reached at. */
    data class Candidate(val candidate: String, val sdpMid: String?, val sdpMLineIndex: Int) : RtcEvent()

    data class Error(val message: String) : RtcEvent()
}

/** One event of the offer subscription as an [RtcEvent], or null for one we don't use. */
fun parseRtcEvent(event: JSONObject): RtcEvent? = when (event.optString("type")) {
    "session" -> event.optString("session_id").takeIf { it.isNotBlank() }?.let { RtcEvent.Session(it) }
    "answer" -> event.optString("answer").takeIf { it.isNotBlank() }?.let { RtcEvent.Answer(it) }
    "candidate" -> {
        // Sent as an object {candidate, sdpMid, sdpMLineIndex}, or as just the candidate line.
        val c = event.opt("candidate")
        when (c) {
            is JSONObject -> c.optString("candidate").takeIf { it.isNotBlank() }
                ?.let { RtcEvent.Candidate(it, c.optString("sdpMid").takeIf { m -> m.isNotBlank() }, c.optInt("sdpMLineIndex", 0)) }
            is String -> c.takeIf { it.isNotBlank() }?.let { RtcEvent.Candidate(it, "0", 0) }
            else -> null
        }
    }
    "error" -> RtcEvent.Error(event.optString("message").ifBlank { event.optString("code", "Live view failed") })
    else -> null
}

/** Our own candidate, in the shape `camera/webrtc/candidate` takes. */
fun candidateJson(candidate: String, sdpMid: String?, sdpMLineIndex: Int): JSONObject =
    JSONObject().put("candidate", candidate).put("sdpMid", sdpMid ?: "0").put("sdpMLineIndex", sdpMLineIndex)

/** A WebRTC server: its addresses and, for a relay, the login. */
data class IceServerSpec(val urls: List<String>, val username: String?, val credential: String?)

/** The `iceServers` of `camera/webrtc/get_client_config`: [{urls: [...] or "...", username, credential}]. */
fun parseIceServers(servers: JSONArray?): List<IceServerSpec> {
    val out = mutableListOf<IceServerSpec>()
    for (i in 0 until (servers?.length() ?: 0)) {
        val s = servers?.optJSONObject(i) ?: continue
        val urls = when (val u = s.opt("urls")) {
            is JSONArray -> (0 until u.length()).mapNotNull { u.optString(it).takeIf { x -> x.isNotBlank() } }
            is String -> listOf(u)
            else -> emptyList()
        }
        if (urls.isNotEmpty()) {
            out += IceServerSpec(urls, s.optString("username").takeIf { it.isNotBlank() }, s.optString("credential").takeIf { it.isNotBlank() })
        }
    }
    return out
}

/** The kinds of media in an SDP, in order of its `m=` lines (`audio`, `video`, `application`): for telling offer and answer apart. */
fun mediaOrder(sdp: String): List<String> =
    sdp.lineSequence().filter { it.startsWith("m=") }.map { it.removePrefix("m=").substringBefore(' ') }.toList()
