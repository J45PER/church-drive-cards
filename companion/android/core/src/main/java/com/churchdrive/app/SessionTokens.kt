package com.churchdrive.app

import com.churchdrive.app.ha.HaAuth
import com.churchdrive.app.ha.Refresh

/** A fresh access token (made from the refresh token when the sign-in has one), or the stored one. Blocking: call off the main thread. */
fun Session.freshToken(): String? {
    val base = url ?: return null
    val refresh = refreshToken ?: return token
    return when (val r = HaAuth.refresh(base, refresh)) {
        is Refresh.Fresh -> r.tokens.access.also { saveAccess(it) }
        else -> token
    }
}
