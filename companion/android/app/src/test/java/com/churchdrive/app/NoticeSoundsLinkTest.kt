package com.churchdrive.app

import com.churchdrive.app.house.NoticeLink
import com.churchdrive.app.house.NoticeStyle
import com.churchdrive.app.house.SoundFamily
import com.churchdrive.app.house.channelFor
import com.churchdrive.app.house.pageNameForSlug
import com.churchdrive.app.house.parseNoticeLink
import com.churchdrive.app.house.soundFamilyOf
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoticeSoundsLinkTest {
    @Test fun eachKindHasTheSoundOfItsFamily() {
        assertEquals(SoundFamily.Doorbell, soundFamilyOf("doorbell"))
        assertEquals(SoundFamily.Motion, soundFamilyOf("night_motion"))
        assertEquals(SoundFamily.Energy, soundFamilyOf("cheap_rate"))
        assertEquals(SoundFamily.Energy, soundFamilyOf("car_charged"))
        assertEquals(SoundFamily.Todo, soundFamilyOf("todo_new"))
        assertEquals(SoundFamily.System, soundFamilyOf("battery_report"))
        assertEquals(SoundFamily.System, soundFamilyOf("device_stale"))
        assertEquals(SoundFamily.Default, soundFamilyOf("door_away"))
        assertEquals(SoundFamily.Default, soundFamilyOf(null))
        assertEquals(SoundFamily.Default, soundFamilyOf("arrivals:person.jamie"))
    }

    @Test fun onlyAPopUpTakesItsFamilysChannel() {
        assertEquals("cd_popup_doorbell", channelFor(NoticeStyle.Popup, "doorbell"))
        assertEquals(NoticeStyle.Popup.channel, channelFor(NoticeStyle.Popup, "door_away"))
        assertEquals(NoticeStyle.Quiet.channel, channelFor(NoticeStyle.Quiet, "doorbell"))
        assertEquals(NoticeStyle.PopupQuiet.channel, channelFor(NoticeStyle.PopupQuiet, "doorbell"))
        assertEquals(NoticeStyle.Alert.channel, channelFor(NoticeStyle.Alert, "doorbell"))
    }

    @Test fun everyFamilyWithASoundHasItsFileAndItsOwnChannel() {
        val withSound = SoundFamily.entries.filter { it.raw != null }
        for (f in withSound) assertTrue("res/raw/${f.raw}.wav is missing", File("src/main/res/raw/${f.raw}.wav").exists())
        val channels = SoundFamily.entries.map { it.channel }
        assertEquals(channels.size, channels.toSet().size)
    }

    @Test fun linksSayWhereATapGoes() {
        assertEquals(NoticeLink.Camera("camera.front_door_live_view", true), parseNoticeLink("church-drive-camera:camera.front_door_live_view?answer"))
        assertEquals(NoticeLink.PageLink("Security"), parseNoticeLink("/dashboard-mobile/security"))
        assertEquals(NoticeLink.PageLink("Lighting"), parseNoticeLink("/dashboard-mobile/lighting"))
        assertEquals(NoticeLink.PageLink("Lighting"), parseNoticeLink("/dashboard-mobile/lights?x=1"))
        assertEquals(NoticeLink.PageLink("Todo"), parseNoticeLink("/dashboard-mobile/todo"))
        assertEquals(NoticeLink.Device("binary_sensor.back_door_battery_plus_low"), parseNoticeLink("church-drive-device:binary_sensor.back_door_battery_plus_low"))
        assertEquals(NoticeLink.Health, parseNoticeLink("church-drive-health"))
        assertEquals(NoticeLink.Health, parseNoticeLink("/battery-status/battery-status"))
        assertEquals(NoticeLink.Health, parseNoticeLink("church-drive-device:../../etc"))
        assertNull(parseNoticeLink("/dashboard-mobile/energy"))
        assertNull(parseNoticeLink("https://example.com"))
        assertNull(parseNoticeLink(null))
    }

    @Test fun slugsMapToPagesButAdministratorPagesDoNot() {
        assertEquals("Lighting", pageNameForSlug("Lights"))
        assertEquals("Todo", pageNameForSlug("to-do"))
        assertNull(pageNameForSlug("devices"))
        assertNull(pageNameForSlug(""))
    }
}
