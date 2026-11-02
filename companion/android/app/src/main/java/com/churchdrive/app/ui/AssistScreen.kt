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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.withTransform
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

@Composable
fun assistPalette(): AssistPalette {
    val s = MaterialTheme.colorScheme
    val phoneTheme = AssistColourSetting.phoneTheme
    return remember(phoneTheme, s.primary, s.tertiary, s.secondary) { if (phoneTheme) AssistPalette(s.primary, s.tertiary, s.secondary) else AssistClassicPalette }
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
    /** The typing box's state when the app's bar drives it; Clive on his own keeps his own. */
    typingState: AssistantTyping? = null,
    /** The bar's Settings button: take the person to Clive's settings. */
    onSettings: () -> Unit = {},
    /** Shown inside the app (over the page, under the app's round Clive button) instead of as a window of its own. */
    embedded: Boolean = false,
) {
    val ui = typingState ?: remember { AssistantTyping(startTyping) }
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
            } else FullAssist(model, close, onTask, ui, embedded, onSettings)
        }
    }
}

/** The cloud (see the shared one in core), in the phone's chosen palette. */
@Composable
fun AssistOrb(state: AssistState, level: Float, size: androidx.compose.ui.unit.Dp) = AssistOrb(state, level, size, assistPalette())

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
            HaIcon(icon, CdVectors.Info, scheme.onSurface, size.value.dp / 2)
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
                modifier = Modifier.clip(RoundedCornerShape(18.dp)).background(scheme.surfaceContainer).clickable { model.send(s, typed = true) }
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
    // Calm, not flickering: he counts as active a moment longer than the voice pipeline says (it flips between waiting and listening in quick
    // succession), and the voice level is smoothed so the glow swells with the voice instead of jumping with every sound.
    var held by remember { mutableStateOf(active) }
    LaunchedEffect(active) { if (active) held = true else { kotlinx.coroutines.delay(1_500); held = false } }
    val on by animateFloatAsState(if (held) 1f else 0.3f, tween(1200), label = "on")
    val smooth by animateFloatAsState(level, tween(450), label = "level")
    // Clipped to its own box, so it cannot spill over the bar below it; the top of the box fades out completely, so it has no edge there.
    Canvas(modifier = modifier.fillMaxWidth().height(240.dp).clipToBounds().graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)) {
        val a = (0.34f * breathe + 0.1f * smooth) * on
        listOf(0.2f to pal.first, 0.5f to pal.second, 0.82f to pal.third).forEach { (fx, colour) ->
            val c = Offset(size.width * fx, size.height + 24.dp.toPx())
            val r = size.width * 0.55f
            drawCircle(Brush.radialGradient(listOf(colour.copy(alpha = a), Color.Transparent), center = c, radius = r), radius = r, center = c)
        }
        drawRect(Brush.verticalGradient(0f to Color.Transparent, 0.5f to Color.Black, 1f to Color.Black), blendMode = BlendMode.DstIn)
    }
}

/** The round Clive button (see the shared one in core), in the phone's chosen palette. */
@Composable
fun AssistantRound(label: String, onClick: () -> Unit, state: AssistState = AssistState.Idle, level: Float = 0f, content: @Composable () -> Unit) =
    AssistantRound(label, onClick, assistPalette(), state, level, content)

/** What the typing box is doing: shared, so the app's bottom bar (which Clive's buttons borrow while he is open) can drive it. */
class AssistantTyping(start: Boolean = false) {
    var typing by mutableStateOf(start)
    var typed by mutableStateOf("")
    val focus = FocusRequester()
    /** Set by the Type button, and cleared once the keyboard has been brought up. */
    var keyboardRequested by mutableStateOf(false)

    /** Shows the typing box and brings the keyboard up. */
    fun showKeyboard() {
        typing = true
        keyboardRequested = true
    }

    /** A fresh opening: the box as Settings says, empty, and no keyboard asked for. */
    fun reset(typingBox: Boolean) {
        typing = typingBox
        typed = ""
        keyboardRequested = false
    }
}

/** How far up the bottom of Clive sits: above the bottom bar (80 dp and the system's bar), or above the keyboard when that is higher. */
@Composable
fun assistantBottomPad(keyboard: Boolean): androidx.compose.ui.unit.Dp {
    val d = androidx.compose.ui.platform.LocalDensity.current
    val nav = WindowInsets.navigationBars.getBottom(d)
    val ime = if (keyboard) WindowInsets.ime.getBottom(d) else 0
    val bar = with(d) { 80.dp.roundToPx() } + nav
    return with(d) { maxOf(ime, bar).toDp() }
}

/**
 * Leaves room at the bottom of whatever fills its parent so that its bottom edge sits where Clive's should: above the bottom bar, or above the
 * keyboard when that is higher. It measures where the parent really ends on the screen instead of guessing how much of the keyboard the parent has
 * already given up, which left the page showing through between Clive and the keyboard. Put it right after `fillMaxSize()`.
 */
fun Modifier.assistantBottomInset(keyboard: Boolean, extra: androidx.compose.ui.unit.Dp = 0.dp): Modifier = composed {
    val d = androidx.compose.ui.platform.LocalDensity.current
    val view = androidx.compose.ui.platform.LocalView.current
    val ime = if (keyboard) WindowInsets.ime.getBottom(d) else 0
    val bar = with(d) { 80.dp.roundToPx() } + WindowInsets.navigationBars.getBottom(d)
    var bottomOnScreen by remember { androidx.compose.runtime.mutableIntStateOf(-1) }
    val wanted = view.rootView.height - maxOf(ime, bar)
    val pad = if (bottomOnScreen < 0) maxOf(ime, bar) else (bottomOnScreen - wanted).coerceAtLeast(0)
    this
        .onGloballyPositioned { bottomOnScreen = (it.positionInWindow().y + it.size.height).toInt() }
        .padding(bottom = with(d) { pad.toDp() } + extra)
}

/**
 * Clive's own buttons for the bottom bar: Close, New chat, Type (the middle one), Voice, Settings. Inside the app they take the place
 * of the app's buttons in the same bar.
 */
@Composable
fun RowScope.AssistantBarItems(model: AssistModel, ui: AssistantTyping, onClose: () -> Unit, onSettings: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val chatting = model.messages.isNotEmpty()
    val keyboardUp = WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current) > 0
    NavigationBarItem(
        selected = false, onClick = onClose,
        icon = { HaIcon("mdi:close", CdVectors.Info, scheme.onSurfaceVariant, 24.dp) },
        label = { Text("Close", fontSize = 11.sp, maxLines = 1) },
    )
    NavigationBarItem(
        selected = false, enabled = chatting, onClick = { ui.typed = ""; model.newChat() },
        icon = { HaIcon("mdi:chat-plus-outline", CdVectors.Info, scheme.onSurfaceVariant.copy(alpha = if (chatting) 1f else 0.4f), 24.dp) },
        label = { Text("New chat", fontSize = 11.sp, maxLines = 1) },
    )
    NavigationBarItem(
        selected = keyboardUp, onClick = { ui.showKeyboard() },
        icon = { HaIcon("mdi:keyboard-outline", CdVectors.Info, if (keyboardUp) scheme.onSecondaryContainer else scheme.onSurfaceVariant, 24.dp) },
        label = { Text("Type", fontSize = 11.sp, maxLines = 1) },
    )
    NavigationBarItem(
        selected = false,
        onClick = {
            AssistVoiceSetting.set(context, !AssistVoiceSetting.on)
            if (!AssistVoiceSetting.on) model.stopSpeaking()
        },
        icon = { HaIcon(if (AssistVoiceSetting.on) "mdi:volume-high" else "mdi:volume-off", CdVectors.Info, scheme.onSurfaceVariant, 24.dp) },
        label = { Text(if (AssistVoiceSetting.on) "Voice" else "Muted", fontSize = 11.sp, maxLines = 1) },
    )
    NavigationBarItem(
        selected = false, onClick = onSettings,
        icon = { HaIcon("mdi:cog-outline", CdVectors.Info, scheme.onSurfaceVariant, 24.dp) },
        label = { Text("Settings", fontSize = 11.sp, maxLines = 1) },
    )
}

/**
 * Clive's screen. Inside the app ([embedded]) it fills the space above the app's own bottom bar, which has changed into Clive's
 * buttons, and the app's round button floats over it. On its own (from a widget or tile) it brings a bar and a button of its own.
 * Either way the keyboard goes over the bar, and the typing box rises above the keyboard.
 */
@Composable
private fun FullAssist(model: AssistModel, onClose: () -> Unit, onTask: (String) -> Unit, ui: AssistantTyping, embedded: Boolean, onSettings: () -> Unit) {
    val pal = assistPalette()
    val scheme = MaterialTheme.colorScheme
    val chatting = model.messages.isNotEmpty()
    val active = model.state == AssistState.Listening || model.state == AssistState.Thinking || model.state == AssistState.Speaking
    // The Type button: bring the keyboard up once the box is on screen. The box may still hold focus after the keyboard was folded away
    // (which leaves nothing to ask for), so focus is let go first.
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    LaunchedEffect(ui.keyboardRequested) {
        if (ui.keyboardRequested) {
            focusManager.clearFocus(force = true)
            kotlinx.coroutines.delay(60)
            runCatching { ui.focus.requestFocus() }
            keyboard?.show()
            ui.keyboardRequested = false
        }
    }
    // A question sent, and its answer arriving, fold the keyboard away, so the whole answer (and its card) has the screen to itself.
    // Android can ignore a hide that lands in the middle of the keyboard's own slide, so it is asked again a moment later.
    val lastMessage = model.messages.lastOrNull()
    val view = androidx.compose.ui.platform.LocalView.current
    LaunchedEffect(model.messages.size, model.state, lastMessage?.show) {
        val answering = model.state == AssistState.Thinking || (lastMessage != null && !lastMessage.fromUser)
        if (answering && !ui.keyboardRequested) {
            repeat(2) {
                keyboard?.hide()
                focusManager.clearFocus(force = true)
                androidx.core.view.ViewCompat.getWindowInsetsController(view)?.hide(androidx.core.view.WindowInsetsCompat.Type.ime())
                kotlinx.coroutines.delay(350)
            }
        }
    }
    // Leaving the app with the typing box focused makes Android put the keyboard straight back up on return, over whatever page is
    // showing. So the box lets go of focus when the app stops, and the keyboard only comes back when the Type button asks for it.
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(owner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) {
                focusManager.clearFocus(force = true)
                keyboard?.hide()
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val pad = assistantBottomPad(keyboard = true)
    Box(modifier = Modifier.fillMaxSize().background(scheme.background)) {
        BottomGlow(active, model.level, Modifier.align(Alignment.BottomCenter).padding(bottom = if (embedded) 0.dp else pad))
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(bottom = if (embedded) 0.dp else pad)) {
            Text(
                model.name.ifBlank { "Assistant" }, style = MaterialTheme.typography.titleMedium, color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            )
            if (chatting) {
                // The larger Clive icon stays at the top; the conversation starts below it.
                Column(modifier = Modifier.fillMaxWidth().padding(top = 0.dp, bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    AssistOrb(model.state, model.level, 72.dp)
                    Spacer(Modifier.height(4.dp))
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
                // The newest message (and its card, which arrives a moment after its words and changes the height) comes up to the top.
                val last = model.messages.lastOrNull()
                LaunchedEffect(model.messages.size, model.partial, model.state, last?.text, last?.show) {
                    list.animateScrollToItem(maxOf(0, model.messages.size))
                    kotlinx.coroutines.delay(400)
                    list.animateScrollToItem(maxOf(0, model.messages.size))
                }
                LazyColumn(state = list, modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    item { Spacer(Modifier.height(4.dp)) }
                    items(model.messages.size) { i -> ChatLine(model.messages[i]) }
                    item {
                        if (model.partial.isNotEmpty()) ChatLine(AssistMessage(true, model.partial), faded = true)
                        if (model.state == AssistState.Thinking) PendingCard()
                        Notice(model, onTask)
                    }
                }
            }
            // Room is left at the right for the round button, which floats over this row.
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (!chatting && !ui.typing) Box(Modifier.padding(end = 74.dp)) { SuggestionRow(model) }
                if (chatting || ui.typing) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = ui.typed, onValueChange = { ui.typed = it }, singleLine = true, modifier = Modifier.weight(1f).focusRequester(ui.focus),
                            placeholder = { Text("Ask or tell the house") },
                            keyboardOptions = KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { model.send(ui.typed, typed = true); ui.typed = ""; keyboard?.hide(); focusManager.clearFocus(force = true) }),
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
        if (!embedded) {
            // Alone, he brings the bar and the round button himself, in the same places as inside the app.
            NavigationBar(modifier = Modifier.align(Alignment.BottomCenter)) { AssistantBarItems(model, ui, onClose, onSettings) }
            Box(Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = pad + 16.dp)) {
                AssistantRound("Talk", { model.listen() }, model.state, model.level) {
                    AssistantGlyph(assistantGlyphPose(true, model.state), Color.White, 30.dp, model.level)
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

/** One line of the conversation: what you said in a soft gradient pill, and what Clive said as plain words. */
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
        AssistantBubble(message.text, message.show)
    }
}

/** The width of a card, whether it is still loading or finished. */
private val CARD_WIDTH = 280.dp

/**
 * What shows the moment a question has been asked: an empty card with his thinking mark in the middle. The answer's card (or words) takes
 * its place a moment later, so the screen is never blank while he works.
 */
@Composable
private fun PendingCard() {
    val scheme = MaterialTheme.colorScheme
    // The same width, padding and icon box as the card that replaces it, so the mark and the icon it becomes are in exactly the same place.
    Box(
        modifier = Modifier.fillMaxWidth().widthIn(max = CARD_WIDTH)
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 6.dp, bottomEnd = 20.dp))
            .background(scheme.surfaceContainerHigh)
            .padding(horizontal = 16.dp, vertical = 11.dp),
    ) { Box(Modifier.fillMaxWidth().height(CARD_ICON_ZONE), contentAlignment = Alignment.Center) { AssistantGlyph(AssistantGlyphPose.Thinking, scheme.onSurface, CARD_ICON_SIZE) } }
}

/** Clive's words in a speech bubble: the tail corner is bottom-left, the mirror of the user's gradient bubble. */
@Composable
private fun AssistantBubble(text: String, show: AssistantShow? = null) {
    val scheme = MaterialTheme.colorScheme
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    // An answer with a card is the card alone in his bubble: what he says out loud can say more, but the screen shows just the figures.
    val cardOnly = show?.card != null
    // A card with pictures waits, as a thinking pause, until they have all arrived, so nothing pops in afterwards.
    val context = androidx.compose.ui.platform.LocalContext.current
    val pictures by androidx.compose.runtime.produceState(show?.card?.let { !cardHasPictures(it) } ?: true, show?.card) {
        show?.card?.let { if (cardHasPictures(it)) { preloadCardImages(context, it); value = true } }
    }
    if (!pictures) { PendingCard(); return }
    // A card starts as the thinking mark in the icon box, the mark turns into the card's icon, and the figures open out underneath.
    val morph = remember { androidx.compose.animation.core.Animatable(if (show?.card != null) 0f else 1f) }
    var showBody by remember { androidx.compose.runtime.mutableStateOf(show?.card == null) }
    LaunchedEffect(Unit) {
        if (show?.card != null) {
            kotlinx.coroutines.delay(150)
            morph.animateTo(1f, tween(520))
            showBody = true
        }
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = (if (cardOnly) Modifier.fillMaxWidth().widthIn(max = CARD_WIDTH) else Modifier.widthIn(max = 320.dp))
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 6.dp, bottomEnd = 20.dp))
            .background(scheme.surfaceContainerHigh)
            .padding(horizontal = 16.dp, vertical = 11.dp),
    ) {
        if (!cardOnly) Text(text, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface)
        show?.card?.let { AssistantCardView(it, morph = morph.value, showBody = showBody) }
        show?.image?.let {
            coil.compose.AsyncImage(
                model = it, contentDescription = show.linkLabel ?: "Picture from ${AssistantBrand.shown}", contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
            )
        }
        show?.link?.let { l ->
            Text(
                show.linkLabel ?: l.removePrefix("https://").substringBefore('/'),
                style = MaterialTheme.typography.bodyMedium, color = scheme.primary,
                textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                modifier = Modifier.clickable { runCatching { uri.openUri(l) } },
            )
        }
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
                        HaIcon(f.icon, CdVectors.Info, tone.accent, 28.dp)
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
