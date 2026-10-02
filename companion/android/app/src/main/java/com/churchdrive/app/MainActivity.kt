package com.churchdrive.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
                // The surface fills the whole window (behind the system bars); content is kept clear of them.
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                ) {
                    Box(modifier = Modifier.safeDrawingPadding()) {
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
    }
}
