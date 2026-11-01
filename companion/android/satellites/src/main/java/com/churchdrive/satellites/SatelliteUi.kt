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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TextButton
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.churchdrive.app.ui.PersonButton
import com.churchdrive.app.ui.Tone
import com.churchdrive.app.ui.ToneCard
import com.churchdrive.app.ui.TonePill
import com.churchdrive.app.ui.ToneSectionHeader
import com.churchdrive.app.ui.ToneTallTileShape
import com.churchdrive.app.ui.ToneTile
import com.churchdrive.app.ui.ToneTileShape
import com.churchdrive.app.ui.toneColors

// The look is the main app's (companion/android/UI-STANDARDS.md): Material 3 in the phone's own colours, a medium top bar with the
// person's initial, section headers with a tone icon in a tinted circle, 28dp entity cards tinted by tone, 48dp tiles with the chosen
// one filled in the tone's strong colour, pills, and the bottom bar.

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

// ---------------------------------------------------------------------------------------------------- the app

@Composable
fun SatellitesApp(vm: SatelliteModel) {
    val open = vm.open
    var panel by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(enabled = vm.openSlug != null && panel == null) { vm.openSlug = null }
    LaunchedEffect(vm.openSlug) { vm.noteOpen(vm.openSlug) }
    // Swiping left anywhere that doesn't scroll sideways opens the account panel, as in the main app, as well as the person's button.
    Box(
        Modifier.fillMaxSize().pointerInput(panel) {
            var swiped = 0f
            detectHorizontalDragGestures(
                onDragStart = { swiped = 0f },
                onDragEnd = { if (swiped < -120f && panel == null) panel = "account" },
            ) { _, delta -> swiped += delta }
        },
    ) {
        if (open == null) ListPage(vm) { panel = "account" } else DetailPage(vm, open) { panel = "account" }
        SatellitePanel(panel, { panel = it }, vm)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListPage(vm: SatelliteModel, onPerson: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm.notice) {
        vm.notice?.let { snackbar.showSnackbar(it); vm.notice = null }
    }
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            MediumTopAppBar(title = { Text(if (tab == 0) "Satellites" else "Add a satellite") }, actions = { PersonButton(vm.personName, onPerson) }, scrollBehavior = scroll)
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Icon(Icons.Filled.Speaker, null) }, label = { Text("Satellites", fontSize = 11.sp) })
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Icon(Icons.Filled.QrCodeScanner, null) }, label = { Text("Add", fontSize = 11.sp) })
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
            if (tab == 0) SatelliteList(vm) else AddPage(vm)
        }
    }
}

@Composable
private fun SatelliteList(vm: SatelliteModel) {
    val sats = vm.satellites
    val online = sats.count { it.online }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ToneSectionHeader("Home", Icons.Filled.Home, Tone.Indigo, if (sats.isEmpty()) null else "$online online")
        if (sats.isEmpty()) {
            ToneCard(null) { ink, soft ->
                SatelliteMark(androidx.compose.foundation.isSystemInDarkTheme(), height = 64.dp, animate = false)
                Text(if (vm.connected) "No satellites yet" else "Connecting to the house…", style = MaterialTheme.typography.titleMedium, color = ink)
                Text(
                    "A satellite appears here once it has been added to Home Assistant. Use the Add tab to pair a new one.",
                    style = MaterialTheme.typography.bodyMedium, color = soft,
                )
            }
        } else {
            ToneCard(null) { ink, soft ->
                sats.forEach { s ->
                    Row(
                        Modifier.fillMaxWidth().clip(ToneTileShape).clickable { vm.openSlug = s.slug },
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        val tone = toneColors(Tone.Indigo)
                        Box(Modifier.size(40.dp).background(tone.accent.copy(alpha = 0.18f), CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Monitor, null, tint = tone.accent, modifier = Modifier.size(22.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(s.name, style = MaterialTheme.typography.titleMedium, color = ink, maxLines = 1)
                            Text(
                                if (!s.hasScreen) "No screen" else s.design?.let { "${it.label} design" } ?: "Round 3.4″ display",
                                style = MaterialTheme.typography.bodySmall, color = soft,
                            )
                        }
                        val warning = s.warning()
                        if (warning != null) TonePill(warning, if (s.online) Tone.Amber else Tone.Red) else TonePill("Online", Tone.Green)
                    }
                }
            }
        }
    }
}

@Composable
private fun AddPage(vm: SatelliteModel) {
    val context = LocalContext.current
    // Bluetooth is needed to send the Wi-Fi: on Android 12 and later its own permissions, before that location.
    val needed = if (android.os.Build.VERSION.SDK_INT >= 31) arrayOf(android.Manifest.permission.BLUETOOTH_SCAN, android.Manifest.permission.BLUETOOTH_CONNECT)
    else arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION)
    var granted by remember { mutableStateOf(needed.all { androidx.core.content.ContextCompat.checkSelfPermission(context, it) == android.content.pm.PackageManager.PERMISSION_GRANTED }) }
    val askPermission = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()) { result ->
        granted = result.values.all { it }
    }
    var scanError by remember { mutableStateOf<String?>(null) }
    var typedName by remember { mutableStateOf("") }
    var typedCode by remember { mutableStateOf("") }

    fun scan() {
        scanError = null
        val options = com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(com.google.mlkit.vision.barcode.common.Barcode.FORMAT_QR_CODE).build()
        com.google.mlkit.vision.codescanner.GmsBarcodeScanning.getClient(context, options).startScan()
            .addOnSuccessListener { code ->
                val parsed = PairCode.parse(code.rawValue)
                if (parsed == null) scanError = "That isn't a satellite's pairing code." else vm.pairScanned(parsed)
            }
            .addOnFailureListener { scanError = "The scanner couldn't start: ${it.message ?: "try again"}." }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ToneSectionHeader("Add a satellite", Icons.Filled.QrCodeScanner, Tone.Teal)
        when (val step = vm.pairStep) {
            is SatelliteModel.PairStep.Idle -> ToneCard(Tone.Teal) { ink, soft ->
                Text("1. On the satellite, open Settings > Pair or reset. A new one shows the code by itself.", color = ink, style = MaterialTheme.typography.bodyLarge)
                Text("2. Scan the QR code on its screen (or its sticker, if it has no screen).", color = ink, style = MaterialTheme.typography.bodyLarge)
                Text("3. Give it the home Wi-Fi. It joins and is added to Home Assistant for you.", color = ink, style = MaterialTheme.typography.bodyLarge)
                if (!granted) {
                    ToneTile("Allow Bluetooth to continue", false, Tone.Teal, ink, Modifier.fillMaxWidth()) { askPermission.launch(needed) }
                    Text("Bluetooth is how the Wi-Fi is sent to the satellite. Nothing else uses it.", style = MaterialTheme.typography.bodySmall, color = soft)
                } else {
                    ToneTile("Scan the QR code", true, Tone.Teal, ink, Modifier.fillMaxWidth()) { scan() }
                }
                scanError?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
            }
            is SatelliteModel.PairStep.Scanned -> PairForm(vm, step.code)
            is SatelliteModel.PairStep.Working -> ToneCard(Tone.Teal) { ink, soft ->
                androidx.compose.material3.CircularProgressIndicator()
                Text(step.message, color = ink, style = MaterialTheme.typography.titleMedium)
                Text("Keep the phone next to the satellite and its code showing. This takes up to a couple of minutes.", style = MaterialTheme.typography.bodySmall, color = soft)
            }
            is SatelliteModel.PairStep.Done -> ToneCard(Tone.Green) { ink, soft ->
                Text("The satellite is added", color = ink, style = MaterialTheme.typography.titleMedium)
                step.note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = soft) }
                if (step.slug != null) ToneTile("Set it up", true, Tone.Green, ink, Modifier.fillMaxWidth()) { vm.pairReset(); vm.openSlug = step.slug }
                ToneTile("Done", false, Tone.Green, ink, Modifier.fillMaxWidth()) { vm.pairReset() }
            }
            is SatelliteModel.PairStep.Failed -> ToneCard(Tone.Red) { ink, soft ->
                Text("That didn't work", color = ink, style = MaterialTheme.typography.titleMedium)
                Text(step.message, style = MaterialTheme.typography.bodyMedium, color = soft)
                ToneTile("Try again", true, Tone.Red, ink, Modifier.fillMaxWidth()) { vm.pairReset() }
            }
        }
        if (vm.pairStep is SatelliteModel.PairStep.Idle) {
            ToneCard(null) { ink, soft ->
                Text("Can't scan?", color = ink, style = MaterialTheme.typography.titleMedium)
                Text("Type the name and the six digits shown under the QR code.", style = MaterialTheme.typography.bodySmall, color = soft)
                OutlinedTextField(typedName, { typedName = it.trim() }, singleLine = true, label = { Text("Name, for example clive-round") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(typedCode, { typedCode = it.filter(Char::isDigit).take(6) }, singleLine = true, label = { Text("Code") }, modifier = Modifier.fillMaxWidth())
                ToneTile("Continue", false, Tone.Teal, ink, Modifier.fillMaxWidth()) {
                    if (typedName.isNotBlank() && typedCode.length == 6) vm.pairScanned(PairCode(typedName, typedCode))
                }
            }
        }
    }
}

@Composable
private fun PairForm(vm: SatelliteModel, code: PairCode) {
    var ssid by remember { mutableStateOf(vm.wifiName) }
    var password by remember { mutableStateOf("") }
    var key by remember { mutableStateOf(vm.apiKey) }
    ToneCard(Tone.Teal) { ink, soft ->
        Text("Found ${code.device}", color = ink, style = MaterialTheme.typography.titleMedium)
        Text("Check the code on its screen says ${code.spaced}.", style = MaterialTheme.typography.bodyMedium, color = soft)
        OutlinedTextField(ssid, { ssid = it }, singleLine = true, label = { Text("Home Wi-Fi name") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            password, { password = it }, singleLine = true, label = { Text("Wi-Fi password") }, modifier = Modifier.fillMaxWidth(),
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
        )
        OutlinedTextField(
            key, { key = it }, singleLine = true, label = { Text("Satellite API key") }, modifier = Modifier.fillMaxWidth(),
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
        )
        Text("The API key is the clive_round_api_key in satellite/secrets.yaml that the satellites were built with. It is kept on this phone so the next one is quicker. The Wi-Fi password is not kept.", style = MaterialTheme.typography.bodySmall, color = soft)
        ToneTile("Pair", true, Tone.Teal, ink, Modifier.fillMaxWidth()) { if (ssid.isNotBlank() && key.isNotBlank()) vm.pair(code, ssid, password, key) }
        ToneTile("Cancel", false, Tone.Teal, ink, Modifier.fillMaxWidth()) { vm.pairReset() }
    }
}

// ---------------------------------------------------------------------------------------------------- one satellite

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailPage(vm: SatelliteModel, sat: Satellite, onPerson: () -> Unit) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(sat.slug) { vm.loadRoom(sat) }
    LaunchedEffect(vm.notice) {
        vm.notice?.let { snackbar.showSnackbar(it); vm.notice = null }
    }
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            MediumTopAppBar(
                title = { Text(sat.name, maxLines = 1) },
                navigationIcon = { IconButton(onClick = { vm.openSlug = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { PersonButton(vm.personName, onPerson) },
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
            if (!sat.online) ToneCard(Tone.Amber) { ink, soft ->
                Text("Offline", style = MaterialTheme.typography.titleMedium, color = ink)
                Text("Home Assistant can't reach this satellite, so changes will wait until it is back.", style = MaterialTheme.typography.bodyMedium, color = soft)
            }
            if (sat.hasScreen) {
                DesignSection(vm, sat)
                TemperatureSection(sat)
                if ((vm.pendingDesign ?: sat.design) == SatDesign.Glance) GlanceSection(vm, sat)
                BacklightSection(vm, sat)
            }
            SoundSection(vm, sat)
            VoiceSection(vm, sat)
            SwitchSection(vm, sat)
            PresetsSection(vm, sat)
            StatusSection(sat)
            UpdateSection(vm, sat)
            NameRoomSection(vm, sat)
            ActionsSection(vm, sat)
        }
    }
}

@Composable
private fun DesignSection(vm: SatelliteModel, sat: Satellite) {
    val current = vm.pendingDesign ?: sat.design ?: SatDesign.Glance
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ToneSectionHeader("Design", Icons.Filled.Palette, Tone.Indigo, current.label)
        ToneCard(Tone.Indigo) { ink, soft ->
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { RoundPreview(current, vm.previewState, 176) }
            Text("Preview the flow", style = MaterialTheme.typography.bodySmall, color = soft)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PreviewState.entries.forEach { st -> ToneTile(st.label, vm.previewState == st, Tone.Indigo, ink, Modifier.weight(1f)) { vm.previewState = st } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val accent = toneColors(Tone.Indigo)
                SatDesign.entries.forEach { d ->
                    val selected = d == current
                    Column(
                        Modifier.weight(1f).clip(ToneTallTileShape).background(if (selected) accent.accent else ink.copy(alpha = 0.10f)).clickable(onClickLabel = d.label) { vm.chooseDesign(sat, d) }.padding(vertical = 8.dp, horizontal = 3.dp),
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
        ToneSectionHeader("Temperatures", Icons.Filled.Thermostat, Tone.Amber, "From the house")
        ToneCard(null) { ink, soft ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Outside, from the weather feed", color = ink, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                TonePill("Always on", Tone.Green)
            }
            Text("The outside temperature is always the weather feed's, with no setting. The inside one shows in the Glance design only.", style = MaterialTheme.typography.bodySmall, color = soft)
        }
    }
}

@Composable
private fun GlanceSection(vm: SatelliteModel, sat: Satellite) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ToneSectionHeader("Glances", Icons.Filled.Tune, Tone.Teal, "3 of 3")
        ToneCard(null) { ink, soft ->
            (0..2).forEach { slot ->
                var menu by remember { mutableStateOf(false) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Glance ${slot + 1}", color = ink, style = MaterialTheme.typography.bodyLarge)
                    Box {
                        TonePill(sat.glances.getOrNull(slot)?.ifBlank { null } ?: "Choose", Tone.Teal) { menu = true }
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
        ToneSectionHeader("Backlight", Icons.Filled.Brightness6, Tone.Blue, if (sat.nightStart != null) "Dims at ${"%02d".format(sat.nightStart)}:00" else null)
        ToneCard(null) { ink, soft ->
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
        ToneSectionHeader("On the satellite", Icons.Filled.Speaker, Tone.Purple, sat.signal?.let { "Wi-Fi $it dBm" })
        ToneCard(null) { ink, _ ->
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

// ---------------------------------------------------------------------------------------------------- status, sound, name, actions

@Composable
private fun FactRow(label: String, value: String, ink: Color, soft: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        Text(label, color = ink, style = MaterialTheme.typography.bodyLarge)
        Text(value, color = soft, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End, modifier = Modifier.weight(1f).padding(start = 16.dp))
    }
}

/** "31 Oct, 20:43" for a timestamp Home Assistant sent, or null if it can't be read. */
private fun whenText(iso: String?): String? = iso?.let {
    runCatching {
        java.time.OffsetDateTime.parse(it).atZoneSameInstant(java.time.ZoneId.systemDefault())
            .format(java.time.format.DateTimeFormatter.ofPattern("d MMM, HH:mm"))
    }.getOrNull()
}

@Composable
private fun StatusSection(sat: Satellite) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ToneSectionHeader("Status", Icons.Filled.Info, Tone.Green, if (sat.online) "Online" else "Offline")
        ToneCard(null) { ink, soft ->
            val dbm = sat.signal?.toIntOrNull()
            FactRow("Wi-Fi", dbm?.let { "${signalWords(it)} ($it dBm)" } ?: "–", ink, soft)
            sat.network?.let { FactRow("Network", it, ink, soft) }
            FactRow("Address", sat.ip ?: "–", ink, soft)
            FactRow("Firmware", sat.firmware?.let { "ESPHome $it" } ?: "–", ink, soft)
            FactRow("Running for", sat.uptimeSeconds?.let(::formatUptime) ?: "–", ink, soft)
            if (!sat.online) whenText(sat.lastChanged)?.let { FactRow("Went offline", it, ink, soft) }
        }
    }
}

@Composable
private fun UpdateSection(vm: SatelliteModel, sat: Satellite) {
    val update = sat.update ?: return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ToneSectionHeader("Firmware", Icons.Filled.SystemUpdate, Tone.Orange, if (update.available) "Update ready" else "Up to date")
        ToneCard(if (update.available) Tone.Orange else null) { ink, soft ->
            Text(
                if (update.available) "Version ${update.latest ?: "newer"} is ready. This one has ${update.installed ?: "an older version"}." else "Running ${update.installed ?: "the latest firmware"}.",
                color = ink, style = MaterialTheme.typography.bodyLarge,
            )
            if (update.available) {
                ToneTile(if (update.installing) "Installing…" else "Install the update", false, Tone.Orange, ink, Modifier.fillMaxWidth()) { if (!update.installing) vm.installUpdate(sat) }
                Text("The satellite restarts when it finishes, which takes a minute or two.", style = MaterialTheme.typography.bodySmall, color = soft)
            }
        }
    }
}

@Composable
private fun SoundSection(vm: SatelliteModel, sat: Satellite) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ToneSectionHeader("Sound", Icons.Filled.VolumeUp, Tone.Coral, sat.volume?.let { "${it.toInt()}%" })
        ToneCard(null) { ink, soft ->
            SliderRow("Speaker volume", sat.volume, 0f..100f, "%", ink, soft) { vm.setNumber(sat, "volume", it) }
            if (sat.canSpeak) {
                ToneTile("Say hello", false, Tone.Coral, ink, Modifier.fillMaxWidth()) { vm.sayHello(sat) }
                Text("Makes this satellite say a short line, to hear the volume and to tell which one it is.", style = MaterialTheme.typography.bodySmall, color = soft)
            }
        }
    }
}

@Composable
private fun NameRoomSection(vm: SatelliteModel, sat: Satellite) {
    var renaming by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val room = vm.roomOf[sat.slug]
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ToneSectionHeader("Name and room", Icons.Filled.LocationOn, Tone.Teal)
        ToneCard(null) { ink, soft ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(sat.name, color = ink, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1)
                TonePill("Rename", Tone.Teal) { renaming = true }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Room", color = ink, style = MaterialTheme.typography.bodyLarge)
                Box {
                    TonePill(vm.areas.firstOrNull { it.first == room }?.second ?: "No room", Tone.Teal) { menu = true }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("No room") }, onClick = { menu = false; vm.setRoom(sat, null) })
                        vm.areas.forEach { (id, name) -> DropdownMenuItem(text = { Text(name) }, onClick = { menu = false; vm.setRoom(sat, id) }) }
                    }
                }
            }
            Text("The room is how Clive will know which TV or lights \"here\" means. Both need a Home Assistant administrator.", style = MaterialTheme.typography.bodySmall, color = soft)
        }
    }
    if (renaming) {
        var text by remember { mutableStateOf(sat.name) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Rename satellite") },
            text = { OutlinedTextField(text, { text = it }, singleLine = true, label = { Text("Name") }) },
            confirmButton = { TextButton(enabled = text.isNotBlank(), onClick = { renaming = false; vm.rename(sat, text) }) { Text("Rename") } },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ActionsSection(vm: SatelliteModel, sat: Satellite) {
    var confirmRestart by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ToneSectionHeader("Actions", Icons.Filled.RestartAlt, Tone.Red)
        ToneCard(null) { ink, soft ->
            if ("identify" in sat.buttons) {
                ToneTile("Flash the screen", false, Tone.Blue, ink, Modifier.fillMaxWidth()) { vm.press(sat, "identify") }
            }
            if ("clean_screen" in sat.buttons) {
                ToneTile("Clean screen (30 seconds)", false, Tone.Blue, ink, Modifier.fillMaxWidth()) { vm.press(sat, "clean_screen") }
            }
            if ("restart" in sat.buttons) {
                ToneTile("Restart", false, Tone.Red, ink, Modifier.fillMaxWidth()) { confirmRestart = true }
            }
            if ("reset_pairing" in sat.buttons) {
                ToneTile("Reset pairing (forget the Wi-Fi)", false, Tone.Red, ink, Modifier.fillMaxWidth()) { confirmReset = true }
            }
            ToneTile("Remove from Home Assistant", false, Tone.Red, ink, Modifier.fillMaxWidth()) { confirmRemove = true }
            if ("clean_screen" in sat.buttons) Text("Clean screen ignores touches for 30 seconds so the glass can be wiped.", style = MaterialTheme.typography.bodySmall, color = soft)
        }
    }
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove ${sat.name}?") },
            text = { Text("It disappears from Home Assistant and this list. The satellite itself isn't wiped: to use it again, reset its pairing and add it again.") },
            confirmButton = { TextButton(onClick = { confirmRemove = false; vm.remove(sat) }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancel") } },
        )
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset ${sat.name}'s pairing?") },
            text = { Text("It forgets the Wi-Fi and its own settings, then restarts showing a pairing code. Add it again with the Add tab.") },
            confirmButton = { TextButton(onClick = { confirmReset = false; vm.press(sat, "reset_pairing") }) { Text("Reset") } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
    if (confirmRestart) {
        AlertDialog(
            onDismissRequest = { confirmRestart = false },
            title = { Text("Restart ${sat.name}?") },
            text = { Text("It stops listening for about a minute while it starts again.") },
            confirmButton = { TextButton(onClick = { confirmRestart = false; vm.press(sat, "restart") }) { Text("Restart") } },
            dismissButton = { TextButton(onClick = { confirmRestart = false }) { Text("Cancel") } },
        )
    }
}

// ---------------------------------------------------------------------------------------------------- voice, presets, copying

@Composable
private fun VoiceSection(vm: SatelliteModel, sat: Satellite) {
    if (sat.voice.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ToneSectionHeader("Voice", Icons.Filled.RecordVoiceOver, Tone.Indigo)
        ToneCard(null) { ink, soft ->
            sat.voice.forEach { v ->
                var menu by remember { mutableStateOf(false) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(v.label, color = ink, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Box {
                        TonePill(v.state.ifBlank { "Choose" }, Tone.Indigo) { menu = true }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            v.options.forEach { o -> DropdownMenuItem(text = { Text(o) }, onClick = { menu = false; vm.chooseVoice(v, o) }) }
                        }
                    }
                }
            }
            Text("The assistant decides who answers and in which language. These are Home Assistant's own settings for this satellite.", style = MaterialTheme.typography.bodySmall, color = soft)
        }
    }
}

/** A list of satellites to tick, in a dialog. */
@Composable
private fun PickSatellites(title: String, confirm: String, choices: List<Satellite>, onDone: (List<Satellite>) -> Unit, onDismiss: () -> Unit) {
    var picked by remember { mutableStateOf(choices.map { it.slug }.toSet()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                choices.forEach { c ->
                    Row(Modifier.fillMaxWidth().clickable { picked = if (c.slug in picked) picked - c.slug else picked + c.slug }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = c.slug in picked, onCheckedChange = { picked = if (it) picked + c.slug else picked - c.slug })
                        Text(c.name)
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = picked.isNotEmpty(), onClick = { onDone(choices.filter { it.slug in picked }) }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PresetsSection(vm: SatelliteModel, sat: Satellite) {
    var naming by remember { mutableStateOf(false) }
    var copying by remember { mutableStateOf(false) }
    var applying by remember { mutableStateOf<Preset?>(null) }
    val others = vm.satellites.filter { it.slug != sat.slug }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ToneSectionHeader("Presets and copying", Icons.Filled.ContentCopy, Tone.Orange, if (vm.presets.isEmpty()) null else "${vm.presets.size} saved")
        ToneCard(null) { ink, soft ->
            ToneTile("Save these settings as a preset", false, Tone.Orange, ink, Modifier.fillMaxWidth()) { naming = true }
            if (others.isNotEmpty()) ToneTile("Copy these settings to other satellites", false, Tone.Orange, ink, Modifier.fillMaxWidth()) { copying = true }
            vm.presets.forEach { p ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(p.name, color = ink, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1)
                    TonePill("Apply", Tone.Orange) { applying = p }
                    TonePill("Delete", Tone.Grey) { vm.deletePreset(p.name) }
                }
            }
            Text("A preset holds the design, glances, backlight, night hours, volume and the spoken-answers and follow-up switches. Parts a satellite doesn't have are skipped.", style = MaterialTheme.typography.bodySmall, color = soft)
        }
    }
    if (naming) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { naming = false },
            title = { Text("Name the preset") },
            text = { OutlinedTextField(text, { text = it }, singleLine = true, label = { Text("For example: Bedroom, dim") }) },
            confirmButton = { TextButton(enabled = text.isNotBlank(), onClick = { naming = false; vm.savePreset(text, sat) }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { naming = false }) { Text("Cancel") } },
        )
    }
    if (copying) PickSatellites("Copy ${sat.name}'s settings to", "Copy", others, { copying = false; vm.copySettings(sat, it) }, { copying = false })
    applying?.let { preset ->
        PickSatellites("Apply \"${preset.name}\" to", "Apply", vm.satellites, { applying = null; vm.applyPreset(preset, it) }, { applying = null })
    }
}
