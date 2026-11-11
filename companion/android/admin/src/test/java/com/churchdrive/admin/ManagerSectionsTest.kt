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
        assertFalse(ManagerSections.builtHere(ManagerSection("People", null, null, listOf(ManagerCard("custom:people-manager-card", JSONObject())))))
    }

    @Test fun leavesOutPeopleLocationsAndIconsOnPurpose() {
        val keep = ManagerSection("Notifications", null, null, listOf(ManagerCard("custom:notifications-card", JSONObject())))
        val people = ManagerSection("People", null, null, listOf(ManagerCard("custom:people-manager-card", JSONObject())))
        val locations = ManagerSection("Locations", null, null, listOf(ManagerCard("custom:zone-map-card", JSONObject())))
        val icons = ManagerSection("Icons", null, null, listOf(ManagerCard("custom:icon-styles-card", JSONObject())))
        assertEquals(listOf("Notifications"), ManagerSections.forAdmin(listOf(people, keep, locations, icons)).map { it.title })
    }

    @Test fun anEmptyDashboardHasNoSections() {
        assertTrue(ManagerSections.parse(JSONObject("{}")).isEmpty())
    }
}
