package com.churchdrive.app.ui

import org.json.JSONObject
import java.util.TimeZone

/**
 * Tells the house which time zone this phone is in, so the assistant can give the time where the person is as well as the time at home (everything the
 * house itself says or shows is in the home's time zone). Each person has a text helper in Home Assistant, `input_text.clive_tz_<first name>`, that holds
 * "Europe/Athens|120": the zone's name and its offset from UTC in minutes. The assistant's instructions read it; when it matches the home's offset nothing changes.
 * (The helpers' ids carry the old name: they are Home Assistant's, set up there, and never change here.)
 */
object TimeZoneSync {
    /** The last value written during this run of the app, so nothing is sent again until the phone's zone really changes. */
    private var lastSent: String? = null

    /** The helper for [personName] ("Jamie Smith" is input_text.clive_tz_jamie: the first name, lower case, letters and digits only), or null with no name. */
    fun entityFor(personName: String?): String? {
        val first = personName?.trim()?.substringBefore(' ')?.lowercase()?.filter { it in 'a'..'z' || it in '0'..'9' }
        return first?.takeIf { it.isNotEmpty() }?.let { "input_text.clive_tz_$it" }
    }

    /** "Europe/Athens|120": the zone and its offset from UTC in minutes at [nowMs] (summer time included). */
    fun valueFor(zone: TimeZone = TimeZone.getDefault(), nowMs: Long = System.currentTimeMillis()): String = "${zone.id}|${zone.getOffset(nowMs) / 60_000}"

    /** Writes this phone's zone to the house if it differs from what was last written. A house with no such helper just refuses it, and it is tried again next time. */
    fun sync(api: HaApi, personName: String?) {
        val entity = entityFor(personName) ?: return
        val value = valueFor()
        if (value == lastSent) return
        lastSent = value
        api.request(
            "call_service",
            JSONObject().put("domain", "input_text").put("service", "set_value")
                .put("service_data", JSONObject().put("value", value))
                .put("target", JSONObject().put("entity_id", entity)),
        ) { result -> if (result == null) lastSent = null }
    }
}
