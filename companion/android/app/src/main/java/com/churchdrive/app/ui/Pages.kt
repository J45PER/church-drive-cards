package com.churchdrive.app.ui

/** The app's pages and the sections on each, from the mobile dashboard (without Media, Devices and Energy). */
enum class Page(val label: String, val icon: String, val sections: List<Section>) {
    Home(
        "Home", "🏠",
        listOf(
            Section("Security", SectionKind.Alarm),
            Section("Climate"),
            Section("Lights"),
            Section("Cleaning"),
            Section("Car charger"),
        ),
    ),
    Lighting(
        "Lighting", "💡",
        listOf("Ground Floor", "Middle Floor", "Top Floor", "Garden", "Front Garden").map { Section(it) },
    ),
    Security(
        "Security", "🔒",
        listOf(
            Section("Alarm", SectionKind.Alarm),
            Section("Safety"),
            Section("Outdoor Cameras"),
            Section("Indoor Cameras"),
            Section("Doors & Motion"),
        ),
    ),
    Climate(
        "Climate", "🌡️",
        listOf("Heating", "Climate", "Cooling", "Air Quality", "Windows & Doors").map { Section(it) },
    ),
    Cleaning("Cleaning", "🧹", listOf(Section("Cleaning"))),
    Todo("To-do", "✅", listOf("From the house", "My to-do", "Shared", "Cleaning").map { Section(it) }),
}

enum class SectionKind { Alarm, NotBuilt }

data class Section(val title: String, val kind: SectionKind = SectionKind.NotBuilt)
