package com.churchdrive.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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

/**
 * The sign-in page of the smaller apps (To-Do, Satellites), one copy instead of one each: a title, a line of explanation, the Home Assistant
 * username and password (then the two-step code if the house asks for one), and the error. The house address is the house's own and is not
 * asked for; when [changeAddress] is on, press and hold the title to show it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SignInForm(
    title: String,
    blurb: String,
    busy: Boolean,
    needCode: Boolean,
    error: String?,
    changeAddress: Boolean,
    onLogin: (url: String, user: String, password: String) -> Unit,
    onCode: (code: String) -> Unit,
    /** Drawn above the title (Satellites puts its mark here). */
    header: @Composable () -> Unit = {},
) {
    var url by rememberSaveable { mutableStateOf(com.churchdrive.app.HouseAddress.CLOUD_URL) }
    var showAddress by rememberSaveable { mutableStateOf(false) }
    var user by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    Column(modifier = Modifier.statusBarsPadding().padding(horizontal = 16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 32.dp)) {
            header()
            Text(
                title, style = MaterialTheme.typography.headlineSmall,
                modifier = if (changeAddress) Modifier.combinedClickable(onClick = {}, onLongClick = { showAddress = !showAddress }) else Modifier,
            )
            Text(blurb)
            if (!needCode) {
                if (changeAddress && showAddress) OutlinedTextField(
                    url, { url = it }, label = { Text("Home Assistant address") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                OutlinedTextField(user, { user = it }, label = { Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    password, { password = it }, label = { Text("Password") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                Button(
                    onClick = { onLogin(url, user, password) },
                    enabled = !busy && url.isNotBlank() && user.isNotBlank() && password.isNotEmpty(),
                ) { Text(if (busy) "Signing in…" else "Sign in") }
            } else {
                OutlinedTextField(
                    code, { code = it }, label = { Text("Two-step code") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                )
                Button(onClick = { onCode(code) }, enabled = !busy && code.isNotBlank()) { Text(if (busy) "Checking…" else "Continue") }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}
