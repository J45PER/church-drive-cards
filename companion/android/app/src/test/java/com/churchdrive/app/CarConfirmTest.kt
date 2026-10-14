package com.churchdrive.app

import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ha.parseNoticeButton
import com.churchdrive.app.ha.NoticeButton
import com.churchdrive.app.ui.carNeedsConfirm
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
