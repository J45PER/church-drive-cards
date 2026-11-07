package com.churchdrive.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * Material 3 with the house's own colours (Clive's palette, CliveColours.kt), not the phone's wallpaper (no Material You) and not
 * Google's. Backgrounds, surfaces, text and lines are the neutral set, the app's accent is Clive's indigo, and the second and third
 * accents are the palette's To-do and Air sets; errors are the palette's "wrong" red. Entities take their own colours through [Tone].
 */
@Composable
fun ChurchDriveTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) cliveDarkScheme else cliveLightScheme, content = content)
}

private fun tint(c: Color, over: Color, by: Float) = lerp(over, c, by)

val cliveLightScheme: ColorScheme = CliveColours.NeutralLight.let { n ->
    val tasks = CliveColours.Tasks
    val bad = CliveColours.Bad
    lightColorScheme(
        primary = n.primary, onPrimary = n.onPrimary,
        primaryContainer = tint(n.primary, Color.White, 0.16f), onPrimaryContainer = n.primary,
        secondary = n.soft, onSecondary = n.surface,
        secondaryContainer = n.surface2, onSecondaryContainer = n.text,
        tertiary = tasks.accent, onTertiary = tasks.dark,
        tertiaryContainer = lerp(Color.White, tasks.light, 0.5f), onTertiaryContainer = tasks.deep,
        error = bad.colour, onError = bad.dark,
        errorContainer = lerp(Color.White, bad.light, 0.5f), onErrorContainer = bad.deep,
        background = n.background, onBackground = n.text,
        surface = n.background, onSurface = n.text,
        surfaceVariant = n.surface2, onSurfaceVariant = n.soft,
        outline = n.soft, outlineVariant = n.line,
        surfaceTint = n.primary,
        inverseSurface = n.text, inverseOnSurface = n.background, inversePrimary = CliveColours.NeutralDark.primary,
        surfaceContainerLowest = n.surface, surfaceContainerLow = n.surface,
        surfaceContainer = n.surface, surfaceContainerHigh = n.surface2, surfaceContainerHighest = n.line,
        surfaceDim = n.line, surfaceBright = n.surface,
    )
}

val cliveDarkScheme: ColorScheme = CliveColours.NeutralDark.let { n ->
    val tasks = CliveColours.Tasks
    val bad = CliveColours.Bad
    darkColorScheme(
        primary = n.primary, onPrimary = n.onPrimary,
        primaryContainer = tint(n.primary, n.background, 0.22f), onPrimaryContainer = n.primary,
        secondary = n.soft, onSecondary = n.background,
        secondaryContainer = n.surface2, onSecondaryContainer = n.text,
        tertiary = tasks.colour, onTertiary = tasks.dark,
        tertiaryContainer = tasks.dark, onTertiaryContainer = tasks.colour,
        error = bad.colour, onError = bad.dark,
        errorContainer = bad.dark, onErrorContainer = bad.colour,
        background = n.background, onBackground = n.text,
        surface = n.background, onSurface = n.text,
        surfaceVariant = n.surface2, onSurfaceVariant = n.soft,
        outline = n.soft, outlineVariant = n.line,
        surfaceTint = n.primary,
        inverseSurface = n.text, inverseOnSurface = n.background, inversePrimary = CliveColours.NeutralLight.primary,
        surfaceContainerLowest = n.background, surfaceContainerLow = n.surface,
        surfaceContainer = n.surface, surfaceContainerHigh = n.surface2, surfaceContainerHighest = n.line,
        surfaceDim = n.background, surfaceBright = n.surface2,
    )
}
