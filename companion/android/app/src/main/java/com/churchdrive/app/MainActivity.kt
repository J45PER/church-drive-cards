package com.churchdrive.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
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
import com.churchdrive.app.ui.AccountSettings
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

    /** The page a widget asked for, until the app has opened it. */
    private var openPage by mutableStateOf<String?>(null)

    /** The camera a widget asked to see live, until it is closed. */
    private var openCamera by mutableStateOf<String?>(null)

    /** The camera was opened from a doorbell notification's Answer. */
    private var openCameraAnswer by mutableStateOf(false)

    /** Text shared or highlighted in another app, until it has been sent to be read for tasks. */
    private var shareText by mutableStateOf<String?>(null)
    private var shareSource = "Share"
    private var shareAutoAdd by mutableStateOf(false)

    /** The shopping list a reminder asked to open, until it is closed. */
    private var openShopping by mutableStateOf<String?>(null)

    /** Device Health is open (a notification, a long press on a card or the account panel opened it), on [openDevice] if it names one. */
    private var deviceHealthOpen by mutableStateOf(false)
    private var openDevice by mutableStateOf<String?>(null)

    /** The charger widget's Start charge asked the app to ask: "Charge at 00:30" or "Charge now". */
    private var chargePrompt by mutableStateOf(false)

    /** True each time the app comes to the front, to show tasks waiting to be checked (from the "Tasks to check" notice or a voice request). */
    private var showInbox by mutableStateOf(false)

    private fun readShare(intent: android.content.Intent?) {
        intent?.getStringExtra(EXTRA_SHARE_TEXT)?.let {
            shareText = it
            shareSource = intent.getStringExtra(EXTRA_SHARE_SOURCE) ?: "Share"
            shareAutoAdd = intent.getBooleanExtra(EXTRA_SHARE_AUTO_ADD, false)
            intent.removeExtra(EXTRA_SHARE_TEXT)
        }
        intent?.getStringExtra(EXTRA_SHOPPING_LIST)?.let {
            openShopping = it
            intent.removeExtra(EXTRA_SHOPPING_LIST)
        }
        intent?.getStringExtra(EXTRA_DEVICE)?.let {
            openDevice = it
            deviceHealthOpen = true
            intent.removeExtra(EXTRA_DEVICE)
        }
        if (intent?.getBooleanExtra(EXTRA_DEVICE_HEALTH, false) == true) {
            openDevice = null
            deviceHealthOpen = true
            intent.removeExtra(EXTRA_DEVICE_HEALTH)
        }
        if (intent?.getBooleanExtra("charge_prompt", false) == true) {
            chargePrompt = true
            intent.removeExtra("charge_prompt")
        }
    }

    override fun onStart() {
        super.onStart()
        vm.onForeground()
        showInbox = true
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openPage = intent.getStringExtra("page")
        openCamera = intent.getStringExtra("camera")
        openCameraAnswer = intent.getBooleanExtra("answer", false)
        readShare(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // The splash screen (the icon on its dark background) shows until the first screen is ready; this also puts the normal theme back.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        openPage = intent?.getStringExtra("page")
        openCamera = intent?.getStringExtra("camera")
        openCameraAnswer = intent?.getBooleanExtra("answer", false) == true
        readShare(intent)
        val lastCrash = CrashReport.take(this)
        CrashReport.install(this)
        com.churchdrive.app.ha.IconPack.prepare()
        com.churchdrive.app.ui.MdiAll.load(this)
        com.churchdrive.app.ui.AssistWakeSetting.load(this)
        setContent {
            var crash by remember { mutableStateOf(lastCrash) }
            // The crash report is for administrators only.
            val admin by vm.isAdmin.collectAsStateWithLifecycle()
            crash?.takeIf { admin }?.let { text ->
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
                        val statesLoaded by vm.statesLoaded.collectAsStateWithLifecycle()
                        val updateAvailable by vm.updateAvailable.collectAsStateWithLifecycle()
                        val notifyOn by vm.notifyOn.collectAsStateWithLifecycle()
                        val locationOn by vm.locationOn.collectAsStateWithLifecycle()
                        val lights by vm.lights.collectAsStateWithLifecycle()
                        val sceneLooks by vm.sceneLooks.collectAsStateWithLifecycle()
                        val panels by vm.panels.collectAsStateWithLifecycle()
                        val managerPanels by vm.managerPanels.collectAsStateWithLifecycle()
                        val registry by vm.registry.collectAsStateWithLifecycle()
                        val areaNames by vm.areaNames.collectAsStateWithLifecycle()
                        val jarvis = remember { com.churchdrive.app.ui.JarvisController() }
                        CompositionLocalProvider(
                            com.churchdrive.app.ui.LocalJarvis provides jarvis,
                            LocalSceneLooks provides sceneLooks,
                            LocalTemplates provides vm.templates,
                            LocalBaseUrl provides vm.baseUrl,
                            LocalCameraHost provides vm.cameraHost,
                            LocalHistory provides vm::history,
                            com.churchdrive.app.ui.LocalOpenDevice provides { ref -> openDevice = ref; deviceHealthOpen = true },
                            LocalHaApi provides vm.haApi,
                            LocalUserName provides userName,
                            com.churchdrive.app.ui.LocalAreaNames provides areaNames,
                            com.churchdrive.app.ui.LocalCanRetrySignIn provides vm.canRetrySignIn,
                        ) {
                            if (signedIn) {
                                HomeScreen(
                                    connection = connection,
                                    entities = entities,
                                    userName = userName,
                                    isAdmin = isAdmin,
                                    statesLoaded = statesLoaded,
                                    updateAvailable = updateAvailable,
                                    account = AccountSettings(
                                        notifyOn = notifyOn,
                                        locationOn = locationOn,
                                        onNotify = vm::setNotifications,
                                        onLocation = vm::setLocation,
                                        notifyService = vm.notifyService,
                                        onCheckUpdate = vm::checkUpdateNow,
                                    ),
                                    lights = lights,
                                    areaNames = areaNames,
                                    panels = panels,
                                    managerPanels = managerPanels,
                                    registry = registry,
                                    call = vm::call,
                                    onSignOut = vm::signOut,
                                    openPage = openPage,
                                    onOpened = { openPage = null },
                                    openCamera = openCamera,
                                    openCameraAnswer = openCameraAnswer,
                                    onCameraClosed = { openCamera = null; openCameraAnswer = false },
                                    onDeviceHealth = { openDevice = null; deviceHealthOpen = true },
                                )
                                if (deviceHealthOpen && statesLoaded) {
                                    com.churchdrive.app.ui.DeviceHealthOverlay(openDevice, entities, registry, areaNames) { deviceHealthOpen = false; openDevice = null }
                                }
                                openShopping?.let { com.churchdrive.app.ui.ShoppingScreen(it) { openShopping = null } }
                                if (chargePrompt) {
                                    com.churchdrive.app.ui.ChargePromptHost(entities, statesLoaded, vm::call) { chargePrompt = false }
                                }
                                // Text sent from another app, or tasks waiting from earlier: checked here before they go on a list.
                                com.churchdrive.app.ui.AssistWakeHost(
                                    controller = jarvis,
                                    connected = connection == com.churchdrive.app.ha.ConnectionState.Connected,
                                    base = vm.baseUrl, api = vm.haApi, userName = userName.orEmpty(),
                                    onTask = { said -> shareText = said; shareSource = "Voice"; shareAutoAdd = true },
                                )
                                if (shareText != null || showInbox) {
                                    com.churchdrive.app.ui.InboxDialog(shareText, shareSource, shareAutoAdd) { shareText = null; showInbox = false }
                                }
                            } else {
                                LoginScreen(onSignIn = vm::signIn, onLogin = vm::login, onCode = vm::loginCode)
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_SHOPPING_LIST = "shopping_list"
        /** A notification asked for a device's health page (an entity id or a device id), or for the Device Health list. */
        const val EXTRA_DEVICE = "device"
        const val EXTRA_DEVICE_HEALTH = "device_health"
        const val EXTRA_SHARE_TEXT = "share_text"
        const val EXTRA_SHARE_SOURCE = "share_source"
        const val EXTRA_SHARE_AUTO_ADD = "share_auto_add"
    }
}
