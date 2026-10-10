package com.churchdrive.app

import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ui.CHARGE_LATER_SCRIPT
import com.churchdrive.app.ui.Octopus
import com.churchdrive.app.ui.SMART_ENABLED
import com.churchdrive.app.ui.SMART_STATE
import com.churchdrive.app.ui.SmartCharge
import com.churchdrive.app.ui.chargeAsk
import com.churchdrive.app.ui.chargeAskText
import com.churchdrive.app.ui.inText
import com.churchdrive.app.ui.smartCharge
import com.churchdrive.app.ui.smartText
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ChargePromptTest {
    private val half = 30 * 60_000L
    private val hour = 2 * half

    /** Half-hours from [from] on: [cheap] ranges of half-hour numbers (from 0) cost 4.8p, the rest 25.3p. */
    private fun rates(from: Long, count: Int, cheap: IntRange?): List<Pair<Long, Double>> =
        (0 until count).map { i -> from + i * half to if (cheap != null && i in cheap) 0.04755 else 0.253004 }

    private fun event(id: String, list: List<Pair<Long, Double>>) = EntityState(
        id, "x",
        JSONObject().put(
            "rates",
            JSONArray().also { arr ->
                list.forEach { (s, v) ->
                    arr.put(JSONObject().put("start", Instant.ofEpochMilli(s).toString()).put("end", Instant.ofEpochMilli(s + half).toString()).put("value_inc_vat", v))
                }
            },
        ),
    )

    private fun entities(list: List<Pair<Long, Double>>, smart: Boolean = true, later: Boolean = true, on: Boolean = true, phase: String = "Waiting"): Map<String, EntityState> {
        val m = mutableMapOf(
            "event.octopus_energy_electricity_1_2_current_day_rates" to event("event.octopus_energy_electricity_1_2_current_day_rates", list),
        )
        if (smart) {
            m[SMART_ENABLED] = EntityState(SMART_ENABLED, if (on) "on" else "off", JSONObject())
            m[SMART_STATE] = EntityState(SMART_STATE, phase, JSONObject())
        }
        if (later) m[CHARGE_LATER_SCRIPT] = EntityState(CHARGE_LATER_SCRIPT, "off", JSONObject())
        return m
    }

    // 12:00 now, with the half-hours from 11:30; the cheap run starts three hours on.
    private val t0 = 1_800_000_000_000L - 1_800_000_000_000L % hour
    private val now = t0 + 10 * 60_000L

    @Test
    fun theHouseWithoutSmartChargeAsksNothingAndShowsNoSwitch() {
        assertNull(smartCharge(entities(rates(t0 - half, 60, 7..16), smart = false)))
        assertNull(chargeAsk(entities(rates(t0 - half, 60, 7..16), smart = false, later = false), now))
    }

    @Test
    fun theNormalRateWithACheapOneComingAsksWhetherToWait() {
        val ask = chargeAsk(entities(rates(t0 - half, 60, 7..16)), now)
        assertNotNull(ask)
        assertEquals(25.3, ask!!.nowPence, 0.05)
        assertEquals(4.8, ask.cheapPence, 0.05)
        assertEquals(t0 - half + 7 * half, ask.startMs)
        assertTrue(chargeAskText(ask, now).contains("in 2 h 50 m"))
    }

    @Test
    fun noQuestionWhenTheRateIsCheapAlready() {
        assertNull(chargeAsk(entities(rates(t0 - half, 60, 0..9)), now))
    }

    @Test
    fun noQuestionWhenTheCheapRateIsMoreThanEighteenHoursAway() {
        assertNull(chargeAsk(entities(rates(t0 - half, 60, 45..55)), now))
    }

    @Test
    fun noQuestionWhenThereIsNoRealDifference() {
        assertNull(chargeAsk(entities(rates(t0 - half, 60, null)), now))
    }

    @Test
    fun theSwitchSaysWhatSmartChargeIsDoing() {
        val r = Octopus.allRates(entities(rates(t0 - half, 60, 7..16)))
        assertEquals("Off: it only charges when you start it", smartText(SmartCharge(false, "Waiting", true), r, now))
        assertTrue(smartText(SmartCharge(true, "Waiting", true), r, now).startsWith("Waiting for the cheap rate, "))
        assertEquals("Finished for this plug-in", smartText(SmartCharge(true, "Done", true), r, now))
        assertEquals("You're in control for this plug-in", smartText(SmartCharge(true, "Manual", true), r, now))
        assertEquals("Charges at the cheap rate when a car is plugged in", smartText(SmartCharge(true, "Idle", true), r, now))
        val cheapNow = Octopus.allRates(entities(rates(t0 - half, 60, 0..9)))
        assertTrue(smartText(SmartCharge(true, "Charging", true), cheapNow, now).startsWith("Charging at the cheap rate until "))
    }

    @Test
    fun howLongUntilIsInPlainWords() {
        assertEquals("45 min", inText(45 * 60_000L))
        assertEquals("3 h", inText(3 * hour))
        assertEquals("6 h 12 m", inText(6 * hour + 12 * 60_000L))
    }
}
