package com.churchdrive.satellites

import com.churchdrive.app.ha.EntityState

/**
 * The four complete designs a satellite can wear (docs/CLIVE-STYLE.md, satellite/design/flows.html). A design is the resting
 * screen plus how the rest of the flow wears it. [label] is the option the satellite's Design setting takes in Home Assistant.
 */
enum class SatDesign(val label: String, val blurb: String, val prefix: String) {
    Glance("Glance", "The time, a date line with the outside and inside temperature, and three glances you choose.", "glance"),
    Clock("Clock", "A big thin clock with the outside temperature above it. The least lit.", "clock"),
    Weather("Weather", "The outside temperature first, big, with the time and date under it.", "weather"),
    Ring("Ring", "The time inside two thin rings that fill with the minute and the hour.", "ring"),
    ;

    companion object {
        fun from(label: String?): SatDesign? = entries.firstOrNull { it.label.equals(label, ignoreCase = true) }
    }
}

/** What the preview of a design shows: resting, Clive listening, or a card (their names in the preview image files). */
enum class PreviewState(val label: String, val key: String) {
    Resting("Resting", "rest"),
    Talking("Talking", "talk"),
    Card("Card", "card"),
}

/** What a glance slot can show (the satellite's Glance 1 to 3 options). */
val GLANCE_OPTIONS = listOf("Who's home", "Electricity", "Car charge", "Alarm", "Heating", "Air quality", "Forecast")

/** One of the choices Home Assistant gives every Assist satellite: which assistant it talks to, its wake word, how quickly it decides you've finished. */
data class VoiceSetting(val label: String, val entity: String, val state: String, val options: List<String>)

/** A firmware update offered for a satellite: [entity] is the `update.` entity to install it with. */
data class SatUpdate(val entity: String, val installed: String?, val latest: String?, val available: Boolean, val installing: Boolean)

/** "3 d 4 h", "5 h 12 min", "8 min": how long it has been up, for the status card. */
fun formatUptime(seconds: Long): String {
    val d = seconds / 86_400
    val h = seconds % 86_400 / 3_600
    val m = seconds % 3_600 / 60
    return when {
        d > 0 -> "$d d $h h"
        h > 0 -> "$h h $m min"
        else -> "$m min"
    }
}

/** What Wi-Fi level in dBm means, in words, for the status card. */
fun signalWords(dbm: Int): String = when {
    dbm >= -55 -> "Excellent"
    dbm >= -67 -> "Good"
    dbm >= -75 -> "Fair"
    else -> "Weak"
}

/** One voice satellite as Home Assistant shows it: its settings are entities that share a prefix. */
data class Satellite(
    /** The entity id prefix, for example `clive_round_screen` (the part between the domain and `_design`). */
    val slug: String,
    val name: String,
    val design: SatDesign?,
    val glances: List<String>,
    val backlightDay: Float?,
    val backlightNight: Float?,
    val nightStart: Int?,
    val nightEnd: Int?,
    val microphone: Boolean?,
    val spokenAnswers: Boolean?,
    val followUp: Boolean?,
    val nightMode: Boolean?,
    val online: Boolean,
    val signal: String?,
    val volume: Float? = null,
    /** Seconds since it started, from its Uptime sensor. */
    val uptimeSeconds: Long? = null,
    val ip: String? = null,
    val network: String? = null,
    /** The ESPHome version it runs. */
    val firmware: String? = null,
    /** When it last changed between online and offline, as Home Assistant sends it. */
    val lastChanged: String? = null,
    /** The firmware update Home Assistant offers for it, if the ESPHome builder is installed. */
    val update: SatUpdate? = null,
    /** Which of the one-tap buttons the firmware has (older firmware lacks them). */
    val buttons: Set<String> = emptySet(),
    /** Whether Home Assistant has an Assist satellite for it, so it can be made to speak. */
    val canSpeak: Boolean = false,
    /** False for a satellite with no screen: it has no design, glances or backlight. */
    val hasScreen: Boolean = true,
    val voice: List<VoiceSetting> = emptyList(),
) {
    fun button(suffix: String) = "button.${slug}_$suffix"
    fun select(suffix: String) = "select.${slug}_$suffix"
    fun number(suffix: String) = "number.${slug}_$suffix"
    fun switch(suffix: String) = "switch.${slug}_$suffix"
}

private fun EntityState.options(): List<String> = attributes.optJSONArray("options")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList()

/**
 * The satellites in the house. One with a screen is found by its Design setting: a `select` entity named `..._design` whose options
 * are exactly the four designs. One without a screen has no such setting, so it is found as an Assist satellite that also has the
 * satellite's own "Follow-up listening" switch. Everything else about a satellite is an entity with the same prefix (ESPHome names them
 * after the device).
 */
fun findSatellites(entities: Map<String, EntityState>): List<Satellite> {
    val wanted = SatDesign.entries.map { it.label }
    val withScreen = entities.values
        .filter { it.entityId.startsWith("select.") && it.entityId.endsWith("_design") && it.options() == wanted }
        .associateBy { it.entityId.removePrefix("select.").removeSuffix("_design") }
    val withoutScreen = entities.values
        .filter { it.entityId.startsWith("assist_satellite.") }
        .map { it.entityId.removePrefix("assist_satellite.") }
        .filter { it !in withScreen && entities.containsKey("switch.${it}_follow_up_listening") }
    val slugs = withScreen.keys + withoutScreen
    return slugs.map { slug ->
        val d = withScreen[slug]
        val assist = entities["assist_satellite.$slug"]
        fun e(domain: String, suffix: String) = entities["$domain.${slug}_$suffix"]
        fun num(suffix: String) = e("number", suffix)?.state?.toFloatOrNull()
        fun on(suffix: String) = e("switch", suffix)?.state?.let { it == "on" }
        val anchor = d ?: assist ?: e("switch", "follow_up_listening")
        Satellite(
            slug = slug,
            name = (d?.friendlyName?.removeSuffix(" Design") ?: assist?.friendlyName).orEmpty().ifBlank { slug },
            design = SatDesign.from(d?.state),
            glances = (1..3).map { e("select", "glance_$it")?.state ?: "" },
            backlightDay = num("backlight_day"),
            backlightNight = num("backlight_night"),
            nightStart = num("night_starts_hour")?.toInt(),
            nightEnd = num("night_ends_hour")?.toInt(),
            microphone = on("microphone"),
            spokenAnswers = on("spoken_answers"),
            followUp = on("follow_up_listening"),
            nightMode = on("night_mode"),
            online = anchor?.available ?: false,
            signal = e("sensor", "wi_fi_signal")?.state?.takeIf { it != "unavailable" && it != "unknown" },
            volume = num("volume"),
            uptimeSeconds = e("sensor", "uptime")?.takeIf { it.available }?.state?.toDoubleOrNull()?.toLong(),
            ip = e("sensor", "ip_address")?.takeIf { it.available }?.state,
            network = e("sensor", "wi_fi_network")?.takeIf { it.available }?.state,
            firmware = e("sensor", "esphome_version")?.takeIf { it.available }?.state,
            lastChanged = anchor?.lastChanged,
            update = (entities["update.${slug}_firmware"] ?: entities["update.${slug}"])?.let { u ->
                SatUpdate(
                    entity = u.entityId,
                    installed = u.attributes.optString("installed_version").ifBlank { null },
                    latest = u.attributes.optString("latest_version").ifBlank { null },
                    available = u.state == "on",
                    installing = u.attributes.optBoolean("in_progress", false),
                )
            },
            buttons = listOf("restart", "identify", "clean_screen", "reset_pairing").filter { entities.containsKey("button.${slug}_$it") }.toSet(),
            canSpeak = assist != null,
            hasScreen = d != null,
            voice = listOf("Assistant" to "assistant", "Wake word" to "wake_word", "Finished speaking" to "finished_speaking_detection").mapNotNull { (label, suffix) ->
                e("select", suffix)?.takeIf { it.available }?.let { VoiceSetting(label, it.entityId, it.state, it.options()) }
            },
        )
    }.sortedBy { it.name.lowercase() }
}

/** The problems worth telling someone about, for the list: a weak signal, or offline for a while. */
fun Satellite.warning(nowMillis: Long = System.currentTimeMillis()): String? {
    if (!online) {
        val since = runCatching { java.time.OffsetDateTime.parse(lastChanged).toInstant().toEpochMilli() }.getOrNull()
        return if (since != null && nowMillis - since > 10 * 60_000L) "Offline for ${formatUptime((nowMillis - since) / 1000)}" else "Offline"
    }
    val dbm = signal?.toIntOrNull()
    return if (dbm != null && dbm < -75) "Weak Wi-Fi" else null
}
