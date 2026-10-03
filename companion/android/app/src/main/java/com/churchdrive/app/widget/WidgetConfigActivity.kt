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
                                "ScenesWidgetReceiver" -> ScenesGlanceWidget()
                                "FanWidgetReceiver" -> FanGlanceWidget()
                                "PurifierWidgetReceiver" -> PurifierGlanceWidget()
                                "BlindsWidgetReceiver" -> BlindsGlanceWidget()
                                "CameraWidgetReceiver" -> CameraGlanceWidget()
                                "ActivityWidgetReceiver" -> ActivityGlanceWidget()
                                "TodoWidgetReceiver" -> MyTodoGlanceWidget()
                                "GaugeWidgetReceiver" -> GaugeGlanceWidget()
                                "ClusterWidgetReceiver" -> ClusterGlanceWidget()
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

    var single by remember { mutableStateOf("") }
    val many = remember { mutableStateListOf<String>().also { it.addAll(WidgetConfig.strings(existing, if (kind == "ScenesWidgetReceiver") "scenes" else "readings")) } }
    val singleKey = SINGLE_KEYS[kind]
    LaunchedEffect(singleKey) { if (singleKey != null) single = existing.optString(singleKey) }

    val title = when (kind) {
        "ScenesWidgetReceiver" -> "Scenes widget"
        "FanWidgetReceiver" -> "Fan widget"
        "PurifierWidgetReceiver" -> "Air purifier widget"
        "BlindsWidgetReceiver" -> "Blinds widget"
        "CameraWidgetReceiver" -> "Camera widget"
        "ActivityWidgetReceiver" -> "Doorbell and movement widget"
        "TodoWidgetReceiver" -> "To-do widget"
        "GaugeWidgetReceiver" -> "Gauge widget"
        "ClusterWidgetReceiver" -> "Gauge cluster widget"
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
                "ScenesWidgetReceiver" -> {
                    Text("Pick up to 8 scenes, in the colours you gave them.", style = MaterialTheme.typography.bodyMedium)
                    SceneButtons.catalogue(d.entities, d.lights).groupBy { it.group }.forEach { (group, list) ->
                        Heading(group)
                        list.forEach { b ->
                            CheckRow(b.label.substringAfter(" · "), b.id in many) {
                                if (b.id in many) many.remove(b.id) else if (many.size < 8) many.add(b.id)
                            }
                        }
                    }
                }
                "ClusterWidgetReceiver" -> {
                    Text("Pick 2 to 4 readings.", style = MaterialTheme.typography.bodyMedium)
                    (Gauges.BASIC.filter { it.first != "heating" } + Gauges.sensorChoices(d.entities)).forEach { (key, label) ->
                        CheckRow(label, key in many) { if (key in many) many.remove(key) else if (many.size < 4) many.add(key) }
                    }
                }
                in SINGLE_KEYS -> {
                    Text(SINGLE_PROMPT[kind].orEmpty(), style = MaterialTheme.typography.bodyMedium)
                    val choices = if (kind == "GaugeWidgetReceiver") Gauges.BASIC + Gauges.sensorChoices(d.entities) else singleChoices(kind, d)
                    choices.forEach { (key, label) -> RadioRow(label, single == key) { single = key } }
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
            enabled = data != null && (singleKey == null || single.isNotBlank()) && (kind != "ClusterWidgetReceiver" || many.size >= 2) && (kind != "ScenesWidgetReceiver" || many.isNotEmpty()),
            onClick = {
                val config = JSONObject()
                when (kind) {
                    "LightsWidgetReceiver" -> { WidgetConfig.put(config, "rooms", rooms.toList()); config.put("scenes", scenes) }
                    "ClimateWidgetReceiver" -> config.put("climate", climate)
                    "ScenesWidgetReceiver" -> WidgetConfig.put(config, "scenes", many.toList())
                    "ClusterWidgetReceiver" -> WidgetConfig.put(config, "readings", many.toList())
                    in SINGLE_KEYS -> singleKey?.let { config.put(it, single) }
                    "SummaryWidgetReceiver" -> WidgetConfig.put(config, "stats", stats.toList())
                    else -> WidgetConfig.put(config, "actions", actions.toList())
                }
                onSave(config)
            },
        ) { Text("Add widget") }
    }
}

/** The kinds that ask for one thing, and the name its choice is kept under. */
private val SINGLE_KEYS = mapOf(
    "FanWidgetReceiver" to "fan", "PurifierWidgetReceiver" to "fan", "BlindsWidgetReceiver" to "cover", "CameraWidgetReceiver" to "camera",
    "ActivityWidgetReceiver" to "base", "TodoWidgetReceiver" to "list", "GaugeWidgetReceiver" to "reading",
)

private val SINGLE_PROMPT = mapOf(
    "FanWidgetReceiver" to "Which fan?", "PurifierWidgetReceiver" to "Which air purifier?", "BlindsWidgetReceiver" to "Which blind (or group)?",
    "CameraWidgetReceiver" to "Which camera?", "ActivityWidgetReceiver" to "Which doorbell or camera zone?", "TodoWidgetReceiver" to "Which list?",
    "GaugeWidgetReceiver" to "Which reading? Heating also gets − and + for the target.",
)

private fun singleChoices(kind: String, d: WidgetData): List<Pair<String, String>> {
    val e = d.entities.values
    return when (kind) {
        "FanWidgetReceiver", "PurifierWidgetReceiver" -> e.filter { it.entityId.startsWith("fan.") }.sortedBy { it.friendlyName }.map { it.entityId to it.friendlyName }
        "BlindsWidgetReceiver" -> e.filter { it.entityId.startsWith("cover.") }.sortedBy { it.friendlyName }.map { it.entityId to it.friendlyName }
        "CameraWidgetReceiver" -> e.filter { it.entityId.startsWith("camera.") && it.entityId.endsWith("_live_view") }.sortedBy { it.friendlyName }.map { it.entityId to it.friendlyName }
        "ActivityWidgetReceiver" -> activityBases(d.entities).map { it to it.replace('_', ' ').replaceFirstChar { c -> c.uppercase() } }
        else -> e.filter { it.entityId.startsWith("todo.") }.sortedBy { it.friendlyName }.map { it.entityId to it.friendlyName }
    }
}
