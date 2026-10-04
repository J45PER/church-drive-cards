package com.churchdrive.app

import com.churchdrive.app.ha.MobileApp
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MobileAppTest {
    @Test
    fun registrationAsksForTheWebsocketPushChannel() {
        val b = MobileApp.registrationBody("id1", "Jamie Pixel", "0.1.100", "17", "Google", "Pixel")
        assertEquals("id1", b.getString("device_id"))
        assertEquals("com.churchdrive.app", b.getString("app_id"))
        assertFalse(b.getBoolean("supports_encryption"))
        assertTrue(b.getJSONObject("app_data").getBoolean("push_websocket_channel"))
    }

    @Test
    fun locationMessageCarriesPositionAndOnlyRealExtras() {
        val b = MobileApp.locationBody(52.5, -1.8, 12.5f, null, null, null, 80)
        assertEquals("update_location", b.getString("type"))
        val d = b.getJSONObject("data")
        assertEquals(52.5, d.getJSONArray("gps").getDouble(0), 0.0)
        assertEquals(-1.8, d.getJSONArray("gps").getDouble(1), 0.0)
        assertEquals(12.5, d.getDouble("gps_accuracy"), 0.0)
        assertEquals(80, d.getInt("battery"))
        assertFalse(d.has("altitude"))
        assertFalse(MobileApp.locationBody(1.0, 1.0, null, null, null, null, 150).getJSONObject("data").has("battery"))
    }

    @Test
    fun aPushNeedsWordsAndDefaultsTheRest() {
        val p = MobileApp.parsePush(JSONObject("""{"message":"Someone is at the door","data":{"tag":"door","channel":"doorbell"}}"""))!!
        assertEquals("Church Drive", p.title)
        assertEquals("Someone is at the door", p.message)
        assertEquals("door", p.tag)
        assertEquals("doorbell", p.channel)
        assertNull(MobileApp.parsePush(JSONObject("""{"title":"x"}""")))
        assertEquals("house", MobileApp.parsePush(JSONObject("""{"message":"hi"}"""))!!.channel)
    }

    @Test
    fun theNotifyServiceNameFollowsHomeAssistant() {
        assertEquals("notify.mobile_app_jamie_pixel_10_pro_xl", MobileApp.notifyService("Jamie Pixel 10 Pro XL"))
        assertEquals("Jamie Pixel 10", MobileApp.deviceName(" Jamie Phillips ", "Pixel 10"))
        assertEquals("Church Drive phone", MobileApp.deviceName(null, ""))
    }
}
