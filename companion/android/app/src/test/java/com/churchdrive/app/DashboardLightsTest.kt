package com.churchdrive.app

import com.churchdrive.app.ui.DashboardLights
import com.churchdrive.app.ui.LightLayout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardLightsTest {
    private val config = JSONObject(
        """
        {"views":[
          {"path":"home","sections":[{"cards":[{"type":"custom:auto-layout-card","cards":[
            {"type":"custom:section-panel-card","title":"Security","cards":[{"type":"custom:alarm-panel-card"}]},
            {"type":"custom:section-panel-card","title":"Lights","cards":[
              {"type":"custom:light-control-card","mode":"room","area":"kitchen","entity":"light.middle_floor_spot_1",
               "entities":[{"entity":"light.kitchen"},{"entity":"light.kitchen_spotlights"},{"entity":"light.kitchen_ambience","name":"Ambience","level":2}],
               "scenes":[{"entity":"universal:bright@light.kitchen"},{"entity":"universal:soho@light.kitchen_ambience"},{"entity":"scene.movie","name":"Movie"}]},
              {"type":"custom:light-control-card","mode":"room","area":"middle_floor","entities":[{"entity":"light.middle_floor"}]}
            ]}]}]}]},
          {"path":"lighting","sections":[{"cards":[{"type":"custom:auto-layout-card","cards":[
            {"type":"custom:section-panel-card","title":"Garden","cards":[
              {"type":"custom:light-control-card","mode":"room","area":"garden","name":"The garden","max_scenes":1,
               "entities":["light.garden","light.outside"],
               "scenes":[{"entity":"universal:bright@light.garden"},{"entity":"universal:dimmed@light.garden"}]}]}
          ]}]}]}
        ]}
        """,
    )

    @Test
    fun readsHomeRooms() {
        val home = DashboardLights.parse(config).home
        assertEquals(2, home.size)
        val kitchen = home[0]
        assertEquals("light.kitchen", kitchen.head)
        assertEquals("kitchen", kitchen.area)
        assertNull(kitchen.title)
        assertEquals(listOf("light.kitchen_spotlights", "light.kitchen_ambience"), kitchen.rows.map { it.entity })
        assertEquals(listOf(1, 2), kitchen.rows.map { it.level })
        assertEquals("Ambience", kitchen.rows[1].name)
    }

    @Test
    fun readsScenesAndTheirTargets() {
        val scenes = DashboardLights.parse(config).home[0].scenes
        assertEquals(listOf("bright", "soho", "scene.movie"), scenes.map { it.key })
        assertEquals("light.kitchen", scenes[0].target)
        assertEquals("light.kitchen_ambience", scenes[1].target)
        assertEquals("scene.movie", scenes[2].haScene)
        assertEquals("Movie", scenes[2].name)
    }

    @Test
    fun aCardWithNoSceneListGetsTheFourDefaults() {
        val room = DashboardLights.parse(config).home[1]
        assertEquals(listOf("bright", "dimmed", "relax", "nightlight"), room.scenes.map { it.key })
        assertTrue(room.scenes.all { it.target == "light.middle_floor" })
    }

    @Test
    fun readsFloorsAndRespectsMaxScenes() {
        val floors = DashboardLights.parse(config).floors
        assertEquals(listOf("Garden"), floors.map { it.first })
        val garden = floors[0].second[0]
        assertEquals("The garden", garden.title)
        assertEquals(1, garden.scenes.size)
    }

    @Test
    fun fallsBackWhenTheDashboardHasNothing() {
        assertEquals(LightLayout.Fallback, DashboardLights.parse(JSONObject("""{"views":[]}""")))
    }

    @Test
    fun readsAreaNames() {
        val names = DashboardLights.areaNames(JSONArray("""[{"area_id":"living_room","name":"Living Room"}]"""))
        assertEquals("Living Room", names["living_room"])
        assertEquals(emptyMap<String, String>(), DashboardLights.areaNames(null))
    }
}
