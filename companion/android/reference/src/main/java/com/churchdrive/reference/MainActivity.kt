package com.churchdrive.reference

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.churchdrive.app.ui.AppAlert
import com.churchdrive.app.ui.AppMenu
import com.churchdrive.app.ui.AppMenuItem
import com.churchdrive.app.ui.AppSwitch
import com.churchdrive.app.ui.BarSlider
import com.churchdrive.app.ui.ChurchDriveTheme
import com.churchdrive.app.ui.DUE_CHOICES
import com.churchdrive.app.ui.DatePickDialog
import com.churchdrive.app.ui.DurationPickDialog
import com.churchdrive.app.ui.OptionRow
import com.churchdrive.app.ui.PickDialog
import com.churchdrive.app.ui.TileItem
import com.churchdrive.app.ui.TileRow
import com.churchdrive.app.ui.TimePickDialog
import com.churchdrive.app.ui.TimeTile
import com.churchdrive.app.ui.Tone
import com.churchdrive.app.ui.ToneColors
import com.churchdrive.app.ui.WashField
import com.churchdrive.app.ui.dateWords
import com.churchdrive.app.ui.toneColors
import java.time.LocalDate

/**
 * The reference app: every shared element of the Church Drive apps, drawn by the real components in `:core` (not copies), in any of the
 * apps' colours. Review-only, never published. Add a board here whenever the kit gets a new element.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ChurchDriveTheme { Surface(Modifier.fillMaxSize()) { ReferenceApp() } } }
    }
}

private val BOARDS = listOf("Forms", "Pickers", "Switches", "Sliders", "Menus", "Dialogs")
private val TONES = listOf(Tone.Teal, Tone.Coral, Tone.Indigo, Tone.Green, Tone.Amber, Tone.Blue, Tone.Purple, Tone.Red, Tone.Grey)

@Composable
private fun ReferenceApp() {
    var board by remember { mutableStateOf(BOARDS.first()) }
    var tone by remember { mutableStateOf(Tone.Teal) }
    val colours = toneColors(tone)
    Column(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(colours.container, MaterialTheme.colorScheme.background))).safeDrawingPadding()
            .verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Reference", style = MaterialTheme.typography.headlineLarge, color = colours.onContainer)
        Text("The real components from the shared kit. Pick a board and a colour.", style = MaterialTheme.typography.bodyMedium, color = colours.onContainer.copy(alpha = 0.75f))
        OptionRow(BOARDS, board, colours) { board = it }
        OptionRow(TONES.map { it.name }, tone.name, colours) { name -> tone = TONES.first { it.name == name } }
        when (board) {
            "Forms" -> FormsBoard(colours)
            "Pickers" -> PickersBoard(colours)
            "Switches" -> SwitchesBoard(colours)
            "Sliders" -> SlidersBoard(colours)
            "Menus" -> MenusBoard(colours)
            else -> DialogsBoard(colours)
        }
    }
}

@Composable
private fun Label(text: String, tone: ToneColors) = Text(text, style = MaterialTheme.typography.labelLarge, color = tone.onContainer.copy(alpha = 0.8f))

@Composable
private fun FormsBoard(tone: ToneColors) {
    val today = LocalDate.now()
    var name by remember { mutableStateOf("") }
    var due by remember { mutableStateOf("Tomorrow") }
    var picked by remember { mutableStateOf<LocalDate?>(null) }
    var picking by remember { mutableStateOf(false) }
    var time by remember { mutableStateOf("09:00") }
    var notes by remember { mutableStateOf("") }
    var sure by remember { mutableStateOf(false) }
    var people by remember { mutableStateOf(setOf("Jamie")) }
    WashField(name, { name = it }, "Name", tone)
    Label("Who is it for?", tone)
    TileRow(listOf("Diane", "Hayley", "Ian", "Jamie").map { p -> TileItem("mdi:account-outline", p, p in people) { people = if (p in people) people - p else people + p } }, tone, tone.onContainer)
    Label("Day", tone)
    OptionRow(DUE_CHOICES, if (picked != null) "" else due, tone) { due = it; picked = null }
    picked?.let { Text(dateWords(it, today), style = MaterialTheme.typography.bodySmall, color = tone.onContainer) }
    TileRow(listOf(TileItem("mdi:calendar-edit", "Pick a date", picked != null) { picking = true }), tone, tone.onContainer, perRow = 1)
    if (picking) DatePickDialog(picked ?: today, { picked = it; picking = false }, { picking = false })
    TimeTile("Reminder time", time, tone) { time = it }
    WashField(notes, { notes = it }, "Notes", tone, singleLine = false, minLines = 3)
    TileRow(listOf(TileItem("mdi:close", "Cancel", false) {}, TileItem("mdi:check", "Save", true) {}), tone, tone.onContainer)
    TileRow(listOf(TileItem("mdi:delete-outline", if (sure) "Tap again to delete" else "Delete", sure) { sure = !sure }), toneColors(Tone.Red), tone.onContainer)
}

@Composable
private fun PickersBoard(tone: ToneColors) {
    var open by remember { mutableStateOf("") }
    var choice by remember { mutableStateOf("Weekly") }
    var seconds by remember { mutableStateOf(90) }
    Label("Tap one to open it, as it is in the apps.", tone)
    TileRow(
        listOf(
            TileItem("mdi:format-list-bulleted", "Choice: $choice", false) { open = "choice" },
            TileItem("mdi:clock-outline", "Time", false) { open = "time" },
            TileItem("mdi:calendar", "Date", false) { open = "date" },
            TileItem("mdi:timer-outline", "Length: $seconds s", false) { open = "length" },
        ),
        tone, tone.onContainer, perRow = 2,
    )
    when (open) {
        "choice" -> PickDialog("Repeat", listOf("None", "Daily", "Weekly", "Monthly"), choice, { it }, { open = "" }) { choice = it }
        "time" -> TimePickDialog("09:00", { open = "" }, { open = "" })
        "date" -> DatePickDialog(LocalDate.now(), { open = "" }, { open = "" })
        "length" -> DurationPickDialog("Length", seconds, { seconds = it; open = "" }, { open = "" })
    }
}

@Composable
private fun SwitchesBoard(tone: ToneColors) {
    var a by remember { mutableStateOf(true) }
    var b by remember { mutableStateOf(false) }
    var c by remember { mutableStateOf(false) }
    Label("A setting that stays as set. Off: faint track, light thumb. On: the strong colour, dark thumb.", tone)
    SwitchRow("Carpet boost", a, tone) { a = it }
    SwitchRow("Do not disturb", b, tone) { b = it }
    SwitchRow("Child lock", c, tone) { c = it }
    SwitchRow("Not available", true, tone, enabled = false) {}
}

@Composable
private fun SwitchRow(label: String, on: Boolean, tone: ToneColors, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(18.dp)).background(tone.onContainer.copy(alpha = 0.10f)).padding(start = 16.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = tone.onContainer)
        AppSwitch(on, onChange, enabled = enabled, tone = tone)
    }
}

@Composable
private fun SlidersBoard(tone: ToneColors) {
    var brightness by remember { mutableFloatStateOf(0.7f) }
    var target by remember { mutableFloatStateOf(0.55f) }
    var volume by remember { mutableFloatStateOf(0.4f) }
    val warm = Brush.horizontalGradient(listOf(androidx.compose.ui.graphics.Color(0xFFFFB45C), androidx.compose.ui.graphics.Color(0xFFFFF3D6)))
    val strong = Brush.horizontalGradient(listOf(tone.accent.copy(alpha = 0.35f), tone.accent.copy(alpha = 0.8f)))
    Label("One bar for every level: the value on the right, the fill is the level, a white handle at its end.", tone)
    BarSlider("Brightness", "${(brightness * 100).toInt()}%", brightness, warm, thumbOnly = false, onSet = { brightness = it }, valueOf = { "${(it * 100).toInt()}%" })
    BarSlider("Target", "${16 + (target * 10).toInt()}°", target, strong, thumbOnly = false, onSet = { target = it }, valueOf = { "${16 + (it * 10).toInt()}°" })
    Label("Short, where the row already says what it is:", tone)
    BarSlider("", "", volume, strong, thumbOnly = false, height = 48.dp, header = false, onSet = { volume = it })
}

@Composable
private fun MenusBoard(tone: ToneColors) {
    var open by remember { mutableStateOf(false) }
    var last by remember { mutableStateOf("Nothing picked yet") }
    Label("More actions than there is room for. Choosing a value is a picker, not a menu.", tone)
    Column {
        TileRow(listOf(TileItem("mdi:plus", "Open the menu", false) { open = true }), tone, tone.onContainer, perRow = 1)
        AppMenu(open, { open = false }) {
            AppMenuItem("mdi:video-outline", "Open the camera", { open = false; last = "Open the camera" })
            AppMenuItem("mdi:microphone-outline", "Talk", { open = false; last = "Talk" })
            AppMenuItem("mdi:download-outline", "Save clip", { open = false; last = "Save clip" }, selected = true)
            AppMenuItem("mdi:delete-outline", "Delete clip", { open = false; last = "Delete clip" }, destructive = true)
        }
    }
    Text(last, style = MaterialTheme.typography.bodyMedium, color = tone.onContainer)
}

@Composable
private fun DialogsBoard(tone: ToneColors) {
    var open by remember { mutableStateOf("") }
    Label("A question or notice: the same grey card as the pickers, plain text buttons.", tone)
    TileRow(listOf(TileItem("mdi:help-circle-outline", "A question", false) { open = "question" }, TileItem("mdi:information-outline", "A notice", false) { open = "notice" }), tone, tone.onContainer)
    if (open == "question") AppAlert(
        onDismissRequest = { open = "" }, title = { Text("Stop telling me?") },
        text = { Text("This is a safety alarm. You will not be told about it on any of your phones.") },
        confirmButton = { androidx.compose.material3.TextButton({ open = "" }) { Text("Stop telling me") } },
        dismissButton = { androidx.compose.material3.TextButton({ open = "" }) { Text("Keep it on") } },
    )
    if (open == "notice") AppAlert(
        onDismissRequest = { open = "" }, title = { Text("No tasks found") }, text = { Text("There was nothing in that text to do.") },
        confirmButton = { androidx.compose.material3.TextButton({ open = "" }) { Text("OK") } },
    )
}
