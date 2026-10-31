package com.churchdrive.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.churchdrive.app.ha.CallService
import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ha.data
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// The assistant looks after the heating by himself (set up in Home Assistant: the "Clive: keep the climate comfortable" automation and
// its scripts). When somebody changes a thermostat by hand he stops adjusting that zone for a while, and the house keeps the time he
// takes over again in one input_datetime per zone. This is the Home page's reminder of that, with a button to hand it back at once.
// The same question comes as a notification with a Resume button; this is its copy on the Home page, like the car confirmation's.
// (The helpers' ids carry the old name: they are Home Assistant's, set up there, and never change here.)

const val PAUSE_CONTROL_SCRIPT = "script.clive_climate_control"
private val PAUSE_ENTITY = Regex("^input_datetime\\.clive_climate_(.+)_resume$")

/** One zone the assistant has stepped back from, until [resumeAtMs] (epoch milliseconds). */
data class AssistantPause(val zone: String, val resumeAtMs: Long)

/** The zones paused at [nowMs], soonest to end first. A time in the past (or 2000, "no pause") means he is in charge. */
fun assistantPauses(entities: Map<String, EntityState>, nowMs: Long): List<AssistantPause> =
    entities.values.mapNotNull { e ->
        val slug = PAUSE_ENTITY.find(e.entityId)?.groupValues?.get(1) ?: return@mapNotNull null
        if (!e.available) return@mapNotNull null
        val at = (e.attributes.optDouble("timestamp", 0.0) * 1000).toLong()
        if (at > nowMs) AssistantPause(slug.replace('_', ' ').replaceFirstChar { it.uppercase() }, at) else null
    }.sortedBy { it.resumeAtMs }

/** "35 min", "2 h" or "1 h 20 min": the time left, rounded up to the minute (never "0 min"). */
fun pauseLeftText(resumeAtMs: Long, nowMs: Long): String {
    val mins = ((resumeAtMs - nowMs + 59_999) / 60_000).coerceAtLeast(1)
    return when {
        mins < 60 -> "$mins min"
        mins % 60 == 0L -> "${mins / 60} h"
        else -> "${mins / 60} h ${mins % 60} min"
    }
}

/** "Downstairs: takes over again at 16:42 (in 1 h 20 min)". */
fun pauseLine(p: AssistantPause, nowMs: Long, zone: TimeZone = TimeZone.getDefault()): String {
    val clock = HomeTime.format(p.resumeAtMs, "HH:mm", zone.toZoneId())
    return "${p.zone}: takes over again at $clock (in ${pauseLeftText(p.resumeAtMs, nowMs)})"
}

/** The script call that hands the heating back to the assistant now (the house's script turns every paused zone back on). */
fun resumeAssistantData(): JSONObject = data("variables" to JSONObject().put("command", "resume"))

/** The reminder on the Home page, below the to-do highlights: nothing at all while the assistant is in charge everywhere. */
@Composable
fun AssistantPauseCard(entities: Map<String, EntityState>, call: CallService) {
    // The time left is worked out again every half minute.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = System.currentTimeMillis() } }
    val pauses = assistantPauses(entities, now)
    // Hides at once on a tap, then follows the house (which clears the time itself).
    var resumed by remember { mutableStateOf(false) }
    LaunchedEffect(pauses.isEmpty()) { if (pauses.isEmpty()) resumed = false }
    if (pauses.isEmpty() || resumed) return
    val name = LocalAssistant.current?.model?.name?.takeIf { it.isNotBlank() } ?: "The assistant"
    val blue = toneColors(Tone.Blue)
    EntityCard(blue.container, blue.onContainer) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Heating changed", style = MaterialTheme.typography.titleMedium)
                pauses.forEach { Text(pauseLine(it, now), style = MaterialTheme.typography.bodySmall) }
                Text("$name is not adjusting ${if (pauses.size == 1) "it" else "them"} until then.", style = MaterialTheme.typography.bodySmall)
            }
            TileRow(
                listOf(
                    TileItem("mdi:play", "Resume $name now", true) {
                        resumed = true
                        call("script", "turn_on", PAUSE_CONTROL_SCRIPT, resumeAssistantData())
                    },
                ),
                blue, blue.onContainer, perRow = 1,
            )
        }
    }
}
