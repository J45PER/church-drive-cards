package com.churchdrive.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.graphics.vector.ImageVector
import com.churchdrive.app.TrustedApps
import com.churchdrive.app.UpdateCheck

// The rows and pages every app's sidebar has, taken from the phone app: an app supplies only its own words and what they do.

/** "Open Church Drive": the way to the main app from any of the other apps. */
@Composable
fun OpenChurchDriveCard(onClick: () -> Unit) {
    PanelCard {
        PanelRow(
            CdVectors.Home, "Open Church Drive", "The main app: lights, alarm, cameras and the rest",
            onClick = onClick,
            leading = { Icon(painterResource(com.churchdrive.core.R.drawable.cd_logo_church_drive), contentDescription = null) },
        )
    }
}

/**
 * Sign out. A sign-in taken from the Church Drive app ([borrowed]) is that app's to end, so here it only says so; otherwise the row signs
 * out ([onSignOut]).
 */
@Composable
fun SignOutCard(borrowed: Boolean, onSignOut: () -> Unit) {
    PanelCard {
        if (borrowed) PanelRow(CdVectors.SignOut, "Sign out", "This app uses the Church Drive app's sign-in. Sign out there and it signs out here too.")
        else PanelRow(CdVectors.SignOut, "Sign out", onClick = onSignOut)
    }
}

/** A setting that is on or off: a row with a switch at its end. */
@Composable
fun PanelSwitchRow(
    icon: ImageVector,
    text: String,
    supporting: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    leading: (@Composable () -> Unit)? = null,
) {
    PanelRow(icon, text, supporting, leading = leading, trailing = { Switch(checked = checked, onCheckedChange = onChange) })
}

/**
 * The About page, the same in every app: the app's name and version, how it is updated, and (when [showCertificate]) the key this copy is
 * signed with, to be copied. With [onCheckUpdate] the app can check for a newer build itself (then [apkUrl] is where it is downloaded from);
 * without it the row only says how updates arrive. [play] is true for the Google Play build, which Play keeps up to date. [logo] is the
 * app's own logo (a core drawable), drawn like the other rows' icons.
 */
@Composable
fun SidebarAboutPage(
    appName: String,
    versionLine: String,
    onBack: () -> Unit,
    play: Boolean,
    testBuildNote: String,
    logo: Int,
    updateAvailable: Boolean = false,
    onCheckUpdate: (((Boolean) -> Unit) -> Unit)? = null,
    apkUrl: String? = null,
    showCertificate: Boolean = true,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var status by remember { mutableStateOf<String?>(null) }
    var copied by remember { mutableStateOf(false) }
    val available = updateAvailable || status == "available"
    // The key this installed copy is signed with. On the Google Play version that is Play's own key, which the Church Drive app has to be told
    // about before it will share its sign-in (TrustedApps.CERTS), so it is shown here to be copied.
    val certs = remember { TrustedApps.ownCerts(context) }
    SidebarSubPage("About", onBack) {
        PanelCard {
            PanelRow(CdVectors.Info, appName, versionLine, leading = { Icon(painterResource(logo), contentDescription = null) })
            if (onCheckUpdate != null) {
                PanelRow(
                    CdVectors.SystemUpdate, "Check for updates",
                    when {
                        play -> "Google Play keeps this app up to date. Tap to open its Play page"
                        status == "checking" -> "Checking…"
                        available -> "A newer version is ready"
                        status == "current" -> "You have the newest version"
                        else -> "Tap to check"
                    },
                    onClick = {
                        if (play) UpdateCheck.openPlayPage(context, context.packageName)
                        else {
                            status = "checking"
                            onCheckUpdate { newer -> status = if (newer) "available" else "current" }
                        }
                    },
                )
            } else {
                PanelRow(
                    CdVectors.SystemUpdate, "Updates",
                    if (play) "Google Play keeps this app up to date. Tap to open its Play page" else testBuildNote,
                    onClick = if (play) ({ UpdateCheck.openPlayPage(context, context.packageName) }) else null,
                )
            }
        }
        if (available && apkUrl != null) {
            Button(
                onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl))) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Download the update") }
        }
        if (showCertificate) {
            PanelCard {
                if (certs.isEmpty()) PanelRow(CdVectors.ShieldOutline, "Signing certificate (SHA-256)", "Not available")
                certs.forEach { c ->
                    PanelRow(
                        CdVectors.ShieldOutline, "Signing certificate (SHA-256)",
                        if (copied) "Copied" else "$c\nTap to copy. The Church Drive app needs this to share its sign-in with this app.",
                        onClick = { clipboard.setText(AnnotatedString(c)); copied = true },
                    )
                }
            }
        }
    }
}
