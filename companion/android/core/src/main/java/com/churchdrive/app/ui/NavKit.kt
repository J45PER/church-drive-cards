package com.churchdrive.app.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild

// What every app's page bar shares: pressing a button takes the page to its top, and the bar is acrylic (the page blurs behind it).

/**
 * Pressing a bar or rail button takes that page to its top: the page's scroll goes back to 0 and the collapsing title bar opens. Call the
 * returned function from every button's click (also for the page that is already open). It does nothing until the first press, so a
 * turned phone keeps its place.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberToTop(scroll: ScrollState, bar: TopAppBarScrollBehavior? = null): () -> Unit {
    var presses by remember { mutableIntStateOf(0) }
    LaunchedEffect(presses) {
        if (presses == 0) return@LaunchedEffect
        bar?.state?.heightOffset = 0f
        bar?.state?.contentOffset = 0f
        scroll.scrollTo(0)
    }
    return { presses++ }
}

/** The page's own padding from a Scaffold without the bottom: the bottom is left to the page so it can scroll under the acrylic bar. */
@Composable
fun PaddingValues.withoutBottom(): PaddingValues {
    val direction = LocalLayoutDirection.current
    return PaddingValues(start = calculateStartPadding(direction), top = calculateTopPadding(), end = calculateEndPadding(direction), bottom = 0.dp)
}

/** Marks the page that scrolls behind an [AcrylicNavigationBar]: it fills the Scaffold's body, bar included. */
fun Modifier.acrylicBehind(state: HazeState): Modifier = haze(state)

/**
 * The page bar as acrylic, as on the Manager website (docs/STYLE.md): the page blurs behind it (about 18 px), through a 70% tint of the bar's
 * own colour, with a faint line along its top edge. Phones before Android 12 cannot blur, so there the tint is nearly solid. The body behind
 * it is marked with [acrylicBehind], with its bottom padding given to the page so it scrolls under the bar.
 */
/** The acrylic look of the bar, also used by the sidebar: the page blurs through a 70% tint of the bar's colour. */
@Composable
fun rememberAcrylicStyle(): HazeStyle {
    val base = MaterialTheme.colorScheme.surfaceContainer
    return remember(base) {
        HazeStyle(
            backgroundColor = base,
            tints = listOf(HazeTint(base.copy(alpha = 0.70f))),
            blurRadius = 18.dp,
            noiseFactor = 0f,
            fallbackTint = HazeTint(base.copy(alpha = 0.92f)),
        )
    }
}

@Composable
fun AcrylicNavigationBar(state: HazeState, content: @Composable RowScope.() -> Unit) {
    val line = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    val style = rememberAcrylicStyle()
    NavigationBar(
        modifier = Modifier.hazeChild(state, style).drawBehind { drawLine(line, Offset(0f, 0.5f), Offset(size.width, 0.5f), 1.dp.toPx()) },
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        content = content,
    )
}

/** The bottom bar's selected page: a white pill with its icon knocked out (dark on white), in both themes. */
@Composable
fun navBarItemColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = Color(0xFF15171C),
    selectedTextColor = MaterialTheme.colorScheme.onSurface,
    indicatorColor = Color.White,
)

/** The same white pill for a side rail's selected page. */
@Composable
fun navRailItemColors() = NavigationRailItemDefaults.colors(
    selectedIconColor = Color(0xFF15171C),
    selectedTextColor = MaterialTheme.colorScheme.onSurface,
    indicatorColor = Color.White,
)
