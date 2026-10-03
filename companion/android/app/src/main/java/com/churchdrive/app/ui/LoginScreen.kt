package com.churchdrive.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.churchdrive.app.ha.LoginStep

/**
 * First-run sign in with the person's normal Home Assistant username and password (and their two-step code if they use
 * one). A pasted long-lived access token is still there as the other way in.
 */
@Composable
fun LoginScreen(
    onSignIn: (url: String, token: String) -> Unit,
    onLogin: (url: String, username: String, password: String, onStep: (LoginStep) -> Unit) -> Unit,
    onCode: (code: String, onStep: (LoginStep) -> Unit) -> Unit,
) {
    var url by rememberSaveable { mutableStateOf("http://homeassistant.local:8123") }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var token by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var useToken by rememberSaveable { mutableStateOf(false) }
    var askCode by rememberSaveable { mutableStateOf(false) }
    var busy by rememberSaveable { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }

    // What Home Assistant answered: ask for the code, show why it failed, or (signed in) the screen changes by itself.
    val onStep: (LoginStep) -> Unit = { step ->
        busy = false
        when (step) {
            is LoginStep.Mfa -> { askCode = true; message = step.error }
            is LoginStep.Credentials -> { askCode = false; message = step.error }
            is LoginStep.Failed -> { askCode = false; message = step.message }
            is LoginStep.Done -> message = null
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, alignment = androidx.compose.ui.Alignment.CenterVertically),
    ) {
        Text("Church Drive", style = MaterialTheme.typography.headlineLarge)
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Home Assistant address") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            singleLine = true,
            enabled = !askCode,
            modifier = Modifier.fillMaxWidth(),
        )
        if (useToken) {
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                label = { Text("Access token") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        } else if (askCode) {
            OutlinedTextField(
                value = code,
                onValueChange = { code = it },
                label = { Text("Two-step code") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            onClick = {
                when {
                    useToken -> onSignIn(url, token)
                    askCode -> { busy = true; onCode(code, onStep) }
                    else -> { busy = true; message = null; onLogin(url, username, password, onStep) }
                }
            },
            enabled = !busy && url.isNotBlank() && when {
                useToken -> token.isNotBlank()
                askCode -> code.isNotBlank()
                else -> username.isNotBlank() && password.isNotBlank()
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (busy) "Signing in…" else "Sign in") }
        if (!askCode) {
            TextButton(onClick = { useToken = !useToken; message = null }) {
                Text(if (useToken) "Use my username and password" else "Use an access token instead")
            }
        }
    }
}
