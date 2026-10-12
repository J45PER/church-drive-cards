package com.churchdrive.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** The shopping lists, kept up to date: asked for when shown, and again whenever the house says they changed. */
class ShoppingModel(val state: ShoppingState?, val failed: Boolean, val reload: () -> Unit)

@Composable
fun rememberShoppingModel(): ShoppingModel {
    val api = LocalHaApi.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<ShoppingState?>(null) }
    var failed by remember { mutableStateOf(false) }
    val reload: () -> Unit = {
        scope.launch {
            val parsed = parseShopping(api?.ask("church_drive/shopping", JSONObject()))
            if (parsed != null) {
                state = parsed
                failed = false
            } else if (state == null) failed = true
        }
    }
    LaunchedEffect(api) { reload() }
    DisposableEffect(api) {
        val sub = api?.subscribe("subscribe_events", JSONObject().put("event_type", "church_drive_shopping_changed")) { reload() } ?: -1
        onDispose { if (sub >= 0) api?.close(sub) }
    }
    return ShoppingModel(state, failed, reload)
}

private typealias Send = (String, JSONObject, (Any?) -> Unit) -> Unit

/** The list id [ShoppingScreen] is given to start a new steps list (as "new" starts a shopping list). */
const val NEW_STEPS = "new-steps"

private const val NOT_READY = "Couldn't read the shopping lists. The house may need Church Drive 0.42 or newer."

/**
 * A shopping list as a full page you can scroll and leave with Back: tick each item Bought or Not available, add more,
 * change who it is for, its date and where it will be bought. [listId] is a list's id, or "new" to make one.
 */
@Composable
fun ShoppingScreen(listId: String, onClose: () -> Unit) {
    val api = LocalHaApi.current
    val model = rememberShoppingModel()
    val tone = toneColors(Tone.Indigo)
    var id by remember(listId) { mutableStateOf(listId) }
    var editing by remember(listId) { mutableStateOf(listId == "new" || listId == NEW_STEPS) }
    var message by remember { mutableStateOf("") }
    val list = model.state?.lists?.firstOrNull { it.id == id }
    val steps = list?.isSteps ?: (listId == NEW_STEPS)
    val send: Send = { type, params, then ->
        api?.request(type, params) { r ->
            if (r == null) message = "That didn't work. Try again." else {
                message = ""
                then(r)
            }
            model.reload()
        }
    }
    PopupWindow(onDismissRequest = onClose, handleBack = { if (editing && list != null) { editing = false; true } else false }) { close ->
        val back = { if (editing && list != null) editing = false else close() }
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.clickable { back() }.padding(12.dp)) {
                        HaIcon("mdi:arrow-left", Icons.Filled.Info, MaterialTheme.colorScheme.onBackground, 24.dp)
                    }
                    Text(
                        if (editing) (if (list == null) (if (steps) "New steps list" else "New shopping list") else if (steps) "Edit steps" else "Edit list")
                        else list?.title ?: (if (steps) "Steps" else "Shopping"),
                        style = MaterialTheme.typography.titleLarge, maxLines = 1, modifier = Modifier.weight(1f),
                    )
                }
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (message.isNotEmpty()) Text(message, color = MaterialTheme.colorScheme.error)
                    val state = model.state
                    when {
                        state == null -> Text(if (model.failed) NOT_READY else "Loading…")
                        editing -> ShoppingForm(list, steps, state, tone, send, { newId -> if (newId != null) id = newId; editing = false }, back)
                        list == null -> Text("This list has been deleted or finished.")
                        else -> ShoppingItemsView(list, state, tone, send, { editing = true }, close)
                    }
                    Spacer(Modifier.height(96.dp)) // room to scroll the last buttons clear of the gesture bar (a dialog window doesn't always report that bar's height)
                }
            }
        }
    }
}

/** Start navigation to [p] in Google Maps, or open its directions page if the Maps app isn't there. */
private fun navigateTo(context: Context, p: ShoppingPlace) {
    val nav = Intent(Intent.ACTION_VIEW, Uri.parse(navigationUri(p))).setPackage("com.google.android.apps.maps").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(nav)
    } catch (_: ActivityNotFoundException) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(mapsWebUrl(p))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

@Composable
private fun ShoppingItemsView(list: ShoppingList, state: ShoppingState, tone: ToneColors, send: Send, onEdit: () -> Unit, onDeleted: () -> Unit) {
    val neutral = toneColors(Tone.Grey)
    val context = LocalContext.current
    val meta = listOf(whenText(list.date.ifEmpty { null }), list.time.takeIf { list.date.isNotEmpty() && !list.done }.orEmpty(), list.people.joinToString(", "), list.places.joinToString(", ") { it.name })
        .filter { it.isNotBlank() }.joinToString(" · ")
    if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (list.places.isNotEmpty() && !list.done) {
        TileRow(list.places.map { p -> TileItem("mdi:navigation-variant", "Navigate to ${p.name}", true) { navigateTo(context, p) } }, tone, neutral.onContainer)
    }
    val steps = list.isSteps
    val total = list.items.size
    Text(
        if (total == 0) (if (steps) "No steps yet." else "Nothing on the list yet.") else "${total - list.open} of $total done" + if (steps) " · ${list.open} to do" else "",
        style = MaterialTheme.typography.titleSmall,
    )
    if (list.done) {
        val missed = if (steps) 0 else list.items.count { it.status == "unavailable" }
        EntityCard(tone.container, tone.onContainer) {
            Text("All done. This list goes tomorrow.", style = MaterialTheme.typography.titleMedium)
            if (missed > 0) Text("$missed ${if (missed == 1) "wasn't" else "weren't"} available; you'll be reminded next time.", style = MaterialTheme.typography.bodyMedium)
        }
    }
    var text by remember(list.id) { mutableStateOf("") }
    OutlinedTextField(
        value = text, onValueChange = { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(if (steps) "Add a step" else "Add an item (or several, with commas)") },
    )
    TileRow(
        listOf(
            TileItem("mdi:plus", "Add", text.isNotBlank()) {
                val items = splitItems(text)
                if (items.isNotEmpty()) {
                    send("church_drive/shopping/add", JSONObject().put("list", list.id).put("items", JSONArray(items))) { }
                    text = ""
                }
            },
        ),
        tone, neutral.onContainer, enabled = text.isNotBlank(),
    )
    val setStatus = { item: ShoppingItem, status: String ->
        send("church_drive/shopping/item", JSONObject().put("list", list.id).put("item", item.id).put("status", status)) { }
    }
    list.items.filter { it.status == "open" }.forEach { item ->
        EntityCard(neutral.container, neutral.onContainer) {
            Text(item.text, style = MaterialTheme.typography.bodyLarge)
            TileRow(
                if (steps) listOf(TileItem("mdi:check", "Done", true) { setStatus(item, "bought") })
                else listOf(
                    TileItem("mdi:close", "Not available", false) { setStatus(item, "unavailable") },
                    TileItem("mdi:check", "Bought", true) { setStatus(item, "bought") },
                ),
                tone, neutral.onContainer,
            )
        }
    }
    val finished = list.items.filter { it.status != "open" }
    if (finished.isNotEmpty()) {
        Text(if (steps) "Done" else "Bought and not available", style = MaterialTheme.typography.titleSmall)
        finished.forEach { item ->
            Row(modifier = Modifier.fillMaxWidth().clickable { setStatus(item, "open") }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.text + if (item.status == "unavailable") " (not available)" else "",
                    style = MaterialTheme.typography.bodyLarge, textDecoration = TextDecoration.LineThrough,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f),
                )
                HaIcon("mdi:undo", Icons.Filled.Info, MaterialTheme.colorScheme.onSurfaceVariant, 22.dp)
            }
        }
    }
    var sure by remember(list.id) { mutableStateOf(false) }
    TileRow(
        listOf(
            TileItem("mdi:pencil-outline", "Edit", false) { onEdit() },
            TileItem("mdi:delete-outline", if (sure) "Tap again" else "Delete", sure) {
                if (sure) send("church_drive/shopping/delete", JSONObject().put("list", list.id)) { onDeleted() } else sure = true
            },
        ),
        toneColors(Tone.Red), neutral.onContainer,
    )
}

@Composable
private fun ShoppingForm(existing: ShoppingList?, steps: Boolean, state: ShoppingState, tone: ToneColors, send: Send, onSaved: (String?) -> Unit, onCancel: () -> Unit) {
    val neutral = toneColors(Tone.Grey)
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var who by remember { mutableStateOf(existing?.people ?: listOfNotNull(state.mine.ifEmpty { null })) }
    var date by remember { mutableStateOf(existing?.date.orEmpty()) }
    var time by remember { mutableStateOf(existing?.time.orEmpty()) }
    var places by remember { mutableStateOf(existing?.places ?: emptyList()) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<ShoppingPlace>>(emptyList()) }
    var searchNote by remember { mutableStateOf("") }
    var items by remember { mutableStateOf("") }
    var withMissed by remember { mutableStateOf(!steps && existing == null && state.missed.isNotEmpty()) }
    // The house, for a one-tap "Home" place on a steps list (Home Assistant's zone.home).
    val api = LocalHaApi.current
    var home by remember { mutableStateOf<ShoppingPlace?>(null) }
    LaunchedEffect(steps, api) {
        if (steps) {
            val all = api?.ask("get_states", JSONObject()) as? JSONArray
            val zone = all?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) }.firstOrNull { it.optString("entity_id") == "zone.home" } }
            val at = zone?.optJSONObject("attributes")
            if (at != null && at.has("latitude") && at.has("longitude")) {
                home = ShoppingPlace("Home", "", at.optDouble("latitude"), at.optDouble("longitude"))
            }
        }
    }
    var error by remember { mutableStateOf("") }
    if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Text("Who is it for?", style = MaterialTheme.typography.titleSmall)
    TileRow(state.people.map { p -> TileItem("mdi:account-outline", p, p in who) { who = if (p in who) who - p else who + p } }, tone, neutral.onContainer)
    Text(if (steps) "Date and time (optional)" else "Date to go shopping", style = MaterialTheme.typography.titleSmall)
    val today = LocalDate.now()
    val weekend = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY)).toString()
    TileRow(
        listOf(
            TileItem("mdi:calendar-remove-outline", "None", date.isEmpty()) { date = "" },
            TileItem("mdi:calendar-today", "Today", date == today.toString()) { date = today.toString() },
            TileItem("mdi:calendar-arrow-right", "Tomorrow", date == today.plusDays(1).toString()) { date = today.plusDays(1).toString() },
            TileItem("mdi:calendar-weekend", "Saturday", date == weekend) { date = weekend },
        ),
        tone, neutral.onContainer,
    )
    OutlinedTextField(value = date, onValueChange = { date = it.trim() }, label = { Text("Date (YYYY-MM-DD)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    if (date.isNotEmpty()) {
        OutlinedTextField(value = time, onValueChange = { time = it.trim() }, label = { Text("Time (HH:MM, optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    }
    Text(if (steps) "Remind me at a place (optional)" else "Where will you buy it? Search Google Maps", style = MaterialTheme.typography.titleSmall)
    home?.let { h ->
        val chosen = places.any { it.name == "Home" && it.lat == h.lat && it.lon == h.lon }
        TileRow(
            listOf(TileItem("mdi:home-outline", "Home", chosen) { places = if (chosen) places.filterNot { it.name == "Home" && it.lat == h.lat && it.lon == h.lon } else if (places.size < 5) places + h else places }),
            tone, neutral.onContainer,
        )
    }
    if (places.isNotEmpty()) {
        TileRow(places.map { p -> TileItem("mdi:map-marker-remove-outline", p.name, true) { places = places - p } }, tone, neutral.onContainer)
    }
    OutlinedTextField(
        value = query, onValueChange = { query = it }, label = { Text("e.g. Lidl Protaras Cyprus") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
    )
    TileRow(
        listOf(
            TileItem("mdi:magnify", "Search", query.trim().length >= 2) {
                searchNote = "Searching…"
                results = emptyList()
                send("church_drive/maps/search", JSONObject().put("query", query.trim()).put("anywhere", true)) { r ->
                    val found = ((r as? JSONArray)?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(::parsePlace) } }).orEmpty()
                    results = found
                    searchNote = if (found.isEmpty()) "Nothing found. Try the shop name and the town." else ""
                }
            },
        ),
        tone, neutral.onContainer, enabled = query.trim().length >= 2,
    )
    if (searchNote.isNotEmpty()) Text(searchNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (results.isNotEmpty() || places.isNotEmpty()) {
        PlaceMap(results, places) { p ->
            if (places.none { it.lat == p.lat && it.lon == p.lon } && places.size < 5) places = places + p
            results = emptyList()
        }
    }
    results.forEach { p ->
        Row(
            modifier = Modifier.fillMaxWidth().clickable {
                if (places.none { it.lat == p.lat && it.lon == p.lon } && places.size < 5) places = places + p
                results = emptyList()
            }.padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HaIcon("mdi:map-marker-outline", Icons.Filled.Info, tone.accent, 24.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(p.name, style = MaterialTheme.typography.bodyLarge)
                Text(p.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (existing == null) {
        OutlinedTextField(
            value = items, onValueChange = { items = it }, label = { Text(if (steps) "Steps (one per line)" else "Items (one per line)") },
            minLines = 3, modifier = Modifier.fillMaxWidth(),
        )
        if (!steps && state.missed.isNotEmpty()) {
            val n = state.missed.size
            TileRow(
                listOf(TileItem("mdi:alert-circle-outline", "Add the $n not available last time", withMissed) { withMissed = !withMissed }),
                tone, neutral.onContainer,
            )
            Text(state.missed.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    TileRow(
        listOf(
            TileItem("mdi:close", "Cancel", false) { onCancel() },
            TileItem("mdi:check", if (existing == null) "Make the list" else "Save", true) {
                if (who.isEmpty()) {
                    error = "Choose who the list is for."
                } else if (existing == null) {
                    val body = JSONObject().put("name", name.trim()).put("people", JSONArray(who)).put("date", date).put("time", time).put("places", JSONArray(places.map { it.toJson() }))
                        .put("items", JSONArray(splitItems(items))).put("include_missed", withMissed)
                    if (steps) body.put("kind", "steps")
                    send("church_drive/shopping/create", body) { r -> onSaved(((r as? JSONObject)?.optJSONObject("list"))?.optString("id")) }
                } else {
                    val body = JSONObject().put("list", existing.id).put("name", name.trim()).put("people", JSONArray(who)).put("date", date).put("time", time).put("places", JSONArray(places.map { it.toJson() }))
                    send("church_drive/shopping/update", body) { onSaved(null) }
                }
            },
        ),
        tone, neutral.onContainer,
    )
}

/** The dashboard's `custom:shopping-card`: the open lists, a way to make one, and what wasn't available last time. */
@Composable
fun ShoppingCard(config: JSONObject) {
    val model = rememberShoppingModel()
    val tone = toneColors(Tone.Indigo)
    var open by remember { mutableStateOf<String?>(null) }
    open?.let { ShoppingScreen(it) { open = null } }
    EntityCard(tone.container, tone.onContainer) {
        config.optString("title").takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
        val state = model.state
        if (state == null) {
            Text(if (model.failed) NOT_READY else "Loading…", style = MaterialTheme.typography.bodyMedium)
        } else {
            val lists = openLists(state, if (config.optBoolean("all")) null else state.mine)
            if (lists.isEmpty()) Text("No shopping lists.", style = MaterialTheme.typography.bodyMedium)
            lists.forEach { l ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { open = l.id }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    HaIcon(if (l.isSteps) "mdi:format-list-checks" else "mdi:cart-outline", Icons.Filled.Info, tone.accent, 26.dp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(l.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                        val sub = listOf(whenText(l.date.ifEmpty { null }), l.time.takeIf { l.date.isNotEmpty() }.orEmpty(), l.people.joinToString(", "), l.places.joinToString(", ") { it.name })
                            .filter { it.isNotBlank() }.joinToString(" · ")
                        if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, color = tone.onContainer.copy(alpha = 0.75f), maxLines = 1)
                    }
                    Text("${l.open} ${if (l.isSteps) "to do" else "to get"}", style = MaterialTheme.typography.labelLarge)
                }
            }
            if (state.missed.isNotEmpty()) {
                Text("Not available last time: ${state.missed.joinToString(", ")}", style = MaterialTheme.typography.bodySmall)
            }
        }
        TileRow(
            listOf(
                TileItem("mdi:plus", "New shopping list", false) { open = "new" },
                TileItem("mdi:format-list-checks", "New steps list", false) { open = NEW_STEPS },
            ),
            tone, tone.onContainer,
        )
    }
}
