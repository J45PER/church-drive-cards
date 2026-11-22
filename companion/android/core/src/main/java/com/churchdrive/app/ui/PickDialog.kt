package com.churchdrive.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** The frame every picker dialog shares: the app's grey acrylic card in the middle of the screen, with the tone's colours handed to [content]. */
@Composable
internal fun AppDialog(onDismiss: () -> Unit, content: @Composable ColumnScope.(ToneColors) -> Unit) {
    val tone = toneColors(Tone.Grey)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.padding(horizontal = 16.dp).widthIn(max = 360.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp))
                .background(Brush.linearGradient(listOf(tone.container, lerp(tone.container, tone.accent, 0.14f))))
                .padding(24.dp),
        ) { content(tone) }
    }
}

/** Cancel, and OK when [onOk] is given, in the tone's accent colour. */
@Composable
private fun DialogButtons(tone: ToneColors, onCancel: () -> Unit, onOk: (() -> Unit)?) {
    val colours = ButtonDefaults.textButtonColors(contentColor = tone.accent)
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = onCancel, colors = colours) { Text("Cancel") }
        if (onOk != null) TextButton(onClick = onOk, colors = colours) { Text("OK") }
    }
}

/**
 * A single-choice dialog in the middle of the screen, in the app's acrylic: a title, large rows with a radio button each (the current choice
 * washed and selected) and Cancel. A tap on a row picks it and closes the dialog; so does tapping outside.
 */
@Composable
fun <T> PickDialog(title: String, options: List<T>, selected: T?, label: (T) -> String, onDismiss: () -> Unit, onPick: (T) -> Unit) {
    AppDialog(onDismiss) { tone ->
        Text(title, style = MaterialTheme.typography.headlineSmall, color = tone.onContainer)
        Spacer(Modifier.padding(top = 14.dp))
        Column(Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            options.forEach { option ->
                val on = option == selected
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(18.dp))
                        .background(if (on) tone.onContainer.copy(alpha = 0.10f) else Color.Transparent)
                        .clickable(onClickLabel = label(option)) { onPick(option); onDismiss() }.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RadioButton(
                        selected = on, onClick = null,
                        colors = RadioButtonDefaults.colors(selectedColor = tone.accent, unselectedColor = tone.onContainer.copy(alpha = 0.6f)),
                    )
                    Text(label(option), style = MaterialTheme.typography.bodyLarge, color = tone.onContainer)
                }
            }
        }
        DialogButtons(tone, onDismiss, null)
    }
}

/** A time of day ("HH:mm", 24 hour) picked on a clock face, in the app's acrylic. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickDialog(initial: String?, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val parts = initial?.split(":")?.mapNotNull { it.toIntOrNull() }.orEmpty()
    val state = rememberTimePickerState(parts.getOrNull(0) ?: 7, parts.getOrNull(1) ?: 30, true)
    AppDialog(onDismiss) { tone ->
        Text("Select time", style = MaterialTheme.typography.labelLarge, color = tone.onContainer.copy(alpha = 0.7f))
        Spacer(Modifier.padding(top = 16.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            TimePicker(
                state,
                colors = TimePickerDefaults.colors(
                    containerColor = Color.Transparent,
                    clockDialColor = tone.onContainer.copy(alpha = 0.10f),
                    selectorColor = tone.accent,
                    clockDialSelectedContentColor = tone.onAccent,
                    clockDialUnselectedContentColor = tone.onContainer,
                    timeSelectorSelectedContainerColor = tone.accent,
                    timeSelectorUnselectedContainerColor = tone.onContainer.copy(alpha = 0.10f),
                    timeSelectorSelectedContentColor = tone.onAccent,
                    timeSelectorUnselectedContentColor = tone.onContainer,
                ),
            )
        }
        DialogButtons(tone, onDismiss) { onPick("%02d:%02d".format(state.hour, state.minute)); onDismiss() }
    }
}

/** A length of time in words: "20 s", "1 min", "1 min 30 s". */
fun delayText(seconds: Int): String = when {
    seconds < 60 -> "$seconds s"
    seconds % 60 == 0 -> "${seconds / 60} min"
    else -> "${seconds / 60} min ${seconds % 60} s"
}

/** The quick choices under a duration's boxes, in seconds. */
private val DURATION_PRESETS = listOf(5, 10, 20, 30, 60, 300, 600, 1800, 3600)

/** A length of time from one second to an hour: minutes and seconds typed in, with quick choices beneath, in the app's acrylic. [seconds] comes back between 1 and 3600. */
@Composable
fun DurationPickDialog(title: String, seconds: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    var minutes by remember { mutableStateOf((seconds / 60).toString().padStart(2, '0')) }
    var secs by remember { mutableStateOf((seconds % 60).toString().padStart(2, '0')) }
    fun total() = ((minutes.toIntOrNull() ?: 0) * 60 + (secs.toIntOrNull() ?: 0)).coerceIn(1, 3600)
    AppDialog(onDismiss) { tone ->
        Text(title.uppercase(), style = MaterialTheme.typography.labelLarge, color = tone.onContainer.copy(alpha = 0.7f))
        Spacer(Modifier.padding(top = 16.dp))
        val fieldColours = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = tone.accent, unfocusedContainerColor = tone.onContainer.copy(alpha = 0.10f),
            focusedTextColor = tone.onAccent, unfocusedTextColor = tone.onContainer,
            focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent, cursorColor = tone.onAccent,
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                minutes, { minutes = it.filter(Char::isDigit).take(2) }, Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(18.dp),
                label = { Text("min") }, colors = fieldColours, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = MaterialTheme.typography.headlineMedium,
            )
            Text(":", style = MaterialTheme.typography.headlineMedium, color = tone.onContainer)
            OutlinedTextField(
                secs, { secs = it.filter(Char::isDigit).take(2) }, Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(18.dp),
                label = { Text("sec") }, colors = fieldColours, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = MaterialTheme.typography.headlineMedium,
            )
        }
        Text("From 1 second to 60 minutes", style = MaterialTheme.typography.bodySmall, color = tone.onContainer.copy(alpha = 0.7f), modifier = Modifier.padding(top = 8.dp, bottom = 12.dp))
        TileRow(
            DURATION_PRESETS.map { s ->
                TileItem("", delayText(s), total() == s) { minutes = (s / 60).toString().padStart(2, '0'); secs = (s % 60).toString().padStart(2, '0') }
            },
            tone, tone.onContainer, perRow = 3,
        )
        DialogButtons(tone, onDismiss) { onPick(total()); onDismiss() }
    }
}
