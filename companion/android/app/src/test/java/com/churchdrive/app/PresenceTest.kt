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

    private fun light(id: String, vararg members: String) =
        com.churchdrive.app.ha.EntityState(id, "off", JSONObject().apply { if (members.isNotEmpty()) put("entity_id", org.json.JSONArray(members.toList())) })

    @Test fun aRoomTakesThePresenceOfALightOrZoneInsideIt() {
        val room = light("light.landing", "light.lower_main", "light.upper_main", "light.spot_1")
        val zone = light("light.landing_main", "light.lower_main", "light.upper_main")
        val other = light("light.kitchen", "light.k1")
        val entities = listOf(room, zone, other).associateBy { it.entityId }
        val p = Presence(false, 4, 10, "Landings")
        val all = mapOf("light.landing_main" to p)
        assertEquals(p, com.churchdrive.app.ui.presenceFor(room, null, entities, all))
        assertNull(com.churchdrive.app.ui.presenceFor(other, null, entities, all))
        assertEquals(p, com.churchdrive.app.ui.presenceFor(zone, null, entities, all))
        assertNull(com.churchdrive.app.ui.presenceFor(room, null, entities, emptyMap()))
    }

    @Test fun aSingleLightCardTakesThePresenceOfTheGroupMadeOnlyOfIt() {
        val bulb = light("light.front_light")
        val group = light("light.front", "light.front_light")
        val other = light("light.porch")
        val entities = listOf(bulb, group, other).associateBy { it.entityId }
        val p = Presence(false, null, 30, "Front", 0L, true)
        val all = mapOf("light.front" to p)
        assertEquals(p, com.churchdrive.app.ui.presenceFor(bulb, null, entities, all))
        assertNull(com.churchdrive.app.ui.presenceFor(other, null, entities, all))
    }

    @Test fun aMotionDetectorSaysMotionNotSomeone() {
        val motion = Presence(true, null, 10, "Landings", 0L, true)
        assertEquals("Motion detected", presenceLine(motion, null))
        assertEquals("No motion", presenceLine(motion.copy(present = false), null))
        assertEquals("Off in 3 s", presenceLine(motion.copy(present = false, offIn = 3), 3))
        assertEquals("Someone's in", presenceLine(Presence(true, null, 20, "Cloakroom"), null))
        assertEquals(true, parsePresence(JSONObject("""{"lights":{"light.a":{"present":true,"off_in":null,"delay":10,"sensor":"S","motion":true}}}"""), 0L)!!["light.a"]!!.motion)
    }
}
