package com.churchdrive.app.ui

import androidx.compose.material.icons.Icons
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
    /** Only shown to a Home Assistant administrator. */
    val adminOnly: Boolean = false,
) {
    Home(
        "Home", "mdi:home", CdVectors.Home,
        listOf(
            Section("Security", SectionKind.Alarm),
            Section("Climate"),
            Section("Lights"),
            Section("Cleaning"),
            Section("Car charger"),
        ),
    ),
    Lighting(
        "Lights", "mdi:lightbulb", CdVectors.Bulb,
        listOf("Ground Floor", "Middle Floor", "Top Floor", "Garden", "Front Garden").map { Section(it) },
    ),
    Security(
        "Security", "mdi:shield-lock", CdVectors.ShieldOutline,
        listOf(
            Section("Alarm", SectionKind.Alarm),
            Section("Safety"),
            Section("Outdoor Cameras"),
            Section("Indoor Cameras"),
            Section("Doors & Motion"),
        ),
    ),
    Climate(
        "Climate", "mdi:leaf", CdVectors.Thermostat,
        listOf("Heating", "Climate", "Cooling", "Air Quality", "Windows & Doors").map { Section(it) },
    ),
    Cleaning(
        "Cleaning", "mdi:robot-vacuum", CdVectors.Gregg,
        listOf(Section("Cleaning")),
    ),
    Todo(
        "To-do", "mdi:format-list-checks", CdVectors.Todo,
        listOf("From the house", "My to-do", "Shopping", "Shared", "Cleaning").map { Section(it) },
    ),
    Devices("Devices", "mdi:server-network", CdVectors.DeviceServer, emptyList(), adminOnly = true),
    Energy("Energy", "mdi:lightning-bolt", CdVectors.Bolt, emptyList(), adminOnly = true),
}

/** The nav bar's colour for a page, as on the dashboard: the Security page follows the alarm. */
fun pageTone(page: Page, alarmState: String?): Tone = when (page) {
    Page.Home -> Tone.Blue
    Page.Lighting -> Tone.Lights
    Page.Security -> alarmTone(alarmState)
    Page.Climate -> Tone.Heating
    Page.Cleaning -> Tone.Gregg
    Page.Todo -> Tone.Tasks
    Page.Devices -> Tone.Media
    Page.Energy -> Tone.Energy
}

enum class SectionKind { Alarm, NotBuilt }

data class Section(val title: String, val kind: SectionKind = SectionKind.NotBuilt)
