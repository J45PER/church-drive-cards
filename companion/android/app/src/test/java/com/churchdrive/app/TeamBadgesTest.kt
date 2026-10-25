package com.churchdrive.app

import com.churchdrive.app.ui.TeamBadges
import org.junit.Assert.assertEquals
import org.junit.Test

class TeamBadgesTest {
    private fun crest(name: String) = TeamBadges.peek(name).second

    @Test
    fun bigEuropeanClubsHaveACrestWithoutAskingAnyone() {
        assertEquals("https://crests.football-data.org/81.png", crest("Barcelona"))
        assertEquals("https://crests.football-data.org/81.png", crest("FC Barcelona"))
        assertEquals("https://crests.football-data.org/524.png", crest("Paris Saint-Germain"))
        assertEquals("https://crests.football-data.org/5.png", crest("Bayern München"))
        assertEquals("https://crests.football-data.org/108.png", crest("Inter"))
        assertEquals("https://crests.football-data.org/78.png", crest("Atlético Madrid"))
        assertEquals("https://crests.football-data.org/721.png", crest("RB Leipzig"))
    }

    @Test
    fun premierLeagueClubsStillWork() {
        assertEquals("https://crests.football-data.org/65.png", crest("Manchester City"))
        assertEquals("https://crests.football-data.org/58.png", crest("Aston Villa"))
    }
}
