package com.churchdrive.app.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.churchdrive.core.R

private val SplashFont = FontFamily(Font(R.font.instrument_sans_semibold, FontWeight.SemiBold))

/**
 * The branded screen shown for a moment when an app starts, after the system's own (blank) splash: the app's mark, its name, and
 * "Powered by Home Assistant" at the bottom. In light mode the mark keeps its colours and the text is dark; in dark mode (on the app's
 * own dark colour) everything is white. Shown once per start (it stays gone after a rotation), and a tap skips it.
 */
@Composable
fun BrandSplash(name: String, @DrawableRes mark: Int, holdMillis: Long = 1300) {
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
                Image(
                    painterResource(mark), contentDescription = null, modifier = Modifier.height(104.dp),
                    // All white in dark mode (the tint keeps the shape and drops the colours).
                    colorFilter = if (dark) ColorFilter.tint(Color.White) else null,
                )
                Spacer(Modifier.height(22.dp))
                Text(name, color = ink, fontFamily = SplashFont, fontWeight = FontWeight.SemiBold, fontSize = 34.sp)
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
