package com.churchdrive.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

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
) {
    // A dialog doesn't set the text colour, so it would be dark on dark.
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        if (full) FullAssist(model, onFull, onClose, onTask) else SheetAssist(model, onFull, onClose, onTask)
    }
}

/**
 * The glow of colour, like Siri's or Gemini's: three soft clouds (indigo, purple, green) drifting round each other and blending
 * where they meet, with a bright centre. It stirs faster and swells with the voice when listening or speaking.
 */
@Composable
fun AssistOrb(state: AssistState, level: Float, size: androidx.compose.ui.unit.Dp) {
    val t = rememberInfiniteTransition(label = "orb")
    val slow = state == AssistState.Idle || state == AssistState.Waiting
    val phase by t.animateFloat(0f, (2 * Math.PI).toFloat(), infiniteRepeatable(tween(if (slow) 11000 else 4800, easing = LinearEasing)), label = "phase")
    val breathe by t.animateFloat(0.94f, 1.06f, infiniteRepeatable(tween(2200), RepeatMode.Reverse), label = "breathe")
    val energy = when (state) {
        AssistState.Listening -> 0.35f + level * 0.65f
        AssistState.Speaking -> 0.7f
        AssistState.Thinking -> 0.55f
        else -> 0.15f
    }
    val orb = size
    Canvas(modifier = Modifier.size(orb * 1.5f).graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)) {
        val c = center
        val r = orb.toPx() * 0.5f * breathe * (1f + energy * 0.12f)
        // A wide, faint halo.
        drawCircle(
            Brush.radialGradient(listOf(AssistColours.Purple.copy(alpha = 0.16f + 0.2f * energy), Color.Transparent), center = c, radius = r * 1.5f),
            radius = r * 1.5f, center = c,
        )
        val wander = r * (0.16f + 0.26f * energy)
        fun cloud(colour: Color, a: Float, b: Float, scale: Float) {
            val at = Offset(c.x + cos(phase * a) * wander, c.y + sin(phase * b) * wander)
            val radius = r * scale
            drawCircle(
                Brush.radialGradient(listOf(colour.copy(alpha = 0.95f), colour.copy(alpha = 0.5f), Color.Transparent), center = at, radius = radius),
                radius = radius, center = at, blendMode = BlendMode.Plus,
            )
        }
        cloud(AssistColours.Indigo, 1f, 1.3f, 1.0f)
        cloud(AssistColours.Purple, 1.2f + 0.0f, 0.8f, 0.95f)
        cloud(AssistColours.Green, 0.9f, 1.1f, 0.9f)
        // A bright, soft centre.
        drawCircle(
            Brush.radialGradient(listOf(Color.White.copy(alpha = 0.35f + 0.4f * energy), Color.Transparent), center = c, radius = r * 0.55f),
            radius = r * 0.55f, center = c, blendMode = BlendMode.Plus,
        )
    }
}

/** Bars that move with the voice, in the gradient. */
@Composable
private fun Waveform(state: AssistState, level: Float) {
    val t = rememberInfiniteTransition(label = "wave")
    Row(modifier = Modifier.height(44.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(13) { i ->
            val wave by t.animateFloat(0.2f, 1f, infiniteRepeatable(tween(600 + (i % 5) * 110), RepeatMode.Reverse), label = "bar$i")
            val amount = when (state) {
                AssistState.Listening -> 0.25f + level * wave * 0.75f
                AssistState.Thinking, AssistState.Speaking -> 0.2f + wave * 0.5f
                AssistState.Idle, AssistState.Waiting -> 0.12f
            }
            Box(modifier = Modifier.width(4.dp).height((44 * amount).dp).clip(RoundedCornerShape(2.dp)).background(AssistColours.down))
        }
    }
}

/**
 * A round grey button. When [glow] is on, a soft indigo, purple and green glow shines out from behind it (it breathes while it
 * is on), so the button stays calm and quiet and the colour says what is happening.
 */
@Composable
private fun RoundIcon(icon: String, label: String, glow: Boolean, onClick: () -> Unit, size: androidx.compose.ui.unit.Dp = 48.dp) {
    val scheme = MaterialTheme.colorScheme
    val t = rememberInfiniteTransition(label = "glow")
    val pulse by t.animateFloat(0.55f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "pulse")
    val turn by t.animateFloat(0f, 360f, infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "turn")
    // The glow is drawn bigger than the button without taking any room: it spills out around it.
    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        if (glow) {
            Canvas(
                modifier = Modifier.requiredSize(size * 1.9f).rotate(turn).graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen),
            ) {
                val r = this.size.minDimension / 2f
                drawCircle(
                    Brush.sweepGradient(listOf(AssistColours.Indigo, AssistColours.Purple, AssistColours.Green, AssistColours.Indigo)),
                    radius = r, alpha = 0.7f * pulse,
                )
                // Fade it out towards the edge (keeping only the alpha of this gradient) so it is a glow, not a disc.
                drawCircle(
                    Brush.radialGradient(0.3f to Color.Black, 1f to Color.Transparent, center = center, radius = r),
                    radius = r, blendMode = BlendMode.DstIn,
                )
            }
        }
        Box(
            modifier = Modifier.size(size).clip(CircleShape)
                .background(scheme.onSurface.copy(alpha = if (glow) 0.18f else 0.1f))
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
        TileRow(listOf(TileItem("mdi:format-list-checks", "Add “$said” as a task", false) { onTask(said) }), tone, MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun Suggestions(model: AssistModel) {
    val tone = toneColors(Tone.Indigo)
    TileRow(
        ASSIST_SUGGESTIONS.map { s -> TileItem("mdi:chat-processing-outline", s, false) { model.send(s) } },
        tone, MaterialTheme.colorScheme.onSurface, perRow = 2,
    )
}

@Composable
private fun SheetAssist(model: AssistModel, onFull: (Boolean) -> Unit, onClose: () -> Unit, onTask: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var drag by remember { mutableFloatStateOf(0f) }
    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)).clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { onClose() }) {
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
                },
        ) {
            Box(modifier = Modifier.fillMaxWidth().height(4.dp).background(AssistColours.across))
            Column(
                modifier = Modifier.navigationBarsPadding().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(modifier = Modifier.width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(scheme.onSurface.copy(alpha = 0.25f)).align(Alignment.CenterHorizontally))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    RoundIcon("mdi:microphone", "Talk", model.state == AssistState.Listening, { model.listen() })
                    Column(modifier = Modifier.weight(1f)) {
                        Text(assistStatus(model.state), style = MaterialTheme.typography.titleMedium)
                        Text("${model.name.ifBlank { "Assistant" }} \u00B7 swipe up for the full screen", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    }
                    RoundIcon("mdi:chevron-up", "Full screen", false, { onFull(true) })
                }
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Waveform(model.state, model.level) }
                val last = model.messages.lastOrNull()
                val heard = model.partial.ifEmpty { model.messages.lastOrNull { it.fromUser }?.text.orEmpty() }
                if (heard.isNotEmpty()) Text(heard, style = MaterialTheme.typography.titleLarge)
                if (model.partial.isEmpty() && last != null && !last.fromUser) {
                    EntityCard(toneColors(Tone.Indigo).container, toneColors(Tone.Indigo).onContainer) {
                        Text(last.text, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Notice(model, onTask)
                if (model.messages.isEmpty() && model.partial.isEmpty()) Suggestions(model)
            }
        }
    }
}

@Composable
private fun FullAssist(model: AssistModel, onFull: (Boolean) -> Unit, onClose: () -> Unit, onTask: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var typed by remember { mutableStateOf("") }
    val chatting = model.messages.isNotEmpty()
    Column(modifier = Modifier.fillMaxSize().background(scheme.background).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Box(modifier = Modifier.fillMaxWidth().height(4.dp).background(AssistColours.across))
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RoundIcon("mdi:close", "Close", false, { onClose() })
            Text(model.name.ifBlank { "Assistant" }, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            RoundIcon("mdi:chevron-down", "Smaller", false, { onFull(false) })
        }
        if (!chatting) {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                AssistOrb(model.state, model.level, 168.dp)
                Spacer(Modifier.height(28.dp))
                Text(assistStatus(model.state), style = MaterialTheme.typography.labelLarge, color = AssistColours.Green)
                Spacer(Modifier.height(8.dp))
                Text(
                    model.partial.ifEmpty { if (model.wakeWord != null) "Say \u201C${wakePhrase(model.wakeWord)}\u201D or tap the microphone" else "Ask or tell the house" }, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center,
                    color = if (model.partial.isEmpty()) scheme.onSurfaceVariant else scheme.onSurface,
                )
                Spacer(Modifier.height(16.dp))
                Notice(model, onTask)
            }
        } else {
            val list = rememberLazyListState()
            LaunchedEffect(model.messages.size, model.partial) { if (model.messages.isNotEmpty()) list.animateScrollToItem(model.messages.size) }
            LazyColumn(state = list, modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { Spacer(Modifier.height(4.dp)) }
                items(model.messages.size) { i -> Bubble(model.messages[i]) }
                item {
                    if (model.partial.isNotEmpty()) Bubble(AssistMessage(true, model.partial), faded = true)
                    if (model.state == AssistState.Thinking) Text("…", style = MaterialTheme.typography.headlineMedium, color = AssistColours.Purple)
                    Notice(model, onTask)
                }
            }
        }
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!chatting) Suggestions(model) else if (model.state == AssistState.Listening) Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Waveform(model.state, model.level) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = typed, onValueChange = { typed = it }, singleLine = true, modifier = Modifier.weight(1f),
                    placeholder = { Text("Ask or tell the house") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { model.send(typed); typed = "" }),
                    shape = RoundedCornerShape(28.dp),
                )
                RoundIcon(
                    if (model.state == AssistState.Listening) "mdi:stop" else "mdi:microphone", "Talk",
                    model.state == AssistState.Listening || model.state == AssistState.Speaking || model.state == AssistState.Thinking,
                    { model.listen() }, size = 56.dp,
                )
            }
        }
    }
}

@Composable
private fun Bubble(message: AssistMessage, faded: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    val mine = message.fromUser
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Box(
            modifier = Modifier.fillMaxWidth(0.82f)
                .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = if (mine) 22.dp else 6.dp, bottomEnd = if (mine) 6.dp else 22.dp))
                .background(if (mine) Brush.horizontalGradient(listOf(AssistColours.Indigo, AssistColours.Purple)) else Brush.horizontalGradient(listOf(scheme.surfaceVariant, scheme.surfaceVariant)))
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(message.text, style = MaterialTheme.typography.bodyLarge, color = if (mine) Color.White.copy(alpha = if (faded) 0.7f else 1f) else scheme.onSurfaceVariant)
        }
    }
}

/** What the first time after an update shows: the assistant introduces itself and what is new, with a sentence to try for each. */
@Composable
fun AssistIntro(name: String, assistant: String, wakeWord: String?, onTry: () -> Unit, onSkip: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val tone = toneColors(Tone.Indigo)
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides scheme.onSurface) {
    Column(modifier = Modifier.fillMaxSize().background(scheme.background).statusBarsPadding().navigationBarsPadding()) {
        Box(modifier = Modifier.fillMaxWidth().height(4.dp).background(AssistColours.across))
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AssistOrb(AssistState.Idle, 0f, 104.dp)
            val who = assistant.ifBlank { "your house assistant" }
            Text(if (name.isNotBlank()) "Hi $name, I'm $who" else "Hi, I'm $who", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            if (wakeWord != null) {
                Text("Say \u201C${wakePhrase(wakeWord)}\u201D any time the app is open, or tap the microphone.", style = MaterialTheme.typography.bodyMedium, color = AssistColours.Green, textAlign = TextAlign.Center)
            }
            Text(
                "I can do anything Home Assistant can: lights, heating, cleaning, the alarm, your lists and your own automations. Just ask.",
                style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center,
            )
            Text("New in this version", style = MaterialTheme.typography.labelLarge, color = AssistColours.Green, modifier = Modifier.align(Alignment.Start))
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
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            TileRow(
                listOf(TileItem("mdi:close", "Not now", false) { onSkip() }, TileItem("mdi:microphone", "Try it", true) { onTry() }),
                tone, scheme.onSurface,
            )
        }
    }
    }
}
