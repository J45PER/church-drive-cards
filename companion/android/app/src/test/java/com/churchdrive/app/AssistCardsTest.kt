package com.churchdrive.app

import androidx.compose.ui.graphics.Color
import com.churchdrive.app.ui.Tone
import com.churchdrive.app.ui.cardBrand
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
        assertEquals("Cooling" to Tone.Blue, climatePill("cooling"))
        assertEquals("Off" to Tone.Grey, climatePill("off"))
        assertEquals("Off" to Tone.Grey, climatePill(""))
    }

    @Test fun aClimateCardIsKept() {
        val card = parseAssistantCard(JSONObject("""{"kind":"climate","rows":[{"name":"Downstairs","temp":19.7,"state":"idle"}],"outside":11.2}"""))
        assertNotNull(card)
        assertEquals("climate", card!!.kind)
    }

    @Test fun carBatteryFollowsTheDashboardBands() {
        assertEquals(Color(0xFFEF5350), carBatteryColour(19.0, ok))
        assertEquals(Color(0xFFFFA726), carBatteryColour(20.0, ok))
        assertEquals(Color(0xFFFFA726), carBatteryColour(39.0, ok))
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
        val set = JSONObject(File("src/main/assets/si-icons.json").readText())
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
        val si = JSONObject(File("src/main/assets/si-icons.json").readText())
        val own = JSONObject(File("src/main/assets/own-logos.json").readText())
        for (slug in listOf("primevideo", "bbciplayer", "amazonmusic", "landrover", "mercedes", "premierleague", "fifa", "vauxhall", "airplayvideo")) assertEquals(slug, true, si.optString(slug).isNotBlank())
        for (slug in listOf("championsleague", "europaleague", "conferenceleague", "facup", "eflleague", "carabaocup", "scottishpremiership", "nationsleague", "disneyplus", "cupra", "octopusenergy")) {
            assertEquals(slug, true, own.optString(slug).startsWith("M"))
        }
    }
}
