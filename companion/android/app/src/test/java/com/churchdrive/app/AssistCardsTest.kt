package com.churchdrive.app

import androidx.compose.ui.graphics.Color
import com.churchdrive.app.ui.CliveColours
import com.churchdrive.app.ui.Tone
import com.churchdrive.app.ui.DoneMotion
import com.churchdrive.app.ui.cardBrand
import com.churchdrive.app.ui.doneMotion
import com.churchdrive.app.ui.parseDoneRules
import com.churchdrive.app.ui.AssistantCard
import com.churchdrive.app.ui.cardIcon
import com.churchdrive.app.ui.cardRows
import com.churchdrive.app.ui.carBatteryColour
import com.churchdrive.app.ui.sportIcon
import com.churchdrive.app.ui.climatePill
import com.churchdrive.app.ui.parseAssistantCard
import com.churchdrive.app.ui.personPill
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import com.churchdrive.app.ui.SimpleIcons
import java.io.File

class AssistCardsTest {
    private val ok = Color(0xFF00838F)

    @Test fun aHeatingZoneIsColouredAsOnTheDashboard() {
        assertEquals("Heating" to Tone.Orange, climatePill("heating"))
        assertEquals("Idle" to Tone.Orange, climatePill("idle"))
        assertEquals("Eco" to Tone.Green, climatePill("Eco"))
        assertEquals("Cooling" to Tone.Cooling, climatePill("cooling"))
        assertEquals("Off" to Tone.Grey, climatePill("off"))
        assertEquals("Off" to Tone.Grey, climatePill(""))
    }

    @Test fun aClimateCardIsKept() {
        val card = parseAssistantCard(JSONObject("""{"kind":"climate","rows":[{"name":"Downstairs","temp":19.7,"state":"idle"}],"outside":11.2}"""))
        assertNotNull(card)
        assertEquals("climate", card!!.kind)
    }

    @Test fun carBatteryFollowsTheDashboardBands() {
        assertEquals(CliveColours.Bad.colour, carBatteryColour(19.0, ok))
        assertEquals(CliveColours.Warn.colour, carBatteryColour(20.0, ok))
        assertEquals(CliveColours.Warn.colour, carBatteryColour(39.0, ok))
        assertEquals(ok, carBatteryColour(40.0, ok))
        assertEquals(Color.Gray, carBatteryColour(null, ok))
    }

    @Test fun peopleAreColouredByWhereTheyAre() {
        assertEquals("Home" to Tone.Green, personPill("home"))
        assertEquals("Away" to Tone.Grey, personPill("not_home"))
        assertEquals("Unknown" to Tone.Amber, personPill("unknown"))
        assertEquals("Work" to Tone.Teal, personPill("Work"))
    }

    @Test fun theNewCardKindsAreAccepted() {
        for (k in listOf("people", "done", "car", "scores", "fixtures")) assertNotNull(parseAssistantCard(JSONObject("""{"kind":"$k"}""")))
        assertNull(parseAssistantCard(JSONObject("""{"kind":"nonsense"}""")))
    }

    @Test fun listsInACardKeepTheirRowsAndDropUnsafeAddresses() {
        val card = parseAssistantCard(JSONObject("""{"kind":"scores","rows":[{"home":{"name":"A","badge_url":"http://x"},"away":{"name":"B"}}]}"""))!!
        val row = card.fields.getJSONArray("rows").getJSONObject(0)
        assertEquals("A", row.getJSONObject("home").getString("name"))
        assertEquals(false, row.getJSONObject("home").has("badge_url"))
    }

    @Test fun serviceNamesBecomeSimpleIconsNames() {
        assertEquals("youtubemusic", SimpleIcons.slugFor("YouTube Music"))
        assertEquals("disneyplus", SimpleIcons.slugFor("Disney+"))
        assertEquals("googlecast", SimpleIcons.slugFor("Chromecast"))
        assertEquals("spotify", SimpleIcons.slugFor(" Spotify "))
    }

    @Test fun theBrandLogoSetHasTheCommonServices() {
        val set = JSONObject(File("../core/src/main/assets/si-icons.json").readText())
        for (slug in listOf("spotify", "netflix", "youtube", "plex", "applemusic", "googlecast", "homeassistant")) {
            assertEquals(slug, true, set.optString(slug).isNotBlank())
        }
    }

    @Test fun everyNewCardKindIsAccepted() {
        for (k in listOf("energy", "secure", "doors", "tasks", "devices", "lights", "heat", "shop", "event", "reminder", "media", "answer"))
            assertNotNull(parseAssistantCard(JSONObject("""{"kind":"$k","title":"x"}""")))
    }

    @Test fun sportsShowTheirOwnIcon() {
        assertEquals("mdi:soccer", sportIcon(""))
        assertEquals("mdi:tennis-ball", sportIcon("Tennis"))
        assertEquals("mdi:golf", sportIcon("golf"))
        for (kind in listOf("score", "scores", "fixture", "fixtures")) assertEquals("mdi:tennis-ball", cardIcon(parseAssistantCard(JSONObject("""{"kind":"$kind","sport":"tennis"}"""))!!))
    }

    @Test fun logosStandInForIconsWhenTheHouseNamesThem() {
        assertEquals("Spotify", cardBrand(parseAssistantCard(JSONObject("""{"kind":"music","app":"Spotify"}"""))!!))
        assertEquals("Vauxhall", cardBrand(parseAssistantCard(JSONObject("""{"kind":"car","name":"Vauxhall Astra"}"""))!!))
        assertEquals("premierleague", cardBrand(parseAssistantCard(JSONObject("""{"kind":"fixture","comp":"Premier League"}"""))!!))
        assertNull(cardBrand(parseAssistantCard(JSONObject("""{"kind":"weather"}"""))!!))
    }

    @Test fun listCardsKeepTheirRows() {
        val card = parseAssistantCard(JSONObject("""{"kind":"doors","rows":[{"name":"Back door","pill":"Open","tone":"amber"},{"name":"Front door"}]}"""))!!
        assertEquals(listOf("Back door", "Front door"), cardRows(card.fields).map { it.name })
        assertEquals("amber", cardRows(card.fields)[0].tone)
    }

    private fun brandOf(json: String) = cardBrand(parseAssistantCard(JSONObject(json))!!)

    @Test fun competitionsFindTheirLogoName() {
        assertEquals("premierleague", brandOf("""{"kind":"fixture","comp":"Premier League"}"""))
        assertEquals("eflleague", brandOf("""{"kind":"score","comp":"Sky Bet Championship"}"""))
        assertEquals("eflleague", brandOf("""{"kind":"scores","comp":"EFL League One"}"""))
        assertEquals("eflleague", brandOf("""{"kind":"fixtures","comp":"Championship"}"""))
        assertEquals("eflleague", brandOf("""{"kind":"fixture","comp":"League Two"}"""))
        assertEquals("carabaocup", brandOf("""{"kind":"fixture","comp":"Carabao Cup"}"""))
        assertEquals("facup", brandOf("""{"kind":"fixture","comp":"Emirates FA Cup"}"""))
        assertEquals("conferenceleague", brandOf("""{"kind":"fixture","comp":"UEFA Europa Conference League"}"""))
        assertEquals("championsleague", brandOf("""{"kind":"score","comp":"UEFA Champions League"}"""))
        assertEquals("europaleague", brandOf("""{"kind":"score","comp":"Europa League"}"""))
        assertEquals("europeanchampionship", brandOf("""{"kind":"score","comp":"European Championship"}"""))
        assertNull(brandOf("""{"kind":"score","comp":"Sunday league"}"""))
        assertEquals("Octopus Energy", brandOf("""{"kind":"energy","brand":"Octopus Energy"}"""))
    }

    @Test fun moreServiceNamesBecomeLogoNames() {
        assertEquals("mercedes", SimpleIcons.slugFor("Mercedes-Benz"))
        assertEquals("airplayvideo", SimpleIcons.slugFor("AirPlay"))
        assertEquals("primevideo", SimpleIcons.slugFor("Prime Video"))
        assertEquals("bbc", SimpleIcons.slugFor("BBC Sounds"))
    }

    @Test fun everyLogoNameTheCardsCanAskForHasALogo() {
        val si = JSONObject(File("../core/src/main/assets/si-icons.json").readText())
        val own = JSONObject(File("../core/src/main/assets/own-logos.json").readText())
        for (slug in listOf("primevideo", "bbciplayer", "amazonmusic", "landrover", "mercedes", "premierleague", "fifa", "vauxhall", "airplayvideo")) assertEquals(slug, true, si.optString(slug).isNotBlank())
        for (slug in listOf("championsleague", "europaleague", "conferenceleague", "facup", "eflleague", "carabaocup", "scottishpremiership", "nationsleague", "disneyplus", "cupra", "octopusenergy")) {
            assertEquals(slug, true, own.optString(slug).startsWith("M"))
        }
    }

    /** The app's own copy of the rules the house serves. */
    private val served = JSONObject(File("../core/src/main/assets/clive-assistant.json").readText())
    private val rules = parseDoneRules(served.getJSONArray("motions"))
    private fun done(json: String) = doneMotion(AssistantCard("done", JSONObject(json)), rules)
    private val up = DoneMotion.Wipe("cd:blind-closed", "cd:blind-open", down = false, ms = 1200)
    private val down = DoneMotion.Wipe("cd:blind-open", "cd:blind-closed", down = true, ms = 1200)

    @Test fun somethingDoneToTheHouseShowsItHappening() {
        // The two cards the house really sent: a blind opening, and lights turned off.
        assertEquals(up, done("""{"name":"Hayley's bedroom blind","detail":"Opening","icon":"mdi:blinds-open"}"""))
        assertEquals(DoneMotion.Swap("cd:bulb-lit", "mdi:lightbulb-off", 450), done("""{"name":"Hayley's bedroom lights","detail":"Turned off","icon":"mdi:lightbulb-off","percent":0}"""))
        assertEquals(down, done("""{"detail":"Closing","icon":"mdi:blinds-open"}"""))
        assertEquals(down, done("""{"icon":"mdi:blinds"}"""))
        assertEquals(up, done("""{"detail":"Upstairs","icon":"mdi:roller-shade"}"""))
        assertEquals(DoneMotion.Swap("cd:bulb", "cd:bulb-lit", 450), done("""{"detail":"Turned on","icon":"mdi:lightbulb"}"""))
        assertEquals(DoneMotion.Spin("cd:fan", 1100, 3), done("""{"detail":"On, speed 2","icon":"mdi:fan"}"""))
        assertEquals(DoneMotion.Swap("cd:lock-open", "cd:lock", 650), done("""{"detail":"Locked","icon":"mdi:lock"}"""))
        assertEquals(DoneMotion.Swap("cd:lock", "cd:lock-open", 650), done("""{"detail":"Unlocked","icon":"mdi:lock"}"""))
        assertEquals(DoneMotion.Cycle(listOf("cd:volume-low", "cd:volume-medium", "cd:volume-high"), 480), done("""{"detail":"Volume 40%","icon":"mdi:volume-high"}"""))
    }

    @Test fun aPlainDoneKeepsItsTick() {
        assertNull(done("""{"name":"Alarm","detail":"Armed away","icon":"mdi:shield-lock"}"""))
        assertNull(done("""{"name":"Energy","icon":"mdi:lightning-bolt"}"""))
        assertNull(done("""{"name":"Fan","detail":"Turned off","icon":"mdi:fan"}"""))
        assertNull(done("""{"name":"Blind","detail":"Stopped","icon":"mdi:blinds"}"""))
        assertNull(done("""{"name":"Done"}"""))
        // A failure shows its cross, never a motion.
        assertNull(done("""{"ok":false,"detail":"Opening","icon":"mdi:blinds-open"}"""))
        assertNull(doneMotion(AssistantCard("lights", JSONObject("""{"icon":"mdi:lightbulb"}""")), rules))
        // With no rules at all (a house that sent none, and no copy), everything is a tick.
        assertNull(doneMotion(AssistantCard("done", JSONObject("""{"icon":"mdi:blinds-open"}""")), emptyList()))
    }

    @Test fun everyIconAMotionNamesIsOneTheAppCanDraw() {
        val all = rules.mapNotNull { it.motion }.flatMap { m ->
            when (m) {
                is DoneMotion.Swap -> listOf(m.from, m.to)
                is DoneMotion.Wipe -> listOf(m.from, m.to)
                is DoneMotion.Spin -> listOf(m.icon)
                is DoneMotion.Cycle -> m.icons
            }
        }.toSet()
        val missing = all.filter { com.churchdrive.app.ui.CdIcons.alias(it)?.removePrefix("cd:")?.let { n -> com.churchdrive.app.ui.CdIcons.icons.containsKey(n) } != true }
        assertEquals("icons a motion names that the app has no drawing for: $missing", emptyList<String>(), missing)
    }

    @Test fun aRuleTheAppCannotReadIsLeftOut() {
        val list = org.json.JSONArray("""[{"says":["open"],"motion":{"type":"swap","from":"cd:a","to":"cd:b"}},{"icon":["kettle"],"motion":{"type":"explode"}},{"icon":["door"],"motion":{"type":"swap","from":"cd:door"}}]""")
        val parsed = parseDoneRules(list)
        assertEquals(2, parsed.size)
        assertNull(parsed[0].motion)
        assertNull(parsed[1].motion)
    }
}
