package com.churchdrive.app.ui

import androidx.compose.runtime.compositionLocalOf
import kotlin.coroutines.resume
import org.json.JSONObject

/** Plain access to Home Assistant's commands for the cards that fetch their own data (to-do lists, the forecast). */
interface HaApi {
    /** One command; [done] gets its result, or null if it failed. */
    fun request(type: String, params: JSONObject, done: (Any?) -> Unit)

    /** A streaming command; every event goes to [onEvent]. Returns the subscription to [close], or -1. */
    fun subscribe(type: String, params: JSONObject, onEvent: (JSONObject) -> Unit): Int

    fun close(subscription: Int)

    /** Whether the line to the house is up right now. A failed command while it is up was refused, not lost. */
    val connected: Boolean get() = true
}

val LocalHaApi = compositionLocalOf<HaApi?> { null }

/** One command, waited for: its result, or null if it failed or timed out. */
suspend fun HaApi.ask(type: String, params: JSONObject): Any? =
    kotlinx.coroutines.suspendCancellableCoroutine { cont -> request(type, params) { if (cont.isActive) cont.resume(it) } }

/** The signed-in person's name, for their own to-do list. */
val LocalUserName = compositionLocalOf<String?> { null }

/** Every area's name by its id, for cards that name a room by its area. */
val LocalAreaNames = compositionLocalOf<Map<String, String>> { emptyMap() }

/** Whether a refused sign-in is tried again by itself, so the screen says so instead of blaming the token. */
val LocalCanRetrySignIn = compositionLocalOf { false }
