package com.churchdrive.satellites

import dev.chrisbanes.haze.HazeState
import androidx.compose.ui.unit.Dp
import com.churchdrive.app.ui.sidebarParent
import com.churchdrive.app.ui.SidebarHost
import com.churchdrive.app.ui.PageColumn
import com.churchdrive.app.ui.SidebarListPage
import com.churchdrive.app.ui.SidebarSubPage
import com.churchdrive.app.ui.PanelTitle
import com.churchdrive.app.ui.PanelRow
import com.churchdrive.app.ui.PanelCard
import com.churchdrive.app.ui.CdVectors

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
import androidx.compose.material.icons.outlined.Fingerprint
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.churchdrive.app.TrustedApps
import com.churchdrive.app.UpdateCheck

/**
 * The account panel, laid out like the main Church Drive app's and the To-Do app's: it slides in from the right over the app, from the
 * person's initial or a swipe left. It holds Settings, About and Sign out; Settings and About slide in over it. Back goes back one step.
 */
@Composable
fun SatellitePanel(panel: String?, onPanel: (String?) -> Unit, vm: SatelliteModel, acrylic: HazeState? = null, bottomInset: Dp = 0.dp) {
    SidebarHost(panel, onPanel, acrylic, bottomInset) { which ->
        when (which) {
            "settings" -> SettingsPage(vm) { onPanel(sidebarParent(which)) }
            "about" -> AboutPage { onPanel(sidebarParent(which)) }
            else -> AccountPage(vm, onClose = { onPanel(null) }, onOpen = onPanel)
        }
    }
}

@Composable
private fun AccountPage(vm: SatelliteModel, onClose: () -> Unit, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    SidebarListPage(vm.personName, if (vm.borrowed) "Signed in through Church Drive" else "Church Drive Satellites", onClose) {
        PanelCard { PanelRow(CdVectors.Home, "Open Church Drive", "The main app: lights, alarm, cameras and the rest", onClick = { ChurchDriveLink.open(context) }, leading = { Icon(painterResource(com.churchdrive.core.R.drawable.cd_logo_church_drive), contentDescription = null) }) }
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
private fun SettingsPage(vm: SatelliteModel, onBack: () -> Unit) {
    SidebarSubPage("Settings", onBack) {
        PanelCard {
            PanelRow(
                CdVectors.Eye, "Open on the satellite I left",
                "When this is off, the app always opens on the list of satellites.",
                leading = { Icon(painterResource(R.drawable.sat_device_history), contentDescription = null) },
                trailing = { Switch(checked = vm.rememberSatellite, onCheckedChange = { vm.changeRememberSatellite(it) }) },
            )
        }
        PanelCard {
            PanelRow(
                CdVectors.Cog, "Each satellite's own settings",
                "Design, glances, backlight and the switches are on the satellite's page: tap it in the list.",
                leading = { Icon(painterResource(R.drawable.sat_device_cog), contentDescription = null) },
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
    SidebarSubPage("About", onBack) {
        PanelCard {
            PanelRow(CdVectors.Info, "Church Drive Satellites", "Version ${BuildConfig.VERSION_NAME} (build ${BuildConfig.BUILD}) · ${BuildConfig.COMMIT}", leading = { Icon(painterResource(com.churchdrive.core.R.drawable.cd_logo_satellites), contentDescription = null) })
            PanelRow(
                CdVectors.SystemUpdate, "Updates",
                if (BuildConfig.PLAY) "Google Play keeps this app up to date. Tap to open its Play page" else "A test build: newer ones are on GitHub, under the satellites-latest release",
                onClick = if (BuildConfig.PLAY) ({ UpdateCheck.openPlayPage(context, context.packageName) }) else null,
            )
        }
        PanelCard {
            if (certs.isEmpty()) PanelRow(Icons.Outlined.Fingerprint, "Signing certificate (SHA-256)", "Not available", leading = { Icon(painterResource(R.drawable.mdi_shield_key), contentDescription = null) })
            certs.forEach { c ->
                PanelRow(
                    Icons.Outlined.Fingerprint, "Signing certificate (SHA-256)",
                    if (copied) "Copied" else "$c\nTap to copy. The Church Drive app needs this to share its sign-in with this app.",
                    onClick = { clipboard.setText(AnnotatedString(c)); copied = true },
                    leading = { Icon(painterResource(R.drawable.mdi_shield_key), contentDescription = null) }
                )
            }
        }
    }
}
