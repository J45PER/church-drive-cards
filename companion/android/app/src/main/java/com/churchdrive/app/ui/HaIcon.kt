package com.churchdrive.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import com.churchdrive.app.ha.IconPack

/**
 * An entity's own icon. `cd:` icons are the house's own set (CdIcons), `si:` icons are brand logos (Simple Icons), `phu:` icons (the Hue-style set the dashboards use) are drawn from the
 * icon pack Home Assistant serves, and every `mdi:` icon is drawn from its path data (the common ones are built in,
 * see MdiIcons; the rest come from the full set in MdiAll); anything else, or while they're still loading, shows [fallback].
 */
@Composable
fun HaIcon(iconName: String?, fallback: ImageVector, tint: Color, size: Dp, modifier: Modifier = Modifier) {
    // `cd:` icons are the house's own set (CdIcons); a few Hue-pack names the dashboards use are drawn with them instead.
    val icon = CdIcons.alias(iconName)
    val cd = icon?.takeIf { it.startsWith("cd:") }?.removePrefix("cd:")?.let { CdIcons.icons[it] }
    if (cd != null) {
        val paths = remember(cd) { cd.map { runCatching { PathParser().parsePathString(it.d).toPath().apply { fillType = PathFillType.EvenOdd } }.getOrNull() } }
        Canvas(modifier.size(size)) {
            val s = this.size.minDimension / 200f
            scale(s, s, pivot = Offset.Zero) {
                cd.forEachIndexed { i, shape ->
                    val path = paths[i] ?: return@forEachIndexed
                    if (shape.scale != 1f) scale(shape.scale, pivot = Offset(100f, 100f)) { drawPath(path, color = tint, alpha = shape.alpha) }
                    else drawPath(path, color = tint, alpha = shape.alpha)
                }
            }
        }
        return
    }
    val name = icon?.takeIf { it.startsWith("phu:") }?.removePrefix("phu:")
    val pack = IconPack.text
    val all = MdiAll.paths
    val mdi = icon?.takeIf { it.startsWith("mdi:") }?.removePrefix("mdi:")?.let { MdiIcons.paths[it] ?: all?.get(it) }
    // `si:` icons are brand logos (Simple Icons), read from their own set the first time one is wanted.
    val siName = icon?.takeIf { it.startsWith("si:") }?.removePrefix("si:")
    val context = LocalContext.current
    if (siName != null) LaunchedEffect(Unit) { SimpleIcons.load(context) }
    val si = siName?.let { SimpleIcons.paths?.get(SimpleIcons.slugFor(it)) }
    // `ol:` icons are our own logos (OwnLogos): the ones Simple Icons does not have, drawn with the even-odd rule.
    val olName = icon?.takeIf { it.startsWith("ol:") }?.removePrefix("ol:")
    if (olName != null) LaunchedEffect(Unit) { OwnLogos.load(context) }
    val ol = olName?.let { OwnLogos.paths?.get(SimpleIcons.slugFor(it)) }
    val found = remember(name, mdi, si, ol, pack) {
        when {
            mdi != null -> 24f to mdi
            si != null -> 24f to si
            ol != null -> 24f to ol
            name != null -> IconPack.find(name)
            else -> null
        }
    }
    val evenOdd = ol != null || (mdi != null && icon?.removePrefix("mdi:") in MdiIcons.EVEN_ODD)
    val path = remember(found, evenOdd) { found?.let { runCatching { PathParser().parsePathString(it.second).toPath().apply { if (evenOdd) fillType = PathFillType.EvenOdd } }.getOrNull() } }

    if (found != null && path != null) {
        Canvas(modifier.size(size)) {
            val s = this.size.minDimension / found.first
            scale(s, s, pivot = Offset.Zero) { drawPath(path, color = tint) }
        }
    } else {
        Icon(fallback, contentDescription = null, tint = tint, modifier = modifier.size(size))
    }
}
