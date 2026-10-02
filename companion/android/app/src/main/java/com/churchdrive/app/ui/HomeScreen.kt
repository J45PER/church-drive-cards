package com.churchdrive.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.churchdrive.app.ha.ConnectionState
import com.churchdrive.app.ha.EntityState

/** The signed-in app: a page at a time, with the page bar along the bottom. */
@Composable
fun HomeScreen(
    connection: ConnectionState,
    alarm: EntityState?,
    onAlarm: (service: String, code: String?) -> Unit,
    onSignOut: () -> Unit,
) {
    var page by rememberSaveable { mutableStateOf(Page.Home) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Page.entries.forEach { p ->
                    NavigationBarItem(
                        selected = p == page,
                        onClick = { page = p },
                        icon = { Text(p.icon, fontSize = 20.sp) },
                        label = { Text(p.label, fontSize = 10.sp, maxLines = 1) },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(page.label, style = MaterialTheme.typography.headlineLarge)
            when (connection) {
                ConnectionState.Connected -> Unit
                ConnectionState.AuthFailed ->
                    Text("Sign-in failed. Check the token.", color = MaterialTheme.colorScheme.error)
                else -> Text("Connecting…")
            }

            page.sections.forEach { section ->
                SectionPanel(section.title) {
                    when (section.kind) {
                        SectionKind.Alarm -> AlarmCard(alarm, onAlarm)
                        SectionKind.NotBuilt -> NotBuiltCard()
                    }
                }
            }

            if (page == Page.Home) {
                TextButton(onClick = onSignOut) { Text("Sign out") }
            }
        }
    }
}

/** A titled group of cards, like the section-panel-card on the dashboards. */
@Composable
fun SectionPanel(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

@Composable
fun NotBuiltCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Text(
            "Coming soon",
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun AlarmCard(alarm: EntityState?, onAlarm: (service: String, code: String?) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(alarmLabel(alarm?.state), style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onAlarm("alarm_disarm", null) }, enabled = alarm != null) { Text("Disarm") }
                OutlinedButton(onClick = { onAlarm("alarm_arm_home", null) }, enabled = alarm != null) { Text("Home") }
                OutlinedButton(onClick = { onAlarm("alarm_arm_away", null) }, enabled = alarm != null) { Text("Away") }
                OutlinedButton(onClick = { onAlarm("alarm_arm_night", null) }, enabled = alarm != null) { Text("Night") }
            }
        }
    }
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
