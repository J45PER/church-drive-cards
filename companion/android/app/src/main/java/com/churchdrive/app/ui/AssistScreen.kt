package com.churchdrive.app.ui

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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
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

/** The three colours of the assistant, a gradient like Siri's or Gemini's: Church Drive's indigo, purple and green. */
object AssistColours {
    val Indigo = Color(0xFF5C6BC0)
    val Purple = Color(0xFFAB47BC)
    val Green = Color(0xFF4FD08A)
    val all = listOf(Indigo, Purple, Green)
    val across: Brush get() = Brush.horizontalGradient(listOf(Indigo, Purple, Green))
    val down: Brush get() = Brush.verticalGradient(listOf(Indigo, Purple, Green))
}

/**
 * The assistant, drawn two ways that share everything: a bottom sheet over the page ([full] false: swipe it up for the
 * whole screen, down to close), and the whole screen (the orb to start with, then the conversation).
 */
@Composable
fun AssistPanel(
    model: AssistModel,
    full: Boolean,
    onFull: (Boolean) -> Unit,
    onClose: () -> Unit,
    onTask: (String) -> Unit,
    /** The introduction to show first (and say out loud), or null. */
    intro: IntroKind? = null,
    person: String = "",
    onIntroDone: () -> Unit = {},
    /** "Try it" in the introduction: done with it, and start listening (asking for the microphone first if needed). */
    onIntroTry: () -> Unit = { onIntroDone(); model.listen() },
    /** Whether "listen for the wake word" is on, and a way to turn it on (the introduction offers it). */
    wakeOn: Boolean = true,
    onEnableWake: () -> Unit = {},
) {
    // A dialog doesn't set the text colour, so it would be dark on dark.
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        if (intro != null) {
            AssistIntro(
                kind = intro, person = person, assistant = model.name, wakeWord = model.wakeWord, model = model,
                onTry = onIntroTry, onSkip = onIntroDone, wakeOn = wakeOn, onEnableWake = onEnableWake,
            )
        } else if (full) FullAssist(model, onFull, onClose, onTask) else SheetAssist(model, onFull, onClose, onTask)
    }
}

/**
 * The cloud: three soft clouds (indigo, purple, green) that drift slowly round each other and blend where they meet. Calm when it
 * is waiting, a little livelier and a gentle swell with the voice when listening, thinking or speaking.
 */
@Composable
fun AssistOrb(state: AssistState, level: Float, size: androidx.compose.ui.unit.Dp) {
    val t = rememberInfiniteTransition(label = "orb")
    val slow = state == AssistState.Idle || state == AssistState.Waiting
    val phase by t.animateFloat(0f, (2 * Math.PI).toFloat(), infiniteRepeatable(tween(if (slow) 22000 else 11000, easing = LinearEasing)), label = "phase")
    val breathe by t.animateFloat(0.96f, 1.04f, infiniteRepeatable(tween(3600), RepeatMode.Reverse), label = "breathe")
    // One 12 second clock: the bloom (2.4 s), heartbeat and wave (1.2 s) all repeat a whole number of times in it, so nothing jumps.
    val clock by t.animateFloat(0f, 1f, infiniteRepeatable(tween(12000, easing = LinearEasing)), label = "clock")
    val energy = when (state) {
        AssistState.Listening -> 0.3f + level * 0.5f
        AssistState.Speaking -> 0.5f
        AssistState.Thinking -> 0.4f
        else -> 0.1f
    }
    val orb = size
    Canvas(modifier = Modifier.size(orb * 1.5f).graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)) {
        val c = center
        // Thinking beats like a heart: two quick swells, then a rest.
        val beat = if (state == AssistState.Thinking) {
            val x = (clock * 10f) % 1f
            1f + 0.12f * kotlin.math.exp(-((x - 0.15f) * (x - 0.15f)) / 0.004f) + 0.07f * kotlin.math.exp(-((x - 0.45f) * (x - 0.45f)) / 0.006f)
        } else 1f
        val r = orb.toPx() * 0.5f * breathe * beat * (1f + energy * 0.06f)
        // A wide, faint halo.
        drawCircle(
            Brush.radialGradient(listOf(AssistColours.Purple.copy(alpha = 0.12f + 0.14f * energy), Color.Transparent), center = c, radius = r * 1.5f),
            radius = r * 1.5f, center = c,
        )
        // Small clouds (the header) spread their colours further apart and skip the bright centre, so they don't wash out to white.
        val small = orb < 80.dp
        val wander = r * ((if (small) 0.26f else 0.12f) + 0.12f * energy)
        fun cloud(colour: Color, a: Float, b: Float, scale: Float, wavePhase: Float) {
            val at = Offset(c.x + cos(phase * a) * wander, c.y + sin(phase * b) * wander)
            // Speaking: each colour swells in turn, a wave passing through the cloud.
            val wave = if (state == AssistState.Speaking) 1f + 0.2f * sin(((clock * 10f + wavePhase) % 1f) * 2f * Math.PI.toFloat()) else 1f
            val radius = r * scale * wave
            drawCircle(
                Brush.radialGradient(listOf(colour.copy(alpha = if (small) 0.8f else 0.9f), colour.copy(alpha = if (small) 0.35f else 0.45f), Color.Transparent), center = at, radius = radius),
                radius = radius, center = at, blendMode = BlendMode.Screen,
            )
        }
        val k = if (small) 0.85f else 1.0f
        cloud(AssistColours.Indigo, 1f, 1.3f, 1.0f * k, 0f)
        cloud(AssistColours.Purple, 1.2f, 0.8f, 0.95f * k, 0.33f)
        cloud(AssistColours.Green, 0.9f, 1.1f, 0.9f * k, 0.66f)
        if (!small) {
            drawCircle(
                Brush.radialGradient(listOf(Color.White.copy(alpha = 0.08f + 0.1f * energy), Color.Transparent), center = c, radius = r * 0.5f),
                radius = r * 0.5f, center = c, blendMode = BlendMode.Screen,
            )
        }
        // Listening: soft rings bloom outwards and fade, one colour after another, stronger with your voice.
        if (state == AssistState.Listening) {
            listOf(AssistColours.Indigo, AssistColours.Purple, AssistColours.Green).forEachIndexed { i, colour ->
                val x = ((clock * 5f) + i / 3f) % 1f
                val ringR = r * (0.6f + 0.75f * x)
                val a = (1f - x) * (0.35f + 0.5f * level.coerceIn(0f, 1f))
                drawCircle(colour.copy(alpha = a), radius = ringR, center = c, style = Stroke(width = r * 0.22f), blendMode = BlendMode.Screen)
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
                    Brush.sweepGradient(listOf(AssistColours.Indigo, AssistColours.Purple, AssistColours.Green, AssistColours.Indigo)),
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

/** A small cloud with his name and what he is doing. */
@Composable
private fun JarvisHeader(model: AssistModel, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(modifier = Modifier.size(46.dp), contentAlignment = Alignment.Center) { AssistOrb(model.state, model.level, 34.dp) }
        Column {
            Text(model.name.ifBlank { "Assistant" }, style = MaterialTheme.typography.titleMedium)
            Text(assistStatus(model.state), style = MaterialTheme.typography.bodySmall, color = if (model.state == AssistState.Idle) scheme.onSurfaceVariant else AssistColours.Green)
        }
    }
}

@Composable
private fun SheetAssist(model: AssistModel, onFull: (Boolean) -> Unit, onClose: () -> Unit, onTask: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var drag by remember { mutableFloatStateOf(0f) }
    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.34f)).clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { onClose() }) {
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .background(scheme.surface)
                .clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { drag = 0f },
                        onDragEnd = { if (drag < -60f) onFull(true) else if (drag > 140f) onClose() },
                        onVerticalDrag = { _, dy -> drag += dy },
                    )
                }
                .navigationBarsPadding().padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(modifier = Modifier.width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(scheme.onSurface.copy(alpha = 0.2f)).align(Alignment.CenterHorizontally))
            Row(verticalAlignment = Alignment.CenterVertically) {
                JarvisHeader(model, Modifier.weight(1f))
                RoundIcon("mdi:chevron-up", "Full screen", false, { onFull(true) }, size = 40.dp)
            }
            val last = model.messages.lastOrNull()
            val heard = model.partial.ifEmpty { model.messages.lastOrNull { it.fromUser }?.text.orEmpty() }
            if (heard.isNotEmpty()) Text(heard, style = MaterialTheme.typography.headlineSmall)
            if (model.partial.isEmpty() && last != null && !last.fromUser) {
                JarvisBubble(last.text)
            }
            if (model.state == AssistState.Thinking) ThinkingDots()
            Notice(model, onTask)
            if (model.state != AssistState.Listening) SuggestionRow(model)
            // The controls sit at the very back, so the microphone's glow is behind the chips above them.
            Row(
                modifier = Modifier.fillMaxWidth().zIndex(-1f).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                RoundIcon("mdi:keyboard-outline", "Type instead", false, { onFull(true) })
                RoundIcon(
                    if (model.state == AssistState.Listening) "mdi:stop" else "mdi:microphone", "Talk",
                    model.state != AssistState.Idle && model.state != AssistState.Waiting, { model.listen() }, size = 68.dp,
                )
                RoundIcon("mdi:close", "Close", false, { onClose() })
            }
        }
    }
}

/** The glow along the bottom edge, like Gemini's: stronger while he listens, thinks or speaks. */
@Composable
private fun BottomGlow(active: Boolean, level: Float, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "band")
    val breathe by t.animateFloat(0.7f, 1f, infiniteRepeatable(tween(6000), RepeatMode.Reverse), label = "b")
    val on by animateFloatAsState(if (active) 1f else 0.3f, tween(900), label = "on")
    Canvas(modifier = modifier.fillMaxWidth().height(200.dp)) {
        val a = (0.42f * breathe + 0.2f * level) * on
        listOf(0.2f to AssistColours.Indigo, 0.5f to AssistColours.Purple, 0.82f to AssistColours.Green).forEach { (fx, colour) ->
            val c = Offset(size.width * fx, size.height + 24.dp.toPx())
            val r = size.width * 0.55f
            drawCircle(Brush.radialGradient(listOf(colour.copy(alpha = a), Color.Transparent), center = c, radius = r), radius = r, center = c)
        }
    }
}

@Composable
private fun FullAssist(model: AssistModel, onFull: (Boolean) -> Unit, onClose: () -> Unit, onTask: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var typed by remember { mutableStateOf("") }
    var typing by remember { mutableStateOf(false) }
    val chatting = model.messages.isNotEmpty()
    val active = model.state == AssistState.Listening || model.state == AssistState.Thinking || model.state == AssistState.Speaking
    Box(modifier = Modifier.fillMaxSize().background(scheme.background)) {
        BottomGlow(active, model.level, Modifier.align(Alignment.BottomCenter))
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundIcon("mdi:close", "Close", false, { onClose() }, size = 44.dp)
                run {
                    Text(model.name.ifBlank { "Assistant" }, style = MaterialTheme.typography.titleMedium, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                }
                RoundIcon("mdi:chevron-down", "Smaller", false, { onFull(false) }, size = 44.dp)
            }
            if (chatting) {
                // The larger Jarvis icon stays at the top; the conversation starts below it.
                Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    AssistOrb(model.state, model.level, 96.dp)
                    Spacer(Modifier.height(8.dp))
                    Text(assistStatus(model.state), style = MaterialTheme.typography.labelLarge, color = AssistColours.Green)
                }
            }
            if (!chatting) {
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    AssistOrb(model.state, model.level, 170.dp)
                    Spacer(Modifier.height(24.dp))
                    Text(assistStatus(model.state), style = MaterialTheme.typography.labelLarge, color = AssistColours.Green)
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
            Column(modifier = Modifier.padding(start = 16.dp, end = 20.dp, top = 8.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (!chatting && !typing) SuggestionRow(model)
                if (chatting || typing) {
                    // Type or speak: the microphone's glow sits at the very back, behind the typing box.
                    Row(modifier = Modifier.zIndex(-1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = typed, onValueChange = { typed = it }, singleLine = true, modifier = Modifier.weight(1f).zIndex(1f),
                            placeholder = { Text("Ask or tell the house") },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { model.send(typed); typed = "" }),
                            shape = RoundedCornerShape(28.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = scheme.surfaceContainer, unfocusedContainerColor = scheme.surfaceContainer,
                                focusedBorderColor = scheme.outline, unfocusedBorderColor = scheme.outlineVariant,
                            ),
                        )
                        RoundIcon(
                            if (model.state == AssistState.Listening) "mdi:stop" else "mdi:microphone", "Talk",
                            model.state != AssistState.Idle && model.state != AssistState.Waiting, { model.listen() }, size = 56.dp,
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().zIndex(-1f).padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        RoundIcon("mdi:keyboard-outline", "Type instead", false, { typing = true }, size = 52.dp)
                        RoundIcon(
                            if (model.state == AssistState.Listening) "mdi:stop" else "mdi:microphone", "Talk",
                            model.state != AssistState.Idle && model.state != AssistState.Waiting, { model.listen() }, size = 76.dp,
                        )
                        Spacer(Modifier.size(52.dp))
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
    val t = rememberInfiniteTransition(label = "dots")
    val grey = Color(0xFF767F7B)
    val glow by t.animateFloat(0.7f, 1f, infiniteRepeatable(tween(2400), RepeatMode.Reverse), label = "dotGlow")
    Row(
        modifier = Modifier.padding(vertical = 6.dp)
            .drawBehind {
                // Drawn behind the Row's own centre, so the glow is exactly behind the dots (and spills out around them).
                val r = 17.dp.toPx()
                listOf(-8 to AssistColours.Indigo, 0 to AssistColours.Purple, 8 to AssistColours.Green).forEach { (dx, colour) ->
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
    val scheme = MaterialTheme.colorScheme
    if (message.fromUser) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Box(
                modifier = Modifier.widthIn(max = 300.dp)
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 6.dp))
                    .background(Brush.horizontalGradient(listOf(AssistColours.Indigo.copy(alpha = 0.55f), AssistColours.Purple.copy(alpha = 0.55f))))
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
fun AssistIntro(kind: IntroKind, person: String, assistant: String, wakeWord: String?, model: AssistModel, onTry: () -> Unit, onSkip: () -> Unit, wakeOn: Boolean = true, onEnableWake: () -> Unit = {}) {
    val scheme = MaterialTheme.colorScheme
    val tone = toneColors(Tone.Indigo)
    // Say it once the name is known (it arrives from the house a moment after opening), or after a short wait if the house has none.
    LaunchedEffect(kind) {
        kotlinx.coroutines.withTimeoutOrNull(2_500) { androidx.compose.runtime.snapshotFlow { model.wakeWord }.first { it != null } }
        model.speak(introSpeech(kind, person, model.name, ASSIST_FEATURES))
    }
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides scheme.onSurface) {
    Column(modifier = Modifier.fillMaxSize().background(scheme.background).statusBarsPadding().navigationBarsPadding()) {
        Box(modifier = Modifier.fillMaxWidth().height(4.dp).background(AssistColours.across))
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
                Text("Say \u201C${wakePhrase(wakeWord)}\u201D any time the app is open, or tap the microphone.", style = MaterialTheme.typography.bodyMedium, color = AssistColours.Green, textAlign = TextAlign.Center)
            }
            Text(if (kind == IntroKind.First) "What I can do" else "New in this version", style = MaterialTheme.typography.labelLarge, color = AssistColours.Green, modifier = Modifier.align(Alignment.Start))
            ASSIST_FEATURES.forEach { f ->
                EntityCard(tone.container, tone.onContainer) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        HaIcon(f.icon, Icons.Filled.Info, tone.accent, 28.dp)
                        Column {
                            Text(f.title, style = MaterialTheme.typography.titleMedium)
                            Text("Say: ${f.example}", style = MaterialTheme.typography.bodyMedium)
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
