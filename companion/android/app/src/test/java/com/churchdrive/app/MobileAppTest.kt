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
        assertEquals("com.churchdrive.manager", b.getString("app_id"))
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
    fun theSetupReportIsAProblemSensorThatIsOnWhenSomethingIsMissing() {
        val ok = MobileApp.phoneSetupBody(emptyList(), linkedMapOf("notifications" to true), "0.99.0 (7)")
        assertEquals("register_sensor", ok.getString("type"))
        val d = ok.getJSONObject("data")
        assertEquals("church_drive_phone_setup", d.getString("unique_id"))
        assertEquals("binary_sensor", d.getString("type"))
        assertEquals("problem", d.getString("device_class"))
        assertFalse(d.getBoolean("state"))
        assertTrue(d.getJSONObject("attributes").getBoolean("notifications"))
        assertEquals("0.99.0 (7)", d.getJSONObject("attributes").getString("app_version"))

        val bad = MobileApp.phoneSetupBody(listOf("battery", "always_location"), linkedMapOf("notifications" to true), "0.99.0 (7)").getJSONObject("data")
        assertTrue(bad.getBoolean("state"))
        assertEquals(2, bad.getJSONObject("attributes").getJSONArray("missing").length())
        assertEquals("battery", bad.getJSONObject("attributes").getJSONArray("missing").getString(0))
    }

    @Test
    fun theZonesAreAskedForByTheWebhooksOwnName() {
        assertEquals("get_zones", MobileApp.getZonesBody().getString("type"))
    }

    @Test
    fun aNoticeTestAsksTheHouseForOneKindForOnePerson() {
        val e = MobileApp.noticeTestBody("doorbell", "Jamie").getJSONObject("data")
        assertEquals("church_drive_notice_test", e.getString("event_type"))
        assertEquals("doorbell", e.getJSONObject("event_data").getString("kind"))
        assertEquals("Jamie", e.getJSONObject("event_data").getString("who"))
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

    @Test fun theAppRegistersUnderThePhonesOwnNameWithCda() {
        assertEquals("XITOL-J45PER-P10PXL-CDA", MobileApp.cdaDeviceName(" XITOL-J45PER-P10PXL ", "Jamie", "Pixel 10 Pro XL"))
        assertEquals("Jamie Pixel 10 Pro XL-CDA", MobileApp.cdaDeviceName(null, "Jamie Phillips", "Pixel 10 Pro XL"))
        assertEquals("Jamie Pixel 10 Pro XL-CDA", MobileApp.cdaDeviceName("  ", "Jamie", "Pixel 10 Pro XL"))
        assertEquals("notify.mobile_app_xitol_j45per_p10pxl_cda", MobileApp.notifyService("XITOL-J45PER-P10PXL-CDA"))
    }

    @Test fun renamingAPhoneSendsItsVersionAndName() {
        val data = MobileApp.updateRegistrationBody("0.28.0 (5)", "Name-CDA", "16", "Google", "Pixel").getJSONObject("data")
        assertEquals("update_registration", MobileApp.updateRegistrationBody("v", "n", "o", "m", "x").getString("type"))
        assertEquals("Name-CDA", data.getString("device_name"))
        assertEquals("0.28.0 (5)", data.getString("app_version"))
    }

    @Test fun askingTheHouseWhetherGooglePushReachesAPhone() {
        // The house lists phones by the service name without "notify.", so the question must too.
        val template = MobileApp.pushReachableBody("notify.mobile_app_x_cda").getJSONObject("data").getJSONObject("reachable").getString("template")
        assertTrue(template.contains("'mobile_app_x_cda' in"))
        assertTrue(!template.contains("notify."))
        assertEquals("render_template", MobileApp.pushReachableBody("a").getString("type"))
        assertTrue(MobileApp.parseReachable("""{"reachable": "True"}"""))
        assertTrue(MobileApp.parseReachable("""{"reachable": true}"""))
        assertTrue(!MobileApp.parseReachable("""{"reachable": "False"}"""))
        assertTrue(!MobileApp.parseReachable("{}"))
        assertTrue(!MobileApp.parseReachable(null))
        assertTrue(!MobileApp.parseReachable("not json"))
    }
}
