package com.churchdrive.app.ui

import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
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
fun ClimateRoomCard(cardConfig: JSONObject, entities: Map<String, EntityState>, call: CallService) {
    val id = cardConfig.optString("entity")
    // The graph's band follows the live comfortable range (the assistant's numbers for this thermostat), not just the room type.
    val config = withLiveRange(cardConfig, entities, zoneControl(entities, id))
    ClimateCard(
        entities[id],
        call,
        id,
        humidity = config.optString("humidity_entity").takeIf { it.isNotBlank() }?.let { entities[it] },
        outdoor = config.optString("outdoor_entity").takeIf { it.isNotBlank() }?.let { entities[it]?.num("temperature") },
        quick = quickSettings(config),
        zone = zoneControl(entities, id),
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
fun ClimateZoneCard(config: JSONObject, entities: Map<String, EntityState>, call: CallService) {
    val neutral = toneColors(Tone.Grey)
    // A room that has its own range numbers (`low_entity` and `high_entity`) opens a sheet to change them.
    var editing by remember { mutableStateOf<String?>(null) }
    val rooms = config.optJSONArray("rooms")
    // The arrow in column 8 opens the floor out: each room's temperature (and humidity) over the last day under it. Folded by default.
    val title = config.optString("title").ifBlank { "Rooms" }
    val fold = rememberFold("rooms|$title", true)
    val open = !fold.value
    val count = rooms?.length() ?: 0
    val pitch = if (open) 4f else 1.5f
    val ink = neutral.onContainer
    GridCard(neutral.container, ink, 1f + pitch * count, title, fold = fold) {
        for (i in 0 until count) {
            val r = rooms?.optJSONObject(i) ?: continue
            val y = 1f + pitch * i
            val t = entities[r.optString("temperature")]?.state?.toDoubleOrNull()
            val h = r.optString("humidity").takeIf { it.isNotBlank() }?.let { entities[it]?.state?.toDoubleOrNull() }
            val lowHelper = r.optString("low_entity").takeIf { it.isNotBlank() }?.let { entities[it] }?.takeIf { it.available }
            val highHelper = r.optString("high_entity").takeIf { it.isNotBlank() }?.let { entities[it] }?.takeIf { it.available }
            val low = lowHelper?.state?.toDoubleOrNull() ?: r.optDouble("low").takeIf { !it.isNaN() }
            val high = highHelper?.state?.toDoubleOrNull() ?: r.optDouble("high").takeIf { !it.isNaN() }
            val tone = toneColors(comfortTone(t, r.optString("type"), low, high))
            val editable = lowHelper != null && highHelper != null
            Row(
                modifier = Modifier.at(0.5f, y, 7f, 1f).then(if (editable) Modifier.clickable(onClickLabel = "Change the comfortable range") { editing = r.optString("name") } else Modifier),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(cell * 0.35f),
            ) {
                ToneIconName(r.optString("icon"), CdVectors.Info, tone, 40)
                Column(modifier = Modifier.weight(1f)) {
                    Text(r.optString("name"), style = MaterialTheme.typography.bodyLarge, color = ink, maxLines = 1)
                    r.optString("note").takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.8f), maxLines = 1)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(if (t == null) "–" else "${temp(t)}°", style = MaterialTheme.typography.headlineMedium, color = tone.accent)
                    if (h != null) Text("${h.roundToInt()}% humidity", style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.8f))
                }
            }
            if (editing == r.optString("name") && lowHelper != null && highHelper != null) {
                RoomRangeSheet(r.optString("name"), lowHelper, highHelper, tone, call) { editing = null }
            }
            if (open) {
                // The same graph as the thermostat cards: the comfort band for the room, the humidity limits, smoothed.
                Box(Modifier.at(0.5f, y + 1.5f, 7f, 2f).clip(RoundedCornerShape(18.dp))) {
                    androidx.compose.runtime.CompositionLocalProvider(LocalGraphFill provides true) {
                        RoomGraph(r.optString("temperature"), r.optString("humidity").takeIf { it.isNotBlank() }, r.optString("type"), ink, tone.accent, low, high)
                    }
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
    val speeds = fanSpeeds(fan)
    val percentage = fan?.num("percentage") ?: 0.0
    val current = if (on) speeds.firstOrNull { sp -> if (sp.preset != null) sp.preset == preset else Math.abs(percentage - (sp.percentage ?: 0)) < 5 } else null
    val others = presets.filter { speedNumber(it) == null }
    val canOscillate = fan?.str("oscillating") != null || ((fan?.num("supported_features")?.toInt() ?: 0) and 2) == 2
    // Icons first: the fan with its speed number, and the fan crossed out for Off, no words.
    val speedItems = buildList {
        add(TileItem("mdi:fan-off", "Off", !on, false) { call("fan", "turn_off", id, data()) })
        speeds.forEachIndexed { i, sp ->
            add(TileItem(if (speeds.size <= 3) "mdi:fan-speed-${sp.n}" else fanSpeedIcon(i, speeds.size), "Speed ${sp.n}", current === sp, false) {
                if (sp.preset != null) call("fan", "set_preset_mode", id, data("preset_mode" to sp.preset))
                else call("fan", "set_percentage", id, data("percentage" to (sp.percentage ?: 0)))
            })
        }
    }
    val second = buildList {
        if (config.optBoolean("show_presets", true)) {
            others.forEach { p -> add(TileItem(fanPresetIcon(p), presetLabel(p), on && preset == p) { call("fan", "set_preset_mode", id, data("preset_mode" to p)) }) }
        }
        if (config.optBoolean("show_oscillate", true) && canOscillate) {
            val oscillating = fan?.attributes?.optBoolean("oscillating", false) == true
            add(TileItem(IconMap.of("fan", "oscillate", "mdi:arrow-oscillating"), "Oscillate", on && oscillating) { call("fan", "oscillate", id, data("oscillating" to !oscillating)) })
        }
    }
    val showSpeeds = config.optBoolean("show_speeds", true)
    val status = if (!on) "Off" else preset?.let { presetLabel(it) } ?: "On"
    val fold = rememberFold("grid-fold|$id")
    val ink = tone.onContainer
    if (fold.value) {
        GridCard(tone.container, ink, 2.5f, name, deviceEntity = id, fold = fold) {
            val first = 8f - speedItems.size
            FoldBand(0.5f, first, ink, listOfNotNull(status, room?.let { "${temp(it)}°" }).joinToString(" · "), null, if (showSpeeds) speedItems else emptyList(), tone, enabled)
        }
        return
    }
    var y = 3f
    val speedsY = if (showSpeeds) { val v = y; y += 2f; v } else 0f
    val secondY = if (second.isNotEmpty()) { val v = y; y += 2f; v } else 0f
    GridCard(tone.container, ink, y, name, deviceEntity = id, fold = fold) {
        RingHead(config.optString("icon").ifBlank { "mdi:fan" }, if (on && speeds.isNotEmpty() && current != null) (speeds.indexOf(current) + 1f) / speeds.size else if (on) 1f else 0f, tone.accent, ink, status, "", room?.let { "${temp(it)}°" })
        if (showSpeeds) GridTiles(speedItems, speedsY, 1.5f, tone, ink, enabled, stacked = true)
        if (second.isNotEmpty()) GridTiles(second, secondY, 1.5f, tone, ink, enabled, stacked = true)
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
    // What was just pressed is shown at once and kept until the purifier says it has changed (a slow cloud device can take half a minute), so the highlighted button does not jump back.
    var wanted by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(wanted) { if (wanted != null) { kotlinx.coroutines.delay(30_000); wanted = null } }
    androidx.compose.runtime.LaunchedEffect(on, preset) { if (wanted != null && ((wanted == "__off" && !on) || (on && preset == wanted))) wanted = null }
    val shownOn = when (wanted) { null -> on; "__off" -> false; else -> true }
    val shownPreset = if (wanted != null && wanted != "__off") wanted else preset
    val modeItems = buildList {
        add(TileItem(IconMap.of("purifier", "off", "mdi:power"), "Off", !shownOn) { wanted = "__off"; call("fan", "turn_off", id, data()) })
        if (presets.isEmpty()) add(TileItem("mdi:air-purifier", "On", shownOn) { wanted = null; call("fan", "turn_on", id, data()) })
        presets.forEach { p -> add(TileItem(purifierModeIcon(p), presetLabel(p), shownOn && shownPreset == p) { wanted = p; call("fan", "set_preset_mode", id, data("preset_mode" to p)) }) }
    }
    val filterRows = (0 until (filters?.length() ?: 0)).mapNotNull { i ->
        val f = filters?.optJSONObject(i) ?: return@mapNotNull null
        val left = entities[f.optString("entity")]?.state?.toDoubleOrNull() ?: return@mapNotNull null
        Triple(f.optString("name").ifBlank { "Filter" }, left, if (left < 10) toneColors(Tone.Red).accent else tone.accent)
    }
    val status = if (!shownOn) "Off" else shownPreset?.let { presetLabel(it) } ?: "On"
    val ink = tone.onContainer
    val fold = rememberFold("grid-fold|$id")
    if (fold.value) {
        GridCard(tone.container, ink, 2.5f, name, deviceEntity = id, fold = fold) {
            val buttons = modeItems.take(4)
            FoldBand(0.5f, 8f - buttons.size, ink, listOfNotNull(status, pm?.let { "PM2.5 ${it.roundToInt()}" }).joinToString(" · "),
                listOfNotNull(pm?.let { pmWord(it) }, filterRows.firstOrNull()?.let { "filters ${it.second.roundToInt()}%" }).joinToString(" · "), buttons, tone, enabled)
        }
        return
    }
    val showGraph = config.optBoolean("show_graph", true) && config.optString("pm25_entity").isNotBlank()
    var y = 3f
    val graphY = if (showGraph) { val v = y; y += 2.5f; v } else 0f
    val modesY = y; y += 2f
    val filtersY = y
    val total = if (filterRows.isEmpty()) filtersY else Math.ceil(((filtersY + filterRows.size * 0.75f + 0.5f) * 2f).toDouble()).toFloat() / 2f
    GridCard(tone.container, ink, total, name, deviceEntity = id, fold = fold) {
        RingHead("mdi:air-purifier", if (pm == null) 1f else (1f - (pm.toFloat() / 135f)).coerceIn(0.1f, 1f), tone.accent, ink, status, listOfNotNull(pm?.let { "PM2.5 · ${pmWord(it)}" }, allergen?.let { "Allergen ${it.roundToInt()}" }).joinToString(" · "), pm?.let { "${it.roundToInt()}" })
        if (showGraph) Box(Modifier.at(0.5f, graphY, 7f, 2f).clip(RoundedCornerShape(18.dp))) {
            androidx.compose.runtime.CompositionLocalProvider(LocalGraphFill provides true) { SensorGraph(
                config.optString("pm25_entity"), 24, " µg/m³", tone.accent, tone.onContainer, config.optBoolean("smooth_graphs", true),
                // Coloured by value, as on the dashboard: each band's colour in the middle of its band, blending into the next.
                scale = listOf(
                    21.0 to toneColors(Tone.QualityGood).accent,
                    55.0 to toneColors(Tone.QualityFair).accent,
                    95.0 to toneColors(Tone.QualityPoor).accent,
                    135.0 to toneColors(Tone.Red).accent,
                ),
            ) }
        }
        GridTiles(modeItems, modesY, 1.5f, tone, ink, enabled, stacked = true)
        filterRows.forEachIndexed { k, (fname, left, _) ->
            Row(Modifier.at(0.5f, filtersY + 0.75f * k, 7f, 0.75f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(fname, style = MaterialTheme.typography.bodyMedium, color = ink, maxLines = 1, modifier = Modifier.weight(1f))
                LevelBar((left / 100.0).toFloat(), batteryFade((left / 100.0).toFloat()), ink, Modifier.width(cell * 1.75f), 5.dp)
                Text("${left.roundToInt()}% left", style = MaterialTheme.typography.bodyMedium, color = ink, maxLines = 1, modifier = Modifier.width(cell * 1.5f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
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
    val facts = listOfNotNull(
        status?.let { presetLabel(it) },
        battery?.let { "battery ${it.roundToInt()}%" },
        shortDayOf(report)?.let { "checked in $it" },
    ).joinToString(" · ")
    val ink = tone.onContainer
    val fold = rememberFold("grid-fold|co|${config.optString("entity")}")
    val buttons = buildList {
        if (test != null) add(TileItem(IconMap.of("co", "test", "cd:co-alert"), "Test", false) { call("button", "press", test, data()) })
        if (mute != null) add(TileItem(IconMap.of("co", "mute", "mdi:volume-off"), "Mute", alarm) { call("button", "press", mute, data()) })
    }
    if (fold.value) {
        GridCard(tone.container, ink, 2.5f, "Carbon Monoxide", deviceEntity = config.optString("entity"), fold = fold) {
            FoldBand(0.5f, 8f - buttons.size, ink, (if (alarm) "ALARM" else "All clear") + (reading?.let { " · ${it.roundToInt()} ppm" } ?: ""), battery?.let { "Battery ${it.roundToInt()}%" }, buttons, tone, true)
        }
        return
    }
    GridCard(tone.container, ink, if (buttons.isEmpty()) 3.5f else 4.5f, "Carbon Monoxide", deviceEntity = config.optString("entity"), fold = fold) {
        Column(Modifier.at(0.5f, 1f, 5f, 2f)) {
            StatusPill(if (alarm) "ALARM" else "All clear", tone)
            if (facts.isNotEmpty()) Text(facts, style = MaterialTheme.typography.bodyMedium, color = ink.copy(alpha = 0.85f), modifier = Modifier.padding(top = 6.dp))
        }
        Text(if (reading == null) "–" else "${reading.roundToInt()} ppm", style = MaterialTheme.typography.headlineMedium, color = ink, modifier = Modifier.at(5f, 1.5f, 2.5f, 1f).wrapContentSize(Alignment.CenterEnd))
        if (buttons.isNotEmpty()) GridTiles(buttons, 3f, 1f, tone, ink, true, stacked = false)
    }
}

/** The dashboard's `custom:cover-card`: a blind or shutter with its position and Open, Stop and Close. */
@Composable
fun CoverCard(config: JSONObject, entities: Map<String, EntityState>, call: CallService) {
    val id = config.optString("entity")
    val cover = entities[id]
    val position = cover?.num("current_position")
    val state = cover?.state
    // A blind that only assumes its state (an RF one) reports "unknown", but still takes Open, Stop and Close: only a missing or unavailable one doesn't.
    val enabled = cover != null && state != "unavailable"
    val tone = toneColors(Tone.Blinds) // a blind does not report its state, so it keeps its colour rather than going grey
    var slider0 by remember(position) { mutableStateOf((position ?: 0.0).toFloat()) }
    var last by remember { mutableStateOf<String?>(null) }
    val name = config.optString("name").ifBlank { cover?.friendlyName ?: id }
    val word = when (state) {
        null -> "Loading…"
        "unknown" -> last?.let { "Last: ${presetLabel(it)}" } ?: "Position unknown"
        "open", "closed" -> presetLabel(state) + (position?.let { " · ${it.roundToInt()}%" } ?: "")
        else -> presetLabel(state)
    }
    val ink = tone.onContainer
    val fold = rememberFold("grid-fold|$id")
    val info = listOf(config.optString("subtitle"), word).filter { it.isNotBlank() }.joinToString(" · ")
    val items = listOf(
        TileItem(IconMap.of("cover", "open", "mdi:arrow-up"), "Open", cover?.state == "open") { last = "open"; call("cover", "open_cover", id, data()) },
        TileItem(IconMap.of("cover", "stop", "mdi:stop"), "Stop", false) { last = "stopped"; call("cover", "stop_cover", id, data()) },
        TileItem(IconMap.of("cover", "close", "mdi:arrow-down"), "Close", cover?.state == "closed") { last = "closed"; call("cover", "close_cover", id, data()) },
    )
    if (fold.value) {
        GridCard(tone.container, ink, 2.5f, name, deviceEntity = id, fold = fold) { FoldBand(0.5f, 5f, ink, word, config.optString("subtitle").ifBlank { null }, items, tone, enabled) }
        return
    }
    val slider = position != null
    val tilesY = if (slider) 3.5f else 2.5f
    GridCard(tone.container, ink, tilesY + 2f, name, deviceEntity = id, fold = fold) {
        Text(info, style = MaterialTheme.typography.bodyMedium, color = ink, modifier = Modifier.at(0.5f, 1f, 7f, 1f).wrapContentHeight(Alignment.CenterVertically))
        if (position != null) {
            Slider(
                value = slider0,
                onValueChange = { slider0 = it },
                onValueChangeFinished = { call("cover", "set_cover_position", id, data("position" to slider0.roundToInt())) },
                valueRange = 0f..100f,
                enabled = enabled,
                modifier = Modifier.at(0.5f, 2.25f, 7f, 1f),
            )
        }
        GridTiles(items, tilesY, 1.5f, tone, ink, enabled, stacked = true)
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
    val ground = toneColors(Tone.Grey)
    val tone = toneColors(weatherTone(weather.state))
    val upcoming = points.filter { it.timeMs >= System.currentTimeMillis() - 30 * 60_000L }.take(12)
    EntityCard(ground.container, ground.onContainer) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ToneIconName(weatherIcon(weather.state), CdVectors.Info, tone, 48)
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
                        HaIcon(weatherIcon(p.condition), CdVectors.Info, toneColors(weatherTone(p.condition)).accent, 24.dp, Modifier.padding(vertical = 4.dp))
                        Text(p.temperature?.let { "${it.roundToInt()}°" } ?: "–", fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
