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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.churchdrive.app.ha.ConnectionState
import com.churchdrive.app.ha.EntityState

@Composable
fun HomeScreen(
    connection: ConnectionState,
    alarm: EntityState?,
    onAlarm: (service: String, code: String?) -> Unit,
    onSignOut: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Home", style = MaterialTheme.typography.headlineLarge)
        when (connection) {
            ConnectionState.Connected -> Unit
            ConnectionState.AuthFailed -> Text("Sign-in failed. Check the token.", color = MaterialTheme.colorScheme.error)
            else -> Text("Connecting…")
        }

        SectionPanel("Security") { AlarmCard(alarm, onAlarm) }

        TextButton(onClick = onSignOut) { Text("Sign out") }
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
