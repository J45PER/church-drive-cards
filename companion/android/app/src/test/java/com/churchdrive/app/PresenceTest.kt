package com.churchdrive.app

import com.churchdrive.app.ui.Presence
import com.churchdrive.app.ui.parsePresence
import com.churchdrive.app.ui.presenceFraction
import com.churchdrive.app.ui.presenceLine
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PresenceTest {
    private val answer = JSONObject(
        """{"lights":{"light.a":{"present":true,"off_in":null,"delay":20,"sensor":"Cloakroom"},
            "light.b":{"present":false,"off_in":12,"delay":20,"sensor":"Hall"}}}""",
    )

    @Test fun theHousesAnswerIsRead() {
        val m = parsePresence(answer, 1000L)!!
        assertEquals(true, m["light.a"]!!.present)
        assertNull(m["light.a"]!!.offIn)
        assertEquals(12, m["light.b"]!!.offIn)
        assertNull(parsePresence(null, 0L))
    }

    @Test fun theCountdownRunsDownFromWhenTheHouseAnswered() {
        val p = parsePresence(answer, 1000L)!!["light.b"]!!
        assertEquals(12, p.remaining(1000L))
        assertEquals(7, p.remaining(6000L))
        assertEquals(0, p.remaining(60_000L))
    }

    @Test fun theLineAndBarFollowThePresence() {
        val here = Presence(true, null, 20, "S")
        assertEquals("Someone's in", presenceLine(here, null))
        assertEquals(1f, presenceFraction(here, null), 0f)
        val going = Presence(false, 10, 20, "S")
        assertEquals("Off in 5 s", presenceLine(going, 5))
        assertEquals(0.25f, presenceFraction(going, 5), 0.001f)
        val empty = Presence(false, null, 20, "S")
        assertEquals("Nobody in", presenceLine(empty, null))
        assertEquals(0f, presenceFraction(empty, null), 0f)
    }
}
