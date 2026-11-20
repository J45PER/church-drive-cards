package com.churchdrive.app

import com.churchdrive.app.ui.CardPrefs
import com.churchdrive.app.ui.CardSpec
import com.churchdrive.app.ui.OwnCardRef
import com.churchdrive.app.ui.OwnSection
import com.churchdrive.app.ui.PageLayout
import com.churchdrive.app.ui.PanelSpec
import com.churchdrive.app.ui.Tone
import com.churchdrive.app.ui.UserLayout
import com.churchdrive.app.ui.addableCards
import com.churchdrive.app.ui.autoTone
import com.churchdrive.app.ui.ownPanels
import com.churchdrive.app.ui.ownTone
import com.churchdrive.app.ui.resolveOwnCard
import com.churchdrive.app.ui.sectionId
import com.churchdrive.app.ui.applyPageLayout
import com.churchdrive.app.ui.cardIds
import com.churchdrive.app.ui.cardLabel
import com.churchdrive.app.ui.changed
import com.churchdrive.app.ui.effectiveSectionOrder
import com.churchdrive.app.ui.movedCard
import com.churchdrive.app.ui.movedSection
import com.churchdrive.app.ui.orderedBy
import com.churchdrive.app.ui.parseUserLayout
import com.churchdrive.app.ui.reset
import com.churchdrive.app.ui.toggledCard
import com.churchdrive.app.ui.toggledSection
import com.churchdrive.app.ui.userLayoutBody
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PageLayoutTest {
    private fun card(type: String, vararg kv: Pair<String, String>) = CardSpec(type, JSONObject().apply { kv.forEach { put(it.first, it.second) } })
    private fun panel(title: String, vararg cards: CardSpec) = PanelSpec(title, null, null, null, null, cards.toList())

    private val heating = card("custom:climate-card", "entity" to "climate.hall")
    private val fan = card("custom:fan-card", "entity" to "fan.bedroom")
    private val purifier = card("custom:air-purifier-card", "entity" to "fan.purifier")
    private val panels = listOf(
        panel("", card("header:forecast")),
        panel("Heating", heating, fan),
        panel("Cooling", purifier),
        panel("Air Quality", card("tile", "entity" to "sensor.aq")),
    )

    @Test fun noChangesLeavesThePageAsTheHouseHasIt() {
        assertEquals(panels, applyPageLayout(panels, null))
        assertEquals(panels, applyPageLayout(panels, PageLayout()))
    }

    @Test fun sectionsFollowTheirOrderAndNewOnesComeLast() {
        val out = applyPageLayout(panels, PageLayout(order = listOf("Air Quality", "Heating")))
        assertEquals(listOf("", "Air Quality", "Heating", "Cooling"), out.map { it.title })
    }

    @Test fun hiddenSectionsAreLeftOutButAHeaderStays() {
        val out = applyPageLayout(panels, PageLayout(hidden = setOf("Cooling", "Heating")))
        assertEquals(listOf("", "Air Quality"), out.map { it.title })
    }

    @Test fun cardsAreOrderedAndHiddenInsideTheirSection() {
        val ids = cardIds(listOf(heating, fan))
        val layout = PageLayout(cards = mapOf("Heating" to CardPrefs(order = listOf(ids[1], ids[0]), hidden = emptySet())))
        assertEquals(listOf(fan, heating), applyPageLayout(panels, layout).first { it.title == "Heating" }.cards)
        val hide = PageLayout(cards = mapOf("Heating" to CardPrefs(hidden = setOf(ids[0]))))
        assertEquals(listOf(fan), applyPageLayout(panels, hide).first { it.title == "Heating" }.cards)
    }

    @Test fun aNewCardInTheHouseShowsAtTheEnd() {
        val ids = cardIds(listOf(heating, fan))
        val layout = PageLayout(cards = mapOf("Heating" to CardPrefs(order = listOf(ids[1]))))
        val extra = card("custom:cover-card", "entity" to "cover.blind")
        val withNew = listOf(panel("Heating", heating, fan, extra))
        assertEquals(listOf(fan, heating, extra), applyPageLayout(withNew, layout).single().cards)
    }

    @Test fun twoAlikeCardsGetDifferentIds() {
        val ids = cardIds(listOf(heating, heating, fan))
        assertEquals(3, ids.toSet().size)
        assertTrue(ids[0].startsWith("custom:climate-card|climate.hall"))
    }

    @Test fun anAreaOrEntityMakesTheCardsId() {
        assertEquals("custom:light-control-card|kitchen", cardIds(listOf(card("custom:light-control-card", "area" to "kitchen", "entity" to "light.x"))).single())
        assertEquals("tile|", cardIds(listOf(card("tile"))).single())
        assertEquals("custom:fan-card|fan.bedroom", cardIds(listOf(card("custom:fan-card-beta", "entity" to "fan.bedroom"))).single())
    }

    @Test fun orderedByPutsTheListedFirst() {
        assertEquals(listOf("c", "a", "b", "d"), orderedBy(listOf("a", "b", "c", "d"), listOf("c", "a")) { it })
        assertEquals(listOf("a", "b"), orderedBy(listOf("a", "b"), listOf("zzz")) { it })
    }

    @Test fun movingASectionKeepsTheWholeOrder() {
        val ids = listOf("Heating", "Cooling", "Air Quality")
        val first = movedSection(PageLayout(), ids, "Cooling", -1)
        assertEquals(listOf("Cooling", "Heating", "Air Quality"), first.order)
        assertEquals(listOf("Cooling", "Heating", "Air Quality"), effectiveSectionOrder(ids, first))
        assertEquals(first, movedSection(first, ids, "Cooling", -1))
    }

    @Test fun movingTheLastSectionDownDoesNothing() {
        val ids = listOf("a", "b")
        assertEquals(listOf("a", "b"), movedSection(PageLayout(), ids, "b", 1).order)
    }

    @Test fun togglingASectionAndACard() {
        val hidden = toggledSection(PageLayout(), "Cooling")
        assertEquals(setOf("Cooling"), hidden.hidden)
        assertTrue(toggledSection(hidden, "Cooling").hidden.isEmpty())
        val c = toggledCard(PageLayout(), "Heating", "x")
        assertEquals(setOf("x"), c.cards.getValue("Heating").hidden)
        assertTrue(toggledCard(c, "Heating", "x").cards.getValue("Heating").hidden.isEmpty())
    }

    @Test fun movingACardInASection() {
        val l = movedCard(PageLayout(), "Heating", listOf("a", "b", "c"), "c", -1)
        assertEquals(listOf("a", "c", "b"), l.cards.getValue("Heating").order)
    }

    @Test fun aPageWithNoChangesIsForgotten() {
        val user = UserLayout().changed("climate") { toggledSection(it, "Cooling") }
        assertEquals(setOf("climate"), user.pages.keys)
        assertTrue(user.changed("climate") { toggledSection(it, "Cooling") }.pages.isEmpty())
        assertTrue(user.reset("climate").pages.isEmpty())
    }

    @Test fun theHousesAnswerRoundTrips() {
        val user = UserLayout(
            mapOf(
                "climate" to PageLayout(listOf("Cooling"), setOf("Air Quality"), mapOf("Heating" to CardPrefs(listOf("a", "b"), setOf("c")))),
                "home" to PageLayout(own = listOf(OwnSection("s1", "Evenings", "cd:moon", "lights", true, listOf(OwnCardRef("lighting", "Ground Floor", "x"))))),
            ),
        )
        val body = userLayoutBody(user).getJSONObject("layout")
        assertEquals(user, parseUserLayout(body))
    }

    @Test fun nothingBackMeansNoLayout() {
        assertNull(parseUserLayout(null))
        assertNotNull(parseUserLayout(JSONObject("""{"pages":{}}""")))
        assertTrue(parseUserLayout(JSONObject("""{"pages":{"home":{}}}"""))!!.pages.isEmpty())
    }

    @Test fun aCardIsNamedByItsConfigThenItsEntity() {
        assertEquals("Spare room", cardLabel(card("custom:fan-card", "name" to "Spare room", "entity" to "fan.x")) { "Fan" })
        assertEquals("Bedroom fan", cardLabel(card("custom:fan-card", "entity" to "fan.bedroom")) { "Bedroom fan" })
        assertEquals("Kitchen", cardLabel(card("custom:light-control-card", "area" to "kitchen")) { null })
        assertEquals("Climate", cardLabel(card("custom:climate-card")) { null })
    }

    private val pages = mapOf(
        "lighting" to listOf(panel("Ground Floor", card("custom:light-control-card", "area" to "kitchen"), card("custom:light-control-card", "area" to "living_room"))),
        "climate" to listOf(panel("", card("header:forecast")), panel("Heating", heating, card("custom:fan-card", "entity" to "fan.bedroom", "admin_only" to "true"))),
    )

    @Test fun anOwnSectionFindsItsCardsInTheHousesPages() {
        val kitchen = cardIds(pages.getValue("lighting").first().cards).first()
        val ref = OwnCardRef("lighting", "Ground Floor", kitchen)
        assertEquals(pages.getValue("lighting").first().cards.first(), resolveOwnCard(ref, pages))
        assertNull(resolveOwnCard(OwnCardRef("lighting", "Nowhere", kitchen), pages))
        assertNull(resolveOwnCard(OwnCardRef("lighting", "Ground Floor", "nope"), pages))
        val layout = PageLayout(own = listOf(OwnSection("s1", "Evenings", "cd:moon", "auto", false, listOf(ref, OwnCardRef("lighting", "Gone", "x")))))
        val panel = ownPanels(layout, pages).single()
        assertEquals("Evenings", panel.title)
        assertEquals("s1", panel.ownId)
        assertEquals(1, panel.cards.size)
    }

    @Test fun ownSectionsJoinTheOrderAndAreNeverHiddenByTheHouse() {
        val layout = PageLayout(
            order = listOf("own:s1", "Heating"),
            own = listOf(OwnSection("s1", "Evenings", "cd:moon", "auto", false, emptyList())),
        )
        val out = applyPageLayout(panels, layout, pages)
        assertEquals(listOf("", "Evenings", "Heating", "Cooling", "Air Quality"), out.map { it.title })
        assertEquals("own:s1", sectionId(out[1]))
    }

    @Test fun anOwnSectionWithTheSameNameAsAHouseOneIsStillItsOwn() {
        val layout = PageLayout(own = listOf(OwnSection("s2", "Heating", "cd:home", "auto", false, emptyList())))
        val out = applyPageLayout(panels, layout, pages)
        assertEquals(listOf("Heating", "Heating"), out.filter { it.title == "Heating" }.map { it.title })
        assertEquals(listOf("Heating", "own:s2"), out.filter { it.title == "Heating" }.map { sectionId(it) })
    }

    @Test fun theSameCardCanBeInTwoOwnSections() {
        val kitchen = OwnCardRef("lighting", "Ground Floor", cardIds(pages.getValue("lighting").first().cards).first())
        val layout = PageLayout(own = listOf(OwnSection("a", "A", "cd:home", "auto", false, listOf(kitchen)), OwnSection("b", "B", "cd:home", "auto", false, listOf(kitchen))))
        assertEquals(listOf(1, 1), ownPanels(layout, pages).map { it.cards.size })
    }

    @Test fun onlyCardsAPersonMayUseCanBeAdded() {
        val admin = addableCards(pages, admin = true)
        val other = addableCards(pages, admin = false)
        assertEquals(4, admin.size)
        assertEquals(3, other.size)
        assertTrue(other.none { it.card.type == "custom:fan-card" })
        assertTrue(admin.none { it.card.type.startsWith("header:") })
        assertEquals(setOf("Lighting", "Climate"), admin.map { it.pageLabel }.toSet())
    }

    @Test fun anAutoSectionTakesTheColourOfWhatIsInside() {
        assertEquals(Tone.Lights, autoTone(listOf(card("custom:light-control-card", "area" to "kitchen"))))
        assertEquals(Tone.Heating, autoTone(listOf(heating)))
        assertEquals(Tone.Grey, autoTone(emptyList()))
        assertEquals(Tone.Lights, ownTone(null, listOf(card("custom:light-control-card")), emptyMap()))
        assertEquals(Tone.Teal, ownTone("teal", listOf(heating), emptyMap()))
        assertEquals(Tone.Heating, ownTone("nonsense", listOf(heating), emptyMap()))
        assertEquals(Tone.Grey, ownTone("dynamic", emptyList(), emptyMap()))
    }
}
