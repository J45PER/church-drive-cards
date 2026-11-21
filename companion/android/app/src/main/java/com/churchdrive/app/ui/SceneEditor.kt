package com.churchdrive.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Asks the house for the scene library again, after a scene is saved or deleted. */
val LocalRefreshLibrary = compositionLocalOf<() -> Unit> { {} }

private fun colourOf(hex: String): Color = hexToArgb(hex)?.let { Color(it) } ?: Color.Gray

private fun hexOfHs(c: Pair<Double, Double>): String = hsToRgb(c.first, c.second).let { "#%02x%02x%02x".format(it[0], it[1], it[2]) }

/**
 * Makes or changes a scene: a name, up to five colours (one look for the whole room), a brightness and whether Hue lights animate it.
 * Saved in the house's scene library (`church_drive/scene/save`), so it shows in every room's settings.
 */
@Composable
fun SceneEditorPage(initial: SceneDraft, onClose: () -> Unit) {
    val api = LocalHaApi.current
    val refresh = LocalRefreshLibrary.current
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf(initial) }
    var sel by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    val tone = toneColors(Tone.Lights)
    val ink = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val at = sel.coerceIn(0, draft.colours.lastIndex)
    FullPage(if (initial.key == null) "New scene" else "Change scene", onClose) {
        Text("One look for the whole room. For different colours on different lights, make a hybrid scene instead.", style = MaterialTheme.typography.bodySmall, color = muted)
        OutlinedTextField(draft.name, { draft = draft.copy(name = it.take(32)) }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Text("Colours", style = MaterialTheme.typography.labelMedium, color = muted)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            draft.colours.forEachIndexed { i, hex ->
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(colourOf(hex))
                        .then(if (i == at) Modifier.border(3.dp, ink, CircleShape) else Modifier)
                        .clickable { sel = i },
                )
            }
        }
        TileRow(
            listOf(
                TileItem("cd:minus", "Remove colour", false) { draft = draft.withoutColour(at); sel = 0 },
                TileItem("cd:plus", "Add colour", false) { draft = draft.withAdded("#ffd84a"); sel = draft.colours.lastIndex },
            ),
            tone, ink, perRow = 2,
        )
        HsWheel(hexToHs(draft.colours[at]) ?: (30.0 to 60.0)) { pick -> draft = draft.withColour(at, hexOfHs(pick)) }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Brightness", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            TileRow(listOf(TileItem("cd:minus", "Dimmer", false, false) { draft = draft.stepBrightness(-1) }), tone, ink, perRow = 1)
            Text("${draft.brightness}%", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 4.dp))
            TileRow(listOf(TileItem("cd:plus", "Brighter", false, false) { draft = draft.stepBrightness(1) }), tone, ink, perRow = 1)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Animated on Hue lights", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Switch(draft.animated, { draft = draft.copy(animated = it) })
        }
        (message ?: sceneProblem(draft))?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = muted) }
        TileRow(
            listOf(
                TileItem("cd:close", "Cancel", false) { onClose() },
                TileItem("cd:check", "Save", true) {
                    if (sceneProblem(draft) != null) return@TileItem
                    scope.launch {
                        if (api?.ask("church_drive/scene/save", sceneSaveBody(draft)) != null) { refresh(); onClose() } else message = "The house didn't save it."
                    }
                },
            ) + (if (initial.key != null) listOf(TileItem("cd:bin", "Delete", false) {
                scope.launch {
                    if (api?.ask("church_drive/scene/delete", JSONObject().put("key", initial.key)) != null) { refresh(); onClose() } else message = "The house didn't delete it."
                }
            }) else emptyList()),
            tone, ink, perRow = 3,
        )
    }
}
