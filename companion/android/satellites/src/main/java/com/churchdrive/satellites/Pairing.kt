package com.churchdrive.satellites

/**
 * What a satellite's QR code says: `clive://pair?d=<device name>&t=<six digit code>` (satellite/clive_ui.h `show_pair`, and the
 * sticker on a satellite without a screen). The code is only there for the person to check against the one on the screen: it is never the
 * encryption key.
 */
data class PairCode(val device: String, val code: String) {
    /** "482 913", as the screen shows it. */
    val spaced: String get() = if (code.length == 6) "${code.take(3)} ${code.drop(3)}" else code

    companion object {
        fun parse(text: String?): PairCode? {
            val t = text?.trim() ?: return null
            if (!t.startsWith("clive://pair?")) return null
            val query = t.substringAfter('?').split('&').mapNotNull { part ->
                val i = part.indexOf('=')
                if (i <= 0) null else part.substring(0, i) to java.net.URLDecoder.decode(part.substring(i + 1), "UTF-8")
            }.toMap()
            val device = query["d"]?.takeIf { it.isNotBlank() } ?: return null
            val code = query["t"]?.takeIf { it.length in 4..8 && it.all(Char::isDigit) } ?: return null
            return PairCode(device, code)
        }
    }
}

/** The Improv Wi-Fi protocol's packets over Bluetooth (https://www.improv-wifi.com/ble/), as plain bytes so they can be tested. */
object Improv {
    const val SERVICE = "00467768-6228-2272-4663-277478268000"
    const val CURRENT_STATE = "00467768-6228-2272-4663-277478268001"
    const val ERROR_STATE = "00467768-6228-2272-4663-277478268002"
    const val RPC_COMMAND = "00467768-6228-2272-4663-277478268003"
    const val RPC_RESULT = "00467768-6228-2272-4663-277478268004"

    const val STATE_AUTHORIZATION_REQUIRED = 0x01
    const val STATE_AUTHORIZED = 0x02
    const val STATE_PROVISIONING = 0x03
    const val STATE_PROVISIONED = 0x04

    fun errorText(code: Int): String? = when (code) {
        0x00 -> null
        0x01 -> "The satellite didn't understand the message. Try again."
        0x02 -> "The satellite doesn't know that request. Its firmware may be too old."
        0x03 -> "The satellite couldn't join that Wi-Fi. Check the name and password, and that the signal reaches it."
        0x04 -> "The satellite isn't accepting Wi-Fi right now. Open Settings > Pair or reset on it, so the code is showing."
        else -> "The satellite reported an error ($code)."
    }

    /** The "send Wi-Fi settings" command: command 1, then the length, the network name and the password, then a checksum of all of it. */
    fun wifiCommand(ssid: String, password: String): ByteArray {
        val s = ssid.toByteArray(Charsets.UTF_8)
        val p = password.toByteArray(Charsets.UTF_8)
        require(s.size in 1..255 && p.size <= 255) { "Wi-Fi name or password too long" }
        val data = byteArrayOf(s.size.toByte()) + s + byteArrayOf(p.size.toByte()) + p
        val body = byteArrayOf(0x01, data.size.toByte()) + data
        return body + checksum(body)
    }

    fun checksum(bytes: ByteArray): Byte = (bytes.sumOf { it.toInt() and 0xFF } and 0xFF).toByte()

    /** The strings in a result packet (for Wi-Fi settings: the address to visit next, if any), or null if the packet is damaged. */
    fun resultStrings(packet: ByteArray): List<String>? {
        if (packet.size < 3) return null
        val length = packet[1].toInt() and 0xFF
        if (packet.size < length + 3) return null
        if (checksum(packet.copyOfRange(0, length + 2)) != packet[length + 2]) return null
        val out = mutableListOf<String>()
        var i = 2
        while (i < length + 2) {
            val n = packet[i].toInt() and 0xFF
            if (i + 1 + n > length + 2) return null
            out += String(packet, i + 1, n, Charsets.UTF_8)
            i += 1 + n
        }
        return out
    }
}
