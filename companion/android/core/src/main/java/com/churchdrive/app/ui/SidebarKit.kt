package com.churchdrive.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.align
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeChild

/**
 * The bottom bar's sidebar button: a thin chevron at the right end, with no background and no name. It points out (open) when the
 * sidebar is shut and in (close) when it is open.
 */
@Composable
fun RowScope.SidebarBarButton(open: Boolean, onClick: () -> Unit) {
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    val label = if (open) "Close the sidebar" else "Open the sidebar"
    Box(
        Modifier.width(28.dp).fillMaxHeight().clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(20.dp).semantics { contentDescription = label }) {
            val w = size.width
            val h = size.height
            val path = Path().apply {
                if (open) {
                    moveTo(w * 0.35f, h * 0.2f); lineTo(w * 0.7f, h * 0.5f); lineTo(w * 0.35f, h * 0.8f)
                } else {
                    moveTo(w * 0.65f, h * 0.2f); lineTo(w * 0.3f, h * 0.5f); lineTo(w * 0.65f, h * 0.8f)
                }
            }
            drawPath(path, ink, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** The thin divider between a bar's pages and its sidebar button. */
@Composable
fun RowScope.SidebarBarDivider() {
    Box(
        Modifier.align(Alignment.CenterVertically).padding(horizontal = 4.dp).size(width = 1.dp, height = 36.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

/** The height of the bottom bar, including the phone's navigation bar: the sidebar stops above it. */
@Composable
fun sidebarBottomInset(): Dp = 80.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

/**
 * The sidebar: full width, sliding in from the right and stopping above the bottom bar ([bottomInset]). With [acrylic] (the page's blur
 * source) it is acrylic: the page blurs behind it, and its pages should be see-through too. Without it the sidebar is solid.
 */
@Composable
fun SidebarSheet(visible: Boolean, acrylic: HazeState?, bottomInset: Dp, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(initialOffsetX = { it }),
        exit = slideOutHorizontally(targetOffsetX = { it }),
    ) {
        val blur = acrylic?.let { rememberAcrylicStyle() }
        Surface(
            Modifier.fillMaxSize().padding(bottom = bottomInset).let { m -> if (acrylic != null && blur != null) m.hazeChild(acrylic, blur) else m },
            color = if (acrylic != null) Color.Transparent else MaterialTheme.colorScheme.background,
            content = content,
        )
    }
}
