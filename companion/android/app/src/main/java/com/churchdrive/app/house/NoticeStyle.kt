package com.churchdrive.app.house

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager

/** How a notification appears on this phone. Each one is its own Android notification channel, so Android's own settings can adjust it too. */
enum class NoticeStyle(val key: String, val label: String, val about: String, val channel: String) {
    Alert("alert", "Alert", "A pop-up with sound that rings through silent mode and Do Not Disturb.", "cd_alert"),
    Popup("popup", "Pop up", "A banner at the top of the screen, with sound.", "cd_popup"),
    PopupQuiet("popup_quiet", "Pop up quietly", "A banner at the top of the screen, with no sound or buzz.", "cd_popup_quiet"),
    Quiet("quiet", "Quiet", "Only in the notification shade: no banner and no sound.", "cd_quiet"),
    Off("off", "Off on this phone", "You will not see it on this phone.", "");

    companion object {
        fun of(key: String?): NoticeStyle? = entries.firstOrNull { it.key == key }
    }
}

/** What a notification does until the person chooses: what the house asked for (loud for a safety alarm, quiet for the quiet ones). */
fun defaultNoticeStyle(critical: Boolean, loud: Boolean, quiet: Boolean): NoticeStyle = when {
    critical || loud -> NoticeStyle.Alert
    quiet -> NoticeStyle.Quiet
    else -> NoticeStyle.Popup
}

/** The styles a kind offers: the loud one is for safety alarms only. */
fun noticeStylesFor(critical: Boolean): List<NoticeStyle> =
    if (critical) NoticeStyle.entries else NoticeStyle.entries.filter { it != NoticeStyle.Alert }

/** The person's choice of style for each kind of notification, kept on this phone. */
object NoticeStyles {
    private const val PREFS = "notice_styles"
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun chosen(context: Context, kind: String): NoticeStyle? = NoticeStyle.of(prefs(context).getString(kind, null))

    fun set(context: Context, kind: String, style: NoticeStyle?) {
        prefs(context).edit().apply { if (style == null) remove(kind) else putString(kind, style.key) }.apply()
    }

    /** The style a notification will use: the person's choice for its kind, else what the house asked for. */
    fun styleFor(context: Context, push: com.churchdrive.app.ha.Push): NoticeStyle =
        push.kind?.let { chosen(context, it) } ?: defaultNoticeStyle(push.loud, push.loud, push.quiet)

    /** Creates the channels (once; Android keeps what the person changes in its own settings afterwards). */
    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        fun make(style: NoticeStyle, name: String, importance: Int, setup: NotificationChannel.() -> Unit = {}) {
            if (manager.getNotificationChannel(style.channel) != null) return
            manager.createNotificationChannel(NotificationChannel(style.channel, name, importance).apply { description = style.about; setup() })
        }
        make(NoticeStyle.Alert, "Alerts (ring through silent)", NotificationManager.IMPORTANCE_HIGH) {
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
            )
            enableVibration(true)
            setBypassDnd(true)
        }
        make(NoticeStyle.Popup, "Pop-ups", NotificationManager.IMPORTANCE_HIGH)
        make(NoticeStyle.PopupQuiet, "Quiet pop-ups", NotificationManager.IMPORTANCE_HIGH) {
            setSound(null, null)
            enableVibration(false)
        }
        make(NoticeStyle.Quiet, "Quiet", NotificationManager.IMPORTANCE_LOW)
    }
}

/** Posts a sample notification in [style], so the person can see and hear how it will look. False when it can't (off, or notifications aren't allowed). */
fun sendNoticeTest(context: Context, kindName: String, style: NoticeStyle): Boolean {
    if (style == NoticeStyle.Off || !Permissions.canNotify(context)) return false
    NoticeStyles.ensureChannels(context)
    val notice = androidx.core.app.NotificationCompat.Builder(context, style.channel)
        .setSmallIcon(com.churchdrive.app.R.drawable.ic_notification)
        .setContentTitle("Test: $kindName")
        .setContentText("This is how it will look on this phone.")
        .setCategory(if (style == NoticeStyle.Alert) androidx.core.app.NotificationCompat.CATEGORY_ALARM else androidx.core.app.NotificationCompat.CATEGORY_MESSAGE)
        .setAutoCancel(true)
        .build()
    return runCatching { androidx.core.app.NotificationManagerCompat.from(context).notify("church-drive-test", 4299, notice) }.isSuccess
}

/**
 * Asks the house to send this person a pretend notice of [kind]: it comes back through the real path, so it has the real
 * buttons and picture and is shown in the style the person chose for it. False when the house can't be reached. Blocking.
 */
fun askHouseForNoticeTest(context: Context, kind: String): Boolean {
    val session = com.churchdrive.app.Session(context)
    val url = session.url ?: return false
    val webhook = House.ensureRegistered(context) ?: return false
    val who = session.personName?.substringBefore(' ')
    return com.churchdrive.app.ha.MobileApp.post(url, webhook, com.churchdrive.app.ha.MobileApp.noticeTestBody(kind, who)) == com.churchdrive.app.ha.WebhookResult.Ok
}

/** Opens Android's own settings for the channel [style] uses (or the app's notification settings for Off). */
fun openAndroidNoticeSettings(context: Context, style: NoticeStyle) {
    NoticeStyles.ensureChannels(context)
    val intent = if (style.channel.isNotEmpty()) {
        android.content.Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, style.channel)
    } else {
        android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
    }.putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}
