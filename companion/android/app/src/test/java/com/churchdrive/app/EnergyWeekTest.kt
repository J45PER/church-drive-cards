package com.churchdrive.app

import com.churchdrive.app.ui.EnergyWeek
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

class EnergyWeekTest {
    private val london = ZoneId.of("Europe/London")
    private val elec = "sensor.octopus_energy_electricity_24l3478610_1160001231179_current_rate"
    private val gas = "sensor.octopus_energy_gas_e6s16825432562_7586596805_current_rate"
    private val ids = EnergyWeek.ids(elec, gas)
    private val now = ZonedDateTime.of(2026, 10, 11, 14, 30, 0, 0, london).toInstant().toEpochMilli()

    /** The start of an October day in London, as Home Assistant's statistics give it. */
    private fun day(n: Int) = ZonedDateTime.of(2026, 10, n, 0, 0, 0, 0, london).toInstant().toEpochMilli()

    private fun rows(vararg pairs: Pair<Int, Double>) = JSONArray().also { a ->
        pairs.forEach { (n, v) -> a.put(JSONObject().put("start", day(n)).put("change", v)) }
    }

    @Test
    fun findsTheStatisticIdsFromTheMeters() {
        assertEquals("octopus_energy:electricity_24l3478610_1160001231179_previous_accumulative_cost", ids.elecCost)
        assertEquals("octopus_energy:gas_e6s16825432562_7586596805_previous_accumulative_consumption_kwh", ids.gasKwh)
        assertNull(EnergyWeek.ids(null, gas).elecCost)
    }

    @Test
    fun theSevenDaysEndYesterday() {
        val days = EnergyWeek.lastDays(now, london)
        assertEquals(LocalDate.of(2026, 10, 4), days.first())
        assertEquals(LocalDate.of(2026, 10, 10), days.last())
        assertEquals(7, days.size)
    }

    @Test
    fun electricityBehindGasShowsGapsAndSaysHowFar() {
        val result = JSONObject()
            .put(ids.elecCost!!, rows(4 to 1.64, 5 to 4.83, 6 to 2.54, 7 to 2.17))
            .put(ids.elecKwh!!, rows(4 to 7.4, 5 to 44.2, 6 to 12.2, 7 to 7.9))
            .put(ids.gasCost!!, rows(4 to 0.3, 5 to 0.3, 6 to 0.3, 7 to 0.3, 8 to 0.3, 9 to 0.3))
        val w = EnergyWeek.build(result, ids, now, london)
        assertEquals(2.17, w.days[3].elecCost!!, 0.001) // Wed 7 Oct
        assertNull(w.days[4].elecCost) // Thu 8 Oct: Octopus hasn't sent electricity
        assertEquals(0.3, w.days[4].gasCost!!, 0.001)
        assertNull(w.days[6].gasCost)
        assertEquals(3, w.elecBehind)
        assertEquals(1, w.gasBehind)
        assertEquals(11.18, w.elecCost, 0.001)
        val note = EnergyWeek.lagNote(w) { it.toString() }
        assertEquals("Electricity is 3 days behind (latest 2026-10-07) · Gas is 1 day behind (latest 2026-10-09)", note)
    }

    @Test
    fun aMissingMeterIsNotReportedBehind() {
        val onlyElec = EnergyWeek.ids(elec, null)
        val w = EnergyWeek.build(JSONObject().put(onlyElec.elecCost!!, rows(10 to 2.0)), onlyElec, now, london)
        assertEquals("", EnergyWeek.lagNote(w) { it.toString() })
        assertEquals(LocalDate.of(2026, 10, 10), w.latestWithData)
    }

    @Test
    fun cheapShareAndBalance() {
        val result = JSONObject()
            .put(ids.elecCost!!, rows(9 to 3.0))
            .put(ids.gasCost!!, rows(9 to 1.0))
            .put(ids.offPeak!!, rows(9 to 6.0))
            .put(ids.peak!!, rows(9 to 4.0))
        val w = EnergyWeek.build(result, ids, now, london)
        assertEquals(60, w.cheapShare)
        assertEquals(60, w.days[5].cheapShare)
        val b = EnergyWeek.balance(w)
        assertNotNull(b)
        assertEquals(75, b!!.elec)
        assertEquals(25, b.gas)
        assertNull(EnergyWeek.balance(EnergyWeek.build(JSONObject(), ids, now, london)))
    }

    @Test
    fun daysFollowTheZoneGiven() {
        // The same instant is the 6th in UTC but the 7th in London: the statistics are cut at London's midnight.
        val result = JSONObject().put(ids.elecCost!!, rows(7 to 2.17))
        assertEquals(2.17, EnergyWeek.build(result, ids, now, london).days.first { it.date.dayOfMonth == 7 }.elecCost!!, 0.001)
        assertNull(EnergyWeek.build(result, ids, now, ZoneOffset.UTC).days.firstOrNull { it.date.dayOfMonth == 7 }?.elecCost)
    }
}
