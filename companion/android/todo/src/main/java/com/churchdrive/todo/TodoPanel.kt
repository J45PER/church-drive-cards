package com.churchdrive.todo

import dev.chrisbanes.haze.HazeState
import androidx.compose.ui.unit.Dp
import com.churchdrive.app.ui.sidebarParent
import com.churchdrive.app.ui.SidebarHost
import com.churchdrive.app.ui.PageColumn
import com.churchdrive.app.ui.PanelTitle
import com.churchdrive.app.ui.PanelRow
import com.churchdrive.app.ui.PanelCard
import com.churchdrive.app.ui.CdVectors

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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import com.churchdrive.app.UpdateCheck

private fun openChurchDrive(context: Context): Boolean = ChurchDriveLink.open(context)

/**
 * The account panel, laid out like the main Church Drive app's: it slides in from the right over the app, from the person's
 * initial. It holds Settings, About and Sign out; Settings and About slide in over it. Back goes back one step.
 */
@Composable
fun TodoPanel(panel: String?, onPanel: (String?) -> Unit, vm: TodoModel, acrylic: HazeState? = null, bottomInset: Dp = 0.dp) {
    SidebarHost(panel, onPanel, acrylic, bottomInset) { which ->
        when (which) {
            "settings" -> SettingsPage(vm) { onPanel(sidebarParent(which)) }
            "about" -> AboutPage(vm) { onPanel(sidebarParent(which)) }
            else -> AccountPage(vm, onClose = { onPanel(null) }, onOpen = onPanel)
        }
    }
}

@Composable
private fun AccountPage(vm: TodoModel, onClose: () -> Unit, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    PageColumn {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            IconButton(onClick = onClose) { Icon(CdVectors.Close, contentDescription = "Close") }
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
        PanelCard { PanelRow(CdVectors.Home, "Open Church Drive", "The main app: lights, alarm, cameras and the rest", onClick = { openChurchDrive(context) }, leading = { Icon(painterResource(com.churchdrive.core.R.drawable.cd_logo_church_drive), contentDescription = null) }) }
        PanelCard {
            PanelRow(CdVectors.Cog, "Settings", onClick = { onOpen("settings") }, leading = { Icon(painterResource(com.churchdrive.core.R.drawable.cd_cog_outline), contentDescription = null) })
            PanelRow(CdVectors.Info, "About", onClick = { onOpen("about") })
        }
        // A sign-in taken from the Church Drive app is that app's to end; signing out there signs this one out too.
        PanelCard {
            if (vm.borrowed) PanelRow(CdVectors.SignOut, "Sign out", "This app uses the Church Drive app's sign-in. Sign out there and it signs out here too.")
            else PanelRow(CdVectors.SignOut, "Sign out", onClick = { onClose(); vm.signOut() })
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
                CdVectors.Chat, "Notifications",
                // Reminders, the doorbell and alarms are all delivered to the phone by the Church Drive app (including Google push), once; this
                // app never posts its own, so there is nothing to switch on or off here and no second copy to arrive.
                "Task reminders and alerts from the house come through the Church Drive app, which also sets up Google push. Tap to open it and choose what you get.",
                leading = { Icon(painterResource(com.churchdrive.core.R.drawable.cd_clive_chat), contentDescription = null) },
                onClick = { openChurchDrive(context) },
            )
        }
        PanelCard {
            PanelRow(
                CdVectors.Todo, "Show finished tasks",
                "Keep each list's Done section open, instead of folded away until you ask for it.",
                trailing = { Switch(checked = vm.showDone, onCheckedChange = { vm.changeShowDone(it) }) },
            )
            PanelRow(
                CdVectors.Eye, "Open on the page I left",
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
            PanelRow(CdVectors.Info, "Church Drive To-Do", "Version ${BuildConfig.VERSION_NAME} (build ${BuildConfig.BUILD}) · ${BuildConfig.COMMIT}")
            PanelRow(
                CdVectors.SystemUpdate, "Check for updates",
                when {
                    BuildConfig.PLAY -> "Google Play keeps this app up to date. Tap to open its Play page"
                    status == "checking" -> "Checking…"
                    available -> "A newer version is ready"
                    status == "current" -> "You have the newest version"
                    else -> "Tap to check"
                },
                onClick = {
                    if (BuildConfig.PLAY) UpdateCheck.openPlayPage(context, context.packageName)
                    else {
                        status = "checking"
                        vm.checkForUpdate { newer -> status = if (newer) "available" else "current" }
                    }
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
