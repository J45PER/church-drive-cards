package com.churchdrive.app

import com.churchdrive.app.ui.mapsWebUrl
import com.churchdrive.app.ui.navigationUri
import com.churchdrive.app.ui.ShoppingPlace
import com.churchdrive.app.ui.optionLabel
import com.churchdrive.app.ui.placeMapHtml
import com.churchdrive.app.ui.parsePlace
import com.churchdrive.app.ui.ShoppingOption
import com.churchdrive.app.ui.openLists
import com.churchdrive.app.ui.parseShopping
import com.churchdrive.app.ui.parseShoppingChoice
import com.churchdrive.app.ui.shoppingConfirm
import com.churchdrive.app.ui.shoppingIdFromLink
import com.churchdrive.app.ui.shoppingIdOf
import com.churchdrive.app.ui.shoppingNote
import com.churchdrive.app.ui.splitItems
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ShoppingDataTest {
    private val reply = JSONObject(
        """{"mine":"Jamie","missed":[{"text":"Eggs"}],"people":["Hayley","Jamie"],
        "lists":[
          {"id":"aaaaaa111111","name":"Weekly","people":["Jamie","Hayley"],"date":"2026-10-14","time":"09:30","places":[{"name":"Lidl Protaras","address":"Protaras, Cyprus","lat":35.0123,"lon":34.0567}],"done":false,
           "items":[{"id":"i1","text":"Milk","status":"open"},{"id":"i2","text":"Bread","status":"bought"}]},
          {"id":"bbbbbb222222","name":"","people":["Hayley"],"date":"","places":[],"done":false,"items":[]},
          {"id":"cccccc333333","name":"Old","people":["Jamie"],"date":"2026-10-01","places":[],"done":true,"items":[]}]}""",
    )

    @Test
    fun readsTheListsAndWhatWasMissed() {
        val s = parseShopping(reply)!!
        assertEquals(3, s.lists.size)
        assertEquals(listOf("Eggs"), s.missed)
        assertEquals("Lidl Protaras", s.lists[0].places.single().name)
        assertEquals("09:30", s.lists[0].time)
        assertEquals("", s.lists[1].time)
        assertEquals(1, s.lists[0].open)
        assertEquals("Shopping", s.lists[1].title)
        assertNull(parseShopping(null))
        assertNull(parseShopping(JSONObject("{}")))
    }

    @Test
    fun openListsAreSoonestFirstAndOnlyThePersons() {
        val s = parseShopping(reply)!!
        assertEquals(listOf("aaaaaa111111", "bbbbbb222222"), openLists(s).map { it.id })
        assertEquals(listOf("aaaaaa111111"), openLists(s, "Jamie").map { it.id })
    }

    @Test
    fun aTaskPointsAtItsListThroughTheMarker() {
        val d = "• Milk\n• Eggs\nchurch-drive-shopping:aaaaaa111111"
        assertEquals("aaaaaa111111", shoppingIdOf(d))
        assertEquals("", shoppingIdOf("Mon 09:00 · for Hayley"))
        assertEquals("", shoppingIdOf(null))
        assertEquals("2 to get", shoppingNote(d))
    }

    @Test
    fun aReminderLinkOpensTheList() {
        assertEquals("aaaaaa111111", shoppingIdFromLink("/dashboard-mobile/shopping?list=aaaaaa111111"))
        assertNull(shoppingIdFromLink("/dashboard-mobile/todo"))
        assertNull(shoppingIdFromLink(null))
    }

    @Test
    fun splitsWhatWasTyped() {
        assertEquals(listOf("milk", "eggs", "bread"), splitItems("milk, eggs\n bread ;"))
        assertEquals(emptyList<String>(), splitItems("  "))
    }

    @Test
    fun sharedItemsAskWhereToGo() {
        val c = parseShoppingChoice(JSONObject("""{"lists":[{"id":"aaaaaa111111","name":"Weekly","date":"","open":3}],"default":"aaaaaa111111","missed":["Eggs"]}"""))
        assertNotNull(c)
        assertEquals("aaaaaa111111", c!!.default)
        assertEquals("Weekly (3)", optionLabel(ShoppingOption("a", "Weekly", "", 3)))
        assertNull(parseShoppingChoice(null))
        assertEquals("new", shoppingConfirm("new", " Big shop ").getString("target"))
        assertEquals("Big shop", shoppingConfirm("new", " Big shop ").getString("name"))
        assertEquals(false, shoppingConfirm("aaaaaa111111", "x").has("name"))
    }

    @Test
    fun aPlaceStartsNavigationInGoogleMaps() {
        val p = parseShopping(reply)!!.lists[0].places.single()
        assertEquals("google.navigation:q=35.0123,34.0567", navigationUri(p))
        assertEquals("https://www.google.com/maps/dir/?api=1&destination=35.0123,34.0567", mapsWebUrl(p))
        assertEquals(p, parsePlace(p.toJson()))
        assertNull(parsePlace(JSONObject("""{"name":"x"}""")))
    }

    @Test
    fun theMapPageHasAPinPerResultAndNeverBreaksOutOfItsScript() {
        val p = ShoppingPlace("Lidl </script>", "Protaras", 35.0, 34.0)
        val html = placeMapHtml(listOf(p), emptyList())
        assertEquals(1, Regex("""</script>""").findAll(html.substringAfter("var results=")).count())
        assert(html.contains("35.0"))
    }
}
