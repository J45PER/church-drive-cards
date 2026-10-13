package com.churchdrive.app.ui

import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ha.Registry

/** A battery at or under this percent is low (amber); at or under [BATTERY_CRITICAL] it is nearly flat (red). */
const val BATTERY_LOW = 20
const val BATTERY_CRITICAL = 10

/** A filter with this much life left or less needs changing soon. */
const val FILTER_LOW = 15

enum class HealthLevel { Ok, Warn, Bad }

/** What the house knows about one device's health: its battery, filter, whether it answers, and when it was last heard from. */
data class DeviceHealthItem(
    val deviceId: String,
    val name: String,
    val area: String?,
    val entityIds: List<String>,
    /** The lowest battery percent among the device's batteries, or null. */
    val battery: Int?,
    /** A "battery low" sensor (Battery Notes) is on. */
    val batteryLow: Boolean,
    /** Filter life left in percent, or null. */
    val filter: Int?,
    /** Every one of its entities is unavailable. */
    val offline: Boolean,
    /** Church Drive's device health says it stopped responding (the reason, or "Not responding"). */
    val staleReason: String?,
    /** The most recent change among its entities (ISO 8601), if known. */
    val heard: String?,
) {
    val batteryLevel: HealthLevel?
        get() = when {
            battery != null -> if (battery <= BATTERY_CRITICAL) HealthLevel.Bad else if (battery <= BATTERY_LOW) HealthLevel.Warn else if (batteryLow) HealthLevel.Bad else HealthLevel.Ok
            batteryLow -> HealthLevel.Bad
            else -> null
        }
    val filterLevel: HealthLevel? get() = filter?.let { if (it <= FILTER_LOW) HealthLevel.Warn else HealthLevel.Ok }

    /** Whether it is worth showing at all in the Device Health list: it has a battery or filter, or it isn't answering. */
    val tracked: Boolean get() = battery != null || batteryLow || filter != null || offline || staleReason != null

    val level: HealthLevel
        get() = when {
            offline || batteryLevel == HealthLevel.Bad -> HealthLevel.Bad
            staleReason != null || batteryLevel == HealthLevel.Warn || filterLevel == HealthLevel.Warn -> HealthLevel.Warn
            else -> HealthLevel.Ok
        }

    val needsAttention: Boolean get() = level != HealthLevel.Ok

    /** One line: the things to know about it. */
    val summary: String
        get() = listOfNotNull(
            if (offline) "Not responding" else staleReason,
            battery?.let { "Battery $it%" } ?: if (batteryLow) "Battery low" else null,
            filter?.let { "Filter $it%" },
        ).distinct().joinToString(" · ").ifBlank { "All fine" }
}

private fun percentOf(e: EntityState): Int? = e.state.toDoubleOrNull()?.let { Math.round(it).toInt().coerceIn(0, 100) }

/** Everything the house knows about [deviceId]'s health, or null if the device has no entities. */
fun healthOfDevice(deviceId: String, entities: Map<String, EntityState>, registry: Registry, areaNames: Map<String, String>, stale: Map<String, String> = staleDevices(entities, registry)): DeviceHealthItem? {
    val ids = registry.entitiesOfDevice(deviceId)
    if (ids.isEmpty()) return null
    var battery: Int? = null
    var low = false
    var filter: Int? = null
    var heard: Long? = null
    var heardIso: String? = null
    val present = ids.mapNotNull { entities[it] }
    for (e in present) {
        val id = e.entityId
        val deviceClass = e.str("device_class")
        if (id.startsWith("sensor.") && deviceClass == "battery") percentOf(e)?.let { battery = minOf(battery ?: it, it) }
        if (id.startsWith("binary_sensor.") && (deviceClass == "battery" || id.endsWith("_battery_plus_low")) && e.state == "on") low = true
        if (id.startsWith("sensor.") && "filter" in id && e.str("unit_of_measurement") == "%") percentOf(e)?.let { filter = minOf(filter ?: it, it) }
        val at = e.lastChanged?.let { parseMillis(it) }
        if (at != null && (heard == null || at > heard)) { heard = at; heardIso = e.lastChanged }
    }
    return DeviceHealthItem(
        deviceId = deviceId,
        name = registry.deviceNameById(deviceId) ?: present.firstOrNull()?.friendlyName ?: deviceId,
        area = registry.areaOf(ids.first())?.let { areaNames[it] ?: it },
        entityIds = ids,
        battery = battery, batteryLow = low, filter = filter,
        offline = present.isNotEmpty() && present.all { it.state == "unavailable" },
        staleReason = stale[deviceId],
        heard = heardIso,
    )
}

/** The devices Church Drive's own watch says stopped responding, by device id, with the reason. */
fun staleDevices(entities: Map<String, EntityState>, registry: Registry): Map<String, String> =
    watchedDevices(entities).filter { !it.ok }.mapNotNull { w -> registry.deviceOf(w.id)?.let { it to w.reason.ifBlank { "Not responding" } } }.toMap()

/** The devices worth listing (a battery, a filter, or not answering), those needing attention first and then by name. Phones are left out. */
fun deviceHealthList(entities: Map<String, EntityState>, registry: Registry, areaNames: Map<String, String>): List<DeviceHealthItem> {
    val stale = staleDevices(entities, registry)
    return registry.deviceIds()
        .filter { d -> registry.entitiesOfDevice(d).none { registry.platformOf(it) == "mobile_app" } }
        .mapNotNull { healthOfDevice(it, entities, registry, areaNames, stale) }
        .filter { it.tracked }
        .sortedWith(compareByDescending<DeviceHealthItem> { it.level.ordinal }.thenBy { it.name.lowercase() })
}

/** The device a notification link or a long press names: an entity id (`binary_sensor.x`) means that entity's device; anything else is a device id. */
fun deviceIdFor(ref: String, registry: Registry): String? =
    if ('.' in ref) registry.deviceOf(ref) else ref.takeIf { it in registry.deviceIds() }

/** "2 need attention" / "All fine" / "" while there is nothing to say. */
fun deviceHealthSummary(items: List<DeviceHealthItem>): String {
    if (items.isEmpty()) return "Batteries, filters and devices that stop responding"
    val n = items.count { it.needsAttention }
    return if (n == 0) "All ${items.size} fine" else "$n of ${items.size} need attention"
}
