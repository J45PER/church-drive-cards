package com.churchdrive.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/**
 * What you just asked for, shown at once while the house catches up. A control that only shows what Home Assistant reports looks laggy: you drag
 * a slider or press a button and nothing changes until the light has changed and told us. [ask] keeps the value you chose until [actual] changes
 * (the house has answered) or [timeoutMs] passes (it never will), then [value] is the real one again.
 */
class Pending<T>(private val state: androidx.compose.runtime.MutableState<T?>) {
    var asked: T?
        get() = state.value
        private set(v) { state.value = v }

    fun ask(value: T) { asked = value }

    fun clear() { asked = null }

    fun value(actual: T): T = asked ?: actual
}

@Composable
fun <T> rememberPending(actual: Any?, timeoutMs: Long = 4000): Pending<T> {
    val state = remember { mutableStateOf<T?>(null) }
    LaunchedEffect(actual) { state.value = null }
    LaunchedEffect(state.value) { if (state.value != null) { delay(timeoutMs); state.value = null } }
    return remember { Pending(state) }
}
