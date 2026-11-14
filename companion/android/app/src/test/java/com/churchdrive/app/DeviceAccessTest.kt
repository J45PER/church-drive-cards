package com.churchdrive.app

import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ui.DeviceAccess
import com.churchdrive.app.ui.mediaPlayers
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceAccessTest {
    private fun e(id: String, state: String = "off", vararg attrs: Pair<String, Any>) = EntityState(id, state, JSONObject().also { o -> attrs.forEach { o.put(it.first, it.second) } })

    private fun house(): Map<String, EntityState> {
        val access = JSONObject()
            .put("media_player.living_room_tv", JSONArray(listOf("person.diane", "person.ian", "person.jamie")))
            .put("media_player.kitchen_speaker", JSONArray(listOf("person.diane", "person.jamie")))
            .put("media_player.office_display", JSONArray(listOf("person.diane", "person.jamie")))   // nobody was given it: administrators only
        return listOf(
            e("person.jamie", "home", "user_id" to "u-jamie"), e("person.ian", "home", "user_id" to "u-ian"), e("person.hayley", "home", "user_id" to "u-hayley"),
            e("sensor.church_drive_people", "3", "access" to access),
            e("media_player.living_room_tv", "playing", "friendly_name" to "Living Room TV"), e("media_player.kitchen_speaker", "idle", "friendly_name" to "Kitchen speaker"),
            e("media_player.office_display", "off", "friendly_name" to "Office display"),
        ).associateBy { it.entityId }
    }

    @Test
    fun theSignedInPersonIsFoundByTheirLogin() {
        val all = house()
        assertEquals("person.ian", DeviceAccess.me(all, "u-ian"))
        assertNull(DeviceAccess.me(all, "u-nobody"))
        assertNull(DeviceAccess.me(all, null))
    }

    @Test
    fun administratorsSeeEverythingAndOthersOnlyWhatIsGivenToThem() {
        val all = house()
        fun seen(user: String): List<String> =
            all.values.filter { it.entityId.startsWith("media_player.") }.filter { DeviceAccess.canSee(all, it.entityId, DeviceAccess.me(all, user)) }.map { it.entityId }.sorted()
        assertEquals(listOf("media_player.kitchen_speaker", "media_player.living_room_tv", "media_player.office_display"), seen("u-jamie"))
        assertEquals(listOf("media_player.living_room_tv"), seen("u-ian"))
        assertEquals(emptyList<String>(), seen("u-hayley"))
        assertEquals(emptyList<String>(), seen("u-stranger"))
    }

    @Test
    fun anEntityTheHouseDoesNotListIsEveryonesAndSoIsEverythingWithoutTheSensor() {
        val all = house()
        assertTrue(DeviceAccess.canSee(all, "light.hall", null))
        assertTrue(DeviceAccess.canSee(all - "sensor.church_drive_people", "media_player.living_room_tv", null))
        assertFalse(DeviceAccess.canSee(all, "media_player.living_room_tv", null))
    }

    @Test
    fun theMediaCardLeavesOutWhatThePersonMaySeeNothingOf() {
        val all = house()
        DeviceAccess.userId = "u-ian"
        try {
            assertEquals(listOf("media_player.living_room_tv"), mediaPlayers(JSONObject(), all).map { it.entityId })
            DeviceAccess.userId = "u-jamie"
            assertEquals(listOf("media_player.living_room_tv", "media_player.kitchen_speaker", "media_player.office_display"), mediaPlayers(JSONObject(), all).map { it.entityId })
        } finally {
            DeviceAccess.userId = null
        }
    }
}
