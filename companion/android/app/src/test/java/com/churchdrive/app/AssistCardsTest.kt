package com.churchdrive.app

import androidx.compose.ui.graphics.Color
import com.churchdrive.app.ui.Tone
import com.churchdrive.app.ui.carBatteryColour
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
        for (k in listOf("people", "done", "car", "scores")) assertNotNull(parseAssistantCard(JSONObject("""{"kind":"$k"}""")))
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
}
