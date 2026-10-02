package com.churchdrive.app

import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ha.Registry
import com.churchdrive.app.ui.DashboardPanels
import com.churchdrive.app.ui.Tone
import com.churchdrive.app.ui.clockTime
import com.churchdrive.app.ui.safetyItems
import com.churchdrive.app.ui.safetySubtitle
import com.churchdrive.app.ui.shortDay
import com.churchdrive.app.ui.toneFromColour
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId

class SecurityPageTest {
    private val dashboard = JSONObject(
        """
        {"views":[
          {"path":"home","sections":[]},
          {"path":"security","sections":[{"cards":[{"type":"custom:auto-layout-card","cards":[
            {"type":"custom:section-panel-card","title":"Alarm","icon":"mdi:shield-home","color":"green",
             "summary":"{{ states('alarm_control_panel.a') }}","color_template":"{{ 'red' }}",
             "cards":[{"type":"custom:alarm-panel-card","entity":"alarm_control_panel.a"}]},
            {"type":"custom:section-panel-card","title":"Safety","icon":"mdi:fire-alert",
             "cards":[{"type":"custom:safety-card"},{"type":"tile","entity":"sensor.safe_mode"},
                      {"type":"vertical-stack","cards":[{"type":"tile","entity":"number.volume"}]}]},
            {"type":"custom:nav-bar-card","pages":[]}
          ]}]}]}
        ]}
        """,
    )

    @Test
    fun readsThePanelsOfAPage() {
        val security = DashboardPanels.parse(dashboard)["security"]!!
        assertEquals(listOf("Alarm", "Safety"), security.map { it.title })
        val alarm = security[0]
        assertEquals("mdi:shield-home", alarm.icon)
        assertEquals("green", alarm.color)
        assertEquals("{{ states('alarm_control_panel.a') }}", alarm.summaryTemplate)
        assertEquals("{{ 'red' }}", alarm.colorTemplate)
        assertNull(security[1].summaryTemplate)
    }

    @Test
    fun cardsKeepTheirOrderAndStacksAreOpenedUp() {
        val cards = DashboardPanels.parse(dashboard)["security"]!![1].cards
        assertEquals(listOf("custom:safety-card", "tile", "tile"), cards.map { it.type })
        assertEquals("number.volume", cards[2].config.getString("entity"))
        assertEquals(emptyList<Any>(), DashboardPanels.parse(dashboard)["home"])
    }

    @Test
    fun colourNamesAndHexMapToTones() {
        assertEquals(Tone.Green, toneFromColour("green"))
        assertEquals(Tone.Orange, toneFromColour("deep-orange"))
        assertEquals(Tone.Grey, toneFromColour("blue-grey"))
        assertEquals(Tone.Red, toneFromColour("#e53935"))
        assertEquals(Tone.Green, toneFromColour("#4caf50"))
        assertEquals(Tone.Amber, toneFromColour("#ffa726"))
        assertEquals(Tone.Indigo, toneFromColour("#5c6bc0"))
        assertEquals(Tone.Purple, toneFromColour("#7e57c2"))
        assertEquals(Tone.Teal, toneFromColour("#00b8d4"))
        assertEquals(Tone.Grey, toneFromColour("#8b919c"))
        assertNull(toneFromColour("not a colour"))
        assertNull(toneFromColour(null))
    }

    private fun state(id: String, state: String, attrs: String, changed: String? = null) =
        EntityState(id, state, JSONObject(attrs), changed)

    private val registry = Registry.parse(
        JSONObject(
            """{"entities":[
              {"ei":"binary_sensor.hall_smoke","di":"d1"},{"ei":"sensor.hall_battery","di":"d1"},
              {"ei":"sensor.hall_battery_plus","di":"d1"},{"ei":"sensor.hall_report_time","di":"d1"},
              {"ei":"binary_sensor.co","di":"d2"},{"ei":"sensor.co_reading","di":"d2"},
              {"ei":"binary_sensor.no_device"}]}""",
        ),
        JSONArray("""[{"id":"d1","name":"Hall smoke alarm"},{"id":"d2","name":"CO","name_by_user":"Carbon monoxide"}]"""),
    )

    private val entities = listOf(
        state("binary_sensor.hall_smoke", "off", """{"device_class":"smoke","friendly_name":"Hall Alarm Status"}""", "2026-10-02T13:25:44.672Z"),
        state("sensor.hall_battery", "96.4", """{"device_class":"battery"}"""),
        state("sensor.hall_battery_plus", "99", """{"device_class":"battery"}"""),
        state("sensor.hall_report_time", "2026-09-22T09:00:00+00:00", "{}"),
        state("binary_sensor.co", "on", """{"device_class":"carbon_monoxide","friendly_name":"CO alarm"}""", "2026-10-02T13:25:00Z"),
        state("sensor.co_reading", "12", """{"unit_of_measurement":"ppm"}"""),
        state("binary_sensor.no_device", "unavailable", """{"device_class":"heat","friendly_name":"Kitchen heat Alarm Status"}"""),
        state("binary_sensor.door", "off", """{"device_class":"door"}"""),
    ).associateBy { it.entityId }

    @Test
    fun findsEveryAlarmWithItsDeviceReadings() {
        val items = safetyItems(entities, registry)
        // Alarms going off come first; a door sensor isn't an alarm.
        assertEquals("binary_sensor.co", items.first().id)
        assertEquals(setOf("binary_sensor.co", "binary_sensor.hall_smoke", "binary_sensor.no_device"), items.map { it.id }.toSet())
        val hall = items.first { it.id == "binary_sensor.hall_smoke" }
        assertEquals("Hall smoke alarm", hall.name)
        assertEquals(96, hall.battery) // the plain battery, not the "_plus" one
        assertEquals("2026-09-22T09:00:00+00:00", hall.report)
        val co = items.first { it.id == "binary_sensor.co" }
        assertEquals("Carbon monoxide", co.name)
        assertEquals(12.0, co.ppm!!, 0.0)
        // With no device, the entity's own name is used, without " Alarm Status".
        assertEquals("Kitchen heat", items.first { it.id == "binary_sensor.no_device" }.name)
    }

    @Test
    fun theLineUnderAnAlarm() {
        val hall = safetyItems(entities, registry).first { it.id == "binary_sensor.hall_smoke" }
        assertEquals("All clear · battery 96% · checked in 22 Sep", safetySubtitle(hall))
        val co = safetyItems(entities, registry).first { it.id == "binary_sensor.co" }
        assertEquals("ALARM since ${clockTime("2026-10-02T13:25:00Z")}", safetySubtitle(co))
        val gone = safetyItems(entities, registry).first { it.id == "binary_sensor.no_device" }
        assertEquals("Not responding", safetySubtitle(gone))
    }

    @Test
    fun onlyTheChosenAlarmsWhenTheCardNamesThem() {
        val items = safetyItems(entities, registry, listOf("binary_sensor.hall_smoke", "binary_sensor.missing"))
        assertEquals(listOf("binary_sensor.hall_smoke"), items.map { it.id })
    }

    @Test
    fun timesAreShownInTheGivenZone() {
        val london = ZoneId.of("Europe/London")
        assertEquals("14:25", clockTime("2026-10-02T13:25:44.672Z", london))
        assertEquals("2 Oct", shortDay("2026-10-02T13:25:44.672Z", london))
        assertEquals("", clockTime("not a time", london))
        assertEquals("", clockTime(null, london))
    }
}
