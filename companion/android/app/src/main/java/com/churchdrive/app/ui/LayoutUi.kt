package com.churchdrive.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.churchdrive.app.ha.EntityState
import kotlinx.coroutines.launch
import org.json.JSONObject

/** This person's own layout and how to change it (kept in the house against their Home Assistant user). */
class UserLayoutState(val layout: UserLayout, val set: (UserLayout) -> Unit)

val LocalUserLayout = compositionLocalOf { UserLayoutState(UserLayout()) {} }

/** The house's pages as the app lays them out (page key to its sections): where a person's own sections find their cards. */
val LocalHousePages = compositionLocalOf<Map<String, List<PanelSpec>>> { emptyMap() }

/** The person's layout, asked for once and again whenever it changes in the house (on another of their phones, for example). */
@Composable
fun rememberUserLayoutState(api: HaApi?): UserLayoutState {
    val state = remember { mutableStateOf(UserLayout()) }
    var changed by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(api, changed) { parseUserLayout(api?.ask("church_drive/layout", JSONObject()))?.let { state.value = it } }
    DisposableEffect(api) {
        val sub = api?.subscribe("subscribe_events", JSONObject().put("event_type", "church_drive_layout_changed")) { changed++ } ?: -1
        onDispose { if (sub >= 0) api?.close(sub) }
    }
    return UserLayoutState(state.value) { next ->
        state.value = next
        scope.launch { api?.ask("church_drive/layout/set", userLayoutBody(next)) }
    }
}

/**
 * A page with this person's layout applied to its [houseSections] (the house's sections as the app lays them out): [content] draws what is left,
 * in their order, and a "Customise this page" button under it opens [CustomisePage]. [editCards] says whether the page's cards can be hidden and
 * reordered (the Home page draws its house sections itself, so there only the sections can).
 */
@Composable
fun CustomisablePage(page: String, title: String, houseSections: List<PanelSpec>, entities: Map<String, EntityState>, editCards: Boolean = true, own: Boolean = false, content: @Composable (List<PanelSpec>) -> Unit) {
    val state = LocalUserLayout.current
    val pages = LocalHousePages.current
    var customising by rememberSaveable(page) { mutableStateOf(false) }
    content(applyPageLayout(houseSections, state.layout.pages[page], pages))
    if (houseSections.any { it.title.isNotBlank() }) {
        Box(Modifier.fullSpan()) {
            TileRow(listOf(TileItem("cd:sliders", "Customise this page", false) { customising = true }), toneColors(Tone.Grey), MaterialTheme.colorScheme.onSurface, perRow = 1)
        }
    }
    if (customising) CustomisePage(page, title, houseSections, entities, editCards, own) { customising = false }
}

/** A small square button: an icon that does something, greyed out when it can't. */
@Composable
private fun IconBox(icon: String, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).alpha(if (enabled) 1f else 0.35f)
            .clickable(enabled = enabled, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { HaIcon(icon, CdVectors.Info, MaterialTheme.colorScheme.onSurface, 20.dp) }
}

private val OWN_ICONS = listOf(
    "cd:home", "cd:sun", "cd:moon", "cd:flower", "cd:bulb-lit", "cd:thermostat", "cd:room-living", "cd:room-bed-king", "cd:bolt",
    "cd:television", "cd:music-note", "cd:leaf", "cd:palette", "cd:checklist",
)

/** The colours an own section can have: "auto" is the colour of what is inside it, "dynamic" follows state, the rest are the house's palettes. */
private val OWN_TONES = listOf(
    "auto" to "Auto", "dynamic" to "Dynamic", "lights" to "Lights", "heating" to "Heating", "air" to "Air", "blue" to "Blue", "green" to "Green",
    "amber" to "Amber", "purple" to "Purple", "indigo" to "Indigo", "coral" to "Coral", "teal" to "Teal", "orange" to "Orange", "grey" to "Grey",
)

/**
 * Customise a page: its sections in a list, each with arrows to move it. A house section has a switch to show or hide it and, where [editCards],
 * opens to its cards, each with arrows and an eye; the house's sections can't be renamed or deleted, so Reset always brings the page back. Where [own]
 * (Home) a person can also make their own sections: they are added and removed, and edited in [OwnSectionEditor].
 */
@Composable
fun CustomisePage(page: String, title: String, houseSections: List<PanelSpec>, entities: Map<String, EntityState>, editCards: Boolean, own: Boolean, onClose: () -> Unit) {
    val state = LocalUserLayout.current
    val pages = LocalHousePages.current
    val layout = state.layout.pages[page] ?: PageLayout()
    val admin = LocalIsAdmin.current
    val sections = houseSections.filter { it.title.isNotBlank() }.filter { p -> p.cards.isEmpty() || visibleCards(p.cards, admin).isNotEmpty() }
    val ownSections = layout.own
    val order = effectiveSectionOrder(sections.map(::sectionId) + ownSections.map { "own:${it.id}" }, layout)
    var open by rememberSaveable(page) { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<OwnSection?>(null) }
    var addingTo by remember { mutableStateOf<Pair<String, String>?>(null) }
    fun change(f: (PageLayout) -> PageLayout) = state.set(state.layout.changed(page, f))
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    FullPage("Customise $title", onClose) {
        Text(
            "Order the sections, and switch off the ones you don't want.${if (editCards) " Tap a section to choose which of its cards show." else ""}${if (own) " Make your own sections too, with cards from any page." else ""}",
            style = MaterialTheme.typography.bodyMedium, color = muted,
        )
        order.forEachIndexed { index, id ->
            val mine = ownSections.firstOrNull { "own:${it.id}" == id }
            val panel = sections.firstOrNull { it.title == id }
            if (mine == null && panel == null) return@forEachIndexed
            val shown = id !in layout.hidden
            val kind = if (mine == null) sectionKind(page, id) else null
            val canOpen = mine == null && (editCards || kind != null)
            val isOpen = canOpen && open == id
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 12.dp, vertical = 4.dp)) {
                Row(
                    Modifier.fillMaxWidth()
                        .clickable(enabled = mine != null || canOpen, onClickLabel = if (mine != null) "Edit ${mine.title}" else if (isOpen) "Close $id" else "Open $id") {
                            if (mine != null) editing = mine else open = if (isOpen) null else id
                        }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HaIcon(mine?.icon ?: panel?.icon, CdVectors.Info, MaterialTheme.colorScheme.onSurface, 22.dp)
                    Column(Modifier.weight(1f)) {
                        Text(mine?.title ?: id, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                        val n = mine?.cards?.size ?: if (editCards && panel != null) visibleCards(panel.cards, admin).size else null
                        if (n != null) Text("$n card${if (n == 1) "" else "s"}${if (mine != null) " · yours" else ""}", style = MaterialTheme.typography.bodySmall, color = muted)
                    }
                    IconBox("cd:arrow-up", "Move up", enabled = index > 0) { change { movedSection(it, order, id, -1) } }
                    IconBox("cd:arrow-down", "Move down", enabled = index < order.size - 1) { change { movedSection(it, order, id, 1) } }
                    if (mine != null) IconBox("cd:bin", "Delete ${mine.title}") { change { l -> l.copy(own = l.own.filterNot { it.id == mine.id }, order = l.order - id) } }
                    else Switch(checked = shown, onCheckedChange = { change { l -> toggledSection(l, id) } })
                }
                if (isOpen && panel != null) {
                    val cards = visibleCards(panel.cards, admin)
                    val ids = cardIds(panel.cards).filterIndexed { i, _ -> panel.cards[i] in cards }
                    val byId = ids.zip(cards).toMap()
                    val cardOrder = effectiveCardOrder(ids, layout, id)
                    val addedItems = layout.cards[id]?.added.orEmpty()
                    Column(Modifier.padding(bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (editCards) cardOrder.forEachIndexed { ci, cid ->
                            val card = byId[cid] ?: return@forEachIndexed
                            val hidden = cid in (layout.cards[id]?.hidden ?: emptySet())
                            Row(Modifier.fillMaxWidth().alpha(if (hidden) 0.55f else 1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(cardLabel(card) { entities[it]?.friendlyName }, style = MaterialTheme.typography.bodyLarge, maxLines = 1, modifier = Modifier.weight(1f))
                                IconBox("cd:arrow-up", "Move up", enabled = ci > 0) { change { movedCard(it, id, ids, cid, -1) } }
                                IconBox("cd:arrow-down", "Move down", enabled = ci < cardOrder.size - 1) { change { movedCard(it, id, ids, cid, 1) } }
                                IconBox(if (hidden) "cd:eye-off" else "cd:eye", if (hidden) "Show" else "Hide") { change { toggledCard(it, id, cid) } }
                            }
                        }
                        // What this person added to the section: each can be taken out again (the house's own cards can only be hidden).
                        addedItems.forEach { item ->
                            val label = item.entity?.let { entities[it]?.friendlyName ?: it } ?: item.card?.let { r -> resolveOwnCard(r, pages)?.let { c -> cardLabel(c) { entities[it]?.friendlyName } } } ?: "A card the house no longer has"
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("$label · added", style = MaterialTheme.typography.bodyLarge, maxLines = 1, modifier = Modifier.weight(1f))
                                IconBox("cd:close", "Take out") { change { it.withoutAdded(id, item) } }
                            }
                        }
                        if (kind != null) TileRow(listOf(TileItem("cd:plus", "Add to this section", false) { addingTo = id to kind }), toneColors(Tone.Grey), MaterialTheme.colorScheme.onSurface, perRow = 1)
                    }
                }
            }
        }
        if (own && ownSections.size < 12) {
            TileRow(
                listOf(TileItem("cd:plus", "New section", false) { editing = OwnSection("s" + System.currentTimeMillis().toString(36), "", "cd:palette", "auto", false, emptyList()) }),
                toneColors(Tone.Lights), MaterialTheme.colorScheme.onSurface, perRow = 1,
            )
        }
        TileRow(
            listOf(
                TileItem("cd:restore", "Reset this page", false) { state.set(state.layout.reset(page)); open = null },
                TileItem("cd:restore", "Reset every page", false) { state.set(UserLayout()); open = null },
            ),
            toneColors(Tone.Grey), MaterialTheme.colorScheme.onSurface, perRow = 2,
        )
        Text("Sections and cards the house adds later show up at the end. Reset puts this page back as the house has it.", style = MaterialTheme.typography.bodySmall, color = muted)
    }
    addingTo?.let { (sectionTitle, kind) ->
        AddToSectionPage(kind, entities, layout.cards[sectionTitle]?.added.orEmpty(), onAdd = { item -> change { it.withAdded(sectionTitle, item) } }) { addingTo = null }
    }
    editing?.let { section ->
        OwnSectionEditor(
            section, isNew = ownSections.none { it.id == section.id }, entities,
            onSave = { s -> change { l -> l.copy(own = if (l.own.any { it.id == s.id }) l.own.map { o -> if (o.id == s.id) s else o } else l.own + s) }; editing = null },
            onClose = { editing = null },
        )
    }
}

/**
 * Makes or changes one of a person's own sections in place: a name, an icon, a colour (Auto from what's inside, Dynamic to follow state, or one of the
 * house's palettes), and its cards from any page, each with arrows and a way to take it out. Nothing is kept until Save.
 */
@Composable
private fun OwnSectionEditor(start: OwnSection, isNew: Boolean, entities: Map<String, EntityState>, onSave: (OwnSection) -> Unit, onClose: () -> Unit) {
    val pages = LocalHousePages.current
    var draft by remember(start.id) { mutableStateOf(start) }
    var adding by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val tone = toneColors(Tone.Grey)
    val ink = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    FullPage(if (isNew) "New section" else "Edit section", onClose) {
        WashField(draft.title, { draft = draft.copy(title = it.take(24)); message = null }, "Name", tone)
        Text("Icon", style = MaterialTheme.typography.labelMedium, color = muted)
        TileRow(OWN_ICONS.map { icon -> TileItem(icon, icon.removePrefix("cd:"), draft.icon == icon, showLabel = false) { draft = draft.copy(icon = icon) } }, tone, ink, perRow = 7)
        Text("Colour", style = MaterialTheme.typography.labelMedium, color = muted)
        TileRow(OWN_TONES.map { (key, label) -> TileItem("cd:palette", label, draft.tone == key || (key == "dynamic" && draft.dynamic && draft.tone == "auto")) { draft = draft.copy(tone = key, dynamic = key == "dynamic") } }, tone, ink, perRow = 3)
        Text("Auto takes the colour of what is in the section. Dynamic follows state, such as the alarm or lights being on.", style = MaterialTheme.typography.bodySmall, color = muted)
        Text("Cards", style = MaterialTheme.typography.labelMedium, color = muted)
        if (draft.cards.isEmpty()) Text("Nothing in it yet. Add cards from any page.", style = MaterialTheme.typography.bodyMedium, color = muted)
        draft.cards.forEachIndexed { i, ref ->
            val card = resolveOwnCard(ref, pages)
            Row(Modifier.fillMaxWidth().alpha(if (card == null) 0.55f else 1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(card?.let { c -> cardLabel(c) { entities[it]?.friendlyName } } ?: "A card the house no longer has", style = MaterialTheme.typography.bodyLarge, maxLines = 1, modifier = Modifier.weight(1f))
                IconBox("cd:arrow-up", "Move up", enabled = i > 0) { draft = draft.copy(cards = draft.cards.toMutableList().also { java.util.Collections.swap(it, i, i - 1) }) }
                IconBox("cd:arrow-down", "Move down", enabled = i < draft.cards.size - 1) { draft = draft.copy(cards = draft.cards.toMutableList().also { java.util.Collections.swap(it, i, i + 1) }) }
                IconBox("cd:close", "Take out") { draft = draft.copy(cards = draft.cards - ref) }
            }
        }
        TileRow(listOf(TileItem("cd:plus", "Add cards", false) { adding = true }), tone, ink, perRow = 1)
        message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
        TileRow(
            listOf(
                TileItem("cd:close", "Cancel", false) { onClose() },
                TileItem("cd:check", "Save", false) { if (draft.title.isBlank()) message = "Give it a name." else onSave(draft.copy(title = draft.title.trim())) },
            ),
            toneColors(Tone.Lights), ink, perRow = 2,
        )
    }
    if (adding) AddCardsPage(draft.cards, entities, onAdd = { ref -> if (ref !in draft.cards && draft.cards.size < 40) draft = draft.copy(cards = draft.cards + ref) }) { adding = false }
}

/** Cards from the house's other pages, by page, to put in an own section; a card already in it shows as added (it can be in other sections too). */
@Composable
private fun AddCardsPage(chosen: List<OwnCardRef>, entities: Map<String, EntityState>, onAdd: (OwnCardRef) -> Unit, onClose: () -> Unit) {
    val pages = LocalHousePages.current
    val admin = LocalIsAdmin.current
    val all = addableCards(pages, admin)
    val labels = all.map { it.pageLabel }.distinct()
    var category by rememberSaveable { mutableStateOf(labels.firstOrNull().orEmpty()) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    FullPage("Add cards", onClose) {
        if (labels.isEmpty()) {
            Text("No cards to add yet.", style = MaterialTheme.typography.bodyMedium, color = muted)
        } else {
            TileRow(labels.map { l -> TileItem("cd:sliders", l, category == l) { category = l } }, toneColors(Tone.Grey), MaterialTheme.colorScheme.onSurface, perRow = 3)
            all.filter { it.pageLabel == category }.forEach { a ->
                val added = a.ref in chosen
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
                        .clickable(enabled = !added, onClickLabel = "Add ${cardLabel(a.card) { entities[it]?.friendlyName }}") { onAdd(a.ref) }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(cardLabel(a.card) { entities[it]?.friendlyName }, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                        Text(a.ref.section, style = MaterialTheme.typography.bodySmall, color = muted, maxLines = 1)
                    }
                    IconBox(if (added) "cd:check" else "cd:plus", if (added) "Added" else "Add", enabled = !added) { onAdd(a.ref) }
                }
            }
        }
    }
}

/**
 * Adds to a house section: cards of the section's kind from the house's pages, or a single light or device of that kind (found by name). A card or
 * item can be added to more than one section; one already added here shows as added.
 */
@Composable
private fun AddToSectionPage(kind: String, entities: Map<String, EntityState>, added: List<AddedItem>, onAdd: (AddedItem) -> Unit, onClose: () -> Unit) {
    val pages = LocalHousePages.current
    val admin = LocalIsAdmin.current
    var tab by rememberSaveable { mutableStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val cards = addableCards(pages, admin).filter { it.ref.page == kindPage(kind) }
    val things = entities.values.filter { isOfKind(kind, it.entityId, it.attributes.optString("device_class").ifBlank { null }) }
        .filter { query.isBlank() || it.friendlyName.contains(query, ignoreCase = true) || it.entityId.contains(query, ignoreCase = true) }
        .sortedBy { it.friendlyName.lowercase() }.take(150)
    FullPage("Add to this section", onClose) {
        TileRow(
            listOf(TileItem("cd:sliders", "Cards", tab == 0) { tab = 0 }, TileItem("cd:bulb-lit", "Single items", tab == 1) { tab = 1 }),
            toneColors(Tone.Grey), MaterialTheme.colorScheme.onSurface, perRow = 2,
        )
        if (tab == 0) {
            if (cards.isEmpty()) Text("No other cards of this kind yet.", style = MaterialTheme.typography.bodyMedium, color = muted)
            cards.forEach { a ->
                val item = AddedItem(card = a.ref)
                AddRow(cardLabel(a.card) { entities[it]?.friendlyName }, a.ref.section, item in added) { onAdd(item) }
            }
        } else {
            WashField(query, { query = it }, "Search by name", toneColors(Tone.Grey))
            if (things.isEmpty()) Text("Nothing found.", style = MaterialTheme.typography.bodyMedium, color = muted)
            things.forEach { e ->
                val item = AddedItem(entity = e.entityId)
                AddRow(e.friendlyName, e.entityId, item in added) { onAdd(item) }
            }
        }
    }
}

@Composable
private fun AddRow(title: String, subtitle: String, added: Boolean, onAdd: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(enabled = !added, onClickLabel = "Add $title", onClick = onAdd).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        IconBox(if (added) "cd:check" else "cd:plus", if (added) "Added" else "Add", enabled = !added, onClick = onAdd)
    }
}
