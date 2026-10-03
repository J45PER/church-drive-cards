package com.churchdrive.app.ui

import androidx.compose.runtime.compositionLocalOf
import org.json.JSONObject

/** Plain access to Home Assistant's commands for the cards that fetch their own data (to-do lists, the forecast). */
interface HaApi {
    /** One command; [done] gets its result, or null if it failed. */
    fun request(type: String, params: JSONObject, done: (Any?) -> Unit)

    /** A streaming command; every event goes to [onEvent]. Returns the subscription to [close], or -1. */
    fun subscribe(type: String, params: JSONObject, onEvent: (JSONObject) -> Unit): Int

    fun close(subscription: Int)
}

val LocalHaApi = compositionLocalOf<HaApi?> { null }

/** The signed-in person's name, for their own to-do list. */
val LocalUserName = compositionLocalOf<String?> { null }
