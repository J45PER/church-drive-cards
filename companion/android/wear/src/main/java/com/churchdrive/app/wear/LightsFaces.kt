package com.churchdrive.app.wear

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.Text
import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ui.SceneLooks
import com.churchdrive.app.ui.Tone
import kotlin.math.roundToInt

/** Where a nested list of the Lights page goes: a floor's rooms, or a room's zones. */
sealed interface LightsRef {
    data class FloorRooms(val floor: Int) : LightsRef
    data class RoomZones(val head: String) : LightsRef
}

/** Opens a nested list (the face's chip and a room's disc use it). Provided by the app's navigation. */
val LocalOpenLights = compositionLocalOf<(LightsRef) -> Unit> { {} }

/** Everything the lights faces need to know about the house. */
class LightsEnv(
    val entities: Map<String, EntityState>,
    val config: WatchConfig,
    val floors: List<WFloor>,
    val looks: SceneLooks,
    val areaNames: Map<String, String>,
)

private val AMBER = tone(Tone.Amber)
private val GREY = tone(Tone.Grey)

private fun LightsEnv.roomName(r: WRoom): String =
    r.title ?: r.area?.let { areaNames[it] } ?: entities[r.head]?.friendlyName ?: r.head

private fun LightsEnv.zoneName(z: WZone): String = z.name ?: entities[z.entity]?.friendlyName ?: z.entity

private fun LightsEnv.isOn(id: String) = entities[id]?.state == "on"

private fun brightnessPct(e: EntityState?): Int? = e?.num("brightness")?.let { (it / 2.55).roundToInt().coerceIn(1, 100) }

private fun LightsEnv.roomOn(r: WRoom) = isOn(r.head) || r.zones.any { isOn(it.entity) }

/** How bright a room is: its group light's brightness, else the brightest zone that is on. */
private fun LightsEnv.roomBrightness(r: WRoom): Int {
    if (isOn(r.head)) return brightnessPct(entities[r.head]) ?: 100
    return r.zones.filter { isOn(it.entity) }.maxOfOrNull { brightnessPct(entities[it.entity]) ?: 100 } ?: 0
}

private fun LightsEnv.visibleRooms(floor: WFloor): List<WRoom> = floor.rooms.filter { !config.hides(WatchPage.Lights, it.head) }

private fun LightsEnv.visibleZones(r: WRoom): List<WZone> = r.zones.filter { !config.hides(WatchPage.Lights, it.entity) }

/** The first list of the Lights page: floors, rooms or zones, as the manager chose. */
@Composable
fun lightsTopFaces(env: LightsEnv): List<Face> {
    val out = mutableListOf<Face>()
    when (env.config.lightsTop) {
        LightsTop.Floors -> env.floors.forEachIndexed { i, f ->
            val rooms = env.visibleRooms(f)
            if (rooms.isNotEmpty()) out += key("f$i") { floorFace(env, i, f, rooms) }
        }
        LightsTop.Rooms -> env.floors.flatMap { f -> env.visibleRooms(f).map { f to it } }.forEach { (f, r) -> out += key(r.head) { roomFace(env, r, f.name) } }
        LightsTop.Zones -> env.floors.flatMap { f -> env.visibleRooms(f).map { f to it } }.forEach { (f, r) ->
            val zones = env.visibleZones(r)
            if (zones.isEmpty()) out += key(r.head) { controlFace(env, r, null, f.name) }
            else zones.forEach { z -> out += key(z.entity) { controlFace(env, r, z, env.roomName(r)) } }
        }
    }
    return out
}

/** The nested list a navigation face opened. */
@Composable
fun lightsRefFaces(env: LightsEnv, ref: LightsRef): List<Face> = when (ref) {
    is LightsRef.FloorRooms -> {
        val floor = env.floors.getOrNull(ref.floor)
        floor?.let { f -> env.visibleRooms(f).map { r -> key(r.head) { roomFace(env, r, f.name) } } }.orEmpty()
    }
    is LightsRef.RoomZones -> {
        val found = env.floors.firstNotNullOfOrNull { f -> f.rooms.firstOrNull { it.head == ref.head }?.let { f to it } }
        if (found == null) emptyList() else {
            val (f, r) = found
            val zones = env.visibleZones(r)
            buildList {
                add(key("all:${r.head}") { controlFace(env, r, null, if (zones.isEmpty()) f.name else env.roomName(r)) })
                zones.forEach { z -> add(key(z.entity) { controlFace(env, r, z, env.roomName(r)) }) }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------- navigation faces

private fun navSpec(on: Boolean, brightness: Int) = RingSpec(
    bg = (if (on) AMBER else GREY).container,
    fill = RingFill(if (on) (brightness / 100f).coerceIn(0.05f, 1f) else 0f, (if (on) AMBER else GREY).accent, if (on) 1f else 0.5f),
)

@Composable
private fun PowerDisc(on: Boolean, accent: Color, onClick: () -> Unit) {
    val ui = LocalUi.current
    Box(
        Modifier.at(ui, 98f, 88f, 84f, 84f).clip(CircleShape).let { if (on) it.background(accent, CircleShape) else it.border(1.dp, Ink.copy(alpha = 0.4f), CircleShape) }.clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { WIcon("lightbulb", if (on) OnAccent else Ink, ui.d(40f)) }
}

@Composable
private fun floorFace(env: LightsEnv, index: Int, floor: WFloor, rooms: List<WRoom>): Face {
    val on = rooms.any { env.roomOn(it) }
    val count = rooms.count { env.roomOn(it) }
    val colours = if (on) AMBER else GREY
    val open = LocalOpenLights.current
    return Face("lf:$index", navSpec(on, if (on) rooms.maxOf { env.roomBrightness(it) } else 0)) {
        val ui = LocalUi.current
        Box(Modifier.fillMaxSize()) {
            FaceHeader(floor.name, if (count > 0) "$count of ${rooms.size} rooms on" else "All off", colours.accent)
            PowerDisc(on, colours.accent) { switchRooms(rooms, !on) }
            Box(Modifier.at(ui, 0f, 196f, 280f, 30f), contentAlignment = Alignment.Center) {
                PillChip("${rooms.size} rooms ›", Ink, Modifier.height(ui.d(30f))) { open(LightsRef.FloorRooms(index)) }
            }
        }
    }
}

@Composable
private fun roomFace(env: LightsEnv, room: WRoom, floorName: String): Face {
    val on = env.roomOn(room)
    val colours = if (on) AMBER else GREY
    val zones = env.visibleZones(room)
    val open = LocalOpenLights.current
    return Face("lr:${room.head}", navSpec(on, env.roomBrightness(room))) {
        val ui = LocalUi.current
        Box(Modifier.fillMaxSize()) {
            FaceHeader(env.roomName(room), "$floorName · ${if (on) "On" else "Off"}", colours.accent)
            PowerDisc(on, colours.accent) { switchRooms(listOf(room), !on) }
            Box(Modifier.at(ui, 0f, 196f, 280f, 30f), contentAlignment = Alignment.Center) {
                PillChip(if (zones.isEmpty()) "Scenes ›" else "${zones.size} zones ›", Ink, Modifier.height(ui.d(30f))) { open(LightsRef.RoomZones(room.head)) }
            }
        }
    }
}

private fun switchRooms(rooms: List<WRoom>, on: Boolean) {
    for (r in rooms) WatchData.call("light", if (on) "turn_on" else "turn_off", r.head)
}

// ---------------------------------------------------------------------------------------------------- the control face

private fun sceneColours(looks: SceneLooks, s: WScene): List<Color> =
    (looks.colours(s.key, s.name) ?: looks.whiteColours(s.key, s.name))?.map { Color(it) } ?: listOf(Color(0xFF9AA0A6), Color(0xFF5F6368))

/** Scenes split evenly over at most two pages: up to six on one, otherwise half and half (eight is four and four, seven is four and three). */
fun sceneGrid(scenes: List<WScene>, page: Int): Pair<List<WScene>, Int> {
    val pages = if (scenes.size > 6) 2 else 1
    val per = (scenes.size + pages - 1) / pages
    val p = if (pages == 1) 0 else page.mod(pages)
    return scenes.drop(p * per).take(per) to pages
}

/** A zone, or the whole room, to set: brightness on the ring and in the figure, scenes underneath. */
@Composable
private fun controlFace(env: LightsEnv, room: WRoom, zone: WZone?, sub: String): Face {
    val id = zone?.entity ?: room.head
    val entity = env.entities[id]
    val on = entity?.state == "on"
    val dimmable = entity == null || entity.dimmable()
    val pending = rememberPending(id)
    var page by remember(id) { mutableIntStateOf(0) }
    val serverBri = brightnessPct(entity) ?: 100
    val bri = (pending.value?.roundToInt()) ?: serverBri
    val colours = if (on) AMBER else GREY
    val accent = colours.accent
    val name = if (zone != null) env.zoneName(zone) else if (room.zones.isEmpty()) env.roomName(room) else "Whole room"
    val scenes = room.scenesFor(zone)
    val open = LocalOpenLights.current

    fun send(v: Float) {
        val pct = v.roundToInt().coerceIn(0, 100)
        if (pct <= 2) WatchData.call("light", "turn_off", id) else WatchData.call("light", "turn_on", id, json("brightness_pct" to pct))
    }

    val spec = if (dimmable) RingSpec(
        bg = colours.container,
        fill = RingFill(if (on || pending.value != null) bri / 100f else 0f, accent, if (on || pending.value != null) 1f else 0.5f),
        dot = RingDot(if (on || pending.value != null) bri / 100f else 0f, accent),
        drag = RingDrag { f -> pending.set(f * 100f, 450) { send(it) } },
        instant = pending.dragging,
    ) else RingSpec(bg = colours.container, fill = RingFill(if (on) 1f else 0f, accent))

    return Face("l:$id", spec) {
        val ui = LocalUi.current
        Box(Modifier.fillMaxSize()) {
            FaceHeader(name, "$sub · ${if (on) (if (dimmable) "$bri%" else "On") else "Off"}", accent)
            if (!dimmable) {
                PowerDisc(on, accent) { WatchData.call("light", if (on) "turn_off" else "turn_on", id) }
                Note("Only on and off", 0f, 190f, 280f, Ink.copy(alpha = 0.75f))
            } else {
                Stepper(
                    "$bri", "%", Ink, 70f, !on,
                    onMinus = { pending.set((bri - 10).coerceAtLeast(10).toFloat(), 600) { send(it) } },
                    onPlus = { if (!on) WatchData.call("light", "turn_on", id) else pending.set((bri + 10).coerceAtMost(100).toFloat(), 600) { send(it) } },
                    onValue = { WatchData.call("light", if (on) "turn_off" else "turn_on", id) },
                    size = 30f, stepSize = 32f,
                )
                if (scenes.isEmpty()) Note("No scenes chosen for this light", 46f, 150f, 188f, Ink.copy(alpha = 0.75f))
                else {
                    val (shown, pages) = sceneGrid(scenes, page)
                    val columns = if (shown.size == 4) 2 else 3
                    val left = if (columns == 2) 78f else 44f
                    Column(Modifier.at(ui, left, if (columns == 2) 112f else 114f, 280f - 2 * left), horizontalAlignment = Alignment.CenterHorizontally) {
                        for (row in shown.chunked(columns)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(ui.d(2f))) {
                                for (s in row) {
                                    val playing = on && env.entities[s.selectEntity]?.state.equals(s.name, ignoreCase = true)
                                    SceneDisc(s.name, sceneColours(env.looks, s), playing, size = if (columns == 2) 38f else 36f, width = if (columns == 2) 60f else 56f) {
                                        if (s.haScene != null) WatchData.call("scene", "turn_on", s.haScene)
                                        else WatchData.call("church_drive", "apply_scene", s.target, json("scene" to s.key))
                                    }
                                }
                            }
                        }
                    }
                    // The page button never moves: bottom middle, the same place on every page.
                    if (pages > 1) Box(Modifier.at(ui, 0f, 226f, 280f, 28f), contentAlignment = Alignment.Center) {
                        PillChip(if (page.mod(pages) == 0) "1 / $pages ›" else "‹ ${page.mod(pages) + 1} / $pages", Ink, Modifier.height(ui.d(28f))) { page += 1 }
                    }
                }
            }
        }
    }
}
