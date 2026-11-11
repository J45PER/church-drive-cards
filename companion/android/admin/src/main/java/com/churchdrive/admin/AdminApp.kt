package com.churchdrive.admin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import com.churchdrive.app.TrustedApps
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.churchdrive.app.UpdateCheck
import com.churchdrive.app.ui.CliveColours
import com.churchdrive.app.ui.LiveColours
import com.churchdrive.app.ui.UpdateNotice

/** The signed-in app: waits for Home Assistant to say who this is, turns away anyone who is not an administrator, else shows the pages. */
@Composable
fun AdminApp(vm: AdminModel) {
    var page by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(enabled = page != null) { page = null }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        UpdateNotice(BuildConfig.PLAY, vm.updateAvailable, UpdateCheck.ADMIN_APK_URL, "Clive Admin", Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        when {
            !vm.connected || !vm.userKnown -> Message("Connecting to the house…", "If this stays, check the phone can reach Home Assistant.")
            !vm.isAdmin -> NotAdmin(vm)
            page == COLOURS -> ColoursPage { page = null }
            page == ABOUT -> AboutPage { page = null }
            else -> HomePage(vm) { page = it }
        }
    }
}

private const val COLOURS = "colours"
private const val ABOUT = "about"

@Composable
private fun Message(title: String, body: String) {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(body, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun NotAdmin(vm: AdminModel) {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Administrators only", style = MaterialTheme.typography.titleLarge)
        Text(
            "${vm.personName ?: "This account"} is not an administrator in Home Assistant, so Clive Admin has nothing to show. " +
                "Sign in with an administrator's login.",
            style = MaterialTheme.typography.bodyMedium,
        )
        if (!vm.borrowed) TextButton(onClick = vm::signOut) { Text("Sign out") }
    }
}

@Composable
private fun HomePage(vm: AdminModel, open: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Clive Admin", style = MaterialTheme.typography.headlineSmall)
        Text("Signed in as ${vm.personName ?: "an administrator"}.", style = MaterialTheme.typography.bodyMedium)
        PageRow("Colour palettes", "See every palette as the house has it now. Changing them is done on the website.") { open(COLOURS) }
        PageRow("About", "Version, updates and this app's signing certificate.") { open(ABOUT) }
        if (vm.borrowed) {
            Text("Signed in through the Church Drive app: sign out there.", style = MaterialTheme.typography.bodySmall)
        } else {
            TextButton(onClick = vm::signOut) { Text("Sign out") }
        }
        Text("Version ${BuildConfig.VERSION_NAME} (build ${BuildConfig.BUILD}, ${BuildConfig.COMMIT})", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun PageRow(title: String, body: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp), tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** The palettes, view only: the built-in ones, each replaced by the one Home Assistant sent if that was changed. */
@Composable
private fun ColoursPage(back: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = back) { Text("Back") }
            Text("Colour palettes", style = MaterialTheme.typography.titleLarge)
        }
        Text(
            "Colour, Dark, Accent, Light, Deep. View only: change a palette on the website.",
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        LazyColumn(
            Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(CliveColours.byKey.keys.toList()) { key -> PaletteRow(key) }
        }
    }
}

@Composable
private fun PaletteRow(key: String) {
    val base = CliveColours.byKey[key] ?: return
    // Reads the live version, so the row redraws when the house changes the palette.
    val c = LiveColours.resolve(base)
    val changed = c !== base
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(paletteName(key), style = MaterialTheme.typography.titleSmall)
            if (changed) Text("changed in Home Assistant", style = MaterialTheme.typography.labelSmall)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(c.colour, c.dark, c.accent, c.light, c.deep).forEach { colour ->
                Box(Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(8.dp)).background(colour))
            }
        }
    }
}

/** "alarm_home" shown as "Alarm home". */
internal fun paletteName(key: String): String =
    key.split('_').joinToString(" ").replaceFirstChar { it.uppercase() }

/** Version, updates and the key this installed copy is signed with. */
@Composable
private fun AboutPage(back: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    // The key this installed copy is signed with. On the Google Play version that is Play's own key, which the Church Drive app has to be told
    // about before it will share its sign-in (TrustedApps.CERTS), so it is shown here to be copied.
    val certs = remember { TrustedApps.ownCerts(context) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = back) { Text("Back") }
            Text("About", style = MaterialTheme.typography.titleLarge)
        }
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Clive Admin", style = MaterialTheme.typography.titleMedium)
                Text("Version ${BuildConfig.VERSION_NAME} (build ${BuildConfig.BUILD}) · ${BuildConfig.COMMIT}", style = MaterialTheme.typography.bodyMedium)
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Updates", style = MaterialTheme.typography.titleMedium)
                if (BuildConfig.PLAY) {
                    Text("Google Play keeps this app up to date.", style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { UpdateCheck.openPlayPage(context, context.packageName) }) { Text("Open its Play page") }
                } else {
                    Text("A test build: newer ones are on GitHub, under the admin-latest release.", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Signing certificate (SHA-256)", style = MaterialTheme.typography.titleMedium)
                if (certs.isEmpty()) {
                    Text("Not available", style = MaterialTheme.typography.bodyMedium)
                } else {
                    SelectionContainer {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            certs.forEach { Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace) }
                        }
                    }
                    Button(onClick = { clipboard.setText(AnnotatedString(certs.joinToString("\n"))); copied = true }) { Text(if (copied) "Copied" else "Copy") }
                }
                Text(
                    "The Church Drive app shares its sign-in only with apps whose certificate it has been told about. Send this one to be added.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
