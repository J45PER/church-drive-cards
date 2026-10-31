package com.churchdrive.satellites

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** The settings a preset holds. A part left null is not changed when the preset is applied (a satellite without a screen has no design). */
data class SatSettings(
    val design: SatDesign? = null,
    val glances: List<String> = emptyList(),
    val backlightDay: Float? = null,
    val backlightNight: Float? = null,
    val nightStart: Int? = null,
    val nightEnd: Int? = null,
    val volume: Float? = null,
    val spokenAnswers: Boolean? = null,
    val followUp: Boolean? = null,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        design?.let { put("design", it.label) }
        if (glances.isNotEmpty()) put("glances", JSONArray(glances))
        backlightDay?.let { put("backlightDay", it.toDouble()) }
        backlightNight?.let { put("backlightNight", it.toDouble()) }
        nightStart?.let { put("nightStart", it) }
        nightEnd?.let { put("nightEnd", it) }
        volume?.let { put("volume", it.toDouble()) }
        spokenAnswers?.let { put("spokenAnswers", it) }
        followUp?.let { put("followUp", it) }
    }

    companion object {
        fun fromJson(o: JSONObject) = SatSettings(
            design = SatDesign.from(o.optString("design").ifBlank { null }),
            glances = o.optJSONArray("glances")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList(),
            backlightDay = if (o.has("backlightDay")) o.optDouble("backlightDay").toFloat() else null,
            backlightNight = if (o.has("backlightNight")) o.optDouble("backlightNight").toFloat() else null,
            nightStart = if (o.has("nightStart")) o.optInt("nightStart") else null,
            nightEnd = if (o.has("nightEnd")) o.optInt("nightEnd") else null,
            volume = if (o.has("volume")) o.optDouble("volume").toFloat() else null,
            spokenAnswers = if (o.has("spokenAnswers")) o.optBoolean("spokenAnswers") else null,
            followUp = if (o.has("followUp")) o.optBoolean("followUp") else null,
        )

        /** What a satellite is set to now. */
        fun of(s: Satellite) = SatSettings(
            design = s.design,
            glances = s.glances.takeIf { s.hasScreen && it.all(String::isNotBlank) } ?: emptyList(),
            backlightDay = s.backlightDay, backlightNight = s.backlightNight,
            nightStart = s.nightStart, nightEnd = s.nightEnd,
            volume = s.volume, spokenAnswers = s.spokenAnswers, followUp = s.followUp,
        )
    }
}

data class Preset(val name: String, val settings: SatSettings)

/** Presets kept on the phone: a name and the settings of the satellite they were made from. */
class SatellitePresets(context: Context) {
    private val prefs = context.getSharedPreferences("church_drive_satellite_presets", Context.MODE_PRIVATE)

    fun all(): List<Preset> = runCatching {
        val a = JSONArray(prefs.getString("presets", "[]"))
        (0 until a.length()).map { a.getJSONObject(it) }.map { Preset(it.getString("name"), SatSettings.fromJson(it.getJSONObject("settings"))) }
    }.getOrDefault(emptyList())

    /** Saves (or replaces the one with the same name) and gives the new list. */
    fun save(preset: Preset): List<Preset> = write(all().filter { !it.name.equals(preset.name, ignoreCase = true) } + preset)

    fun delete(name: String): List<Preset> = write(all().filter { it.name != name })

    private fun write(list: List<Preset>): List<Preset> {
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("name", it.name).put("settings", it.settings.toJson())) }
        prefs.edit().putString("presets", a.toString()).apply()
        return list
    }
}
