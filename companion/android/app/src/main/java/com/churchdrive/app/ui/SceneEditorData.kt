package com.churchdrive.app.ui

import org.json.JSONArray
import org.json.JSONObject

/** The most colours a scene made in the app can have; the house accepts nine. */
const val MAX_SCENE_COLOURS = 5

/** A scene being made or changed: [key] is null for a new one, [colours] are `#rrggbb`, [brightness] is a percentage (5 to 100). */
data class SceneDraft(val key: String?, val name: String, val colours: List<String>, val brightness: Int, val animated: Boolean)

fun newSceneDraft(): SceneDraft = SceneDraft(null, "", listOf("#5b8cfd", "#b36bff"), 60, false)

/** The draft for changing [scene] (one made in the app). */
fun draftOf(scene: LibraryScene): SceneDraft =
    SceneDraft(scene.key, scene.name, scene.hex.map { "#%06x".format(it and 0xFFFFFF) }.ifEmpty { listOf("#ffd9a0") }.take(MAX_SCENE_COLOURS), scene.brightnessPct ?: 60, scene.dynamic)

/** Why [draft] can't be saved yet, or null. */
fun sceneProblem(draft: SceneDraft): String? = when {
    draft.name.isBlank() -> "Give the scene a name."
    draft.colours.isEmpty() -> "Choose at least one colour."
    else -> null
}

fun SceneDraft.withColour(index: Int, hex: String): SceneDraft = copy(colours = colours.mapIndexed { i, c -> if (i == index) hex else c })

fun SceneDraft.withAdded(hex: String): SceneDraft = if (colours.size >= MAX_SCENE_COLOURS) this else copy(colours = colours + hex)

fun SceneDraft.withoutColour(index: Int): SceneDraft = if (colours.size <= 1) this else copy(colours = colours.filterIndexed { i, _ -> i != index })

fun SceneDraft.stepBrightness(direction: Int): SceneDraft = copy(brightness = (brightness + direction * 10).coerceIn(5, 100))

/** The body of `church_drive/scene/save`. */
fun sceneSaveBody(draft: SceneDraft): JSONObject = JSONObject().put(
    "scene",
    JSONObject()
        .also { if (draft.key != null) it.put("key", draft.key) }
        .put("name", draft.name.trim().take(32))
        .put("kind", "colour")
        .put("brightness", draft.brightness)
        .put("colors", JSONArray(draft.colours))
        .put("dynamic", draft.animated),
)
