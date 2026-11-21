package com.churchdrive.app.ui

import org.json.JSONArray
import org.json.JSONObject

/**
 * A fitting with a main light and a ring of segments (the Cloakroom's Govee), as the house has it set (`church_drive/fitting`). Home Assistant
 * only reports one colour for the whole device and the segments' colours are just the last thing sent, so the dial draws this record instead.
 */
data class FitSegment(val rgb: List<Int>, val brightness: Int) {
    /** A segment set to black is off: the Govee has no power switch for a segment. */
    val dark: Boolean get() = (rgb.maxOrNull() ?: 0) <= SEGMENT_BLACK
}

data class Fitting(
    val room: String,
    val main: String,
    val ring: String,
    val segments: List<String>,
    val mainOn: Boolean,
    val mainMode: String,
    val mainKelvin: Int,
    val mainRgb: List<Int>,
    val mainBrightness: Int,
    val ringOn: Boolean,
    val ringSegments: List<FitSegment>,
) {
    /** The colour the main light is showing: its tone's colour when white, else its colour. */
    val mainShown: List<Int> get() = if (mainMode == "colour") mainRgb else kelvinRgb(mainKelvin).toList()
}

data class FittingList(val byRoom: Map<String, Fitting> = emptyMap())

const val SEGMENT_BLACK = 8

private fun JSONArray?.ints(): List<Int> = this?.let { a -> (0 until a.length()).map { a.optInt(it) } }.orEmpty()

private fun JSONArray?.strings(): List<String> = this?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty()

fun parseFittings(result: Any?): FittingList? {
    val root = (result as? JSONObject)?.optJSONObject("fittings") ?: return null
    val out = mutableMapOf<String, Fitting>()
    root.keys().forEach { room ->
        val o = root.optJSONObject(room) ?: return@forEach
        val f = o.optJSONObject("fitting") ?: return@forEach
        val main = f.optJSONObject("main") ?: return@forEach
        val ring = f.optJSONObject("ring") ?: return@forEach
        val segs = ring.optJSONArray("segments")?.let { a ->
            (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map { FitSegment(it.optJSONArray("rgb").ints(), it.optInt("brightness", 100)) }
        }.orEmpty()
        out[room] = Fitting(
            room, o.optString("main"), o.optString("ring"), o.optJSONArray("segments").strings(),
            main.optBoolean("on"), main.optString("mode", "white"), main.optInt("kelvin", 2700), main.optJSONArray("rgb").ints(), main.optInt("brightness", 100),
            ring.optBoolean("on"), segs,
        )
    }
    return FittingList(out)
}

/** The body of `church_drive/fitting/set`: [main] and/or [ring] are what to change (left out, left alone). */
fun fittingBody(room: String, main: JSONObject? = null, ring: JSONObject? = null, keepRing: Boolean = false): JSONObject =
    JSONObject().put("room", room)
        .also { if (main != null) it.put("main", main) }
        .also { if (ring != null) it.put("ring", ring) }
        .also { if (keepRing) it.put("keep_ring", true) }

fun mainRequest(on: Boolean? = null, mode: String? = null, kelvin: Int? = null, rgb: List<Int>? = null, brightness: Int? = null): JSONObject = JSONObject()
    .also { o ->
        on?.let { o.put("on", it) }
        mode?.let { o.put("mode", it) }
        kelvin?.let { o.put("kelvin", it) }
        rgb?.let { o.put("rgb", JSONArray(it)) }
        brightness?.let { o.put("brightness", it) }
    }

/** A ring request that sets every segment to [segments] (the ring's own list with some changed). */
fun ringSegmentsRequest(segments: List<FitSegment>): JSONObject = JSONObject().put("on", true).put(
    "segments", JSONArray(segments.map { JSONObject().put("rgb", JSONArray(it.rgb)).put("brightness", it.brightness) }),
)

fun ringColourRequest(rgb: List<Int>): JSONObject = JSONObject().put("on", true).put("rgb", JSONArray(rgb))

fun ringLevelRequest(brightness: Int): JSONObject = JSONObject().put("on", true).put("brightness", brightness)

/** [segments] with segment [index] changed to [with]. */
fun List<FitSegment>.replaced(index: Int, with: FitSegment): List<FitSegment> = mapIndexed { i, s -> if (i == index) with else s }
