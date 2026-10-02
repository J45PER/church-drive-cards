package com.churchdrive.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The app's pages, with the same icons as the dashboard's nav bar (`mdi:` names), and the sections on each.
 * [fallback] shows if an icon can't be drawn.
 */
enum class Page(
    val label: String,
    val mdi: String,
    val fallback: ImageVector,
    val sections: List<Section>,
) {
    Home(
        "Home", "mdi:home", Icons.Outlined.Home,
        listOf(
            Section("Security", SectionKind.Alarm),
            Section("Climate"),
            Section("Lights"),
            Section("Cleaning"),
            Section("Car charger"),
        ),
    ),
    Lighting(
        "Lights", "mdi:lightbulb", Icons.Outlined.Lightbulb,
        listOf("Ground Floor", "Middle Floor", "Top Floor", "Garden", "Front Garden").map { Section(it) },
    ),
    Security(
        "Security", "mdi:shield-lock", Icons.Outlined.Security,
        listOf(
            Section("Alarm", SectionKind.Alarm),
            Section("Safety"),
            Section("Outdoor Cameras"),
            Section("Indoor Cameras"),
            Section("Doors & Motion"),
        ),
    ),
    Climate(
        "Climate", "mdi:thermostat", Icons.Outlined.Thermostat,
        listOf("Heating", "Climate", "Cooling", "Air Quality", "Windows & Doors").map { Section(it) },
    ),
    Cleaning(
        "Cleaning", "mdi:robot-vacuum", Icons.Outlined.CleaningServices,
        listOf(Section("Cleaning")),
    ),
    Todo(
        "To-do", "mdi:format-list-checks", Icons.Outlined.Checklist,
        listOf("From the house", "My to-do", "Shared", "Cleaning").map { Section(it) },
    ),
}

/** The nav bar's colour for a page, as on the dashboard: the Security page follows the alarm. */
fun pageTone(page: Page, alarmState: String?): Tone = when (page) {
    Page.Home -> Tone.Blue
    Page.Lighting -> Tone.Amber
    Page.Security -> alarmTone(alarmState)
    Page.Climate -> Tone.Orange
    Page.Cleaning -> Tone.Blue
    Page.Todo -> Tone.Purple
}

enum class SectionKind { Alarm, NotBuilt }

data class Section(val title: String, val kind: SectionKind = SectionKind.NotBuilt)
