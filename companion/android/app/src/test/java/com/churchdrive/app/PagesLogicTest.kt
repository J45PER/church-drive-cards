package com.churchdrive.app

import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ui.DashboardPanels
import com.churchdrive.app.ui.HouseTask
import com.churchdrive.app.ui.Tone
import com.churchdrive.app.ui.TodoItem
import com.churchdrive.app.ui.choreIcon
import com.churchdrive.app.ui.comfortTone
import com.churchdrive.app.ui.houseTask
import com.churchdrive.app.ui.houseTaskFor
import com.churchdrive.app.ui.list
import com.churchdrive.app.ui.myTodoList
import com.churchdrive.app.ui.parseForecast
import com.churchdrive.app.ui.parseTodoItems
import com.churchdrive.app.ui.pmTone
import com.churchdrive.app.ui.pmWord
import com.churchdrive.app.ui.presetLabel
import com.churchdrive.app.ui.quickSettings
import com.churchdrive.app.ui.shortDayOf
import com.churchdrive.app.ui.styledLine
import com.churchdrive.app.ui.taskLine
import com.churchdrive.app.ui.weatherIcon
import com.churchdrive.app.ui.whenText
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class PagesLogicTest {
    private val london = ZoneId.of("Europe/London")
    private val now = LocalDateTime.of(2026, 10, 3, 12, 0)

    @Test
    fun roomsAreColouredByComfort() {
        assertEquals(Tone.Green, comfortTone(20.0, "living"))
        assertEquals(Tone.Teal, comfortTone(17.0, "living"))
        assertEquals(Tone.Blue, comfortTone(15.0, "living"))
        assertEquals(Tone.Amber, comfortTone(24.0, "living"))
        assertEquals(Tone.Red, comfortTone(25.0, "living"))
        assertEquals(Tone.Teal, comfortTone(15.0, "hall"))
        assertEquals(Tone.Grey, comfortTone(null, "living"))
    }

    @Test
    fun airQualityWordsAndTones() {
        assertEquals("Good", pmWord(12.0))
        assertEquals("Fair", pmWord(50.0))
        assertEquals("Poor", pmWord(100.0))
        assertEquals("Very poor", pmWord(200.0))
        assertEquals(Tone.Green, pmTone(null))
        assertEquals(Tone.Amber, pmTone(50.0))
        assertEquals(Tone.Red, pmTone(200.0))
    }

    @Test
    fun labelsAndListsFromAttributes() {
        assertEquals("Speed 2", presetLabel("speed_2"))
        val fan = EntityState("fan.x", "on", JSONObject("""{"preset_modes":["auto","speed_1",null,""]}"""))
        assertEquals(listOf("auto", "speed_1"), fan.list("preset_modes"))
        assertEquals(emptyList<String>(), fan.list("nothing"))
        assertEquals("mdi:weather-partly-cloudy", weatherIcon("partlycloudy"))
        assertEquals("mdi:weather-rainy", weatherIcon("rainy"))
    }

    @Test
    fun readsAForecastAndSkipsBadSteps() {
        val event = JSONObject(
            """{"type":"hourly","forecast":[
              {"datetime":"2026-10-03T10:00:00+00:00","temperature":12.4,"condition":"rainy"},
              {"datetime":"not a time","temperature":1},
              {"datetime":"2026-10-03T11:00:00Z","condition":"sunny"}]}""",
        )
        val points = parseForecast(event)
        assertEquals(2, points.size)
        assertEquals(12.4, points[0].temperature!!, 0.001)
        assertEquals("rainy", points[0].condition)
        assertNull(points[1].temperature)
    }

    @Test
    fun thermostatShortcutsAreReadFromTheCard() {
        val q = quickSettings(JSONObject("""{"quick_settings":[{"name":"Off","hvac_mode":"off"},{"name":"Eco","preset_mode":"eco"}]}"""))!!
        assertEquals(listOf("Off", "Eco"), q.map { it.name })
        assertEquals("off", q[0].hvacMode)
        assertEquals("eco", q[1].presetMode)
        assertNull(quickSettings(JSONObject("{}")))
    }

    @Test
    fun aReportTimeBecomesADay() {
        assertEquals("19 Jul", shortDayOf("2026-07-19T09:00:00+00:00")?.takeIf { it.startsWith("19 Jul") }?.take(6))
        assertNull(shortDayOf("nonsense"))
    }

    @Test
    fun readsTodoItems() {
        val items = parseTodoItems(
            JSONObject(
                """{"items":[{"uid":"a","summary":"Hoover","status":"needs_action","due":"2026-10-04","description":null},
                  {"uid":"b","summary":"Bins","status":"completed"},{"summary":"No id"}]}""",
            ),
        )
        assertEquals(listOf("a", "b"), items.map { it.uid })
        assertEquals("2026-10-04", items[0].due)
        assertNull(items[0].description)
        assertEquals(true, items[1].done)
    }

    @Test
    fun theOwnListComesFromTheFirstName() {
        assertEquals("todo.priorities_hayley", myTodoList("Hayley Smith"))
        assertEquals("todo.priorities_jamie_lee", myTodoList(" Jamie-Lee O'Neil"))
        assertNull(myTodoList(null))
        assertNull(myTodoList("  "))
    }

    @Test
    fun saysWhenATaskIsDue() {
        assertEquals("", whenText(null, now, london))
        assertEquals("today", whenText("2026-10-03", now, london))
        assertEquals("tomorrow", whenText("2026-10-04", now, london))
        assertEquals("2 days overdue", whenText("2026-10-01", now, london))
        assertEquals("due now", whenText("2026-10-03T09:00:00", now, london))
        assertEquals("today 15:00", whenText("2026-10-03T15:00:00", now, london))
        assertEquals("Wednesday", whenText("2026-10-07", now, london))
        assertEquals("20 Oct", whenText("2026-10-20", now, london))
        assertEquals("tomorrow · Every 2 weeks", taskLine(TodoItem("a", "Hoover", false, "2026-10-04", "Every 2 weeks"), now))
    }

    @Test
    fun cleaningJobsGetIcons() {
        assertEquals("mdi:vacuum-outline", choreIcon("Hoover upstairs"))
        assertEquals("mdi:shower", choreIcon("Clean bathrooms"))
        assertEquals("mdi:broom", choreIcon("Something else"))
    }

    @Test
    fun readsTheJobsTheHouseSpotted() {
        val item = TodoItem("a", "Replace battery", false, null, "Automatic · Low batteries · 17% · for Jamie, Hayley")
        val task = houseTask(item)
        assertEquals(HouseTask("Low batteries", "17%", "Jamie, Hayley", "mdi:battery-alert-variant-outline"), task)
        assertTrue(houseTaskFor(task, "hayley"))
        assertEquals(false, houseTaskFor(task, "Sam"))
        val everyone = houseTask(TodoItem("b", "Filter", false, null, "Automatic · Filters due · 8% left"))
        assertEquals("Everyone", everyone.who)
        assertTrue(houseTaskFor(everyone, null))
    }

    @Test
    fun boldPartsOfALineAreKept() {
        val line = styledLine("<b>Front door</b> open · 3 min<br>")
        assertEquals("Front door open · 3 min", line.text)
        assertEquals(1, line.spanStyles.size)
        assertEquals(0, line.spanStyles[0].start)
        assertEquals(10, line.spanStyles[0].end)
    }

    @Test
    fun aPagesHeaderBecomesAPanelBeforeItsPanels() {
        val dashboard = JSONObject(
            """{"views":[{"path":"climate","sections":[{"cards":[
              {"type":"custom:auto-layout-card","header_content":"forecast","forecast_entity":"weather.x",
               "cards":[{"type":"custom:section-panel-card","title":"Heating","cards":[{"type":"custom:climate-card","entity":"climate.a"}]}]}]}]}]}""",
        )
        val panels = DashboardPanels.parse(dashboard)["climate"]!!
        assertEquals(listOf("", "Heating"), panels.map { it.title })
        assertEquals("header:forecast", panels[0].cards[0].type)
        assertEquals("weather.x", panels[0].cards[0].config.getString("forecast_entity"))
        assertEquals("custom:climate-card", panels[1].cards[0].type)
    }
}
