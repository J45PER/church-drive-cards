package com.churchdrive.todo

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.churchdrive.app.ui.AssistantLogo
import com.churchdrive.app.ui.CdVectors
import com.churchdrive.app.ui.ShoppingItem
import com.churchdrive.app.ui.ShoppingList
import com.churchdrive.app.ui.ShoppingScreen
import com.churchdrive.app.ui.ShoppingState
import com.churchdrive.app.ui.ShoppingSuggestion
import com.churchdrive.app.ui.TileItem
import com.churchdrive.app.ui.TileRow
import com.churchdrive.app.ui.Tone
import com.churchdrive.app.ui.ToneColors
import com.churchdrive.app.ui.glowBehind
import com.churchdrive.app.ui.toneColors

/** Clive's own light indigo, for his suggestions and offers. */
private val CliveInk = Color(0xFFC5CAE9)
private val CliveGround = Color(0xFF6B6BBF)

/**
 * The Shopping page: the main list that is always there, grouped by aisle with what Clive suggests above it, and the named trips below.
 * A trip opens into the full-page list (its date, place and people, and its items by aisle).
 */
@Composable
fun ShoppingPage(vm: TodoModel) {
    val tone = toneColors(Tone.Shopping)
    val state = vm.shopping
    var trip by rememberSaveable { mutableStateOf<String?>(null) }
    trip?.let { id -> ShoppingScreen(id) { trip = null } }
    if (state == null) {
        SectionHeader("Shopping list", null, tone) { Icon(CdVectors.Cart, contentDescription = null, tint = tone.accent) }
        Surface(shape = RoundedCornerShape(28.dp), color = tone.container, contentColor = tone.onContainer, modifier = Modifier.fillMaxWidth()) {
            Text(vm.problem ?: "Loading…", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
        }
        return
    }
    NowBanner(vm, state, tone)
    state.offer?.let { OfferCard(vm, it) }
    val main = state.main
    val open = main?.items?.count { it.status == "open" } ?: 0
    SectionHeader("Shopping list", if (open == 0) null else "$open to get", tone) { Icon(CdVectors.Cart, contentDescription = null, tint = tone.accent) }
    if (main == null) {
        Surface(shape = RoundedCornerShape(28.dp), color = tone.container, contentColor = tone.onContainer, modifier = Modifier.fillMaxWidth()) {
            Text("The shopping list isn't ready yet. The house may need Church Drive 0.196 or newer.", modifier = Modifier.padding(16.dp))
        }
    } else MainListCard(vm, state, main, tone)
    SectionHeader("Trips", null, tone) { Icon(CdVectors.Pin, contentDescription = null, tint = tone.accent) }
    NewTripButton(tone) { trip = "new" }
    state.trips.forEach { t -> TripCard(vm, t, tone) { trip = t.id } }
}

/** Who is shopping right now, or a button to say you are. */
@Composable
private fun NowBanner(vm: TodoModel, state: ShoppingState, tone: ToneColors) {
    val now = state.now
    val mine = state.mine
    Surface(shape = RoundedCornerShape(22.dp), color = tone.accent.copy(alpha = 0.22f), contentColor = MaterialTheme.colorScheme.onBackground, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(CdVectors.Cart, contentDescription = null, tint = tone.accent)
            Column(Modifier.weight(1f)) {
                if (now == null) {
                    Text("Going shopping?", style = MaterialTheme.typography.titleSmall)
                    Text("Tell the others, so nobody buys it twice.", style = MaterialTheme.typography.bodySmall)
                } else {
                    Text(if (now.person == mine) "You are shopping now" else "${now.person} is shopping now", style = MaterialTheme.typography.titleSmall)
                    Text(listOf(now.place.takeIf { it.isNotBlank() }?.let { "At $it" }, "${now.left} left").filterNotNull().joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                }
            }
            if (now == null || now.person == mine) {
                Text(
                    if (now == null) "I'm shopping" else "Done",
                    style = MaterialTheme.typography.labelLarge, color = tone.onAccent,
                    modifier = Modifier.background(tone.accent, RoundedCornerShape(50)).clickable { vm.setShoppingNow(now == null) }.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MainListCard(vm: TodoModel, state: ShoppingState, main: ShoppingList, tone: ToneColors) {
    val neutral = toneColors(Tone.Grey)
    var text by rememberSaveable { mutableStateOf("") }
    var choiceFor by remember { mutableStateOf<String?>(null) }
    var showBought by rememberSaveable { mutableStateOf(false) }
    val items = main.items
    val openItems = items.filter { it.status == "open" }
    val bought = items.filter { it.status == "bought" }
    Surface(shape = RoundedCornerShape(28.dp), color = tone.container, contentColor = tone.onContainer, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.glowBehind(tone.container).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Clive's suggestions come first, so they are decided before the list.
            if (state.suggestions.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(start = 4.dp)) {
                    AssistantLogo(CliveInk, 16.dp)
                    Text("CLIVE SUGGESTS", style = MaterialTheme.typography.labelMedium, color = tone.onContainer.copy(alpha = 0.75f))
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.suggestions.forEach { s -> SuggestionChip(s, { vm.addSuggestion(s) }, { vm.dismissSuggestion(s) }) }
                }
            }
            if (state.missed.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().background(tone.accent.copy(alpha = 0.2f), RoundedCornerShape(18.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(CdVectors.History, contentDescription = null, tint = tone.accent)
                    Column(Modifier.weight(1f)) {
                        Text("Not available last time", style = MaterialTheme.typography.titleSmall)
                        Text(state.missed.joinToString(", "), style = MaterialTheme.typography.bodySmall, maxLines = 2)
                    }
                    Text(
                        "Add back", style = MaterialTheme.typography.labelLarge, color = tone.onAccent,
                        modifier = Modifier.background(tone.accent, RoundedCornerShape(50)).clickable { vm.addBackMissed() }.padding(horizontal = 14.dp, vertical = 7.dp),
                    )
                }
            }
            if (openItems.isEmpty()) Text("Nothing to get", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 4.dp))
            // Items grouped by aisle, in the order a usual shop walks them.
            val order = state.categories.ifEmpty { openItems.map { it.cat }.distinct() }
            val grouped = openItems.groupBy { it.cat.ifBlank { "Other" } }
            (order + grouped.keys.filter { it !in order }).filter { grouped.containsKey(it) }.forEach { cat ->
                Text(cat.uppercase(), style = MaterialTheme.typography.labelMedium, color = tone.onContainer.copy(alpha = 0.75f), modifier = Modifier.padding(start = 4.dp, top = 4.dp))
                grouped.getValue(cat).forEach { item ->
                    ItemRow(item, tone, onBought = { vm.setShoppingItem(main, item, "bought") }, onMore = { choiceFor = if (choiceFor == item.id) null else item.id })
                    AnimatedVisibility(choiceFor == item.id) {
                        TileRow(
                            listOf(
                                TileItem("mdi:check", "Bought", true) { vm.setShoppingItem(main, item, "bought"); choiceFor = null },
                                TileItem("mdi:close", "Not available", false) { vm.setShoppingItem(main, item, "unavailable"); choiceFor = null },
                            ),
                            tone, neutral.onContainer,
                        )
                    }
                }
            }
            if (bought.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().clickable { showBought = !showBought }.padding(start = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("IN THE BASKET (${bought.size})", style = MaterialTheme.typography.labelMedium, color = tone.onContainer.copy(alpha = 0.75f), modifier = Modifier.weight(1f))
                    Icon(if (showBought) CdVectors.ChevronUp else CdVectors.ChevronDown, contentDescription = null)
                }
                if (showBought) bought.forEach { item ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Checkbox(checked = true, onCheckedChange = { vm.setShoppingItem(main, item, "open") }, colors = checks(tone))
                        Text(
                            item.text + item.qtyLabel.let { if (it.isEmpty()) "" else " $it" }, style = MaterialTheme.typography.bodyLarge,
                            textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough, color = tone.onContainer.copy(alpha = 0.6f),
                        )
                    }
                }
            }
            vm.notice?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
            AddRow("Add an item (milk 2 adds two)", text, { text = it }, tone) { vm.addShopping(text); text = "" }
        }
    }
}

@Composable
private fun ItemRow(item: ShoppingItem, tone: ToneColors, onBought: () -> Unit, onMore: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(tone.accent.copy(alpha = 0.1f), RoundedCornerShape(18.dp)).padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = false, onCheckedChange = { onBought() }, colors = checks(tone))
        Column(Modifier.weight(1f).clickable { onMore() }.padding(vertical = 6.dp)) {
            Text(item.text, style = MaterialTheme.typography.bodyLarge)
            if (item.note.isNotBlank()) Text(item.note, style = MaterialTheme.typography.bodySmall, color = tone.onContainer.copy(alpha = 0.72f))
        }
        if (item.qty > 1) Text(
            item.qtyLabel, style = MaterialTheme.typography.labelMedium, color = tone.accent,
            modifier = Modifier.padding(horizontal = 4.dp).background(tone.accent.copy(alpha = 0.18f), RoundedCornerShape(50)).padding(horizontal = 9.dp, vertical = 3.dp),
        )
        Icon(
            CdVectors.Close, contentDescription = "Not available or bought", tint = tone.onContainer.copy(alpha = 0.8f),
            modifier = Modifier.padding(end = 4.dp).size(32.dp).background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(50)).clickable { onMore() }.padding(7.dp),
        )
    }
}

/** One of Clive's suggestions: his mark, the item and why. Tap to add it; press and hold to turn it down. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SuggestionChip(s: ShoppingSuggestion, onAdd: () -> Unit, onDismiss: () -> Unit) {
    Row(
        Modifier.background(CliveGround.copy(alpha = 0.3f), RoundedCornerShape(50)).combinedClickable(onClick = onAdd, onLongClick = onDismiss).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AssistantLogo(CliveInk, 13.dp)
        Text(s.text, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFE8EAED))
        if (s.reason.isNotBlank()) Text(s.reason, style = MaterialTheme.typography.labelSmall, color = CliveInk.copy(alpha = 0.85f))
    }
}

@Composable
private fun NewTripButton(tone: ToneColors, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(tone.accent.copy(alpha = 0.16f), RoundedCornerShape(22.dp)).clickable { onClick() }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(CdVectors.Plus, contentDescription = null, tint = tone.accent)
        Text("New trip", style = MaterialTheme.typography.titleSmall, color = tone.accent)
    }
}

/** A named trip: its name, when and where, how many are done and who it is for. */
@Composable
private fun TripCard(vm: TodoModel, trip: ShoppingList, tone: ToneColors, onOpen: () -> Unit) {
    val done = trip.items.size - trip.open
    val whenWhere = listOf(
        com.churchdrive.app.ui.whenText(trip.date.ifEmpty { null }), trip.time.takeIf { trip.date.isNotEmpty() }.orEmpty(), trip.places.firstOrNull()?.name.orEmpty(),
    ).filter { it.isNotBlank() }.joinToString(" · ")
    Surface(shape = RoundedCornerShape(28.dp), color = tone.container, contentColor = tone.onContainer, modifier = Modifier.fillMaxWidth().clickable { onOpen() }) {
        Column(Modifier.glowBehind(tone.container).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(CdVectors.Pin, contentDescription = null, tint = tone.accent, modifier = Modifier.background(tone.accent.copy(alpha = 0.2f), RoundedCornerShape(50)).padding(9.dp).size(22.dp))
                Column(Modifier.weight(1f)) {
                    Text(trip.title, style = MaterialTheme.typography.titleMedium)
                    if (whenWhere.isNotEmpty()) Text(whenWhere, style = MaterialTheme.typography.bodySmall, color = tone.onContainer.copy(alpha = 0.72f))
                }
                Text(
                    "$done of ${trip.items.size}", style = MaterialTheme.typography.labelMedium, color = tone.accent,
                    modifier = Modifier.background(tone.accent.copy(alpha = 0.18f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
            androidx.compose.material3.LinearProgressIndicator(
                progress = { if (trip.items.isEmpty()) 0f else done.toFloat() / trip.items.size }, color = tone.accent,
                trackColor = Color.White.copy(alpha = 0.25f), modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                trip.people.forEach { p -> Text(p, style = MaterialTheme.typography.labelMedium, color = tone.accent, modifier = Modifier.background(tone.accent.copy(alpha = 0.18f), RoundedCornerShape(50)).padding(horizontal = 9.dp, vertical = 3.dp)) }
            }
        }
    }
}


/** Clive's offer: the weekly grocery shop he has learned (day, time, shop) as a trip, with what is open on the list moved onto it. */
@Composable
private fun OfferCard(vm: TodoModel, offer: com.churchdrive.app.ui.ShoppingOffer) {
    Surface(shape = RoundedCornerShape(22.dp), color = CliveGround.copy(alpha = 0.3f), contentColor = Color(0xFFE8EAED), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(44.dp).background(Color.White, RoundedCornerShape(50)), contentAlignment = Alignment.Center) { AssistantLogo(Color(0xFF4A4AA0), 24.dp) }
                Column(Modifier.weight(1f)) {
                    Text("${offer.name}?", style = MaterialTheme.typography.titleSmall)
                    Text(
                        listOf("You usually shop ${offer.day}", offer.time.takeIf { it.isNotBlank() }, offer.place.takeIf { it.isNotBlank() }?.let { "at $it" }).filterNotNull().joinToString(" "),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Make a trip", style = MaterialTheme.typography.labelLarge, color = Color(0xFF1E2345), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.weight(1f).background(CliveInk, RoundedCornerShape(18.dp)).clickable { vm.answerOffer(true) }.padding(vertical = 13.dp),
                )
                Text(
                    "Not now", style = MaterialTheme.typography.labelLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.weight(1f).background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(18.dp)).clickable { vm.answerOffer(false) }.padding(vertical = 13.dp),
                )
            }
        }
    }
}
