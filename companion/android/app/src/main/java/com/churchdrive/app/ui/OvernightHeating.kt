package com.churchdrive.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.churchdrive.app.ha.CallService
import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ha.data
import org.json.JSONObject
import java.util.Calendar

// Overnight heating (Climate page, administrators only): what the heating does at night to save energy. Off, Eco or Smart for the whole house,
// and the two times it runs between (midnight to 6am to begin with). Smart leaves the heating to the assistant; Eco and Off stop him adjusting it
// overnight and put it on Eco or turn it off, then he puts it back in the morning. The setting and the times are Home Assistant helpers; the work
// is done by the assistant's zone script. This mirrors the dashboard's `custom:overnight-heating-card` (src/overnight-heating-card.js, wording and
// sums in src/overnight-logic.js), which an administrator also sees on the web.

const val OVERNIGHT_MODE_ENTITY = "input_select.clive_overnight_mode"
const val OVERNIGHT_START_ENTITY = "input_datetime.clive_overnight_start"
const val OVERNIGHT_END_ENTITY = "input_datetime.clive_overnight_end"

/** The three modes, in the order shown, with what each does. */
val OVERNIGHT_MODES = listOf("Off", "Eco", "Smart")

/** "00:00:00" or "6:5" as "00:00" / "06:05"; null when it isn't a time. */
fun hmOf(value: String?): String? {
    val m = Regex("^(\\d{1,2}):(\\d{1,2})").find(value.orEmpty()) ?: return null
    val h = m.groupValues[1].toInt()
    val n = m.groupValues[2].toInt()
    if (h > 23 || n > 59) return null
    return "%02d:%02d".format(h, n)
}

/** Is [now] inside the window from [start] to [end] (which may run past midnight)? Equal or unreadable times mean no window. */
fun inOvernightWindow(start: String?, end: String?, now: String?): Boolean {
    val s = hmOf(start) ?: return false
    val e = hmOf(end) ?: return false
    val n = hmOf(now) ?: return false
    if (s == e) return false
    return if (s < e) s <= n && n < e else n >= s || n < e
}

/** A time moved by [direction] steps of [step] minutes, wrapping round the clock. */
fun stepTime(value: String?, direction: Int, step: Int = 30): String? {
    val t = hmOf(value) ?: return null
    val (h, m) = t.split(":").map { it.toInt() }
    val total = ((h * 60 + m + direction * step) % 1440 + 1440) % 1440
    return "%02d:%02d".format(total / 60, total % 60)
}

/** The mode as the helper reports it, or Smart when it is missing (so nothing changes until it is set). */
fun overnightModeOf(state: String?): String = if (state in OVERNIGHT_MODES) state!! else "Smart"

/** The sentence under the buttons for the chosen mode. */
fun describeOvernight(mode: String, start: String?, end: String?, name: String): String {
    val s = hmOf(start) ?: "00:00"
    val e = hmOf(end) ?: "06:00"
    return when (mode) {
        "Eco" -> "From $s to $e the heating is put on Eco and $name does not adjust it. In the morning $name puts it back."
        "Off" -> "From $s to $e the heating is turned off and $name does not adjust it. In the morning $name turns it back on."
        else -> "$name looks after the heating all night, as in the day."
    }
}

private fun nowHm(): String = Calendar.getInstance().let { "%02d:%02d".format(it.get(Calendar.HOUR_OF_DAY), it.get(Calendar.MINUTE)) }

/** What is being edited: the mode and the two times. Null while the card is only showing them. */
private data class OvernightDraft(val mode: String, val start: String, val end: String)

/**
 * The dashboard's `custom:overnight-heating-card`. Nothing is drawn for anyone but an administrator. As on the purifier schedule, nothing
 * changes from the normal view: Edit opens the mode and the two times with Save and Cancel, so a stray tap can't alter the heating.
 */
@Composable
fun OvernightHeatingCard(entities: Map<String, EntityState>, call: CallService) {
    if (!LocalIsAdmin.current) return
    val name = AssistantBrand.shown
    val modeEntity = entities[OVERNIGHT_MODE_ENTITY]?.takeIf { it.available }
    val start = hmOf(entities[OVERNIGHT_START_ENTITY]?.takeIf { it.available }?.state)
    val end = hmOf(entities[OVERNIGHT_END_ENTITY]?.takeIf { it.available }?.state)
    val mode = overnightModeOf(modeEntity?.state)
    val tone = toneColors(when (mode) { "Eco" -> Tone.Green; "Off" -> Tone.Grey; else -> Tone.Indigo })
    var draft by remember { mutableStateOf<OvernightDraft?>(null) }
    val ready = modeEntity != null && start != null && end != null
    val active = ready && mode != "Smart" && inOvernightWindow(start, end, nowHm())
    val ink = tone.onContainer
    val fold = rememberFold("grid-fold|overnight")
    if (modeEntity == null || start == null || end == null) {
        GridCard(tone.container, ink, 3f, "Overnight Heating") {
            Text("Overnight heating needs its settings in Home Assistant.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.at(0.5f, 1f, 7f, 1.5f))
        }
        return
    }
    val d = draft
    if (d == null && fold.value) {
        GridCard(tone.container, ink, 2.5f, "Overnight Heating", fold = fold) {
            FoldBand(0.5f, 7f, ink, if (active) "$mode now" else mode, "From $start until $end", listOf(TileItem("mdi:pencil", "Edit", false) { draft = OvernightDraft(mode, start, end) }), tone, true)
        }
        return
    }
    if (d == null) {
        GridCard(tone.container, ink, 6.5f, "Overnight Heating", fold = fold) {
            Column(Modifier.at(0.5f, 1f, 7f, 3.75f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                StatusPill(if (active) "$mode now" else mode, tone)
                Text(describeOvernight(mode, start, end, name), style = MaterialTheme.typography.bodyMedium, color = ink.copy(alpha = 0.9f))
                Text("From $start until $end", style = MaterialTheme.typography.bodyLarge, color = ink)
                Text(
                    "Thermostats set to Manual are left alone. Somebody changing a thermostat in the night keeps it until the morning.",
                    style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.8f),
                )
            }
            GridTiles(listOf(TileItem("mdi:pencil", "Edit", false) { draft = OvernightDraft(mode, start, end) }), 5f, 1f, tone, ink, true, stacked = false)
        }
        return
    }
    // Editing: no fold arrow, the card stays open until Save or Cancel.
    GridCard(tone.container, ink, 9f, "Overnight Heating") {
        GridTiles(
            listOf(
                TileItem(IconMap.of("climate_mode", "off", "mdi:power"), "Off", d.mode == "Off") { draft = d.copy(mode = "Off") },
                TileItem(IconMap.of("climate_preset", "eco", "mdi:leaf"), "Eco", d.mode == "Eco") { draft = d.copy(mode = "Eco") },
                TileItem("mdi:thermostat-auto", "Smart", d.mode == "Smart") { draft = d.copy(mode = "Smart") },
            ),
            1.5f, 1.5f, tone, ink, true, stacked = true,
        )
        Text(describeOvernight(d.mode, d.start, d.end, name), style = MaterialTheme.typography.bodyMedium, color = ink.copy(alpha = 0.85f), modifier = Modifier.at(0.5f, 3.5f, 7f, 1f))
        @Composable
        fun stepper(y: Float, label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
            Row(Modifier.at(0.5f, y, 7f, 1f), verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.bodyLarge, color = ink, modifier = Modifier.weight(1f))
                GridButton(TileItem("mdi:minus", "Earlier", false, false) { onMinus() }, tone, ink, true, false, Modifier.size(cell))
                Text(value, style = MaterialTheme.typography.titleMedium, color = ink, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.width(cell * 2f))
                GridButton(TileItem("mdi:plus", "Later", false, false) { onPlus() }, tone, ink, true, false, Modifier.size(cell))
            }
        }
        stepper(4.5f, "From", d.start, { stepTime(d.start, -1)?.let { draft = d.copy(start = it) } }, { stepTime(d.start, 1)?.let { draft = d.copy(start = it) } })
        stepper(6f, "Until", d.end, { stepTime(d.end, -1)?.let { draft = d.copy(end = it) } }, { stepTime(d.end, 1)?.let { draft = d.copy(end = it) } })
        GridTiles(
            listOf(
                TileItem("mdi:close", "Cancel", false) { draft = null },
                TileItem("mdi:check", "Save", false) {
                    if (d.mode != mode) call("input_select", "select_option", OVERNIGHT_MODE_ENTITY, data("option" to d.mode))
                    if (d.start != start) call("input_datetime", "set_datetime", OVERNIGHT_START_ENTITY, data("time" to "${d.start}:00"))
                    if (d.end != end) call("input_datetime", "set_datetime", OVERNIGHT_END_ENTITY, data("time" to "${d.end}:00"))
                    draft = null
                },
            ),
            7.5f, 1f, tone, ink, true, stacked = false,
        )
    }
}
