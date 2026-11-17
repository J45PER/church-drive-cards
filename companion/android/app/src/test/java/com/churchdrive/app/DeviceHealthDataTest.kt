package com.churchdrive.app

import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ha.Registry
import com.churchdrive.app.ui.HIDE_FROM_HEALTH_LABEL
import com.churchdrive.app.ui.HealthLevel
import com.churchdrive.app.ui.isCarDevice
import com.churchdrive.app.ui.deviceHealthList
import com.churchdrive.app.ui.deviceHealthSummary
import com.churchdrive.app.ui.deviceIdFor
import com.churchdrive.app.ui.healthOfDevice
import com.churchdrive.app.ui.percentLevel
import com.churchdrive.app.ui.BATTERY_CRITICAL
import com.churchdrive.app.ui.BATTERY_LOW
import com.churchdrive.app.ui.FILTER_CRITICAL
import com.churchdrive.app.ui.FILTER_LOW
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
            "switch.gone" to "gone", "sensor.gone_power" to "gone", "media_player.pc" to "pc",
        ),
        deviceNames = mapOf("door" to "Front door sensor", "hall" to "Hall dial", "purifier" to "Air purifier", "lamp" to "Lamp", "phone" to "Phone", "gone" to "Smart plug", "pc" to "Desktop PC"),
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
        entity("media_player.pc", "unavailable"),
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

    @Test fun aBatteryGettingLowIsAmberAndNearlyFlatIsRed() {
        assertEquals(HealthLevel.Ok, percentLevel(62, BATTERY_LOW, BATTERY_CRITICAL))
        assertEquals(HealthLevel.Ok, percentLevel(41, BATTERY_LOW, BATTERY_CRITICAL))
        assertEquals(HealthLevel.Warn, percentLevel(40, BATTERY_LOW, BATTERY_CRITICAL))
        assertEquals(HealthLevel.Warn, percentLevel(28, BATTERY_LOW, BATTERY_CRITICAL))
        assertEquals(HealthLevel.Bad, percentLevel(20, BATTERY_LOW, BATTERY_CRITICAL))
        assertEquals(HealthLevel.Warn, percentLevel(30, FILTER_LOW, FILTER_CRITICAL))
        assertEquals(HealthLevel.Bad, percentLevel(8, FILTER_LOW, FILTER_CRITICAL))
    }

    @Test fun aBatteryAt28PercentNeedsAttention() {
        val low = entities + entity("sensor.door_battery", "28", "device_class" to "battery").let { it.entityId to it }
        val d = healthOfDevice("door", low, registry, areas)!!
        assertEquals(HealthLevel.Warn, d.level)
        assertTrue(d.needsAttention)
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

    @Test fun aPCOrTVThatIsSwitchedOffIsNotNotResponding() {
        // Its only entity is a media player, which is unavailable whenever it is off: not a fault, and not in the list.
        val d = healthOfDevice("pc", entities, registry, areas)!!
        assertFalse(d.offline)
        assertFalse(d.tracked)
        assertFalse(deviceHealthList(entities, registry, areas).any { it.deviceId == "pc" })
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

    // ---- Cars and hidden devices stay out of the list

    private fun battery(id: String, state: String) = entity(id, state, "device_class" to "battery", "unit_of_measurement" to "%")

    /** The house's two cars, as the registry shows them, a Ring camera on both its integration and the add-on, and an alarm sensor with no twin. */
    private val garage = Registry(
        entityDevice = mapOf(
            "sensor.astra_battery" to "astra", "sensor.astra_service_battery" to "astra", "sensor.astra_range" to "astra",
            "sensor.octopus_id5_soc" to "id5", "sensor.octopus_meter_rate" to "meter",
            "sensor.front_door_battery" to "ring", "sensor.front_door_battery_3" to "mqtt", "sensor.hall_sensor_battery" to "alarm",
        ),
        deviceNames = mapOf("astra" to "Vauxhall Astra", "id5" to "Volkswagen ID.5", "meter" to "Smart meter", "ring" to "Front Doorbell", "mqtt" to "Front Door", "alarm" to "Hall sensor"),
        entityArea = emptyMap(), deviceArea = emptyMap(),
        entityPlatform = mapOf(
            "sensor.astra_battery" to "stellantis_vehicles", "sensor.astra_service_battery" to "stellantis_vehicles", "sensor.astra_range" to "stellantis_vehicles",
            "sensor.octopus_id5_soc" to "octopus_energy", "sensor.octopus_meter_rate" to "octopus_energy",
            "sensor.front_door_battery" to "ring", "sensor.front_door_battery_3" to "mqtt", "sensor.hall_sensor_battery" to "mqtt",
        ),
        labels = mapOf("sensor.front_door_battery_3" to setOf(HIDE_FROM_HEALTH_LABEL)),
    )
    private val garageStates = listOf(
        battery("sensor.astra_battery", "6"), battery("sensor.astra_service_battery", "98"), entity("sensor.astra_range", "0"),
        battery("sensor.octopus_id5_soc", "12"), entity("sensor.octopus_meter_rate", "unavailable"),
        battery("sensor.front_door_battery", "34"), battery("sensor.front_door_battery_3", "34"), battery("sensor.hall_sensor_battery", "67"),
    ).associateBy { it.entityId }

    @Test
    fun aCarIsNotADeviceToKeepHealthy() {
        // A car at 6% is how far it can go, not a battery to replace: the Astra's service battery and the ID.5's charge are left out too.
        assertTrue(isCarDevice("astra", garageStates, garage))
        assertTrue(isCarDevice("id5", garageStates, garage))
        val names = deviceHealthList(garageStates, garage, emptyMap()).map { it.name }
        assertFalse("Vauxhall Astra" in names)
        assertFalse("Volkswagen ID.5" in names)
    }

    @Test
    fun otherDevicesOfACarIntegrationAreStillListed() {
        // Octopus's smart meter is on the same integration as the ID.5 but has no charge level to be a car by (and here it is not answering).
        assertFalse(isCarDevice("meter", garageStates, garage))
        assertTrue("Smart meter" in deviceHealthList(garageStates, garage, emptyMap()).map { it.name })
    }

    @Test
    fun aDeviceWithTheHideLabelIsLeftOutAndItsTwinStays() {
        val names = deviceHealthList(garageStates, garage, emptyMap()).map { it.name }
        assertEquals(setOf("Front Doorbell", "Smart meter", "Hall sensor"), names.toSet())
        assertEquals(3, names.size)
    }
}
