package com.churchdrive.app

import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ha.Registry
import com.churchdrive.app.ui.cameraBaseName
import com.churchdrive.app.ui.cameraLight
import com.churchdrive.app.ui.eventClock
import com.churchdrive.app.ui.eventDay
import com.churchdrive.app.ui.eventFilters
import com.churchdrive.app.ui.filterEvents
import com.churchdrive.app.ui.groupByDay
import com.churchdrive.app.ui.parseCameraEvents
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class CameraEventsDataTest {
    private val london = ZoneId.of("Europe/London")
    private fun ms(y: Int, m: Int, d: Int, h: Int, min: Int) = ZonedDateTime.of(y, m, d, h, min, 0, 0, london).toInstant().toEpochMilli()

    private fun state(id: String) = EntityState(id, "off", JSONObject("{}"), null)
    private val entities = listOf("camera.front_door_live_view", "camera.garden_live_view", "light.front_light", "light.porch", "light.hall")
        .associateWith { state(it) }

    @Test
    fun readsEventsNewestFirstAndIgnoresNullLinks() {
        val result = JSONObject(
            """{"events":[
              {"id":"1a","ts":1000.5,"kind":"motion","picture":"/p/1.jpg","clip":null},
              {"id":"2b","ts":2000,"kind":"ding","picture":null,"clip":"/c/2.mp4"},
              {"ts":3000}
            ]}""",
        )
        val events = parseCameraEvents(result)
        assertEquals(listOf("2b", "1a"), events.map { it.id })
        assertEquals(1_000_500L, events[1].ts)
        assertNull(events[1].clip)
        assertEquals("/c/2.mp4", events[0].clip)
        assertEquals(emptyList<Any>(), parseCameraEvents(null))
    }

    @Test
    fun findsTheCameraBaseFromEntitiesOrNames() {
        assertEquals("front_door", cameraBaseName(entities, "camera.front_door_live_view"))
        assertEquals("front_door", cameraBaseName(entities, "event.front_door_ding"))
        assertEquals("garden", cameraBaseName(entities, null, "Garden"))
        assertNull(cameraBaseName(entities, "camera.nowhere_live_view", "Nowhere"))
    }

    @Test
    fun theLightByACameraIsTheConfiguredOneOrTheOnlyOneInItsArea() {
        val registry = Registry.parse(
            JSONObject(
                """{"entities":[{"ei":"camera.front_door_live_view","ai":"front"},{"ei":"light.front_light","ai":"front"},
                  {"ei":"camera.garden_live_view","ai":"garden"},{"ei":"light.porch","ai":"garden"},{"ei":"light.hall","ai":"garden"}]}""",
            ),
            JSONArray("[]"),
        )
        assertEquals("light.front_light", cameraLight(entities, registry, "camera.front_door_live_view", null))
        assertNull(cameraLight(entities, registry, "camera.garden_live_view", null)) // two lights there
        assertEquals("light.hall", cameraLight(entities, registry, "camera.garden_live_view", "light.hall"))
        assertNull(cameraLight(entities, registry, "camera.front_door_live_view", "none"))
    }

    @Test
    fun groupsByDayWithFriendlyDayNames() {
        val now = ms(2026, 10, 3, 12, 0)
        assertEquals("Today", eventDay(ms(2026, 10, 3, 9, 0), now, london))
        assertEquals("Yesterday", eventDay(ms(2026, 10, 2, 23, 30), now, london))
        assertEquals(true, eventDay(ms(2026, 9, 30, 8, 0), now, london).startsWith("Wednesday 30 Sep")) // "Sep" or "Sept", by platform
        assertEquals("21:40", eventClock(ms(2026, 10, 2, 21, 40), london))
        val events = parseCameraEvents(
            JSONObject(
                """{"events":[{"id":"a","ts":${ms(2026, 10, 3, 9, 0) / 1000},"kind":"ding"},
                  {"id":"b","ts":${ms(2026, 10, 3, 8, 0) / 1000},"kind":"motion"},
                  {"id":"c","ts":${ms(2026, 10, 2, 8, 0) / 1000},"kind":"motion"}]}""",
            ),
        )
        val groups = groupByDay(events, now, london)
        assertEquals(listOf("Today", "Yesterday"), groups.map { it.first })
        assertEquals(listOf("a", "b"), groups[0].second.map { it.id })
    }

    @Test
    fun filtersByKindLabel() {
        val events = parseCameraEvents(
            JSONObject("""{"events":[{"id":"a","ts":3,"kind":"motion"},{"id":"b","ts":2,"kind":"ding"},{"id":"c","ts":1,"kind":"interval"}]}"""),
        )
        assertEquals(listOf("Doorbell", "Motion", "Snapshot"), eventFilters(events))
        assertEquals(listOf("b"), filterEvents(events, "Doorbell").map { it.id })
        assertEquals(3, filterEvents(events, null).size)
    }
}
