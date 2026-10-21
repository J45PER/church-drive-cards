package com.churchdrive.app.wear

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Text

/** Scrolls the face list to the next face (a chip that says "the fan speeds are next" uses it). */
val LocalNextFace = androidx.compose.runtime.compositionLocalOf<() -> Unit> { {} }

/** Dark text for the lit (selected) button, as the phone's mode tiles: the face's accent colour behind it. */
val OnAccent = Color(0xFF202124)

/** The name at the top of a face (in its accent colour) and the one line under it. */
@Composable
fun FaceHeader(name: String, word: String?, accent: Color, nameSize: Float = 18f) {
    val ui = LocalUi.current
    Text(
        name, color = accent, fontSize = ui.sp(nameSize), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.at(ui, 30f, 36f - (18f - nameSize) / 2f, 220f),
    )
    if (word != null) Text(
        word, color = Color.White.copy(alpha = 0.88f), fontSize = ui.sp(12f), textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.at(ui, 30f, 60f, 220f),
    )
}

/** A line of text centred in a box [x] units in and [w] wide, [y] units down. */
@Composable
fun Note(text: String, x: Float, y: Float, w: Float, color: Color = Color.White, size: Float = 12f, weight: FontWeight = FontWeight.Normal, maxLines: Int = 3) {
    val ui = LocalUi.current
    Text(
        text, color = color, fontSize = ui.sp(size), fontWeight = weight, textAlign = TextAlign.Center, maxLines = maxLines, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.at(ui, x, y, w),
    )
}

/** One mode button: an icon over its name. The current one is filled with the accent; the others have a thin outline. */
@Composable
fun ModeButton(
    icon: String,
    label: String,
    selected: Boolean,
    accent: Color,
    ink: Color,
    modifier: Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val ui = LocalUi.current
    val shape = RoundedCornerShape(ui.d(18f))
    val fg = if (selected) OnAccent else ink
    Column(
        modifier
            .clip(shape)
            .then(if (selected) Modifier.background(accent, shape) else Modifier.border(1.dp, ink.copy(alpha = 0.4f), shape))
            .clickable(enabled = enabled, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        WIcon(icon, fg, ui.d(22f))
        Spacer(Modifier.height(ui.d(3f)))
        Text(label, color = fg, fontSize = ui.sp(10f), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Clip)
    }
}

/** A row of buttons across the glass: [x] units in from each side, [y] down, [h] tall, the buttons sharing the width. */
@Composable
fun ButtonRow(x: Float, y: Float, h: Float, content: @Composable RowScope.() -> Unit) {
    val ui = LocalUi.current
    Row(
        Modifier.at(ui, x, y, 280f - 2 * x, h),
        horizontalArrangement = Arrangement.spacedBy(ui.d(7f)),
        content = content,
    )
}

/** The share of a [ButtonRow] one button takes. */
@Composable
fun RowScope.Slot(): Modifier = Modifier.weight(1f).fillMaxHeight()

/** The round minus or plus beside a figure. */
@Composable
fun StepButton(plus: Boolean, ink: Color, enabled: Boolean = true, size: Float = 38f, onClick: () -> Unit) {
    val ui = LocalUi.current
    Box(
        Modifier
            .size(ui.d(size))
            .clip(CircleShape)
            .border(1.dp, ink.copy(alpha = if (enabled) 0.4f else 0.18f), CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        WIcon(if (plus) "plus" else "minus", ink.copy(alpha = if (enabled) 1f else 0.35f), ui.d(size * 0.52f))
    }
}

/** A big figure between a minus and a plus: the target temperature, a light's brightness. [onValue] is for tapping the figure itself. */
@Composable
fun Stepper(
    value: String,
    unit: String,
    ink: Color,
    top: Float,
    off: Boolean,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    onValue: (() -> Unit)? = null,
    size: Float = 42f,
    stepSize: Float = 38f,
    minusEnabled: Boolean = true,
) {
    val ui = LocalUi.current
    Row(
        Modifier.at(ui, 0f, top, 280f, stepSize + 4f),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepButton(false, ink, enabled = !off && minusEnabled, size = stepSize, onClick = onMinus)
        Box(
            Modifier.width(ui.d(104f)).let { if (onValue != null) it.clip(RoundedCornerShape(ui.d(12f))).clickable(onClick = onValue) else it },
            contentAlignment = Alignment.Center,
        ) {
            if (off) Text("Off", color = ink.copy(alpha = 0.7f), fontSize = ui.sp(30f), fontWeight = FontWeight.Bold)
            else Row(verticalAlignment = Alignment.Top) {
                Text(value, color = ink, fontSize = ui.sp(size), fontWeight = FontWeight.Bold, maxLines = 1)
                if (unit.isNotEmpty()) Text(unit, color = ink, fontSize = ui.sp(size * 0.5f), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = ui.d(size * 0.1f)))
            }
        }
        StepButton(true, ink, enabled = true, size = stepSize, onClick = onPlus)
    }
}

/** A small outlined pill, such as "Fan · Low" or the lights' page button. */
@Composable
fun PillChip(text: String, ink: Color, modifier: Modifier, onClick: () -> Unit) {
    val ui = LocalUi.current
    val shape = RoundedCornerShape(50)
    Box(
        modifier.clip(shape).border(1.dp, ink.copy(alpha = 0.4f), shape).clickable(onClick = onClick).padding(horizontal = ui.d(12f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = ink, fontSize = ui.sp(11f), fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** A scene as a coloured disc with its name under it; the playing scene has a white ring. */
@Composable
fun SceneDisc(name: String, colours: List<Color>, playing: Boolean, size: Float = 36f, width: Float = 56f, onClick: () -> Unit) {
    val ui = LocalUi.current
    val brush = if (colours.size >= 2) Brush.linearGradient(colours) else Brush.linearGradient(listOf(colours.firstOrNull() ?: Color.Gray, colours.firstOrNull() ?: Color.Gray))
    Column(Modifier.width(ui.d(width)).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(ui.d(size + 8f)).let { if (playing) it.border(2.dp, Color.White, CircleShape) else it }.padding(ui.d(4f)).clip(CircleShape).background(brush),
        )
        Text(name, color = Color.White, fontSize = ui.sp(9f), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

/** The 50% outline the face's text colour gives a button, as the phone's tiles. */
fun outline(ink: Color) = ink.copy(alpha = 0.4f)
