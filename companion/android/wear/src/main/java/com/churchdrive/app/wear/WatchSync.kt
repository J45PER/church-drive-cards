package com.churchdrive.app.wear

import android.content.Context
import android.net.Uri
import com.churchdrive.app.Session
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService

/** Where the phone app puts the sign-in (see the phone's WearSync). */
const val SESSION_PATH = "/church-drive/session"

/** Stores a sign-in the phone sent. */
private fun apply(context: Context, item: DataItem) {
    val map = DataMapItem.fromDataItem(item).dataMap
    val url = map.getString("url") ?: return
    val token = map.getString("token") ?: return
    val refresh = map.getString("refresh")
    val session = Session(context)
    if (refresh != null) session.saveSignIn(url, token, refresh) else session.save(url, token)
    map.getString("person")?.let { session.savePerson(it) }
}

/** Receives the sign-in when the phone sends it, or its removal when the phone signs out. */
class SessionListener : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        for (event in events) {
            if (event.dataItem.uri.path != SESSION_PATH) continue
            if (event.type == DataEvent.TYPE_DELETED) {
                Session(applicationContext).clear()
                WatchHa.disconnect()
            } else {
                runCatching { apply(applicationContext, event.dataItem) }
            }
        }
    }
}

/** Asks for the sign-in the phone already sent (in case this app was not running when it arrived). Blocking: call off the main thread. */
fun pullSession(context: Context) {
    runCatching {
        val buffer = Tasks.await(Wearable.getDataClient(context).getDataItems(Uri.Builder().scheme("wear").path(SESSION_PATH).build()))
        try {
            for (item in buffer) apply(context.applicationContext, item)
        } finally {
            buffer.release()
        }
    }
}
