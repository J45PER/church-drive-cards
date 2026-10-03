package com.churchdrive.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import com.churchdrive.app.BuildConfig
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
    isAdmin: Boolean,
    statesLoaded: Boolean,
    updateAvailable: Boolean,
    account: AccountSettings,
    lights: LightLayout,
    areaNames: Map<String, String>,
    panels: Map<String, List<PanelSpec>>,
    registry: Registry,
    call: CallService,
    onSignOut: () -> Unit,
    /** A page a widget asked for (a [Page] name); [onOpened] says it has been opened. */
    openPage: String? = null,
    onOpened: () -> Unit = {},
    /** A camera (entity id) a widget asked to see live, until it is closed. */
    openCamera: String? = null,
    onCameraClosed: () -> Unit = {},
) {
    var page by rememberSaveable { mutableStateOf(Page.Home) }
    androidx.compose.runtime.LaunchedEffect(openPage) {
        if (openPage != null) {
            Page.entries.firstOrNull { it.name == openPage && !it.adminOnly }?.let { page = it }
            onOpened()
        }
    }
    // Administrator-only pages (Devices, Energy) are opened from the account panel, and only while an administrator is signed in.
    val adminPages = if (isAdmin) Page.entries.filter { it.adminOnly } else emptyList()
    val pages = Page.entries.filter { !it.adminOnly }
    if (page !in pages) page = Page.Home
    // The account panel (null shut, "account", "settings" or "about"), which slides in from the right.
    var panel by rememberSaveable { mutableStateOf<String?>(null) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val title = if (page == Page.Home) "Hello ${userName?.substringBefore(' ') ?: ""}".trim() else page.label

    Box {
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumTopAppBar(
                title = { Text(title) },
                actions = {
                    // Administrators see which build this is.
                    if (isAdmin) {
                        Text(
                            "v${BuildConfig.VERSION_NAME} · ${BuildConfig.COMMIT}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { panel = "account" }) {
                        Icon(Icons.Filled.AccountCircle, contentDescription = "Account")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = {
            // The dashboard's icons, in the phone's own (wallpaper) colours. The Security icon follows the alarm.
            val alarmState = entities[ALARM_ENTITY]?.state
            NavigationBar {
                pages.forEach { p ->
                    val selected = p == page
                    NavigationBarItem(
                        selected = selected,
                        onClick = { page = p },
                        icon = {
                            HaIcon(
                                if (p == Page.Security) alarmIconName(alarmState) else p.mdi,
                                p.fallback,
                                tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                size = 24.dp,
                            )
                        },
                        label = { Text(p.label, fontSize = 11.sp, maxLines = 1) },
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
            if (updateAvailable) UpdateBanner()
            when (connection) {
                ConnectionState.Connected ->
                    if (!statesLoaded) Text("Loading the house…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                ConnectionState.AuthFailed ->
                    Text("Sign-in failed. Check the token.", color = MaterialTheme.colorScheme.error)
                else -> Text("Connecting…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (page == Page.Home) {
                HomePage(lights, areaNames, panels["home"].orEmpty(), entities, call, onOpen = { page = it })
            } else if (page == Page.Lighting) {
                LightingPage(lights, entities, areaNames, call)
            } else if (page == Page.Security) {
                SecurityPage(panels["security"].orEmpty(), entities, registry, call)
            } else {
                val key = when (page) {
                    Page.Climate -> "climate"
                    Page.Cleaning -> "cleaning"
                    Page.Devices -> "devices"
                    Page.Energy -> "energy"
                    else -> "todo"
                }
                // The app has no media controls: the dashboard's "TVs & speakers" panel is left out.
                val shown = panels[key].orEmpty().filter { it.title != "TVs & speakers" }
                DashboardPage(shown, entities, registry, call)
            }
        }
    }
    if (openCamera != null && statesLoaded) CameraViewerFor(openCamera, entities, registry, call, onCameraClosed)
    AccountOverlay(
        panel, { panel = it }, userName, isAdmin, account, updateAvailable, onSignOut,
        adminPages = adminPages,
        pageContent = { p ->
            // The app has no media controls: the dashboard's "TVs & speakers" panel is left out.
            val key = if (p == Page.Energy) "energy" else "devices"
            DashboardPage(panels[key].orEmpty().filter { it.title != "TVs & speakers" }, entities, registry, call)
        },
    )
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
    panels: List<PanelSpec>,
    entities: Map<String, EntityState>,
    call: CallService,
    onOpen: (Page) -> Unit,
) {
    val alarm = entities[ALARM_ENTITY]
    val climate = entities[CLIMATE_ENTITY]
    val vacuum = entities[VACUUM_ENTITY]
    val byTitle = panels.associateBy { it.title }

    /**
     * A Home panel: the icon is the nav bar's own for that page; the summary line and colour are the dashboard
     * panel's (rendered by Home Assistant), with the app's own as a fallback until they arrive.
     */
    @Composable
    fun HomePanel(
        title: String,
        icon: String,
        fallbackTone: Tone,
        fallbackSummary: String,
        page: Page?,
        content: @Composable () -> Unit,
    ) {
        val spec = byTitle[title]
        val summary = rememberTemplate(spec?.summaryTemplate) ?: fallbackSummary
        val colour = rememberTemplate(spec?.colorTemplate) ?: spec?.color
        SectionPanel(
            title,
            iconName = icon,
            tone = toneFromColour(colour) ?: fallbackTone,
            summary = summary,
            onClick = page?.let { { onOpen(it) } },
            content = content,
        )
    }

    HomePanel("Security", alarmIconName(alarm?.state), alarmTone(alarm?.state), alarmLabel(alarm?.state), Page.Security) {
        AlarmCard(alarm, call)
    }
    // The home's climate quality score (a sensor made in Home Assistant) leads the Climate panel, as on the dashboard.
    val quality = entities[CLIMATE_QUALITY_ENTITY]?.state?.toIntOrNull()
    HomePanel(
        "Climate", Page.Climate.mdi, if (quality != null) qualityTone(quality) else climateTone(climate),
        if (quality != null) "$quality/100 · ${qualityWord(quality)}" else "${temp(climate?.num("current_temperature"))} °C · ${climateWord(climate)}",
        Page.Climate,
    ) { ClimateCard(climate, call, quality = quality) }
    HomePanel("Lights", Page.Lighting.mdi, Tone.Amber, lightsSummary(lights.home, entities), Page.Lighting) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            lights.home.forEach { LightRoomCard(it, entities, areaNames, call) }
        }
    }
    HomePanel("Cleaning", Page.Cleaning.mdi, vacuumTone(vacuum), vacuumSummary(vacuum, entities[VACUUM_BATTERY]), Page.Cleaning) {
        VacuumCard(vacuum, entities[VACUUM_BATTERY], call)
    }
    HomePanel("Car charger", "mdi:ev-station", Tone.Teal, entities[ZAPPI_MODE]?.state ?: "", null) {
        ChargerCard(entities, call)
    }
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

/** A banner shown when a newer test build is out: tapping Update downloads it (Android then offers to install it). */
@Composable
private fun UpdateBanner() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val tone = toneColors(Tone.Blue)
    EntityCard(tone.container, tone.onContainer) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("A newer version of the app is ready", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            androidx.compose.material3.TextButton(onClick = {
                context.startActivity(
                    android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(com.churchdrive.app.UpdateCheck.APK_URL)),
                )
            }) { Text("Update") }
        }
    }
}
