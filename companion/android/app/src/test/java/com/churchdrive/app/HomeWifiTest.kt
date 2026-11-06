package com.churchdrive.app

import com.churchdrive.app.house.HomeWifi
import com.churchdrive.app.house.HomeZone
import com.churchdrive.app.house.WifiDecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeWifiTest {
    @Test fun distanceIsInMetres() {
        assertEquals(0.0, HomeWifi.distanceM(53.2, -1.2, 53.2, -1.2), 0.001)
        // 0.001 degrees of latitude is about 111 m.
        assertEquals(111.2, HomeWifi.distanceM(53.2, -1.2, 53.201, -1.2), 1.0)
        // The home zone's centre and where three phones sit indoors: about 60 m apart.
        assertEquals(62.0, HomeWifi.distanceM(53.1996915, -1.2165877, 53.1991368, -1.2166377), 3.0)
    }

    @Test fun androidsQuotedNameIsCleaned() {
        assertEquals("Church Drive", HomeWifi.cleanSsid("\"Church Drive\""))
        assertEquals("Church Drive", HomeWifi.cleanSsid("  Church Drive "))
        assertNull(HomeWifi.cleanSsid("<unknown ssid>"))
        assertNull(HomeWifi.cleanSsid("0x"))
        assertNull(HomeWifi.cleanSsid("\"\""))
        assertNull(HomeWifi.cleanSsid(null))
    }

    @Test fun aNetworkIsLearnedAfterThreeSightingsInsideTheHomeZone() {
        var counts = emptyMap<String, Int>()
        repeat(2) { counts = HomeWifi.learn(counts, "Church Drive", 30.0, 12f, 100.0) }
        assertTrue(HomeWifi.learned(counts).isEmpty())
        counts = HomeWifi.learn(counts, "Church Drive", 30.0, 12f, 100.0)
        assertEquals(setOf("Church Drive"), HomeWifi.learned(counts))
    }

    @Test fun aSightingOutsideTheZoneOrWithAPoorFixDoesNotCount() {
        val none = emptyMap<String, Int>()
        assertEquals(none, HomeWifi.learn(none, "Cafe", 400.0, 10f, 100.0))
        assertEquals(none, HomeWifi.learn(none, "Cafe", 30.0, 80f, 100.0))
        assertEquals(none, HomeWifi.learn(none, null, 30.0, 10f, 100.0))
    }

    @Test fun onlyTheMostSeenNetworksCountAsHome() {
        val counts = mapOf("a" to 10, "b" to 9, "c" to 8, "d" to 7, "e" to 1)
        assertEquals(setOf("a", "b", "c"), HomeWifi.learned(counts))
    }

    @Test fun homeWifiNearHomeIsHomeButTheSameNameFarAwayIsNot() {
        assertEquals(WifiDecision.Home, HomeWifi.decide(true, 40.0))
        assertEquals(WifiDecision.Home, HomeWifi.decide(true, null))
        assertEquals(WifiDecision.Home, HomeWifi.decide(true, HomeWifi.SNAP_RADIUS_M))
        assertEquals(WifiDecision.Raw, HomeWifi.decide(true, 1_500.0))
        assertEquals(WifiDecision.Raw, HomeWifi.decide(false, 10.0))
    }

    @Test fun theHomeZoneIsFoundInTheHousesZones() {
        val answer = """[
            {"entity_id":"zone.work","attributes":{"latitude":53.1,"longitude":-1.3,"radius":50}},
            {"entity_id":"zone.home","state":"2","attributes":{"latitude":53.1996915,"longitude":-1.2165877,"radius":100,"friendly_name":"Church Drive"}}
        ]"""
        assertEquals(HomeZone(53.1996915, -1.2165877, 100.0), HomeWifi.parseHome(answer))
    }

    @Test fun anAnswerWithoutAHomeZoneIsNothing() {
        assertNull(HomeWifi.parseHome(null))
        assertNull(HomeWifi.parseHome("not json"))
        assertNull(HomeWifi.parseHome("[]"))
        assertNull(HomeWifi.parseHome("""[{"entity_id":"zone.home","attributes":{}}]"""))
        assertNotNull(HomeWifi.parseHome("""[{"entity_id":"zone.home","attributes":{"latitude":1.0,"longitude":2.0}}]"""))
    }
}
