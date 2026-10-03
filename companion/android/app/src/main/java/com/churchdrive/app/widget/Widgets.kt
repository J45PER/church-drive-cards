package com.churchdrive.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.churchdrive.app.ui.ALARM_ENTITY
import com.churchdrive.app.ui.CLIMATE_ENTITY
import com.churchdrive.app.ui.CLIMATE_QUALITY_ENTITY
import com.churchdrive.app.ui.DEFAULT_QUICK
import com.churchdrive.app.ui.Tone
import com.churchdrive.app.ui.alarmIconName
import com.churchdrive.app.ui.alarmLabel
import com.churchdrive.app.ui.alarmTone
import com.churchdrive.app.ui.climateTone
import com.churchdrive.app.ui.climateWord
import com.churchdrive.app.ui.lightsSummary
import com.churchdrive.app.ui.qualityTone
import com.churchdrive.app.ui.qualityWord
import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ha.data
import org.json.JSONObject

/** The widget's own id as Android knows it (its saved choices are kept under it). */
private suspend fun appWidgetId(context: Context, id: GlanceId): Int = GlanceAppWidgetManager(context).getAppWidgetId(id)

// ---------------------------------------------------------------------------------------------- Alarm

/** The alarm panel: fixed, it only ever shows the alarm. A strip, a small square with one smart button, or the four modes. */
class AlarmGlanceWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(WidgetSizes.all)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideLive(context, id, onLoaded = { c, l -> if (alarmInDelay(l.data?.entities?.get(ALARM_ENTITY))) refreshSoon(c, AlarmWidgetReceiver::class.java, 4) }, ) { l ->
            val data = l.data
            val config = l.config
            val p = WidgetPalette.of(androidx.glance.LocalContext.current)

            val alarm = data?.entities?.get(ALARM_ENTITY)
            if (data == null || alarm == null) {
                Unreachable("Security", p, if (data == null) "Can't reach the house" else "Alarm not found")
            } else {
                AlarmContent(alarm, p, LocalSize.current.sizeClass())
            }
        }
    }
}

@Composable
private fun AlarmContent(alarm: EntityState, p: WidgetPalette, size: SizeClass) {
    val tone = p.tone(alarmTone(alarm.state))
    val icon = alarmIconName(alarm.state)
    val tiles = WidgetModel.alarmTiles(alarm, ALARM_ENTITY)
    when (size) {
        SizeClass.Strip -> WidgetCard(p, padding = 10.dp) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconCircle(icon, tone, 40.dp)
                Spacer(GlanceModifier.width(10.dp))
                Column(GlanceModifier.defaultWeight()) {
                    Text("Security", style = TextStyle(color = cp(p.onSurface), fontSize = 16.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                    Text(alarmStatus(alarm), style = TextStyle(color = cp(tone.accent), fontSize = 12.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                }
                IconRow(tiles, p, tone, 34.dp)
            }
        }
        SizeClass.Square -> WidgetCard(p, padding = 12.dp) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { IconCircle(icon, tone, 40.dp) }
            Spacer(GlanceModifier.height(6.dp))
            Text("Security", style = TextStyle(color = cp(p.onSurface), fontSize = 16.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            Text(alarmStatus(alarm), style = TextStyle(color = cp(tone.accent), fontSize = 12.sp, fontWeight = FontWeight.Medium), maxLines = 1)
            Spacer(GlanceModifier.height(8.dp))
            // One smart button: arm when it is off, disarm when it is armed.
            val disarmed = alarm.state == "disarmed"
            val button = tiles.first { it.service == if (disarmed) "alarm_arm_home" else "alarm_disarm" }
            Box(
                GlanceModifier.fillMaxWidth().height(34.dp).cornerRadius(17.dp).background(cp(tone.accent)).clickable(button.action()),
                contentAlignment = Alignment.Center,
            ) { Text(if (disarmed) "Arm home" else "Disarm", style = TextStyle(color = cp(tone.onAccent), fontSize = 13.sp, fontWeight = FontWeight.Bold)) }
        }
        else -> WidgetCard(p) {
            HeaderRow(icon, "Security", alarmStatus(alarm), p, tone)
            Spacer(GlanceModifier.height(12.dp))
            TileButtons(tiles, p, tone)
            if (size == SizeClass.Tall) {
                val who = alarm.str(if (alarm.state == "disarmed") "lastDisarmedBy" else "lastArmedBy")
                if (!who.isNullOrBlank()) {
                    Spacer(GlanceModifier.height(10.dp))
                    Text("by $who", style = TextStyle(color = cp(p.muted), fontSize = 12.sp))
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------- Lights

/** Lights: for one room or several, chosen when the widget is added; with scenes or with a brightness bar. */
class LightsGlanceWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(WidgetSizes.all)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideLive(context, id, ) { l ->
            val data = l.data
            val config = l.config
            val p = WidgetPalette.of(androidx.glance.LocalContext.current)

            if (data == null) {
                Unreachable("Lights", p, "Can't reach the house")
            } else {
                val rooms = chosenRooms(data.lights, WidgetConfig.strings(config, "rooms"))
                val withScenes = config.optBoolean("scenes", true)
                LightsContent(rooms, data, withScenes, p, LocalSize.current.sizeClass())
            }
        }
    }
}

@Composable
private fun LightsContent(rooms: List<com.churchdrive.app.ui.LightRoom>, data: WidgetData, withScenes: Boolean, p: WidgetPalette, size: SizeClass) {
    val e = data.entities
    val amber = p.tone(Tone.Amber)
    val any = rooms.any { e[it.head]?.state == "on" }
    val tone = if (any) amber else p.tone(Tone.Grey)
    if (rooms.size == 1) {
        val room = rooms.first()
        val head = e[room.head]
        val on = head?.state == "on"
        val name = WidgetModel.roomName(room, data.areaNames, e)
        val brightness = head?.num("brightness")?.let { it / 255.0 }
        val sub = if (!on) "Off" else brightness?.let { "On · ${(it * 100).toInt()}%" } ?: "On"
        val toggle = serviceAction("light", "toggle", room.head)
        when (size) {
            SizeClass.Strip -> WidgetCard(p, padding = 10.dp) {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconCircle(entityIcon(head, "mdi:lightbulb"), tone, 40.dp)
                    Spacer(GlanceModifier.width(10.dp))
                    Column(GlanceModifier.defaultWeight()) {
                        Text(name, style = TextStyle(color = cp(p.onSurface), fontSize = 16.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                        Text(sub, style = TextStyle(color = cp(tone.accent), fontSize = 12.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                    }
                    Spacer(GlanceModifier.width(6.dp))
                    IconButton("mdi:power", p, toggle, 36.dp, if (on) amber else null)
                }
            }
            SizeClass.Square -> WidgetCard(p, padding = 12.dp) {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(GlanceModifier.clickable(toggle)) { IconCircle(entityIcon(head, "mdi:lightbulb"), tone, 40.dp) }
                }
                Spacer(GlanceModifier.height(6.dp))
                Text(name, style = TextStyle(color = cp(p.onSurface), fontSize = 16.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                Text(sub, style = TextStyle(color = cp(tone.accent), fontSize = 12.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                if (on && brightness != null) {
                    Spacer(GlanceModifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = brightness.toFloat().coerceIn(0f, 1f), modifier = GlanceModifier.fillMaxWidth().height(8.dp),
                        color = cp(amber.accent), backgroundColor = cp(p.tile),
                    )
                }
            }
            else -> WidgetCard(p) {
                HeaderRow(entityIcon(head, "mdi:lightbulb"), name, sub, p, tone) { IconButton("mdi:power", p, toggle, 40.dp, if (on) amber else null) }
                Spacer(GlanceModifier.height(12.dp))
                if (withScenes && room.scenes.isNotEmpty()) {
                    val tiles = room.scenes.map { s ->
                        val action = if (s.haScene != null) Triple("scene", "turn_on", s.haScene) else Triple("church_drive", "apply_scene", s.target)
                        WidgetTile("mdi:lightbulb-group", s.name, false, action.first, action.second, action.third, if (s.haScene != null) "{}" else JSONObject().put("scene", s.key).toString())
                    }
                    TileButtons(tiles, p, amber, perRow = 4, maxRows = if (size == SizeClass.Tall) 2 else 1, showLabels = true)
                } else {
                    // No scenes: a brightness bar with − and +.
                    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IconButton("mdi:minus", p, serviceAction("light", "turn_on", room.head, """{"brightness_step_pct":-10}"""), 40.dp)
                        Spacer(GlanceModifier.width(10.dp))
                        Box(GlanceModifier.defaultWeight(), contentAlignment = Alignment.Center) {
                            LinearProgressIndicator(
                                progress = (brightness ?: 0.0).toFloat().coerceIn(0f, 1f), modifier = GlanceModifier.fillMaxWidth().height(10.dp),
                                color = cp(amber.accent), backgroundColor = cp(p.tile),
                            )
                        }
                        Spacer(GlanceModifier.width(10.dp))
                        IconButton("mdi:plus", p, serviceAction("light", "turn_on", room.head, """{"brightness_step_pct":10}"""), 40.dp)
                    }
                }
            }
        }
        return
    }
    // Several rooms.
    val tiles = WidgetModel.roomTiles(rooms, data.areaNames, e, max = 6)
    when (size) {
        SizeClass.Strip -> WidgetCard(p, padding = 10.dp) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconCircle("mdi:lightbulb", tone, 40.dp)
                Spacer(GlanceModifier.width(10.dp))
                Column(GlanceModifier.defaultWeight()) {
                    Text("Lights", style = TextStyle(color = cp(p.onSurface), fontSize = 16.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                    Text(lightsSummary(rooms, e), style = TextStyle(color = cp(tone.accent), fontSize = 12.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                }
                IconRow(tiles.take(4).map { it.copy(label = "") }, p, amber, 34.dp)
            }
        }
        SizeClass.Square -> WidgetCard(p, padding = 12.dp) {
            Text("Lights", style = TextStyle(color = cp(p.onSurface), fontSize = 14.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            Text(lightsSummary(rooms, e), style = TextStyle(color = cp(tone.accent), fontSize = 12.sp, fontWeight = FontWeight.Medium), maxLines = 1)
            Spacer(GlanceModifier.height(8.dp))
            TileButtons(tiles.take(4), p, amber, perRow = 2, maxRows = 2, showLabels = false)
        }
        else -> WidgetCard(p) {
            HeaderRow("mdi:lightbulb", "Lights", lightsSummary(rooms, e), p, tone)
            Spacer(GlanceModifier.height(12.dp))
            TileButtons(tiles, p, amber, perRow = 3, maxRows = if (size == SizeClass.Tall) 2 else 1)
        }
    }
}

// ---------------------------------------------------------------------------------------------- Thermostat

/** The thermostat: you choose which one when you add it. A dial, a strip, or the temperature with the Off, Heat and Eco buttons. */
class ClimateGlanceWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(WidgetSizes.all)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideLive(context, id, ) { l ->
            val data = l.data
            val config = l.config
            val p = WidgetPalette.of(androidx.glance.LocalContext.current)
            val entity = config.optString("climate").ifBlank { CLIMATE_ENTITY }

            val climate = data?.entities?.get(entity)
            if (data == null || climate == null) {
                Unreachable("Heating", p, if (data == null) "Can't reach the house" else "Thermostat not found")
            } else {
                ClimateContent(climate, entity, data.entities[CLIMATE_QUALITY_ENTITY]?.state?.toIntOrNull(), p, LocalSize.current.sizeClass())
            }
        }
    }
}

@Composable
private fun ClimateContent(climate: EntityState, entity: String, score: Int?, p: WidgetPalette, size: SizeClass) {
    val current = climate.num("current_temperature")
    val temp = current?.let { "%.1f°".format(it) } ?: "–"
    val target = climate.takeIf { it.state != "off" }?.num("temperature")
    val mode = p.tone(climateTone(climate))
    val tone = p.tone(if (score != null && size == SizeClass.Tall) qualityTone(score) else climateTone(climate))
    val status = listOfNotNull(climateWord(climate), target?.let { "target %.1f°".format(it) }).joinToString(" · ")
    val minus = WidgetModel.nextTarget(climate, -1)
    val plus = WidgetModel.nextTarget(climate, 1)
    when (size) {
        SizeClass.Strip -> WidgetCard(p, padding = 10.dp) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconCircle("mdi:thermostat", mode, 40.dp)
                Spacer(GlanceModifier.width(10.dp))
                Column(GlanceModifier.defaultWeight()) {
                    Text(temp, style = TextStyle(color = cp(p.onSurface), fontSize = 18.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                    Text(status, style = TextStyle(color = cp(mode.accent), fontSize = 12.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                }
                if (target != null) {
                    StepButton("mdi:minus", p, mode, entity, minus, 34.dp)
                    Spacer(GlanceModifier.width(6.dp))
                    StepButton("mdi:plus", p, mode, entity, plus, 34.dp)
                }
            }
        }
        SizeClass.Square -> WidgetCard(p, round = true, padding = 6.dp) {
            val range = (climate.num("min_temp") ?: 7.0) to (climate.num("max_temp") ?: 25.0)
            fun frac(v: Double?) = if (v == null) 0f else ((v - range.first) / (range.second - range.first)).toFloat()
            Box(GlanceModifier.size(100.dp), contentAlignment = Alignment.Center) {
                Image(ImageProvider(ringBitmap(300, frac(current), p.tile, mode.accent, target?.let { frac(it) })), null, GlanceModifier.size(100.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(temp, style = TextStyle(color = cp(p.onSurface), fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center), maxLines = 1)
                    Text(climateWord(climate), style = TextStyle(color = cp(mode.accent), fontSize = 11.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center), maxLines = 1)
                    if (target != null) Text("→ %.1f°".format(target), style = TextStyle(color = cp(p.muted), fontSize = 10.sp, textAlign = TextAlign.Center), maxLines = 1)
                }
            }
            if (target != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StepButton("mdi:minus", p, mode, entity, minus, 28.dp)
                    Spacer(GlanceModifier.width(14.dp))
                    StepButton("mdi:plus", p, mode, entity, plus, 28.dp)
                }
            }
        }
        else -> WidgetCard(p) {
            HeaderRow("mdi:leaf", temp, status, p, mode) {
                if (target != null) {
                    Spacer(GlanceModifier.width(8.dp))
                    StepButton("mdi:minus", p, mode, entity, minus, 38.dp)
                    Spacer(GlanceModifier.width(8.dp))
                    StepButton("mdi:plus", p, mode, entity, plus, 38.dp)
                }
            }
            Spacer(GlanceModifier.height(12.dp))
            TileButtons(WidgetModel.climateTiles(climate, entity, DEFAULT_QUICK), p, mode, perRow = 5, maxRows = 1)
            if (size == SizeClass.Tall) {
                val humidity = climate.num("current_humidity")?.let { "${it.toInt()}% humidity" }
                val line = listOfNotNull(humidity, score?.let { "air $it/100 ${qualityWord(it)}" }).joinToString(" · ")
                if (line.isNotBlank()) {
                    Spacer(GlanceModifier.height(10.dp))
                    Text(line, style = TextStyle(color = cp(p.muted), fontSize = 12.sp), maxLines = 1)
                }
            }
        }
    }
}

/** A round − or + that sets the thermostat's target to [to]. */
@Composable
private fun StepButton(icon: String, p: WidgetPalette, tone: com.churchdrive.app.ui.ToneColors, entity: String, to: Double?, size: androidx.compose.ui.unit.Dp) {
    if (to == null) return
    IconButton(icon, p, serviceAction("climate", "set_temperature", entity, """{"temperature":$to}"""), size, tone)
}

// ---------------------------------------------------------------------------------------------- Air quality

// ---------------------------------------------------------------------------------------------- Summary

/** Summary: up to four readings you pick when you add it. */
class SummaryGlanceWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(WidgetSizes.all)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideLive(context, id, ) { l ->
            val data = l.data
            val config = l.config
            val p = WidgetPalette.of(androidx.glance.LocalContext.current)

            if (data == null) {
                Unreachable("Summary", p, "Can't reach the house")
            } else {
                val stats = Stats.chosen(WidgetConfig.strings(config, "stats"), data.entities)
                val size = LocalSize.current.sizeClass()
                WidgetCard(p, padding = if (size == SizeClass.Square) 10.dp else 12.dp) {
                    // A strip or a wide card is a single row; a square or tall card is two rows of two.
                    val perRow = if (size == SizeClass.Strip || size == SizeClass.Wide) (if (size == SizeClass.Strip) 3 else 4) else 2
                    val rows = stats.take(if (size == SizeClass.Strip) 3 else 4).chunked(perRow)
                    Column(GlanceModifier.fillMaxWidth()) {
                        rows.forEachIndexed { r, row ->
                            if (r > 0) Spacer(GlanceModifier.height(8.dp))
                            Row(GlanceModifier.fillMaxWidth()) {
                                row.forEachIndexed { i, stat ->
                                    if (i > 0) Spacer(GlanceModifier.width(8.dp))
                                    StatTile(stat, p, GlanceModifier.defaultWeight(), big = size != SizeClass.Square)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------- Shortcuts

/** Shortcuts: up to eight buttons you pick when you add it. */
class ShortcutsGlanceWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(WidgetSizes.all)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideLive(context, id, ) { l ->
            val data = l.data
            val config = l.config
            val p = WidgetPalette.of(androidx.glance.LocalContext.current)

            if (data == null) {
                Unreachable("Shortcuts", p, "Can't reach the house")
            } else {
                val chosen = Shortcuts.chosen(WidgetConfig.strings(config, "actions"), data.entities, data.lights).map { it.tile }
                val blue = p.tone(Tone.Blue)
                when (LocalSize.current.sizeClass()) {
                    SizeClass.Strip -> WidgetCard(p, padding = 10.dp) {
                        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            chosen.take(5).forEachIndexed { i, tile ->
                                if (i > 0) Spacer(GlanceModifier.width(10.dp))
                                Box(GlanceModifier.defaultWeight(), contentAlignment = Alignment.Center) { IconButton(tile.icon, p, tile.action(), 44.dp) }
                            }
                        }
                    }
                    SizeClass.Square -> WidgetCard(p, padding = 10.dp) { TileButtons(chosen.take(4), p, blue, perRow = 2, maxRows = 2, showLabels = false) }
                    SizeClass.Wide -> WidgetCard(p, padding = 12.dp) { TileButtons(chosen.take(4), p, blue, perRow = 4, maxRows = 1) }
                    SizeClass.Tall -> WidgetCard(p, padding = 12.dp) { TileButtons(chosen, p, blue, perRow = 4, maxRows = 2) }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------- Receivers

/** Forgets a removed widget's choices. */
private fun forget(context: Context, ids: IntArray) = WidgetConfig.remove(context, ids)

class AlarmWidgetReceiver : LiveReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AlarmGlanceWidget()
    override fun onDeleted(context: Context, appWidgetIds: IntArray) { super.onDeleted(context, appWidgetIds); forget(context, appWidgetIds) }
}

class LightsWidgetReceiver : LiveReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LightsGlanceWidget()
    override fun onDeleted(context: Context, appWidgetIds: IntArray) { super.onDeleted(context, appWidgetIds); forget(context, appWidgetIds) }
}

class ClimateWidgetReceiver : LiveReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ClimateGlanceWidget()
    override fun onDeleted(context: Context, appWidgetIds: IntArray) { super.onDeleted(context, appWidgetIds); forget(context, appWidgetIds) }
}

class SummaryWidgetReceiver : LiveReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SummaryGlanceWidget()
    override fun onDeleted(context: Context, appWidgetIds: IntArray) { super.onDeleted(context, appWidgetIds); forget(context, appWidgetIds) }
}

class ShortcutsWidgetReceiver : LiveReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ShortcutsGlanceWidget()
    override fun onDeleted(context: Context, appWidgetIds: IntArray) { super.onDeleted(context, appWidgetIds); forget(context, appWidgetIds) }
}
