package com.churchdrive.app.widget

import android.content.Context
import android.os.Build
import androidx.compose.ui.graphics.Color
import com.churchdrive.app.ui.Tone
import com.churchdrive.app.ui.ToneColors
import com.churchdrive.app.ui.toneColorsFor

/**
 * The widgets' colours. The surface, the text and the button fill come from the phone (Android 12 and later give every
 * phone a palette made from its wallpaper), with a little see-through, so a widget sits on any wallpaper in light or
 * dark. Colour that means something (green disarmed, amber lights on, orange heating...) never follows the wallpaper:
 * it comes from [tone].
 */
class WidgetPalette(val dark: Boolean, val surface: Color, val onSurface: Color, val muted: Color) {
    /** The fill of a button or tile. */
    val tile: Color get() = onSurface.copy(alpha = 0.10f)

    fun tone(tone: Tone): ToneColors = toneColorsFor(tone, dark)

    companion object {
        fun of(context: Context): WidgetPalette {
            val dark = isDark(context)
            if (Build.VERSION.SDK_INT >= 31) {
                fun c(id: Int) = Color(context.getColor(id))
                return if (dark) {
                    WidgetPalette(true, c(android.R.color.system_neutral1_900).copy(alpha = 0.80f), c(android.R.color.system_neutral1_100), c(android.R.color.system_neutral2_300))
                } else {
                    WidgetPalette(false, c(android.R.color.system_neutral1_50).copy(alpha = 0.84f), c(android.R.color.system_neutral1_900), c(android.R.color.system_neutral2_600))
                }
            }
            val grey = toneColorsFor(Tone.Grey, dark)
            return WidgetPalette(dark, grey.container.copy(alpha = if (dark) 0.84f else 0.90f), grey.onContainer, grey.onContainer.copy(alpha = 0.7f))
        }
    }
}
