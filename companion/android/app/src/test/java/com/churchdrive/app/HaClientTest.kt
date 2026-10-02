package com.churchdrive.app

import com.churchdrive.app.ha.HaClient
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
}
