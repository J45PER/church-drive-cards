package com.churchdrive.app

import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ha.Registry
import com.churchdrive.app.ui.Tone
import com.churchdrive.app.ui.heardAgo
import com.churchdrive.app.ui.peopleLists
import com.churchdrive.app.ui.peopleRows
import com.churchdrive.app.ui.plainMarkdown
import com.churchdrive.app.ui.presencePill
import com.churchdrive.app.ui.watchedDevices
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class ManagerCardsTest {
    private fun e(id: String, state: String, vararg a: Pair<String, Any>) =
        EntityState(id, state, JSONObject().apply { a.forEach { put(it.first, it.second) } })

    private fun map(vararg es: EntityState) = es.associateBy { it.entityId }

    @Test
    fun presenceIsHomeAPlaceAwayOrUnknown() {
        assertEquals("Home" to Tone.Green, presencePill("home", null, null))
        assertEquals("Work · Ashfield School" to Tone.Teal, presencePill("Ashfield School", "work", "Ashfield School"))
        assertEquals("Away" to Tone.Grey, presencePill("not_home", null, null))
        assertEquals("Unknown" to Tone.Amber, presencePill("unknown", null, null))
    }

    @Test
    fun peopleComeWithTheirPhonesAndBatteries() {
        val registry = Registry(
            mapOf("device_tracker.ann_phone" to "dev1", "sensor.ann_phone_battery_level" to "dev1"),
            mapOf("dev1" to "Ann's Pixel"),
        )
        val people = JSONArray().put(JSONObject().put("entity_id", "person.ann").put("place", "work").put("zone", "School"))
        val rows = peopleRows(
            map(
                e("person.ann", "School", "friendly_name" to "Ann", "device_trackers" to JSONArray().put("device_tracker.ann_phone")),
                e("person.bo", "home", "friendly_name" to "Bo"),
                e("sensor.ann_phone_battery_level", "82.4"),
                e("sensor.church_drive_people", "2", "people" to people),
            ),
            registry,
        )
        assertEquals(listOf("Ann", "Bo"), rows.map { it.name })
        assertEquals(listOf("Ann's Pixel" to 82), rows.first().phones)
        assertEquals("work", rows.first().place)
        assertEquals(emptyList<Pair<String, Int?>>(), rows.last().phones)
    }

    @Test
    fun watchedDevicesListTroubleFirstWithWhenTheyWereHeard() {
        val now = java.time.Instant.parse("2026-10-03T12:00:00Z").toEpochMilli()
        val devices = JSONObject()
            .put("fan.a", JSONObject().put("name", "Fan A").put("status", "ok").put("last_heard", "2026-10-03T11:59:50Z"))
            .put("sensor.b", JSONObject().put("name", "Sensor B").put("status", "stale").put("reason", "Old readings").put("last_heard", "2026-10-03T10:00:00Z").put("usual_gap", 900).put("fixes", JSONArray().put("Refreshed")))
        val list = watchedDevices(map(e("sensor.church_drive_device_health", "1", "devices" to devices)), now)
        assertEquals(listOf("Sensor B", "Fan A"), list.map { it.name })
        assertEquals("2 h ago", list.first().lastHeard)
        assertEquals("every 15 min", list.first().usual)
        assertEquals(listOf("Refreshed"), list.first().fixes)
        assertEquals("just now", list.last().lastHeard)
        assertEquals("not heard yet", heardAgo(null))
    }

    @Test
    fun eachPersonWithAListGetsOne() {
        val people = JSONArray()
            .put(JSONObject().put("first", "Jamie").put("list", "todo.priorities_jamie"))
            .put(JSONObject().put("first", "Ian").put("list", "todo.priorities_ian"))
            .put(JSONObject().put("first", "Diane").put("list", ""))
        val lists = peopleLists(map(e("sensor.church_drive_people", "3", "people" to people), e("todo.priorities_jamie", "2"), e("todo.priorities_ian", "unavailable")))
        assertEquals(listOf("Jamie" to "todo.priorities_jamie"), lists)
    }

    @Test
    fun markdownLosesItsStars() {
        assertEquals("Give it the Ignore in to-dos label.", plainMarkdown("Give it the **Ignore in to-dos** label."))
    }
}
