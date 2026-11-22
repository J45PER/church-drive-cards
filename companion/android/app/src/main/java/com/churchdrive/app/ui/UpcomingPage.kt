package com.churchdrive.app.ui

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.churchdrive.app.ha.EntityState
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime

// Upcoming (the account panel): everything the house does at a set time, in time order, the Activity log's rows for what is still to come. A row's
// colour is the kind of thing and its pill says who set it (Clive, a person, or the device card it was set on). Nothing here switches on or off. Tapping
// a one-off changes or removes it here; anything that repeats opens where it is set (a blind's or the purifier's own page, a light routine in the
// Lights editor, the To-do, Security or Climate page). Administrators can add a one-off with the button.

private sealed interface UpScreen {
    data object Feed : UpScreen
    data object Add : UpScreen
    data class OneOff(val id: String) : UpScreen
    data class Routine(val id: String) : UpScreen
}

private val UpcomingRowShape = RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp, topEnd = 32.dp, bottomEnd = 32.dp)

/**
 * The Upcoming page. [onOpenPage] opens one of the app's pages by name ("todo", "security", "climate") and [onOpenDevice] a device's page; both close
 * the account panel first.
 */
@Composable
fun UpcomingPage(isAdmin: Boolean, entities: Map<String, EntityState>, onOpenPage: (String) -> Unit, onOpenDevice: (String) -> Unit) {
    val api = LocalHaApi.current
    var screen by remember { mutableStateOf<UpScreen>(UpScreen.Feed) }
    var show by remember { mutableStateOf(UpcomingShow.All) }
    var dates by remember { mutableStateOf(UpcomingDates.Week) }
    var who by remember { mutableStateOf<String?>(null) }
    var items by remember { mutableStateOf<List<UpcomingItem>?>(null) }
    var routines by remember { mutableStateOf<List<RoutineInfo>>(emptyList()) }
    var failed by remember { mutableStateOf(false) }
    var changed by remember { mutableIntStateOf(0) }
    LaunchedEffect(api, dates.days, changed) {
        val got = parseUpcoming(api?.ask("church_drive/upcoming", JSONObject().put("days", dates.days)))
        if (got != null) items = got else if (items == null) failed = true
        parseRoutines(api?.ask("church_drive/routines", JSONObject()))?.let { routines = it }
    }
    DisposableEffect(api) {
        val subs = listOf("church_drive_routines_changed", "church_drive_blind_schedule_changed", "church_drive_purifier_schedule_changed", "church_drive_alarm_tests_changed").map {
            api?.subscribe("subscribe_events", JSONObject().put("event_type", it)) { changed++ } ?: -1
        }
        onDispose { subs.forEach { if (it >= 0) api?.close(it) } }
    }
    BackHandler(enabled = screen != UpScreen.Feed) { screen = UpScreen.Feed }
    val back: () -> Unit = { screen = UpScreen.Feed; changed++ }
    when (val s = screen) {
        UpScreen.Add -> OneOffEditor(null, entities, back)
        is UpScreen.OneOff -> {
            val r = routines.firstOrNull { it.id == s.id && !it.repeats }
            if (r != null) OneOffEditor(r, entities, back) else LoadingNote("Loading…")
        }
        is UpScreen.Routine -> {
            val r = routines.firstOrNull { it.id == s.id }
            when {
                r == null -> LoadingNote("Loading…")
                isLightRoutine(r) -> RoutineEditorCard(r, entities, back)
                else -> RoutineDetailsCard(r, back)
            }
        }
        UpScreen.Feed -> {
            val list = items
            val today = LocalDate.now()
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActivityDropdown("Dates", dates.label, UpcomingDates.entries.map { it.label }, Modifier.weight(1f)) { dates = UpcomingDates.entries[it] }
                    ActivityDropdown("Show", show.label, UpcomingShow.entries.map { it.label }, Modifier.weight(1f)) { show = UpcomingShow.entries[it] }
                }
                val people = upcomingPeople(list.orEmpty())
                ActivityDropdown("Who set it", who ?: "Everyone", listOf("Everyone") + people, Modifier.fillMaxWidth()) { who = if (it == 0) null else people[it - 1] }
                when {
                    list == null -> LoadingNote(if (failed) "Home Assistant didn't answer: the house may need updating." else "Loading what is coming up…")
                    else -> {
                        val shown = upcomingFilter(list, show, dates, who, today)
                        Text(
                            if (shown.isEmpty()) "Nothing is coming up with these choices." else "${shown.size} coming up, ${dates.label.replaceFirstChar { it.lowercase() }}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        upcomingDays(shown, today).forEach { (day, rows) ->
                            Text(day, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                            rows.forEach { item ->
                                UpcomingRow(item) {
                                    when (item.tapTo) {
                                        "oneoff" -> if (isAdmin) screen = UpScreen.OneOff(item.tapId)
                                        "routine" -> if (isAdmin) screen = UpScreen.Routine(item.tapId)
                                        "device" -> onOpenDevice(item.tapEntity)
                                        "page" -> onOpenPage(item.tapPage)
                                    }
                                }
                            }
                        }
                        if (isAdmin) {
                            val tone = toneColors(Tone.Grey)
                            TileRow(listOf(TileItem("cd:plus", "Add a one-off", false) { screen = UpScreen.Add }), tone, tone.onContainer)
                        }
                    }
                }
            }
        }
    }
}

/**
 * One line of Upcoming, drawn as the Activity log draws one: a 64 dp row whose ground follows the kind of thing (blinds blue, lights yellow, air green...),
 * a plain icon, the title over the time, and on the right a pill cut into the row with who set it.
 */
@Composable
private fun UpcomingRow(item: UpcomingItem, onClick: () -> Unit) {
    val colours = toneColorsFor(upcomingTone(item.kind), true)
    val start = lerp(colours.container, colours.accent, 0.3f)
    val end = colours.accent
    val ground = Brush.horizontalGradient(listOf(start, end))
    val dark = CliveColours.NeutralDark.background
    val ink = if ((start.luminance() + end.luminance()) / 2f > 0.45f) dark else Color.White
    val clive = item.src == "clive"
    val person = if (item.src == "user") activityPersonTone(item.by)?.let { toneColorsFor(it, true) } else null
    val pillColour = person?.accent ?: if (clive) AssistClassicPalette.first else toneColorsFor(Tone.Grey, true).container
    val pillBrush: Brush = if (clive) AssistClassicPalette.across else SolidColor(pillColour)
    val pillInk = if (clive || pillColour.luminance() < 0.42f) Color.White else dark
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val pillWidth: Dp = maxOf(150.dp, maxWidth / 6)
        Row(
            Modifier.fillMaxWidth().height(Ui.TallTileHeight).clip(UpcomingRowShape).background(ground).glowBehind(end).clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.width(20.dp))
            HaIcon(upcomingIcon(item.kind), CdVectors.History, ink, 24.dp)
            Column(Modifier.weight(1f).padding(horizontal = 14.dp), verticalArrangement = Arrangement.Center) {
                Text(item.title, style = MaterialTheme.typography.titleMedium, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(upcomingDetail(item), style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(
                Modifier.width(pillWidth).fillMaxHeight().clip(RoundedCornerShape(32.dp)).background(pillBrush).glowBehind(pillColour),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.width(10.dp))
                Box(Modifier.size(42.dp).background(pillInk.copy(alpha = 0.22f), CircleShape), contentAlignment = Alignment.Center) {
                    when {
                        clive -> AssistantLogo(Color.White, 32.dp)
                        item.src == "device" -> HaIcon("cd:sliders", CdVectors.History, pillInk, 24.dp)
                        else -> Text(upcomingWho(item).take(1).uppercase(), color = pillInk, style = MaterialTheme.typography.titleMedium)
                    }
                }
                Spacer(Modifier.width(10.dp))
                Text(upcomingWho(item), color = pillInk, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(end = 12.dp))
            }
        }
    }
}

/** What a device is, for its colour: a blind, a light, a fan or a plug. */
private fun deviceKind(entity: String): String = when (entity.substringBefore('.')) {
    "cover" -> "blind"
    "light" -> "light"
    "fan", "humidifier" -> "fan"
    else -> "plug"
}

/** Adding a one-off, or opening one ([existing]): the device, what it does, the day and the time, in the colour of the device. */
@Composable
private fun OneOffEditor(existing: RoutineInfo?, entities: Map<String, EntityState>, onDone: () -> Unit) {
    val api = LocalHaApi.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val devices = remember(entities) { schedulableDevices(entities) }
    val names = devices.toMap()
    val today = LocalDate.now()
    val startDate = existing?.onceAt?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: today.plusDays(1)
    var device by remember(existing?.id) { mutableStateOf(existing?.entities?.firstOrNull() ?: devices.firstOrNull()?.first.orEmpty()) }
    var action by remember(existing?.id) { mutableStateOf(existing?.action ?: routineActions(device).first().first) }
    var date by remember(existing?.id) { mutableStateOf(startDate) }
    var time by remember(existing?.id) { mutableStateOf(existing?.onceAt?.drop(11)?.take(5)?.takeIf { it.length == 5 } ?: "08:15") }
    var changing by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val tone = toneColors(upcomingTone(deviceKind(device)))
    val ink = tone.onContainer
    EntityCard(tone.container, ink) {
        Text(if (existing == null) "New one-off" else "One-off", style = MaterialTheme.typography.titleLarge, color = ink)
        existing?.let {
            Text(
                if (it.src == "user") "Added by ${it.by.ifBlank { "someone" }}." else "Set by ${AssistantBrand.shown}${if (it.by.isNotBlank()) ", asked by ${it.by}" else ""}.",
                style = MaterialTheme.typography.bodyMedium, color = ink.copy(alpha = 0.85f),
            )
        }
        Text("What", style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.8f))
        TileRow(
            listOf(
                TileItem(upcomingIcon(deviceKind(device)), names[device] ?: entities[device]?.friendlyName ?: device.ifBlank { "Choose a device" }, true) { changing = !changing },
            ),
            tone, ink,
        )
        if (changing) {
            Column(Modifier.fillMaxWidth().heightIn(max = 240.dp).verticalScroll(rememberScrollState())) {
                devices.forEach { (id, name) ->
                    Text(
                        name, style = MaterialTheme.typography.bodyLarge, color = ink,
                        modifier = Modifier.fillMaxWidth().clickable { device = id; action = routineActions(id).first().first; changing = false; message = null }.padding(vertical = 10.dp),
                    )
                }
            }
        }
        Text("Do", style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.8f))
        TileRow(routineActions(device).map { (value, label) -> TileItem("", label, action == value) { action = value } }, tone, ink)
        Text("When", style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.8f))
        TileRow(
            listOf(
                TileItem("", "Today", date == today) { date = today; message = null },
                TileItem("", "Tomorrow", date == today.plusDays(1)) { date = today.plusDays(1); message = null },
                TileItem("", if (date == today || date == today.plusDays(1)) "Pick a date" else "${date.dayOfMonth}/${date.monthValue}", date != today && date != today.plusDays(1)) {
                    DatePickerDialog(context, { _, y, m, d -> date = LocalDate.of(y, m + 1, d); message = null }, date.year, date.monthValue - 1, date.dayOfMonth).show()
                },
            ),
            tone, ink,
        )
        ScheduleTime("At", time, false, ink) { t -> if (t != null) { time = t; message = null } }
        Text("Runs once, at the house's time. Anything that repeats is set on its own page.", style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.8f))
        message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
        TileRow(
            listOfNotNull(
                existing?.let { r -> TileItem("cd:bin", "Remove", false) { scope.launch { api?.ask("church_drive/routines/delete", JSONObject().put("routine_id", r.id)); onDone() } } },
                TileItem("cd:close", "Cancel", false) { onDone() },
                TileItem("cd:check", if (saving) "Saving…" else if (existing == null) "Add" else "Save", false) {
                    val past = date == today && (runCatching { LocalTime.parse(time) }.getOrNull()?.isBefore(LocalTime.now()) == true)
                    when {
                        device.isBlank() -> message = "Choose a device first."
                        past -> message = "That time has already gone."
                        !saving -> {
                            saving = true
                            scope.launch {
                                val result = api?.ask("church_drive/routines/set", oneOffBody(existing?.id, device, action, date.toString(), time))
                                saving = false
                                if (result != null) onDone() else message = api?.lastErrorCode?.let { "The house said no ($it)." } ?: "The house didn't save it."
                            }
                        }
                    }
                },
            ),
            tone, ink,
        )
    }
}

/** A routine that repeats and isn't for a light (Clive can schedule a blind every day): what it is and who asked, with Remove. */
@Composable
private fun RoutineDetailsCard(r: RoutineInfo, onDone: () -> Unit) {
    val api = LocalHaApi.current
    val scope = rememberCoroutineScope()
    val tone = toneColors(upcomingTone(deviceKind(r.entities.firstOrNull().orEmpty())))
    val ink = tone.onContainer
    EntityCard(tone.container, ink) {
        Text("Routine", style = MaterialTheme.typography.titleLarge, color = ink)
        Text(r.name, style = MaterialTheme.typography.bodyLarge, color = ink)
        Text("${routineDaysText(r.days)} · ${r.time}", style = MaterialTheme.typography.bodyMedium, color = ink.copy(alpha = 0.85f))
        Text(
            if (r.src == "user") "Added by ${r.by.ifBlank { "someone" }}." else "Set by ${AssistantBrand.shown}${if (r.by.isNotBlank()) ", asked by ${r.by}" else ""}.",
            style = MaterialTheme.typography.bodyMedium, color = ink.copy(alpha = 0.85f),
        )
        Text("To change it, ask ${AssistantBrand.shown}, or remove it and make it again.", style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.8f))
        TileRow(
            listOf(
                TileItem("cd:close", "Back", false) { onDone() },
                TileItem("cd:bin", "Remove", false) { scope.launch { api?.ask("church_drive/routines/delete", JSONObject().put("routine_id", r.id)); onDone() } },
            ),
            tone, ink,
        )
    }
}
