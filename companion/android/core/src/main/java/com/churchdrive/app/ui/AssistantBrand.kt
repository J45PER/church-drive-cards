package com.churchdrive.app.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * What the house calls its voice assistant: the one place the apps get the name from, so renaming it is a setting in Home Assistant
 * (Church Drive > Configure > Assistant name) and never a code change. The house sends it with `church_drive/assist/info`; the last one
 * heard is kept on the device so screens are right before the house answers. Never write the name in code or in a string: use
 * [shown] (or `{name}` in text that goes through [branded]).
 */
object AssistantBrand {
    private const val PREFS = "assistant_brand"
    private const val KEY = "name"

    /** The name the house gave, or empty before any is known. */
    var name by mutableStateOf("")
        private set

    /** What to say to wake it ("Hey Jarvis"): from the house's wake word, or empty. */
    var wake by mutableStateOf("")
        private set

    /** The name to write on screen: the house's, or a plain word until it is known. */
    val shown: String get() = name.ifBlank { "Assistant" }

    /** Names it has answered to, which people still say: the wake word's name and earlier names. Kept lower case. */
    private val EARLIER_NAMES = setOf("jarvis", "clive")

    /** Every name a sentence may start or end with ("Clive, add milk"), lower case. */
    fun spokenNames(): Set<String> = (EARLIER_NAMES + name.lowercase()).filter { it.isNotBlank() }.toSet()

    fun load(context: Context) {
        name = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "").orEmpty()
    }

    /** Uses what the house said; remembers it when it is new. */
    fun update(context: Context, houseName: String?, wakePhrase: String?) {
        wake = wakePhrase.orEmpty()
        val n = houseName?.trim().orEmpty()
        if (n.isNotEmpty() && n != name) {
            name = n
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, n).apply()
        }
    }

    /** For tests and previews: sets the name without keeping it. */
    fun useForTest(newName: String, wakePhrase: String = "") {
        name = newName
        wake = wakePhrase
    }
}

/** [text] with `{name}` and `{wake}` filled in with the house's assistant name and wake phrase. */
fun branded(text: String): String =
    text.replace("{name}", AssistantBrand.shown).replace("{wake}", AssistantBrand.wake.ifBlank { "Hey ${AssistantBrand.shown}" })
