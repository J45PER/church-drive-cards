package com.churchdrive.app.ui

import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ha.Registry

/** One smoke, heat or carbon monoxide alarm, with what its device also reports. */
data class SafetyItem(
    val id: String,
    val name: String,
    val kind: String,
    val on: Boolean,
    val since: String?,
    val unavailable: Boolean,
    val battery: Int?,
    val report: String?,
    val ppm: Double?,
)

private val SAFETY_CLASSES = setOf("smoke", "heat", "carbon_monoxide")

/**
 * Every smoke, heat and CO alarm (found by device class, or the [chosen] ones), alarms going off first.
 * Each one's battery, last check-in and CO reading are found on its own device, as the dashboard's Safety
 * card does.
 */
fun safetyItems(entities: Map<String, EntityState>, registry: Registry, chosen: List<String> = emptyList()): List<SafetyItem> {
    val ids = if (chosen.isNotEmpty()) chosen.filter { entities.containsKey(it) }
    else entities.keys.filter { it.startsWith("binary_sensor.") && entities[it]?.str("device_class") in SAFETY_CLASSES }

    return ids.mapNotNull { id ->
        val state = entities[id] ?: return@mapNotNull null
        val siblings = registry.siblings(id)
        fun sibling(test: (String, EntityState) -> Boolean) =
            siblings.firstOrNull { s -> entities[s]?.let { test(s, it) } == true }?.let { entities[it] }

        val kind = state.str("device_class") ?: "smoke"
        val battery = sibling { s, e -> s.startsWith("sensor.") && e.str("device_class") == "battery" && !s.contains("_plus") }
            ?: sibling { s, e -> s.startsWith("sensor.") && e.str("device_class") == "battery" }
        val report = sibling { s, _ -> s.endsWith("report_time") }
        val ppm = if (kind == "carbon_monoxide") sibling { s, e -> s.startsWith("sensor.") && e.str("unit_of_measurement") == "ppm" } else null

        SafetyItem(
            id = id,
            name = registry.deviceName(id) ?: state.friendlyName.replace(Regex(" alarm status$", RegexOption.IGNORE_CASE), ""),
            kind = kind,
            on = state.state == "on",
            since = state.lastChanged,
            unavailable = state.state == "unavailable",
            battery = battery?.state?.toDoubleOrNull()?.let { Math.round(it).toInt() },
            report = report?.state,
            ppm = ppm?.state?.toDoubleOrNull(),
        )
    }.sortedByDescending { it.on }
}

/** The line under an alarm's name: what's wrong, or all clear with its battery and last check-in. */
fun safetySubtitle(item: SafetyItem): String =
    if (item.on) "ALARM since ${clockTime(item.since)}"
    else listOfNotNull(
        if (item.unavailable) "Not responding" else "All clear",
        item.battery?.let { "battery $it%" },
        shortDay(item.report).takeIf { it.isNotEmpty() }?.let { "checked in $it" },
    ).joinToString(" · ")
