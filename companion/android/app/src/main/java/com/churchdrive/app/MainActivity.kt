package com.churchdrive.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.churchdrive.app.ui.ChurchDriveTheme
import com.churchdrive.app.ui.HomeScreen
import com.churchdrive.app.ui.LocalBaseUrl
import com.churchdrive.app.ui.LocalCameraHost
import com.churchdrive.app.ui.LocalHaApi
import com.churchdrive.app.ui.LocalHistory
import com.churchdrive.app.ui.LocalUserName
import com.churchdrive.app.ui.LocalSceneLooks
import com.churchdrive.app.ui.LocalTemplates
import com.churchdrive.app.ui.LoginScreen

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val lastCrash = CrashReport.take(this)
        CrashReport.install(this)
        com.churchdrive.app.ui.MdiAll.load(this)
        setContent {
            var crash by remember { mutableStateOf(lastCrash) }
            crash?.let { text ->
                val clipboard = LocalClipboardManager.current
                AlertDialog(
                    onDismissRequest = { crash = null },
                    title = { Text("The app crashed last time") },
                    text = { Text(text, fontSize = 11.sp, modifier = Modifier.verticalScroll(rememberScrollState())) },
                    confirmButton = { TextButton(onClick = { clipboard.setText(AnnotatedString(text)) }) { Text("Copy") } },
                    dismissButton = { TextButton(onClick = { crash = null }) { Text("Close") } },
                )
            }
            ChurchDriveTheme {
                // Edge to edge: the surface fills the window, and each screen keeps its content clear of the system bars.
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                ) {
                    Box {
                        val signedIn by vm.signedIn.collectAsStateWithLifecycle()
                        val connection by vm.connection.collectAsStateWithLifecycle()
                        val entities by vm.entities.collectAsStateWithLifecycle()
                        val userName by vm.userName.collectAsStateWithLifecycle()
                        val isAdmin by vm.isAdmin.collectAsStateWithLifecycle()
                        val updateAvailable by vm.updateAvailable.collectAsStateWithLifecycle()
                        val lights by vm.lights.collectAsStateWithLifecycle()
                        val sceneLooks by vm.sceneLooks.collectAsStateWithLifecycle()
                        val panels by vm.panels.collectAsStateWithLifecycle()
                        val registry by vm.registry.collectAsStateWithLifecycle()
                        val areaNames by vm.areaNames.collectAsStateWithLifecycle()
                        CompositionLocalProvider(
                            LocalSceneLooks provides sceneLooks,
                            LocalTemplates provides vm.templates,
                            LocalBaseUrl provides vm.baseUrl,
                            LocalCameraHost provides vm.cameraHost,
                            LocalHistory provides vm::history,
                            LocalHaApi provides vm.haApi,
                            LocalUserName provides userName,
                        ) {
                            if (signedIn) {
                                HomeScreen(
                                    connection = connection,
                                    entities = entities,
                                    userName = userName,
                                    isAdmin = isAdmin,
                                    updateAvailable = updateAvailable,
                                    lights = lights,
                                    areaNames = areaNames,
                                    panels = panels,
                                    registry = registry,
                                    call = vm::call,
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
}
