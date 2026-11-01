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
        super.onCreate(savedInstanceState)
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
            com.churchdrive.app.ui.BrandSplash("Satellites", holdMillis = 3200) { dark -> com.churchdrive.app.ui.LogoMorph(com.churchdrive.app.ui.LogoKind.Satellites, dark) }
        }
    }

    override fun onStart() {
        super.onStart()
        vm.onForeground()
    }
}
