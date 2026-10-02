package com.churchdrive.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.churchdrive.app.ui.ChurchDriveTheme
import com.churchdrive.app.ui.HomeScreen
import com.churchdrive.app.ui.LoginScreen

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ChurchDriveTheme {
                val signedIn by vm.signedIn.collectAsStateWithLifecycle()
                val connection by vm.connection.collectAsStateWithLifecycle()
                val entities by vm.entities.collectAsStateWithLifecycle()
                if (signedIn) {
                    HomeScreen(
                        connection = connection,
                        alarm = entities[AppViewModel.ALARM_ENTITY],
                        onAlarm = vm::alarm,
                        onSignOut = vm::signOut,
                    )
                } else {
                    LoginScreen(onSignIn = vm::signIn)
                }
            }
        }
    }
}
