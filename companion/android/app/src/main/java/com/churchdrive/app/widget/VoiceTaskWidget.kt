package com.churchdrive.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.churchdrive.app.VoiceTaskActivity
import com.churchdrive.app.ui.Tone

/**
 * Add task: a one-cell microphone. One tap opens the phone's speech box, and what you say goes onto the right lists. It
 * needs nothing from the house to draw, so it is always there; made wide, it also says what it does. The to-do widget has
 * the same microphone beside its heading.
 */
class VoiceTaskGlanceWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(WidgetSizes.fine)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { VoiceTaskBody(context) }
    }
}

/** Opens the phone's speech box to add a task by voice. */
@Composable
fun voiceTaskAction(): androidx.glance.action.Action =
    actionStartActivity(Intent(androidx.glance.LocalContext.current, VoiceTaskActivity::class.java))

/** The microphone button the to-do widget carries as well. */
@Composable
fun VoiceTaskButton(p: WidgetPalette, size: androidx.compose.ui.unit.Dp = 36.dp) =
    IconButton("mdi:microphone", p, voiceTaskAction(), size, fill = p.tone(Tone.Blue))

@Composable
private fun VoiceTaskBody(context: Context) {
    val p = WidgetPalette.of(androidx.glance.LocalContext.current)
    val blue = p.tone(Tone.Blue)
    val start = voiceTaskAction()
    val wide = LocalSize.current.width.value >= 250f
    Box(
        GlanceModifier.fillMaxSize().cornerRadius(if (wide) 28.dp else 999.dp).background(cp(p.surface)).padding(10.dp).clickable(start),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton("mdi:microphone", p, start, 44.dp, fill = blue)
            if (wide) {
                Spacer(GlanceModifier.width(12.dp))
                Text("Add task", style = TextStyle(color = cp(p.onSurface), fontSize = 16.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            }
        }
    }
}

class VoiceTaskWidgetReceiver : LiveReceiver() {
    override val glanceAppWidget: GlanceAppWidget = VoiceTaskGlanceWidget()
}
