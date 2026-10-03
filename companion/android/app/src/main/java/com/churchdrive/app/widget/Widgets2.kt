package com.churchdrive.app.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
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
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
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
import com.churchdrive.app.Session
import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ha.data
import com.churchdrive.app.ui.Tone
import com.churchdrive.app.ui.ToneColors
import com.churchdrive.app.ui.myTodoList
import com.churchdrive.app.ui.parseForecast
import com.churchdrive.app.ui.parseTodoItems
import com.churchdrive.app.ui.sceneSwatch
import com.churchdrive.app.ui.VACUUM_ENTITY
import com.churchdrive.app.ui.weatherIcon
import com.churchdrive.app.ui.presetLabel
import org.json.JSONObject

/** The widget's own id as Android knows it (its saved choices are kept under it). */
private suspend fun widgetId(context: Context, id: GlanceId): Int = GlanceAppWidgetManager(context).getAppWidgetId(id)

/** What a widget has to hand when it draws: the house, the person's choices, a picture if it asked for one. */
class WidgetScene(val data: WidgetData, val config: JSONObject, val p: WidgetPalette, val size: SizeClass, val picture: Bitmap?, val me: String?, val width: androidx.compose.ui.unit.Dp = 250.dp, val height: androidx.compose.ui.unit.Dp = 110.dp) {
    fun entity(key: String): String = config.optString(key)
}

/** The common shape of the widgets: read the house once, then draw in whichever of the four sizes the widget has been made. */
abstract class SceneWidget(private val title: String) : GlanceAppWidget() {
    // Exact, so a thin card can tell how much room its buttons really have.
    override val sizeMode: SizeMode = SizeMode.Exact

    open fun asks(context: Context, config: JSONObject): List<Ask> = emptyList()

    /** A picture to draw, fetched while the house is read (the camera's). */
    open fun picture(context: Context, data: WidgetData, config: JSONObject): Bitmap? = null

    @Composable
    abstract fun Draw(s: WidgetScene)

    override suspend fun provideGlance(context: Context, id: GlanceId) = provideLive(
        context, id,
        asks = { c, config -> asks(c, config) },
        picture = { c, data, config -> picture(c, data, config) },
    ) { l ->
        val p = WidgetPalette.of(androidx.glance.LocalContext.current)
        val me = Session(androidx.glance.LocalContext.current).personName
        val data = l.data
        if (data == null) Unreachable(title, p, "Can't reach the house")
        else Draw(WidgetScene(data, l.config, p, LocalSize.current.sizeClass(), l.picture, me, LocalSize.current.width, LocalSize.current.height))
    }
}

// ---------------------------------------------------------------------------------------------- Drawing helpers

private fun title(p: WidgetPalette, size: Int = 16) = TextStyle(color = cp(p.onSurface), fontSize = size.sp, fontWeight = FontWeight.Bold)
private fun line(color: Color, size: Int = 12) = TextStyle(color = cp(color), fontSize = size.sp, fontWeight = FontWeight.Medium)

/** A card of an icon, a title, a line under it, and buttons: a strip, a square with a few round buttons, or the full tiles. */
@Composable
fun CardContent(card: Card, p: WidgetPalette, size: SizeClass, width: androidx.compose.ui.unit.Dp = 250.dp, height: androidx.compose.ui.unit.Dp = 110.dp) {
    val tone = p.tone(card.tone)
    when (size) {
        SizeClass.Strip -> WidgetCard(p, padding = 10.dp) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconCircle(card.icon, tone, 40.dp)
                Spacer(GlanceModifier.width(10.dp))
                Column(GlanceModifier.defaultWeight()) {
                    Text(card.title, style = title(p), maxLines = 1)
                    Text(card.sub, style = line(tone.accent), maxLines = 1)
                }
                StripButtons(card.tiles, p, tone, width.value - 20f - 40f - 10f - 110f)
            }
        }
        SizeClass.Square -> WidgetCard(p, padding = 12.dp) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { IconCircle(card.icon, tone, 40.dp) }
            Spacer(GlanceModifier.height(6.dp))
            Text(card.title, style = title(p), maxLines = 1)
            Text(card.sub, style = line(tone.accent), maxLines = 1)
            Spacer(GlanceModifier.height(8.dp))
            // Every button, in as many rows as it takes.
            val perRow = ((width.value - 24f + 6f) / 36f).toInt().coerceAtLeast(2)
            card.tiles.chunked(perRow).forEachIndexed { r, rowTiles ->
                if (r > 0) Spacer(GlanceModifier.height(6.dp))
                Row(GlanceModifier.fillMaxWidth()) {
                    rowTiles.forEachIndexed { i, t ->
                        if (i > 0) Spacer(GlanceModifier.width(6.dp))
                        IconButton(t.icon, p, t.action(), 30.dp, if (t.selected) tone else null)
                    }
                }
            }
        }
        else -> WidgetCard(p) {
            HeaderRow(card.icon, card.title, card.sub, p, tone)
            Spacer(GlanceModifier.height(12.dp))
            val (rows, perRow) = gridFor(card.tiles.size, height.value, 48f, 4, 6)
            TileButtons(card.tiles, p, tone, perRow = perRow, maxRows = rows)
        }
    }
}

/** The buttons of a thin card: each with its icon and name when they fit in [room] dp, else round icon-only buttons. */
@Composable
private fun StripButtons(tiles: List<WidgetTile>, p: WidgetPalette, tone: ToneColors, room: Float) {
    val need = tiles.sumOf { 34 + 7 * it.label.length } + 6 * (tiles.size - 1)
    if (tiles.size in 1..3 && need <= room) {
        tiles.forEach { t ->
            Spacer(GlanceModifier.width(6.dp))
            val fg = if (t.selected) tone.onAccent else p.onSurface
            Row(
                GlanceModifier.height(40.dp).cornerRadius(14.dp).background(cp(if (t.selected) tone.accent else p.tile)).padding(horizontal = 10.dp).clickable(t.action()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                iconBitmap(t.icon, fg)?.let { Image(ImageProvider(it), null, GlanceModifier.size(18.dp)) }
                Spacer(GlanceModifier.width(4.dp))
                Text(t.label, style = TextStyle(color = cp(fg), fontSize = 12.sp, fontWeight = FontWeight.Medium), maxLines = 1)
            }
        }
    } else IconRow(tiles, p, tone, roundButtonSize(tiles.size, room + 20f).dp)
}

/** Reads like a sentence under a title, for the plain pill widgets (people, doors, doorbell). */
@Composable
private fun Pill(icon: String, heading: String, text: String, tone: ToneColors, p: WidgetPalette, size: SizeClass, camera: String? = null, extra: @Composable () -> Unit = {}) {
    if (size == SizeClass.Strip || size == SizeClass.Wide) {
        WidgetCard(p, padding = 12.dp, camera = camera) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconCircle(icon, tone, 40.dp)
                Spacer(GlanceModifier.width(10.dp))
                Column(GlanceModifier.defaultWeight()) {
                    Text(heading, style = title(p), maxLines = 1)
                    Text(text, style = line(tone.accent), maxLines = if (size == SizeClass.Wide) 2 else 1)
                }
            }
            if (size == SizeClass.Wide) extra()
        }
    } else WidgetCard(p, padding = 12.dp, camera = camera) {
        IconCircle(icon, tone, 40.dp)
        Spacer(GlanceModifier.height(6.dp))
        Text(heading, style = title(p), maxLines = 1)
        Text(text, style = line(tone.accent), maxLines = 3)
        extra()
    }
}

// ---------------------------------------------------------------------------------------------- People, doors, activity

class PeopleGlanceWidget : SceneWidget("People") {
    @Composable
    override fun Draw(s: WidgetScene) {
        val people = peopleOf(s.data.entities)
        val p = s.p
        val home = people.count { it.home }
        val tone = p.tone(if (home > 0) Tone.Green else Tone.Grey)
        if (s.size == SizeClass.Strip || s.size == SizeClass.Wide) {
            WidgetCard(p, padding = 10.dp) {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    people.take(5).forEach { who ->
                        val t = p.tone(if (who.home) Tone.Green else Tone.Grey)
                        Column(GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(GlanceModifier.size(38.dp).cornerRadius(19.dp).background(cp(t.accent.copy(alpha = 0.25f))), contentAlignment = Alignment.Center) {
                                Text(who.initial, style = TextStyle(color = cp(t.accent), fontSize = 17.sp, fontWeight = FontWeight.Bold))
                            }
                            Text(who.name, style = TextStyle(color = cp(p.onSurface), fontSize = 12.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                            Text(who.where, style = TextStyle(color = cp(t.accent), fontSize = 11.sp), maxLines = 1)
                        }
                    }
                    if (people.isEmpty()) Text("No people set up", style = line(p.muted))
                }
            }
        } else WidgetCard(p, padding = 12.dp) {
            HeaderRow("mdi:account-group", "People", "$home home", p, tone)
            Spacer(GlanceModifier.height(8.dp))
            people.take(if (s.size == SizeClass.Tall) 6 else 3).forEach { who ->
                val t = p.tone(if (who.home) Tone.Green else Tone.Grey)
                Row(GlanceModifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(who.name, GlanceModifier.defaultWeight(), style = TextStyle(color = cp(p.onSurface), fontSize = 13.sp), maxLines = 1)
                    Text(who.where, style = line(t.accent, 13), maxLines = 1)
                }
            }
        }
    }
}

class DoorsGlanceWidget : SceneWidget("Doors") {
    @Composable
    override fun Draw(s: WidgetScene) {
        val d = doorsStatus(s.data.entities)
        val tone = s.p.tone(d.tone)
        Pill(if (d.open.isEmpty()) "mdi:door-closed" else "mdi:door-open", "Doors and windows", d.line, tone, s.p, s.size) {
            if (d.open.size > 1) Text(d.open.take(4).joinToString(", "), style = TextStyle(color = cp(s.p.muted), fontSize = 12.sp), maxLines = 2)
        }
    }
}

class ActivityGlanceWidget : SceneWidget("Doorbell") {
    @Composable
    override fun Draw(s: WidgetScene) {
        val base = s.entity("base").ifBlank { activityBases(s.data.entities).firstOrNull().orEmpty() }
        val (text, t) = lastActivity(base, s.data.entities)
        val name = s.data.entities["camera.${base}_live_view"]?.friendlyName?.removeSuffix(" live view")?.removeSuffix(" Live view")
            ?: presetLabel(base).ifBlank { "Doorbell" }
        Pill(if (text.startsWith("Doorbell")) "mdi:doorbell" else "mdi:motion-sensor", name, text, s.p.tone(t), s.p, s.size, camera = "camera.${base}_live_view".takeIf { it in s.data.entities })
    }
}

// ---------------------------------------------------------------------------------------------- Weather

class WeatherGlanceWidget : SceneWidget("Weather") {
    override fun asks(context: Context, config: JSONObject) = listOf<Ask>(
        Ask.FirstEvent("forecast", "weather/subscribe_forecast") { entities ->
            entities.keys.firstOrNull { it.startsWith("weather.") }?.let { data("entity_id" to it, "forecast_type" to "daily") }
        },
    )

    @Composable
    override fun Draw(s: WidgetScene) {
        val weather = s.data.entities.values.firstOrNull { it.entityId.startsWith("weather.") }
        val p = s.p
        val blue = p.tone(Tone.Blue)
        if (weather == null) return Unreachable("Weather", p, "No weather in the house")
        val temp = weather.num("temperature")?.let { "${Math.round(it)}°" } ?: "–"
        val days = (s.data.extras["forecast"] as? JSONObject)?.let { forecastDays(parseForecast(it), max = if (s.size == SizeClass.Tall) 5 else 4) }.orEmpty()
        val now = presetLabel(weather.state.replace('-', ' '))
        when (s.size) {
            SizeClass.Strip -> Pill(weatherIcon(weather.state), "$temp · $now", days.firstOrNull()?.let { "Today ${it.temp}" } ?: "", blue, p, s.size)
            SizeClass.Square -> Pill(weatherIcon(weather.state), temp, now, blue, p, s.size)
            else -> WidgetCard(p, padding = 12.dp) {
                HeaderRow(weatherIcon(weather.state), temp, now, p, blue)
                Spacer(GlanceModifier.height(10.dp))
                Row(GlanceModifier.fillMaxWidth()) {
                    days.forEachIndexed { i, d ->
                        if (i > 0) Spacer(GlanceModifier.width(6.dp))
                        Column(
                            GlanceModifier.defaultWeight().cornerRadius(16.dp).background(cp(p.tile)).padding(vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(d.label, style = TextStyle(color = cp(p.muted), fontSize = 11.sp), maxLines = 1)
                            iconBitmap(weatherIcon(d.condition), blue.accent)?.let { Image(ImageProvider(it), null, GlanceModifier.size(22.dp)) }
                            Text(d.temp, style = TextStyle(color = cp(p.onSurface), fontSize = 14.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------- To-do and jobs

/** A to-do list's open tasks; [fixedList] makes it the house's jobs rather than a list the person chose (theirs by default). */
open class TodoGlanceWidget(private val heading: String, private val fixedList: String?) : SceneWidget(heading) {
    private fun listOf(context: Context, config: JSONObject): String =
        fixedList ?: config.optString("list").ifBlank { myTodoList(Session(context).personName) ?: "todo.priorities_automatic" }

    override fun asks(context: Context, config: JSONObject) =
        listOf<Ask>(Ask.Request("items", "todo/item/list", data("entity_id" to listOf(context, config))))

    @Composable
    override fun Draw(s: WidgetScene) {
        val items = todoRows(parseTodoItems(s.data.extras["items"]), 20)
        val p = s.p
        val tone = p.tone(if (items.isEmpty()) Tone.Green else Tone.Blue)
        val icon = if (fixedList == null) "mdi:format-list-checks" else "mdi:broom"
        val sub = if (items.isEmpty()) "All done" else "${items.size} to do"
        when (s.size) {
            SizeClass.Strip -> WidgetCard(p, padding = 10.dp) {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconCircle(icon, tone, 40.dp)
                    Spacer(GlanceModifier.width(10.dp))
                    Column(GlanceModifier.defaultWeight()) {
                        Text(items.firstOrNull()?.summary ?: heading, style = title(p), maxLines = 1)
                        Text(if (items.size > 1) "$sub · next: ${items.first().line.substringBefore(" · ")}" else sub, style = line(tone.accent), maxLines = 1)
                    }
                }
            }
            SizeClass.Square -> WidgetCard(p, padding = 12.dp) {
                IconCircle(icon, tone, 40.dp)
                Spacer(GlanceModifier.height(6.dp))
                Text(heading, style = title(p), maxLines = 1)
                Text(sub, style = line(tone.accent), maxLines = 1)
                items.firstOrNull()?.let { Text(it.summary, style = TextStyle(color = cp(p.muted), fontSize = 12.sp), maxLines = 2) }
            }
            else -> WidgetCard(p, padding = 12.dp, top = true) {
                HeaderRow(icon, heading, sub, p, tone)
                Spacer(GlanceModifier.height(6.dp))
                // Each task on two lines: its name, then when it is due and what repeats.
                items.take(if (s.size == SizeClass.Tall) 6 else 2).forEach { row ->
                    Column(GlanceModifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Text(row.summary, style = TextStyle(color = cp(p.onSurface), fontSize = 14.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                        if (row.line.isNotBlank()) Text(row.line, style = TextStyle(color = cp(p.muted), fontSize = 11.sp), maxLines = 1)
                    }
                }
            }
        }
    }
}

/** Everything waiting for the signed-in person across the To-do page's categories, each in its colour. No setup. */
class MyTodoGlanceWidget : SceneWidget("To-do") {
    override fun asks(context: Context, config: JSONObject) =
        TodoLists.lists(Session(context).personName).map { (_, list) -> Ask.Request("todo:$list", "todo/item/list", data("entity_id" to list)) }

    @Composable
    override fun Draw(s: WidgetScene) {
        val results = TodoLists.lists(s.me).associate { (_, list) -> list to parseTodoItems(s.data.extras["todo:$list"]) }
        val entries = TodoLists.entries(s.me, results, TodoLists.tones(s.data.panels))
        val p = s.p
        val tone = p.tone(if (entries.isEmpty()) Tone.Green else Tone.Purple)
        val sub = if (entries.isEmpty()) "All done" else "${entries.size} to do"
        val icon = "mdi:format-list-checks"
        when (s.size) {
            SizeClass.Strip -> WidgetCard(p, padding = 10.dp) {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconCircle(icon, tone, 40.dp)
                    Spacer(GlanceModifier.width(10.dp))
                    Column(GlanceModifier.defaultWeight()) {
                        Text(entries.firstOrNull()?.summary ?: "To-do", style = title(p), maxLines = 1)
                        Text(if (entries.size > 1) "$sub · next: ${entries.first().category}" else sub, style = line(p.tone(entries.firstOrNull()?.tone ?: Tone.Green).accent), maxLines = 1)
                    }
                }
            }
            SizeClass.Square -> WidgetCard(p, padding = 12.dp) {
                IconCircle(icon, tone, 40.dp)
                Spacer(GlanceModifier.height(6.dp))
                Text("To-do", style = title(p), maxLines = 1)
                Text(sub, style = line(tone.accent), maxLines = 1)
                entries.firstOrNull()?.let { Text(it.summary, style = TextStyle(color = cp(p.muted), fontSize = 12.sp), maxLines = 2) }
            }
            else -> WidgetCard(p, padding = 12.dp, top = true) {
                HeaderRow(icon, "To-do", sub, p, tone)
                Spacer(GlanceModifier.height(6.dp))
                // Each task on two lines, with a bar in its category's colour: its name, then its category and when it is due.
                val rows = (((s.height.value - 24f - 44f - 6f) / 40f).toInt()).coerceIn(1, 12)
                entries.take(rows).forEach { e ->
                    val t = p.tone(e.tone)
                    Row(GlanceModifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(GlanceModifier.width(4.dp).height(32.dp).cornerRadius(2.dp).background(cp(t.accent))) {}
                        Spacer(GlanceModifier.width(8.dp))
                        Column(GlanceModifier.defaultWeight()) {
                            Text(e.summary, style = TextStyle(color = cp(p.onSurface), fontSize = 14.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                            Text(listOf(e.category, e.line).filter { it.isNotBlank() }.joinToString(" · "), style = TextStyle(color = cp(t.accent), fontSize = 11.sp), maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}
class JobsGlanceWidget : TodoGlanceWidget("Jobs", "todo.cleaning")

// ---------------------------------------------------------------------------------------------- Camera

class CameraGlanceWidget : SceneWidget("Camera") {
    private fun pick(data: WidgetData, config: JSONObject): EntityState? =
        data.entities[config.optString("camera")] ?: data.entities.values.firstOrNull { it.entityId.startsWith("camera.") && it.entityId.endsWith("_live_view") }

    override fun picture(context: Context, data: WidgetData, config: JSONObject): Bitmap? =
        pick(data, config)?.str("entity_picture")?.let { WidgetSource.fetchImage(context, it, 640) }

    @Composable
    override fun Draw(s: WidgetScene) {
        val cam = pick(s.data, s.config)
        val p = s.p
        val shot = s.picture
        if (cam == null || shot == null) {
            return Unreachable(cam?.friendlyName ?: "Camera", p, if (cam == null) "No camera chosen" else "No picture right now")
        }
        WidgetCard(p, padding = 0.dp, camera = cam.entityId) {
            Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.BottomStart) {
                Image(ImageProvider(shot), cam.friendlyName, GlanceModifier.fillMaxSize().cornerRadius(28.dp), contentScale = ContentScale.Crop)
                Box(GlanceModifier.padding(12.dp)) {
                    Text(
                        cam.friendlyName.removeSuffix(" live view").removeSuffix(" Live view"),
                        modifier = GlanceModifier.cornerRadius(12.dp).background(cp(Color(0x99000000))).padding(horizontal = 8.dp, vertical = 3.dp),
                        style = TextStyle(color = cp(Color.White), fontSize = 12.sp, fontWeight = FontWeight.Medium),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------- Vacuum, charger, fan, purifier, blinds

class VacuumGlanceWidget : SceneWidget("Vacuum") {
    @Composable
    override fun Draw(s: WidgetScene) = CardContent(vacuumCard(s.data.entities), s.p, s.size, s.width, s.height)
}

class ChargerGlanceWidget : SceneWidget("Car charger") {
    @Composable
    override fun Draw(s: WidgetScene) = CardContent(chargerCard(s.data.entities), s.p, s.size, s.width, s.height)
}

/** One card for any air device you pick: a fan, an air purifier or an air conditioner. */
class FanGlanceWidget : SceneWidget("Fan and air") {
    @Composable
    override fun Draw(s: WidgetScene) {
        val id = s.entity("fan").ifBlank { airDevices(s.data.entities).firstOrNull()?.entityId.orEmpty() }
        CardContent(airCard(s.data.entities, id), s.p, s.size, s.width, s.height)
    }
}

class BlindsGlanceWidget : SceneWidget("Blinds") {
    @Composable
    override fun Draw(s: WidgetScene) {
        val id = s.entity("cover").ifBlank { s.data.entities.keys.firstOrNull { it.startsWith("cover.") }.orEmpty() }
        CardContent(coverCard(s.data.entities[id], id), s.p, s.size, s.width, s.height)
    }
}

// ---------------------------------------------------------------------------------------------- Scenes

class ScenesGlanceWidget : SceneWidget("Scenes") {
    @Composable
    override fun Draw(s: WidgetScene) {
        val p = s.p
        val chosen = SceneButtons.chosen(WidgetConfig.strings(s.config, "scenes"), s.data.entities, s.data.lights)
        if (chosen.isEmpty()) return Unreachable("Scenes", p, "Long-press the widget to choose scenes")
        // Every chosen scene: as many to a row as it takes, in as many rows as the widget has room for.
        val (rows, perRow) = gridFor(chosen.size, s.height.value, 48f, if (s.size == SizeClass.Square) 2 else 4, 6)
        WidgetCard(p, padding = 12.dp) {
            SceneGrid(chosen.map { SceneTile(it.tile, it.key?.let { k -> sceneSwatch(k) } ?: p.tone(Tone.Amber).accent) }, perRow, rows)
        }
    }
}

/** A scene button: what it does, and the colour that stands for the scene. */
class SceneTile(val tile: WidgetTile, val colour: Color)

/** Scene buttons in their own colours, as the app draws them; text is dark on a light colour and white on a dark one. */
@Composable
fun SceneGrid(items: List<SceneTile>, perRow: Int, maxRows: Int) {
    val shown = items.take(perRow * maxRows.coerceAtLeast(1))
    var i = 0
    Column(GlanceModifier.fillMaxWidth()) {
        tileRows(shown.size, perRow).forEachIndexed { r, n ->
            if (r > 0) Spacer(GlanceModifier.height(8.dp))
            Row(GlanceModifier.fillMaxWidth()) {
                repeat(n) { c ->
                    val b = shown[i++]
                    if (c > 0) Spacer(GlanceModifier.width(8.dp))
                    val ink = if (b.colour.luminance() > 0.45f) Color(0xFF202124) else Color.White
                    Column(
                        GlanceModifier.defaultWeight().height(com.churchdrive.app.ui.Ui.TileHeight).cornerRadius(16.dp).background(cp(b.colour.copy(alpha = 0.88f))).clickable(b.tile.action()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        iconBitmap("mdi:lightbulb-group", ink)?.let { Image(ImageProvider(it), null, GlanceModifier.size(20.dp)) }
                        Text(b.tile.label, style = TextStyle(color = cp(ink), fontSize = 11.sp, textAlign = TextAlign.Center), maxLines = 1)
                    }
                }
            }
        }
    }
}

/** The widths of rows for [count] buttons, [perRow] to a row, the last row shared out evenly. */
fun tileRows(count: Int, perRow: Int): List<Int> = com.churchdrive.app.ui.tileRowSizes(count, perRow)

// ---------------------------------------------------------------------------------------------- Gauges

/** A ring with the reading in the middle. */
@Composable
private fun Ring(r: GaugeReading, p: WidgetPalette, size: androidx.compose.ui.unit.Dp, valueSize: Int) {
    val tone = p.tone(r.tone)
    Box(GlanceModifier.size(size), contentAlignment = Alignment.Center) {
        Image(ImageProvider(ringBitmap(240, r.fraction, p.tile, tone.accent, r.marker)), null, GlanceModifier.size(size))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(r.value, style = TextStyle(color = cp(p.onSurface), fontSize = valueSize.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            if (size >= 80.dp) Text(r.label, style = TextStyle(color = cp(p.muted), fontSize = 11.sp), maxLines = 1)
        }
    }
}

/** One reading as a ring (square), a pill (strip) or ring and words (wide). The heating gauge also has − and + for its target and the modes. */
class GaugeGlanceWidget : SceneWidget("Gauge") {
    // Exact, so the circle can be as big as the widget really is.
    override val sizeMode = SizeMode.Exact

    @Composable
    override fun Draw(s: WidgetScene) {
        val id = s.entity("reading").ifBlank { "inside" }
        val p = s.p
        val r = Gauges.compute(id, s.data.entities) ?: return Unreachable("Gauge", p, "Nothing to show for this reading")
        val tone = p.tone(r.tone)
        if (id == "heating") {
            val climate = s.data.entities[com.churchdrive.app.ui.CLIMATE_ENTITY]
            val tiles = WidgetModel.climateTiles(climate, com.churchdrive.app.ui.CLIMATE_ENTITY, com.churchdrive.app.ui.DEFAULT_QUICK)
            val target = WidgetModel.nextTarget(climate, -1)?.let { heatingTo(it) }
            val up = WidgetModel.nextTarget(climate, 1)?.let { heatingTo(it) }
            when (s.size) {
                SizeClass.Strip -> WidgetCard(p, padding = 10.dp) {
                    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IconCircle(r.icon, tone, 40.dp)
                        Spacer(GlanceModifier.width(10.dp))
                        Column(GlanceModifier.defaultWeight()) {
                            Text(r.value, style = title(p, 18), maxLines = 1)
                            Text(r.sub, style = line(tone.accent), maxLines = 1)
                        }
                        if (target != null) IconButton("mdi:minus", p, target.action(), 34.dp)
                        Spacer(GlanceModifier.width(6.dp))
                        if (up != null) IconButton("mdi:plus", p, up.action(), 34.dp)
                    }
                }
                SizeClass.Square -> WidgetCard(p, padding = 10.dp) {
                    Ring(r, p, 78.dp, 18)
                    Spacer(GlanceModifier.height(6.dp))
                    Row(GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (target != null) IconButton("mdi:minus", p, target.action(), 30.dp)
                        Spacer(GlanceModifier.width(8.dp))
                        if (up != null) IconButton("mdi:plus", p, up.action(), 30.dp)
                    }
                }
                else -> WidgetCard(p, padding = 12.dp) {
                    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Ring(r, p, 96.dp, 22)
                        Spacer(GlanceModifier.width(12.dp))
                        Column(GlanceModifier.defaultWeight()) {
                            Text(r.sub, style = line(tone.accent, 13), maxLines = 1)
                            Spacer(GlanceModifier.height(6.dp))
                            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                if (target != null) IconButton("mdi:minus", p, target.action(), 36.dp)
                                Spacer(GlanceModifier.width(8.dp))
                                if (up != null) IconButton("mdi:plus", p, up.action(), 36.dp)
                            }
                        }
                    }
                    Spacer(GlanceModifier.height(10.dp))
                    TileButtons(tiles, p, tone, perRow = 4, maxRows = 1)
                }
            }
            return
        }
        when (s.size) {
            SizeClass.Strip -> WidgetCard(p, padding = 10.dp) {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconCircle(r.icon, tone, 40.dp)
                    Spacer(GlanceModifier.width(10.dp))
                    Column(GlanceModifier.defaultWeight()) {
                        Text(r.label, style = title(p), maxLines = 1)
                        Text(listOf(r.value, r.sub).filter { it.isNotBlank() }.joinToString(" · "), style = line(tone.accent), maxLines = 1)
                    }
                }
            }
            SizeClass.Square -> WidgetCircle(p, LocalSize.current) { d -> Ring(r, p, d * 0.84f, ((d.value * 0.84f) * 0.24f).toInt()) }
            else -> WidgetCard(p, padding = 12.dp) {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Ring(r, p, 100.dp, 24)
                    Spacer(GlanceModifier.width(14.dp))
                    Column(GlanceModifier.defaultWeight()) {
                        Text(r.label, style = title(p, 18), maxLines = 1)
                        if (r.sub.isNotBlank()) Text(r.sub, style = line(tone.accent, 13), maxLines = 1)
                    }
                }
            }
        }
    }
}

private fun heatingTo(t: Double) = WidgetTile("mdi:thermostat", "", false, "climate", "set_temperature", com.churchdrive.app.ui.CLIMATE_ENTITY, JSONObject().put("temperature", t).toString())

/** Two to four readings at once: rings across, or a row of tiles on the strip. */
class ClusterGlanceWidget : SceneWidget("Gauges") {
    @Composable
    override fun Draw(s: WidgetScene) {
        val p = s.p
        val readings = Gauges.cluster(WidgetConfig.strings(s.config, "readings"), s.data.entities)
        if (readings.isEmpty()) return Unreachable("Gauges", p, "Long-press the widget to choose readings")
        when (s.size) {
            SizeClass.Strip -> WidgetCard(p, padding = 8.dp) {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    readings.forEachIndexed { i, r ->
                        if (i > 0) Spacer(GlanceModifier.width(6.dp))
                        StatTile(Stat(r.id, r.label, r.value, r.tone, r.icon), p, GlanceModifier.defaultWeight(), big = false)
                    }
                }
            }
            SizeClass.Square -> WidgetCard(p, padding = 10.dp) {
                readings.chunked(2).forEachIndexed { r, pair ->
                    if (r > 0) Spacer(GlanceModifier.height(6.dp))
                    Row(GlanceModifier.fillMaxWidth()) {
                        pair.forEachIndexed { i, g ->
                            if (i > 0) Spacer(GlanceModifier.width(6.dp))
                            StatTile(Stat(g.id, g.label, g.value, g.tone, g.icon), p, GlanceModifier.defaultWeight(), big = false)
                        }
                    }
                }
            }
            else -> WidgetCard(p, padding = 12.dp) {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    readings.forEachIndexed { i, r ->
                        if (i > 0) Spacer(GlanceModifier.width(6.dp))
                        Box(GlanceModifier.defaultWeight(), contentAlignment = Alignment.Center) { Ring(r, p, if (readings.size > 3) 72.dp else 84.dp, 16) }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------- Receivers

private fun forget2(context: Context, ids: IntArray) = WidgetConfig.remove(context, ids)

class PeopleWidgetReceiver : LiveReceiver() { override val glanceAppWidget: GlanceAppWidget = PeopleGlanceWidget() }
class DoorsWidgetReceiver : LiveReceiver() { override val glanceAppWidget: GlanceAppWidget = DoorsGlanceWidget() }
class WeatherWidgetReceiver : LiveReceiver() { override val glanceAppWidget: GlanceAppWidget = WeatherGlanceWidget() }
class JobsWidgetReceiver : LiveReceiver() { override val glanceAppWidget: GlanceAppWidget = JobsGlanceWidget() }
class VacuumWidgetReceiver : LiveReceiver() { override val glanceAppWidget: GlanceAppWidget = VacuumGlanceWidget() }
class ChargerWidgetReceiver : LiveReceiver() { override val glanceAppWidget: GlanceAppWidget = ChargerGlanceWidget() }

class TodoWidgetReceiver : LiveReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MyTodoGlanceWidget()
    override fun onDeleted(context: Context, appWidgetIds: IntArray) { super.onDeleted(context, appWidgetIds); forget2(context, appWidgetIds) }
}
class CameraWidgetReceiver : LiveReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CameraGlanceWidget()
    override fun onDeleted(context: Context, appWidgetIds: IntArray) { super.onDeleted(context, appWidgetIds); forget2(context, appWidgetIds) }
}
class ActivityWidgetReceiver : LiveReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ActivityGlanceWidget()
    override fun onDeleted(context: Context, appWidgetIds: IntArray) { super.onDeleted(context, appWidgetIds); forget2(context, appWidgetIds) }
}
class FanWidgetReceiver : LiveReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FanGlanceWidget()
    override fun onDeleted(context: Context, appWidgetIds: IntArray) { super.onDeleted(context, appWidgetIds); forget2(context, appWidgetIds) }
}
class BlindsWidgetReceiver : LiveReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BlindsGlanceWidget()
    override fun onDeleted(context: Context, appWidgetIds: IntArray) { super.onDeleted(context, appWidgetIds); forget2(context, appWidgetIds) }
}
class ScenesWidgetReceiver : LiveReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ScenesGlanceWidget()
    override fun onDeleted(context: Context, appWidgetIds: IntArray) { super.onDeleted(context, appWidgetIds); forget2(context, appWidgetIds) }
}
class GaugeWidgetReceiver : LiveReceiver() {
    override val glanceAppWidget: GlanceAppWidget = GaugeGlanceWidget()
    override fun onDeleted(context: Context, appWidgetIds: IntArray) { super.onDeleted(context, appWidgetIds); forget2(context, appWidgetIds) }
}
class ClusterWidgetReceiver : LiveReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ClusterGlanceWidget()
    override fun onDeleted(context: Context, appWidgetIds: IntArray) { super.onDeleted(context, appWidgetIds); forget2(context, appWidgetIds) }
}

/** Every widget the app has, to redraw after a button press. */
fun allGlanceWidgets(): List<GlanceAppWidget> = listOf(
    AlarmGlanceWidget(), LightsGlanceWidget(), ClimateGlanceWidget(), SummaryGlanceWidget(), ShortcutsGlanceWidget(),
    ScenesGlanceWidget(), FanGlanceWidget(), BlindsGlanceWidget(), VacuumGlanceWidget(), ChargerGlanceWidget(),
    MyTodoGlanceWidget(), JobsGlanceWidget(), GaugeGlanceWidget(), ClusterGlanceWidget(), DoorsGlanceWidget(),
)
