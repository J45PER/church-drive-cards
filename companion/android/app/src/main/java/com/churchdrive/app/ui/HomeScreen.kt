package com.churchdrive.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.churchdrive.app.ha.ConnectionState
import com.churchdrive.app.ha.EntityState

/** The signed-in app: a page at a time, a large collapsing title, and the page bar along the bottom. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    connection: ConnectionState,
    alarm: EntityState?,
    onAlarm: (service: String, code: String?) -> Unit,
    onSignOut: () -> Unit,
) {
    var page by rememberSaveable { mutableStateOf(Page.Home) }
    var menuOpen by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(page.label) },
                actions = {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.AccountCircle, contentDescription = "Account")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Sign out") },
                            onClick = {
                                menuOpen = false
                                onSignOut()
                            },
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = {
            NavigationBar {
                Page.entries.forEach { p ->
                    NavigationBarItem(
                        selected = p == page,
                        onClick = { page = p },
                        icon = {
                            Icon(if (p == page) p.selectedIcon else p.icon, contentDescription = p.label)
                        },
                        label = { Text(p.label, fontSize = 11.sp, maxLines = 1) },
                    )
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            when (connection) {
                ConnectionState.Connected -> Unit
                ConnectionState.AuthFailed ->
                    Text("Sign-in failed. Check the token.", color = MaterialTheme.colorScheme.error)
                else -> Text("Connecting…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            page.sections.forEach { section ->
                SectionPanel(section.title) {
                    when (section.kind) {
                        SectionKind.Alarm -> AlarmCard(alarm, onAlarm)
                        SectionKind.NotBuilt -> NotBuiltCard()
                    }
                }
            }
        }
    }
}

/** A titled group of cards, like the section-panel-card on the dashboards. */
@Composable
fun SectionPanel(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
        content()
    }
}

@Composable
fun NotBuiltCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Text(
            "Coming soon",
            modifier = Modifier.padding(20.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun AlarmCard(alarm: EntityState?, onAlarm: (service: String, code: String?) -> Unit) {
    val state = alarm?.state
    val colors = MaterialTheme.colorScheme
    val (container, content) = when (state) {
        "triggered" -> colors.errorContainer to colors.onErrorContainer
        "disarmed" -> colors.secondaryContainer to colors.onSecondaryContainer
        null -> colors.surfaceContainer to colors.onSurface
        else -> colors.primaryContainer to colors.onPrimaryContainer
    }
    val enabled = alarm != null

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Icon(alarmIcon(state), contentDescription = null, modifier = Modifier.size(36.dp))
                Text(alarmLabel(state), style = MaterialTheme.typography.headlineSmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModeButton("Disarm", state == "disarmed", enabled, Modifier.weight(1f)) { onAlarm("alarm_disarm", null) }
                ModeButton("Home", state == "armed_home", enabled, Modifier.weight(1f)) { onAlarm("alarm_arm_home", null) }
                ModeButton("Away", state == "armed_away", enabled, Modifier.weight(1f)) { onAlarm("alarm_arm_away", null) }
                ModeButton("Night", state == "armed_night", enabled, Modifier.weight(1f)) { onAlarm("alarm_arm_night", null) }
            }
        }
    }
}

@Composable
private fun ModeButton(label: String, selected: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val padding = PaddingValues(horizontal = 4.dp)
    if (selected) {
        Button(onClick = onClick, enabled = enabled, modifier = modifier, contentPadding = padding) {
            Text(label, maxLines = 1)
        }
    } else {
        OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier, contentPadding = padding) {
            Text(label, maxLines = 1)
        }
    }
}

private fun alarmIcon(state: String?): ImageVector = when (state) {
    "disarmed" -> Icons.Filled.LockOpen
    "triggered" -> Icons.Filled.Warning
    "armed_home", "armed_away", "armed_night" -> Icons.Filled.Lock
    else -> Icons.Filled.Security
}

fun alarmLabel(state: String?): String = when (state) {
    "disarmed" -> "Disarmed"
    "armed_home" -> "Armed home"
    "armed_away" -> "Armed away"
    "armed_night" -> "Armed night"
    "arming" -> "Arming"
    "pending" -> "Entry delay"
    "triggered" -> "Triggered"
    null -> "Loading…"
    else -> state.replaceFirstChar { it.uppercase() }
}
