package com.churchdrive.admin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.churchdrive.app.UpdateCheck
import com.churchdrive.app.ui.CdVectors
import com.churchdrive.app.ui.ActivityPage
import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ui.CliveColours
import com.churchdrive.app.ui.LocalPanelTone
import com.churchdrive.app.ui.OctopusCard
import com.churchdrive.app.ui.DeviceHealthTab
import com.churchdrive.app.ui.LocalHaApi
import com.churchdrive.app.ui.deviceHealthList
import com.churchdrive.app.ui.deviceHealthSummary
import com.churchdrive.app.ui.LiveColours
import com.churchdrive.app.ui.PersonButton
import com.churchdrive.app.ui.Tone
import com.churchdrive.app.ui.ToneCard
import com.churchdrive.app.ui.ToneCardShape
import com.churchdrive.app.ui.ToneSectionHeader
import com.churchdrive.app.ui.glowBehind
import com.churchdrive.app.ui.toneFromColour
import org.json.JSONObject

/**
 * The signed-in app, laid out like the other Church Drive apps: a title bar with the person's button, the pages in a bar along the bottom,
 * and the account panel sliding in from the right (from the person's button or a swipe left).
 */
@Composable
fun AdminApp(vm: AdminModel) {
    var panel by rememberSaveable { mutableStateOf<String?>(null) }
    // Swiping left anywhere that doesn't scroll sideways opens the account panel, as in the other apps, as well as the person's button.
    Box(
        Modifier.fillMaxSize().pointerInput(panel) {
            var swiped = 0f
            detectHorizontalDragGestures(
                onDragStart = { swiped = 0f },
                onDragEnd = { if (swiped < -120f && panel == null) panel = "account" },
            ) { _, delta -> swiped += delta }
        },
    ) {
        androidx.compose.runtime.CompositionLocalProvider(LocalHaApi provides vm.api, com.churchdrive.app.ui.LocalBaseUrl provides vm.baseUrl,
            com.churchdrive.app.ui.LocalTemplates provides vm.templates, com.churchdrive.app.ui.LocalUserName provides vm.personName,
            com.churchdrive.app.ui.LocalIsAdmin provides vm.isAdmin, com.churchdrive.app.ui.LocalAreaNames provides vm.areaNames) {
            AdminPages(vm) { panel = "account" }
        }
        AdminPanel(panel, { panel = it }, vm)
    }
}

private const val HOME = 0
private const val HEALTH = 1
private const val ENERGY = 2
private const val ACTIVITY = 3
private const val DEVICES = 4

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdminPages(vm: AdminModel, onPerson: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(HOME) }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    BackHandler(enabled = tab != HOME) { tab = HOME }
    Box(Modifier.fillMaxSize()) {
    val link by vm.link.collectAsState()
    // The pull is outside the Scaffold so the collapsing title bar hears the drag first and can open before a pull starts.
    com.churchdrive.app.ui.LinkRefresh(link, vm::refresh, Modifier.fillMaxSize()) {
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            MediumTopAppBar(
                title = { Text(when (tab) { HOME -> "Clive Admin"; HEALTH -> "Device health"; ENERGY -> "Energy"; DEVICES -> "Devices"; else -> "Activity log" }) },
                actions = { PersonButton(vm.personName, onPerson) },
                scrollBehavior = scroll,
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == HOME, onClick = { tab = HOME },
                    icon = { Icon(CdVectors.Home, null) }, label = { Text("Home", fontSize = 11.sp) },
                )
                NavigationBarItem(
                    selected = tab == HEALTH, onClick = { tab = HEALTH },
                    icon = { Icon(CdVectors.BatteryCharging, null) }, label = { Text("Health", fontSize = 11.sp) },
                )
                NavigationBarItem(
                    selected = tab == ENERGY, onClick = { tab = ENERGY },
                    icon = { Icon(CdVectors.Bolt, null) }, label = { Text("Energy", fontSize = 11.sp) },
                )
                NavigationBarItem(
                    selected = tab == DEVICES, onClick = { tab = DEVICES },
                    icon = { Icon(CdVectors.DeviceServer, null) }, label = { Text("Devices", fontSize = 11.sp) },
                )
                NavigationBarItem(
                    selected = tab == ACTIVITY, onClick = { tab = ACTIVITY },
                    icon = { Icon(CdVectors.History, null) }, label = { Text("Activity", fontSize = 11.sp) },
                )
            }
        },
    ) { padding ->
        val ready = vm.connected && vm.userKnown && vm.isAdmin
        // Device health scrolls itself (its devices open pages in place), so it sits in the room under the title bar, not in the scrolling column.
        if (ready && tab == HEALTH) {
            // The update banner is on every tab, as in the other apps, so it sits above the list here.
            Column(Modifier.fillMaxSize().padding(padding)) {
                com.churchdrive.app.ui.UpdateNotice(BuildConfig.PLAY, vm.updateAvailable, UpdateCheck.ADMIN_APK_URL, "Clive Admin", Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                Box(Modifier.weight(1f).fillMaxWidth()) { DeviceHealthTab(vm.entities, vm.registry, vm.areaNames) }
            }
        } else Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            com.churchdrive.app.ui.UpdateNotice(BuildConfig.PLAY, vm.updateAvailable, UpdateCheck.ADMIN_APK_URL, "Clive Admin")
            when {
                // The pill at the top says Connecting, No connection or Restarting; nothing more is needed here.
                !vm.connected || !vm.userKnown -> Unit
                !vm.isAdmin -> Notice(
                    Tone.Red, "Administrators only",
                    "${vm.personName ?: "This account"} is not an administrator in Home Assistant, so Clive Admin has nothing to show. Sign in with an administrator's login.",
                )
                tab == ENERGY -> EnergyPage(vm)
                tab == DEVICES -> DevicesPage(vm)
                tab == ACTIVITY -> ActivityPage()
                else -> HomePage(vm, open = { tab = it })
            }
        }
    }
    }
    }
}

/**
 * Energy, as the house's dashboard lays it out and as the Church Drive app shows it: the header lines, then each panel in order (Octopus
 * Electricity, Gas, Last 7 days, Car charging, Octoplus, and whatever the dashboard has), each with its own icon, colour and summary.
 * If the dashboard cannot be read, the Octopus cards alone.
 */
@Composable
private fun EnergyPage(vm: AdminModel) {
    val panels = vm.energyPanels
    if (panels == null) {
        EnergyFallback(vm.entities)
        return
    }
    vm.energyHeader?.let { com.churchdrive.app.ui.HeaderLines(it) }
    panels.forEach { panel -> EnergyPanelView(panel, vm) }
}

/** Devices, as the house's dashboard lays it out and as the Church Drive app shows it: the TVs and speakers, and the devices and services. */
@Composable
private fun DevicesPage(vm: AdminModel) {
    val panels = vm.devicesPanels
    if (panels == null) {
        com.churchdrive.app.ui.LoadingNote("Loading this page from Home Assistant…")
        return
    }
    panels.forEach { panel -> EnergyPanelView(panel, vm) }
}

@Composable
private fun EnergyPanelView(panel: ManagerSection, vm: AdminModel) {
    val tone = toneFromColour(panel.color) ?: Tone.Grey
    com.churchdrive.app.ui.SectionPanel(
        panel.title, iconName = panel.icon, tone = tone,
        summary = com.churchdrive.app.ui.rememberTemplate(panel.summary),
    ) {
        androidx.compose.runtime.CompositionLocalProvider(LocalPanelTone provides tone) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                panel.cards.forEach { EnergyCardView(it, vm) }
            }
        }
    }
}

@Composable
private fun EnergyCardView(card: ManagerCard, vm: AdminModel) {
    when (card.type.removeSuffix("-beta")) {
        "custom:octopus-card" -> OctopusCard(card.config, vm.entities)
        "custom:car-card" -> com.churchdrive.app.ui.CarCard(card.config, vm.entities, vm.registry, vm.call)
        "custom:ev-charger-card" -> com.churchdrive.app.ui.ChargerCard(vm.entities, vm.call)
        "custom:octopus-plan-card" -> com.churchdrive.app.ui.OctopusPlanCard(vm.entities)
        "custom:media-card" -> com.churchdrive.app.ui.MediaCard(card.config, vm.entities, vm.registry, vm.call)
        "custom:system-card" -> com.churchdrive.app.ui.SystemCard(vm.entities)
        else -> Unit
    }
}

/** The Octopus cards on their own, for when the dashboard cannot be read. */
@Composable
private fun EnergyFallback(entities: Map<String, EntityState>) {
    @Composable
    fun Block(title: String, icon: ImageVector, tone: Tone, show: String) {
        androidx.compose.runtime.CompositionLocalProvider(LocalPanelTone provides tone) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ToneSectionHeader(title, icon, tone)
                OctopusCard(JSONObject().put("show", show), entities)
            }
        }
    }
    Block("Octopus Electricity", CdVectors.Bolt, Tone.Energy, "electricity")
    Block("Octopus Gas", CdVectors.Bolt, Tone.Gas, "gas")
    Block("Last 7 days", CdVectors.History, Tone.Energy, "week")
    Block("Octoplus", CdVectors.Bolt, Tone.Energy, "octoplus")
}

/** A message in a card, in the tone that says how much it matters. */
@Composable
private fun Notice(tone: Tone, title: String, body: String) {
    ToneCard(tone) { ink, soft ->
        Text(title, style = MaterialTheme.typography.titleLarge, color = ink)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = soft)
    }
}

@Composable
private fun HomePage(vm: AdminModel, open: (Int) -> Unit) {
    val health = remember(vm.entities, vm.registry, vm.areaNames) { deviceHealthList(vm.entities, vm.registry, vm.areaNames) }
    ToneSectionHeader("Device health", CdVectors.BatteryCharging, Tone.Teal, deviceHealthSummary(health))
    ToneCard(Tone.Teal, onClick = { open(HEALTH) }) { ink, soft ->
        Text("Batteries, filters and devices that stopped answering", style = MaterialTheme.typography.titleMedium, color = ink)
        Text("The ones that need attention come first.", style = MaterialTheme.typography.bodyMedium, color = soft)
    }
    ToneSectionHeader("Energy", CdVectors.Bolt, Tone.Energy)
    ToneCard(Tone.Energy, onClick = { open(ENERGY) }) { ink, soft ->
        Text("Electricity, gas and the last seven days", style = MaterialTheme.typography.titleMedium, color = ink)
        Text("Rates, what you've used so far and the cheap times.", style = MaterialTheme.typography.bodyMedium, color = soft)
    }
    ToneSectionHeader("Devices", CdVectors.DeviceServer, Tone.Media)
    ToneCard(Tone.Media, onClick = { open(DEVICES) }) { ink, soft ->
        Text("TVs, speakers, and how the house's systems are", style = MaterialTheme.typography.titleMedium, color = ink)
        Text("Internet, remote access, backups and updates.", style = MaterialTheme.typography.bodyMedium, color = soft)
    }
    ToneSectionHeader("Activity log", CdVectors.History, Tone.Indigo)
    ToneCard(Tone.Indigo, onClick = { open(ACTIVITY) }) { ink, soft ->
        Text("What people and the assistant did, and when", style = MaterialTheme.typography.titleMedium, color = ink)
        Text("Filter by day, time, person and kind.", style = MaterialTheme.typography.bodyMedium, color = soft)
    }
}

/** The palettes, view only: the built-in ones, each replaced by the one Home Assistant sent if that was changed. */
@Composable
internal fun ColoursPage() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ToneSectionHeader("Colour palettes", painterResource(R.drawable.mdi_palette), Tone.Indigo, "${CliveColours.byKey.size} palettes")
        Text(
            "Colour, Dark, Accent, Light, Deep. View only: change a palette on the website.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        CliveColours.byKey.keys.toList().forEach { key -> PaletteCard(key) }
    }
}

/** One palette on a card in its own colours (dark ground, the Clive glow behind), as the app's cards are, with its five colours under the name. */
@Composable
private fun PaletteCard(key: String) {
    val base = CliveColours.byKey[key] ?: return
    // Reads the live version, so the card redraws when the house changes the palette.
    val c = LiveColours.resolve(base)
    val changed = c !== base
    Column(
        Modifier.fillMaxWidth().clip(ToneCardShape).background(c.dark).glowBehind(c.dark).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(paletteName(key), style = MaterialTheme.typography.titleLarge, color = Color.White, modifier = Modifier.weight(1f))
            if (changed) Text("Changed in Home Assistant", style = MaterialTheme.typography.labelMedium, color = c.colour)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Colour" to c.colour, "Dark" to c.dark, "Accent" to c.accent, "Light" to c.light, "Deep" to c.deep).forEach { (name, colour) ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(
                        Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(12.dp)).background(colour)
                            .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(12.dp)),
                    )
                    Text(name, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.75f), maxLines = 1)
                }
            }
        }
    }
}

/** "alarm_home" shown as "Alarm home". */
internal fun paletteName(key: String): String =
    key.split('_').joinToString(" ").replaceFirstChar { it.uppercase() }
