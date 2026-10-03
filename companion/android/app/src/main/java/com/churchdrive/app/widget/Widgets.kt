package com.churchdrive.app.widget

import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
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
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.glance.Image
import androidx.glance.ImageProvider
import com.churchdrive.app.MainActivity
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
import com.churchdrive.app.ui.qualityWord
import com.churchdrive.app.ui.toneColorsFor
import androidx.compose.runtime.Composable
import com.churchdrive.app.ui.ToneColors

/** The card shell the widgets share: rounded, tinted, opening the app when its background is tapped. */
@Composable
private fun CardShell(colours: ToneColors, content: @Composable () -> Unit) {
    val context = androidx.glance.LocalContext.current
    Column(
        GlanceModifier.fillMaxSize()
            .cornerRadius(28.dp)
            .background(ColorProvider(colours.container))
            .padding(16.dp)
            .clickable(actionStartActivity(android.content.Intent(context, MainActivity::class.java))),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
private fun Unreachable(title: String, colours: ToneColors) {
    CardShell(colours) {
        Text(title, style = TextStyle(color = ColorProvider(colours.onContainer), fontSize = 18.sp, fontWeight = FontWeight.Bold))
        Text("Can't reach the house", style = TextStyle(color = ColorProvider(colours.onContainer.copy(alpha = 0.75f)), fontSize = 12.sp))
    }
}

/** The alarm card: the state with its shield, and the Disarm, Home, Away and Night buttons. */
class AlarmGlanceWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = WidgetSource.load(context)
        val dark = isDark(context)
        provideContent {
            val alarm = data?.entities?.get(ALARM_ENTITY)
            val colours = toneColorsFor(if (data == null) Tone.Grey else alarmTone(alarm?.state), dark)
            if (data == null) {
                Unreachable("Security", colours)
            } else {
                CardShell(colours) {
                    CardHeader(alarmIconName(alarm?.state), alarmLabel(alarm?.state), "Security", colours)
                    Spacer(GlanceModifier.height(12.dp))
                    TileButtons(WidgetModel.alarmTiles(alarm, ALARM_ENTITY), colours)
                }
            }
        }
    }
}

/** The Home lights card: a tile for each room, lit when it's on; a tap switches the room. */
class LightsGlanceWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = WidgetSource.load(context)
        val dark = isDark(context)
        provideContent {
            val rooms = data?.lights?.home.orEmpty()
            val any = data != null && rooms.any { data.entities[it.head]?.state == "on" }
            val colours = toneColorsFor(if (any) Tone.Amber else Tone.Grey, dark)
            if (data == null) {
                Unreachable("Lights", colours)
            } else {
                CardShell(colours) {
                    CardHeader("mdi:lightbulb", "Lights", lightsSummary(rooms, data.entities), colours)
                    Spacer(GlanceModifier.height(12.dp))
                    TileButtons(WidgetModel.roomTiles(rooms, data.areaNames, data.entities), colours, perRow = 3)
                }
            }
        }
    }
}

/** The thermostat card: the temperature, the target with − and +, the home's quality score and the Off, Heat and Eco buttons. */
class ClimateGlanceWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = WidgetSource.load(context)
        val dark = isDark(context)
        provideContent {
            val climate = data?.entities?.get(CLIMATE_ENTITY)
            val colours = toneColorsFor(if (data == null) Tone.Grey else climateTone(climate), dark)
            if (data == null) {
                Unreachable("Climate", colours)
            } else {
                val temp = climate?.num("current_temperature")?.let { "%.1f°".format(it) } ?: "–"
                val target = climate?.takeIf { it.state != "off" }?.num("temperature")
                val score = data.entities[CLIMATE_QUALITY_ENTITY]?.state?.toIntOrNull()
                val sub = listOfNotNull(
                    climateWord(climate),
                    target?.let { "target %.1f°".format(it) },
                    score?.let { "$it/100 ${qualityWord(it)}" },
                ).joinToString(" · ")
                CardShell(colours) {
                    CardHeader("mdi:thermostat", temp, sub, colours) {
                        if (target != null) {
                            Spacer(GlanceModifier.width(8.dp))
                            StepButton("mdi:minus", colours, WidgetModel.nextTarget(climate, -1))
                            Spacer(GlanceModifier.width(8.dp))
                            StepButton("mdi:plus", colours, WidgetModel.nextTarget(climate, 1))
                        }
                    }
                    Spacer(GlanceModifier.height(12.dp))
                    TileButtons(WidgetModel.climateTiles(climate, CLIMATE_ENTITY, DEFAULT_QUICK), colours, perRow = 5)
                }
            }
        }
    }
}

/** A round − or + button that sets the thermostat's target to [to]. */
@Composable
private fun StepButton(icon: String, colours: ToneColors, to: Double?) {
    if (to == null) return
    Box(
        GlanceModifier.size(40.dp).cornerRadius(20.dp).background(ColorProvider(colours.accent))
            .clickable(serviceAction("climate", "set_temperature", CLIMATE_ENTITY, """{"temperature":$to}""")),
        contentAlignment = Alignment.Center,
    ) {
        iconBitmap(icon, colours.onAccent)?.let { Image(ImageProvider(it), null, GlanceModifier.size(22.dp)) }
    }
}

class AlarmWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AlarmGlanceWidget()
}

class LightsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LightsGlanceWidget()
}

class ClimateWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ClimateGlanceWidget()
}
