package com.churchdrive.app

import android.content.Context
import android.net.Uri
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable

/**
 * Gives a paired watch running Church Drive the phone's Home Assistant sign-in, so the watch needs no typing. The watch app has the
 * same application id and signing key, which is what lets Google's Data Layer hand it over: no other app can read it.
 * Signing out of the phone removes it, and the watch signs out too.
 */
object WearSync {
    const val PATH = "/church-drive/session"

    /** Sends the stored sign-in. Quiet if there is none, or no watch, or no Google services. */
    fun push(context: Context) {
        runCatching {
            val session = Session(context)
            val url = session.url ?: return
            val token = session.token ?: return
            val request = PutDataMapRequest.create(PATH).apply {
                dataMap.putString("url", url)
                dataMap.putString("token", token)
                session.refreshToken?.let { dataMap.putString("refresh", it) }
                session.personName?.let { dataMap.putString("person", it) }
                // So each push is a change, even when the sign-in itself is the same.
                dataMap.putLong("sent", System.currentTimeMillis())
            }.asPutDataRequest().setUrgent()
            Wearable.getDataClient(context.applicationContext).putDataItem(request)
        }
    }

    /** Removes the sign-in from the watch. */
    fun clear(context: Context) {
        runCatching {
            Wearable.getDataClient(context.applicationContext).deleteDataItems(Uri.Builder().scheme("wear").path(PATH).build())
        }
    }
}
