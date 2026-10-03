package com.churchdrive.app.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Button
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.churchdrive.app.BuildConfig
import com.churchdrive.app.UpdateCheck
import com.churchdrive.app.house.Permissions

/** What the Settings page shows and changes: the person's choices for notifications and location, and the checks behind About. */
class AccountSettings(
    val notifyOn: Boolean,
    val locationOn: Boolean,
    val onNotify: (Boolean) -> Unit,
    val onLocation: (Boolean) -> Unit,
    /** The notify service for this phone, shown to administrators for testing. */
    val notifyService: String?,
    val onCheckUpdate: ((Boolean) -> Unit) -> Unit,
)

/**
 * The account panel: it slides in from the right over the app, as Google's apps' account pages do. It holds Settings,
 * About and Sign out; Settings and About slide in over it. Back goes back one step.
 */
@Composable
fun AccountOverlay(
    panel: String?,
    onPanel: (String?) -> Unit,
    userName: String?,
    isAdmin: Boolean,
    settings: AccountSettings,
    updateAvailable: Boolean,
    onSignOut: () -> Unit,
    adminPages: List<Page> = emptyList(),
    pageContent: @Composable (Page) -> Unit = {},
) {
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
                    if (targetState != "account") slideInHorizontally { it } togetherWith slideOutHorizontally { -it / 4 }
                    else slideInHorizontally { -it / 4 } togetherWith slideOutHorizontally { it }
                },
                label = "account",
            ) { which ->
                when (which) {
                    "settings" -> SettingsPage(settings, isAdmin) { onPanel("account") }
                    "about" -> AboutPage(settings, updateAvailable) { onPanel("account") }
                    // The administrator's pages open here, as sub-pages of the panel.
                    "energy", "devices" -> adminPages.firstOrNull { it.name.lowercase() == which }?.let { p ->
                        AdminPage(p.label, { onPanel("account") }) { pageContent(p) }
                    }
                    else -> AccountPage(userName, isAdmin, adminPages, onClose = { onPanel(null) }, onOpen = onPanel, onSignOut = {
                        onPanel(null)
                        onSignOut()
                    })
                }
            }
        }
    }
}

private val PanelShape = RoundedCornerShape(28.dp)

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
private fun AccountPage(userName: String?, isAdmin: Boolean, adminPages: List<Page>, onClose: () -> Unit, onOpen: (String) -> Unit, onSignOut: () -> Unit) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Close") }
        }
        PanelCard {
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(
                    Modifier.size(64.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        userName?.trim()?.firstOrNull()?.uppercase() ?: "?",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Column {
                    Text(userName ?: "Signed in", style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (isAdmin) "Administrator" else "Church Drive",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        // Administrator-only pages (Energy, Devices).
        if (adminPages.isNotEmpty()) {
            PanelCard {
                adminPages.forEach { p -> PanelRow(p.fallback, p.label, onClick = { onOpen(p.name.lowercase()) }) }
            }
        }
        PanelCard {
            PanelRow(Icons.Outlined.Settings, "Settings", onClick = { onOpen("settings") })
            PanelRow(Icons.Outlined.Info, "About", onClick = { onOpen("about") })
        }
        PanelCard { PanelRow(Icons.AutoMirrored.Outlined.Logout, "Sign out", onClick = onSignOut) }
    }
}

/** One of the administrator's pages (Energy, Devices) as a sub-page of the panel. */
@Composable
private fun AdminPage(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        PanelTitle(title, onBack)
        content()
    }
}

@Composable
private fun SettingsPage(settings: AccountSettings, isAdmin: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    var message by remember { mutableStateOf<String?>(null) }

    // "Allow all the time" is its own question, asked after the app is allowed to see the location at all.
    val background = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
        if (Permissions.canLocate(context)) settings.onLocation(true)
        if (!Permissions.canLocateInBackground(context)) {
            message = "Location only updates while the app is open. For it to update while the app is closed, set Location to \"Allow all the time\" in Android's settings for this app."
        }
    }
    val foreground = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
        when {
            !Permissions.canLocate(context) -> message = "Location is off for this app. Allow it in Android's settings."
            Permissions.canLocateInBackground(context) -> settings.onLocation(true)
            else -> background.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) settings.onNotify(true) else message = "Notifications are off for this app. Allow them in Android's settings."
    }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PanelTitle("Settings", onBack)
        PanelCard {
            PanelRow(
                Icons.Outlined.Notifications, "Notifications",
                "Messages from the house, such as the doorbell or an alarm, come to this phone.",
                trailing = {
                    Switch(checked = settings.notifyOn, onCheckedChange = { on ->
                        message = null
                        if (!on) settings.onNotify(false)
                        else if (Permissions.canNotify(context)) settings.onNotify(true)
                        else notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    })
                },
            )
            PanelRow(
                Icons.Outlined.LocationOn, "Share my location",
                "So the house knows when you're home, at work or out.",
                trailing = {
                    Switch(checked = settings.locationOn, onCheckedChange = { on ->
                        message = null
                        when {
                            !on -> settings.onLocation(false)
                            Permissions.canLocate(context) && Permissions.canLocateInBackground(context) -> settings.onLocation(true)
                            Permissions.canLocate(context) -> background.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                            else -> foreground.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                        }
                    })
                },
            )
            PanelRow(
                Icons.Outlined.BatteryChargingFull, "Keep it running",
                "Set this app's battery use to Unrestricted so Android doesn't stop the connection.",
                onClick = { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) },
            )
        }
        message?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 8.dp)) }
        if (isAdmin && settings.notifyService != null) {
            PanelCard {
                PanelRow(
                    Icons.Outlined.Info, "This phone in Home Assistant",
                    "Send it a notification with ${settings.notifyService}",
                )
            }
        }
    }
}

@Composable
private fun AboutPage(settings: AccountSettings, updateAvailable: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    var status by remember { mutableStateOf<String?>(null) }
    val available = updateAvailable || status == "available"
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PanelTitle("About", onBack)
        PanelCard {
            PanelRow(Icons.Outlined.Info, "Church Drive", "Version ${BuildConfig.VERSION_NAME} · ${BuildConfig.COMMIT}")
            PanelRow(
                Icons.Outlined.SystemUpdate, "Check for updates",
                when {
                    status == "checking" -> "Checking…"
                    available -> "A newer version is ready"
                    status == "current" -> "You have the newest version"
                    else -> "Tap to check"
                },
                onClick = {
                    status = "checking"
                    settings.onCheckUpdate { newer -> status = if (newer) "available" else "current" }
                },
            )
        }
        if (available) {
            Button(
                onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(UpdateCheck.APK_URL))) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Download the update") }
        }
    }
}
