package com.churchdrive.app.house

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.core.graphics.PathParser
import androidx.core.graphics.drawable.IconCompat
import com.churchdrive.app.ui.CdIcons

/**
 * The small icon of a notification, by the group its kind belongs to (safety, security, doorbell, car, energy, house, people, system, to-do,
 * shopping), drawn from the house's own icons in one colour. The titles carry no emoji, so this is what tells the areas apart at a glance in
 * the status bar and the shade. A kind with no group keeps the Manager's mark.
 */
object NoticeIcons {
    private const val SIZE = 96
    private val cache = HashMap<String, IconCompat?>()

    /** The house icon for a notice, or null for the Manager's own mark. [kind] is the notice's kind, [tag] its tag (reminders and lists have no kind). */
    fun iconNameFor(kind: String?, tag: String?): String? {
        val k = kind.orEmpty()
        val t = tag.orEmpty()
        return when {
            k in setOf("smoke", "heat", "co", "alarm_triggered", "safety_alarms") -> "fire-alert"
            k == "doorbell" -> "doorbell"
            k in setOf("door_away", "door_left_open", "alarm_mode", "tamper", "night_motion") -> "shield"
            k.startsWith("car_") -> "car-ev"
            k in setOf("power_down", "cheap_rate", "energy_cost") -> "bolt"
            k in setOf("vacuum", "vacuum_messages", "damp", "frost", "clive_actions") -> "home"
            k.startsWith("arrivals:") || k == "left_on" -> "person"
            k in setOf("internet", "backup", "remote_access", "device_stale", "devices_not_responding", "app_update", "battery_report", "low_batteries", "filters_due", "updates") -> "device-health"
            k.startsWith("todo_") || t.startsWith("church-drive-task") || t.startsWith("church-drive-todo") -> "todo"
            t.startsWith("shopping-") -> "cart"
            else -> null
        }
    }

    /** The icon as a one-colour bitmap for the status bar (null when the house has no such icon). */
    fun smallIcon(context: Context, kind: String?, tag: String?): IconCompat? {
        val name = iconNameFor(kind, tag) ?: return null
        return synchronized(cache) { cache.getOrPut(name) { render(name) } }
    }

    private fun render(name: String): IconCompat? {
        val shapes = CdIcons.icons[name] ?: return null
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; style = Paint.Style.FILL }
        val s = SIZE / 200f
        shapes.forEach { sh ->
            val path: Path = runCatching { PathParser.createPathFromPathData(sh.d) }.getOrNull() ?: return@forEach
            path.fillType = Path.FillType.EVEN_ODD
            canvas.save()
            canvas.scale(s, s)
            if (sh.scale != 1f) canvas.scale(sh.scale, sh.scale, 100f, 100f)
            canvas.drawPath(path, paint)
            canvas.restore()
        }
        return IconCompat.createWithBitmap(bitmap)
    }
}
