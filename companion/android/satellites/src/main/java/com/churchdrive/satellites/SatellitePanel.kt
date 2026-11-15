package com.churchdrive.satellites

import com.churchdrive.app.ui.PanelSwitchRow
import com.churchdrive.app.ui.SidebarAboutPage
import com.churchdrive.app.ui.SignOutCard
import com.churchdrive.app.ui.OpenChurchDriveCard
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
        OpenChurchDriveCard { ChurchDriveLink.open(context) }
        PanelCard {
            PanelRow(CdVectors.Cog, "Settings", onClick = { onOpen("settings") }, leading = { Icon(painterResource(com.churchdrive.core.R.drawable.cd_cog_outline), contentDescription = null) })
            PanelRow(CdVectors.Info, "About", onClick = { onOpen("about") })
        }
        SignOutCard(vm.borrowed) { onClose(); vm.signOut() }
    }
}

@Composable
private fun SettingsPage(vm: SatelliteModel, onBack: () -> Unit) {
    SidebarSubPage("Settings", onBack) {
        PanelCard {
            PanelSwitchRow(
                CdVectors.Eye, "Open on the satellite I left",
                "When this is off, the app always opens on the list of satellites.",
                checked = vm.rememberSatellite, onChange = { vm.changeRememberSatellite(it) },
                leading = { Icon(painterResource(R.drawable.sat_device_history), contentDescription = null) },
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
    SidebarAboutPage(
        appName = "Church Drive Satellites",
        versionLine = "Version ${BuildConfig.VERSION_NAME} (build ${BuildConfig.BUILD}) · ${BuildConfig.COMMIT}",
        onBack = onBack,
        play = BuildConfig.PLAY,
        testBuildNote = "A test build: newer ones are on GitHub, under the satellites-latest release",
        logo = com.churchdrive.core.R.drawable.cd_logo_satellites,
    )
}
