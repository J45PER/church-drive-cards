package com.churchdrive.app

import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ui.cameraAge
import com.churchdrive.app.ui.cameraFind
import com.churchdrive.app.ui.cameraKindLabel
import com.churchdrive.app.ui.cameraPicture
import com.churchdrive.app.ui.parseMillis
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId

class CameraDataTest {
    private fun state(id: String, state: String, attrs: String = "{}", changed: String? = null) =
        EntityState(id, state, JSONObject(attrs), changed)

    private val entities = listOf(
        state("camera.front_door_live_view", "idle", """{"entity_picture":"/api/camera_proxy/camera.front_door_live_view?token=L"}"""),
        state("camera.front_door_snapshot", "idle", """{"entity_picture":"/api/camera_proxy/camera.front_door_snapshot?token=S","timestamp":1790960000,"type":"on-demand"}"""),
        state("sensor.front_door_last_activity", "2026-10-02T21:46:30+00:00", """{"category":"ding"}"""),
        state("button.front_door_take_snapshot", "2026-10-02T20:03:25+00:00"),
        state("sensor.front_door_battery", "79"),
        state("event.front_door_ding", "2026-09-28T14:28:00.559+00:00"),
        state("camera.driveway_live_view", "idle", """{"entity_picture":"/p?token=D"}"""),
    ).associateBy { it.entityId }

    @Test
    fun findsTheEntitiesThatGoWithACamera() {
        val f = cameraFind(entities, "camera.front_door_live_view")
        assertEquals("camera.front_door_snapshot", f.snap)
        assertEquals("button.front_door_take_snapshot", f.button)
        assertEquals("sensor.front_door_battery", f.battery)
        assertEquals("event.front_door_ding", f.ding)
        assertNull(f.motion)
        val bare = cameraFind(entities, "camera.driveway_live_view")
        assertNull(bare.snap)
        assertNull(bare.button)
    }

    @Test
    fun theNewerPictureWins() {
        val f = cameraFind(entities, "camera.front_door_live_view")
        // The live view's last activity (21:46 UTC on 2 Oct) is newer than the snapshot's timestamp (1790960000 = 16:53 UTC).
        val pic = cameraPicture(entities, f)!!
        assertEquals("camera.front_door_live_view", pic.id)
        assertEquals("ding", pic.kind)
        assertEquals(parseMillis("2026-10-02T21:46:30+00:00"), pic.ms)
        assertEquals("/api/camera_proxy/camera.front_door_live_view?token=L", pic.url)

        // If the snapshot is newer, it wins, with its own reason.
        val newer = entities + ("camera.front_door_snapshot" to state(
            "camera.front_door_snapshot", "idle",
            """{"entity_picture":"/s?token=S","timestamp":1790990000,"type":"on-demand"}""",
        ))
        val pic2 = cameraPicture(newer, f)!!
        assertEquals("camera.front_door_snapshot", pic2.id)
        assertEquals("on-demand", pic2.kind)
        assertEquals(1790990000L * 1000, pic2.ms)
    }

    @Test
    fun aCameraWithNoPictureHasNone() {
        assertNull(cameraPicture(emptyMap(), cameraFind(emptyMap(), "camera.x_live_view")))
    }

    @Test
    fun readsTimesWithZOrAnOffset() {
        assertEquals(parseMillis("2026-10-02T21:46:30Z"), parseMillis("2026-10-02T21:46:30+00:00"))
        assertEquals(parseMillis("2026-10-02T21:46:30Z")!! - 3_600_000, parseMillis("2026-10-02T21:46:30+01:00"))
        assertNull(parseMillis("later"))
        assertNull(parseMillis(null))
    }

    @Test
    fun ageIsShownLikeTheDashboard() {
        val now = 1_000_000_000_000L
        assertEquals("just now", cameraAge(now - 30_000, now))
        assertEquals("4 min", cameraAge(now - 4 * 60_000 - 5_000, now))
        assertEquals("3 h", cameraAge(now - 3 * 3_600_000L - 60_000, now))
        // Older than a day: the day and time. 1_000_000_000_000 ms is Sun 9 Sep 2001, 01:46:40 UTC (02:46 in London, BST).
        assertEquals("Sun 02:46", cameraAge(1_000_000_000_000L, 1_000_000_000_000L + 3 * 86_400_000L, ZoneId.of("Europe/London")))
    }

    @Test
    fun reasonsAreNamed() {
        assertEquals("Doorbell", cameraKindLabel("ding"))
        assertEquals("Motion", cameraKindLabel("motion"))
        assertEquals("Snapshot", cameraKindLabel("on-demand"))
        assertEquals("Snapshot", cameraKindLabel("interval"))
    }
}
