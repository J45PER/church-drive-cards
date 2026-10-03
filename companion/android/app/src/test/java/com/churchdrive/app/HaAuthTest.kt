package com.churchdrive.app

import com.churchdrive.app.ha.HaAuth
import com.churchdrive.app.ha.LoginStep
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HaAuthTest {
    @Test
    fun clientIdIsTheAddressWithOneTrailingSlash() {
        assertEquals("http://ha.local:8123/", HaAuth.clientId(" http://ha.local:8123// "))
    }

    @Test
    fun aFinishedSignInGivesItsCode() {
        val step = HaAuth.parseStep("f1", JSONObject("""{"type":"create_entry","result":"abc"}"""))
        assertEquals(LoginStep.Done("abc"), step)
    }

    @Test
    fun wrongPasswordAsksAgainWithWords() {
        val step = HaAuth.parseStep("f1", JSONObject("""{"type":"form","step_id":"init","flow_id":"f2","errors":{"base":"invalid_auth"}}"""))
        assertEquals(LoginStep.Credentials("f2", "Wrong username or password."), step)
    }

    @Test
    fun twoStepAsksForTheCode() {
        val step = HaAuth.parseStep("f1", JSONObject("""{"type":"form","step_id":"mfa","flow_id":"f3"}"""))
        assertEquals(LoginStep.Mfa("f3", null), step)
    }

    @Test
    fun anAbortOrSurpriseFails() {
        assertTrue(HaAuth.parseStep("f", JSONObject("""{"type":"abort","reason":"x"}""")) is LoginStep.Failed)
        assertTrue(HaAuth.parseStep("f", JSONObject("""{"type":"???"}""")) is LoginStep.Failed)
    }

    @Test
    fun tokensNeedAnAccessToken() {
        val t = HaAuth.parseTokens(JSONObject("""{"access_token":"a","refresh_token":"r","expires_in":1800}"""))!!
        assertEquals("r", t.refresh)
        assertEquals(1800L, t.expiresInSeconds)
        assertNull(HaAuth.parseTokens(JSONObject("""{"error":"invalid_grant"}""")))
    }
}
