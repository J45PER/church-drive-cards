package com.churchdrive.app

import com.churchdrive.app.ui.CardSpec
import com.churchdrive.app.ui.DashboardPanels
import com.churchdrive.app.ui.PanelSpec
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class PageArrangeTest {
    private fun panel(title: String, vararg types: String) =
        PanelSpec(title, null, null, null, null, types.map { CardSpec(it, JSONObject()) })

    @Test
    fun theAlarmLeadsTheSecurityPage() {
        val header = panel("", "header:lines")
        val out = DashboardPanels.arrange("security", listOf(header, panel("Alarm", "custom:alarm-panel-card"), panel("Safety")))
        assertEquals(listOf("Alarm", "", "Safety"), out.map { it.title })
    }

    @Test
    fun theWeatherJoinsTheClimateSection() {
        val out = DashboardPanels.arrange(
            "climate",
            listOf(panel("", "header:forecast"), panel("Heating", "custom:climate-card"), panel("Climate", "custom:climate-zone-card")),
        )
        assertEquals(listOf("Heating", "Climate"), out.map { it.title })
        assertEquals(listOf("header:forecast", "custom:climate-zone-card"), out.last().cards.map { it.type })
    }

    @Test
    fun otherPagesAndMissingPartsAreLeftAlone() {
        val todo = listOf(panel("", "header:todo_summary"), panel("My to-do"))
        assertEquals(todo, DashboardPanels.arrange("todo", todo))
        val noClimate = listOf(panel("", "header:forecast"), panel("Heating"))
        assertEquals(noClimate, DashboardPanels.arrange("climate", noClimate))
    }
}
