package com.churchdrive.todo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.churchdrive.app.ui.TodoItem
import com.churchdrive.app.ui.taskLine

/** Church Drive To-Do: sign in to the house, pick a list, tick tasks off, add new ones. */
class MainActivity : ComponentActivity() {
    private val vm: TodoModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.statusBarsPadding().padding(horizontal = 16.dp)) {
                        if (!vm.signedIn) SignIn(vm) else Lists(vm)
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        vm.onForeground()
    }
}

@Composable
private fun SignIn(vm: TodoModel) {
    var url by rememberSaveable { mutableStateOf("http://homeassistant.local:8123") }
    var user by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 32.dp)) {
        Text("Church Drive To-Do", style = MaterialTheme.typography.headlineSmall)
        Text("Sign in with your Home Assistant login.")
        if (!vm.needCode) {
            OutlinedTextField(url, { url = it }, label = { Text("Home Assistant address") }, singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
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

@Composable
private fun Lists(vm: TodoModel) {
    val open = vm.openList
    BackHandler(enabled = open != null) { vm.open(null) }
    if (open == null) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
            Text("To-Do", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            if (!vm.borrowed) TextButton(onClick = { vm.signOut() }) { Text("Sign out") }
        }
        if (!vm.connected) Text("Connecting to the house…", modifier = Modifier.padding(vertical = 8.dp))
        LazyColumn {
            items(vm.lists, key = { it.first }) { list ->
                Text(list.second, style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth().clickable { vm.open(list) }.padding(vertical = 16.dp))
                HorizontalDivider()
            }
        }
        return
    }

    var text by rememberSaveable(open.first) { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
        TextButton(onClick = { vm.open(null) }) { Text("Back") }
        Text(open.second, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 8.dp))
    }
    OutlinedTextField(
        text, { text = it }, label = { Text("Add a task") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { vm.add(text); text = "" }),
    )
    vm.problem?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
    val all = vm.items
    if (all == null) {
        Text("Loading…", modifier = Modifier.padding(top = 16.dp))
        return
    }
    val (done, todo) = all.partition { it.done }
    LazyColumn(modifier = Modifier.padding(top = 8.dp)) {
        if (todo.isEmpty()) item { Text("Nothing to do", modifier = Modifier.padding(vertical = 16.dp)) }
        items(todo, key = { it.uid }) { Task(it) { checked -> vm.setDone(it, checked) } }
        if (done.isNotEmpty()) {
            item { Text("Done", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 24.dp, bottom = 4.dp)) }
            items(done, key = { it.uid }) { Task(it) { checked -> vm.setDone(it, checked) } }
        }
    }
}

@Composable
private fun Task(item: TodoItem, onChecked: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onChecked(!item.done) }) {
        Checkbox(checked = item.done, onCheckedChange = onChecked)
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Text(item.summary)
            val line = taskLine(item)
            if (line.isNotBlank()) Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
