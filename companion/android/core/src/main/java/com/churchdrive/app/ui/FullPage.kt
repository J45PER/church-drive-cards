package com.churchdrive.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.background
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A full-screen page for editing something (a task, the tasks to check): a back arrow and a title, then a scrolling body. It
 * respects the screen's edges and the keyboard, so the last buttons can always be reached. Back (the arrow or the phone's)
 * calls [onBack]. [handleBack] runs first on the arrow and the system back; when it returns true (a page that has a view of its own open, such as an editor,
 * closing that view) the page stays.
 */
@Composable
fun FullPage(title: String, onBack: () -> Unit, handleBack: () -> Boolean = { false }, tone: ToneColors? = null, content: @Composable ColumnScope.() -> Unit) {
    PopupWindow(onDismissRequest = onBack, handleBack = handleBack) { close ->
        val bg = MaterialTheme.colorScheme.background
        val haze = remember { HazeState() }
        val style = rememberAcrylicStyle()
        val top = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding()
        val bar = 56.dp
        // The page's ground: the app's background with a wash of the page's colour from the top, so a page has the colour of the list it belongs to.
        val ground = if (tone == null) Brush.verticalGradient(listOf(bg, bg))
        else Brush.verticalGradient(listOf(lerp(bg, tone.accent, 0.16f), lerp(bg, tone.container, 0.55f), bg))
        Box(Modifier.fillMaxSize().background(bg)) {
            Box(Modifier.fillMaxSize().haze(haze).background(ground)) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(bottom = 0.dp).verticalScroll(rememberScrollState())
                        .padding(start = 16.dp, end = 16.dp, top = top + bar + 8.dp, bottom = 8.dp).safeDrawingPadding2(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    content()
                    Spacer(Modifier.height(96.dp)) // clears the gesture bar even where the dialog window reports no bottom inset
                }
            }
            // The title bar is acrylic: the page scrolls and blurs behind it.
            Row(
                modifier = Modifier.fillMaxWidth().hazeChild(haze, style).height(top + bar).padding(top = top).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.clickable(onClickLabel = "Back") { if (!handleBack()) close() }.padding(12.dp)) {
                    HaIcon("mdi:arrow-left", CdVectors.Info, MaterialTheme.colorScheme.onBackground, 24.dp)
                }
                Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** The page's side and bottom edges (the keyboard and the gesture bar); its top is the acrylic bar's. */
@Composable
private fun Modifier.safeDrawingPadding2(): Modifier =
    this.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
