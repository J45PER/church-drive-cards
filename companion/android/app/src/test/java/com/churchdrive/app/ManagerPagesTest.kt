package com.churchdrive.app

import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ui.canTick
import com.churchdrive.app.ui.kindGoesTo
import com.churchdrive.app.ui.kindGroups
import com.churchdrive.app.ui.linksOf
import com.churchdrive.app.ui.triggerChoices
import com.churchdrive.app.ui.validIconName
import com.churchdrive.app.ui.zoneRows
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManagerPagesTest {
    private fun e(id: String, state: String, vararg a: Pair<String, Any>) =
        EntityState(id, state, JSONObject().apply { a.forEach { put(it.first, it.second) } })

    @Test
    fun kindsAreGroupedInOrder() {
        val kinds = JSONArray("""[{"group":"Safety","key":"a"},{"group":"Security","key":"b"},{"group":"Safety","key":"c"}]""")
        val groups = kindGroups(kinds)
        assertEquals(listOf("Safety", "Security"), groups.map { it.first })
        assertEquals(listOf("a", "c"), groups[0].second.map { it.getString("key") })
    }

    @Test
    fun aKindGoesToEveryoneOrTheTicked() {
        assertTrue(kindGoesTo(JSONObject("""{"all":true,"people":[]}"""), "person.x"))
        assertTrue(kindGoesTo(JSONObject("""{"all":false,"people":["person.x"]}"""), "person.x"))
        assertFalse(kindGoesTo(JSONObject("""{"all":false,"people":["person.y"]}"""), "person.x"))
    }

    @Test
    fun costsAreForAdministratorsOnly() {
        val cost = JSONObject("""{"admin_only":true}""")
        assertFalse(canTick(cost, JSONObject("""{"admin":false}""")))
        assertTrue(canTick(cost, JSONObject("""{"admin":true}""")))
        assertTrue(canTick(JSONObject("""{}"""), JSONObject("""{"admin":false}""")))
    }

    @Test
    fun iconNamesAreMdiWords() {
        assertTrue(validIconName("mdi:fan"))
        assertTrue(validIconName(" mdi:weather-night "))
        assertFalse(validIconName("fan"))
        assertFalse(validIconName("mdi:Fan"))
        assertFalse(validIconName("mdi:"))
    }

    @Test
    fun cameraLinksReadPerMode() {
        val links = JSONObject("""{"away":{"binary_sensor.door":{"cams":["front","back"],"secs":45}},"home":{}}""")
        val away = linksOf(links, "away")
        assertEquals(1, away.size)
        assertEquals(listOf("front", "back"), away[0].cams)
        assertEquals(45, away[0].secs)
        assertTrue(linksOf(links, "home").isEmpty())
        assertTrue(linksOf(null, "away").isEmpty())
    }

    @Test
    fun triggersAreMovementAndDoorsNotAlreadyTaken() {
        val all = listOf(
            e("binary_sensor.door", "off", "device_class" to "door"),
            e("binary_sensor.hall", "off", "device_class" to "motion"),
            e("binary_sensor.other", "off", "device_class" to "battery"),
            e("sensor.temp", "20"),
        ).associateBy { it.entityId }
        assertEquals(listOf("binary_sensor.door", "binary_sensor.hall"), triggerChoices(all, emptySet()).map { it.entityId })
        assertEquals(listOf("binary_sensor.hall"), triggerChoices(all, setOf("binary_sensor.door")).map { it.entityId })
    }

    @Test
    fun zonesListHomeFirstWithWhoIsIn() {
        val all = listOf(
            e("zone.work", "1", "friendly_name" to "Work", "radius" to 100.0),
            e("zone.home", "1", "friendly_name" to "Home", "radius" to 50.0),
            e("person.a", "home", "friendly_name" to "Ann"),
            e("person.b", "Work", "friendly_name" to "Bob"),
        ).associateBy { it.entityId }
        val rows = zoneRows(all)
        assertEquals(listOf("Home", "Work"), rows.map { it.name })
        assertEquals(listOf("Ann"), rows[0].people)
        assertEquals(listOf("Bob"), rows[1].people)
        assertEquals(50, rows[0].radius)
    }
}
