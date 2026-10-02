package com.churchdrive.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.ui.graphics.vector.ImageVector

/** The app's pages and the sections on each, from the mobile dashboard (without Media, Devices and Energy). */
enum class Page(
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
    val sections: List<Section>,
) {
    Home(
        "Home", Icons.Outlined.Home, Icons.Filled.Home,
        listOf(
            Section("Security", SectionKind.Alarm),
            Section("Climate"),
            Section("Lights"),
            Section("Cleaning"),
            Section("Car charger"),
        ),
    ),
    Lighting(
        "Lighting", Icons.Outlined.Lightbulb, Icons.Filled.Lightbulb,
        listOf("Ground Floor", "Middle Floor", "Top Floor", "Garden", "Front Garden").map { Section(it) },
    ),
    Security(
        "Security", Icons.Outlined.Security, Icons.Filled.Security,
        listOf(
            Section("Alarm", SectionKind.Alarm),
            Section("Safety"),
            Section("Outdoor Cameras"),
            Section("Indoor Cameras"),
            Section("Doors & Motion"),
        ),
    ),
    Climate(
        "Climate", Icons.Outlined.Thermostat, Icons.Filled.Thermostat,
        listOf("Heating", "Climate", "Cooling", "Air Quality", "Windows & Doors").map { Section(it) },
    ),
    Cleaning(
        "Cleaning", Icons.Outlined.CleaningServices, Icons.Filled.CleaningServices,
        listOf(Section("Cleaning")),
    ),
    Todo(
        "To-do", Icons.Outlined.Checklist, Icons.Filled.Checklist,
        listOf("From the house", "My to-do", "Shared", "Cleaning").map { Section(it) },
    ),
}

enum class SectionKind { Alarm, NotBuilt }

data class Section(val title: String, val kind: SectionKind = SectionKind.NotBuilt)
