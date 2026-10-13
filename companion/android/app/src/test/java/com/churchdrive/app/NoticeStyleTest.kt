package com.churchdrive.app

import com.churchdrive.app.ha.MobileApp
import com.churchdrive.app.house.NoticeStyle
import com.churchdrive.app.house.defaultNoticeStyle
import com.churchdrive.app.house.noticeStylesFor
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoticeStyleTest {
    @Test fun theHouseDecidesUntilThePersonChooses() {
        assertEquals(NoticeStyle.Alert, defaultNoticeStyle(critical = true, loud = false, quiet = false))
        assertEquals(NoticeStyle.Alert, defaultNoticeStyle(critical = false, loud = true, quiet = false))
        assertEquals(NoticeStyle.Quiet, defaultNoticeStyle(critical = false, loud = false, quiet = true))
        assertEquals(NoticeStyle.Popup, defaultNoticeStyle(critical = false, loud = false, quiet = false))
    }

    @Test fun onlySafetyAlarmsOfferAlert() {
        assertTrue(NoticeStyle.Alert in noticeStylesFor(critical = true))
        assertFalse(NoticeStyle.Alert in noticeStylesFor(critical = false))
        assertTrue(NoticeStyle.Off in noticeStylesFor(critical = false))
    }

    @Test fun everyStyleButOffHasItsOwnChannel() {
        val channels = NoticeStyle.entries.filter { it != NoticeStyle.Off }.map { it.channel }
        assertEquals(channels.size, channels.toSet().size)
        assertTrue(channels.all { it.startsWith("cd_") })
        assertEquals("", NoticeStyle.Off.channel)
        assertEquals(NoticeStyle.PopupQuiet, NoticeStyle.of("popup_quiet"))
        assertNull(NoticeStyle.of("nonsense"))
    }

    @Test fun aPushCarriesItsKindAndWhatTheHouseAskedFor() {
        val loud = MobileApp.parsePush(JSONObject("""{"title":"Smoke","message":"Going off","data":{"group":"smoke","priority":"high","channel":"alarm_stream"}}"""))!!
        assertEquals("smoke", loud.kind)
        assertTrue(loud.loud)
        assertFalse(loud.quiet)
        val quiet = MobileApp.parsePush(JSONObject("""{"message":"Cheap","data":{"group":"cheap_rate","importance":"low","channel":"Church Drive quiet"}}"""))!!
        assertEquals("cheap_rate", quiet.kind)
        assertTrue(quiet.quiet)
        assertFalse(quiet.loud)
        val plain = MobileApp.parsePush(JSONObject("""{"message":"Hi","data":{"group":"church-drive"}}"""))!!
        assertNull(plain.kind)
    }
}
