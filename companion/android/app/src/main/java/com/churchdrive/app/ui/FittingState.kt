package com.churchdrive.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import org.json.JSONObject

/** Every fitting (a light with a main light and a ring of segments) as the house has it set, kept up to date by [rememberFittingList]. */
val LocalFittings = compositionLocalOf { FittingList() }

/** The house's fittings, asked for (again, if the line to the house wasn't up yet) and asked for again whenever one changes. */
@Composable
fun rememberFittingList(api: HaApi?): FittingList {
    val state = remember { mutableStateOf(FittingList()) }
    var changed by remember { mutableIntStateOf(0) }
    LaunchedEffect(api, changed) {
        var tries = 0
        while (tries < 8) {
            val list = parseFittings(api?.ask("church_drive/fitting", JSONObject()))
            // An empty answer can be the house still starting (the Govee lights come back a minute or two after Home Assistant), so ask again.
            if (list != null) { state.value = list; if (list.byRoom.isNotEmpty()) break }
            tries++
            delay(1500L * tries)
        }
    }
    DisposableEffect(api) {
        val sub = api?.subscribe("subscribe_events", JSONObject().put("event_type", "church_drive_fittings_changed")) { changed++ } ?: -1
        onDispose { if (sub >= 0) api?.close(sub) }
    }
    return state.value
}
