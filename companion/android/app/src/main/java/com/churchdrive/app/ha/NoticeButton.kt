package com.churchdrive.app.ha

/** What a notification button does when pressed. The house builds these (`notice_actions_logic.py`); calling and disarming are never among them. */
sealed class NoticeButton {
    /** Opens the app: a camera (`church-drive-camera:camera.x[?answer]`) or a page. */
    data class Link(val uri: String) : NoticeButton()

    /** Asks the house to do something (lights on, send the vacuum home...). */
    data class House(val verb: String) : NoticeButton()

    /** Silences this kind of notification on this phone for a while. */
    data class Mute(val minutes: Int) : NoticeButton()

    /** Brings this notification back on this phone after a while. */
    data class Snooze(val minutes: Int) : NoticeButton()
}

private val HOUSE_VERBS = setOf("lights_on", "dock", "all_off", "car_now", "car_pause", "backup", "car_yes", "car_no")

/** The button for a push action, or null for one this app does not do. */
fun parseNoticeButton(id: String?, uri: String?): NoticeButton? {
    if (id == "URI") return uri?.takeIf { it.isNotBlank() }?.let { NoticeButton.Link(it) }
    if (id == null || !id.startsWith("CD_")) return null
    val verb = id.removePrefix("CD_").substringBefore(':')
    val arg = id.substringAfter(':', "")
    return when {
        verb in HOUSE_VERBS -> NoticeButton.House(verb)
        verb == "mute" -> arg.toIntOrNull()?.takeIf { it in 1..1440 }?.let { NoticeButton.Mute(it) }
        verb == "snooze" -> arg.toIntOrNull()?.takeIf { it in 1..1440 }?.let { NoticeButton.Snooze(it) }
        else -> null
    }
}

/** The app page a link such as `/dashboard-mobile/security` opens, as a [com.churchdrive.app.ui.Page] name, or null. */
fun pageFromLink(uri: String?): String? {
    val slug = uri?.substringAfter("/dashboard-mobile/", "")?.substringBefore('?')?.substringBefore('/')?.takeIf { it.isNotBlank() } ?: return null
    return slug.replaceFirstChar { it.uppercase() }
}

/** Whether a kind is muted at [now], given when each mute ends. */
fun isMuted(until: Long?, now: Long): Boolean = until != null && until > now

/** The app's own connection to the house (with Android's "connected" notice) runs only when notifications are on and Google push doesn't have this phone yet. */
fun connectionNeeded(signedIn: Boolean, notifyOn: Boolean, googlePush: Boolean): Boolean = signedIn && notifyOn && !googlePush
