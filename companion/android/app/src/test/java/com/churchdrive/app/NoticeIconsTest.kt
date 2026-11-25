package com.churchdrive.app

import com.churchdrive.app.house.IconSpec
import com.churchdrive.app.house.NoticeIcons
import com.churchdrive.app.ui.CdIcons
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoticeIconsTest {
    @Test
    fun eachKindGetsItsAgreedIcon() {
        assertEquals("smoke-detector-alert", NoticeIcons.iconNameFor("smoke", null))
        assertEquals("fire-alert", NoticeIcons.iconNameFor("heat", null))
        assertEquals("doorbell", NoticeIcons.iconNameFor("doorbell", null))
        assertEquals("car-ev", NoticeIcons.iconNameFor("car_charged", null))
        assertEquals("car+ev-plug", NoticeIcons.iconNameFor("car_not_plugged", null))
        assertEquals("octopus+pound", NoticeIcons.iconNameFor("cheap_rate", null))
        assertEquals("home", NoticeIcons.iconNameFor("arrivals:person.jamie", null))
        assertEquals("network-off", NoticeIcons.iconNameFor("internet", null))
        assertEquals("todo-clock-logo", NoticeIcons.iconNameFor("todo_overdue", null))
    }

    @Test
    fun aReminderAndAShoppingListAreKnownByTheirTag() {
        assertEquals("todo-logo+pin", NoticeIcons.iconNameFor(null, "church-drive-task-call-the-plumber"))
        assertEquals("cart", NoticeIcons.iconNameFor(null, "shopping-abc123"))
        assertNull(NoticeIcons.iconNameFor("mystery", "other"))
    }

    @Test
    fun aSpecIsABaseABadgeAndAnOffSlash() {
        assertEquals(IconSpec("car", "ev-plug", false), IconSpec.parse("car+ev-plug"))
        assertEquals(IconSpec("globe", null, true), IconSpec.parse("globe/off"))
        assertEquals(IconSpec("shield-home"), IconSpec.parse(" Shield-Home "))
        assertNull(IconSpec.parse(""))
        assertNull(IconSpec.parse("a+b+c"))
        assertNull(IconSpec.parse("a+"))
    }

    @Test
    fun theHousesIconWinsAndANonsenseOneFallsBack() {
        assertEquals("fire-alert", NoticeIcons.specFor("smoke", null, "fire-alert"))
        assertEquals("smoke-detector-alert", NoticeIcons.specFor("smoke", null, "no-such-icon"))
        assertEquals("shield-lock", NoticeIcons.specFor("alarm_mode", null, "shield-lock"))
        assertNull(NoticeIcons.specFor("mystery", null, null))
    }

    @Test
    fun everyBuiltInIconCanBeDrawn() {
        val kinds = listOf("smoke", "heat", "co", "alarm_triggered", "alarm_mode", "alarm_test", "doorbell", "night_motion", "car_plugged", "car_charging", "power_down", "cheap_rate", "energy_cost",
            "vacuum", "damp", "frost", "clive_actions", "arrivals:person.x", "left_on", "internet", "backup", "remote_access", "device_stale", "app_update", "battery_report", "filters_due", "updates",
            "todo_summary", "todo_overdue")
        kinds.forEach { assertTrue(it, NoticeIcons.drawable(IconSpec.parse(NoticeIcons.iconNameFor(it, null)))) }
        listOf("church-drive-task-x", "inbox-1", "shopping-x").forEach { assertTrue(it, NoticeIcons.drawable(IconSpec.parse(NoticeIcons.iconNameFor(null, it)))) }
        listOf("shield-home", "shield-lock", "shield-moon", "shield-off", "leaf+clive", "gregg-error", "gregg-docked", "gregg-returning", "gregg-paused", "gregg-cleaning").forEach { assertTrue(it, NoticeIcons.drawable(IconSpec.parse(it))) }
        assertFalse(NoticeIcons.drawable(IconSpec.parse("nothing")))
        assertTrue(CdIcons.icons.containsKey("cart"))
    }
}
