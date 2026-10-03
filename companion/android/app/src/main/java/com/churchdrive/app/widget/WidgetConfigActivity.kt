package com.churchdrive.app.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import com.churchdrive.app.ui.CLIMATE_ENTITY
import com.churchdrive.app.ui.ChurchDriveTheme
import com.churchdrive.app.ui.allRoomsByFloor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Asked when a widget that needs choices is added (and again when the person re-opens its settings): which rooms a Lights
 * widget is for, which thermostat, which stats, which shortcuts.
 */
class WidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val id = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Backing out leaves the widget unadded.
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val kind = AppWidgetManager.getInstance(this).getAppWidgetInfo(id)?.provider?.className.orEmpty().substringAfterLast('.')
        val existing = WidgetConfig.get(this, id)
        val context: Context = this
        setContent {
            ChurchDriveTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    ConfigScreen(kind, existing) { config ->
                        WidgetConfig.save(context, id, config)
                        CoroutineScope(Dispatchers.Main).launch {
                            val widget: GlanceAppWidget? = when (kind) {
                                "LightsWidgetReceiver" -> LightsGlanceWidget()
                                "ClimateWidgetReceiver" -> ClimateGlanceWidget()
                                "SummaryWidgetReceiver" -> SummaryGlanceWidget()
                                "ShortcutsWidgetReceiver" -> ShortcutsGlanceWidget()
                                else -> null
                            }
                            runCatching { widget?.update(context, GlanceAppWidgetManager(context).getGlanceIdBy(id)) }
                            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
                            finish()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckRow(text: String, checked: Boolean, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onToggle), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, onCheckedChange = { onToggle() })
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun RadioRow(text: String, selected: Boolean, onPick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onPick), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, onClick = onPick)
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun Heading(text: String) = Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 12.dp))

@Composable
private fun ConfigScreen(kind: String, existing: JSONObject, onSave: (JSONObject) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var data by remember { mutableStateOf<WidgetData?>(null) }
    LaunchedEffect(Unit) { data = WidgetSource.load(context) }
    val rooms = remember { mutableStateListOf<String>().also { it.addAll(WidgetConfig.strings(existing, "rooms")) } }
    var scenes by remember { mutableStateOf(existing.optBoolean("scenes", true)) }
    var climate by remember { mutableStateOf(existing.optString("climate").ifBlank { CLIMATE_ENTITY }) }
    val stats = remember { mutableStateListOf<String>().also { it.addAll(WidgetConfig.strings(existing, "stats").ifEmpty { Stats.DEFAULT }) } }
    val actions = remember { mutableStateListOf<String>().also { it.addAll(WidgetConfig.strings(existing, "actions").ifEmpty { Shortcuts.DEFAULT }) } }

    val title = when (kind) {
        "LightsWidgetReceiver" -> "Lights widget"
        "ClimateWidgetReceiver" -> "Thermostat widget"
        "SummaryWidgetReceiver" -> "Summary widget"
        else -> "Shortcuts widget"
    }
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            val d = data
            if (d == null) {
                Text("Loading the house…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else when (kind) {
                "LightsWidget", "LightsWidgetReceiver" -> {
                    Text("Which lights is this for? One room shows its scenes or brightness; several show a tile for each.", style = MaterialTheme.typography.bodyMedium)
                    allRoomsByFloor(d.lights).forEach { (floor, list) ->
                        Heading(floor)
                        list.forEach { room ->
                            CheckRow(WidgetModel.roomName(room, d.areaNames, d.entities), room.head in rooms) {
                                if (room.head in rooms) rooms.remove(room.head) else rooms.add(room.head)
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Show scenes (for one room)", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        Switch(scenes, onCheckedChange = { scenes = it })
                    }
                }
                "ClimateWidgetReceiver" -> {
                    Text("Which thermostat?", style = MaterialTheme.typography.bodyMedium)
                    d.entities.values.filter { it.entityId.startsWith("climate.") }.sortedBy { it.friendlyName }.forEach {
                        RadioRow(it.friendlyName, climate == it.entityId) { climate = it.entityId }
                    }
                }
                "SummaryWidgetReceiver" -> {
                    Text("Pick up to 4 readings.", style = MaterialTheme.typography.bodyMedium)
                    Stats.CATALOGUE.forEach { (key, label) ->
                        CheckRow(label, key in stats) {
                            if (key in stats) stats.remove(key) else if (stats.size < 4) stats.add(key)
                        }
                    }
                }
                else -> {
                    Text("Pick up to 8 buttons.", style = MaterialTheme.typography.bodyMedium)
                    Shortcuts.catalogue(d.entities, d.lights).groupBy { it.group }.forEach { (group, list) ->
                        Heading(group)
                        list.forEach { s ->
                            CheckRow(s.label, s.id in actions) {
                                if (s.id in actions) actions.remove(s.id) else if (actions.size < 8) actions.add(s.id)
                            }
                        }
                    }
                }
            }
        }
        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = data != null,
            onClick = {
                val config = JSONObject()
                when (kind) {
                    "LightsWidgetReceiver" -> { WidgetConfig.put(config, "rooms", rooms.toList()); config.put("scenes", scenes) }
                    "ClimateWidgetReceiver" -> config.put("climate", climate)
                    "SummaryWidgetReceiver" -> WidgetConfig.put(config, "stats", stats.toList())
                    else -> WidgetConfig.put(config, "actions", actions.toList())
                }
                onSave(config)
            },
        ) { Text("Add widget") }
    }
}
