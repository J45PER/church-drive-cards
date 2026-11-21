package com.churchdrive.app

import com.churchdrive.app.ui.FitSegment
import com.churchdrive.app.ui.fittingBody
import com.churchdrive.app.ui.mainRequest
import com.churchdrive.app.ui.parseFittings
import com.churchdrive.app.ui.replaced
import com.churchdrive.app.ui.ringColourRequest
import com.churchdrive.app.ui.ringSegmentsRequest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FittingDataTest {
    private val answer = JSONObject(
        """{"fittings":{"light.cloakroom":{"room":"light.cloakroom","main":"light.cloakroom_down_light","ring":"light.cloakroom_up_light",
        "segments":["light.s1","light.s2"],"fitting":{"main":{"on":true,"mode":"white","kelvin":2700,"rgb":[255,200,140],"brightness":80},
        "ring":{"on":true,"segments":[{"rgb":[0,0,255],"brightness":100},{"rgb":[0,0,0],"brightness":50}]}}}}}""",
    )

    @Test fun theRecordIsRead() {
        val f = parseFittings(answer)!!.byRoom.getValue("light.cloakroom")
        assertEquals("light.cloakroom_up_light", f.ring)
        assertTrue(f.mainOn)
        assertEquals(80, f.mainBrightness)
        assertEquals(2, f.ringSegments.size)
        assertFalse(f.ringSegments[0].dark)
        assertTrue(f.ringSegments[1].dark)
        assertEquals(255, f.mainShown[0])
    }

    @Test fun noRecordIsNull() {
        assertNull(parseFittings(JSONObject()))
        assertNull(parseFittings(null))
    }

    @Test fun requestsHaveOnlyWhatChanged() {
        val body = fittingBody("light.cloakroom", main = mainRequest(on = false))
        assertEquals("light.cloakroom", body.getString("room"))
        assertFalse(body.getJSONObject("main").getBoolean("on"))
        assertFalse(body.has("ring"))
        assertFalse(body.has("keep_ring"))
        assertTrue(fittingBody("r", keepRing = true).getBoolean("keep_ring"))
        assertEquals(3, ringColourRequest(listOf(1, 2, 3)).getJSONArray("rgb").length())
    }

    @Test fun oneSegmentChangesAndTheRestStay() {
        val segs = listOf(FitSegment(listOf(255, 0, 0), 100), FitSegment(listOf(0, 255, 0), 100))
        val next = segs.replaced(1, FitSegment(listOf(0, 0, 0), 100))
        assertEquals(listOf(255, 0, 0), next[0].rgb)
        assertTrue(next[1].dark)
        assertEquals(2, ringSegmentsRequest(next).getJSONArray("segments").length())
    }
}
