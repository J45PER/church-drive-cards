package com.churchdrive.app

import com.churchdrive.app.house.NoticeIcons
import com.churchdrive.app.ui.CdIcons
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoticeIconsTest {
    @Test
    fun eachKindGetsItsAreasIcon() {
        assertEquals("fire-alert", NoticeIcons.iconNameFor("smoke", null))
        assertEquals("doorbell", NoticeIcons.iconNameFor("doorbell", null))
        assertEquals("shield", NoticeIcons.iconNameFor("alarm_mode", null))
        assertEquals("car-ev", NoticeIcons.iconNameFor("car_charged", null))
        assertEquals("bolt", NoticeIcons.iconNameFor("cheap_rate", null))
        assertEquals("person", NoticeIcons.iconNameFor("arrivals:person.jamie", null))
        assertEquals("device-health", NoticeIcons.iconNameFor("internet", null))
        assertEquals("todo", NoticeIcons.iconNameFor("todo_overdue", null))
    }

    @Test
    fun aReminderAndAShoppingListAreKnownByTheirTag() {
        assertEquals("todo", NoticeIcons.iconNameFor(null, "church-drive-task-call-the-plumber"))
        assertEquals("cart", NoticeIcons.iconNameFor(null, "shopping-abc123"))
        assertNull(NoticeIcons.iconNameFor("mystery", "other"))
    }

    @Test
    fun everyIconNamedIsOneOfTheHouses() {
        val kinds = listOf("smoke", "doorbell", "alarm_mode", "car_charged", "cheap_rate", "vacuum", "left_on", "internet", "todo_new")
        kinds.forEach { assertTrue(it, CdIcons.icons.containsKey(NoticeIcons.iconNameFor(it, null))) }
        assertTrue(CdIcons.icons.containsKey("cart"))
    }
}
