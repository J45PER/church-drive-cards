package com.churchdrive.app

import android.content.Context
import android.content.Intent
import android.net.Uri

/** Opens a page of Home Assistant itself: in the Home Assistant app if it is on the phone, else in the browser. */
object HomeAssistantLink {
    /** [path] is a Home Assistant page such as `/config/updates`, [baseAddress] the house's address. False when neither could open it. */
    fun open(context: Context, baseAddress: String?, path: String): Boolean {
        // The Home Assistant app understands homeassistant://navigate/<path>; where it isn't installed nothing handles that and the browser is used.
        val app = Intent(Intent.ACTION_VIEW, Uri.parse("homeassistant://navigate$path")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(app) }.isSuccess) return true
        val address = baseAddress?.takeIf { it.isNotBlank() } ?: return false
        val web = Intent(Intent.ACTION_VIEW, Uri.parse(address.trimEnd('/') + path)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(web) }.isSuccess
    }
}
