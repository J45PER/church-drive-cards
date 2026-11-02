package com.churchdrive.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * The house's own icons as [ImageVector]s, for the places that take a Material `Icons.*` vector (rows, buttons, navigation items). Each one is
 * built once from the same shapes [CdIcons] draws (so a change there is a change here), on the 200 unit grid with the even-odd rule, and is
 * tinted by `Icon` like any other vector. Use these instead of the Material icons for anything the icon audit has signed off.
 */
object CdVectors {
    private fun build(name: String): ImageVector {
        val shapes = CdIcons.icons.getValue(name)
        val b = ImageVector.Builder(name = "cd:$name", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 200f, viewportHeight = 200f)
        shapes.forEach { sh ->
            val nodes = PathParser().parsePathString(sh.d).toNodes()
            if (sh.scale != 1f) b.addGroup(scaleX = sh.scale, scaleY = sh.scale, pivotX = 100f, pivotY = 100f)
            b.addPath(pathData = nodes, pathFillType = PathFillType.EvenOdd, fill = SolidColor(Color.Black), fillAlpha = sh.alpha)
            if (sh.scale != 1f) b.clearGroup()
        }
        return b.build()
    }
    val Plus: ImageVector by lazy { build("plus") }
    val Minus: ImageVector by lazy { build("minus") }
    val Close: ImageVector by lazy { build("close") }
    val Back: ImageVector by lazy { build("back") }
    val ChevronDown: ImageVector by lazy { build("chevron") }
    val ChevronUp: ImageVector by lazy { build("chevron-up") }
    val ChevronRight: ImageVector by lazy { build("chevron-right") }
    val Info: ImageVector by lazy { build("info") }
    val SignOut: ImageVector by lazy { build("sign-out") }
    val SystemUpdate: ImageVector by lazy { build("system-update") }
    val Eye: ImageVector by lazy { build("eye") }
    val Pin: ImageVector by lazy { build("pin") }
    val History: ImageVector by lazy { build("history") }
    val BatteryCharging: ImageVector by lazy { build("battery-charging") }
    val ShieldOutline: ImageVector by lazy { build("shield-outline") }
    val Bulb: ImageVector by lazy { build("bulb") }
    val Sun: ImageVector by lazy { build("sun") }
    val SunHalf: ImageVector by lazy { build("sun-half") }
    val Flower: ImageVector by lazy { build("flower") }
    val Moon: ImageVector by lazy { build("moon") }
    val Bolt: ImageVector by lazy { build("bolt") }
    val Palette: ImageVector by lazy { build("palette") }
    val Cog: ImageVector by lazy { build("cog") }
    val Chat: ImageVector by lazy { build("chat") }
    val Home: ImageVector by lazy { build("home") }
    val Todo: ImageVector by lazy { build("todo") }
    val Mic: ImageVector by lazy { build("mic") }
    val Play: ImageVector by lazy { build("play") }
    val Pause: ImageVector by lazy { build("pause") }
}
