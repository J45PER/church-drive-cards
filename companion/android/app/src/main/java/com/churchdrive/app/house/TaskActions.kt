package com.churchdrive.app.house

import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import com.churchdrive.app.Session
import com.churchdrive.app.ha.MobileApp
import com.churchdrive.app.ha.SNOOZE_MINUTES
import com.churchdrive.app.ha.TaskAction
import com.churchdrive.app.ha.WebhookResult
import com.churchdrive.app.ha.parseTaskAction
import com.churchdrive.app.ha.snoozeLabel
import kotlin.concurrent.thread

const val EXTRA_TASK_ACTION = "task_action"
const val EXTRA_NOTICE_TAG = "notice_tag"
const val EXTRA_NOTICE_ID = "notice_id"

/** Tells the house which button was pressed on a task reminder. Blocking. */
fun sendTaskAction(context: Context, action: TaskAction, kind: String, minutes: Int? = null): Boolean {
    val session = Session(context)
    val url = session.url ?: return false
    val webhook = House.ensureRegistered(context) ?: return false
    return MobileApp.post(url, webhook, MobileApp.taskActionBody(action, kind, minutes)) == WebhookResult.Ok
}

private fun clearNotice(context: Context, intent: Intent) {
    val id = intent.getIntExtra(EXTRA_NOTICE_ID, 0)
    runCatching { NotificationManagerCompat.from(context).cancel(intent.getStringExtra(EXTRA_NOTICE_TAG), id) }
}

/** The Done button on a task reminder: ticks the task off in the house. */
class TaskActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = parseTaskAction(intent.getStringExtra(EXTRA_TASK_ACTION)) ?: return
        clearNotice(context, intent)
        val pending = goAsync()
        val app = context.applicationContext
        thread {
            try {
                val ok = sendTaskAction(app, action, "done")
                if (!ok) android.os.Handler(android.os.Looper.getMainLooper()).post {
                    Toast.makeText(app, "Couldn't tick that off. Try again from the app.", Toast.LENGTH_LONG).show()
                }
            } finally {
                pending.finish()
            }
        }
    }
}

/** The Snooze button on a task reminder: a small box to choose how long, then the house brings the reminder back. */
class SnoozeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val action = parseTaskAction(intent.getStringExtra(EXTRA_TASK_ACTION))
        if (action == null) {
            finish()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Snooze for")
            .setItems(SNOOZE_MINUTES.map(::snoozeLabel).toTypedArray()) { _, which ->
                val minutes = SNOOZE_MINUTES[which]
                clearNotice(this, intent)
                val app = applicationContext
                thread {
                    val ok = sendTaskAction(app, action, "snooze", minutes)
                    runOnUiThread {
                        Toast.makeText(app, if (ok) "Snoozed for ${snoozeLabel(minutes)}" else "Couldn't snooze. Try again.", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            }
            .setOnCancelListener { finish() }
            .show()
    }
}
