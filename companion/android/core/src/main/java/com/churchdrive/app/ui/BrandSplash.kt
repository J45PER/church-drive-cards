package com.churchdrive.app.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.churchdrive.core.R

/** Instrument Sans in its four weights (Regular, Medium, SemiBold, Bold), the Clive apps' own typeface for splash screens and wordmark text. */
val BrandFont = FontFamily(
    Font(R.font.instrument_sans_regular, FontWeight.Normal),
    Font(R.font.instrument_sans_medium, FontWeight.Medium),
    Font(R.font.instrument_sans_semibold, FontWeight.SemiBold),
    Font(R.font.instrument_sans_bold, FontWeight.Bold),
)
private val SplashFont = BrandFont

/**
 * The branded screen shown for a moment when an app starts, after the system's own (blank) splash: the app's mark, its name, and
 * "Powered by Home Assistant" at the bottom. In light mode the mark keeps its colours and the text is dark; in dark mode (on the app's
 * own dark colour) everything is white. Shown once per start (it stays gone after a rotation), and a tap skips it.
 */
@Composable
fun BrandSplash(name: String, @DrawableRes mark: Int, holdMillis: Long = 1300) {
    BrandSplash(name, holdMillis) { dark ->
        Image(
            painterResource(mark), contentDescription = null, modifier = Modifier.height(104.dp),
            // All white in dark mode (the tint keeps the shape and drops the colours).
            colorFilter = if (dark) ColorFilter.tint(Color.White) else null,
        )
    }
}

/**
 * The splash for the Clive apps: the logo changes from Clive's into the app's own (see [LogoMorph]) while the word "Clive" moves left and the
 * app's name ([sub], for example "Home Manager") slides in beside it, so the screen ends reading "Clive Home Manager". The word and the logo
 * change at the same moment ([SPLASH_MORPH_DELAY_MS], [SPLASH_MORPH_MS]).
 */
@Composable
fun BrandSplash(kind: LogoKind, sub: String, holdMillis: Long = 3200) {
    BrandSplash(title = { ink -> CliveTitle(sub, ink) }, holdMillis = holdMillis) { dark -> LogoMorph(kind, dark) }
}

@Composable
private fun CliveTitle(sub: String, ink: Color) {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(SPLASH_MORPH_DELAY_MS)
        p.animateTo(1f, tween(SPLASH_MORPH_MS.toInt(), easing = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)))
    }
    Row(verticalAlignment = Alignment.Bottom) {
        Text("Clive", color = ink, fontFamily = SplashFont, fontWeight = FontWeight.SemiBold, fontSize = 42.sp, modifier = Modifier.alignByBaseline())
        // The name takes up more room as it arrives, so "Clive" slides left to make way; it also fades in and travels in from the right.
        Text(
            sub, color = ink.copy(alpha = 0.8f), fontFamily = SplashFont, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, maxLines = 1, softWrap = false,
            modifier = Modifier
                .alignByBaseline()
                .padding(start = 8.dp)
                .layout { m, c ->
                    val pl = m.measure(c.copy(maxWidth = Constraints.Infinity))
                    layout((pl.width * p.value).roundToInt(), pl.height, mapOf(FirstBaseline to pl[FirstBaseline], LastBaseline to pl[LastBaseline])) { pl.place(0, 0) }
                }
                .graphicsLayer { alpha = p.value; translationX = (1f - p.value) * 24.dp.toPx(); clip = false },
        )
    }
}

/** The same screen with a mark the app draws itself (for a mark that moves): [mark] is told whether the screen is dark. */
@Composable
fun BrandSplash(name: String, holdMillis: Long = 1300, mark: @Composable (dark: Boolean) -> Unit) {
    BrandSplash(title = { ink -> Text(name, color = ink, fontFamily = SplashFont, fontWeight = FontWeight.SemiBold, fontSize = 34.sp) }, holdMillis = holdMillis, mark = mark)
}

@Composable
private fun BrandSplash(title: @Composable (ink: Color) -> Unit, holdMillis: Long, mark: @Composable (dark: Boolean) -> Unit) {
    var show by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(holdMillis)
        show = false
    }
    val dark = isSystemInDarkTheme()
    val bg = if (dark) Color(0xFF0B0F11) else Color.White
    val ink = if (dark) Color.White else Color(0xFF1E2A3A)
    val quiet = if (dark) Color(0xFF9AA5B1) else Color(0xFF6B7686)
    AnimatedVisibility(visible = show, exit = fadeOut(tween(350))) {
        Box(Modifier.fillMaxSize().background(bg).clickable(enabled = true, onClick = { show = false })) {
            Column(Modifier.align(Alignment.Center).wrapContentSize().padding(bottom = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                mark(dark)
                Spacer(Modifier.height(22.dp))
                title(ink)
            }
            Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Powered by", color = quiet, fontSize = 15.sp, fontFamily = SplashFont)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Image(painterResource(R.drawable.ic_ha_logo), contentDescription = null, modifier = Modifier.size(34.dp), colorFilter = ColorFilter.tint(ink))
                    Text("Home Assistant", color = ink, fontFamily = SplashFont, fontWeight = FontWeight.SemiBold, fontSize = 21.sp)
                }
            }
        }
    }
}
