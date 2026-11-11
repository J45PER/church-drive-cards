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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.churchdrive.app.UpdateCheck
import com.churchdrive.app.ui.CdVectors
import com.churchdrive.app.ui.CliveColours
import com.churchdrive.app.ui.LiveColours
import com.churchdrive.app.ui.PersonButton
import com.churchdrive.app.ui.Tone
import com.churchdrive.app.ui.ToneCard
import com.churchdrive.app.ui.ToneCardShape
import com.churchdrive.app.ui.ToneSectionHeader
import com.churchdrive.app.ui.glowBehind

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
        AdminPages(vm) { panel = "account" }
        AdminPanel(panel, { panel = it }, vm)
    }
}

private const val HOME = 0
private const val COLOURS = 1

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdminPages(vm: AdminModel, onPerson: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(HOME) }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    BackHandler(enabled = tab != HOME) { tab = HOME }
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            MediumTopAppBar(
                title = { Text(if (tab == HOME) "Clive Admin" else "Colour palettes") },
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
                    selected = tab == COLOURS, onClick = { tab = COLOURS },
                    icon = { Icon(painterResource(R.drawable.mdi_palette), null) }, label = { Text("Colours", fontSize = 11.sp) },
                )
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            com.churchdrive.app.ui.UpdateNotice(BuildConfig.PLAY, vm.updateAvailable, UpdateCheck.ADMIN_APK_URL, "Clive Admin")
            when {
                !vm.connected || !vm.userKnown -> Notice(Tone.Amber, "Connecting to the house…", "If this stays, check the phone can reach Home Assistant.")
                !vm.isAdmin -> Notice(
                    Tone.Red, "Administrators only",
                    "${vm.personName ?: "This account"} is not an administrator in Home Assistant, so Clive Admin has nothing to show. Sign in with an administrator's login.",
                )
                tab == COLOURS -> ColoursPage()
                else -> HomePage { tab = it }
            }
        }
    }
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
private fun HomePage(open: (Int) -> Unit) {
    val palettes = CliveColours.byKey.size
    ToneSectionHeader("Colour palettes", painterResource(R.drawable.mdi_palette), Tone.Indigo, "$palettes palettes")
    ToneCard(Tone.Indigo, onClick = { open(COLOURS) }) { ink, soft ->
        Text("Every palette as the house has it now", style = MaterialTheme.typography.titleMedium, color = ink)
        Text("View only. Changing a palette is done on the website.", style = MaterialTheme.typography.bodyMedium, color = soft)
    }
}

/** The palettes, view only: the built-in ones, each replaced by the one Home Assistant sent if that was changed. */
@Composable
private fun ColoursPage() {
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
