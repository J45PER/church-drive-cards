package com.churchdrive.app

import androidx.compose.ui.graphics.Color
import com.churchdrive.app.ui.Tone
import com.churchdrive.app.ui.carBatteryColour
import com.churchdrive.app.ui.parseAssistantCard
import com.churchdrive.app.ui.personPill
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AssistCardsTest {
    private val ok = Color(0xFF00838F)

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
}
