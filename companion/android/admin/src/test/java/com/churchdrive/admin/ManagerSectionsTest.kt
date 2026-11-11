package com.churchdrive.admin

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManagerSectionsTest {
    private val config = JSONObject(
        """{"views":[{"sections":[{"cards":[{"type":"custom:auto-layout-card","cards":[
            {"type":"custom:section-panel-card","title":"Notifications","icon":"mdi:bell","color":"neutral","cards":[{"type":"custom:notifications-card"}]},
            {"type":"custom:section-panel-card","title":"To-do settings","cards":[{"type":"vertical-stack","cards":[{"type":"heading"},{"type":"entities"},{"type":"markdown"}]}]},
            {"type":"custom:section-panel-card","title":"Watch","cards":[{"type":"custom:watch-settings-card"}]},
            {"type":"custom:section-panel-card","title":"","cards":[]}
        ]}]}]}]}""",
    )

    @Test fun readsEverySectionWithItsCardsAndTakesStacksApart() {
        val sections = ManagerSections.parse(config)
        assertEquals(listOf("Notifications", "To-do settings", "Watch"), sections.map { it.title })
        assertEquals("mdi:bell", sections[0].icon)
        assertEquals(listOf("heading", "entities", "markdown"), sections[1].cards.map { it.type })
    }

    @Test fun saysWhichSectionsCliveAdminDrawsItself() {
        val sections = ManagerSections.parse(config)
        assertTrue(ManagerSections.builtHere(sections[0]))
        assertTrue(ManagerSections.builtHere(sections[1]))
        assertTrue(ManagerSections.builtHere(sections[2])) // the Watch is built in Clive Admin now
        assertFalse(ManagerSections.builtHere(ManagerSection("Alarm lights", null, null, listOf(ManagerCard("custom:alarm-lights-card", JSONObject())))))
    }

    @Test fun leavesOutLocationsAndIconsOnPurposeButKeepsPeople() {
        val keep = ManagerSection("Notifications", null, null, listOf(ManagerCard("custom:notifications-card", JSONObject())))
        val people = ManagerSection("Alarm lights", null, null, listOf(ManagerCard("custom:alarm-lights-card", JSONObject())))
        val locations = ManagerSection("Locations", null, null, listOf(ManagerCard("custom:zone-map-card", JSONObject())))
        val icons = ManagerSection("Icons", null, null, listOf(ManagerCard("custom:icon-styles-card", JSONObject())))
        assertEquals(listOf("People", "Notifications"), ManagerSections.forAdmin(listOf(people, keep, locations, icons)).map { it.title })
    }

    @Test fun anEmptyDashboardHasNoSections() {
        assertTrue(ManagerSections.parse(JSONObject("{}")).isEmpty())
    }

    @Test fun watchNotificationsAndHealthHaveTheirOwnPagesAndTheRestIsTheManagerPage() {
        fun section(title: String, type: String) = ManagerSection(title, null, null, listOf(ManagerCard(type, JSONObject())))
        val all = listOf(
            section("Who's home", "custom:people-card"), section("Device Health", "custom:device-health-card"),
            section("Notifications", "custom:notifications-card"), section("Alarm lights", "custom:alarm-lights-card"),
            section("Watch", "custom:watch-settings-card"), section("Camera links", "custom:camera-links-card"),
            section("Automatic to-dos", "todo-list"),
        )
        assertEquals(listOf("Who's home", "Automatic to-dos"), ManagerSections.forManagerPage(all).map { it.title })
        assertEquals("Camera links", ManagerSections.forPage(all, "cameras")?.title)
        assertEquals("Watch", ManagerSections.forPage(all, "watch")?.title)
        assertEquals("Notifications", ManagerSections.forPage(all, "notifications")?.title)
        assertEquals("Device Health", ManagerSections.forPage(all, "health")?.title)
        assertFalse(ManagerSections.forAdmin(all).any { it.title == "Alarm lights" })
    }

    @Test fun aHeadingAboveAnEntitiesListBecomesItsTitle() {
        val cards = listOf(
            ManagerCard("heading", JSONObject("""{"heading":"When to make a house job"}""")),
            ManagerCard("entities", JSONObject("""{"entities":[]}""")),
            ManagerCard("markdown", JSONObject()),
        )
        val out = ManagerSections.pageCards(cards)
        assertEquals(listOf("entities", "markdown"), out.map { it.type })
        assertEquals("When to make a house job", out[0].config.optString("title"))
    }
}
