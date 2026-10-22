package com.churchdrive.todo

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Visibility
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import com.churchdrive.app.UpdateCheck

private fun openChurchDrive(context: Context): Boolean = ChurchDriveLink.open(context)

private val PanelShape = RoundedCornerShape(28.dp)

/**
 * The account panel, laid out like the main Church Drive app's: it slides in from the right over the app, from the person's
 * initial. It holds Settings, About and Sign out; Settings and About slide in over it. Back goes back one step.
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun TodoPanel(panel: String?, onPanel: (String?) -> Unit, vm: TodoModel) {
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
                        "about" -> AboutPage(vm) { onPanel("account") }
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
private fun AccountPage(vm: TodoModel, onClose: () -> Unit, onOpen: (String) -> Unit) {
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
                        if (vm.borrowed) "Signed in through Church Drive" else "Church Drive To-Do",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        PanelCard { PanelRow(Icons.Outlined.Home, "Open Church Drive", "The main app: lights, alarm, cameras and the rest", onClick = { openChurchDrive(context) }) }
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
private fun SettingsPage(vm: TodoModel, onBack: () -> Unit) {
    val context = LocalContext.current
    PageColumn {
        PanelTitle("Settings", onBack)
        PanelCard {
            PanelRow(
                Icons.Outlined.Notifications, "Notifications",
                // Reminders, the doorbell and alarms are all delivered to the phone by the Church Drive app (including Google push), once; this
                // app never posts its own, so there is nothing to switch on or off here and no second copy to arrive.
                "Task reminders and alerts from the house come through the Church Drive app, which also sets up Google push. Tap to open it and choose what you get.",
                onClick = { openChurchDrive(context) },
            )
        }
        PanelCard {
            PanelRow(
                Icons.Outlined.Checklist, "Show finished tasks",
                "Keep each list's Done section open, instead of folded away until you ask for it.",
                trailing = { Switch(checked = vm.showDone, onCheckedChange = { vm.changeShowDone(it) }) },
            )
            PanelRow(
                Icons.Outlined.Visibility, "Open on the page I left",
                "When this is off, the app always opens on My to-do.",
                trailing = { Switch(checked = vm.rememberPage, onCheckedChange = { vm.changeRememberPage(it) }) },
            )
        }
    }
}

@Composable
private fun AboutPage(vm: TodoModel, onBack: () -> Unit) {
    val context = LocalContext.current
    var status by remember { mutableStateOf<String?>(null) }
    val available = vm.updateAvailable || status == "available"
    PageColumn {
        PanelTitle("About", onBack)
        PanelCard {
            PanelRow(Icons.Outlined.Info, "Church Drive To-Do", "Version ${BuildConfig.VERSION_NAME} (build ${BuildConfig.BUILD}) · ${BuildConfig.COMMIT}")
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
                    vm.checkForUpdate { newer -> status = if (newer) "available" else "current" }
                },
            )
        }
        if (available) {
            Button(
                onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(UpdateCheck.TODO_APK_URL))) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Download the update") }
        }
    }
}
