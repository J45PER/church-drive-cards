package com.churchdrive.app

import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ha.parseNoticeButton
import com.churchdrive.app.ha.NoticeButton
import com.churchdrive.app.ui.BoostSwitch
import com.churchdrive.app.ui.CarEntities
import com.churchdrive.app.ui.CarRoleChoice
import com.churchdrive.app.ui.OctopusCar
import com.churchdrive.app.ui.PlanSlot
import com.churchdrive.app.ui.boostSwitch
import com.churchdrive.app.ui.boostText
import com.churchdrive.app.ui.carNeedsConfirm
import com.churchdrive.app.ui.carRoleChoice
import com.churchdrive.app.ui.carRoleText
import com.churchdrive.app.ui.octopusCarState
import com.churchdrive.app.ui.octopusPlan
import com.churchdrive.app.ui.planLine
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class CarConfirmTest {
    private fun e(id: String, state: String) = id to EntityState(id, state, JSONObject(), "2026-10-14T20:00:00+00:00")

    private fun house(plug: String, confirmed: String, smart: String) = mapOf(
        e("sensor.zappi_plug_status", plug), e("input_boolean.zappi_car_confirmed", confirmed), e("input_select.zappi_smart_charge_state", smart),
    )

    @Test fun aPluggedInCarNobodyHasAnsweredIsAsked() {
        assertTrue(carNeedsConfirm(house("EV Connected", "off", "Waiting")))
        assertTrue(carNeedsConfirm(house("Waiting for EV", "off", "Waiting")))
    }

    @Test fun nothingIsAskedWhenThereIsNothingToAsk() {
        assertFalse(carNeedsConfirm(house("EV Disconnected", "off", "Idle")))
        assertFalse(carNeedsConfirm(house("EV Connected", "on", "Waiting")))
        // After No, smart charge is Manual: not asked again until it is plugged in again.
        assertFalse(carNeedsConfirm(house("EV Connected", "off", "Manual")))
        // A car that is already charging carries on: no question.
        assertFalse(carNeedsConfirm(house("Charging", "off", "Waiting")))
        assertFalse(carNeedsConfirm(house("unavailable", "off", "Waiting")))
        assertFalse(carNeedsConfirm(mapOf(e("sensor.zappi_plug_status", "EV Connected"))))
    }

    @Test fun yesAndNoAreButtonsTheAppDoes() {
        assertEquals(NoticeButton.House("car_yes"), parseNoticeButton("CD_car_yes", null))
        assertEquals(NoticeButton.House("car_no"), parseNoticeButton("CD_car_no", null))
    }

    @Test fun primaryAndAdditionalAreButtonsTheAppDoes() {
        assertEquals(NoticeButton.House("car_primary"), parseNoticeButton("CD_car_primary", null))
        assertEquals(NoticeButton.House("car_additional"), parseNoticeButton("CD_car_additional", null))
    }

    private val role = "input_select.zappi_car_role"

    @Test fun theCarChoiceShowsOnlyWhileACarIsPluggedIn() {
        assertEquals(CarRoleChoice("Primary", true), carRoleChoice(house("EV Connected", "on", "Manual") + e(role, "Primary")))
        assertEquals(CarRoleChoice("Unknown", false), carRoleChoice(house("EV Connected", "off", "Waiting") + e(role, "Unknown")))
        assertNull(carRoleChoice(house("EV Disconnected", "on", "Idle") + e(role, "Primary")))
        // A house without the role helper has no choice to show.
        assertNull(carRoleChoice(house("EV Connected", "on", "Manual")))
    }

    @Test fun theChoiceSaysWhatTheRoleMeans() {
        assertEquals("Primary: also charges when Octopus schedules it", carRoleText(CarRoleChoice("Primary", true)))
        assertEquals("Additional: charges 23:30 to 05:30 only", carRoleText(CarRoleChoice("Additional", true)))
        assertEquals("Not set: charges 23:30 to 05:30 only", carRoleText(CarRoleChoice("Unknown", true)))
        assertEquals("Not answered yet, so it will not charge", carRoleText(CarRoleChoice("Unknown", false)))
    }

    private val noCar = CarEntities("", "", "", "", "", "", "", "", dispatch = "binary_sensor.dispatching")

    private fun dispatching(state: String, vararg attrs: Pair<String, String>) =
        "binary_sensor.dispatching" to EntityState("binary_sensor.dispatching", state, JSONObject().apply { attrs.forEach { put(it.first, it.second) } }, "2026-10-31T23:00:00+00:00")

    @Test fun anOctopusCarIsPluggedInAndChargingWhenItIsThePrimaryCarOnTheZappi() {
        val charging = mapOf(
            dispatching("on", "current_end" to "2026-11-01T05:30:00+00:00"), e("sensor.zappi_plug_status", "Charging"),
            e("sensor.zappi_charging_power", "7000"), e(role, "Primary"),
        )
        assertEquals(OctopusCar(true, true, "Octopus charging until 05:30"), octopusCarState(noCar, charging, ZoneOffset.UTC))
        // The Additional car is on the charger: this car is not plugged in or charging.
        assertEquals(OctopusCar(false, false, "Octopus charging until 05:30"), octopusCarState(noCar, charging + e(role, "Additional"), ZoneOffset.UTC))
        // Nobody has said which car it is.
        assertEquals(false, octopusCarState(noCar, charging + e(role, "Unknown"), ZoneOffset.UTC)?.plugged)
    }

    @Test fun anOctopusCarSaysWhenItsNextChargeIs() {
        val waiting = mapOf(
            dispatching("off", "next_start" to "2026-11-01T02:30:00+00:00", "next_end" to "2026-11-01T04:30:00+00:00"),
            e("sensor.zappi_plug_status", "EV Disconnected"), e(role, "Primary"),
        )
        assertEquals(OctopusCar(false, false, "Next Octopus charge 02:30 to 04:30"), octopusCarState(noCar, waiting, ZoneOffset.UTC))
    }

    private val boostId = "switch.octopus_energy_00000000_0002_4000_8020_00000012e6f1_intelligent_bump_charge"

    @Test fun boostShowsOnlyForThePrimaryCarWhilePluggedIn() {
        val primary = house("EV Connected", "on", "Manual") + e(role, "Primary") + e(boostId, "off")
        assertEquals(BoostSwitch(boostId, false), boostSwitch(primary))
        assertEquals(BoostSwitch(boostId, true), boostSwitch(primary + e(boostId, "on")))
        // The Additional car is not controlled by Octopus, so there is nothing to boost.
        assertNull(boostSwitch(primary + e(role, "Additional")))
        // Nothing plugged in, or a house without Octopus's Boost switch.
        assertNull(boostSwitch(house("EV Disconnected", "on", "Idle") + e(role, "Primary") + e(boostId, "off")))
        assertNull(boostSwitch(house("EV Connected", "on", "Manual") + e(role, "Primary")))
        assertNull(boostSwitch(primary + e(boostId, "unavailable")))
    }

    @Test fun boostSaysItIsAtTheStandardRate() {
        assertEquals("Charge now, at the standard rate, not the cheap rate", boostText(BoostSwitch(boostId, false)))
        assertEquals("On: Octopus is charging now, at the standard rate", boostText(BoostSwitch(boostId, true)))
    }

    private fun slot(start: String, end: String, kwh: Double, source: String) =
        JSONObject().put("start", start).put("end", end).put("charge_in_kwh", kwh).put("source", source)

    private fun planEntity(state: String, vararg slots: JSONObject): Map<String, EntityState> {
        val id = "binary_sensor.octopus_energy_x_intelligent_dispatching"
        val list = org.json.JSONArray().also { a -> slots.forEach { a.put(it) } }
        return mapOf(id to EntityState(id, state, JSONObject().put("planned_dispatches", list), "2026-11-01T00:00:00+00:00"))
    }

    private val now = java.time.Instant.parse("2026-11-01T00:30:00Z").toEpochMilli()

    @Test fun thePlanListsUnfinishedDispatchesOldestFirstWithPositiveKwh() {
        val plan = octopusPlan(
            planEntity(
                "on",
                slot("2026-11-01T02:30:00+00:00", "2026-11-01T04:30:00+00:00", -15.556, "SMART"),
                slot("2026-10-31T23:00:00+00:00", "2026-10-31T23:30:00+00:00", -3.889, "SMART"),
                slot("2026-11-01T00:00:00+00:00", "2026-11-01T01:00:00+00:00", -2.0, "BOOST"),
            ),
            now,
        )!!
        assertTrue(plan.active)
        assertEquals(listOf("BOOST" to 2.0, "SMART" to 15.556), plan.slots.map { it.source to it.kwh })
    }

    @Test fun aPlanLineSaysNowTheTimesTheKwhAndSmartOrBoost() {
        val boost = PlanSlot(now - 30 * 60_000, now + 30 * 60_000, 2.0, "BOOST")
        assertEquals("Now: 00:00 to 01:00 · 2.0 kWh · Boost", planLine(boost, now, ZoneOffset.UTC))
        assertEquals("02:30 to 04:30 · Smart", planLine(PlanSlot(now + 2 * 3_600_000, now + 4 * 3_600_000, 0.0, "SMART"), now, ZoneOffset.UTC))
    }

    @Test fun noOctopusDeviceMeansNoPlan() {
        assertNull(octopusPlan(house("EV Connected", "on", "Manual"), now))
        assertEquals(emptyList<PlanSlot>(), octopusPlan(planEntity("off"), now)!!.slots)
    }

    @Test fun aCarNotMadeByOctopusIsLeftAlone() {
        assertNull(octopusCarState(CarEntities("", "", "", "", "", "", "", ""), house("EV Connected", "on", "Manual"), ZoneOffset.UTC))
    }
}
