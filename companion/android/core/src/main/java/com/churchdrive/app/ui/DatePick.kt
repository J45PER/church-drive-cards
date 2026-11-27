package com.churchdrive.app.ui

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** A calendar to pick a day from (a task's due date, or the next date of a repeating one), shared by the phone app and To-Do. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickDialog(initial: LocalDate?, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    // The picker works in UTC milliseconds; a day is its midnight there.
    val state = rememberDatePickerState(initialSelectedDateMillis = (initial ?: LocalDate.now()).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
    val tone = toneColors(Tone.Grey)
    val colours = DatePickerDefaults.colors(
        containerColor = Color.Transparent, titleContentColor = tone.onContainer.copy(alpha = 0.7f), headlineContentColor = tone.onContainer,
        weekdayContentColor = tone.onContainer.copy(alpha = 0.7f), subheadContentColor = tone.onContainer.copy(alpha = 0.7f),
        navigationContentColor = tone.onContainer, yearContentColor = tone.onContainer, currentYearContentColor = tone.accent,
        selectedYearContainerColor = tone.accent, selectedYearContentColor = tone.onAccent,
        dayContentColor = tone.onContainer, selectedDayContainerColor = tone.accent, selectedDayContentColor = tone.onAccent,
        todayContentColor = tone.accent, todayDateBorderColor = tone.accent,
    )
    // The same acrylic frame, title and Cancel/OK as the time picker, so the two read as one family.
    AppDialog(onDismiss, maxWidth = 384.dp, inset = 12.dp) { _ ->
        DatePicker(state, colors = colours)
        DialogButtons(tone, onDismiss) { state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) } ?: onDismiss() }
    }
}
