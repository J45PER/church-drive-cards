package com.churchdrive.app.wear

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.churchdrive.app.Session
import com.churchdrive.app.ha.ConnectionState
import com.churchdrive.app.ui.ASSIST_SUGGESTIONS
import com.churchdrive.app.ui.AssistState
import com.churchdrive.app.ui.AssistantBrand
import com.churchdrive.app.ui.AssistantGlyph
import com.churchdrive.app.ui.AssistantGlyphPose
import com.churchdrive.app.ui.ask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Clive on the wrist, his own app with his own launcher icon: three faces down the glass (Talk, Prompts, Settings) over his avatar glow,
 * which fills the whole screen and takes on the colours of what he has just answered.
 */
class AssistantActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WatchData.restore(this)
        setContent { MaterialTheme { CliveRoot() } }
    }
}

private val SHADOW = TextStyle(shadow = Shadow(Color(0xA6000000), Offset(0f, 2f), 12f))

/** What the prompts face offers: the phone's suggestions and the weather, so the cards are one tap away. */
private val PROMPTS = (ASSIST_SUGGESTIONS + "What's the weather?").distinct().take(5)

@Composable
private fun CliveRoot() {
    val context = LocalContext.current
    var signedIn by remember { mutableStateOf(Session(context).signedIn) }
    val connection by WatchHa.client.connection.collectAsState()
    val defaults by WatchData.config.collectAsState()
    val settings = remember { CliveSettings(context, defaults.clive) }
    val model = remember { CliveModel(context, settings) }
    DisposableEffect(Unit) { onDispose { model.stop() } }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { pullSession(context) }
        AssistantBrand.load(context)
        while (true) {
            signedIn = Session(context).signedIn
            if (signedIn) WatchHa.ensure(context)
            delay(2_000)
        }
    }
    val connected = connection == ConnectionState.Connected
    LaunchedEffect(connected) {
        if (connected) {
            WatchData.loadConfig()
            (WatchHa.api.ask("church_drive/assist/info", JSONObject()) as? JSONObject)?.let { AssistantBrand.update(context, it.optString("name"), null) }
            model.attach()
        } else model.lost()
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF090A14))) {
        val ui = remember(maxWidth, maxHeight) { Ui(minOf(maxWidth, maxHeight) / 280f) }
        CompositionLocalProvider(LocalUi provides ui) {
            val pager = rememberPagerState { 3 }
            val scope = rememberCoroutineScope()
            val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                if (granted) model.listen() else toast(context, "The microphone is off for this app")
            }
            val fallback = rememberListener("Ask ${AssistantBrand.shown}") { model.send(it) }
            val talk: () -> Unit = {
                when {
                    model.state == AssistState.Listening || model.state == AssistState.Speaking -> model.stop()
                    !model.speech.available -> fallback()
                    model.speech.hasPermission() -> model.listen()
                    else -> askPermission.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
            val visualCard = if (settings.visual && model.state != AssistState.Listening && model.state != AssistState.Thinking) model.card else null
            CliveGlow(if (signedIn) model.state else AssistState.Idle, model.level, cardPalette(visualCard), dim = pager.currentPage != 0 || !signedIn)
            // Lit while he is working, and for a minute after: a watch switches the glass off long before a slow answer arrives.
            var awake by remember { mutableStateOf(true) }
            LaunchedEffect(model.state, pager.currentPage, model.answer) {
                awake = true
                delay(if (model.state == AssistState.Idle) 90_000 else 300_000)
                awake = false
            }
            KeepAwake(awake)
            VerticalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                when (page) {
                    0 -> TalkFace(model, !signedIn, talk)
                    1 -> PromptsFace(signedIn) { sentence ->
                        scope.launch { pager.animateScrollToPage(0) }
                        model.send(sentence)
                    }
                    else -> SettingsFace(settings)
                }
            }
            Rail(pager.currentPage, 3)
            LoadingRing(visible = signedIn && !connected, label = "Connecting")
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.Rail(current: Int, count: Int) {
    val ui = LocalUi.current
    Column(Modifier.align(Alignment.CenterEnd).padding(end = ui.d(20f)), verticalArrangement = Arrangement.spacedBy(ui.d(5f)), horizontalAlignment = Alignment.CenterHorizontally) {
        for (i in 0 until count) {
            Box(Modifier.size(ui.d(4f), if (i == current) ui.d(12f) else ui.d(4f)).clip(CircleShape).background(Color.White.copy(alpha = if (i == current) 0.95f else 0.35f)))
        }
    }
}

@Composable
private fun TalkFace(model: CliveModel, signedOut: Boolean, onTap: () -> Unit) {
    val ui = LocalUi.current
    val settings = model.settings
    val calm = model.state != AssistState.Listening && model.state != AssistState.Thinking
    val card = if (settings.visual && calm) model.card else null
    val picture = if (settings.visual && calm) model.picture else null
    val talking = model.heard.isNotEmpty() || model.answer.isNotEmpty() || model.notice.isNotEmpty() || model.state == AssistState.Listening || model.state == AssistState.Thinking
    val bitmap by rememberRemoteImage(picture)
    val symbol = cardSymbol(card)
    val big = !talking || signedOut
    val photoMode = bitmap != null

    val markSize by animateDpAsState(if (photoMode) ui.d(32f) else if (big) ui.d(104f) else ui.d(58f), tween(550), label = "markSize")
    val markCentreY by animateDpAsState(if (photoMode) ui.d(244f) else if (big) ui.d(124f) else if (card != null) ui.d(82f) else ui.d(84f), tween(550), label = "markY")
    val morph by animateFloatAsState(if (symbol != null && !photoMode) 1f else 0f, tween(450), label = "morph")
    val photoAlpha by animateFloatAsState(if (photoMode) 1f else 0f, tween(600), label = "photo")

    Box(Modifier.fillMaxSize()) {
        bitmap?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize().graphicsLayer { alpha = photoAlpha }, contentScale = ContentScale.Crop) }
        when {
            signedOut -> {
                Note(AssistantBrand.shown, 30f, 34f, 220f, Color(0xFFB9BDD9), 17f, FontWeight.Bold, 1)
                Note("Open Church Drive on your phone to sign this watch in.", 36f, 176f, 208f, Color(0xFFD5D8F6), 13f)
            }
            !talking -> {
                Text(AssistantBrand.shown, color = Color.White, fontSize = ui.sp(17f), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, style = SHADOW, modifier = Modifier.at(ui, 30f, 34f, 220f))
                Text("Tap to talk", color = Color.White.copy(alpha = 0.9f), fontSize = ui.sp(12f), textAlign = TextAlign.Center, style = SHADOW, modifier = Modifier.at(ui, 30f, 206f, 220f))
            }
            else -> {
                if (model.heard.isNotEmpty()) Text(model.heard, color = Color(0xFFD5D8F6), fontSize = ui.sp(12f), textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis, style = SHADOW, modifier = Modifier.at(ui, 38f, 32f, 204f))
                when {
                    model.state == AssistState.Listening ->
                        Text(model.live.ifBlank { "Go ahead…" }, color = Color.White, fontSize = ui.sp(14f), textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis, style = SHADOW, modifier = Modifier.at(ui, 36f, 178f, 208f))
                    model.state == AssistState.Thinking ->
                        Text("Thinking…", color = Color(0xFFD5D8F6), fontSize = ui.sp(12f), textAlign = TextAlign.Center, style = SHADOW, modifier = Modifier.at(ui, 30f, 174f, 220f))
                    model.notice.isNotEmpty() ->
                        Text(model.notice, color = Color(0xFFFFB4AB), fontSize = ui.sp(14f), textAlign = TextAlign.Center, style = SHADOW, modifier = Modifier.at(ui, 34f, 134f, 212f))
                    photoMode -> Text("${model.pictureCaption ?: "Picture"}", color = Color.White, fontSize = ui.sp(11f), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.at(ui, 64f, 196f, 152f).background(Color(0x9E080914), RoundedCornerShape(50)).padding(horizontal = ui.d(10f), vertical = ui.d(5f)), textAlign = TextAlign.Center)
                    else -> Column(
                        Modifier.at(ui, 34f, if (card != null) 122f else 128f, 212f, if (card != null) 112f else 104f).verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (card != null && symbol != null) CardBlock(card)
                        if (card?.kind != "scores") Text(model.answer, color = Color.White, fontSize = ui.sp(if (card != null) 12f else 14f), textAlign = TextAlign.Center, style = SHADOW, modifier = Modifier.padding(top = if (card != null) ui.d(8f) else ui.d(0f), bottom = ui.d(18f)))
                    }
                }
            }
        }
        // His mark, straight on the glow; for a card it turns into the card's symbol.
        Box(
            // No clip: the mark drifts and morphs beyond its own square, and a clip cut its edges off.
            Modifier.offset(ui.d(140f) - markSize / 2, markCentreY - markSize / 2).size(markSize).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, enabled = !signedOut, onClick = onTap),
            contentAlignment = Alignment.Center,
        ) {
            val pose = when (model.state) {
                AssistState.Listening -> AssistantGlyphPose.Listening
                AssistState.Thinking -> AssistantGlyphPose.Thinking
                AssistState.Speaking -> AssistantGlyphPose.Speaking
                else -> AssistantGlyphPose.Idle
            }
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = 1f - morph; scaleX = 1f - 0.65f * morph; scaleY = 1f - 0.65f * morph; rotationZ = 25f * morph }, contentAlignment = Alignment.Center) {
                AssistantGlyph(pose, Color.White, markSize, model.level)
            }
            if (symbol != null) Box(Modifier.fillMaxSize().graphicsLayer { alpha = morph; scaleX = 0.35f + 0.65f * morph; scaleY = 0.35f + 0.65f * morph; rotationZ = -25f * (1f - morph) }, contentAlignment = Alignment.Center) {
                WIcon(symbol, Color.White, markSize)
            }
        }
    }
}

@Composable
private fun PromptsFace(enabled: Boolean, onSay: (String) -> Unit) {
    val ui = LocalUi.current
    Box(Modifier.fillMaxSize()) {
        Text("Ask ${AssistantBrand.shown}", color = Color.White, fontSize = ui.sp(16f), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, style = SHADOW, modifier = Modifier.at(ui, 30f, 30f, 220f))
        if (!enabled) {
            Note("Open Church Drive on your phone to sign this watch in.", 36f, 110f, 208f, Color(0xFFD5D8F6), 13f)
        } else PROMPTS.forEachIndexed { i, p ->
            Box(
                Modifier.at(ui, 44f, 56f + i * 36f, 192f, 32f).clip(RoundedCornerShape(50)).background(Color(0x59080914)).border(1.dp, Color(0x80DCE0FF), RoundedCornerShape(50)).clickable { onSay(p) },
                contentAlignment = Alignment.Center,
            ) { Text(p, color = Color(0xFFEEF0FF), fontSize = ui.sp(12f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = ui.d(10f))) }
        }
    }
}

@Composable
private fun SettingsFace(settings: CliveSettings) {
    val ui = LocalUi.current
    val items = listOf(
        Triple("voice", "Spoken answers", settings.voice),
        Triple("follow", "Follow-up listening", settings.follow),
        Triple("visual", "Visual answers", settings.visual),
        Triple("buzz", "Buzz on answer", settings.buzz),
    )
    Box(Modifier.fillMaxSize()) {
        Text("${AssistantBrand.shown} settings", color = Color.White, fontSize = ui.sp(16f), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, style = SHADOW, modifier = Modifier.at(ui, 30f, 30f, 220f))
        items.forEachIndexed { i, (key, label, on) ->
            val shape = RoundedCornerShape(50)
            Box(
                Modifier.at(ui, 38f, 58f + i * 46f, 204f, 40f).clip(shape).background(Color(0x66080914)).border(1.dp, Color(0x73DCE0FF), shape).clickable { settings.toggle(key) },
            ) {
                Text(label, color = Color(0xFFEEF0FF), fontSize = ui.sp(12f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.CenterStart).padding(start = ui.d(16f)).width(ui.d(120f)))
                Box(Modifier.align(Alignment.CenterEnd).padding(end = ui.d(12f)).size(ui.d(34f), ui.d(20f)).clip(RoundedCornerShape(50)).background(if (on) Color(0xFF4FD08A) else Color(0x47FFFFFF))) {
                    Box(Modifier.align(if (on) Alignment.CenterEnd else Alignment.CenterStart).padding(horizontal = ui.d(3f)).size(ui.d(14f)).clip(CircleShape).background(Color.White))
                }
            }
        }
    }
}
