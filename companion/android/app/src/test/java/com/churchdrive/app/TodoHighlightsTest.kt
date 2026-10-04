package com.churchdrive.app

import com.churchdrive.app.ui.TodoItem
import com.churchdrive.app.ui.todoHighlights
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class TodoHighlightsTest {
    private val now = LocalDateTime.of(2026, 10, 4, 12, 0)
    private fun item(uid: String, summary: String, due: String? = null, done: Boolean = false, note: String? = null) = TodoItem(uid, summary, done, due, note)

    @Test
    fun overdueAndSoonestFirstThenListOrder() {
        val lists = mapOf(
            "todo.priorities_jamie" to listOf(item("a", "Later", "2026-10-09"), item("b", "Overdue", "2026-10-01"), item("c", "No date")),
            "todo.priorities_everyone" to listOf(item("d", "Shared today", "2026-10-04")),
        )
        val rows = todoHighlights(lists, "Jamie", now, ZoneOffset.UTC)
        assertEquals(listOf("Overdue", "Shared today", "Later", "No date"), rows.map { it.text })
        assertTrue(rows[0].overdue)
        assertFalse(rows[1].overdue)
        assertEquals("mdi:account-group", rows[1].icon)
        assertEquals("mdi:flag", rows[0].icon)
    }

    @Test
    fun finishedTasksAreLeftOut() {
        val rows = todoHighlights(mapOf("todo.priorities_jamie" to listOf(item("a", "Done", done = true), item("b", "Open"))), "Jamie", now, ZoneOffset.UTC)
        assertEquals(listOf("Open"), rows.map { it.text })
    }

    @Test
    fun theHousesJobsAreOnlyForThePersonOrEveryone() {
        val lists = mapOf(
            "todo.priorities_automatic" to listOf(
                item("h1", "Kitchen", note = "Automatic · Battery low · for Hayley"),
                item("h2", "Hall", note = "Automatic · Battery low · for Jamie"),
                item("h3", "Garage", note = "Automatic · Device not responding"),
            ),
        )
        val rows = todoHighlights(lists, "Jamie", now, ZoneOffset.UTC)
        assertEquals(listOf("Hall", "Garage"), rows.map { it.text })
        assertTrue(rows.all { it.auto })
        assertEquals("mdi:battery-alert-variant-outline", rows[0].icon)
    }

    @Test
    fun aRepeatingTaskShowsOnlyWhenDueWithinTwoDays() {
        val lists = mapOf(
            "todo.priorities_jamie" to listOf(
                item("a", "Bins", "2026-10-05", note = "Every 1 days · for Jamie"),
                item("b", "Gutters", "2026-10-20", note = "Every 2 weeks: Mon 09:00"),
                item("c", "Once only", "2026-10-20"),
            ),
        )
        val rows = todoHighlights(lists, "Jamie", now, ZoneOffset.UTC)
        assertEquals(listOf("Bins", "Once only"), rows.map { it.text })
    }
}
