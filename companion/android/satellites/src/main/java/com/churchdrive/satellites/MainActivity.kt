package com.churchdrive.satellites

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.churchdrive.app.ui.ChurchDriveTheme
import com.churchdrive.app.ui.SignInForm

/** Church Drive Satellites: the house's voice satellites, each with its design, glances and backlight. */
class MainActivity : ComponentActivity() {
    private val vm: SatelliteModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        CrashNote.install(this)
        super.onCreate(savedInstanceState)
        // If the app crashed last time, say what happened on a plain screen before starting anything that could crash again.
        val crash = CrashNote.take(this)
        if (crash != null) {
            setContent { CrashScreen(crash) { recreate() } }
            return
        }
        setContent {
            ChurchDriveTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (!vm.signedIn) {
                        SignInForm(
                            title = "Church Drive Satellites",
                            blurb = "Sign in with your Home Assistant login. If the Church Drive app is on this phone and signed in, this app uses its sign-in.",
                            busy = vm.busy, needCode = vm.needCode, error = vm.loginError, changeAddress = false,
                            onLogin = vm::login, onCode = vm::submitCode,
                            header = { SatelliteMark(androidx.compose.foundation.isSystemInDarkTheme(), height = 88.dp) },
                        )
                    } else {
                        SatellitesApp(vm)
                    }
                }
            }
            com.churchdrive.app.ui.BrandSplash(com.churchdrive.app.ui.LogoKind.Satellites, "Satellite Manager")
        }
    }

    override fun onStart() {
        super.onStart()
        vm.onForeground()
    }
}

/** What the last crash was, to read out or copy, with a way to try the app again. */
@Composable
private fun CrashScreen(text: String, tryAgain: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(Modifier.statusBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("The app stopped last time", style = MaterialTheme.typography.titleLarge)
                Text("This is what it says. Send a screenshot of it, or copy it.", style = MaterialTheme.typography.bodyMedium)
                androidx.compose.foundation.layout.Box(Modifier.weight(1f).fillMaxWidth()) {
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        Text(text, style = MaterialTheme.typography.bodySmall, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            modifier = Modifier.verticalScroll(rememberScrollState()))
                    }
                }
                Button(onClick = {
                    val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("Crash", text))
                }, modifier = Modifier.fillMaxWidth()) { Text("Copy") }
                Button(onClick = tryAgain, modifier = Modifier.fillMaxWidth()) { Text("Try the app again") }
            }
        }
    }
}

