package com.churchdrive.app

import com.churchdrive.app.ha.MobileApp
import com.churchdrive.app.ha.NoticeButton
import com.churchdrive.app.ha.isMuted
import com.churchdrive.app.ha.pageFromLink
import com.churchdrive.app.ha.parseNoticeButton
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoticeButtonTest {
    @Test fun linksHouseActionsMuteAndSnoozeAreUnderstood() {
        assertEquals(NoticeButton.Link("church-drive-camera:camera.entrance_live_view?answer"), parseNoticeButton("URI", "church-drive-camera:camera.entrance_live_view?answer"))
        assertEquals(NoticeButton.House("lights_on"), parseNoticeButton("CD_lights_on", null))
        assertEquals(NoticeButton.Mute(60), parseNoticeButton("CD_mute:60", null))
        assertEquals(NoticeButton.Snooze(10), parseNoticeButton("CD_snooze:10", null))
    }

    @Test fun anythingElseIsIgnored() {
        assertNull(parseNoticeButton("URI", null))
        assertNull(parseNoticeButton("CD_disarm", null))
        assertNull(parseNoticeButton("CD_call_999", null))
        assertNull(parseNoticeButton("CD_mute:abc", null))
        assertNull(parseNoticeButton("CD_snooze:99999", null))
        assertNull(parseNoticeButton("something", null))
    }

    @Test fun aPageLinkNamesThePage() {
        assertEquals("Security", pageFromLink("/dashboard-mobile/security"))
        assertEquals("Cleaning", pageFromLink("/dashboard-mobile/cleaning?x=1"))
        assertNull(pageFromLink("church-drive-camera:camera.x"))
        assertNull(pageFromLink(null))
    }

    @Test fun aMuteLastsUntilItsTime() {
        assertTrue(isMuted(2000, 1000))
        assertFalse(isMuted(1000, 1000))
        assertFalse(isMuted(null, 1000))
    }

    @Test fun pushCarriesButtonsTheAppCanDo() {
        val push = MobileApp.parsePush(JSONObject("""{"message":"Someone is at the door","data":{"group":"doorbell","actions":[
            {"action":"URI","title":"Answer","uri":"church-drive-camera:camera.entrance_live_view?answer"},
            {"action":"CD_mute:60","title":"Mute 1 hour"},{"action":"CD_disarm","title":"Disarm"}]}}"""))!!
        assertEquals(listOf("Answer", "Mute 1 hour"), push.actions.map { it.title })
        assertEquals("church-drive-camera:camera.entrance_live_view?answer", push.actions[0].uri)
    }

    @Test fun houseActionBodyFiresTheEvent() {
        val data = MobileApp.noticeActionBody("dock", "Jamie").getJSONObject("data")
        assertEquals("church_drive_notice_action", data.getString("event_type"))
        assertEquals("CD_dock", data.getJSONObject("event_data").getString("action"))
    }

    @Test fun aPushCarriesItsNumberAndAKeptNoticeBecomesAnEvent() {
        val live = MobileApp.parsePush(JSONObject("""{"message":"Hi","data":{"cd_seq":1700000000123}}"""))!!
        assertEquals(1700000000123, live.seq)
        assertEquals(0, MobileApp.parsePush(JSONObject("""{"message":"Hi"}"""))!!.seq)
        val kept = JSONObject("""{"seq":5,"title":"Door","message":"Opened","data":{"tag":"d","cd_seq":5,"group":"door_away"}}""")
        val again = MobileApp.parsePush(com.churchdrive.app.house.missedNoticeEvent(kept))!!
        assertEquals("Door", again.title)
        assertEquals(5, again.seq)
        assertEquals("door_away", again.kind)
    }

    @Test fun thisPhoneIsFoundByItsRegisteredName() {
        val people = listOf(JSONObject("""{"entity_id":"person.jamie","phones":[{"service":"mobile_app_a","on":true},{"service":"mobile_app_jamie_pixel","on":false}]}"""))
        assertEquals(false, com.churchdrive.app.ui.thisPhone(people, "person.jamie", "mobile_app_jamie_pixel")!!.getBoolean("on"))
        assertNull(com.churchdrive.app.ui.thisPhone(people, "person.jamie", "mobile_app_other"))
        assertNull(com.churchdrive.app.ui.thisPhone(people, "person.ian", "mobile_app_a"))
    }

    @Test fun theGooglePushTokenBodyFiresTheEvent() {
        val data = MobileApp.fcmTokenBody("mobile_app_jamie_pixel", "abc").getJSONObject("data")
        assertEquals("church_drive_fcm_token", data.getString("event_type"))
        assertEquals("mobile_app_jamie_pixel", data.getJSONObject("event_data").getString("service"))
        assertEquals("abc", data.getJSONObject("event_data").getString("token"))
    }

    @Test fun theOwnConnectionRunsOnlyUntilGooglePushHasThePhone() {
        assertTrue(com.churchdrive.app.ha.connectionNeeded(signedIn = true, notifyOn = true, googlePush = false))
        assertFalse(com.churchdrive.app.ha.connectionNeeded(signedIn = true, notifyOn = true, googlePush = true))
        assertFalse(com.churchdrive.app.ha.connectionNeeded(signedIn = true, notifyOn = false, googlePush = false))
        assertFalse(com.churchdrive.app.ha.connectionNeeded(signedIn = false, notifyOn = true, googlePush = false))
    }
}
