package com.churchdrive.admin

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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.churchdrive.app.TrustedApps
import com.churchdrive.app.UpdateCheck
import com.churchdrive.app.ui.CdVectors
import com.churchdrive.app.ui.LogoKind
import com.churchdrive.app.ui.LogoMorph
import com.churchdrive.app.ui.DeviceHealthTab

/**
 * The account panel, laid out like the main Church Drive app's and the To-Do and Satellites apps': it slides in from the right over the app,
 * from the person's initial or a swipe left. It holds the way to the Church Drive app, About and Sign out; About slides in over it.
 * Back goes back one step.
 */
@Composable
fun AdminPanel(panel: String?, onPanel: (String?) -> Unit, vm: AdminModel, acrylic: HazeState? = null, bottomInset: Dp = 0.dp) {
    // Device health opens from Home's card, not from the sidebar's list, so Back from it shuts the panel; every other page goes back to the list.
    val back: (String?) -> String? = { if (it == "health") null else sidebarParent(it) }
    SidebarHost(panel, onPanel, acrylic, bottomInset, back = back) { which ->
        when (which) {
            "about" -> AboutPage { onPanel(back(which)) }
            "health" -> Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                // Device health scrolls itself (its devices open pages in place), so it is not put in the scrolling page column.
                Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { PanelTitle("Device health", { onPanel(back(which)) }) }
                Box(Modifier.weight(1f).fillMaxWidth()) { DeviceHealthTab(vm.entities, vm.registry, vm.areaNames) }
            }
            "colours" -> SidebarSubPage("Colour palettes", { onPanel(back(which)) }) {
                ColoursPage()
            }
            "watch", "notifications", "cameras", "people" -> SidebarSubPage(when (which) { "watch" -> "Watch"; "cameras" -> "Camera links"; "people" -> "People"; else -> "Notifications" }, { onPanel(back(which)) }) {
                val section = ManagerSections.forPage(vm.managerSections, which)
                if (section == null) com.churchdrive.app.ui.LoadingNote("Loading this page from Home Assistant…")
                else ManagerSectionContent(section, vm.entities, vm.registry, vm.call)
            }
            else -> AccountPage(vm, onClose = { onPanel(null) }, onOpen = onPanel)
        }
    }
}

@Composable
private fun AccountPage(vm: AdminModel, onClose: () -> Unit, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    SidebarListPage(vm.personName, if (vm.borrowed) "Signed in through Church Drive" else "Clive Admin", onClose) {
        OpenChurchDriveCard { ChurchDriveLink.open(context) }
        PanelCard {
            PanelRow(
                CdVectors.BatteryCharging, "Device health", "Batteries, filters and devices that stopped answering",
                onClick = { onOpen("health") },
            )
            if (ManagerSections.forPage(vm.managerSections, "notifications") != null) PanelRow(
                CdVectors.Info, "Notifications", "Who gets each alert and house job, and when a house job is made",
                onClick = { onOpen("notifications") }, leading = { com.churchdrive.app.ui.HaIcon("mdi:bell-ring-outline", CdVectors.Info, MaterialTheme.colorScheme.onSurfaceVariant, 24.dp) },
            )
            if (ManagerSections.forPage(vm.managerSections, "people") != null) PanelRow(
                CdVectors.Info, "People", "Each person's phones, places, cars and the alerts they get",
                onClick = { onOpen("people") }, leading = { com.churchdrive.app.ui.HaIcon("mdi:account-cog", CdVectors.Info, MaterialTheme.colorScheme.onSurfaceVariant, 24.dp) },
            )
            if (ManagerSections.forPage(vm.managerSections, "cameras") != null) PanelRow(
                CdVectors.Info, "Camera links", "Where each camera's links go, by alarm mode",
                onClick = { onOpen("cameras") }, leading = { com.churchdrive.app.ui.HaIcon("mdi:link-variant", CdVectors.Info, MaterialTheme.colorScheme.onSurfaceVariant, 24.dp) },
            )
            if (ManagerSections.forPage(vm.managerSections, "watch") != null) PanelRow(
                CdVectors.Info, "Watch", "Pages and items on the watch",
                onClick = { onOpen("watch") }, leading = { com.churchdrive.app.ui.HaIcon("mdi:watch", CdVectors.Info, MaterialTheme.colorScheme.onSurfaceVariant, 24.dp) },
            )
        }
        PanelCard {
            PanelRow(
                CdVectors.Info, "Colour palettes", "Every palette as the house has it. View only: change them on the website",
                onClick = { onOpen("colours") }, leading = { Icon(painterResource(R.drawable.mdi_palette), contentDescription = null) },
            )
        }
        PanelCard { PanelRow(CdVectors.Info, "About", onClick = { onOpen("about") }) }
        SignOutCard(vm.borrowed) { onClose(); vm.signOut() }
    }
}

@Composable
private fun AboutPage(onBack: () -> Unit) {
    SidebarAboutPage(
        appName = "Clive Admin",
        versionLine = "Version ${BuildConfig.VERSION_NAME} (build ${BuildConfig.BUILD}) · ${BuildConfig.COMMIT}",
        onBack = onBack,
        play = BuildConfig.PLAY,
        testBuildNote = "A test build: newer ones are on GitHub, under the admin-latest release",
        logo = com.churchdrive.core.R.drawable.cd_logo_admin,
    )
}
