package com.churchdrive.app

import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ui.comfortRange
import com.churchdrive.app.ui.comfortTone
import com.churchdrive.app.ui.Tone
import com.churchdrive.app.ui.withLiveRange
import com.churchdrive.app.ui.zoneControl
import com.churchdrive.app.ui.zoneStatusText
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

class ThermostatSheetTest {
    private fun e(id: String, state: String, attrs: JSONObject = JSONObject()) = EntityState(id, state, attrs)

    private fun house(auto: String, resumeSeconds: Double? = null): Map<String, EntityState> = listOfNotNull(
        e("input_boolean.clive_climate_downstairs_auto", auto),
        e("input_number.clive_climate_downstairs_low", "18.0"),
        e("input_number.clive_climate_downstairs_high", "21.0"),
        resumeSeconds?.let { e("input_datetime.clive_climate_downstairs_resume", "2026-10-25 12:24:00", JSONObject().put("timestamp", it)) },
        e("input_text.clive_climate_downstairs_note", "07:12: Raised the heating to 19.5°C."),
    ).associateBy { it.entityId }

    @Test
    fun noSwitchInTheHouseMeansNothingIsShown() {
        assertNull(zoneControl(emptyMap(), "climate.downstairs"))
        assertNull(zoneControl(mapOf("input_boolean.clive_climate_downstairs_auto" to e("input_boolean.clive_climate_downstairs_auto", "unavailable")), "climate.downstairs"))
    }

    @Test
    fun theZoneKnowsItsRangeAndWhoIsInCharge() {
        val z = zoneControl(house("on"), "climate.downstairs")!!
        assertEquals("downstairs", z.zone)
        assertEquals(true, z.controlled)
        assertEquals(18.0, z.lowValue!!, 0.0)
        assertEquals(21.0, z.highValue!!, 0.0)
        assertNotNull(z.note)
    }

    @Test
    fun statusSaysManualControlledOrPausedUntil() {
        val now = System.currentTimeMillis()
        assertEquals("Manual", zoneStatusText(zoneControl(house("off"), "climate.downstairs")!!, "Clive", now))
        assertEquals("Clive controlled", zoneStatusText(zoneControl(house("on"), "climate.downstairs")!!, "Clive", now))
        // A pause that ended already, or the year 2000 (no pause), is not shown.
        assertEquals("Clive controlled", zoneStatusText(zoneControl(house("on", now / 1000.0 - 60), "climate.downstairs")!!, "Clive", now))
        assertEquals("Clive controlled", zoneStatusText(zoneControl(house("on", 946_684_800.0), "climate.downstairs")!!, "Clive", now))
        val until = now + 3_600_000
        val clock = SimpleDateFormat("HH:mm", Locale.UK).format(java.util.Date(until))
        assertEquals("Clive controlled · paused until $clock", zoneStatusText(zoneControl(house("on", until / 1000.0), "climate.downstairs")!!, "Clive", now))
    }

    @Test
    fun aRoomsRangeIsItsOwnNumbersElseItsTypes() {
        assertEquals(19.0 to 22.0, comfortRange("living"))
        assertEquals(18.0 to 21.0, comfortRange("living", 18.0, 21.0))
        assertEquals(18.0 to 22.0, comfortRange("living", 18.0, null))
        assertEquals(16.0 to 21.0, comfortRange("nonsense"))
        // The top never sits at or under the bottom.
        assertEquals(20.0 to 20.5, comfortRange("living", 20.0, 19.0))
    }

    @Test
    fun theColourFollowsTheRangeGiven() {
        assertEquals(Tone.Teal, comfortTone(18.5, "living"))
        assertEquals(Tone.Green, comfortTone(18.5, "living", 18.0, 21.0))
    }

    @Test
    fun theGraphTakesTheAssistantsRangeForAThermostat() {
        val zone = zoneControl(house("on"), "climate.downstairs")
        val card = JSONObject().put("entity", "climate.downstairs").put("room_type", "living")
        val live = withLiveRange(card, house("on"), zone)
        assertEquals(18.0, live.getDouble("comfort_low"), 0.0)
        assertEquals(21.0, live.getDouble("comfort_high"), 0.0)
        // The card itself is not changed, and a thermostat the assistant doesn't look after keeps the card's own range.
        assertEquals(false, card.has("comfort_low"))
        assertEquals(card, withLiveRange(card, emptyMap(), null))
    }

    @Test
    fun aNamedNumberHelperWinsOverTheAssistantsRange() {
        val entities = house("on") + ("input_number.hall_low" to e("input_number.hall_low", "17.5"))
        val card = JSONObject().put("comfort_low_entity", "input_number.hall_low")
        val live = withLiveRange(card, entities, zoneControl(entities, "climate.downstairs"))
        assertEquals(17.5, live.getDouble("comfort_low"), 0.0)
        assertEquals(21.0, live.getDouble("comfort_high"), 0.0)
    }
}
