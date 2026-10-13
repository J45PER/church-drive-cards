package com.churchdrive.app

import com.churchdrive.app.ha.MobileApp
import com.churchdrive.app.house.NoticePicture
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NoticePictureTest {
    @Test
    fun onlyCameraPicturesFromTheHouseAreFetched() {
        assertEquals("/api/camera_proxy/camera.front_door_snapshot", NoticePicture.path("/api/camera_proxy/camera.front_door_snapshot"))
        assertNull(NoticePicture.path("https://example.com/api/camera_proxy/camera.front_door_snapshot"))
        assertNull(NoticePicture.path("/api/camera_proxy/camera.x/../../auth"))
        assertNull(NoticePicture.path("/api/states"))
        assertNull(NoticePicture.path(null))
    }

    @Test
    fun aPushKeepsItsPicture() {
        val p = MobileApp.parsePush(JSONObject("""{"message":"Pressed","data":{"image":"/api/camera_proxy/camera.front_door_snapshot"}}"""))!!
        assertEquals("/api/camera_proxy/camera.front_door_snapshot", p.image)
        assertNull(MobileApp.parsePush(JSONObject("""{"message":"hi"}"""))!!.image)
    }
}
