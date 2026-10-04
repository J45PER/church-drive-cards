package com.churchdrive.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.churchdrive.app.ha.CallService
import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ha.data
import org.json.JSONObject
import kotlin.math.roundToInt

/** A thermostat from the dashboard's `custom:climate-card`: temperature, target, humidity, outside and its shortcuts. */
@Composable
fun ClimateRoomCard(config: JSONObject, entities: Map<String, EntityState>, call: CallService) {
    val id = config.optString("entity")
    ClimateCard(
        entities[id],
        call,
        id,
        humidity = config.optString("humidity_entity").takeIf { it.isNotBlank() }?.let { entities[it] },
        outdoor = config.optString("outdoor_entity").takeIf { it.isNotBlank() }?.let { entities[it]?.num("temperature") },
        quick = quickSettings(config),
        below = {
            // Temperature and humidity over the last day (each is a switch on the dashboard card), sharing one graph.
            if (config.optBoolean("show_temperature_history") || config.optBoolean("show_humidity_history")) {
                val tone = toneColors(climateTone(entities[id]))
                ClimateGraph(
                    entities[id], id,
                    config.optString("humidity_entity").takeIf { it.isNotBlank() }?.let { entities[it] },
                    config, tone.onContainer, tone.accent,
                )
            }
        },
    )
}

/** A row of choices that scrolls sideways when there are many. */
@Composable
fun OptionRow(options: List<String>, selected: String?, tone: ToneColors, enabled: Boolean = true, onPick: (String) -> Unit) {
    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o -> ChoiceButton(presetLabel(o), selected == o, tone, tone.onContainer, Modifier, enabled) { onPick(o) } }
    }
}

/** The dashboard's `custom:climate-zone-card`: a floor's rooms, each with its temperature (coloured by comfort) and humidity. */
@Composable
fun ClimateZoneCard(config: JSONObject, entities: Map<String, EntityState>) {
    val neutral = toneColors(Tone.Grey)
    val rooms = config.optJSONArray("rooms")
    EntityCard(neutral.container, neutral.onContainer) {
        config.optString("title").takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
        for (i in 0 until (rooms?.length() ?: 0)) {
            val r = rooms?.optJSONObject(i) ?: continue
            val t = entities[r.optString("temperature")]?.state?.toDoubleOrNull()
            val h = r.optString("humidity").takeIf { it.isNotBlank() }?.let { entities[it]?.state?.toDoubleOrNull() }
            val tone = toneColors(comfortTone(t, r.optString("type")))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ToneIconName(r.optString("icon"), Icons.Filled.Info, tone)
                Column(modifier = Modifier.weight(1f)) {
                    Text(r.optString("name"), style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    r.optString("note").takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(if (t == null) "–" else "${temp(t)}°", style = MaterialTheme.typography.titleLarge, color = tone.accent)
                    if (h != null) Text("${h.roundToInt()}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** The dashboard's `custom:fan-card`: on and off, and the fan's speeds or modes. */
@Composable
fun FanCard(config: JSONObject, entities: Map<String, EntityState>, call: CallService) {
    val id = config.optString("entity")
    val fan = entities[id]
    val on = fan?.state == "on"
    val tone = toneColors(if (on) Tone.Teal else Tone.Grey)
    val name = config.optString("name").ifBlank { fan?.friendlyName ?: id }
    val room = config.optString("temperature_entity").takeIf { it.isNotBlank() }?.let { entities[it]?.state?.toDoubleOrNull() }
    val presets = fan?.list("preset_modes").orEmpty()
    val preset = fan?.str("preset_mode")
    val enabled = fan?.available == true
    EntityCard(tone.container, tone.onContainer) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ToneIconName(config.optString("icon").ifBlank { "mdi:fan" }, Icons.Filled.Info, tone)
            Column(modifier = Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(if (!on) "Off" else preset?.let { presetLabel(it) } ?: "On", style = MaterialTheme.typography.bodyMedium)
            }
            if (room != null) Text("${temp(room)}°", style = MaterialTheme.typography.titleLarge)
        }
        val speeds = fanSpeeds(fan)
        val percentage = fan?.num("percentage") ?: 0.0
        val current = if (on) speeds.firstOrNull { sp -> if (sp.preset != null) sp.preset == preset else Math.abs(percentage - (sp.percentage ?: 0)) < 5 } else null
        val others = presets.filter { speedNumber(it) == null }
        val canOscillate = fan?.str("oscillating") != null || ((fan?.num("supported_features")?.toInt() ?: 0) and 2) == 2
        // Row one: Off and the speeds. Row two: the other modes and Oscillate. As on the dashboard.
        if (config.optBoolean("show_speeds", true)) {
            TileRow(
                buildList {
                    add(TileItem(IconMap.of("fan", "off", "mdi:power"), "Off", !on) { call("fan", "turn_off", id, data()) })
                    speeds.forEachIndexed { i, sp ->
                        add(TileItem(fanSpeedIcon(i, speeds.size), "${sp.n}", current === sp) {
                            if (sp.preset != null) call("fan", "set_preset_mode", id, data("preset_mode" to sp.preset))
                            else call("fan", "set_percentage", id, data("percentage" to (sp.percentage ?: 0)))
                        })
                    }
                },
                tone, tone.onContainer, enabled, perRow = 4,
            )
        }
        val second = buildList {
            if (config.optBoolean("show_presets", true)) {
                others.forEach { p -> add(TileItem(fanPresetIcon(p), presetLabel(p), on && preset == p) { call("fan", "set_preset_mode", id, data("preset_mode" to p)) }) }
            }
            if (config.optBoolean("show_oscillate", true) && canOscillate) {
                val oscillating = fan?.attributes?.optBoolean("oscillating", false) == true
                add(TileItem(IconMap.of("fan", "oscillate", "mdi:arrow-oscillating"), "Oscillate", on && oscillating) {
                    call("fan", "oscillate", id, data("oscillating" to !oscillating))
                })
            }
        }
        TileRow(second, tone, tone.onContainer, enabled, perRow = 4)
    }
}

/** The dashboard's `custom:air-purifier-card`: the air quality (coloured Good to Very poor), modes and filter life. */
@Composable
fun AirPurifierCard(config: JSONObject, entities: Map<String, EntityState>, call: CallService) {
    val id = config.optString("entity")
    val fan = entities[id]
    val on = fan?.state == "on"
    val pm = config.optString("pm25_entity").takeIf { it.isNotBlank() }?.let { entities[it]?.state?.toDoubleOrNull() }
    val allergen = config.optString("allergen_entity").takeIf { it.isNotBlank() }?.let { entities[it]?.state?.toDoubleOrNull() }
    val tone = toneColors(pmTone(pm))
    val name = config.optString("name").ifBlank { fan?.friendlyName ?: id }
    val presets = fan?.list("preset_modes").orEmpty()
    val preset = fan?.str("preset_mode")
    val enabled = fan?.available == true
    val filters = config.optJSONArray("filters")
    EntityCard(tone.container, tone.onContainer) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ToneIconName("mdi:air-purifier", Icons.Filled.Info, tone)
            Column(modifier = Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(if (!on) "Off" else preset?.let { presetLabel(it) } ?: "On", style = MaterialTheme.typography.bodyMedium)
            }
            if (pm != null) {
                Column(horizontalAlignment = Alignment.End) {
                    Text("${pm.roundToInt()}", style = MaterialTheme.typography.headlineMedium, color = tone.accent)
                    Text("PM2.5 · ${pmWord(pm)}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (config.optBoolean("show_graph", true) && config.optString("pm25_entity").isNotBlank()) {
            SensorGraph(config.optString("pm25_entity"), 24, " µg/m³", tone.accent, tone.onContainer, config.optBoolean("smooth_graphs", true))
        }
        if (allergen != null) Text("Allergen index ${allergen.roundToInt()}", style = MaterialTheme.typography.bodyMedium)
        TileRow(
            buildList {
                add(TileItem(IconMap.of("purifier", "off", "mdi:power"), "Off", !on) { call("fan", "turn_off", id, data()) })
                if (presets.isEmpty()) add(TileItem("mdi:air-purifier", "On", on) { call("fan", "turn_on", id, data()) })
                presets.forEach { p ->
                    add(TileItem(purifierModeIcon(p), presetLabel(p), on && preset == p) { call("fan", "set_preset_mode", id, data("preset_mode" to p)) })
                }
            },
            tone, tone.onContainer, enabled, perRow = 4, column = true,
        )
        for (i in 0 until (filters?.length() ?: 0)) {
            val f = filters?.optJSONObject(i) ?: continue
            val left = entities[f.optString("entity")]?.state?.toDoubleOrNull() ?: continue
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row {
                    Text(f.optString("name").ifBlank { "Filter" }, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text("${left.roundToInt()}% left", style = MaterialTheme.typography.bodyMedium)
                }
                LinearProgressIndicator(
                    progress = { (left / 100.0).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = if (left < 10) toneColors(Tone.Red).accent else tone.accent,
                )
            }
        }
    }
}

/** The dashboard's `custom:co-alarm-card`: the carbon monoxide reading, the alarm, battery and the Test and Mute buttons. */
@Composable
fun CoAlarmCard(config: JSONObject, entities: Map<String, EntityState>, call: CallService) {
    fun entity(key: String) = config.optString(key).takeIf { it.isNotBlank() }?.let { entities[it] }
    val reading = entity("entity")?.state?.toDoubleOrNull()
    val alarm = entity("alarm_entity")?.state == "on"
    val tone = toneColors(if (alarm) Tone.Red else Tone.Green)
    val battery = entity("battery_entity")?.state?.toDoubleOrNull()
    val status = entity("status_entity")?.state?.takeIf { it != "unknown" && it != "unavailable" }
    val report = entity("report_entity")?.state
    val test = config.optString("test_entity").takeIf { it.isNotBlank() }
    val mute = config.optString("mute_entity").takeIf { it.isNotBlank() }
    EntityCard(tone.container, tone.onContainer) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ToneIconName("mdi:molecule-co", Icons.Filled.Info, tone)
            Column(modifier = Modifier.weight(1f)) {
                Text("Carbon monoxide", style = MaterialTheme.typography.titleMedium)
                Text(if (alarm) "ALARM" else "All clear", style = MaterialTheme.typography.bodyMedium, color = tone.accent)
            }
            Text(if (reading == null) "–" else "${reading.roundToInt()} ppm", style = MaterialTheme.typography.headlineMedium)
        }
        val facts = listOfNotNull(
            status?.let { presetLabel(it) },
            battery?.let { "battery ${it.roundToInt()}%" },
            shortDayOf(report)?.let { "checked in $it" },
        ).joinToString(" · ")
        if (facts.isNotEmpty()) Text(facts, style = MaterialTheme.typography.bodyMedium)
        if (test != null || mute != null) {
            TileRow(
                buildList {
                    if (test != null) add(TileItem(IconMap.of("co", "test", "mdi:bell-ring"), "Test", false) { call("button", "press", test, data()) })
                    if (mute != null) add(TileItem(IconMap.of("co", "mute", "mdi:volume-off"), "Mute", alarm) { call("button", "press", mute, data()) })
                },
                tone, tone.onContainer,
            )
        }
    }
}

/** The dashboard's `custom:cover-card`: a blind or shutter with its position and Open, Stop and Close. */
@Composable
fun CoverCard(config: JSONObject, entities: Map<String, EntityState>, call: CallService) {
    val id = config.optString("entity")
    val cover = entities[id]
    val position = cover?.num("current_position")
    val tone = toneColors(if (cover?.state == "closed" || cover?.available != true) Tone.Grey else Tone.Blue)
    var slider by remember(position) { mutableStateOf((position ?: 0.0).toFloat()) }
    val name = config.optString("name").ifBlank { cover?.friendlyName ?: id }
    val state = cover?.state
    val word = when (state) {
        null -> "Loading…"
        "open", "closed" -> presetLabel(state) + (position?.let { " · ${it.roundToInt()}%" } ?: "")
        else -> presetLabel(state)
    }
    val enabled = cover?.available == true
    EntityCard(tone.container, tone.onContainer) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ToneIconName(config.optString("icon").ifBlank { "mdi:blinds" }, Icons.Filled.Info, tone)
            Column(modifier = Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(listOf(config.optString("subtitle"), word).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (position != null) {
            Slider(
                value = slider,
                onValueChange = { slider = it },
                onValueChangeFinished = { call("cover", "set_cover_position", id, data("position" to slider.roundToInt())) },
                valueRange = 0f..100f,
                enabled = enabled,
            )
        }
        TileRow(
            listOf(
                TileItem(IconMap.of("cover", "open", "mdi:arrow-up"), "Open", cover?.state == "open") { call("cover", "open_cover", id, data()) },
                TileItem(IconMap.of("cover", "stop", "mdi:stop"), "Stop", false) { call("cover", "stop_cover", id, data()) },
                TileItem(IconMap.of("cover", "close", "mdi:arrow-down"), "Close", cover?.state == "closed") { call("cover", "close_cover", id, data()) },
            ),
            tone, tone.onContainer, enabled,
        )
    }
}

/** The Climate page's weather header: now, and the next hours of the forecast. */
@Composable
fun ForecastHeader(config: JSONObject, entities: Map<String, EntityState>) {
    val api = LocalHaApi.current
    val entityId = config.optString("forecast_entity")
    val type = config.optString("forecast_type").ifBlank { "hourly" }
    var points by remember(entityId) { mutableStateOf<List<ForecastPoint>>(emptyList()) }
    DisposableEffect(entityId, type) {
        var subscription = -1
        if (api != null && entityId.isNotBlank()) {
            subscription = api.subscribe("weather/subscribe_forecast", data("entity_id" to entityId, "forecast_type" to type)) { event ->
                points = parseForecast(event)
            }
        }
        onDispose { if (subscription >= 0) api?.close(subscription) }
    }
    val weather = entities[entityId] ?: return
    val tone = toneColors(Tone.Blue)
    val upcoming = points.filter { it.timeMs >= System.currentTimeMillis() - 30 * 60_000L }.take(12)
    EntityCard(tone.container, tone.onContainer) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ToneIconName(weatherIcon(weather.state), Icons.Filled.Info, tone, 48)
            Column(modifier = Modifier.weight(1f)) {
                Text(weather.num("temperature")?.let { "${it.roundToInt()}°" } ?: "–", style = MaterialTheme.typography.headlineMedium)
                Text(presetLabel(weather.state.replace('-', ' ')), style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (upcoming.isNotEmpty()) {
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                upcoming.forEach { p ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(44.dp)) {
                        Text(eventClock(p.timeMs).take(2), fontSize = 12.sp)
                        HaIcon(weatherIcon(p.condition), Icons.Filled.Info, tone.accent, 24.dp, Modifier.padding(vertical = 4.dp))
                        Text(p.temperature?.let { "${it.roundToInt()}°" } ?: "–", fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
