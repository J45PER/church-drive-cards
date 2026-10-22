package com.churchdrive.app.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.churchdrive.app.Session
import com.churchdrive.app.ha.ConnectionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The Church Drive companion on the watch: pages across (Alarm, Climate, Lights, Cleaning, Car charger, as the manager's Watch panel
 * set them), faces up and down within a page, and nested levels for Lights. The ring and the face's colour stay on the glass while the
 * content slides over them.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WatchData.restore(this)
        setContent { MaterialTheme { CompanionRoot(onExit = { finish() }) } }
    }
}

@Composable
private fun CompanionRoot(onExit: () -> Unit) {
    val context = LocalContext.current
    var signedIn by remember { mutableStateOf(Session(context).signedIn) }
    val connection by WatchHa.client.connection.collectAsState()
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { pullSession(context) }
        while (true) {
            signedIn = Session(context).signedIn
            if (signedIn) WatchHa.ensure(context)
            delay(2_000)
        }
    }
    val connected = connection == ConnectionState.Connected
    LaunchedEffect(connected) {
        if (connected) WatchData.load() else WatchData.lost()
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val ui = remember(maxWidth, maxHeight) { Ui(minOf(maxWidth, maxHeight) / 280f) }
        CompositionLocalProvider(LocalUi provides ui) {
            if (!signedIn) Message("Open Church Drive on your phone to sign this watch in.")
            else CompanionScreen(connected, onExit)
        }
    }
}

@Composable
private fun Message(text: String) {
    val ui = LocalUi.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = Color.White, fontSize = ui.sp(14f), textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = ui.d(44f)))
    }
}

@Composable
private fun CompanionScreen(connected: Boolean, onExit: () -> Unit) {
    val ui = LocalUi.current
    val entities by WatchData.entities.collectAsState()
    val config by WatchData.config.collectAsState()
    val floors by WatchData.floors.collectAsState()
    val looks by WatchData.looks.collectAsState()
    val areas by WatchData.areaNames.collectAsState()
    val loaded by WatchData.loaded.collectAsState()
    var stack by remember { mutableStateOf(listOf<LightsRef>()) }
    val env = LightsEnv(entities, config, floors, looks, areas)
    val open: (LightsRef) -> Unit = { stack = stack + it }

    CompositionLocalProvider(LocalOpenLights provides open) {
        // Every page's faces are made once, here, so the ring that follows a finger and the face that holds it share one state.
        val faceLists: Map<WatchPage, List<Face>> = mapOf(
            WatchPage.Alarm to listOf(alarmFace(entities, config)),
            WatchPage.Climate to climateFaces(entities, config),
            WatchPage.Lights to lightsTopFaces(env),
            WatchPage.Cleaning to cleaningFaces(entities, config),
            WatchPage.Car to carFaces(entities, config),
        )
        val pages = config.pages.map { it to faceLists.getValue(it) }.filter { it.second.isNotEmpty() }
        val nested = stack.lastOrNull()
        val nestedFaces = if (nested != null) lightsRefFaces(env, nested) else emptyList()

        val hState = rememberPagerState { pages.size }
        val vStates = WatchPage.entries.associateWith { p -> rememberPagerState { faceLists.getValue(p).size } }
        val nestedState = key(nested) { rememberPagerState { nestedFaces.size } }
        val scope = rememberCoroutineScope()

        // The glass stays lit for two minutes after the last touch or turn of the crown (a watch would switch it off after a few seconds).
        var lastUse by remember { mutableLongStateOf(System.currentTimeMillis()) }
        var awake by remember { mutableStateOf(true) }
        LaunchedEffect(lastUse) {
            awake = true
            delay(120_000)
            awake = false
        }
        LaunchedEffect(hState.currentPage, stack.size, nestedState.currentPage, vStates.values.sumOf { it.currentPage }) { lastUse = System.currentTimeMillis() }
        KeepAwake(awake)

        val currentFaces: List<Face>
        val currentIndex: Int
        if (nested != null) {
            currentFaces = nestedFaces
            currentIndex = nestedState.currentPage
        } else {
            val page = pages.getOrNull(hState.currentPage)
            currentFaces = page?.second.orEmpty()
            currentIndex = page?.let { vStates.getValue(it.first).currentPage } ?: 0
        }
        val spec = currentFaces.getOrNull(currentIndex)?.spec ?: RingSpec(bg = Color.Black, trackAlpha = 0.06f)

        fun back() {
            if (stack.isNotEmpty()) stack = stack.dropLast(1) else onExit()
        }
        BackHandler(enabled = stack.isNotEmpty()) { back() }

        Box(Modifier.fillMaxSize().pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    awaitPointerEvent(PointerEventPass.Initial)
                    val now = System.currentTimeMillis()
                    if (now - lastUse > 5_000) lastUse = now
                }
            }
        }) {
            Glass(spec)
            when {
                !loaded -> Message(if (connected) "Loading…" else "Connecting…")
                pages.isEmpty() -> Message("Nothing is set to show on the watch. Ask the manager to choose pages.")
                nested != null -> SlideIn(stack) { FaceList(nestedFaces, nestedState) }
                else -> HorizontalPager(state = hState, modifier = Modifier.fillMaxSize(), key = { pages[it].first.id }) { i ->
                    val (page, faces) = pages[i]
                    FaceList(faces, vStates.getValue(page)) { n -> scope.launch { hState.animateScrollToPage((hState.currentPage + n).coerceIn(0, pages.size - 1)) } }
                }
            }
            if (loaded && nested == null && pages.size > 1) PageDots(hState.currentPage, pages.size)
            // The strip at the left edge: swiping in from it goes back a level, and from the first level leaves the app.
            Box(
                Modifier.align(Alignment.CenterStart).width(ui.d(26f)).fillMaxHeight().pointerInput(stack.size) {
                    var total = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = { if (total > 40.dp.toPx()) back() },
                        onDragCancel = { total = 0f },
                    ) { change, amount ->
                        total += amount
                        change.consume()
                    }
                },
            )
        }
    }
}

/** Slides [content] in from the right when [key] changes (going in) and from the left (going back). */
@Composable
private fun SlideIn(key: Any, content: @Composable () -> Unit) {
    val progress = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { progress.animateTo(1f, tween(260)) }
    Box(Modifier.fillMaxSize().graphicsLayer {
        translationX = (1f - progress.value) * size.width * 0.3f
        alpha = progress.value
    }) { content() }
}

/** One page's faces, one after another down the glass, with a rail of dots at the right edge. */
@Composable
private fun FaceList(faces: List<Face>, state: PagerState, onEdge: (Int) -> Unit = {}) {
    val ui = LocalUi.current
    val scope = rememberCoroutineScope()
    val next: () -> Unit = { scope.launch { state.animateScrollToPage((state.currentPage + 1).coerceAtMost((faces.size - 1).coerceAtLeast(0))) } }
    // The crown turns the ring's value when the face has one (temperature, brightness); otherwise it moves between the faces, and from
    // the last one on (or a page with a single face) it moves between the pages.
    val crownStep = faces.getOrNull(state.currentPage)?.spec?.drag?.onStep
    val crownMod = Modifier.crown { n ->
        when {
            crownStep != null -> crownStep(n)
            faces.size <= 1 -> onEdge(n)
            else -> {
                val to = state.currentPage + n
                if (to in faces.indices) scope.launch { state.animateScrollToPage(to) } else onEdge(n)
            }
        }
    }
    CompositionLocalProvider(LocalNextFace provides next) {
        Box(Modifier.fillMaxSize().then(crownMod)) {
            VerticalPager(state = state, modifier = Modifier.fillMaxSize(), key = { faces.getOrNull(it)?.id ?: it }) { i ->
                val face = faces.getOrNull(i)
                if (face != null) Box(Modifier.fillMaxSize().ringDrag(face.spec)) { face.content() }
            }
            if (faces.size > 1) {
                // At most seven dots, a window round the face you are on.
                val cur = state.currentPage
                val start = (cur - 3).coerceAtLeast(0).coerceAtMost((faces.size - 7).coerceAtLeast(0))
                val shown = (start until minOf(faces.size, start + 7)).toList()
                Column(Modifier.align(Alignment.CenterEnd).padding(end = ui.d(20f)), verticalArrangement = Arrangement.spacedBy(ui.d(5f)), horizontalAlignment = Alignment.CenterHorizontally) {
                    for (i in shown) {
                        Box(Modifier.size(ui.d(4f), if (i == cur) ui.d(12f) else ui.d(4f)).clip(CircleShape).background(Color.White.copy(alpha = if (i == cur) 0.95f else 0.35f)))
                    }
                }
            }
        }
    }
}

/** The page dots at the bottom of the glass. */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.PageDots(current: Int, count: Int) {
    val ui = LocalUi.current
    Row(Modifier.align(Alignment.BottomCenter).padding(bottom = ui.d(8f)), horizontalArrangement = Arrangement.spacedBy(ui.d(5f)), verticalAlignment = Alignment.CenterVertically) {
        for (i in 0 until count) {
            Box(Modifier.size(if (i == current) ui.d(14f) else ui.d(5f), ui.d(5f)).clip(CircleShape).background(Color.White.copy(alpha = if (i == current) 1f else 0.35f)))
        }
    }
}
