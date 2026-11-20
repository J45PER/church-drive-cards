package com.churchdrive.app.ui

import org.json.JSONArray
import org.json.JSONObject

/**
 * A person's own layout (decided 2026-11-20): the house's pages, sections and cards are the starting point, and a person's changes are kept
 * against their Home Assistant user in the house. Only the changes are kept, as ids of the house's sections and cards, so a section or card
 * added in Home Assistant shows by default (at the end) and nothing a person arranged moves. On every page a person can reorder sections, hide
 * a house section and reorder or hide its cards; on Home they can also make their own sections, with cards from any page.
 */
/** Something a person added to a house section: a house card from any page (a reference), or a single light or device (its entity). */
data class AddedItem(val card: OwnCardRef? = null, val entity: String? = null)

data class CardPrefs(val order: List<String> = emptyList(), val hidden: Set<String> = emptySet(), val added: List<AddedItem> = emptyList())

/** A house card in one of a person's own sections: where it is in the house's layout (its page, section and card id). */
data class OwnCardRef(val page: String, val section: String, val card: String)

/** One of a person's own sections: its name, icon, colour (a palette name, "auto" for the colour of what is inside, "dynamic" to follow state) and cards. */
data class OwnSection(val id: String, val title: String, val icon: String, val tone: String, val dynamic: Boolean, val cards: List<OwnCardRef>)

data class PageLayout(
    val order: List<String> = emptyList(),
    val hidden: Set<String> = emptySet(),
    val cards: Map<String, CardPrefs> = emptyMap(),
    val own: List<OwnSection> = emptyList(),
) {
    val isEmpty: Boolean get() = order.isEmpty() && hidden.isEmpty() && cards.isEmpty() && own.isEmpty()
}

data class UserLayout(val pages: Map<String, PageLayout> = emptyMap())

/** The id of a section: the house's is its title, one of a person's own is "own:" and its id. */
fun sectionId(panel: PanelSpec): String = panel.ownId?.let { "own:$it" } ?: panel.title

/** What a card is called in a list: its own name, then its entity's, then what kind of card it is. */
fun cardLabel(card: CardSpec, nameOf: (String) -> String?): String {
    val c = card.config
    listOf("name", "title", "heading").map { c.optString(it) }.firstOrNull { it.isNotBlank() }?.let { return it }
    c.optString("entity").takeIf { it.isNotBlank() }?.let { e -> nameOf(e)?.let { return it } }
    c.optString("area").takeIf { it.isNotBlank() }?.let { return it.replace('_', ' ').replaceFirstChar { ch -> ch.uppercase() } }
    return card.type.removePrefix("custom:").removeSuffix("-card").replace('-', ' ').replaceFirstChar { it.uppercase() }
}

/** The ids of a list of cards: what they are (type and entity, area or name), made different when two are alike. */
fun cardIds(cards: List<CardSpec>): List<String> {
    val seen = mutableMapOf<String, Int>()
    return cards.map { card ->
        val c = card.config
        val key = listOf("area", "entity", "name", "title", "heading").map { c.optString(it) }.firstOrNull { it.isNotBlank() }.orEmpty()
        val base = "${card.type.removeSuffix("-beta")}|$key"
        val n = (seen[base] ?: 0) + 1
        seen[base] = n
        if (n == 1) base else "$base#$n"
    }
}

/** [items] with the ones named in [order] first, in that order, then the rest as they were (so anything new shows at the end). */
fun <T> orderedBy(items: List<T>, order: List<String>, id: (T) -> String): List<T> {
    val index = order.withIndex().associate { it.value to it.index }
    val (listed, rest) = items.partition { id(it) in index }
    return listed.sortedBy { index.getValue(id(it)) } + rest
}

/** A house card from a person's own section's reference, as it is in the house's pages now; null if the house no longer has it. */
fun resolveOwnCard(ref: OwnCardRef, pages: Map<String, List<PanelSpec>>): CardSpec? {
    val panel = pages[ref.page]?.firstOrNull { it.title == ref.section } ?: return null
    return panel.cards.getOrNull(cardIds(panel.cards).indexOf(ref.card))
}

/** A card for a single light or device: a light's own card, a thermostat or fan's, or a plain tile. */
fun entityCard(entity: String): CardSpec {
    val config = org.json.JSONObject().put("entity", entity)
    return when (entity.substringBefore('.')) {
        "light" -> CardSpec("custom:light-control-card", config.put("mode", "light"))
        "climate" -> CardSpec("custom:climate-card", config)
        "fan" -> CardSpec("custom:fan-card", config)
        else -> CardSpec("tile", config)
    }
}

/** What a person added to a house section as a card: the house card it refers to (if the house still has it), or a card for the entity. */
fun resolveAdded(item: AddedItem, pages: Map<String, List<PanelSpec>>): CardSpec? =
    item.entity?.let { entityCard(it) } ?: item.card?.let { resolveOwnCard(it, pages) }

/** A person's own sections as panels, their cards found in [pages] (the house's pages as the app lays them out). */
fun ownPanels(layout: PageLayout?, pages: Map<String, List<PanelSpec>>): List<PanelSpec> =
    layout?.own.orEmpty().map { s ->
        PanelSpec(
            s.title, s.icon, if (s.dynamic) "dynamic" else s.tone.takeIf { it != "auto" }, null, null,
            s.cards.mapNotNull { resolveOwnCard(it, pages) }, ownId = s.id,
        )
    }

/**
 * [panels] (the house's sections of a page, as the app lays them out) with this person's changes: their own sections added, sections in their
 * order (anything not in it at the end), the hidden ones left out, and each house section's cards in their order without the hidden ones. A page's
 * header (a panel with no title) stays where it is. [pages] is where an own section's cards are found.
 */
fun applyPageLayout(panels: List<PanelSpec>, layout: PageLayout?, pages: Map<String, List<PanelSpec>> = emptyMap()): List<PanelSpec> {
    if (layout == null || layout.isEmpty) return panels
    val (headers, houseNamed) = panels.partition { it.title.isBlank() }
    val named = houseNamed + ownPanels(layout, pages)
    val kept = orderedBy(named, layout.order) { sectionId(it) }.filter { sectionId(it) !in layout.hidden }.map { panel ->
        val prefs = layout.cards[sectionId(panel)] ?: return@map panel
        val ids = cardIds(panel.cards)
        val entries = panel.cards.zip(ids)
        val ordered = orderedBy(entries, prefs.order) { it.second }.filter { it.second !in prefs.hidden }.map { it.first }
        panel.copy(cards = ordered, extra = prefs.added.mapNotNull { resolveAdded(it, pages) })
    }
    return headers + kept
}

/** A card that can be put in one of a person's own sections: where it is in the house's layout, and the card itself. */
data class AddableCard(val ref: OwnCardRef, val card: CardSpec, val pageLabel: String)

val PAGE_LABELS = mapOf(
    "lighting" to "Lighting", "security" to "Security", "climate" to "Climate", "cleaning" to "Cleaning", "todo" to "To-do", "devices" to "Devices", "energy" to "Energy",
)

/** Cards that make no sense on their own (a page's header, the purifier's schedule, the stacks that only hold other cards). */
private val NOT_ADDABLE = setOf("custom:purifier-schedule-card", "custom:auto-entities")

/** Every card of the house's other pages a person can add to their own sections, by page (the Home page's own cards are drawn by the app, so not these). */
fun addableCards(pages: Map<String, List<PanelSpec>>, admin: Boolean): List<AddableCard> =
    PAGE_LABELS.flatMap { (page, label) ->
        pages[page].orEmpty().filter { it.title.isNotBlank() }.flatMap { panel ->
            val ids = cardIds(panel.cards)
            panel.cards.mapIndexedNotNull { i, card ->
                if (card.type.startsWith("header:") || card.type.removeSuffix("-beta") in NOT_ADDABLE) return@mapIndexedNotNull null
                if (!admin && card.config.optBoolean("admin_only")) return@mapIndexedNotNull null
                AddableCard(OwnCardRef(page, panel.title, ids[i]), card, label)
            }
        }
    }

/**
 * What kind of thing a house section holds, which decides what a person can add to it: its cards of the same kind and single lights or devices of that
 * kind. On Home the sections are named; on the other pages the whole page is one kind. Null where nothing can be added.
 */
fun sectionKind(page: String, title: String): String? = when (page) {
    "home" -> when (title) { "Lights" -> "lights"; "Security" -> "security"; "Climate" -> "climate"; "Cleaning" -> "cleaning"; else -> null }
    "security" -> "security"
    "climate" -> "climate"
    "cleaning" -> "cleaning"
    "todo" -> "todo"
    else -> null
}

/** The house page a kind's cards come from. */
fun kindPage(kind: String): String? = when (kind) {
    "lights" -> "lighting"; "security" -> "security"; "climate" -> "climate"; "cleaning" -> "cleaning"; "todo" -> "todo"; else -> null
}

private val SECURITY_CLASSES = setOf("door", "window", "motion", "occupancy", "smoke", "carbon_monoxide", "tamper", "garage_door", "opening", "lock", "vibration", "moisture")
private val CLIMATE_CLASSES = setOf("temperature", "humidity", "aqi", "pm25", "pm10", "co2", "volatile_organic_compounds", "atmospheric_pressure")

/** Whether a light or device is of [kind]: what a person can add to that kind of section as a single item. */
fun isOfKind(kind: String, entityId: String, deviceClass: String?): Boolean {
    val domain = entityId.substringBefore('.')
    return when (kind) {
        "lights" -> domain == "light"
        "security" -> domain in setOf("alarm_control_panel", "lock", "camera", "siren") || (domain == "binary_sensor" && deviceClass in SECURITY_CLASSES)
        "climate" -> domain in setOf("climate", "humidifier", "fan") || (domain == "sensor" && deviceClass in CLIMATE_CLASSES)
        "cleaning" -> domain == "vacuum"
        "todo" -> domain == "todo"
        else -> false
    }
}

fun PageLayout.withAdded(section: String, item: AddedItem): PageLayout {
    val prefs = cards[section] ?: CardPrefs()
    if (item in prefs.added) return this
    return copy(cards = cards + (section to prefs.copy(added = prefs.added + item)))
}

fun PageLayout.withoutAdded(section: String, item: AddedItem): PageLayout {
    val prefs = cards[section] ?: return this
    return copy(cards = cards + (section to prefs.copy(added = prefs.added - item)))
}

/** The ids of the sections in the order they are shown for this person: their order first, then the rest. */
fun effectiveSectionOrder(ids: List<String>, layout: PageLayout?): List<String> = orderedBy(ids, layout?.order.orEmpty()) { it }

fun effectiveCardOrder(ids: List<String>, layout: PageLayout?, section: String): List<String> = orderedBy(ids, layout?.cards?.get(section)?.order.orEmpty()) { it }

private fun <T> moved(list: List<T>, item: T, delta: Int): List<T> {
    val i = list.indexOf(item)
    val j = i + delta
    if (i < 0 || j < 0 || j >= list.size) return list
    return list.toMutableList().also { java.util.Collections.swap(it, i, j) }
}

/** Section [id] moved up or down one place among [ids] (the sections as they are shown now). */
fun movedSection(layout: PageLayout, ids: List<String>, id: String, delta: Int): PageLayout =
    layout.copy(order = moved(effectiveSectionOrder(ids, layout), id, delta))

fun toggledSection(layout: PageLayout, id: String): PageLayout =
    layout.copy(hidden = if (id in layout.hidden) layout.hidden - id else layout.hidden + id)

/** Card [card] of section [section] moved up or down one place among [ids] (the section's cards as they are shown now). */
fun movedCard(layout: PageLayout, section: String, ids: List<String>, card: String, delta: Int): PageLayout {
    val prefs = layout.cards[section] ?: CardPrefs()
    val next = prefs.copy(order = moved(effectiveCardOrder(ids, layout, section), card, delta))
    return layout.copy(cards = layout.cards + (section to next))
}

fun toggledCard(layout: PageLayout, section: String, card: String): PageLayout {
    val prefs = layout.cards[section] ?: CardPrefs()
    val next = prefs.copy(hidden = if (card in prefs.hidden) prefs.hidden - card else prefs.hidden + card)
    return layout.copy(cards = layout.cards + (section to next))
}

/** [layout] with [change] made to [page], forgetting a page left with no changes. */
fun UserLayout.changed(page: String, change: (PageLayout) -> PageLayout): UserLayout {
    val changedPage = change(pages[page] ?: PageLayout())
    // A section left with no changes is forgotten, and so is a page left with none.
    val next = changedPage.copy(cards = changedPage.cards.filterValues { it.order.isNotEmpty() || it.hidden.isNotEmpty() || it.added.isNotEmpty() })
    return UserLayout(if (next.isEmpty) pages - page else pages + (page to next))
}

/** The house's layout for [page] again: every change to it, and the person's own sections on it, forgotten. */
fun UserLayout.reset(page: String): UserLayout = UserLayout(pages - page)

fun parseUserLayout(result: Any?): UserLayout? {
    val pagesJson = (result as? JSONObject)?.optJSONObject("pages") ?: return null
    fun strings(a: JSONArray?): List<String> = (0 until (a?.length() ?: 0)).mapNotNull { a?.optString(it)?.takeIf { s -> s.isNotBlank() } }
    val pages = pagesJson.keys().asSequence().associateWith { page ->
        val p = pagesJson.optJSONObject(page) ?: JSONObject()
        val cardsJson = p.optJSONObject("cards")
        val cards = cardsJson?.keys()?.asSequence()?.associateWith { section ->
            val prefs = cardsJson.optJSONObject(section) ?: JSONObject()
            val addedJson = prefs.optJSONArray("added")
            val added = (0 until (addedJson?.length() ?: 0)).mapNotNull { i ->
                val o = addedJson?.optJSONObject(i) ?: return@mapNotNull null
                val card = o.optJSONObject("card")?.let { OwnCardRef(it.optString("page"), it.optString("section"), it.optString("card")) }
                val entity = o.optString("entity").takeIf { it.isNotBlank() }
                if (card == null && entity == null) null else AddedItem(card, entity)
            }
            CardPrefs(strings(prefs.optJSONArray("order")), strings(prefs.optJSONArray("hidden")).toSet(), added)
        } ?: emptyMap()
        val ownJson = p.optJSONArray("own")
        val own = (0 until (ownJson?.length() ?: 0)).mapNotNull { i ->
            val o = ownJson?.optJSONObject(i) ?: return@mapNotNull null
            val refs = o.optJSONArray("cards")
            OwnSection(
                o.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null, o.optString("title"), o.optString("icon").ifBlank { "cd:palette" },
                o.optString("tone").ifBlank { "auto" }, o.optBoolean("dynamic"),
                (0 until (refs?.length() ?: 0)).mapNotNull { k ->
                    val r = refs?.optJSONObject(k) ?: return@mapNotNull null
                    OwnCardRef(r.optString("page"), r.optString("section"), r.optString("card"))
                },
            )
        }
        PageLayout(strings(p.optJSONArray("order")), strings(p.optJSONArray("hidden")).toSet(), cards, own)
    }.filterValues { !it.isEmpty }
    return UserLayout(pages)
}

/** The body of `church_drive/layout/set` for [layout]. */
fun userLayoutBody(layout: UserLayout): JSONObject {
    val pages = JSONObject()
    layout.pages.forEach { (name, p) ->
        val cards = JSONObject()
        p.cards.forEach { (section, prefs) -> cards.put(
            section,
            JSONObject().put("order", JSONArray(prefs.order)).put("hidden", JSONArray(prefs.hidden.toList())).put(
                "added",
                JSONArray(prefs.added.map { a -> JSONObject().apply { a.entity?.let { put("entity", it) }; a.card?.let { c -> put("card", JSONObject().put("page", c.page).put("section", c.section).put("card", c.card)) } } }),
            ),
        ) }
        val own = JSONArray(p.own.map { s ->
            JSONObject().put("id", s.id).put("title", s.title).put("icon", s.icon).put("tone", s.tone).put("dynamic", s.dynamic)
                .put("cards", JSONArray(s.cards.map { JSONObject().put("page", it.page).put("section", it.section).put("card", it.card) }))
        })
        pages.put(name, JSONObject().put("order", JSONArray(p.order)).put("hidden", JSONArray(p.hidden.toList())).put("cards", cards).put("own", own))
    }
    return JSONObject().put("layout", JSONObject().put("pages", pages))
}
