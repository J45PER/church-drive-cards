package com.churchdrive.app

import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ui.CdIcons
import com.churchdrive.app.ui.GreggState
import com.churchdrive.app.ui.greggEntityIcon
import com.churchdrive.app.ui.greggState
import com.churchdrive.app.ui.greggLevelIcon
import com.churchdrive.app.ui.greggStateFor
import com.churchdrive.app.ui.parseAssistantCard
import com.churchdrive.app.ui.word
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GreggIconTest {
    private fun e(id: String, state: String) = EntityState(id, state, JSONObject())
    private fun state(vacuum: String?, battery: String? = null) =
        greggState(vacuum?.let { e("vacuum.gregg", it) }, battery?.let { e("sensor.gregg_battery", it) })

    @Test fun eachVacuumStateHasItsOwnIcon() {
        assertEquals(GreggState.Cleaning, state("cleaning", "70"))
        assertEquals(GreggState.Paused, state("paused", "70"))
        assertEquals(GreggState.Returning, state("returning", "40"))
        assertEquals(GreggState.Error, state("error", "40"))
        assertEquals(GreggState.Idle, state("idle", "40"))
    }

    @Test fun dockedShowsTheBoltUntilTheBatteryIsFull() {
        assertEquals(GreggState.Charging, state("docked", "64"))
        assertEquals(GreggState.Charging, state("docked", "99"))
        assertEquals(GreggState.Charged, state("docked", "100"))
        // With no battery reading he is shown as charged rather than charging for ever.
        assertEquals(GreggState.Charged, state("docked"))
        assertEquals(GreggState.Charged, state("docked", "unavailable"))
    }

    @Test fun anUnknownGreggIsIdle() {
        assertEquals(GreggState.Idle, state(null))
        assertEquals(GreggState.Idle, state("unavailable"))
    }

    @Test fun theButtonsAndPartsAreInTheSet() {
        for (name in listOf("play", "pause", "stop", "home", "filter", "roller", "side")) {
            val shapes = CdIcons.icons[name]
            assertTrue(name, shapes != null && shapes.isNotEmpty())
            assertTrue(name, shapes!!.all { it.d.startsWith("M") })
        }
    }

    @Test fun buttonsAreBareGlyphsWithNoRing() {
        for (name in listOf("play", "pause", "stop", "home")) assertEquals(name, 1, CdIcons.icons[name]!!.size)
        // A part is the disc with its glyph, then the ring.
        assertEquals(2, CdIcons.icons["filter"]!!.size)
    }

    @Test fun theHuePackNamesForGreggsBrushesAreDrawnWithTheHousesOwn() {
        assertEquals("cd:roller", CdIcons.alias("phu:vac_mbrush"))
        assertEquals("cd:side", CdIcons.alias("phu:vac_sbrush"))
        // Home Assistant's houses are drawn with the house's own house: solid for home, outlined for out and away.
        assertEquals("cd:home", CdIcons.alias("mdi:home"))
        assertEquals("cd:home-away", CdIcons.alias("mdi:home-export-outline"))
        assertEquals("mdi:lightbulb", CdIcons.alias("mdi:lightbulb"))
        assertTrue(CdIcons.icons.containsKey("home-away"))
        assertNull(CdIcons.alias(null))
        assertFalse(CdIcons.icons.isEmpty())
    }

    @Test fun everyMicrophoneIsClives() {
        assertEquals("cd:mic", CdIcons.alias("mdi:microphone"))
        assertEquals("cd:mic-off", CdIcons.alias("mdi:microphone-off"))
        assertEquals("cd:mic-message", CdIcons.alias("mdi:microphone-message"))
        for (name in listOf("mic", "mic-off", "mic-message")) assertTrue(name, CdIcons.icons[name]?.all { it.d.startsWith("M") } == true)
    }

    @Test fun aCardCarriesTheVacuumStateAndBattery() {
        assertEquals(GreggState.Cleaning, greggStateFor("cleaning", 80.0))
        assertEquals(GreggState.Charging, greggStateFor("docked", 80.0))
        assertEquals(GreggState.Charged, greggStateFor("docked", 100.0))
        assertEquals("Heading home", GreggState.Returning.word())
        assertEquals("Docked", GreggState.Charged.word())
    }

    @Test fun theGreggAndAirCardsAreKept() {
        for (kind in listOf("gregg", "air")) {
            val card = parseAssistantCard(JSONObject("""{"kind":"$kind","state":"docked","battery":100,"score":98}"""))
            assertEquals(kind, card?.kind)
        }
    }

    @Test fun suctionAndWaterLevelsHaveTheirIcons() {
        val suction = listOf("Quiet", "Standard", "Turbo", "Max", "Boost_IQ")
        val water = listOf("Low", "Medium", "High")
        for (o in suction) assertTrue(o, CdIcons.icons.containsKey(greggLevelIcon("select.gregg_suction_level", o)!!.removePrefix("cd:")))
        for (o in water) assertTrue(o, CdIcons.icons.containsKey(greggLevelIcon("select.gregg_water_level", o)!!.removePrefix("cd:")))
        assertEquals("cd:suction-boost", greggLevelIcon("select.gregg_suction_level", "Boost IQ"))
        assertNull(greggLevelIcon("select.gregg_cleaning_intensity", "Normal"))
        assertNull(greggLevelIcon("select.gregg_mop_intensity", "Quiet"))
        assertNull(greggLevelIcon("select.gregg_water_level", "Quiet"))
    }

    @Test fun cleaningModesAndDockButtonsHaveTheirIcons() {
        for (o in listOf("Vacuum", "Mop", "Vacuum and mop", "Mopping after sweeping"))
            assertTrue(o, CdIcons.icons.containsKey(greggLevelIcon("select.gregg_cleaning_mode", o)!!.removePrefix("cd:")))
        for (id in listOf("button.gregg_empty_dust_bin", "button.gregg_wash_mop", "button.gregg_dry_mop", "switch.gregg_find_robot"))
            assertTrue(id, CdIcons.icons.containsKey(greggEntityIcon(id)!!.removePrefix("cd:")))
        assertNull(greggEntityIcon("button.gregg_reset_filter"))
    }
}
