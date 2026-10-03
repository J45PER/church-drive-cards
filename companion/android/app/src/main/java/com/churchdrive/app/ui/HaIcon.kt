package com.churchdrive.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import com.churchdrive.app.ha.IconPack

/**
 * An entity's own icon. `phu:` icons (the Hue-style set the dashboards use) are drawn from the
 * icon pack Home Assistant serves, and every `mdi:` icon is drawn from its path data (the common ones are built in,
 * see MdiIcons; the rest come from the full set in MdiAll); anything else, or while they're still loading, shows [fallback].
 */
@Composable
fun HaIcon(icon: String?, fallback: ImageVector, tint: Color, size: Dp, modifier: Modifier = Modifier) {
    val name = icon?.takeIf { it.startsWith("phu:") }?.removePrefix("phu:")
    val pack = IconPack.text
    val all = MdiAll.paths
    val mdi = icon?.takeIf { it.startsWith("mdi:") }?.removePrefix("mdi:")?.let { MdiIcons.paths[it] ?: all?.get(it) }
    val found = remember(name, mdi, pack) {
        when {
            mdi != null -> 24f to mdi
            name != null -> IconPack.find(name)
            else -> null
        }
    }
    val path = remember(found) { found?.let { runCatching { PathParser().parsePathString(it.second).toPath() }.getOrNull() } }

    if (found != null && path != null) {
        Canvas(modifier.size(size)) {
            val s = this.size.minDimension / found.first
            scale(s, s, pivot = Offset.Zero) { drawPath(path, color = tint) }
        }
    } else {
        Icon(fallback, contentDescription = null, tint = tint, modifier = modifier.size(size))
    }
}
