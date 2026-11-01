package com.churchdrive.satellites

/**
 * A satellite's permanent ID, as printed on its sticker: `RND1-000123-4`. A model code (RND round screen, SPK speaker with no screen, DSP small
 * display), the hardware generation (one digit), a six digit running number for that model and generation, and a check character that lets
 * the app tell a typing mistake from a code that doesn't exist. satellite/tools/make_device.py makes them with the same sum.
 */
data class DeviceId(val model: String, val generation: Int, val number: Int) {
    val check: Char get() = checkChar(body)
    private val body: String get() = "$model$generation${"%06d".format(number)}"

    /** As printed: `RND1-000123-4`. */
    val text: String get() = "$model$generation-${"%06d".format(number)}-$check"

    /** The name the satellite gives itself on Bluetooth, the network and in Home Assistant: `clive-rnd1-000123`. */
    val deviceName: String get() = "clive-${model.lowercase()}$generation-${"%06d".format(number)}"

    val modelName: String get() = MODELS[model] ?: "Satellite"

    companion object {
        /** Crockford base 32: no I, L, O or U, so nothing looks like something else. */
        const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
        val MODELS = mapOf("RND" to "Round screen", "SPK" to "Speaker", "DSP" to "Small display")

        /** Luhn mod 32 over [body] (model, generation and number, without the dashes). */
        fun checkChar(body: String): Char {
            val n = ALPHABET.length
            var factor = 2
            var total = 0
            for (c in body.reversed()) {
                val addend = factor * ALPHABET.indexOf(c)
                factor = if (factor == 2) 1 else 2
                total += addend / n + addend % n
            }
            return ALPHABET[(n - total % n) % n]
        }

        /** Reads a typed or scanned ID. Case, spaces and dashes don't matter, and O, I and L are taken as 0, 1 and 1. Null if it isn't a valid ID. */
        fun parse(text: String?): DeviceId? {
            val t = text?.uppercase()?.filter { it.isLetterOrDigit() } ?: return null
            if (t.length != 3 + 1 + 6 + 1) return null
            val model = t.substring(0, 3)
            if (model !in MODELS) return null
            val gen = t[3].digitToIntOrNull()?.takeIf { it in 1..9 } ?: return null
            val digits = t.substring(4, 10).replace('O', '0').replace('I', '1').replace('L', '1')
            val number = digits.toIntOrNull()?.takeIf { digits.all(Char::isDigit) } ?: return null
            val given = t[10].let { when (it) { 'O' -> '0'; 'I', 'L' -> '1'; else -> it } }
            val id = DeviceId(model, gen, number)
            return if (id.check == given) id else null
        }
    }
}
