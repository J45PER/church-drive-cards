package com.churchdrive.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.churchdrive.app.ha.EntityState
import org.json.JSONObject

/** The dashboard's `entities` card: a plain list of rows, each an icon, a name and a value. */
@Composable
fun EntitiesCard(config: JSONObject, entities: Map<String, EntityState>) {
    val neutral = toneColors(Tone.Grey)
    val rows = config.optJSONArray("entities")
    EntityCard(neutral.container, neutral.onContainer) {
        config.optString("title").takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
        for (i in 0 until (rows?.length() ?: 0)) {
            // A row is an entity id, or { entity, name, icon }.
            val raw = rows?.opt(i)
            val spec = raw as? JSONObject ?: JSONObject().put("entity", raw?.toString().orEmpty())
            val id = spec.optString("entity")
            val entity = entities[id]
            val name = spec.optString("name").ifBlank { entity?.friendlyName ?: id }
            val unit = entity?.str("unit_of_measurement")?.let { " $it" }.orEmpty()
            val value = if (entity == null) "–" else presetLabel(entity.state) + unit
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ToneIconName(spec.optString("icon").ifBlank { entity?.str("icon") ?: "" }, Icons.Filled.Info, neutral, 36)
                Column(modifier = Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                }
                Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
