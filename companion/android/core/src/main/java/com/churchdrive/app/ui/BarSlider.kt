package com.churchdrive.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.churchdrive.app.ha.CallService
import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ha.data
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import kotlin.math.hypot
import kotlin.math.atan2
import kotlin.math.roundToInt
import androidx.compose.ui.draw.alpha

/**
 * A wide bar you drag across (or tap) to set a level; it calls [onSet] when you let go. The slider is part of the bar: the fill is the
 * level and a white handle sits at its end. [snap] moves the level to its nearest step and [valueOf] words the level while you drag.
 */
@Composable
fun BarSlider(
    label: String, value: String, fraction: Float, fill: Brush, thumbOnly: Boolean, onSet: (Float) -> Unit,
    snap: ((Float) -> Float)? = null, valueOf: ((Float) -> String)? = null, height: androidx.compose.ui.unit.Dp = 60.dp, header: Boolean = true,
    enabled: Boolean = true,
) {
    var drag by remember(fraction) { mutableStateOf<Float?>(null) }
    val asked = rememberPending<Float>(fraction)
    var width by remember { mutableStateOf(1) }
    val shown = drag ?: asked.asked ?: fraction
    fun at(x: Float): Float = (x / width).coerceIn(0f, 1f).let { snap?.invoke(it) ?: it }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (header) Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(if (drag != null && valueOf != null) valueOf(shown) else value, style = MaterialTheme.typography.bodyMedium)
        }
        Box(
            modifier = Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(18.dp)).alpha(if (enabled) 1f else 0.5f)
                .background(if (thumbOnly) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHigh)
                .onSizeChanged { width = it.width.coerceAtLeast(1) }
                .pointerInput(enabled) { if (enabled) detectTapGestures { asked.ask(at(it.x)); onSet(at(it.x)) } }
                .pointerInput(enabled) {
                    if (enabled) detectHorizontalDragGestures(
                        onDragStart = { drag = at(it.x) },
                        onDragEnd = { drag?.let { asked.ask(it); onSet(it) }; drag = null },
                        onDragCancel = { drag = null },
                    ) { change, _ -> drag = at(change.position.x) }
                },
        ) {
            if (thumbOnly) Box(Modifier.fillMaxSize().background(fill))
            else Box(Modifier.fillMaxHeight().fillMaxWidth(shown).background(fill))
            Box(Modifier.align(Alignment.CenterStart).fillMaxWidth(shown.coerceAtLeast(0.04f)), contentAlignment = Alignment.CenterEnd) {
                Box(Modifier.padding(end = 8.dp).size(width = 4.dp, height = (height - 24.dp).coerceAtLeast(16.dp)).clip(RoundedCornerShape(2.dp)).background(Color.White))
            }
        }
    }
}

