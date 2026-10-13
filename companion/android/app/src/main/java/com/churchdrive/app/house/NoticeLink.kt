package com.churchdrive.app.house

import android.content.Context
import android.content.Intent
import com.churchdrive.app.MainActivity
import com.churchdrive.app.ui.Page
import com.churchdrive.app.ui.cameraOpenFromLink
import com.churchdrive.app.ui.shoppingIdFromLink

/**
 * Where tapping a notification (or one of its Open buttons) goes. The house writes the link:
 * `church-drive-camera:camera.x[?answer]`, `church-drive-device:<entity id>` (that device's page), `church-drive-health`
 * (the Device Health list), a shopping list's link, or `/dashboard-mobile/<page>`.
 */
sealed interface NoticeLink {
    data class Camera(val entityId: String, val answer: Boolean) : NoticeLink
    data class Shopping(val listId: String) : NoticeLink
    data class PageLink(val page: String) : NoticeLink
    data class Device(val ref: String) : NoticeLink
    data object Health : NoticeLink
}

const val DEVICE_LINK = "church-drive-device:"
const val HEALTH_LINK = "church-drive-health"

/** The app page (a [Page] name) a dashboard slug such as `lights`, `lighting` or `todo` means, or null (also for administrator pages). */
fun pageNameForSlug(slug: String?): String? {
    val s = slug?.lowercase()?.filter(Char::isLetter)?.takeIf { it.isNotEmpty() } ?: return null
    return Page.entries.firstOrNull { !it.adminOnly && (it.name.lowercase() == s || it.label.lowercase().filter(Char::isLetter) == s) }?.name
}

/** What a link means, or null when it isn't one the app can open. */
fun parseNoticeLink(url: String?): NoticeLink? {
    val link = url?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    cameraOpenFromLink(link)?.let { return NoticeLink.Camera(it.entityId, it.answer) }
    shoppingIdFromLink(link)?.let { return NoticeLink.Shopping(it) }
    if (link == HEALTH_LINK || link.startsWith("/battery-status")) return NoticeLink.Health
    if (link.startsWith(DEVICE_LINK)) {
        val ref = link.removePrefix(DEVICE_LINK).substringBefore('?').trim()
        return if (Regex("^[A-Za-z0-9_.\\-]{1,128}$").matches(ref)) NoticeLink.Device(ref) else NoticeLink.Health
    }
    val slug = link.substringAfter("/dashboard-mobile/", "").substringBefore('?').substringBefore('/')
    return pageNameForSlug(slug)?.let { NoticeLink.PageLink(it) }
}

/** The intent that opens the app at [link] (the same extras however it got here: a tap, a button or a widget). */
fun noticeIntent(context: Context, link: NoticeLink?): Intent = Intent(context, MainActivity::class.java).also { i ->
    when (link) {
        is NoticeLink.Camera -> i.putExtra("camera", link.entityId).putExtra("answer", link.answer)
        is NoticeLink.Shopping -> i.putExtra(MainActivity.EXTRA_SHOPPING_LIST, link.listId)
        is NoticeLink.PageLink -> i.putExtra("page", link.page)
        is NoticeLink.Device -> i.putExtra(MainActivity.EXTRA_DEVICE, link.ref)
        NoticeLink.Health -> i.putExtra(MainActivity.EXTRA_DEVICE_HEALTH, true)
        null -> Unit
    }
}

/** A request code for a link's PendingIntent, so two different links never share (and overwrite) one. */
fun noticeLinkCode(link: NoticeLink?): Int = link?.hashCode() ?: 0
