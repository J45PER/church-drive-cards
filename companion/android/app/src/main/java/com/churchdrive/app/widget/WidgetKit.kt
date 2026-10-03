package com.churchdrive.app.widget

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.glance.appwidget.provideContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.PathParser
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.updateAll
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
import androidx.glance.unit.ColorProvider
import com.churchdrive.app.MainActivity
import com.churchdrive.app.ui.MdiIcons
import com.churchdrive.app.ui.ToneColors
import com.churchdrive.app.ui.Ui
import com.churchdrive.app.ui.tileRowSizes
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

/** Whether the phone is in dark mode now (the widgets are drawn in the matching colours). */
fun isDark(context: Context): Boolean =
    context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

/** The sizes the widgets lay themselves out for; the widget uses the closest that fits how big it has been made. */
object WidgetSizes {
    val strip = DpSize(250.dp, 70.dp)
    val square = DpSize(110.dp, 110.dp)
    val wide = DpSize(250.dp, 140.dp)
    val tall = DpSize(250.dp, 230.dp)
    val all = setOf(strip, square, wide, tall)

    /**
     * Finer sizes: three widths by four heights. Android picks the largest that fits how big the widget really is, which
     * is reliable where the exact size it reports is not, so a thin card is laid out as thin and a card knows how much room it has.
     */
    val fine: Set<DpSize> = buildSet {
        for (w in listOf(110, 250, 320)) for (h in listOf(70, 140, 230, 320)) add(DpSize(w.dp, h.dp))
    }
}

fun DpSize.sizeClass(): SizeClass = sizeClass(width.value, height.value)

/** Every Material Design icon's path, for the icons Home Assistant gives entities (the app's own short list has only the usual ones). */
object WidgetMdi {
    @Volatile private var appContext: Context? = null
    @Volatile private var all: Map<String, String>? = null

    fun init(context: Context) { appContext = context.applicationContext }

    fun path(name: String): String? {
        MdiIcons.paths[name]?.let { return it }
        com.churchdrive.app.ui.MdiAll.paths?.get(name)?.let { return it }
        return (all ?: load())?.get(name)
    }

    @Synchronized
    private fun load(): Map<String, String>? = all ?: runCatching {
        appContext!!.assets.open("mdi-icons.json").bufferedReader().use { r ->
            val json = JSONObject(r.readText())
            HashMap<String, String>(json.length() * 2).also { map -> for (k in json.keys()) map[k] = json.getString(k) }
        }
    }.getOrNull().also { all = it }
}

/** A Material Design icon drawn into a bitmap in [colour], for a widget (which can't use the app's own icon drawing). */
fun iconBitmap(name: String, colour: Color, sizePx: Int = 96): Bitmap? {
    val d = WidgetMdi.path(name.removePrefix("mdi:")) ?: return null
    val path = runCatching { PathParser.createPathFromPathData(d) }.getOrNull() ?: return null
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val scale = sizePx / 24f
    val canvas = Canvas(bitmap)
    canvas.scale(scale, scale)
    canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colour.toArgb() })
    return bitmap
}

/**
 * A gauge ring as a picture: a track and, over it, the value as an arc ([fraction] of the way round), with an optional
 * white marker (the heating target). The ring leaves a gap at the bottom, like the dashboard's gauges.
 */
fun ringBitmap(sizePx: Int, fraction: Float, track: Color, colour: Color, marker: Float? = null, stroke: Float = 0.09f): Bitmap {
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val width = sizePx * stroke
    val inset = width / 2f + 1f
    val rect = RectF(inset, inset, sizePx - inset, sizePx - inset)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = width
        strokeCap = Paint.Cap.ROUND
    }
    paint.color = track.toArgb()
    canvas.drawArc(rect, 135f, 270f, false, paint)
    val f = fraction.coerceIn(0f, 1f)
    if (f > 0f) {
        paint.color = colour.toArgb()
        canvas.drawArc(rect, 135f, 270f * f, false, paint)
    }
    marker?.let { m ->
        val angle = Math.toRadians((135f + 270f * m.coerceIn(0f, 1f)).toDouble())
        val r = (sizePx - 2 * inset) / 2f
        val cx = sizePx / 2f + (r * Math.cos(angle)).toFloat()
        val cy = sizePx / 2f + (r * Math.sin(angle)).toFloat()
        canvas.drawCircle(cx, cy, width * 0.62f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE })
    }
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

fun WidgetTile.action(): Action = serviceAction(domain, service, entity, data)

/**
 * A widget button press: calls the service (and any calls that must follow it, kept in `__then`, such as Eco after Heat),
 * then redraws the widgets once the house has caught up.
 */
class ServiceCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val domain = parameters[WidgetKeys.DOMAIN] ?: return
        val service = parameters[WidgetKeys.SERVICE] ?: return
        val entity = parameters[WidgetKeys.ENTITY] ?: return
        val extra = runCatching { JSONObject(parameters[WidgetKeys.DATA] ?: "{}") }.getOrDefault(JSONObject())
        val then = extra.optJSONArray("__then") ?: JSONArray()
        extra.remove("__then")
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            WidgetSource.callService(context, domain, service, entity, extra)
        }
        for (i in 0 until then.length()) {
            val step = then.optJSONObject(i) ?: continue
            // Eco only takes hold once the thermostat is on: give it a moment.
            delay(1_500)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                WidgetSource.callService(context, domain, step.optString("service"), entity, JSONObject().put(step.optString("key"), step.opt("value")))
            }
        }
        delay(1_000)
        // Widgets still drawn from an earlier reading read again; those with no drawing in progress start one.
        WidgetTicks.bumpAll()
        for (w in allGlanceWidgets()) {
            w.updateAll(context)
        }
    }
}

fun cp(c: Color) = ColorProvider(c)

/** The page of the app a widget opens, by the widget's receiver name; the app's [com.churchdrive.app.ui.Page] names. */
object WidgetPages {
    private val BY_WIDGET = mapOf(
        "Alarm" to "Security", "Security" to "Security", "Camera" to "Security",
        "Lights" to "Lighting",
        "Climate" to "Climate", "Weather" to "Climate", "Gauge" to "Climate", "Cluster" to "Climate",
        // Widgets that are all buttons open nothing when the background is tapped.
        "Fan" to "", "Blinds" to "", "Scenes" to "", "Shortcuts" to "",
        "Vacuum" to "Cleaning", "Todo" to "Todo", "Jobs" to "Todo",
        "People" to "Home", "Charger" to "Home",
    )

    fun of(receiverName: String?): String? = receiverName?.substringAfterLast('.')?.removeSuffix("WidgetReceiver")?.let { BY_WIDGET[it] }

    /** Opens the app on [page]. The address makes each page's intent its own (else Android would share one between widgets). */
    fun intent(context: Context, page: String?, camera: String? = null): Intent = Intent(context, MainActivity::class.java).apply {
        if (!page.isNullOrEmpty()) {
            putExtra("page", page)
            data = android.net.Uri.parse("churchdrive://open/$page" + (camera?.let { "/$it" } ?: ""))
        }
        if (camera != null) putExtra("camera", camera)
    }
}

/** The page the widget being drawn belongs to, found from its provider. */
@Composable
fun widgetPage(): String? {
    val context = LocalContext.current
    val id = androidx.glance.LocalGlanceId.current
    return runCatching {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        WidgetPages.of(android.appwidget.AppWidgetManager.getInstance(context).getAppWidgetInfo(appWidgetId)?.provider?.className)
    }.getOrNull()
}

/** The widget's card: the phone's surface colour with a little see-through, 28 dp corners; tapping the background opens the app on its page. */
@Composable
fun WidgetCard(p: WidgetPalette, round: Boolean = false, padding: Dp = 14.dp, top: Boolean = false, camera: String? = null, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val page = widgetPage()
    val base = GlanceModifier.fillMaxSize().cornerRadius(if (round) 999.dp else 28.dp).background(cp(p.surface)).padding(padding)
    Column(
        // A page of "" means the widget is all buttons: tapping its background does nothing.
        if (page == "" && camera == null) base else base.clickable(actionStartActivity(WidgetPages.intent(context, page, camera))),
        verticalAlignment = if (top) Alignment.Top else Alignment.CenterVertically,
        horizontalAlignment = if (round) Alignment.CenterHorizontally else Alignment.Start,
    ) { content() }
}

/** A true circle card: as wide as the widget's shorter side, centred, whatever shape the widget has been made. */
@Composable
fun WidgetCircle(p: WidgetPalette, size: DpSize, content: @Composable (Dp) -> Unit) {
    val context = LocalContext.current
    val page = widgetPage()
    val d = if (size.width < size.height) size.width else size.height
    Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val disc = GlanceModifier.size(d).cornerRadius(d / 2).background(cp(p.surface))
        Box(if (page == "") disc else disc.clickable(actionStartActivity(WidgetPages.intent(context, page))), contentAlignment = Alignment.Center) { content(d) }
    }
}

/** Draws the widgets again in [seconds] seconds, for something that changes by itself (the alarm's countdown). */
fun refreshSoon(context: Context, receiver: Class<*>, seconds: Int) {
    runCatching {
        val ids = android.appwidget.AppWidgetManager.getInstance(context).getAppWidgetIds(android.content.ComponentName(context, receiver))
        if (ids.isEmpty()) return
        val intent = Intent(android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE).setClass(context, receiver)
            .putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        val pending = android.app.PendingIntent.getBroadcast(
            context, receiver.name.hashCode(), intent, android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        context.getSystemService(android.app.AlarmManager::class.java).set(android.app.AlarmManager.RTC, System.currentTimeMillis() + seconds * 1000L, pending)
    }
}

/** Shown when the house can't be read, or what the widget is for is missing. */
@Composable
fun Unreachable(title: String, p: WidgetPalette, why: String) {
    WidgetCard(p) {
        Text(title, style = TextStyle(color = cp(p.onSurface), fontSize = 18.sp, fontWeight = FontWeight.Bold))
        Text(why, style = TextStyle(color = cp(p.muted), fontSize = 12.sp))
    }
}

/** An icon in a circle tinted with the section's colour. */
@Composable
fun IconCircle(icon: String, tone: ToneColors, size: Dp = 44.dp) {
    Box(GlanceModifier.size(size).cornerRadius(size / 2).background(cp(tone.accent.copy(alpha = 0.20f))), contentAlignment = Alignment.Center) {
        iconBitmap(icon, tone.accent)?.let { Image(ImageProvider(it), null, GlanceModifier.size(size * 0.58f)) }
    }
}

/** A round button with an icon: filled with [fill] when it is the current choice, the tile colour otherwise. */
@Composable
fun IconButton(icon: String, p: WidgetPalette, action: Action, size: Dp = 36.dp, fill: ToneColors? = null) {
    Box(
        GlanceModifier.size(size).cornerRadius(size / 2).background(cp(fill?.accent ?: p.tile)).clickable(action),
        contentAlignment = Alignment.Center,
    ) {
        iconBitmap(icon, fill?.onAccent ?: p.onSurface)?.let { Image(ImageProvider(it), null, GlanceModifier.size(size * 0.5f)) }
    }
}

/** The heading row: an icon circle, the title, and a line under it in the section's colour; [trailing] goes at the end. */
@Composable
fun HeaderRow(icon: String, title: String, subtitle: String, p: WidgetPalette, tone: ToneColors, trailing: @Composable () -> Unit = {}) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconCircle(icon, tone)
        Spacer(GlanceModifier.width(12.dp))
        Column(GlanceModifier.defaultWeight()) {
            Text(title, style = TextStyle(color = cp(p.onSurface), fontSize = 18.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            if (subtitle.isNotBlank()) Text(subtitle, style = TextStyle(color = cp(tone.accent), fontSize = 13.sp, fontWeight = FontWeight.Medium), maxLines = 1)
        }
        trailing()
    }
}

/** Buttons filling the width, as the app's tiles do (icon over label); the chosen one fills with [tone]. Up to [maxRows] rows of [perRow]. */
@Composable
fun TileButtons(tiles: List<WidgetTile>, p: WidgetPalette, tone: ToneColors, perRow: Int = 4, maxRows: Int = 2, showLabels: Boolean = true) {
    val shown = tiles.take(perRow * maxRows)
    var i = 0
    Column(GlanceModifier.fillMaxWidth()) {
        tileRowSizes(shown.size, perRow).forEachIndexed { r, size ->
            if (r > 0) Spacer(GlanceModifier.height(8.dp))
            Row(GlanceModifier.fillMaxWidth()) {
                repeat(size) { n ->
                    val tile = shown[i++]
                    if (n > 0) Spacer(GlanceModifier.width(8.dp))
                    val fg = if (tile.selected) tone.onAccent else p.onSurface
                    Column(
                        GlanceModifier.defaultWeight().height(Ui.TileHeight)
                            .cornerRadius(16.dp)
                            .background(cp(if (tile.selected) tone.accent else p.tile))
                            .clickable(tile.action()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        iconBitmap(tile.icon, fg)?.let { Image(ImageProvider(it), null, GlanceModifier.size(20.dp)) }
                        if (showLabels) Text(tile.label, style = TextStyle(color = cp(fg), fontSize = 11.sp, textAlign = TextAlign.Center), maxLines = 1)
                    }
                }
            }
        }
    }
}

/** A row of round icon-only buttons (the strip layouts). */
@Composable
fun IconRow(tiles: List<WidgetTile>, p: WidgetPalette, tone: ToneColors, size: Dp = 36.dp) {
    tiles.forEach { tile ->
        Spacer(GlanceModifier.width(6.dp))
        IconButton(tile.icon, p, tile.action(), size, if (tile.selected) tone else null)
    }
}

/** A small reading: a value over its label, on a tile. */
@Composable
fun StatTile(stat: Stat, p: WidgetPalette, modifier: GlanceModifier = GlanceModifier, big: Boolean = true) {
    val tone = p.tone(stat.tone)
    Column(
        modifier.cornerRadius(16.dp).background(cp(p.tile)).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stat.value, style = TextStyle(color = cp(tone.accent), fontSize = if (big) 20.sp else 16.sp, fontWeight = FontWeight.Bold), maxLines = 1)
        Text(stat.label, style = TextStyle(color = cp(p.muted), fontSize = 11.sp), maxLines = 1)
    }
}


// ---------------------------------------------------------------------------------------------- Reading the house again

/** What a widget drew from: the house, the person's choices for this widget, a picture if it asked for one. */
class Loaded(val data: WidgetData?, val config: JSONObject, val picture: Bitmap?)

/**
 * A nudge for each widget, in this process: when it changes the widget reads its choices and the house again. Android
 * can hand an update to a drawing already in progress without running it afresh, so changes of choice or state go
 * through here as well as through Android's own update.
 */
object WidgetTicks {
    private val flows = java.util.concurrent.ConcurrentHashMap<Int, MutableStateFlow<Long>>()
    fun of(id: Int): MutableStateFlow<Long> = flows.getOrPut(id) { MutableStateFlow(0L) }
    fun bump(id: Int) { of(id).value = System.nanoTime() }
    fun bumpAll() { flows.values.forEach { it.value = System.nanoTime() } }
}

/** Draws a widget from what [Loaded] reads (its choices, then the house), and again each time the widget is nudged. */
suspend fun GlanceAppWidget.provideLive(
    context: Context,
    id: GlanceId,
    asks: (Context, JSONObject) -> List<Ask> = { _, _ -> emptyList() },
    picture: (Context, WidgetData, JSONObject) -> Bitmap? = { _, _, _ -> null },
    onLoaded: (Context, Loaded) -> Unit = { _, _ -> },
    content: @Composable (Loaded) -> Unit,
) {
    WidgetMdi.init(context)
    val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
    provideContent {
        val tick by WidgetTicks.of(appWidgetId).collectAsState()
        var loaded by remember { mutableStateOf<Loaded?>(null) }
        LaunchedEffect(tick) {
            val config = WidgetConfig.get(context, appWidgetId)
            val data = WidgetSource.load(context, asks(context, config))
            val shot = data?.let { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { picture(context, it, config) } }
            val result = Loaded(data, config, shot)
            onLoaded(context, result)
            loaded = result
        }
        loaded?.let { content(it) } ?: LoadingCard()
    }
}

@Composable
fun LoadingCard() {
    val p = WidgetPalette.of(LocalContext.current)
    WidgetCard(p) { Text("Loading the house…", style = TextStyle(color = cp(p.muted), fontSize = 13.sp)) }
}

/** Every widget's receiver: nudges the widget to read afresh when Android asks it to update. */
abstract class LiveReceiver : androidx.glance.appwidget.GlanceAppWidgetReceiver() {
    override fun onUpdate(context: Context, appWidgetManager: android.appwidget.AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { WidgetTicks.bump(it) }
        super.onUpdate(context, appWidgetManager, appWidgetIds)
    }
}

/**
 * Rows and buttons per row that show all [count] buttons: as many rows as fit under a heading in [height] dp (the card's
 * padding, the heading and a gap taken off), with more to a row (up to [maxPerRow]) and then more rows when they don't all fit.
 * Buttons are never left out.
 */
fun gridFor(count: Int, height: Float, tile: Float, minPerRow: Int, maxPerRow: Int): Pair<Int, Int> {
    val fit = (((height - 28f - 44f - 12f) + 8f) / (tile + 8f)).toInt().coerceAtLeast(1)
    val perRow = maxOf(minPerRow, (count + fit - 1) / fit).coerceAtMost(maxPerRow)
    val rows = maxOf(fit, (count + perRow - 1) / perRow)
    return rows to perRow
}

/** The size of round buttons when [count] of them share [room] dp: as big as 36, shrinking to 28 but no further. */
fun roundButtonSize(count: Int, room: Float): Float = ((room - 6f * count) / count.coerceAtLeast(1)).coerceIn(28f, 36f)
