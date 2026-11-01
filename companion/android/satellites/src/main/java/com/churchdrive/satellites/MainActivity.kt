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

/** Church Drive Satellites: the house's voice satellites, each with its design, glances and backlight. */
class MainActivity : ComponentActivity() {
    private val vm: SatelliteModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ChurchDriveTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (!vm.signedIn) {
                        Column(modifier = Modifier.statusBarsPadding().padding(horizontal = 16.dp)) { SignIn(vm) }
                    } else {
                        SatellitesApp(vm)
                    }
                }
            }
            com.churchdrive.app.ui.BrandSplash("Satellites") { dark -> SatelliteMark(dark) }
        }
    }

    override fun onStart() {
        super.onStart()
        vm.onForeground()
    }
}

@Composable
private fun SignIn(vm: SatelliteModel) {
    var url by rememberSaveable { mutableStateOf(com.churchdrive.app.HouseAddress.CLOUD_URL) }
    var user by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 32.dp)) {
        SatelliteMark(androidx.compose.foundation.isSystemInDarkTheme(), height = 88.dp)
        Text("Church Drive Satellites", style = MaterialTheme.typography.headlineSmall)
        Text("Sign in with your Home Assistant login. If the Church Drive app is on this phone and signed in, this app uses its sign-in.")
        if (!vm.needCode) {
            OutlinedTextField(user, { user = it }, label = { Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                password, { password = it }, label = { Text("Password") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
            Button(onClick = { vm.login(url, user, password) }, enabled = !vm.busy && url.isNotBlank() && user.isNotBlank() && password.isNotEmpty()) { Text(if (vm.busy) "Signing in…" else "Sign in") }
        } else {
            OutlinedTextField(
                code, { code = it }, label = { Text("Two-step code") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            )
            Button(onClick = { vm.submitCode(code) }, enabled = !vm.busy && code.isNotBlank()) { Text(if (vm.busy) "Checking…" else "Continue") }
        }
        vm.loginError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
