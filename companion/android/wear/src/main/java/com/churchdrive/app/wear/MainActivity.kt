package com.churchdrive.app.wear

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.Text
import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ha.HaClient
import com.churchdrive.app.ha.data
import com.churchdrive.app.ui.ask
import org.json.JSONArray
import org.json.JSONObject

// The same entities the phone app's own cards use (ALARM_ENTITY and VACUUM_ENTITY in ui/Cards.kt).
private const val ALARM = "alarm_control_panel.church_drive_alarm"
private const val VACUUM = "vacuum.gregg"

private enum class Page { Home, Alarm, Scenes, Vacuum }

/** Key actions: Clive and the to-do list, then the alarm, scenes, all lights off and the vacuum. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Signed { connected -> Actions(connected) } }
    }
}

@Composable
private fun Actions(connected: Boolean) {
    val context = LocalContext.current
    var page by remember { mutableStateOf(Page.Home) }
    var states by remember { mutableStateOf<Map<String, EntityState>>(emptyMap()) }
    // What the house is doing now (the alarm's mode, the scenes there are), read when the line comes up and each time the page changes.
    LaunchedEffect(connected, page) {
        if (!connected) return@LaunchedEffect
        val all = WatchHa.api.ask("get_states", JSONObject()) as? JSONArray ?: return@LaunchedEffect
        states = HaClient.parseStates(all)
    }
    BackHandler(enabled = page != Page.Home) { page = Page.Home }

    fun call(domain: String, service: String, entity: String, done: String) {
        if (!connected) {
            toast(context, "Can't reach the house")
            return
        }
        WatchHa.client.callService(domain, service, entity, data())
        toast(context, done)
    }

    ScalingLazyColumn(modifier = Modifier.fillMaxWidth()) {
        when (page) {
            Page.Home -> {
                item { ListHeader { Text("Church Drive") } }
                if (!connected) item { Text("Connecting…", textAlign = TextAlign.Center) }
                row("Clive") { context.startActivity(Intent(context, JarvisActivity::class.java)) }
                row("To-Do") { context.startActivity(Intent(context, TodoActivity::class.java)) }
                row("Alarm", alarmWords(states[ALARM]?.state)) { page = Page.Alarm }
                row("Scenes") { page = Page.Scenes }
                row("All lights off") { call("light", "turn_off", "all", "Lights off") }
                row("Vacuum", states[VACUUM]?.state?.replace('_', ' ')) { page = Page.Vacuum }
            }
            Page.Alarm -> {
                item { ListHeader { Text("Alarm") } }
                row("Home") { call("alarm_control_panel", "alarm_arm_home", ALARM, "Arming home") }
                row("Away") { call("alarm_control_panel", "alarm_arm_away", ALARM, "Arming away") }
                row("Night") { call("alarm_control_panel", "alarm_arm_night", ALARM, "Arming night") }
                row("Disarm") { call("alarm_control_panel", "alarm_disarm", ALARM, "Disarming") }
            }
            Page.Scenes -> {
                item { ListHeader { Text("Scenes") } }
                val scenes = states.values.filter { it.entityId.startsWith("scene.") && it.available }.sortedBy { it.friendlyName }
                if (scenes.isEmpty()) item { Text(if (connected) "No scenes" else "Connecting…", textAlign = TextAlign.Center) }
                for (scene in scenes) row(scene.friendlyName) { call("scene", "turn_on", scene.entityId, scene.friendlyName) }
            }
            Page.Vacuum -> {
                item { ListHeader { Text("Vacuum") } }
                row("Start or resume") { call("vacuum", "start", VACUUM, "Starting") }
                row("Pause") { call("vacuum", "pause", VACUUM, "Pausing") }
                row("Dock") { call("vacuum", "return_to_base", VACUUM, "Sending home") }
            }
        }
    }
}

/** The alarm's mode in words for under the "Alarm" button, or null when it isn't known. */
private fun alarmWords(state: String?): String? = when (state) {
    "disarmed" -> "Disarmed"
    "armed_home" -> "Armed home"
    "armed_away" -> "Armed away"
    "armed_night" -> "Armed night"
    "arming" -> "Arming"
    "pending" -> "Pending"
    "triggered" -> "Triggered"
    null -> null
    else -> state.replace('_', ' ').replaceFirstChar { it.uppercase() }
}
