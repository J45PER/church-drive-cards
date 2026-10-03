package com.churchdrive.app

import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ha.Registry
import com.churchdrive.app.ui.DashboardPanels
import com.churchdrive.app.ui.carRows
import com.churchdrive.app.ui.chargeEndText
import com.churchdrive.app.ui.mediaLine
import com.churchdrive.app.ui.mediaPlayers
import com.churchdrive.app.ui.personOf
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class DashboardParityTest {
    private fun e(id: String, state: String, vararg a: Pair<String, Any>) =
        EntityState(id, state, JSONObject().apply { a.forEach { put(it.first, it.second) } })

    private fun map(vararg es: EntityState) = es.associateBy { it.entityId }

    /** A registry reply with a Vauxhall's entities on one device, and a mobile app phone. */
    private fun registry(): Registry {
        val entities = JSONObject().put("entities", JSONArray()
            .put(JSONObject().put("ei", "sensor.corsa_battery").put("di", "car1").put("pl", "stellantis_vehicles"))
            .put(JSONObject().put("ei", "sensor.corsa_range").put("di", "car1").put("pl", "stellantis_vehicles"))
            .put(JSONObject().put("ei", "binary_sensor.corsa_charging").put("di", "car1").put("pl", "stellantis_vehicles"))
            .put(JSONObject().put("ei", "binary_sensor.corsa_plugged_in").put("di", "car1").put("pl", "stellantis_vehicles"))
            .put(JSONObject().put("ei", "sensor.corsa_charging_end").put("di", "car1").put("pl", "stellantis_vehicles"))
            .put(JSONObject().put("ei", "device_tracker.corsa").put("di", "car1").put("pl", "stellantis_vehicles"))
            .put(JSONObject().put("ei", "sensor.phone_battery").put("di", "phone1").put("pl", "mobile_app")))
        val devices = JSONArray().put(JSONObject().put("id", "car1").put("name", "Corsa").put("name_by_user", "Hayley's Vauxhall")).put(JSONObject().put("id", "phone1").put("name", "Phone"))
        return Registry.parse(entities, devices)
    }

    private val states = map(
        e("sensor.corsa_battery", "64", "device_class" to "battery", "unit_of_measurement" to "%"),
        e("sensor.corsa_range", "142", "unit_of_measurement" to "mi"),
        e("binary_sensor.corsa_charging", "on"),
        e("binary_sensor.corsa_plugged_in", "on"),
        e("sensor.corsa_charging_end", "2026-10-03T06:30:00+00:00"),
        e("device_tracker.corsa", "home"),
        e("sensor.phone_battery", "80", "device_class" to "battery", "unit_of_measurement" to "%"),
    )

    @Test
    fun theVauxhallShowsFromItsOwnIntegrationOnly() {
        val rows = carRows(JSONObject(), states, registry(), null, ZoneOffset.UTC)
        assertEquals(1, rows.size)
        val car = rows[0]
        assertEquals("Hayley's Vauxhall", car.name)
        assertEquals(64.0, car.battery!!, 0.0)
        assertEquals("142 mi", car.range)
        assertTrue(car.charging && car.plugged)
        assertEquals("Full by 06:30", car.end)
        assertEquals("Home", car.where)
    }

    @Test
    fun aCarGivenToSomeoneIsOnlyTheirs() {
        val withOwner = states + map(e("sensor.church_drive_people", "2", "people" to JSONArray().put(JSONObject().put("entity_id", "person.hayley").put("cars", JSONArray().put("car1")))))
        assertEquals(1, carRows(JSONObject(), withOwner, registry(), "person.hayley", ZoneOffset.UTC).size)
        assertTrue(carRows(JSONObject(), withOwner, registry(), "person.jamie", ZoneOffset.UTC).isEmpty())
        assertEquals(1, carRows(JSONObject().put("only_mine", false), withOwner, registry(), "person.jamie", ZoneOffset.UTC).size)
    }

    @Test
    fun chargeEndIsATimeOrTimeLeft() {
        assertEquals("Full by 06:30", chargeEndText("2026-10-03T06:30:00+00:00", null, ZoneOffset.UTC))
        assertEquals("45 min left", chargeEndText("45", "min"))
    }

    @Test
    fun thePersonIsFoundByFirstName() {
        val all = map(e("person.hayley", "home", "friendly_name" to "Hayley Smith"), e("person.jamie", "home", "friendly_name" to "Jamie"))
        assertEquals("person.hayley", personOf("Hayley", all))
        assertEquals(null, personOf("Zed", all))
    }

    @Test
    fun aMirrorStandsForTheCardItPointsAt() {
        val config = JSONObject("""{"views":[
          {"path":"home","sections":[{"cards":[{"type":"custom:section-panel-card","title":"Charging","cards":[{"type":"custom:mirror-card","dashboard":"this","view":"energy","source":"name:Cars"}]}]}]},
          {"path":"energy","sections":[{"cards":[{"type":"custom:section-panel-card","title":"Car","cards":[{"type":"custom:car-card","title":"Cars"}]}]}]}]}""")
        val home = DashboardPanels.parse(config).getValue("home")
        assertEquals(listOf("custom:car-card"), home[0].cards.map { it.type })
        assertEquals("Cars", home[0].cards[0].config.getString("title"))
    }

    @Test
    fun mediaPlayersPutWhatIsPlayingFirst() {
        val all = map(
            e("media_player.bedroom", "off", "friendly_name" to "Bedroom TV"),
            e("media_player.lounge", "playing", "friendly_name" to "Lounge TV", "media_title" to "Bake Off", "app_name" to "Netflix"),
            e("sensor.other", "1"),
        )
        val players = mediaPlayers(JSONObject(), all)
        assertEquals(listOf("media_player.lounge", "media_player.bedroom"), players.map { it.entityId })
        assertEquals("Bake Off · Netflix", mediaLine(players[0]))
        assertEquals("Off", mediaLine(players[1]))
    }
}
