package com.churchdrive.app.ui

import org.json.JSONObject

/**
 * What the air purifier's display does in each mode, kept by the house (`church_drive/purifier_display`, rules in purifier_display_logic.py):
 * off, dim, bright, or left alone ("keep"). It is set when the mode changes. [modes] is by the mode's lower-case name.
 */
data class PurifierDisplayModes(
    val enabled: Boolean,
    val entity: String,
    val display: String?,
    val modes: Map<String, String>,
    val presetModes: List<String>,
    val summary: String,
) {
    /** What a mode does: keep, off, dim or bright. */
    fun choiceFor(mode: String): String = modes[mode.trim().lowercase()] ?: "keep"
}

/** The choices for a mode, in order: the house's word and the one shown. */
val DISPLAY_MODE_CHOICES: List<Pair<String, String>> = listOf("keep" to "Leave", "off" to "Off", "dim" to "Dim", "bright" to "Bright")

/** The house's answer, or null if it is missing or an older house has none. */
fun parsePurifierDisplayModes(json: JSONObject?): PurifierDisplayModes? {
    json ?: return null
    if (!json.has("modes")) return null
    val modes = json.optJSONObject("modes")?.let { m -> m.keys().asSequence().associateWith { m.optString(it, "keep") } }.orEmpty()
    val presets = json.optJSONArray("preset_modes")?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() } }.orEmpty()
    return PurifierDisplayModes(
        enabled = json.optBoolean("enabled", true),
        entity = json.optString("entity", ""),
        display = json.optString("display", "").takeIf { it.isNotBlank() && it != "null" },
        modes = modes,
        presetModes = presets,
        summary = json.optString("summary", ""),
    )
}

/** What to send to set one mode's choice. */
fun purifierDisplaySetBody(mode: String, choice: String): JSONObject =
    JSONObject().put("modes", JSONObject().put(mode.trim().lowercase(), choice))

/** The line shown on the closed row: what the modes do, or that the display isn't found. */
fun purifierDisplayLine(state: PurifierDisplayModes): String = when {
    state.display == null -> "No display found"
    !state.enabled -> "Off"
    else -> state.summary.ifBlank { "Left as it is" }
}
