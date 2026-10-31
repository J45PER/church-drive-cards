package com.churchdrive.satellites

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Monitor
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.churchdrive.app.ui.Tone
import com.churchdrive.app.ui.toneColors

// The look is the main app's (companion/android/UI-STANDARDS.md): Material 3 in the phone's own colours, a medium top bar with the
// person's initial, section headers with a tone icon in a tinted circle, 28dp entity cards tinted by tone, 48dp tiles with the chosen
// one filled in the tone's strong colour, pills, and the bottom bar.

private val CardShape = RoundedCornerShape(28.dp)
private val TileShape = RoundedCornerShape(12.dp)
private val TallTileShape = RoundedCornerShape(18.dp)

/** A section's heading: its tone icon in a tinted circle, the title, and a short summary at the right. */
@Composable
private fun SectionHeader(title: String, icon: ImageVector, tone: Tone, summary: String? = null) {
    val colors = toneColors(tone)
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(36.dp).background(colors.accent.copy(alpha = 0.18f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = colors.accent, modifier = Modifier.size(20.dp))
        }
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (!summary.isNullOrBlank()) Text(summary, style = MaterialTheme.typography.bodyMedium, color = colors.accent)
    }
}

/** The shared card: rounded 28dp, tinted in a tone (or the neutral surface), 20dp inside, 16dp between things. */
@Composable
private fun EntityCard(tone: Tone?, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, body: @Composable ColumnScope.(ink: Color, soft: Color) -> Unit) {
    val colors = tone?.let { toneColors(it) }
    val container = colors?.container ?: MaterialTheme.colorScheme.surfaceContainer
    val ink = colors?.onContainer ?: MaterialTheme.colorScheme.onSurface
    val soft = ink.copy(alpha = 0.75f)
    Column(
        modifier.fillMaxWidth().clip(CardShape).background(container).let { if (onClick != null) it.clickable(onClick = onClick) else it }.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) { body(ink, soft) }
}

@Composable
private fun Pill(text: String, tone: Tone, onClick: (() -> Unit)? = null) {
    val accent = toneColors(tone).accent
    Text(
        text, color = accent, style = MaterialTheme.typography.labelMedium, maxLines = 1,
        modifier = Modifier.clip(CircleShape).background(accent.copy(alpha = 0.18f)).let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

/** A choice tile: 48dp high, the chosen one filled in the tone's strong colour (as the main app's tile rows). */
@Composable
private fun Tile(label: String, selected: Boolean, tone: Tone, ink: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = toneColors(tone)
    Box(
        modifier.height(48.dp).clip(TileShape).background(if (selected) colors.accent else ink.copy(alpha = 0.10f)).clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = if (selected) colors.onAccent else ink, fontSize = 13.sp, maxLines = 1) }
}

@Composable
private fun previewRes(design: SatDesign, state: PreviewState): Int {
    val context = LocalContext.current
    return context.resources.getIdentifier("prev_${design.prefix}_${state.key}", "drawable", context.packageName)
}

@Composable
private fun RoundPreview(design: SatDesign, state: PreviewState, size: Int) {
    val id = previewRes(design, state)
    if (id != 0) Image(painterResource(id), "${design.label} design, ${state.label.lowercase()}", Modifier.size(size.dp).clip(CircleShape), contentScale = ContentScale.Crop)
    else Box(Modifier.size(size.dp).clip(CircleShape).background(Color.Black))
}

@Composable
private fun PersonButton(name: String?) {
    Box(
        Modifier.padding(horizontal = 6.dp).size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(name?.trim()?.firstOrNull()?.uppercase() ?: "?", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

// ---------------------------------------------------------------------------------------------------- the app

@Composable
fun SatellitesApp(vm: SatelliteModel) {
    val open = vm.open
    BackHandler(enabled = vm.openSlug != null) { vm.openSlug = null }
    if (open == null) ListPage(vm) else DetailPage(vm, open)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListPage(vm: SatelliteModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            MediumTopAppBar(title = { Text(if (tab == 0) "Satellites" else "Add a satellite") }, actions = { PersonButton(vm.personName) }, scrollBehavior = scroll)
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Icon(Icons.Filled.Speaker, null) }, label = { Text("Satellites", fontSize = 11.sp) })
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Icon(Icons.Filled.QrCodeScanner, null) }, label = { Text("Add", fontSize = 11.sp) })
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
            if (tab == 0) SatelliteList(vm) else AddPage()
        }
    }
}

@Composable
private fun SatelliteList(vm: SatelliteModel) {
    val sats = vm.satellites
    val online = sats.count { it.online }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader("Home", Icons.Filled.Home, Tone.Indigo, if (sats.isEmpty()) null else "$online online")
        if (sats.isEmpty()) {
            EntityCard(null) { ink, soft ->
                Text(if (vm.connected) "No satellites yet" else "Connecting to the house…", style = MaterialTheme.typography.titleMedium, color = ink)
                Text(
                    "A satellite appears here once it has been added to Home Assistant (as an ESPHome device). Each one is found by its Design setting.",
                    style = MaterialTheme.typography.bodyMedium, color = soft,
                )
            }
        } else {
            EntityCard(null) { ink, soft ->
                sats.forEach { s ->
                    Row(
                        Modifier.fillMaxWidth().clip(TileShape).clickable { vm.openSlug = s.slug },
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        val tone = toneColors(Tone.Indigo)
                        Box(Modifier.size(40.dp).background(tone.accent.copy(alpha = 0.18f), CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Monitor, null, tint = tone.accent, modifier = Modifier.size(22.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(s.name, style = MaterialTheme.typography.titleMedium, color = ink, maxLines = 1)
                            Text(s.design?.let { "${it.label} design" } ?: "Round 3.4″ display", style = MaterialTheme.typography.bodySmall, color = soft)
                        }
                        if (s.online) Pill("Online", Tone.Green) else Pill("Offline", Tone.Grey)
                    }
                }
            }
        }
    }
}

@Composable
private fun AddPage() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader("Add a satellite", Icons.Filled.QrCodeScanner, Tone.Teal)
        EntityCard(Tone.Teal) { ink, soft ->
            Text("Scanning is coming", style = MaterialTheme.typography.titleMedium, color = ink)
            Text(
                "The plan is to scan the QR code on the satellite's screen or its sticker, send it the home Wi-Fi over Bluetooth, and name it. That is not built yet.",
                style = MaterialTheme.typography.bodyMedium, color = soft,
            )
            Text(
                "For now: add the device in Home Assistant (Settings > Devices > ESPHome). It shows up on the Satellites tab as soon as it connects.",
                style = MaterialTheme.typography.bodyMedium, color = soft,
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------------- one satellite

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailPage(vm: SatelliteModel, sat: Satellite) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            MediumTopAppBar(
                title = { Text(sat.name, maxLines = 1) },
                navigationIcon = { IconButton(onClick = { vm.openSlug = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { PersonButton(vm.personName) },
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
            if (!sat.online) EntityCard(Tone.Amber) { ink, soft ->
                Text("Offline", style = MaterialTheme.typography.titleMedium, color = ink)
                Text("Home Assistant can't reach this satellite, so changes will wait until it is back.", style = MaterialTheme.typography.bodyMedium, color = soft)
            }
            DesignSection(vm, sat)
            TemperatureSection(sat)
            if ((vm.pendingDesign ?: sat.design) == SatDesign.Glance) GlanceSection(vm, sat)
            BacklightSection(vm, sat)
            SwitchSection(vm, sat)
        }
    }
}

@Composable
private fun DesignSection(vm: SatelliteModel, sat: Satellite) {
    val current = vm.pendingDesign ?: sat.design ?: SatDesign.Glance
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader("Design", Icons.Filled.Palette, Tone.Indigo, current.label)
        EntityCard(Tone.Indigo) { ink, soft ->
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { RoundPreview(current, vm.previewState, 176) }
            Text("Preview the flow", style = MaterialTheme.typography.bodySmall, color = soft)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PreviewState.entries.forEach { st -> Tile(st.label, vm.previewState == st, Tone.Indigo, ink, Modifier.weight(1f)) { vm.previewState = st } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val accent = toneColors(Tone.Indigo)
                SatDesign.entries.forEach { d ->
                    val selected = d == current
                    Column(
                        Modifier.weight(1f).clip(TallTileShape).background(if (selected) accent.accent else ink.copy(alpha = 0.10f)).clickable(onClickLabel = d.label) { vm.chooseDesign(sat, d) }.padding(vertical = 8.dp, horizontal = 3.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        RoundPreview(d, PreviewState.Resting, 46)
                        Text(d.label, color = if (selected) accent.onAccent else ink, fontSize = 11.sp, maxLines = 1, textAlign = TextAlign.Center)
                    }
                }
            }
            Text("${current.blurb} Every screen follows the design: resting, listening, thinking, speaking and cards.", style = MaterialTheme.typography.bodySmall, color = soft)
        }
    }
}

@Composable
private fun TemperatureSection(sat: Satellite) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader("Temperatures", Icons.Filled.Thermostat, Tone.Amber, "From the house")
        EntityCard(null) { ink, soft ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Outside, from the weather feed", color = ink, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Pill("Always on", Tone.Green)
            }
            Text("The outside temperature is always the weather feed's, with no setting. The inside one shows in the Glance design only.", style = MaterialTheme.typography.bodySmall, color = soft)
        }
    }
}

@Composable
private fun GlanceSection(vm: SatelliteModel, sat: Satellite) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader("Glances", Icons.Filled.Tune, Tone.Teal, "3 of 3")
        EntityCard(null) { ink, soft ->
            (0..2).forEach { slot ->
                var menu by remember { mutableStateOf(false) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Glance ${slot + 1}", color = ink, style = MaterialTheme.typography.bodyLarge)
                    Box {
                        Pill(sat.glances.getOrNull(slot)?.ifBlank { null } ?: "Choose", Tone.Teal) { menu = true }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            GLANCE_OPTIONS.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { menu = false; vm.chooseGlance(sat, slot, option) }) }
                        }
                    }
                }
            }
            Text("Forecast is one of the options: a weather icon and the outside temperature.", style = MaterialTheme.typography.bodySmall, color = soft)
        }
    }
}

@Composable
private fun SliderRow(label: String, value: Float?, range: ClosedFloatingPointRange<Float>, unit: String, ink: Color, soft: Color, onDone: (Float) -> Unit) {
    var v by remember(value) { mutableFloatStateOf(value ?: range.start) }
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = ink, style = MaterialTheme.typography.bodyLarge)
            Text(if (value == null) "–" else "${v.toInt()}$unit", color = soft, style = MaterialTheme.typography.bodyMedium)
        }
        Slider(value = v, onValueChange = { v = it }, valueRange = range, onValueChangeFinished = { onDone(v) }, enabled = value != null)
    }
}

@Composable
private fun BacklightSection(vm: SatelliteModel, sat: Satellite) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader("Backlight", Icons.Filled.Brightness6, Tone.Blue, if (sat.nightStart != null) "Dims at ${"%02d".format(sat.nightStart)}:00" else null)
        EntityCard(null) { ink, soft ->
            SliderRow("Day", sat.backlightDay, 8f..100f, "%", ink, soft) { vm.setNumber(sat, "backlight_day", it) }
            SliderRow("Night", sat.backlightNight, 1f..100f, "%", ink, soft) { vm.setNumber(sat, "backlight_night", it) }
            SliderRow("Night starts at", sat.nightStart?.toFloat(), 0f..23f, ":00", ink, soft) { vm.setNumber(sat, "night_starts_hour", it) }
            SliderRow("Night ends at", sat.nightEnd?.toFloat(), 0f..23f, ":00", ink, soft) { vm.setNumber(sat, "night_ends_hour", it) }
        }
    }
}

@Composable
private fun SwitchSection(vm: SatelliteModel, sat: Satellite) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader("On the satellite", Icons.Filled.Speaker, Tone.Purple, sat.signal?.let { "Wi-Fi $it dBm" })
        EntityCard(null) { ink, _ ->
            listOf(
                Triple("Microphone", sat.microphone, "microphone"), Triple("Spoken answers", sat.spokenAnswers, "spoken_answers"),
                Triple("Follow-up listening", sat.followUp, "follow_up_listening"), Triple("Night mode", sat.nightMode, "night_mode"),
            ).forEach { (label, on, suffix) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(label, color = ink, style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = on == true, enabled = on != null, onCheckedChange = { vm.setSwitch(sat, suffix, it) })
                }
            }
        }
    }
}
