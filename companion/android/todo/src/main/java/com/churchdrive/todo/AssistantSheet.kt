package com.churchdrive.todo

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.churchdrive.app.ui.AssistantBrand
import com.churchdrive.app.ui.AssistantGlyph
import com.churchdrive.app.ui.AssistantGlyphPose

/** The round Clive button of the To-Do app: opens [AssistantSheet]. */
@Composable
fun AssistantButton(onClick: () -> Unit) {
    Box(
        Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClickLabel = "${AssistantBrand.shown} To Do", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { AssistantGlyph(AssistantGlyphPose.Idle, MaterialTheme.colorScheme.onPrimaryContainer, 30.dp) }
}

/**
 * Clive To Do: a small sheet where a sentence is typed or spoken ("add milk", "tick off the bins") and done. It is deliberately
 * narrow: it can add to and tick off the person's own, the shared and the cleaning lists, and nothing else (see [TodoModel.clive]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantSheet(vm: TodoModel, onClose: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var reply by rememberSaveable { mutableStateOf("") }
    fun send(sentence: String) {
        if (sentence.isBlank()) return
        reply = vm.askAssistant(sentence)
        text = ""
    }
    // Speech to text is the phone's own: no microphone permission is needed, and nothing is sent to the house but the words.
    val speak = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { send(it) }
        }
    }
    fun listen() {
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Say \"add milk\" or \"tick off the bins\"")
        runCatching { speak.launch(i) }.onFailure { reply = "This phone has no speech input. Type it instead." }
    }
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 20.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                    AssistantGlyph(AssistantGlyphPose.Idle, MaterialTheme.colorScheme.onPrimaryContainer, 26.dp)
                }
                Column {
                    Text("${AssistantBrand.shown} To Do", style = MaterialTheme.typography.titleMedium)
                    Text("Add things and tick them off.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (reply.isNotBlank()) {
                Text(
                    reply,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.secondaryContainer).padding(14.dp),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    text, { text = it }, modifier = Modifier.weight(1f), singleLine = true,
                    placeholder = { Text("Add milk, tick off bins…") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send(text) }),
                    trailingIcon = { IconButton(onClick = { listen() }) { Icon(Icons.Filled.Mic, contentDescription = "Speak") } },
                )
                FilledIconButton(onClick = { send(text) }, enabled = text.isNotBlank()) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = { text = "Add " }, label = { Text("Add…") })
                AssistChip(onClick = { text = "Tick off " }, label = { Text("Tick off…") })
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}
