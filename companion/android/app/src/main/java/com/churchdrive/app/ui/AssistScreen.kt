package com.churchdrive.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.flow.first

/**
 * The three colours of the assistant, a gradient like Siri's or Gemini's. They come from the phone's own theme (Material You: the
 * primary, tertiary and secondary colours of the wallpaper palette), so Jarvis matches the rest of the phone.
 */
@androidx.compose.runtime.Immutable
class AssistPalette(val first: Color, val second: Color, val third: Color) {
    val across: Brush get() = Brush.horizontalGradient(listOf(first, second, third))
}

/** The original Church Drive look: indigo, purple and green. */
private val ClassicPalette = AssistPalette(Color(0xFF5C6BC0), Color(0xFFAB47BC), Color(0xFF4FD08A))

@Composable
fun assistPalette(): AssistPalette {
    val s = MaterialTheme.colorScheme
    val classic = AssistColourSetting.classic
    return remember(classic, s.primary, s.tertiary, s.secondary) { if (classic) ClassicPalette else AssistPalette(s.primary, s.tertiary, s.secondary) }
}

/**
 * The assistant, full screen (the orb to start with, then the conversation). It opens and closes the same way as every other pop-up
 * (see [PopupWindow]): it rises from below with a fade, and leaves the same way. [onClose] is called once it has gone.
 */
@Composable
fun AssistPanel(
    model: AssistModel,
    onClose: () -> Unit,
    onTask: (String) -> Unit,
    /** The introduction to show first (and say out loud), or null. */
    intro: IntroKind? = null,
    /** What the introduction lists: everything the first time, then only what is new. */
    introFeatures: List<AssistFeature> = ASSIST_FEATURES,
    person: String = "",
    onIntroDone: () -> Unit = {},
    /** "Try it" in the introduction: done with it, and start listening (asking for the microphone first if needed). */
    onIntroTry: () -> Unit = { onIntroDone(); model.listen() },
    /** Whether "listen for the wake word" is on, and a way to turn it on (the introduction offers it). */
    wakeOn: Boolean = true,
    onEnableWake: () -> Unit = {},
    /** Open with the keyboard up instead of listening (the person's choice in Settings). */
    startTyping: Boolean = false,
    /** Shown inside the app (over the page, under the app's round Jarvis button) instead of as a window of its own. */
    embedded: Boolean = false,
) {
    var shown by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val close: () -> Unit = { if (!leaving) { leaving = true; shown = false } }
    // Said "that's it": he closes himself once he has answered, with the same exit.
    LaunchedEffect(model.closeRequested) { if (model.closeRequested) close() }
    LaunchedEffect(leaving) { if (leaving) { kotlinx.coroutines.delay(PopupMotion.ExitMs + 30L); onClose() } }
    if (!embedded) QuietWindow()
    androidx.activity.compose.BackHandler(enabled = !leaving) { close() }
    // A dialog doesn't set the text colour, so it would be dark on dark.
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        AnimatedVisibility(
            visible = shown, modifier = Modifier.fillMaxSize(),
            // He comes up out of his button: a soft fade and a small swell, from the bottom right, not a sheet sliding over the page.
            enter = fadeIn(tween(PopupMotion.EnterMs)) + scaleIn(tween(PopupMotion.EnterMs), initialScale = 0.9f, transformOrigin = TransformOrigin(0.9f, 0.95f)),
            exit = fadeOut(tween(PopupMotion.ExitMs)) + scaleOut(tween(PopupMotion.ExitMs), targetScale = 0.9f, transformOrigin = TransformOrigin(0.9f, 0.95f)),
        ) {
            if (intro != null) {
                AssistIntro(
                    kind = intro, features = introFeatures, person = person, assistant = model.name, wakeWord = model.wakeWord, model = model,
                    onTry = onIntroTry, onSkip = onIntroDone, wakeOn = wakeOn, onEnableWake = onEnableWake,
                )
            } else FullAssist(model, close, onTask, startTyping, embedded)
        }
    }
}

/**
 * The cloud: three soft clouds (the phone theme's three accent colours) that drift slowly round each other and blend where they meet. Calm when it
 * is waiting, a little livelier and a gentle swell with the voice when listening, thinking or speaking.
 */
@Composable
fun AssistOrb(state: AssistState, level: Float, size: androidx.compose.ui.unit.Dp) {
    val pal = assistPalette()
    val t = rememberInfiniteTransition(label = "orb")
    // One drift cycle, always the same length (changing it with the state made the clouds jump); the multipliers below are whole numbers so the end of a cycle meets its start.
    val phase by t.animateFloat(0f, (2 * Math.PI).toFloat(), infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "phase")
    val breathe by t.animateFloat(-1f, 1f, infiniteRepeatable(tween(4500), RepeatMode.Reverse), label = "breathe")
    // One 12 second clock: the bloom (2 s), heartbeat (1.5 s) and wave (1.2 s) repeat a whole number of times in it, so nothing jumps.
    val clock by t.animateFloat(0f, 1f, infiniteRepeatable(tween(12000, easing = LinearEasing)), label = "clock")
    val orb = size
    // Everything below is measured in the design mock-up's pixels (the cloud is 120 wide) and scaled to the real size, so it looks like the mock-up.
    val small = orb < 80.dp
    // The soft edges and the rings reach well past the cloud, so the drawing area is twice its size (centred, not taking room): a smaller one cut the glow off in a square.
    Box(modifier = Modifier.size(orb * 1.5f), contentAlignment = Alignment.Center) {
    Canvas(modifier = Modifier.requiredSize(orb * 2.2f).graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)) {
        val c = center
        val twoPi = 2f * Math.PI.toFloat()
        // The whole cloud swells or beats as one: idle breathes, listening swells with the bloom, thinking has a heartbeat.
        val group = when (state) {
            AssistState.Listening -> 1f + 0.06f * sin(clock * 5f * twoPi)
            AssistState.Thinking -> {
                val x = (clock * 8f) % 1f
                val pts = floatArrayOf(0f, 0.15f, 0.30f, 0.45f, 1f)
                val vals = floatArrayOf(0.94f, 1.14f, 0.98f, 1.08f, 0.94f)
                var i = 0
                while (i < 3 && x > pts[i + 1]) i++
                vals[i] + (vals[i + 1] - vals[i]) * ((x - pts[i]) / (pts[i + 1] - pts[i]))
            }
            AssistState.Speaking -> 1f
            else -> 1f + 0.05f * breathe
        }
        val u = orb.toPx() / 120f * group
        // Idle and thinking drift gently; listening and speaking hold still so the bloom and the wave read clearly.
        val drift = if (state == AssistState.Listening || state == AssistState.Speaking) 0f else 11f * u
        val spread = if (small) 1.5f else 1f
        fun cloud(colour: Color, dx: Float, dy: Float, disc: Float, a: Float, b: Float, wavePhase: Float) {
            val at = Offset(c.x + dx * u * spread + cos(phase * a) * drift, c.y + dy * u * spread + sin(phase * b) * drift)
            var radius = disc * u
            var alpha = 1f
            if (state == AssistState.Speaking) {
                // A wave: each colour swells in turn.
                val w = 0.5f - 0.5f * cos(((clock * 10f + wavePhase) % 1f) * twoPi)
                radius *= 0.85f + 0.4f * w
                alpha = 0.7f + 0.3f * w
            }
            val outer = radius + 26f * u
            if (small) radius *= 0.9f
            drawCircle(
                Brush.radialGradient(
                    0f to colour.copy(alpha = alpha), ((radius - 26f * u).coerceAtLeast(0f) / outer) to colour.copy(alpha = alpha),
                    (radius / outer) to colour.copy(alpha = 0.5f * alpha), 1f to Color.Transparent,
                    center = at, radius = outer,
                ),
                radius = outer, center = at, blendMode = BlendMode.Screen,
            )
        }
        cloud(pal.first, -13f, -1f, 42f, 1f, 2f, 0f)
        cloud(pal.second, 15.5f, -13.5f, 39.5f, 2f, 1f, 0.33f)
        cloud(pal.third, -3f, 15f, 37f, 1f, 1f, 0.66f)
        // Listening: soft rings bloom outwards from the cloud and melt away, one colour after another.
        if (state == AssistState.Listening) {
            listOf(pal.first, pal.second, pal.third).forEachIndexed { i, colour ->
                val x = ((clock * 6f) + i / 3f) % 1f
                val ringR = (35f + 35f * x) * u
                val outer = ringR + 19f * u
                val a = 0.47f * (1f - x) * (0.8f + 0.4f * level.coerceIn(0f, 1f))
                drawCircle(
                    Brush.radialGradient(
                        0f to Color.Transparent, ((ringR - 19f * u).coerceAtLeast(0f) / outer) to Color.Transparent,
                        (ringR / outer) to colour.copy(alpha = a), 1f to Color.Transparent,
                        center = c, radius = outer,
                    ),
                    radius = outer, center = c,
                )
            }
        }
    }
    }
}

/**
 * A round grey button. When [glow] is on, a soft indigo, purple and green glow shines out from behind it (it breathes while it
 * is on). The glow is drawn at the very back (see the Row it sits in: zIndex), so it never covers the typing box, the buttons or the text.
 */
@Composable
private fun RoundIcon(icon: String, label: String, glow: Boolean, onClick: () -> Unit, size: androidx.compose.ui.unit.Dp = 48.dp) {
    val pal = assistPalette()
    val scheme = MaterialTheme.colorScheme
    val t = rememberInfiniteTransition(label = "glow")
    val pulse by t.animateFloat(0.55f, 1f, infiniteRepeatable(tween(2600), RepeatMode.Reverse), label = "pulse")
    val turn by t.animateFloat(0f, 360f, infiniteRepeatable(tween(14000, easing = LinearEasing)), label = "turn")
    // The glow is drawn bigger than the button without taking any room: it spills out around it.
    Box(modifier = Modifier.size(size).then(if (glow) Modifier.zIndex(-1f) else Modifier), contentAlignment = Alignment.Center) {
        if (glow) {
            Canvas(
                modifier = Modifier.requiredSize(size * 1.7f).rotate(turn).graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen),
            ) {
                val r = this.size.minDimension / 2f
                drawCircle(
                    Brush.sweepGradient(listOf(pal.first, pal.second, pal.third, pal.first)),
                    radius = r, alpha = 0.65f * pulse,
                )
                drawCircle(
                    Brush.radialGradient(0.3f to Color.Black, 1f to Color.Transparent, center = center, radius = r),
                    radius = r, blendMode = BlendMode.DstIn,
                )
            }
        }
        Box(
            modifier = Modifier.size(size).clip(CircleShape)
                .background(if (glow) scheme.surfaceVariant else scheme.onSurface.copy(alpha = 0.1f).compositeOver(scheme.surface))
                .clickable(onClickLabel = label) { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            HaIcon(icon, Icons.Filled.Info, scheme.onSurface, size.value.dp / 2)
        }
    }
}

@Composable
private fun Notice(model: AssistModel, onTask: (String) -> Unit) {
    if (model.notice.isNotEmpty()) Text(model.notice, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
    model.notUnderstood?.let { said ->
        val tone = toneColors(Tone.Indigo)
        TileRow(listOf(TileItem("mdi:format-list-checks", "Add \u201C$said\u201D as a task", false) { onTask(said) }), tone, MaterialTheme.colorScheme.onSurface)
    }
}

/** Sentences to try: one calm row that scrolls sideways. */
@Composable
private fun SuggestionRow(model: AssistModel) {
    val scheme = MaterialTheme.colorScheme
    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ASSIST_SUGGESTIONS.forEach { s ->
            Box(
                modifier = Modifier.clip(RoundedCornerShape(18.dp)).background(scheme.surfaceContainer).clickable { model.send(s) }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
            ) { Text(s, style = MaterialTheme.typography.bodyMedium, maxLines = 1) }
        }
    }
}

/**
 * Switches off the window's own open and close animation and dimming (the system would slide it as well as the card does), so the
 * assistant moves only the way [AssistPanel] moves it, the same as every other pop-up.
 */
@Composable
private fun QuietWindow() {
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.SideEffect {
        val window = (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
            ?: generateSequence(view.context) { (it as? android.content.ContextWrapper)?.baseContext }.filterIsInstance<android.app.Activity>().firstOrNull()?.window
        window?.setWindowAnimations(-1)
        window?.setDimAmount(0f)
    }
}

/** The glow along the bottom edge, like Gemini's: stronger while he listens, thinks or speaks. */
@Composable
private fun BottomGlow(active: Boolean, level: Float, modifier: Modifier = Modifier) {
    val pal = assistPalette()
    val t = rememberInfiniteTransition(label = "band")
    val breathe by t.animateFloat(0.7f, 1f, infiniteRepeatable(tween(6000), RepeatMode.Reverse), label = "b")
    val on by animateFloatAsState(if (active) 1f else 0.3f, tween(900), label = "on")
    Canvas(modifier = modifier.fillMaxWidth().height(200.dp)) {
        val a = (0.42f * breathe + 0.2f * level) * on
        listOf(0.2f to pal.first, 0.5f to pal.second, 0.82f to pal.third).forEach { (fx, colour) ->
            val c = Offset(size.width * fx, size.height + 24.dp.toPx())
            val r = size.width * 0.55f
            drawCircle(Brush.radialGradient(listOf(colour.copy(alpha = a), Color.Transparent), center = c, radius = r), radius = r, center = c)
        }
    }
}

/**
 * The round button at the bottom right: Jarvis's logo on every page of the app, his microphone once he is open. It is the same size
 * and in the same place in both, just above the bottom bar.
 */
@Composable
fun JarvisRound(label: String, container: Color, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.size(58.dp).shadow(6.dp, CircleShape).clip(CircleShape).background(container).clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun FullAssist(model: AssistModel, onClose: () -> Unit, onTask: (String) -> Unit, startTyping: Boolean, embedded: Boolean) {
    val pal = assistPalette()
    val scheme = MaterialTheme.colorScheme
    var typed by remember { mutableStateOf("") }
    var typing by remember { mutableStateOf(startTyping) }
    val chatting = model.messages.isNotEmpty()
    val active = model.state == AssistState.Listening || model.state == AssistState.Thinking || model.state == AssistState.Speaking
    val focus = remember { FocusRequester() }
    // The same layout as the app: the bar along the bottom (here with Jarvis's own buttons) and the round button above it at the right.
    Scaffold(
        modifier = Modifier.fillMaxSize().imePadding(),
        containerColor = scheme.background,
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = false, onClick = onClose,
                    icon = { HaIcon("mdi:close", Icons.Filled.Info, scheme.onSurfaceVariant, 24.dp) },
                    label = { Text("Close", fontSize = 11.sp, maxLines = 1) },
                )
                NavigationBarItem(
                    selected = typing, onClick = { typing = true; runCatching { focus.requestFocus() } },
                    icon = { HaIcon("mdi:keyboard-outline", Icons.Filled.Info, if (typing) scheme.onSecondaryContainer else scheme.onSurfaceVariant, 24.dp) },
                    label = { Text("Type", fontSize = 11.sp, maxLines = 1) },
                )
            }
        },
        // Inside the app the round button is the app's own (the same one as on every page, above him); alone, he brings his.
        floatingActionButton = {
            if (!embedded) JarvisRound("Talk", pal.third, { model.listen() }) {
                HaIcon(if (model.state == AssistState.Listening) "mdi:stop" else "mdi:microphone", Icons.Filled.Info, scheme.background, 28.dp)
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            BottomGlow(active, model.level, Modifier.align(Alignment.BottomCenter))
            Column(modifier = Modifier.fillMaxSize()) {
                Text(
                    model.name.ifBlank { "Assistant" }, style = MaterialTheme.typography.titleMedium, color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                )
                if (chatting) {
                    // The larger Jarvis icon stays at the top; the conversation starts below it.
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        AssistOrb(model.state, model.level, 96.dp)
                        Spacer(Modifier.height(8.dp))
                        Text(assistStatus(model.state), style = MaterialTheme.typography.labelLarge, color = pal.third)
                    }
                }
                if (!chatting) {
                    Column(
                        modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                    ) {
                        AssistOrb(model.state, model.level, 170.dp)
                        Spacer(Modifier.height(24.dp))
                        Text(assistStatus(model.state), style = MaterialTheme.typography.labelLarge, color = pal.third)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            model.partial.ifEmpty { if (model.wakeWord != null) "Say \u201C${wakePhrase(model.wakeWord)}\u201D or tap the microphone" else "Ask or tell the house" },
                            style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center,
                            color = if (model.partial.isEmpty()) scheme.onSurfaceVariant else scheme.onSurface,
                        )
                        Spacer(Modifier.height(16.dp))
                        Notice(model, onTask)
                    }
                } else {
                    val list = rememberLazyListState()
                    LaunchedEffect(model.messages.size, model.partial, model.state) { list.animateScrollToItem(maxOf(0, model.messages.size)) }
                    LazyColumn(state = list, modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        item { Spacer(Modifier.height(4.dp)) }
                        items(model.messages.size) { i -> ChatLine(model.messages[i]) }
                        item {
                            if (model.partial.isNotEmpty()) ChatLine(AssistMessage(true, model.partial), faded = true)
                            if (model.state == AssistState.Thinking) ThinkingDots()
                            Notice(model, onTask)
                        }
                    }
                }
                // Room is left at the right for the round button, which floats over this row.
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (!chatting && !typing) Box(Modifier.padding(end = 74.dp)) { SuggestionRow(model) }
                    if (chatting || typing) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = typed, onValueChange = { typed = it }, singleLine = true, modifier = Modifier.weight(1f).focusRequester(focus),
                                placeholder = { Text("Ask or tell the house") },
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(onSend = { model.send(typed); typed = "" }),
                                shape = RoundedCornerShape(28.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = scheme.surfaceContainer, unfocusedContainerColor = scheme.surfaceContainer,
                                    focusedBorderColor = scheme.outline, unfocusedBorderColor = scheme.outlineVariant,
                                ),
                            )
                            Spacer(Modifier.size(58.dp))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Three small grey dots while the house works out an answer: one soft indigo, purple and green glow behind all of them (a small
 * version of the one behind the microphone), and each dot moving in its own time, so it feels alive without being busy.
 */
@Composable
private fun ThinkingDots() {
    val pal = assistPalette()
    val t = rememberInfiniteTransition(label = "dots")
    val grey = Color(0xFF767F7B)
    val glow by t.animateFloat(0.7f, 1f, infiniteRepeatable(tween(2400), RepeatMode.Reverse), label = "dotGlow")
    Row(
        modifier = Modifier.padding(vertical = 6.dp)
            .drawBehind {
                // Drawn behind the Row's own centre, so the glow is exactly behind the dots (and spills out around them).
                val r = 17.dp.toPx()
                listOf(-8 to pal.first, 0 to pal.second, 8 to pal.third).forEach { (dx, colour) ->
                    val at = Offset(center.x + dx.dp.toPx(), center.y)
                    drawCircle(Brush.radialGradient(listOf(colour.copy(alpha = 0.34f * glow), Color.Transparent), center = at, radius = r), radius = r, center = at)
                }
            }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(1050 to 0, 1450 to 180, 1250 to 360).forEachIndexed { i, (ms, delay) ->
            val a by t.animateFloat(0.55f, 1f, infiniteRepeatable(tween(ms, delayMillis = delay), RepeatMode.Reverse), label = "dotA$i")
            val sc by t.animateFloat(0.75f, 1.2f, infiniteRepeatable(tween(ms, delayMillis = delay), RepeatMode.Reverse), label = "dotS$i")
            val y by t.animateFloat(2f, -3f, infiniteRepeatable(tween(ms + 200, delayMillis = delay), RepeatMode.Reverse), label = "dotY$i")
            Box(modifier = Modifier.size(6.dp).graphicsLayer { translationY = y; scaleX = sc; scaleY = sc; alpha = a }.clip(CircleShape).background(grey))
        }
    }
}

/** One line of the conversation: what you said in a soft gradient pill, and what Jarvis said as plain words. */
@Composable
private fun ChatLine(message: AssistMessage, faded: Boolean = false) {
    val pal = assistPalette()
    val scheme = MaterialTheme.colorScheme
    if (message.fromUser) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Box(
                modifier = Modifier.widthIn(max = 300.dp)
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 6.dp))
                    .background(Brush.horizontalGradient(listOf(pal.first.copy(alpha = 0.55f), pal.second.copy(alpha = 0.55f))))
                    .padding(horizontal = 16.dp, vertical = 11.dp),
            ) {
                Text(message.text, style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = if (faded) 0.7f else 1f))
            }
        }
    } else {
        JarvisBubble(message.text)
    }
}

/** Jarvis's words in a speech bubble: the tail corner is bottom-left, the mirror of the user's gradient bubble. */
@Composable
private fun JarvisBubble(text: String) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier.widthIn(max = 320.dp)
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 6.dp, bottomEnd = 20.dp))
            .background(scheme.surfaceContainerHigh)
            .padding(horizontal = 16.dp, vertical = 11.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface)
    }
}

/**
 * The introduction, said out loud as well as shown. The first time ever the assistant introduces itself and what it can do;
 * after an update it just says what is new.
 */
@Composable
fun AssistIntro(kind: IntroKind, features: List<AssistFeature>, person: String, assistant: String, wakeWord: String?, model: AssistModel, onTry: () -> Unit, onSkip: () -> Unit, wakeOn: Boolean = true, onEnableWake: () -> Unit = {}) {
    val pal = assistPalette()
    val scheme = MaterialTheme.colorScheme
    val tone = toneColors(Tone.Indigo)
    // Say it once the name is known (it arrives from the house a moment after opening), or after a short wait if the house has none.
    LaunchedEffect(kind) {
        kotlinx.coroutines.withTimeoutOrNull(2_500) { androidx.compose.runtime.snapshotFlow { model.wakeWord }.first { it != null } }
        model.speak(introSpeech(kind, person, model.name, features))
    }
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides scheme.onSurface) {
    Column(modifier = Modifier.fillMaxSize().background(scheme.background).statusBarsPadding().navigationBarsPadding()) {
        Box(modifier = Modifier.fillMaxWidth().height(4.dp).background(pal.across))
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AssistOrb(AssistState.Speaking, 0.4f, 104.dp)
            val who = assistant.ifBlank { "your house assistant" }
            val first = person.trim().substringBefore(' ')
            if (kind == IntroKind.First) {
                Text(if (first.isNotBlank()) "Hi $first, I'm $who" else "Hi, I'm $who", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Text(
                    "I can do anything Home Assistant can: lights, heating, cleaning, the alarm, your lists and your own automations. Just ask.",
                    style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center,
                )
            } else {
                Text("What's new", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Text("$who, with the latest.", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
            if (wakeWord != null) {
                Text("Say \u201C${wakePhrase(wakeWord)}\u201D any time the app is open, or tap the microphone.", style = MaterialTheme.typography.bodyMedium, color = pal.third, textAlign = TextAlign.Center)
            }
            Text(if (kind == IntroKind.First) "What I can do" else "New in this version", style = MaterialTheme.typography.labelLarge, color = pal.third, modifier = Modifier.align(Alignment.Start))
            features.forEach { f ->
                EntityCard(tone.container, tone.onContainer) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        HaIcon(f.icon, Icons.Filled.Info, tone.accent, 28.dp)
                        Column {
                            Text(f.title, style = MaterialTheme.typography.titleMedium)
                            val line = if (f.example.isNotBlank()) "Say: ${f.example}" else f.detail
                            if (line.isNotBlank()) Text(line, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!wakeOn && wakeWord != null) {
                TileRow(listOf(TileItem("mdi:microphone-message", "Turn on \u201C${wakePhrase(wakeWord)}\u201D", false) { onEnableWake() }), tone, scheme.onSurface)
            }
            TileRow(
                listOf(TileItem("mdi:close", "Got it", false) { onSkip() }, TileItem("mdi:microphone", "Try it", true) { onTry() }),
                tone, scheme.onSurface,
            )
        }
    }
    }
}
