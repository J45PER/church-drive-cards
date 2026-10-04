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

/**
 * The offer without the video formats a phone can't be trusted to decode. H265 (HEVC) is dropped: on a Pixel the
 * hardware decoder crashed the whole app when a camera answered with it, and H264 is offered as well. Each dropped
 * format's retransmission twin goes too. If nothing would be left, the offer is returned as it was.
 */
fun withoutVideoFormats(sdp: String, drop: Set<String> = setOf("H265", "HEVC")): String {
    val eol = if (sdp.contains("\r\n")) "\r\n" else "\n"
    val lines = sdp.split(eol)
    val start = lines.indexOfFirst { it.startsWith("m=video") }
    if (start < 0) return sdp
    val end = lines.drop(start + 1).indexOfFirst { it.startsWith("m=") }.let { if (it < 0) lines.size else start + 1 + it }
    val section = lines.subList(start, end)
    val names = Regex("^a=rtpmap:(\\d+) ([^/\\s]+)").let { r -> section.mapNotNull { r.find(it) }.associate { it.groupValues[1] to it.groupValues[2] } }
    val dropped = names.filterValues { n -> drop.any { it.equals(n, ignoreCase = true) } }.keys.toMutableSet()
    val apt = Regex("^a=fmtp:(\\d+) apt=(\\d+)")
    section.mapNotNull { apt.find(it) }.filter { it.groupValues[2] in dropped }.forEach { dropped += it.groupValues[1] }
    if (dropped.isEmpty()) return sdp
    val tokens = lines[start].split(" ")
    val kept = tokens.drop(3).filter { it !in dropped }
    if (kept.isEmpty()) return sdp
    val prefixes = dropped.flatMap { listOf("a=rtpmap:$it ", "a=fmtp:$it ", "a=rtcp-fb:$it ") }
    val out = lines.mapIndexedNotNull { i, line ->
        when {
            i == start -> (tokens.take(3) + kept).joinToString(" ")
            i in start until end && prefixes.any { line.startsWith(it) } -> null
            else -> line
        }
    }
    return out.joinToString(eol)
}
