package com.churchdrive.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EvStation
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.churchdrive.app.ha.CallService
import com.churchdrive.app.ha.ConnectionState
import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ha.Registry

/** The signed-in app: a page at a time, a collapsing title, and the page bar along the bottom. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    connection: ConnectionState,
    entities: Map<String, EntityState>,
    userName: String?,
    lights: LightLayout,
    areaNames: Map<String, String>,
    panels: Map<String, List<PanelSpec>>,
    registry: Registry,
    call: CallService,
    onSignOut: () -> Unit,
) {
    var page by rememberSaveable { mutableStateOf(Page.Home) }
    var menuOpen by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val title = if (page == Page.Home) "Hello ${userName?.substringBefore(' ') ?: ""}".trim() else page.label

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumTopAppBar(
                title = { Text(title) },
                actions = {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.AccountCircle, contentDescription = "Account")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Sign out") },
                            onClick = {
                                menuOpen = false
                                onSignOut()
                            },
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = {
            // The dashboard's nav bar: each page's own icon and colour (Security follows the alarm).
            val alarmState = entities[ALARM_ENTITY]?.state
            NavigationBar {
                Page.entries.forEach { p ->
                    val tone = toneColors(pageTone(p, alarmState))
                    val selected = p == page
                    NavigationBarItem(
                        selected = selected,
                        onClick = { page = p },
                        icon = {
                            HaIcon(
                                if (p == Page.Security) alarmIconName(alarmState) else p.mdi,
                                p.fallback,
                                tint = if (selected) tone.onAccent else tone.accent,
                                size = 24.dp,
                            )
                        },
                        label = { Text(p.label, fontSize = 11.sp, maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = tone.accent,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            when (connection) {
                ConnectionState.Connected -> Unit
                ConnectionState.AuthFailed ->
                    Text("Sign-in failed. Check the token.", color = MaterialTheme.colorScheme.error)
                else -> Text("Connecting…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (page == Page.Home) {
                HomePage(lights, areaNames, entities, call, onOpen = { page = it })
            } else if (page == Page.Lighting) {
                LightingPage(lights, entities, areaNames, call)
            } else if (page == Page.Security) {
                SecurityPage(panels["security"].orEmpty(), entities, registry, call)
            } else {
                page.sections.forEach { section ->
                    SectionPanel(section.title) {
                        when (section.kind) {
                            SectionKind.Alarm -> AlarmCard(entities[ALARM_ENTITY], call)
                            SectionKind.NotBuilt -> NotBuiltCard()
                        }
                    }
                }
            }
        }
    }
}

/**
 * Home: the same five panels as the dashboard's Quick Actions page, each with a coloured icon,
 * a live one-line summary, and a tap through to its own page.
 */
@Composable
private fun HomePage(
    lights: LightLayout,
    areaNames: Map<String, String>,
    entities: Map<String, EntityState>,
    call: CallService,
    onOpen: (Page) -> Unit,
) {
    val alarm = entities[ALARM_ENTITY]
    val climate = entities[CLIMATE_ENTITY]
    val vacuum = entities[VACUUM_ENTITY]

    SectionPanel(
        "Security", icon = Icons.Filled.Security, tone = alarmTone(alarm?.state),
        summary = alarmLabel(alarm?.state), onClick = { onOpen(Page.Security) },
    ) { AlarmCard(alarm, call) }

    SectionPanel(
        "Climate", icon = Icons.Filled.Thermostat, tone = climateTone(climate),
        summary = "${temp(climate?.num("current_temperature"))} °C · ${climateWord(climate)}",
        onClick = { onOpen(Page.Climate) },
    ) { ClimateCard(climate, call) }

    SectionPanel(
        "Lights", icon = Icons.Filled.Lightbulb, tone = Tone.Amber,
        summary = lightsSummary(lights.home, entities), onClick = { onOpen(Page.Lighting) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            lights.home.forEach { LightRoomCard(it, entities, areaNames, call) }
        }
    }

    SectionPanel(
        "Cleaning", icon = Icons.Filled.CleaningServices, tone = vacuumTone(vacuum),
        summary = vacuumSummary(vacuum, entities[VACUUM_BATTERY]), onClick = { onOpen(Page.Cleaning) },
    ) { VacuumCard(vacuum, entities[VACUUM_BATTERY], call) }

    SectionPanel(
        "Car charger", icon = Icons.Filled.EvStation, tone = Tone.Teal,
        summary = entities[ZAPPI_MODE]?.state ?: "", onClick = null,
    ) { ChargerCard(entities, call) }
}

/**
 * A titled group of cards, like the dashboard's section panel: a coloured icon, the title,
 * a live summary on the right, and a chevron when tapping the header opens the section's page.
 */
@Composable
fun SectionPanel(
    title: String,
    icon: ImageVector? = null,
    iconName: String? = null,
    tone: Tone = Tone.Grey,
    summary: String? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val colors = toneColors(tone)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .let { if (onClick != null) it.clickable(onClick = onClick) else it }
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (iconName != null) ToneIconName(iconName, icon ?: Icons.Filled.Security, colors, size = 36)
            else if (icon != null) ToneIcon(icon, colors, size = 36)
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            if (!summary.isNullOrBlank()) {
                Text(summary, style = MaterialTheme.typography.bodyMedium, color = colors.accent)
            }
            if (onClick != null) {
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        content()
    }
}

@Composable
fun NotBuiltCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Text(
            "Coming soon",
            modifier = Modifier.padding(20.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
