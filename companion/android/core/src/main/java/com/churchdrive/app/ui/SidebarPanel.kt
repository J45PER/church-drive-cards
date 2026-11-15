package com.churchdrive.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState

// The one sidebar and the one bottom bar, for every Church Drive app. An app supplies only what is on them: its bar pages, and the pages of
// its sidebar. The look, the slide, the acrylic, Back, and the bar's divider and button are all here.

/** The corner of a sidebar card. */
val PanelShape = RoundedCornerShape(28.dp)

/** A card in the sidebar: the surface colour at 70%, as the bottom bar, so the page blurs through it. */
@Composable
fun PanelCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = PanelShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.70f)),
    ) { androidx.compose.foundation.layout.Column { content() } }
}

/** A row on a sidebar card: an icon (or [leading]), a name, a line under it, and what it does when pressed. */
@Composable
fun PanelRow(
    icon: ImageVector,
    text: String,
    supporting: String? = null,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    ListItem(
        headlineContent = { Text(text) },
        supportingContent = supporting?.let { { Text(it) } },
        leadingContent = leading ?: { Icon(icon, contentDescription = null) },
        trailingContent = trailing,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
    )
}

/** A sidebar page's title with its back arrow. */
@Composable
fun PanelTitle(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(CdVectors.Back, contentDescription = "Back") }
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 4.dp))
    }
}

/**
 * The scrolling column of a sidebar page. It keeps clear of the status bar and the sides, not the bottom: the sidebar already stops above
 * the bottom bar, so the list runs right down to it.
 */
@Composable
fun PageColumn(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}

/** Where Back goes from a sidebar page: the list ("account"); from the list, the sidebar shuts. */
fun sidebarParent(panel: String?): String? = if (panel == null || panel == "account") null else "account"

/** How deep a sidebar page is (the list 0, its pages 1), for the slide direction. */
fun sidebarDepth(panel: String?): Int = if (panel == null || panel == "account") 0 else 1

/**
 * The sidebar for any app: it slides in from the right, full width, stopping above the bottom bar ([bottomInset]). With [acrylic] (the page's
 * blur source) it is acrylic and every page is see-through. [page] draws the page named by the key it is given ("account" is the list); [back] says
 * where Back goes from a page, and [depth] how deep a page is, for the slide.
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun SidebarHost(
    panel: String?,
    onPanel: (String?) -> Unit,
    acrylic: HazeState?,
    bottomInset: Dp,
    back: (String?) -> String? = ::sidebarParent,
    depth: (String?) -> Int = ::sidebarDepth,
    page: @Composable (String) -> Unit,
) {
    // The last page shown, so a page keeps its words while it slides away.
    var shown by remember { mutableStateOf("account") }
    if (panel != null) shown = panel
    BackHandler(enabled = panel != null) { onPanel(back(panel)) }
    SidebarSheet(visible = panel != null, acrylic = acrylic, bottomInset = bottomInset) {
        Box(Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = shown,
                transitionSpec = {
                    // Going deeper, the new page slides over the old; going back, the old page slides away off the top of the one below.
                    val deeper = depth(targetState) > depth(initialState)
                    val move = if (deeper) slideInHorizontally { it } togetherWith slideOutHorizontally { -it / 4 }
                    else slideInHorizontally { -it / 4 } togetherWith slideOutHorizontally { it }
                    move.apply { targetContentZIndex = if (deeper) 1f else -1f }
                },
                label = "sidebar",
            ) { which ->
                Surface(
                    Modifier.fillMaxSize(),
                    color = if (acrylic != null) Color.Transparent else MaterialTheme.colorScheme.background,
                ) { page(which) }
            }
        }
    }
}

/**
 * The bottom bar for an app with no extra moods: its [pages], then the divider and the sidebar button. (The phone app keeps its own row inside
 * [AcrylicNavigationBar] because Clive's buttons take the bar's place; it uses the same divider and button.)
 */
@Composable
fun AppNavigationBar(acrylic: HazeState, sidebarOpen: Boolean, onSidebar: () -> Unit, pages: @Composable RowScope.() -> Unit) {
    AcrylicNavigationBar(acrylic) {
        pages()
        SidebarBarDivider()
        SidebarBarButton(open = sidebarOpen, onClick = onSidebar)
    }
}
