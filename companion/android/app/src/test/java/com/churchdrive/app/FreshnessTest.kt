package com.churchdrive.app

import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ha.Freshness
import com.churchdrive.app.ha.statesDiffer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class FreshnessTest {
    private fun e(id: String, state: String, changed: String? = "2026-11-11T10:00:00Z") = EntityState(id, state, JSONObject(), changed)
    private fun map(vararg s: EntityState) = s.associateBy { it.entityId }

    @Test fun backAfterAMinuteOrMoreReadsTheHouseAgain() {
        assertEquals(false, Freshness.dueOnReturn(100_000L, 0L))                       // never went away
        assertEquals(false, Freshness.dueOnReturn(100_000L, 100_000L - Freshness.AWAY_MS + 1))
        assertEquals(true, Freshness.dueOnReturn(100_000L, 100_000L - Freshness.AWAY_MS))
    }

    @Test fun openAppReadsEveryFiveMinutesOnceItHasLoadedOnce() {
        assertEquals(false, Freshness.duePeriodic(1_000_000L, 0L))                      // nothing loaded yet: the line's own first read is coming
        assertEquals(false, Freshness.duePeriodic(1_000_000L, 1_000_000L - Freshness.PERIODIC_MS + 1))
        assertEquals(true, Freshness.duePeriodic(1_000_000L, 1_000_000L - Freshness.PERIODIC_MS))
    }

    @Test fun aFreshReadThatMatchesIsNotStale() {
        val live = map(e("light.a", "on"), e("light.b", "off"))
        assertEquals(false, statesDiffer(live, map(e("light.a", "on"), e("light.b", "off"))))
    }

    @Test fun aMissedChangeIsFoundWhetherItIsAStateAnEntityOrACount() {
        val live = map(e("light.a", "on"), e("light.b", "off"))
        assertEquals(true, statesDiffer(live, map(e("light.a", "off"), e("light.b", "off"))))                               // a state changed unseen
        assertEquals(true, statesDiffer(live, map(e("light.a", "on"), e("light.b", "off", "2026-11-11T10:05:00Z"))))        // changed and changed back
        assertEquals(true, statesDiffer(live, map(e("light.a", "on"), e("light.c", "off"))))                                // a different entity
        assertEquals(true, statesDiffer(live, map(e("light.a", "on"))))                                                     // one fewer
    }
}
