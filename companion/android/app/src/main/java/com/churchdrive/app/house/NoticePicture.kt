package com.churchdrive.app.house

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.content.Context
import com.churchdrive.app.Session
import com.churchdrive.app.ha.HaAuth
import com.churchdrive.app.ha.Refresh
import java.net.HttpURLConnection
import java.net.URL

/** The picture on a doorbell or movement notification: the camera's latest snapshot, fetched from the house with this phone's login. */
object NoticePicture {
    private const val MAX_WIDTH = 1024

    /** The house path to fetch for a notification's `image`, or null: only camera pictures from the house are ever fetched with the login. */
    fun path(image: String?): String? =
        image?.trim()?.takeIf { Regex("^/api/camera_proxy/camera\\.[a-z0-9_]+$").matches(it) }

    /** The picture, or null if it can't be had. Blocking. */
    fun load(context: Context, path: String): Bitmap? = runCatching {
        val session = Session(context)
        val base = session.url?.trim()?.trimEnd('/') ?: return null
        val refresh = session.refreshToken
        val token = if (refresh == null) session.token else when (val r = HaAuth.refresh(base, refresh)) {
            is Refresh.Fresh -> r.tokens.access.also { session.saveAccess(it) }
            else -> session.token
        } ?: return null
        val connection = URL(base + path).openConnection() as HttpURLConnection
        connection.connectTimeout = 8_000
        connection.readTimeout = 8_000
        connection.setRequestProperty("Authorization", "Bearer $token")
        val bitmap = connection.inputStream.use { BitmapFactory.decodeStream(it) } ?: return null
        if (bitmap.width <= MAX_WIDTH) bitmap
        else Bitmap.createScaledBitmap(bitmap, MAX_WIDTH, bitmap.height * MAX_WIDTH / bitmap.width, true)
    }.getOrNull()
}
