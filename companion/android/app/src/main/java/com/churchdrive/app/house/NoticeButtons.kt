package com.churchdrive.app.house

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import com.churchdrive.app.Session
import com.churchdrive.app.ha.MobileApp
import com.churchdrive.app.ha.WebhookResult
import com.churchdrive.app.ha.isMuted
import org.json.JSONObject
import kotlin.concurrent.thread

const val EXTRA_NOTICE_VERB = "notice_verb"
const val EXTRA_NOTICE_KIND = "notice_kind"
const val EXTRA_NOTICE_MINUTES = "notice_minutes"
const val EXTRA_NOTICE_EVENT = "notice_event"

/** Kinds this phone has silenced for a while (the Mute button), kept on the phone only. */
object NoticeMutes {
    private fun prefs(context: Context) = context.getSharedPreferences("notice_mutes", Context.MODE_PRIVATE)

    fun mute(context: Context, kind: String, minutes: Int) {
        prefs(context).edit().putLong(kind, System.currentTimeMillis() + minutes * 60_000L).apply()
    }

    fun isMuted(context: Context, kind: String?): Boolean =
        kind != null && isMuted(prefs(context).takeIf { it.contains(kind) }?.getLong(kind, 0), System.currentTimeMillis())
}

/** The number of the last notice this phone has seen from the house, so it can ask for what it missed. */
object NoticeSeen {
    private fun prefs(context: Context) = context.getSharedPreferences("notice_seen", Context.MODE_PRIVATE)

    fun last(context: Context): Long = prefs(context).getLong("seq", 0)

    fun save(context: Context, seq: Long) {
        if (seq > last(context)) prefs(context).edit().putLong("seq", seq).apply()
    }
}

/** A notice the house kept for this phone, as an event [HouseService.postNotice] understands. */
fun missedNoticeEvent(notice: JSONObject): JSONObject =
    JSONObject().put("title", notice.optString("title")).put("message", notice.optString("message")).put("data", notice.optJSONObject("data") ?: JSONObject())

private fun clearNotice(context: Context, intent: Intent) {
    val id = intent.getIntExtra(EXTRA_NOTICE_ID, 0)
    runCatching { NotificationManagerCompat.from(context).cancel(intent.getStringExtra(EXTRA_NOTICE_TAG), id) }
}

private fun toast(context: Context, text: String) {
    android.os.Handler(android.os.Looper.getMainLooper()).post { Toast.makeText(context, text, Toast.LENGTH_LONG).show() }
}

/** A button that asks the house to do something. It needs the phone unlocked (set on the button), so a passer-by can't press it. */
class NoticeActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val verb = intent.getStringExtra(EXTRA_NOTICE_VERB) ?: return
        clearNotice(context, intent)
        val pending = goAsync()
        val app = context.applicationContext
        thread {
            try {
                val session = Session(app)
                val url = session.url
                val webhook = if (url != null) House.ensureRegistered(app) else null
                val ok = url != null && webhook != null &&
                    MobileApp.post(url, webhook, MobileApp.noticeActionBody(verb, session.personName?.substringBefore(' '))) == WebhookResult.Ok
                if (!ok) toast(app, "Couldn't do that. Try again from the app.")
            } finally {
                pending.finish()
            }
        }
    }
}

/** The Mute button: silences that kind on this phone for a while. */
class NoticeMuteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val kind = intent.getStringExtra(EXTRA_NOTICE_KIND) ?: return
        val minutes = intent.getIntExtra(EXTRA_NOTICE_MINUTES, 60)
        NoticeMutes.mute(context, kind, minutes)
        clearNotice(context, intent)
        toast(context, "Muted for ${if (minutes % 60 == 0) "${minutes / 60} hour" + (if (minutes > 60) "s" else "") else "$minutes minutes"}")
    }
}

/** The Snooze button: takes the notice away and brings it back after a while. */
class NoticeSnoozeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = intent.getStringExtra(EXTRA_NOTICE_EVENT) ?: return
        if (intent.hasExtra(EXTRA_NOTICE_MINUTES) && intent.getBooleanExtra(EXTRA_SNOOZE_NOW, false).not()) {
            // Pressed on the notification: schedule it.
            val minutes = intent.getIntExtra(EXTRA_NOTICE_MINUTES, 10)
            clearNotice(context, intent)
            val again = Intent(context, NoticeSnoozeReceiver::class.java).putExtra(EXTRA_NOTICE_EVENT, event).putExtra(EXTRA_SNOOZE_NOW, true)
            val pending = PendingIntent.getBroadcast(context, event.hashCode(), again, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + minutes * 60_000L, pending)
            toast(context, "Snoozed for $minutes minutes")
        } else {
            // The alarm went off: show it again.
            runCatching { HouseService.postNotice(context, JSONObject(event), dedupe = false) }
        }
    }
}

private const val EXTRA_SNOOZE_NOW = "snooze_now"
