package com.churchdrive.app

import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ha.Registry
import com.churchdrive.app.ui.HealthLevel
import com.churchdrive.app.ui.deviceHealthList
import com.churchdrive.app.ui.deviceHealthSummary
import com.churchdrive.app.ui.deviceIdFor
import com.churchdrive.app.ui.healthOfDevice
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceHealthDataTest {
    private fun entity(id: String, state: String, vararg attrs: Pair<String, Any>) =
        EntityState(id, state, JSONObject().apply { attrs.forEach { put(it.first, it.second) } })

    private val registry = Registry(
        entityDevice = mapOf(
            "sensor.door_battery" to "door", "binary_sensor.door" to "door",
            "sensor.hall_battery" to "hall", "binary_sensor.hall_battery_plus_low" to "hall",
            "sensor.purifier_filter_life" to "purifier", "fan.purifier" to "purifier",
            "light.lamp" to "lamp", "sensor.phone_battery" to "phone",
            "switch.gone" to "gone", "sensor.gone_power" to "gone",
        ),
        deviceNames = mapOf("door" to "Front door sensor", "hall" to "Hall dial", "purifier" to "Air purifier", "lamp" to "Lamp", "phone" to "Phone", "gone" to "Smart plug"),
        entityArea = emptyMap(), deviceArea = mapOf("door" to "entrance"),
        entityPlatform = mapOf("sensor.phone_battery" to "mobile_app"),
    )
    private val entities = listOf(
        entity("sensor.door_battery", "62", "device_class" to "battery"),
        entity("binary_sensor.door", "off"),
        entity("sensor.hall_battery", "8", "device_class" to "battery"),
        entity("binary_sensor.hall_battery_plus_low", "on", "device_class" to "battery"),
        entity("sensor.purifier_filter_life", "12", "unit_of_measurement" to "%"),
        entity("fan.purifier", "on"),
        entity("light.lamp", "on"),
        entity("sensor.phone_battery", "5", "device_class" to "battery"),
        entity("switch.gone", "unavailable"),
        entity("sensor.gone_power", "unavailable"),
    ).associateBy { it.entityId }
    private val areas = mapOf("entrance" to "Entrance")

    @Test fun aBatteryReadsItsLevelAndAreaAndIsFineWhenHigh() {
        val d = healthOfDevice("door", entities, registry, areas)!!
        assertEquals("Front door sensor", d.name)
        assertEquals("Entrance", d.area)
        assertEquals(62, d.battery)
        assertEquals(HealthLevel.Ok, d.level)
        assertEquals("Battery 62%", d.summary)
        assertFalse(d.needsAttention)
    }

    @Test fun aNearlyFlatBatteryNeedsAttention() {
        val d = healthOfDevice("hall", entities, registry, areas)!!
        assertEquals(8, d.battery)
        assertTrue(d.batteryLow)
        assertEquals(HealthLevel.Bad, d.level)
    }

    @Test fun aLowFilterIsAWarning() {
        val d = healthOfDevice("purifier", entities, registry, areas)!!
        assertEquals(12, d.filter)
        assertEquals(HealthLevel.Warn, d.level)
        assertEquals("Filter 12%", d.summary)
    }

    @Test fun aDeviceWhoseEntitiesAreAllUnavailableIsNotResponding() {
        val d = healthOfDevice("gone", entities, registry, areas)!!
        assertTrue(d.offline)
        assertEquals(HealthLevel.Bad, d.level)
        assertEquals("Not responding", d.summary)
    }

    @Test fun theListHasOnlyTrackedDevicesWorstFirstAndNoPhones() {
        val list = deviceHealthList(entities, registry, areas)
        assertEquals(listOf("Hall dial", "Smart plug", "Air purifier", "Front door sensor"), list.map { it.name })
        assertFalse(list.any { it.deviceId == "lamp" || it.deviceId == "phone" })
        assertEquals("3 of 4 need attention", deviceHealthSummary(list))
        assertEquals("Batteries, filters and devices that stop responding", deviceHealthSummary(emptyList()))
    }

    @Test fun anEntityOrADeviceIdFindsTheDevice() {
        assertEquals("hall", deviceIdFor("binary_sensor.hall_battery_plus_low", registry))
        assertEquals("door", deviceIdFor("door", registry))
        assertNull(deviceIdFor("light.unknown", registry))
        assertNull(deviceIdFor("nonsense", registry))
    }
}
