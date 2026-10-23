package com.churchdrive.app

import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ui.CdIcons
import com.churchdrive.app.ui.GreggState
import com.churchdrive.app.ui.greggState
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
        assertEquals("mdi:home", CdIcons.alias("mdi:home"))
        assertNull(CdIcons.alias(null))
        assertFalse(CdIcons.icons.isEmpty())
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
}
