package com.churchdrive.app.wear

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ui.Tone

/** One of the vacuum's settings, a select entity: what it is called, its choices (until the house lists them), and the icon of each. */
private class CleaningSetting(
    val id: String,
    val title: String,
    val entity: String,
    val fallback: List<String>,
    /** Strengthening choices light up cumulatively on the ring; unordered ones light only the chosen. */
    val cumulative: Boolean,
    val icons: Map<String, String>,
)

private val SETTINGS = listOf(
    CleaningSetting(
        "suction", "Suction", VACUUM_SUCTION, listOf("Quiet", "Standard", "Turbo", "Max", "Boost_IQ"), true,
        mapOf("Quiet" to "volume-low", "Standard" to "fan-speed-1", "Turbo" to "fan-speed-2", "Max" to "fan-speed-3", "Boost_IQ" to "rocket-launch"),
    ),
    CleaningSetting(
        "intensity", "Cleaning intensity", VACUUM_INTENSITY, listOf("Normal", "Narrow", "Quick"), false,
        mapOf("Normal" to "robot-vacuum", "Narrow" to "arrow-collapse-horizontal", "Quick" to "flash"),
    ),
    CleaningSetting(
        "mop", "Mop intensity", VACUUM_MOP, listOf("Quiet", "Automatic", "Max"), true,
        mapOf("Quiet" to "water-minus", "Automatic" to "autorenew", "Max" to "water-plus"),
    ),
)

/** The cleaning page: the vacuum's controls, then its suction, cleaning intensity and mop intensity, one face each. */
@Composable
fun cleaningFaces(entities: Map<String, EntityState>, config: WatchConfig): List<Face> {
    val out = mutableListOf<Face>()
    if (!config.hides(WatchPage.Cleaning, "control")) out += cleaningControlFace(entities)
    for (s in SETTINGS) if (!config.hides(WatchPage.Cleaning, s.id)) out += cleaningSettingFace(entities, s)
    return out
}

private fun cleaningControlFace(entities: Map<String, EntityState>): Face {
    val vacuum = entities[VACUUM]
    val battery = entities[VACUUM_BATTERY]?.state?.toFloatOrNull()
    val colours = tone(if (vacuum?.state == "error") Tone.Red else if (vacuum == null || !vacuum.available) Tone.Grey else Tone.Blue)
    val accent = colours.accent
    val state = vacuum?.state
    val lit = mapOf(
        "start" to (state == "cleaning"),
        "stop" to (state == "idle" || state == "paused"),
        "home" to (state == "docked" || state == "returning"),
    )
    val word = (state?.replace('_', ' ')?.replaceFirstChar { it.uppercase() } ?: "Loading…") + (battery?.let { " · ${it.toInt()}%" } ?: "")
    return Face("cl:control", RingSpec(bg = colours.container, fill = battery?.let { RingFill((it / 100f).coerceIn(0f, 1f), accent) })) {
        Box(Modifier.fillMaxSize()) {
            FaceHeader(vacuum?.friendlyName ?: "Vacuum", word, accent)
            ButtonRow(30f, 100f, 80f) {
                ModeButton("play", "Start", lit.getValue("start"), accent, Ink, Slot(), enabled = vacuum != null, onClick = { WatchData.call("vacuum", "start", VACUUM) })
                ModeButton("stop", "Stop", lit.getValue("stop"), accent, Ink, Slot(), enabled = vacuum != null, onClick = { WatchData.call("vacuum", "stop", VACUUM) })
                ModeButton("home-import-outline", "Home", lit.getValue("home"), accent, Ink, Slot(), enabled = vacuum != null, onClick = { WatchData.call("vacuum", "return_to_base", VACUUM) })
            }
        }
    }
}

private fun cleaningSettingFace(entities: Map<String, EntityState>, s: CleaningSetting): Face {
    val select = entities[s.entity]
    val options = select?.options()?.takeIf { it.isNotEmpty() } ?: s.fallback
    val current = select?.state
    val index = options.indexOf(current)
    val colours = tone(if (select == null || !select.available) Tone.Grey else Tone.Blue)
    val accent = colours.accent
    val segs = RingSegs(accent, options.take(5).indices.map { i -> if (index >= 0 && (if (s.cumulative) i <= index else i == index)) 1f else 0.22f })
    fun label(o: String) = o.replace('_', ' ')
    val rows = rowsOf(options.take(5), 3)
    return Face("cl:${s.id}", RingSpec(bg = colours.container, segs = segs)) {
        Box(Modifier.fillMaxSize()) {
            FaceHeader(s.title, current?.let { label(it) } ?: "–", accent)
            rows.forEachIndexed { r, row ->
                val single = rows.size == 1
                val x = if (single) 30f else if (row.size >= 3) 54f else 76f
                ButtonRow(x, if (single) 100f else 94f + r * 60f, if (single) 80f else 54f) {
                    for (o in row) {
                        ModeButton(s.icons[o] ?: "robot-vacuum", label(o), current == o, accent, Ink, Slot(), enabled = select != null, onClick = {
                            WatchData.call("select", "select_option", s.entity, json("option" to o))
                        })
                    }
                }
            }
        }
    }
}
