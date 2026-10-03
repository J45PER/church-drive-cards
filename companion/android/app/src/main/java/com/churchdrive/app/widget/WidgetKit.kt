package com.churchdrive.app.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.PathParser
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.compose.runtime.Composable
import com.churchdrive.app.ui.MdiIcons
import com.churchdrive.app.ui.ToneColors
import com.churchdrive.app.ui.Ui
import com.churchdrive.app.ui.tileRowSizes
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.delay
import org.json.JSONObject

/** Whether the phone is in dark mode now (the widgets are drawn in the matching colours). */
fun isDark(context: Context): Boolean =
    context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

/** A Material Design icon drawn into a bitmap in [colour], for a widget (which can't use the app's own icon drawing). */
fun iconBitmap(name: String, colour: Color, sizePx: Int = 96): Bitmap? {
    val d = MdiIcons.paths[name.removePrefix("mdi:")] ?: return null
    val path = runCatching { PathParser.createPathFromPathData(d) }.getOrNull() ?: return null
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val scale = sizePx / 24f
    val canvas = Canvas(bitmap)
    canvas.scale(scale, scale)
    canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colour.toArgb() })
    return bitmap
}

/** The parameters of a button press: the service to call, the entity and any extra data (JSON). */
object WidgetKeys {
    val DOMAIN = ActionParameters.Key<String>("domain")
    val SERVICE = ActionParameters.Key<String>("service")
    val ENTITY = ActionParameters.Key<String>("entity")
    val DATA = ActionParameters.Key<String>("data")
}

fun serviceAction(domain: String, service: String, entity: String, data: String = "{}"): Action =
    actionRunCallback<ServiceCallback>(
        actionParametersOf(WidgetKeys.DOMAIN to domain, WidgetKeys.SERVICE to service, WidgetKeys.ENTITY to entity, WidgetKeys.DATA to data),
    )

/** A widget button press: calls the service, then redraws the widgets once the house has caught up. */
class ServiceCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: androidx.glance.GlanceId, parameters: ActionParameters) {
        val domain = parameters[WidgetKeys.DOMAIN] ?: return
        val service = parameters[WidgetKeys.SERVICE] ?: return
        val entity = parameters[WidgetKeys.ENTITY] ?: return
        val extra = runCatching { JSONObject(parameters[WidgetKeys.DATA] ?: "{}") }.getOrDefault(JSONObject())
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { WidgetSource.callService(context, domain, service, entity, extra) }
        delay(1_000)
        for (w in listOf<GlanceAppWidget>(AlarmGlanceWidget(), LightsGlanceWidget(), ClimateGlanceWidget())) w.updateAll(context)
    }
}

private fun provider(c: Color) = ColorProvider(c)

/**
 * The card's heading row, as the app's Home panels have it: an icon in a circle tinted with the section's colour
 * ([tone]), the title, and a line under it in that colour.
 */
@Composable
fun CardHeader(icon: String, title: String, subtitle: String, neutral: ToneColors, tone: ToneColors, trailing: @Composable () -> Unit = {}) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            GlanceModifier.size(44.dp).cornerRadius(22.dp).background(provider(tone.accent.copy(alpha = 0.18f))),
            contentAlignment = Alignment.Center,
        ) {
            iconBitmap(icon, tone.accent)?.let { Image(ImageProvider(it), null, GlanceModifier.size(26.dp)) }
        }
        Spacer(GlanceModifier.width(12.dp))
        Column(GlanceModifier.defaultWeight()) {
            Text(title, style = TextStyle(color = provider(neutral.onContainer), fontSize = 18.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            if (subtitle.isNotBlank()) Text(subtitle, style = TextStyle(color = provider(tone.accent), fontSize = 13.sp, fontWeight = FontWeight.Medium), maxLines = 1)
        }
        trailing()
    }
}

/** Buttons filling the width, as the app's tiles do: icon over label; the chosen one in the section's colour ([tone]). */
@Composable
fun TileButtons(tiles: List<WidgetTile>, neutral: ToneColors, tone: ToneColors, perRow: Int = 4) {
    var i = 0
    Column(GlanceModifier.fillMaxWidth()) {
        tileRowSizes(tiles.size, perRow).forEachIndexed { r, size ->
            if (r > 0) Spacer(GlanceModifier.height(8.dp))
            Row(GlanceModifier.fillMaxWidth()) {
                repeat(size) { n ->
                    val tile = tiles[i++]
                    if (n > 0) Spacer(GlanceModifier.width(8.dp))
                    val fg = if (tile.selected) tone.onAccent else neutral.onContainer
                    Column(
                        GlanceModifier.defaultWeight().height(Ui.TileHeight)
                            .cornerRadius(16.dp)
                            .background(provider(if (tile.selected) tone.accent else neutral.onContainer.copy(alpha = 0.10f)))
                            .clickable(serviceAction(tile.domain, tile.service, tile.entity, tile.data)),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        iconBitmap(tile.icon, fg)?.let { Image(ImageProvider(it), null, GlanceModifier.size(20.dp)) }
                        Text(tile.label, style = TextStyle(color = provider(fg), fontSize = 11.sp), maxLines = 1)
                    }
                }
            }
        }
    }
}
