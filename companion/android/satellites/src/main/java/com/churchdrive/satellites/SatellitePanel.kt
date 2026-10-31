package com.churchdrive.satellites

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.churchdrive.app.TrustedApps
import com.churchdrive.app.UpdateCheck

private val PanelShape = RoundedCornerShape(28.dp)

/**
 * The account panel, laid out like the main Church Drive app's and the To-Do app's: it slides in from the right over the app, from the
 * person's initial or a swipe left. It holds Settings, About and Sign out; Settings and About slide in over it. Back goes back one step.
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun SatellitePanel(panel: String?, onPanel: (String?) -> Unit, vm: SatelliteModel) {
    // The last page shown, so a page keeps its words while it slides away.
    var shown by remember { mutableStateOf("account") }
    if (panel != null) shown = panel
    BackHandler(enabled = panel != null) { onPanel(if (panel == "account") null else "account") }
    AnimatedVisibility(
        visible = panel != null,
        enter = slideInHorizontally(initialOffsetX = { it }),
        exit = slideOutHorizontally(targetOffsetX = { it }),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            AnimatedContent(
                targetState = shown,
                transitionSpec = {
                    val deeper = targetState != "account"
                    val move = if (deeper) slideInHorizontally { it } togetherWith slideOutHorizontally { -it / 4 }
                    else slideInHorizontally { -it / 4 } togetherWith slideOutHorizontally { it }
                    move.apply { targetContentZIndex = if (deeper) 1f else -1f }
                },
                label = "panel",
            ) { which ->
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    when (which) {
                        "settings" -> SettingsPage(vm) { onPanel("account") }
                        "about" -> AboutPage { onPanel("account") }
                        else -> AccountPage(vm, onClose = { onPanel(null) }, onOpen = onPanel)
                    }
                }
            }
        }
    }
}

@Composable
private fun PanelCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = PanelShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) { Column { content() } }
}

@Composable
private fun PanelRow(icon: ImageVector, text: String, supporting: String? = null, onClick: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null) {
    ListItem(
        headlineContent = { Text(text) },
        supportingContent = supporting?.let { { Text(it) } },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = trailing,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
    )
}

@Composable
private fun PanelTitle(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 4.dp))
    }
}

@Composable
private fun PageColumn(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}

@Composable
private fun AccountPage(vm: SatelliteModel, onClose: () -> Unit, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    PageColumn {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Close") }
        }
        PanelCard {
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                    Text(
                        vm.personName?.trim()?.firstOrNull()?.uppercase() ?: "?",
                        style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Column {
                    Text(vm.personName ?: "Signed in", style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (vm.borrowed) "Signed in through Church Drive" else "Church Drive Satellites",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        PanelCard { PanelRow(Icons.Outlined.Home, "Open Church Drive", "The main app: lights, alarm, cameras and the rest", onClick = { ChurchDriveLink.open(context) }) }
        PanelCard {
            PanelRow(Icons.Outlined.Settings, "Settings", onClick = { onOpen("settings") })
            PanelRow(Icons.Outlined.Info, "About", onClick = { onOpen("about") })
        }
        // A sign-in taken from the Church Drive app is that app's to end; signing out there signs this one out too.
        PanelCard {
            if (vm.borrowed) PanelRow(Icons.AutoMirrored.Outlined.Logout, "Sign out", "This app uses the Church Drive app's sign-in. Sign out there and it signs out here too.")
            else PanelRow(Icons.AutoMirrored.Outlined.Logout, "Sign out", onClick = { onClose(); vm.signOut() })
        }
    }
}

@Composable
private fun SettingsPage(vm: SatelliteModel, onBack: () -> Unit) {
    PageColumn {
        PanelTitle("Settings", onBack)
        PanelCard {
            PanelRow(
                Icons.Outlined.Visibility, "Open on the satellite I left",
                "When this is off, the app always opens on the list of satellites.",
                trailing = { Switch(checked = vm.rememberSatellite, onCheckedChange = { vm.changeRememberSatellite(it) }) },
            )
        }
        PanelCard {
            PanelRow(
                Icons.Outlined.Settings, "Each satellite's own settings",
                "Design, glances, backlight and the switches are on the satellite's page: tap it in the list.",
            )
        }
    }
}

@Composable
private fun AboutPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    // The key this installed copy is signed with. On the Google Play version that is Play's own key, which the Church Drive app has to be told
    // about before it will share its sign-in (TrustedApps.CERTS), so it is shown here to be copied.
    val certs = remember { TrustedApps.ownCerts(context) }
    PageColumn {
        PanelTitle("About", onBack)
        PanelCard {
            PanelRow(Icons.Outlined.Info, "Church Drive Satellites", "Version ${BuildConfig.VERSION_NAME} (build ${BuildConfig.BUILD}) · ${BuildConfig.COMMIT}")
            PanelRow(
                Icons.Outlined.SystemUpdate, "Updates",
                if (BuildConfig.PLAY) "Google Play keeps this app up to date. Tap to open its Play page" else "A test build: newer ones are on GitHub, under the satellites-latest release",
                onClick = if (BuildConfig.PLAY) ({ UpdateCheck.openPlayPage(context, context.packageName) }) else null,
            )
        }
        PanelCard {
            if (certs.isEmpty()) PanelRow(Icons.Outlined.Fingerprint, "Signing certificate (SHA-256)", "Not available")
            certs.forEach { c ->
                PanelRow(
                    Icons.Outlined.Fingerprint, "Signing certificate (SHA-256)",
                    if (copied) "Copied" else "$c\nTap to copy. The Church Drive app needs this to share its sign-in with this app.",
                    onClick = { clipboard.setText(AnnotatedString(c)); copied = true },
                )
            }
        }
    }
}
