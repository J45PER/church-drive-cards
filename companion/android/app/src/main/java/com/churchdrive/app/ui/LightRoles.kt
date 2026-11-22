package com.churchdrive.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.churchdrive.app.ha.EntityState
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * The role each light has in themes (Primary, Ambient or Tertiary), one drop-down per light, for administrators. The house keeps it, so every app
 * sees the same roles; a light with no role is Primary.
 */
@Composable
internal fun LightRolesSection(lights: List<EntityState>) {
    if (!LocalIsAdmin.current || lights.isEmpty()) return
    val api = LocalHaApi.current
    val scope = rememberCoroutineScope()
    val all = LocalHybrids.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val tone = toneColors(Tone.Lights)
    var chosen by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    Text("ROLE IN THEMES", style = MaterialTheme.typography.labelMedium, color = muted)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        lights.forEach { light ->
            val role = chosen[light.entityId] ?: all.roleOf(light.entityId)
            RoleDropdown(light.friendlyName, role, tone) { picked ->
                chosen = chosen + (light.entityId to picked)
                scope.launch {
                    api?.ask("church_drive/light_role/set", JSONObject().put("entity_id", light.entityId).put("role", picked))
                    HybridRefresh.trigger?.invoke()
                }
            }
        }
    }
    Text(
        LIGHT_ROLES.joinToString("\n") { "${lightRoleLabel(it)}: ${lightRoleHint(it)}" } + ".",
        style = MaterialTheme.typography.bodySmall, color = muted,
    )
}

/** A light's name and its role on a tappable line that opens the centred picker. */
@Composable
private fun RoleDropdown(name: String, role: String, tone: ToneColors, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().height(Ui.TileHeight).clip(RoundedCornerShape(18.dp)).background(tone.onContainer.copy(alpha = 0.10f))
            .clickable(onClickLabel = "Change the role of $name") { open = true }.padding(start = 14.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        Text(lightRoleLabel(role), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        Icon(CdVectors.ChevronDown, contentDescription = null, tint = tone.onContainer)
    }
    if (open) PickDialog("Role of $name", LIGHT_ROLES, role, { "${lightRoleLabel(it)}: ${lightRoleHint(it)}" }, { open = false }, onPick)
}
