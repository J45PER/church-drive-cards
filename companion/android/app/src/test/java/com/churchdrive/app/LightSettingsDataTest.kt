package com.churchdrive.app

import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ui.LightKind
import com.churchdrive.app.ui.groupKind
import com.churchdrive.app.ui.hsToRgb
import com.churchdrive.app.ui.kelvinRgb
import com.churchdrive.app.ui.lightKindOf
import com.churchdrive.app.ui.lightMembers
import com.churchdrive.app.ui.opensSettings
import com.churchdrive.app.ui.wheelHueSat
import com.churchdrive.app.ui.wheelPoint
import com.churchdrive.app.ui.whitePresets
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LightSettingsDataTest {
    private fun light(vararg attrs: Pair<String, Any>, state: String = "on") =
        EntityState("light.x", state, JSONObject().apply { attrs.forEach { put(it.first, it.second) } })

    private fun modes(vararg m: String) = "supported_color_modes" to JSONArray(m.toList())

    @Test fun kindsFollowTheColourModes() {
        assertEquals(LightKind.Switch, lightKindOf(listOf("onoff")))
        assertEquals(LightKind.Dim, lightKindOf(listOf("brightness")))
        assertEquals(LightKind.White, lightKindOf(listOf("color_temp")))
        assertEquals(LightKind.Colour, lightKindOf(listOf("color_temp", "xy")))
        assertEquals(LightKind.Colour, lightKindOf(listOf("hs")))
        assertEquals(LightKind.Dim, lightKindOf(emptyList(), hasBrightness = true))
        assertEquals(LightKind.Switch, lightKindOf(emptyList()))
    }

    @Test fun aGroupCanDoTheMostOfItsLights() {
        assertEquals(LightKind.Colour, groupKind(listOf(light(modes("onoff")), light(modes("xy")))))
        assertEquals(LightKind.Switch, groupKind(emptyList()))
    }

    @Test fun membersAreTheLightsInTheGroup() {
        val g = light("entity_id" to JSONArray(listOf("light.a", "switch.b", "light.c")))
        assertEquals(listOf("light.a", "light.c"), g.lightMembers())
        assertTrue(light().lightMembers().isEmpty())
    }

    @Test fun onlyLightsThatCanDoMoreOpenSettings() {
        assertFalse(opensSettings(light(modes("onoff"))))
        assertTrue(opensSettings(light(modes("brightness"))))
        assertTrue(opensSettings(light(modes("onoff"), "entity_id" to JSONArray(listOf("light.a", "light.b")))))
        assertFalse(opensSettings(light(modes("brightness"), state = "unavailable")))
        assertFalse(opensSettings(null))
    }

    @Test fun warmIsRedderThanCool() {
        val warm = kelvinRgb(2200)
        val cool = kelvinRgb(6500)
        assertTrue(warm[0] > warm[2])
        assertTrue(cool[2] >= cool[0] - 5)
    }

    @Test fun hueAndSaturationRoundTripOnTheWheel() {
        val (x, y) = wheelPoint(120.0, 50.0)
        val (h, s) = wheelHueSat(x, y, 1.0)
        assertEquals(120.0, h, 0.5)
        assertEquals(50.0, s, 0.5)
        assertEquals(listOf(255, 0, 0), hsToRgb(0.0, 100.0).toList())
        assertEquals(listOf(255, 255, 255), hsToRgb(200.0, 0.0).toList())
    }

    @Test fun presetsStayInsideTheLightsRange() {
        assertEquals(listOf("Candle", "Warm", "Neutral", "Daylight", "Cool"), whitePresets(2000, 6500).map { it.first })
        assertEquals(listOf("Warm", "Neutral", "Daylight", "Cool"), whitePresets(2700, 6500).map { it.first })
    }

    private fun named(id: String, vararg attrs: Pair<String, Any>) =
        EntityState(id, "on", JSONObject().apply { attrs.forEach { put(it.first, it.second) } })

    @Test fun eachRoleOffersOnlyWhatItCanDo() {
        assertEquals(listOf("leave", "off", "white", "colour"), com.churchdrive.app.ui.hybridKindsForRole("primary"))
        assertEquals(listOf("leave", "off", "colour", "gradient", "dynamic"), com.churchdrive.app.ui.hybridKindsForRole("ambient"))
        assertEquals(listOf("leave", "off", "colour", "gradient"), com.churchdrive.app.ui.hybridKindsForRole("tertiary"))
    }

    @Test fun aRoomsColourIsTheOneMostOfItsLightsShow() {
        fun colour(id: String, r: Int, g: Int, b: Int) = EntityState(id, "on", JSONObject().put("color_mode", "rgb").put("rgb_color", org.json.JSONArray(listOf(r, g, b))))
        val warm = listOf(colour("light.a", 255, 150, 40), colour("light.b", 250, 148, 45), colour("light.c", 40, 60, 255))
        assertEquals(listOf("light.a", "light.b"), com.churchdrive.app.ui.majorityColourLights(warm))
        assertEquals(emptyList<String>(), com.churchdrive.app.ui.majorityColourLights(emptyList()))
        val off = EntityState("light.d", "off", JSONObject().put("color_mode", "rgb").put("rgb_color", org.json.JSONArray(listOf(1, 2, 3))))
        assertEquals(emptyList<String>(), com.churchdrive.app.ui.majorityColourLights(listOf(off)))
    }

    @Test fun aLightOffersTheEffectsItSaysItHas() {
        val hue = EntityState("light.front_light", "on", JSONObject().put("effect_list", org.json.JSONArray(listOf("off", "candle", "fire", "underwater"))))
        assertEquals(listOf("candle", "fire", "underwater"), com.churchdrive.app.ui.lightEffects(hue))
        assertEquals(emptyList<String>(), com.churchdrive.app.ui.lightEffects(EntityState("light.plain", "on", JSONObject())))
        assertEquals("Underwater", com.churchdrive.app.ui.effectLabel("underwater"))
    }

    @Test fun aCardForOneLightTakesItsColourFromThatLight() {
        val bulb = EntityState("light.front_light", "on", JSONObject().put("color_mode", "xy").put("rgb_color", org.json.JSONArray(listOf(255, 125, 60))))
        val room = EntityState("light.front", "on", JSONObject().put("entity_id", org.json.JSONArray(listOf("light.front_light"))))
        val all = listOf(bulb, room).associateBy { it.entityId }
        assertEquals(listOf("light.front_light"), com.churchdrive.app.ui.roomTintLights(bulb, null, all))
        assertEquals(listOf("light.front_light"), com.churchdrive.app.ui.roomTintLights(room, null, all))
        assertEquals(emptyList<String>(), com.churchdrive.app.ui.roomTintLights(null, null, all))
    }
}
