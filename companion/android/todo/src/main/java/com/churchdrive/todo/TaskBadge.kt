package com.churchdrive.todo

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * The number of tasks due today and overdue on the app's icon: a quiet, silent notification that carries the number (the launcher shows it
 * as a dot or a count). It is posted when the app reads the lists and taken away when nothing is due or the person turns it off.
 */
object TaskBadge {
    private const val CHANNEL = "task_count"
    private const val ID = 4101

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun update(context: Context, count: Int, on: Boolean) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (!on || count <= 0 || !canPost(context)) {
            manager.cancel(ID)
            return
        }
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Tasks due", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "The number of tasks due today and overdue, shown on the app icon."
                    setShowBadge(true)
                    setSound(null, null)
                    enableVibration(false)
                },
            )
        }
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            android.app.PendingIntent.getActivity(context, 0, it, android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val text = if (count == 1) "1 task due today or overdue" else "$count tasks due today or overdue"
        manager.notify(
            ID,
            NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_agenda)
                .setContentTitle(text)
                .setNumber(count)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setBadgeIconType(NotificationCompat.BADGE_ICON_NONE)
                .setContentIntent(open)
                .build(),
        )
    }
}
