package com.churchdrive.app

import com.churchdrive.app.ha.HaClient
import com.churchdrive.app.ha.IconPack
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Test

class HaClientTest {
    @Test
    fun websocketUrlFromHttp() {
        assertEquals("ws://ha.local:8123/api/websocket", HaClient.websocketUrl("http://ha.local:8123/"))
        assertEquals("wss://ha.example.com/api/websocket", HaClient.websocketUrl("https://ha.example.com"))
        assertEquals("ws://192.168.1.5:8123/api/websocket", HaClient.websocketUrl("192.168.1.5:8123"))
    }

    @Test
    fun parsesStates() {
        val arr = JSONArray("""[{"entity_id":"light.a","state":"on","attributes":{"friendly_name":"A"}}]""")
        val states = HaClient.parseStates(arr)
        assertEquals("on", states["light.a"]?.state)
        assertEquals("A", states["light.a"]?.friendlyName)
    }

    @Test
    fun findsIconInPack() {
        val pack = """var icons = {
  "02tv":[0,0,24,24,"m16.5 8.2Z"],
  "centris-two":[0,0,24,24,"m21.915 13.989-1.64-1.641Z"],
  "wide":[0,0,32,32,"M0 0h32v32z"]
};"""
        assertEquals(24f to "m21.915 13.989-1.64-1.641Z", IconPack.parse(pack, "centris-two"))
        assertEquals(32f to "M0 0h32v32z", IconPack.parse(pack, "wide"))
        assertEquals(null, IconPack.parse(pack, "missing"))
    }
}
