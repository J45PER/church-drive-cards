package com.churchdrive.satellites

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PairingTest {
    @Test fun readsTheQrCode() {
        val c = PairCode.parse("clive://pair?d=clive-round&t=482913")!!
        assertEquals("clive-round", c.device)
        assertEquals("482913", c.code)
        assertEquals("482 913", c.spaced)
    }

    @Test fun refusesOtherCodes() {
        assertNull(PairCode.parse(null))
        assertNull(PairCode.parse("https://example.com"))
        assertNull(PairCode.parse("clive://pair?d=&t=482913"))
        assertNull(PairCode.parse("clive://pair?d=box&t=12ab56"))
        assertNull(PairCode.parse("clive://pair?d=box"))
    }

    @Test fun buildsTheWifiCommandWithItsChecksum() {
        val p = Improv.wifiCommand("ab", "cd")
        // command 1, length 6, then 2 "ab" 2 "cd", then the sum of everything before it
        val body = byteArrayOf(0x01, 0x06, 0x02, 'a'.code.toByte(), 'b'.code.toByte(), 0x02, 'c'.code.toByte(), 'd'.code.toByte())
        assertArrayEquals(body + Improv.checksum(body), p)
        assertEquals(((1 + 6 + 2 + 97 + 98 + 2 + 99 + 100) and 0xFF).toByte(), p.last())
    }

    @Test fun readsTheResultStrings() {
        val body = byteArrayOf(0x01, 0x05, 0x04, 'h'.code.toByte(), 't'.code.toByte(), 't'.code.toByte(), 'p'.code.toByte())
        assertEquals(listOf("http"), Improv.resultStrings(body + Improv.checksum(body)))
        assertNull(Improv.resultStrings(body + 0x00))
        assertNull(Improv.resultStrings(byteArrayOf(0x01)))
    }

    @Test fun explainsErrors() {
        assertNull(Improv.errorText(0))
        assertEquals(true, Improv.errorText(0x03)!!.contains("Wi-Fi"))
        assertEquals(true, Improv.errorText(0x04)!!.contains("Pair or reset"))
    }
}
