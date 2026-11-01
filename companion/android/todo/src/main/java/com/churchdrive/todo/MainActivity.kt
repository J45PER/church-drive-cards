package com.churchdrive.todo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import com.churchdrive.app.ui.ChurchDriveTheme
import com.churchdrive.app.ui.SignInForm
import com.churchdrive.app.ui.TodoDeepLink

/** Church Drive To-Do: sign in to the house, pick a list, tick tasks off, add new ones. */
class MainActivity : ComponentActivity() {
    private val vm: TodoModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        readLink(intent)
        setContent {
            ChurchDriveTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (!vm.signedIn) {
                        SignInForm(
                            title = "Church Drive To-Do",
                            blurb = "Sign in with your Home Assistant login.",
                            busy = vm.busy, needCode = vm.needCode, error = vm.loginError, changeAddress = true,
                            onLogin = vm::login, onCode = vm::submitCode,
                        )
                    } else {
                        TodoApp(vm)
                    }
                }
            }
            com.churchdrive.app.ui.BrandSplash("To Do", holdMillis = 3200) { dark -> com.churchdrive.app.ui.LogoMorph(com.churchdrive.app.ui.LogoKind.Todo, dark) }
        }
    }

    /** The task the Church Drive app's Home page asked to open (its list and uid), if this intent has one. */
    private fun readLink(intent: android.content.Intent?) {
        val list = intent?.getStringExtra(TodoDeepLink.EXTRA_LIST) ?: return
        val uid = intent.getStringExtra(TodoDeepLink.EXTRA_UID) ?: return
        vm.openFromLink(list, uid)
        intent.removeExtra(TodoDeepLink.EXTRA_LIST)
        intent.removeExtra(TodoDeepLink.EXTRA_UID)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readLink(intent)
    }

    override fun onStart() {
        super.onStart()
        vm.onForeground()
    }
}
