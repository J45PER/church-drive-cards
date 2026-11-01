package com.churchdrive.satellites

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceIdTest {
    // The same IDs satellite/tools/make_device.py makes.
    @Test fun matchesTheLabelTool() {
        assertEquals("RND1-000123-4", DeviceId("RND", 1, 123).text)
        assertEquals("RND1-000001-C", DeviceId("RND", 1, 1).text)
        assertEquals("SPK2-004567-1", DeviceId("SPK", 2, 4567).text)
        assertEquals("DSP1-999999-Q", DeviceId("DSP", 1, 999999).text)
    }

    @Test fun readsWhatIsTypedOrScanned() {
        val id = DeviceId.parse("rnd1 000123 4")!!
        assertEquals(DeviceId("RND", 1, 123), id)
        assertEquals("clive-rnd1-000123", id.deviceName)
        assertEquals("Round screen", id.modelName)
        assertEquals(id, DeviceId.parse("RND1-000123-4"))
        assertEquals(id, DeviceId.parse("RND1-OOO123-4"))
    }

    @Test fun catchesMistakes() {
        assertNull(DeviceId.parse("RND1-000123-5"))   // wrong check character
        assertNull(DeviceId.parse("RND1-000132-4"))   // two digits swapped
        assertNull(DeviceId.parse("XYZ1-000123-4"))   // not a model
        assertNull(DeviceId.parse("RND0-000123-4"))   // generation 0
        assertNull(DeviceId.parse("RND1-00123-4"))    // too short
        assertNull(DeviceId.parse(null))
    }

    @Test fun readsAStickerQrCode() {
        val key = "0DbJ56fGAlKmQqFrYw5InyB54pMvfSR1/ag3LttET9w="
        val c = PairCode.parse("clive://pair?id=RND1-000123-4&d=clive-rnd1-000123&k=" + java.net.URLEncoder.encode(key, "UTF-8"))!!
        assertEquals("clive-rnd1-000123", c.device)
        assertEquals(key, c.key)
        assertEquals("RND1-000123-4", c.shown)
        assertEquals("", c.code)
    }

    @Test fun refusesABadSticker() {
        val key = "0DbJ56fGAlKmQqFrYw5InyB54pMvfSR1%2Fag3LttET9w%3D"
        assertNull(PairCode.parse("clive://pair?id=RND1-000123-5&d=x&k=$key"))   // check character wrong
        assertNull(PairCode.parse("clive://pair?id=RND1-000123-4&d=x"))          // no key and no screen code
        assertNull(PairCode.parse("clive://pair?d=x&k=$key"))                     // key without an ID
    }
}
