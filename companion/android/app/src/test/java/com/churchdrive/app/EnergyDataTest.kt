package com.churchdrive.app

import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ui.Octopus
import com.churchdrive.app.ui.SystemRows
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class EnergyDataTest {
    private fun state(id: String, state: String, vararg attrs: Pair<String, Any>) =
        EntityState(id, state, JSONObject().apply { attrs.forEach { put(it.first, it.second) } })

    private fun rate(start: String, end: String, v: Double) =
        JSONObject().put("start", start).put("end", end).put("value_inc_vat", v)

    private val event = state(
        "event.octopus_energy_electricity_1_2_current_day_rates", "x",
        "rates" to JSONArray()
            .put(rate("2026-10-03T00:00:00+00:00", "2026-10-03T00:30:00+00:00", 0.075))
            .put(rate("2026-10-03T00:30:00+00:00", "2026-10-03T01:00:00+00:00", 0.075))
            .put(rate("2026-10-03T01:00:00+00:00", "2026-10-03T01:30:00+00:00", 0.246)),
    )
    private val entities = mapOf(event.entityId to event)
    private fun ms(iso: String) = Instant.parse(iso).toEpochMilli()

    @Test
    fun findsEntitiesWithoutKnowingTheAccountNumber() {
        assertNotNull(Octopus.find(entities, Octopus.currentDay))
        assertNull(Octopus.find(entities, Octopus.gasRate))
    }

    @Test
    fun cheapNowRunsToTheEndOfTheCheapPeriod() {
        val rates = Octopus.allRates(entities)
        assertEquals(3, rates.size)
        val until = Octopus.cheapUntil(rates, ms("2026-10-03T00:10:00Z"))
        assertEquals(ms("2026-10-03T01:00:00Z"), until)
        assertTrue(Octopus.rateLine(rates, ms("2026-10-03T00:10:00Z")).startsWith("Cheap now 7.5p"))
    }

    @Test
    fun peakNamesWhenCheapReturns() {
        val rates = Octopus.allRates(entities)
        assertNull(Octopus.cheapUntil(rates, ms("2026-10-03T01:10:00Z")))
        assertTrue(Octopus.rateLine(rates, ms("2026-10-03T01:10:00Z")).startsWith("Peak 24.6p"))
        assertEquals("No rates yet", Octopus.rateLine(emptyList(), 0))
    }

    @Test
    fun unavailableValuesShowADash() {
        assertEquals("–", Octopus.pounds(null))
        assertEquals("£1.50", Octopus.pounds(state("sensor.x", "1.5")))
        assertEquals("7.5p", Octopus.rateText(state("sensor.x", "0.075")))
        assertEquals("850 W", Octopus.power(state("sensor.x", "850")))
        assertEquals("1.5 kW", Octopus.power(state("sensor.x", "1500")))
    }

    @Test
    fun systemRowsShowInternetBackupsAndUpdates() {
        val e = listOf(
            state("binary_sensor.eero_wan_status", "off", "device_class" to "connectivity"),
            state("update.core", "on", "title" to "Core"),
        ).associateBy { it.entityId }
        val rows = SystemRows.rows(e)
        assertTrue(rows.first { it.name == "Internet" }.bad)
        assertEquals("Core", rows.first { it.name == "Updates" }.sub)
        assertFalse(rows.any { it.name == "Backups" })
    }

    @Test
    fun updateIsOfferedOnlyWhenTheReleaseCameFromAnotherCommit() {
        val body = "Latest test build from the companion-apps branch (commit abc1234). Download it."
        assertEquals("abc1234", UpdateCheck.commitOf(body))
        assertFalse(UpdateCheck.isNewer(body, "abc1234"))
        assertTrue(UpdateCheck.isNewer(body, "def5678"))
        assertFalse(UpdateCheck.isNewer(null, "def5678"))
        assertFalse(UpdateCheck.isNewer(body, ""))
    }
}
