package com.churchdrive.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

// The parts of the edit-task page that the phone app and the To-Do app share, drawn like the rest of the app: a faint wash of the
// text colour, 18 dp corners, no outline, and the clock for a time.

/**
 * A text box on a faint wash, no outline (as the add box on a task card), with its name above the text ([label] blank: only the [placeholder]).
 * [trailing] is an icon button at the end (a microphone), [keyboardOptions] and [keyboardActions] set the Enter key; the shape is the app's 18 dp.
 */
@Composable
fun WashField(
    value: String, onChange: (String) -> Unit, label: String, tone: ToneColors, modifier: Modifier = Modifier, singleLine: Boolean = true, minLines: Int = 1,
    isError: Boolean = false, imeAction: ImeAction = ImeAction.Default, onImeAction: (() -> Unit)? = null, placeholder: String? = null,
    trailing: (@Composable () -> Unit)? = null, enabled: Boolean = true, keyboardOptions: KeyboardOptions? = null,
    keyboardActions: androidx.compose.foundation.text.KeyboardActions? = null,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation = androidx.compose.ui.text.input.VisualTransformation.None,
) {
    val ink = tone.onContainer
    TextField(
        value = value, onValueChange = onChange, enabled = enabled, singleLine = singleLine, minLines = minLines, isError = isError,
        label = label.takeIf { it.isNotBlank() }?.let { l -> { Text(l) } },
        placeholder = placeholder?.let { p -> { Text(p) } },
        trailingIcon = trailing, visualTransformation = visualTransformation,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        keyboardOptions = keyboardOptions ?: KeyboardOptions(imeAction = imeAction),
        keyboardActions = keyboardActions ?: androidx.compose.foundation.text.KeyboardActions(onAny = { onImeAction?.invoke() }),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = ink.copy(alpha = 0.14f), unfocusedContainerColor = ink.copy(alpha = 0.10f), errorContainerColor = ink.copy(alpha = 0.10f),
            disabledContainerColor = ink.copy(alpha = 0.05f),
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, errorIndicatorColor = Color.Transparent, disabledIndicatorColor = Color.Transparent,
            focusedTextColor = ink, unfocusedTextColor = ink, errorTextColor = ink, disabledTextColor = ink.copy(alpha = 0.5f),
            focusedLabelColor = tone.accent, unfocusedLabelColor = ink.copy(alpha = 0.7f), errorLabelColor = MaterialTheme.colorScheme.error,
            focusedPlaceholderColor = ink.copy(alpha = 0.55f), unfocusedPlaceholderColor = ink.copy(alpha = 0.55f),
            cursorColor = tone.accent,
        ),
    )
}

/** A time of day as a tile (its name on the left, the time in bold on the right); a tap opens the clock, as every other time in the app does. */
@Composable
fun TimeTile(label: String, value: String, tone: ToneColors, onPick: (String) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    val ink = tone.onContainer
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(18.dp)).background(ink.copy(alpha = 0.10f))
            .clickable(onClickLabel = "Change $label") { picking = true }.padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = ink, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.headlineSmall, color = ink, fontWeight = FontWeight.Bold)
    }
    if (picking) TimePickDialog(value, { onPick(it) }, { picking = false })
}
