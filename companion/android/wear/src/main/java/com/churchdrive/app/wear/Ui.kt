package com.churchdrive.app.wear

import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Scaffold
import com.churchdrive.app.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Every screen starts here: waits for the phone's sign-in, keeps the line to the house up, and shows [content] once signed in.
 * Until then it says what to do. [connected] is whether the line is up right now.
 */
@Composable
fun Signed(content: @Composable (connected: Boolean) -> Unit) {
    val context = LocalContext.current
    var signedIn by remember { mutableStateOf(Session(context).signedIn) }
    val connection by WatchHa.client.connection.collectAsState()
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { pullSession(context) }
        while (true) {
            signedIn = Session(context).signedIn
            if (signedIn) WatchHa.ensure(context)
            delay(2_000)
        }
    }
    MaterialTheme {
        Scaffold(timeText = { TimeText() }) {
            if (!signedIn) {
                ScalingLazyColumn(modifier = Modifier.fillMaxWidth()) {
                    item { Text("Open Church Drive on your phone to sign this watch in.", textAlign = TextAlign.Center) }
                }
            } else {
                content(connection == com.churchdrive.app.ha.ConnectionState.Connected)
            }
        }
    }
}

/** A full-width chip with one line of text. */
fun ScalingLazyListScope.row(label: String, secondary: String? = null, onClick: () -> Unit) {
    item {
        Chip(
            modifier = Modifier.fillMaxWidth(),
            onClick = onClick,
            label = { Text(label, maxLines = 2) },
            secondaryLabel = secondary?.takeIf { it.isNotBlank() }?.let { { Text(it, maxLines = 1) } },
        )
    }
}

/** The phone's speech box (Google's, on the watch too): [onHeard] gets what was said. Call the returned function to listen. */
@Composable
fun rememberListener(prompt: String, onHeard: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher: ActivityResultLauncher<Intent> = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val said = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.trim()
        if (!said.isNullOrEmpty()) onHeard(said)
    }
    return {
        val listen = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
        runCatching { launcher.launch(listen) }.onFailure { toast(context, "This watch has no speech recogniser") }
    }
}

fun toast(context: Context, text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
