package com.churchdrive.app

import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ui.ZoneKind
import com.churchdrive.app.ui.ZoneLevel
import com.churchdrive.app.ui.ZoneTrack
import com.churchdrive.app.ui.parseHistory
import com.churchdrive.app.ui.parseMillis
import com.churchdrive.app.ui.spansOf
import com.churchdrive.app.ui.stripBars
import com.churchdrive.app.ui.whenMs
import com.churchdrive.app.ui.zoneConfig
import com.churchdrive.app.ui.zoneView
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId

class ZoneDataTest {
    private val london = ZoneId.of("Europe/London")
    private val now = parseMillis("2026-10-02T15:00:00Z")!!   // 16:00 in London
    private fun ago(minutes: Long) = now - minutes * 60_000
    private fun iso(ms: Long) = java.time.Instant.ofEpochMilli(ms).toString()

    private fun state(id: String, state: String, attrs: String = "{}", changedMsAgo: Long? = null) =
        EntityState(id, state, JSONObject(attrs), changedMsAgo?.let { iso(ago(it)) })

    private fun config(json: String) = zoneConfig(JSONObject(json))

    private val entrance = config(
        """{"name":"Entrance","door_entity":"binary_sensor.front_door","motion_entities":["binary_sensor.motion"],
            "tamper_entities":["binary_sensor.front_door_tamper"],"battery_1":"sensor.door_battery","battery_1_name":"Door contact",
            "battery_2":"sensor.motion_battery","alarm_entity":"alarm_control_panel.a","hours":"12","strip":"bars"}""",
    )

    private fun entities(vararg e: EntityState) = e.associateBy { it.entityId }

    @Test
    fun readsTheCardConfig() {
        assertEquals("Entrance", entrance.name)
        assertEquals("binary_sensor.front_door", entrance.door)
        assertEquals(12, entrance.hours)
        assertEquals(false, entrance.ticks)
        assertEquals(listOf("sensor.door_battery" to "Door contact", "sensor.motion_battery" to null), entrance.batteries)
        assertEquals(
            listOf("binary_sensor.front_door", "binary_sensor.motion", "binary_sensor.front_door_tamper"),
            entrance.historyIds(),
        )
    }

    @Test
    fun aClosedQuietDoor() {
        val e = entities(
            state("binary_sensor.front_door", "off", changedMsAgo = 64),
            state("binary_sensor.motion", "off", changedMsAgo = 70),
            state("binary_sensor.front_door_tamper", "off"),
            state("alarm_control_panel.a", "disarmed"),
        )
        val history = mapOf("binary_sensor.motion" to listOf(ago(300) to "on", ago(298) to "off", ago(62) to "on", ago(61) to "off"))
        val v = zoneView(entrance, e, history, now, london)
        assertEquals(ZoneLevel.Ok, v.level)
        assertEquals("Closed", v.word)
        assertEquals("Closed", v.chip)
        assertEquals("Closed since 14:56 · motion 14:58", v.last)
    }

    @Test
    fun motionJustNowAndTheEventsLine() {
        val e = entities(
            state("binary_sensor.front_door", "off", changedMsAgo = 64),
            state("binary_sensor.motion", "on", changedMsAgo = 1),
        )
        val v = zoneView(entrance, e, emptyMap(), now, london)
        assertEquals(ZoneLevel.Motion, v.level)
        assertEquals("Motion just now", v.word)
        assertEquals("Closed since 14:56 · motion just now", v.last)
    }

    @Test
    fun anOpenDoorCountsUpAndTurnsRedIfTheAlarmIsSet() {
        val open = entities(
            state("binary_sensor.front_door", "on", changedMsAgo = 4),
            state("alarm_control_panel.a", "disarmed"),
        )
        val v = zoneView(entrance, open, emptyMap(), now, london)
        assertEquals(ZoneLevel.Open, v.level)
        assertEquals("Open 4 min", v.word)
        assertEquals("Open", v.chip)
        assertEquals("Open since 15:56", v.last)

        val armed = open + ("alarm_control_panel.a" to state("alarm_control_panel.a", "armed_away"))
        val w = zoneView(entrance, armed, emptyMap(), now, london)
        assertEquals(ZoneLevel.Tamper, w.level)
        assertEquals("Open while armed", w.word)
    }

    @Test
    fun aTamperShowsAWarning() {
        val e = entities(
            state("binary_sensor.front_door", "off", changedMsAgo = 64),
            state("binary_sensor.front_door_tamper", "on", """{"friendly_name":"Front Door Tamper"}""", changedMsAgo = 3),
        )
        val v = zoneView(entrance, e, emptyMap(), now, london)
        assertEquals(ZoneLevel.Tamper, v.level)
        assertEquals("Tamper", v.word)
        assertEquals("Front Door Tamper tampered with at 15:57", v.warn)
    }

    @Test
    fun aZoneWhoseSensorsAreGoneIsUnavailable() {
        val v = zoneView(entrance, entities(state("binary_sensor.front_door", "unavailable")), emptyMap(), now, london)
        assertEquals(ZoneLevel.Off, v.level)
        assertEquals("Unavailable", v.word)
    }

    @Test
    fun aZoneWithNoDoorIsQuiet() {
        val garden = config("""{"name":"Back Garden","motion_entities":["binary_sensor.garden"]}""")
        val v = zoneView(garden, entities(state("binary_sensor.garden", "off")), emptyMap(), now, london)
        assertEquals("Quiet", v.word)
    }

    @Test
    fun eventEntitiesGiveAMomentPerEventAndTheDoorbellLine() {
        val porch = config("""{"name":"Front Garden","motion_entities":["event.front_motion"],"doorbell_entity":"event.front_ding"}""")
        val e = entities(
            state("event.front_motion", iso(ago(90))),
            state("event.front_ding", iso(ago(600))),
        )
        val history = mapOf("event.front_motion" to listOf(ago(300) to iso(ago(300)), ago(90) to iso(ago(90))))
        val v = zoneView(porch, e, history, now, london)
        assertEquals("Quiet", v.word)
        assertEquals("Motion 14:30 · rang 06:00", v.last)
        val spans = v.tracks.first { it.kind == ZoneKind.Motion }.spans
        assertEquals(listOf(ago(300), ago(90)), spans.map { it.first })
        assertEquals(30_000L, spans[0].second - spans[0].first)
    }

    @Test
    fun batteriesWithNamesAndLowOnes() {
        val e = entities(
            state("sensor.door_battery", "18", """{"friendly_name":"Front Door Battery"}"""),
            state("sensor.motion_battery", "100", """{"friendly_name":"Ring sensor Battery"}"""),
        )
        val bats = zoneView(entrance, e, emptyMap(), now, london).bats
        assertEquals(listOf("Door contact", "Ring sensor"), bats.map { it.name })
        assertEquals(listOf(18.0, 100.0), bats.map { it.level })
    }

    @Test
    fun spansOfASensorThatWasOn() {
        val from = ago(600)
        val series = listOf(ago(700) to "on", ago(500) to "off", ago(100) to "on")
        val spans = spansOf("binary_sensor.x", series, from, now)
        // On since before the window (clipped to its start) until 500 min ago, then on from 100 min ago to now.
        assertEquals(listOf(from to ago(500), ago(100) to now), spans)
    }

    @Test
    fun theStripBarsAreAsTallAsTheyWereBusy() {
        val from = ago(12 * 60)
        val span = 12 * 3_600_000L
        val tracks = listOf(
            ZoneTrack(ZoneKind.Motion, listOf(from + 10 to from + 20, from + 100 to from + 110, from + 200 to from + 210)),
            ZoneTrack(ZoneKind.Door, listOf(from + span - 1000 to from + span)),
            ZoneTrack(ZoneKind.Light, listOf(from to from + span)),
        )
        val bars = stripBars(tracks, from, span)
        // Three motion events fall in the first slot, a door event in the last; the light isn't a bar.
        assertEquals(listOf(0, 23), bars.map { it.slot })
        assertEquals(ZoneKind.Motion, bars[0].kind)
        assertEquals(0.85f, bars[0].height, 0.001f)
        assertEquals(ZoneKind.Door, bars[1].kind)
        assertEquals(0.283f, bars[1].height, 0.001f)
    }

    @Test
    fun readsHistoryFromHomeAssistant() {
        val result = JSONObject(
            """{"binary_sensor.a":[{"s":"off","a":{},"lu":1790960000.5},{"s":"on","lu":1790960100.0}],
                "event.b":[{"state":"2026-10-02T10:00:00+00:00","last_updated":"2026-10-02T10:00:01+00:00"}]}""",
        )
        val h = parseHistory(result)
        assertEquals(listOf(1790960000500L to "off", 1790960100000L to "on"), h["binary_sensor.a"])
        assertEquals(listOf(parseMillis("2026-10-02T10:00:01Z")!! to "2026-10-02T10:00:00+00:00"), h["event.b"])
        assertEquals(emptyMap<String, Any>(), parseHistory(null))
    }

    @Test
    fun timesAreShownLikeTheDashboard() {
        assertEquals("15:56", whenMs(ago(4), now, london))
        assertEquals("yesterday 16:00", whenMs(ago(24 * 60), now, london))
        assertEquals("Wed 17:00", whenMs(ago(2 * 24 * 60 - 60), now, london))
    }
}
