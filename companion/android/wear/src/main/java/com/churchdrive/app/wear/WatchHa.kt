package com.churchdrive.app.wear

import android.content.Context
import com.churchdrive.app.Session
import com.churchdrive.app.freshToken
import com.churchdrive.app.ha.ConnectionState
import com.churchdrive.app.ha.HaClient
import com.churchdrive.app.ha.asApi
import com.churchdrive.app.ui.HaApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The watch's one line to the house. It is a light connection (no entity states, no dashboard events): each screen asks for what it
 * needs, such as the scenes or a to-do list. [ensure] connects with the stored sign-in, first making a fresh access token.
 */
object WatchHa {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val client = HaClient(scope)
    val api: HaApi = client.asApi()

    private var lastStart = 0L

    /** Connects if not connected (and not already trying). Safe to call as often as a screen likes. */
    fun ensure(context: Context) {
        val session = Session(context.applicationContext)
        if (!session.signedIn) return
        val state = client.connection.value
        val now = System.currentTimeMillis()
        if (state == ConnectionState.Connected) return
        if (state == ConnectionState.Connecting && now - lastStart < 15_000) return
        lastStart = now
        scope.launch(Dispatchers.IO) {
            val url = session.url ?: return@launch
            val token = session.freshToken() ?: return@launch
            withContext(Dispatchers.Main) { client.connect(url, token, light = true) }
        }
    }

    /** Signed out on the phone: drop the line. */
    fun disconnect() {
        client.disconnect()
    }
}
