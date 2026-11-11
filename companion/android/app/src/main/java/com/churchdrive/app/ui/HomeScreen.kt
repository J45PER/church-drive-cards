package com.churchdrive.app.ui

import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.background
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.EvStation
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Thermostat
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
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.TopAppBar
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
    /** The app is checking its connection after being away: shows the same pill as syncing. */
    refreshing: Boolean = false,
    updateAvailable: Boolean,
    account: AccountSettings,
    lights: LightLayout,
    areaNames: Map<String, String>,
    panels: Map<String, List<PanelSpec>>,
    managerPanels: List<PanelSpec> = emptyList(),
    registry: Registry,
    call: CallService,
    onSignOut: () -> Unit,
    /** A page a widget asked for (a [Page] name); [onOpened] says it has been opened. */
    openPage: String? = null,
    onOpened: () -> Unit = {},
    /** A camera (entity id) a widget asked to see live, until it is closed. */
    openCamera: String? = null,
    openCameraAnswer: Boolean = false,
    onCameraClosed: () -> Unit = {},
    /** Open Device Health (from the account panel). */
    onDeviceHealth: () -> Unit = {},
) {
    var page by rememberSaveable { mutableStateOf(Page.Home) }
    val context = androidx.compose.ui.platform.LocalContext.current
    // The To-do page isn't on the bar: it is opened from the account panel, and opens Church Drive To-Do where that is installed.
    var panel by rememberSaveable { mutableStateOf<String?>(null) }
    val openTodo = { if (!com.churchdrive.app.TodoLink.open(context)) panel = "todo" }
    androidx.compose.runtime.LaunchedEffect(openPage) {
        if (openPage != null) {
            if (openPage == Page.Todo.name) openTodo()
            else Page.entries.firstOrNull { it.name == openPage && !it.adminOnly }?.let { page = it }
            onOpened()
        }
    }
    // Administrator-only pages (Devices, Energy) are opened from the account panel, and only while an administrator is signed in.
    val adminPages = if (isAdmin) Page.entries.filter { it.adminOnly } else emptyList()
    val pages = Page.entries.filter { !it.adminOnly && it != Page.Todo }
    if (page !in pages) page = Page.Home
    // The account panel (null shut, "account", "settings", "about" or "todo"), which slides in from the right: declared above.
    // Asked for from outside (Clive alone, or the app opened on its settings).
    androidx.compose.runtime.LaunchedEffect(openPage) { if (openPage == "settings") panel = "settings" }
    // A phone upright keeps the tall title bar and the bar at the bottom; on its side or on a tablet there is a rail down the side, and a
    // short screen has the slim title bar (the tall one would fill it). The cards go in as many columns as fit (AdaptiveColumns).
    val window = rememberAdaptiveWindow()
    val scrollBehavior = if (window.short) TopAppBarDefaults.pinnedScrollBehavior() else TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val title = if (page == Page.Home) "Hello ${userName?.substringBefore(' ') ?: ""}".trim() else page.label

    // A task tapped on the Home page: a house job goes to the device (or place) it is about, a shopping or steps list to the in-app
    // page (the To-Do app doesn't have them), and any other task to that task in the To-Do app, or the in-app page.
    val openDeviceFor = com.churchdrive.app.ui.LocalOpenDevice.current
    val houseAddress = LocalBaseUrl.current
    val openTask: (Highlight) -> Unit = { h ->
        val haPath = houseTaskHaPath(h.kind)
        val inPage = houseTaskPage(h.kind)?.let { name -> Page.entries.firstOrNull { it.name == name } }
        when {
            // A job only Home Assistant itself can do (an update waiting) opens Home Assistant.
            h.house && haPath != null -> if (!com.churchdrive.app.HomeAssistantLink.open(context, houseAddress, haPath)) onDeviceHealth()
            h.house && inPage != null -> page = inPage
            h.house -> deviceForHouseTask(h.text, deviceHealthList(entities, registry, areaNames))?.let { openDeviceFor(it.deviceId) } ?: onDeviceHealth()
            h.shopping || h.steps -> panel = "todo"
            else -> if (!com.churchdrive.app.TodoLink.open(context, h.list, h.uid)) panel = "todo"
        }
    }

    val barActions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
                    // Listening for the wake word: a small green microphone, so you can see it is on.
                    if (AssistWakeStatus.waiting) {
                        Box(Modifier.padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
                            Icon(androidx.compose.ui.res.painterResource(com.churchdrive.core.R.drawable.cd_clive_mic), contentDescription = "Listening", tint = assistPalette().third, modifier = Modifier.size(20.dp))
                        }
                    }
                    // A round button with the person's initial: easy to see and to hit.
                    Box(
                        Modifier.padding(horizontal = 6.dp).size(44.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .clickable(onClickLabel = "Account and settings") { panel = "account" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            userName?.trim()?.firstOrNull()?.uppercase() ?: "?",
                            style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
    }
    // The dashboard's icons, in the phone's own (wallpaper) colours. The Security icon follows the alarm.
    val alarmState = entities[ALARM_ENTITY]?.state
    @Composable
    fun navIcon(p: Page, selected: Boolean) {
        val tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
        // Cleaning shows Gregg's resting icon, which never changes; the rest are the dashboard's icons.
        if (p == Page.Cleaning) GreggIcon(GreggState.Idle, tint, 28.dp)
        else HaIcon(if (p == Page.Security) alarmIconName(alarmState) else p.mdi, p.fallback, tint = tint, size = 24.dp)
    }

    // Swiping left anywhere that doesn't scroll sideways opens the account panel, as well as its button.
    var swiped by remember { mutableStateOf(0f) }
    Box(
        Modifier.pointerInput(panel) {
            if (panel == null) {
                detectHorizontalDragGestures(
                    onDragStart = { swiped = 0f },
                    onDragEnd = { if (swiped < -120f) panel = "account" },
                    onHorizontalDrag = { _, dx -> swiped += dx },
                )
            }
        },
    ) {
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            if (window.short) TopAppBar(title = { Text(title) }, actions = barActions, scrollBehavior = scrollBehavior)
            else MediumTopAppBar(title = { Text(title) }, actions = barActions, scrollBehavior = scrollBehavior)
        },
        bottomBar = {
            val ctl0 = LocalAssistant.current
            val clive0 = ctl0?.open == true && ctl0?.model != null
            // With the rail down the side the bottom bar is only there while Clive is open (his buttons take its place).
            if (!window.rail || clive0) NavigationBar {
                // The same bar: while Clive is open its buttons change into his (and back).
                val assistantCtl = LocalAssistant.current
                val assistantModel = assistantCtl?.model
                androidx.compose.animation.Crossfade(assistantCtl?.open == true && assistantModel != null, Modifier.weight(1f), label = "bar") { assistantOpen ->
                    // A fixed height: the bar is laid out with room to grow, and a row that fills it would fill the whole screen.
                    Row(Modifier.fillMaxWidth().height(80.dp)) {
                        if (assistantOpen && assistantCtl != null && assistantModel != null) {
                            AssistantBarItems(assistantModel, assistantCtl.ui, onClose = assistantCtl.close, onSettings = { assistantCtl.close(); panel = "settings" })
                        } else {
                    pages.forEach { p ->
                        val selected = p == page
                        NavigationBarItem(
                            selected = selected,
                            onClick = { page = p },
                            icon = { navIcon(p, selected) },
                            label = { Text(p.label, fontSize = 11.sp, maxLines = 1) },
                        )
                    }
            
                        }
                    }
                }
            }
        },
    ) { padding ->
        Row(Modifier.fillMaxSize().padding(padding).windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))) {
        if (window.rail) {
            // The pages down the side (a phone on its side, a tablet). It scrolls, so all of them are reachable on a short screen.
            Column(
                Modifier.width(80.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceContainer)
                    .verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                pages.forEach { p ->
                    val selected = p == page
                    NavigationRailItem(
                        selected = selected,
                        onClick = { page = p },
                        icon = { navIcon(p, selected) },
                        label = { Text(p.label, fontSize = 11.sp, maxLines = 1) },
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            UpdatePrompt(updateAvailable)
            PhoneSetupPrompt(account)
            // Connecting and updating show as a pill at the top (LoadingPill), over what was remembered from last time.
            when (connection) {
                ConnectionState.Connected -> Unit
                ConnectionState.AuthFailed ->
                    if (LocalCanRetrySignIn.current) LoadingNote("Can't sign in just now. Trying again…")
                    else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Sign-in failed. The token may have been deleted or have run out.", color = MaterialTheme.colorScheme.error)
                        TileRow(listOf(TileItem("mdi:login", "Sign in again", false) { onSignOut() }), toneColors(Tone.Grey), MaterialTheme.colorScheme.onSurface, perRow = 1)
                    }
                else -> Unit
            }

            androidx.compose.runtime.CompositionLocalProvider(LocalIsAdmin provides isAdmin) {
            AdaptiveColumns {
            if (page == Page.Home) {
                HomePage(lights, areaNames, panels["home"].orEmpty(), entities, registry, call, onOpen = { if (it == Page.Todo) openTodo() else page = it }, onOpenTask = openTask)
            } else if (page == Page.Lighting) {
                LightingPage(lights, entities, areaNames, call)
            } else if (page == Page.Security) {
                androidx.compose.runtime.CompositionLocalProvider(LocalIsAdmin provides isAdmin) {
                    SecurityPage(panels["security"].orEmpty(), entities, registry, call)
                }
            } else if (page == Page.Cleaning && entities[VACUUM_ENTITY] != null) {
                // Gregg's page is drawn by the app from his own entities, so every part of it looks the same.
                CleaningPage(entities, call)
            } else {
                val key = when (page) {
                    Page.Climate -> "climate"
                    Page.Cleaning -> "cleaning"
                    Page.Devices -> "devices"
                    Page.Energy -> "energy"
                    else -> "todo"
                }
                DashboardPage(panels[key].orEmpty().let { list -> if (page == Page.Cleaning) list.map { it.copy(icon = GREGG_IDLE_ICON) } else list }, entities, registry, call)
            }
            }
            }
            // Room to scroll the last card clear of Clive's button.
            Spacer(Modifier.height(72.dp))
        }
        }
    }
    // Clive, over the page, and his round button over him: one button, the same one shut or open.
    AssistantOverlay(onSettings = { panel = "settings" })
    AssistantFab()
    LoadingPill(visible = connection != ConnectionState.AuthFailed && (connection != ConnectionState.Connected || !statesLoaded || refreshing), connecting = connection != ConnectionState.Connected)
    if (openCamera != null && statesLoaded) CameraViewerFor(openCamera, entities, registry, call, onCameraClosed, answer = openCameraAnswer)
    AccountOverlay(
        panel, { panel = it }, userName, isAdmin, account, updateAvailable, onSignOut,
        healthSummary = if (panel != null) deviceHealthSummary(deviceHealthList(entities, registry, areaNames)) else "",
        onDeviceHealth = { panel = null; onDeviceHealth() },
        adminPages = adminPages,
        managerPages = if (isAdmin) managerPanels else emptyList(),
        managerContent = { panel ->
            androidx.compose.runtime.CompositionLocalProvider(LocalPanelTone provides toneFromColour(panel.color)) {
                androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    panel.cards.forEach { CardView(it, entities, registry, call) }
                }
            }
        },
        onTodo = openTodo,
        todoOpensApp = com.churchdrive.app.TodoLink.opensApp(context),
        pageContent = { p ->
            val key = when (p) { Page.Energy -> "energy"; Page.Todo -> "todo"; else -> "devices" }
            DashboardPage(panels[key].orEmpty(), entities, registry, call)
        },
        experimentalContent = { ExperimentalPage(entities, registry, call) },
        activityContent = { ActivityPage() },
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
    registry: Registry,
    call: CallService,
    onOpen: (Page) -> Unit,
    onOpenTask: (Highlight) -> Unit,
) {
    val quality = entities[CLIMATE_QUALITY_ENTITY]?.state?.toIntOrNull()
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
        iconSlot: (@Composable (ToneColors) -> Unit)? = null,
        content: @Composable () -> Unit,
    ) {
        val spec = byTitle[title]
        val summary = rememberTemplate(spec?.summaryTemplate) ?: fallbackSummary
        val colour = rememberTemplate(spec?.colorTemplate) ?: spec?.color
        SectionPanel(
            title,
            iconName = icon,
            // Security's colour is the alarm's own (green off, blue home, red away), whatever colour the dashboard panel carries.
            tone = if (title == "Security") alarmTone(alarm?.state) else if (title == "Climate" && quality != null) qualityTone(quality) else toneFromColour(colour) ?: fallbackTone,
            summary = summary,
            iconSlot = iconSlot,
            onClick = page?.let { { onOpen(it) } },
            content = content,
        )
    }

    // The panels are the dashboard's own, in its order, so a card added in Home Assistant appears here too. The ones the
    // app draws its own way (alarm, climate, lights, vacuum) keep that; any other panel shows its cards as they are.
    TodoHighlights(entities, call, onOpen = { onOpen(Page.Todo) }, onOpenTask = onOpenTask)
    // "Heating set by hand": the assistant has stepped back from a zone for a while; a reminder with a button to hand it back now.
    AssistantPauseCard(entities, call)
    // "Which car is plugged in?", only while a plugged-in car is waiting for an answer: above the panels (Security first).
    CarConfirmCard(entities, call)
    val titles = panels.map { it.title }.ifEmpty { listOf("Security", "Climate", "Lights", "Cleaning", "Car charging") }
    titles.forEach { title ->
        when (title) {
            "Security" -> HomePanel("Security", alarmIconName(alarm?.state), alarmTone(alarm?.state), alarmLabel(alarm?.state), Page.Security) {
                CollapsibleCard("custom:alarm-panel-card|$ALARM_ENTITY", true) { AlarmCard(alarm, call, actor = entities[ALARM_ACTOR_ENTITY]) }
            }
            // The home's climate quality score (a sensor made in Home Assistant) leads the Climate panel, as on the dashboard.
            "Climate" -> HomePanel(
                "Climate", Page.Climate.mdi, if (quality != null) qualityTone(quality) else climateTone(climate),
                if (quality != null) "$quality/100 · ${qualityWord(quality)}" else "${temp(climate?.num("current_temperature"))} °C · ${climateWord(climate)}",
                Page.Climate,
            ) { CollapsibleCard("home-climate", true) { ClimateCard(climate, call, humidityPct = climate?.num("current_humidity"), outdoor = entities.values.firstOrNull { it.entityId.startsWith("weather.") }?.num("temperature"), quality = quality, zone = zoneControl(entities, CLIMATE_ENTITY)) } }
            "Lights" -> HomePanel("Lights", Page.Lighting.mdi, Tone.Amber, lightsSummary(lights.home, entities), Page.Lighting) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    lights.home.forEach { LightRoomCard(it, entities, areaNames, call) }
                }
            }
            "Cleaning" -> HomePanel(
                "Cleaning", Page.Cleaning.mdi, vacuumTone(vacuum), vacuumSummary(vacuum, entities[VACUUM_BATTERY]), Page.Cleaning,
                iconSlot = { colors -> GreggIcon(GreggState.Idle, colors.accent, 40.dp) },
            ) {
                VacuumCard(vacuum, entities[VACUUM_BATTERY], call, entities)
            }
            else -> {
                val spec = byTitle[title]
                if (spec != null) HomePanel(title, spec.icon ?: "mdi:view-dashboard-outline", Tone.Teal, "", null) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        spec.cards.forEach { CardView(it, entities, registry, call) }
                    }
                } else if (title == "Car charging") HomePanel("Car charging", "mdi:ev-station", Tone.Teal, entities[ZAPPI_MODE]?.state ?: "", null) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CollapsibleCard("custom:ev-charger-card|", true) { ChargerCard(entities, call) }
                        OctopusPlanCard(entities)
                    }
                }
            }
        }
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
    /** Draws the header's icon itself (Gregg's status), in place of [iconName] and [icon]. */
    iconSlot: (@Composable (ToneColors) -> Unit)? = null,
    /** Tapping the heading folds the panel away and back (remembered for the title while the app is open). */
    collapsible: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = toneColors(tone)
    var open by androidx.compose.runtime.saveable.rememberSaveable(title) { mutableStateOf(true) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .let { if (onClick != null) it.clickable(onClick = onClick) else if (collapsible) it.clickable(onClickLabel = if (open) "Collapse $title" else "Expand $title") { open = !open } else it }
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (iconSlot != null) iconSlot(colors)
            else if (iconName == GREGG_IDLE_ICON) GreggIcon(GreggState.Idle, colors.accent, 36.dp)
            else if (iconName != null) ToneIconName(iconName, icon ?: CdVectors.ShieldOutline, colors, size = 36)
            else if (icon != null) ToneIcon(icon, colors, size = 36)
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                // A section's title sits on the page background, not on a card, so it takes the section's colour.
                color = colors.accent,
                modifier = Modifier.weight(1f),
            )
            if (!summary.isNullOrBlank()) {
                StatusPill(summary, colors)
            }
            if (onClick != null) {
                Icon(CdVectors.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (collapsible) {
                Icon(if (open) CdVectors.ChevronUp else CdVectors.ChevronDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        // Folding minimises the cards (each shrinks to a short strip) rather than hiding them.
        androidx.compose.runtime.CompositionLocalProvider(LocalCompactCards provides (collapsible && !open)) {
            Column(Modifier.animateContentSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
        }
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

/** A banner shown when a newer version is out: tapping Update runs [onUpdate] (the Play update on a Play install, else the download of the test build). */
@Composable
internal fun UpdateBanner(onUpdate: () -> Unit) {
    val tone = toneColors(Tone.Blue)
    EntityCard(tone.container, tone.onContainer) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("A newer version of the app is ready", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            androidx.compose.material3.TextButton(onClick = onUpdate) { Text("Update") }
        }
    }
}

/** The Experimental page: things being tried out before they replace what is there. Nothing is at the moment (the new camera live view is now the main one). */
@Composable
private fun ExperimentalPage(@Suppress("UNUSED_PARAMETER") entities: Map<String, EntityState>, @Suppress("UNUSED_PARAMETER") registry: Registry, @Suppress("UNUSED_PARAMETER") call: CallService) {
    Text(
        "Nothing is being tried out right now. The new camera live view (bigger and full-screen pictures, Answer on the doorbell, Talk) is now the main one, on the Security page and wherever a camera shows.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
