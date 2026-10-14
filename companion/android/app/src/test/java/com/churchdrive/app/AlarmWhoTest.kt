package com.churchdrive.app

import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ui.ALARM_ENTITY
import com.churchdrive.app.ui.alarmWho
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlarmWhoTest {
    private val alarm = EntityState(ALARM_ENTITY, "disarmed", JSONObject())

    private fun actor(who: String, state: String = "disarmed", at: String = "2026-10-13T14:00:05+00:00", id: String = ALARM_ENTITY) =
        EntityState("sensor.church_drive_alarm_actor", who, JSONObject().put("alarm", id).put("alarm_state", state).put("at", at))

    @Test
    fun theRecordedPersonBeatsRingsAccount() {
        assertEquals("Jamie" to "2026-10-13T14:00:05+00:00", alarmWho(alarm, actor("Jamie"), "Hayley Smith", "2026-10-13T14:00:02Z"))
    }

    @Test
    fun ringsNameIsUsedWhenNothingWasRecordedOrItWasNotALogin() {
        assertEquals("Hayley" to "2026-10-13T14:00:02Z", alarmWho(alarm, null, "Hayley", "2026-10-13T14:00:02Z"))
        assertEquals("Hayley" to "2026-10-13T14:00:02Z", alarmWho(alarm, actor("unknown"), "Hayley", "2026-10-13T14:00:02Z"))
    }

    @Test
    fun aRecordForAnotherStateOrAlarmIsNotUsed() {
        assertEquals("Hayley", alarmWho(alarm, actor("Jamie", state = "armed_away"), "Hayley", "2026-10-13T14:00:02Z")?.first)
        assertEquals("Hayley", alarmWho(alarm, actor("Jamie", id = "alarm_control_panel.other"), "Hayley", "2026-10-13T14:00:02Z")?.first)
    }

    @Test
    fun aLaterChangeFromTheKeypadIsRings() {
        assertEquals("Diane", alarmWho(alarm, actor("Jamie"), "Diane", "2026-10-13T14:30:00Z")?.first)
    }

    @Test
    fun withNeitherThereIsNoLine() {
        assertNull(alarmWho(alarm, null, null, null))
    }
}
