package com.churchdrive.app

import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ha.parseNoticeButton
import com.churchdrive.app.ha.NoticeButton
import com.churchdrive.app.ui.CarEntities
import com.churchdrive.app.ui.CarRoleChoice
import com.churchdrive.app.ui.OctopusCar
import com.churchdrive.app.ui.carNeedsConfirm
import com.churchdrive.app.ui.carRoleChoice
import com.churchdrive.app.ui.carRoleText
import com.churchdrive.app.ui.octopusCarState
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

    @Test fun aCarNotMadeByOctopusIsLeftAlone() {
        assertNull(octopusCarState(CarEntities("", "", "", "", "", "", "", ""), house("EV Connected", "on", "Manual"), ZoneOffset.UTC))
    }
}
