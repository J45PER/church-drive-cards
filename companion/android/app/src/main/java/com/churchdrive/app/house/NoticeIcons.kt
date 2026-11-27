package com.churchdrive.app.house

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import androidx.core.graphics.PathParser
import androidx.core.graphics.drawable.IconCompat
import com.churchdrive.app.ui.CdIcons
import com.churchdrive.app.ui.DISC_SCALE
import com.churchdrive.app.ui.GreggState
import com.churchdrive.app.ui.RING_RADIUS
import com.churchdrive.app.ui.RING_THICKNESS
import com.churchdrive.app.ui.glyphFor

/** An icon as the house sends it: a [base], optionally with a [badge] bottom-right, optionally with the [off] slash. See notice_icons_logic.py. */
data class IconSpec(val base: String, val badge: String? = null, val off: Boolean = false) {
    companion object {
        fun parse(text: String?): IconSpec? {
            val raw = text?.trim()?.lowercase().orEmpty()
            if (raw.isEmpty() || raw.length > 60) return null
            val off = raw.endsWith("/off")
            val body = raw.removeSuffix("/off")
            val parts = body.split("+")
            if (parts.size > 2 || parts.any { it.isBlank() }) return null
            return IconSpec(parts[0], parts.getOrNull(1), off)
        }
    }
}

/**
 * The picture of a notification, by its kind: the house sends the icon it has chosen for the kind (Manager can change it) in the notice itself,
 * and a notice with none gets the built-in one for its kind. The titles carry no emoji, so this is what tells the areas apart at a glance in
 * the status bar and the shade. An icon can carry a solid circle badge bottom-right (the symbol knocked out), as the satellites' icons do.
 */
object NoticeIcons {
    private const val SIZE = 96
    private val cache = HashMap<String, IconCompat?>()

    /** The built-in icon spec for a notice (the same picks as the house's notice_icons_logic.py), or null for the Manager's own mark. */
    fun iconNameFor(kind: String?, tag: String?): String? {
        val k = kind.orEmpty()
        val t = tag.orEmpty()
        return when {
            k == "smoke" || k == "safety_alarms" -> "smoke-detector-alert"
            k == "heat" -> "fire-alert"
            k == "co" -> "co-alert"
            k in setOf("alarm_triggered", "door_away", "door_left_open", "tamper") -> "shield-alert"
            k == "alarm_mode" -> "shield"
            k == "alarm_test" -> "shield-check"
            k == "doorbell" -> "doorbell"
            k == "night_motion" -> "cctv"
            k in setOf("car_plugged", "car_not_plugged") -> "car+ev-plug"
            k.startsWith("car_") -> "car-ev"
            k == "power_down" -> "octopus+power"
            k == "cheap_rate" || k == "energy_cost" -> "octopus+pound"
            k == "shopping_offer" -> "cart+clive"
            k == "vacuum" || k == "vacuum_messages" -> "gregg"
            k == "damp" -> "humidity"
            k == "frost" -> "snowflake-alert"
            k == "clive_actions" -> "thermostat+clive"
            k.startsWith("arrivals:") -> "home"
            k == "left_on" -> "home-alert"
            k == "internet" -> "network-off"
            k == "backup" -> "device-server+restore"
            k == "remote_access" -> "globe+link"
            k == "device_stale" || k == "devices_not_responding" -> "device-health"
            k == "app_update" -> "system-update"
            k == "battery_report" || k == "low_batteries" -> "battery-low"
            k == "filters_due" -> "air-filter"
            k == "updates" -> "update"
            k == "todo_overdue" -> "todo-clock-logo"
            k.startsWith("todo_") -> "todo"
            t.startsWith("inbox-") -> "todo-logo+alert-circle-solid"
            t.startsWith("church-drive-task") -> "todo-logo+pin"
            t.startsWith("church-drive-todo") -> "todo"
            t.startsWith("shopping-") -> "cart"
            else -> null
        }
    }

    /** What to draw for a notice: the house's [icon] when it sent one we can draw, else the built-in one for its kind. */
    fun specFor(kind: String?, tag: String?, icon: String?): String? {
        val sent = icon?.takeIf { drawable(IconSpec.parse(it)) }
        return sent ?: iconNameFor(kind, tag)?.takeIf { drawable(IconSpec.parse(it)) }
    }

    private val greggStates = mapOf("gregg-idle" to GreggState.Idle, "gregg-cleaning" to GreggState.Cleaning, "gregg-paused" to GreggState.Paused, "gregg-returning" to GreggState.Returning, "gregg-docked" to GreggState.Charged, "gregg-error" to GreggState.Error)
    private val ownBases = setOf("octopus", "todo-logo", "todo-clock-logo") + greggStates.keys
    private val ownBadges = setOf("clive")

    /** Whether every part of the spec is something we can draw. */
    fun drawable(spec: IconSpec?): Boolean {
        if (spec == null) return false
        val baseOk = spec.base in ownBases || CdIcons.icons.containsKey(spec.base)
        val badgeOk = spec.badge == null || spec.badge in ownBadges || CdIcons.icons.containsKey(spec.badge)
        return baseOk && badgeOk
    }

    /** The icon as a one-colour bitmap for the status bar (null when there is no icon for it). */
    fun smallIcon(context: Context, kind: String?, tag: String?, icon: String? = null): IconCompat? {
        val name = specFor(kind, tag, icon) ?: return null
        return synchronized(cache) { cache.getOrPut(name) { render(name) } }
    }

    private fun render(name: String): IconCompat? = bitmapOf(name, SIZE, 0, android.graphics.Color.WHITE)?.let { IconCompat.createWithBitmap(it) }

    private val largeCache = HashMap<String, Bitmap?>()

    /**
     * The picture on the notification's card: the icon on a transparent ground, in light ink on a dark shade and dark ink on a light one
     * (chosen when the notice is posted), where the Manager's own mark was a coloured square.
     */
    fun largeIcon(context: Context, kind: String?, tag: String?, icon: String? = null): Bitmap? {
        val name = specFor(kind, tag, icon) ?: return null
        val night = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        return synchronized(largeCache) {
            largeCache.getOrPut("$name|$night") { bitmapOf(name, LARGE, LARGE / 6, if (night) 0xFFF1F3F8.toInt() else 0xFF202124.toInt()) }
        }
    }

    private const val LARGE = 192

    private fun pathOf(d: String): Path? = runCatching { PathParser.createPathFromPathData(d) }.getOrNull()?.also { it.fillType = Path.FillType.EVEN_ODD }

    /** Draws the house icon [name] on the 200-unit grid (the caller has scaled the canvas). */
    private fun drawNamed(canvas: Canvas, name: String, paint: Paint) {
        CdIcons.icons[name]?.forEach { sh ->
            val path = pathOf(sh.d) ?: return@forEach
            canvas.save()
            if (sh.scale != 1f) canvas.scale(sh.scale, sh.scale, 100f, 100f)
            canvas.drawPath(path, paint)
            canvas.restore()
        }
    }

    /** Draws a base (a house icon or one of our own marks) on the 200-unit grid. */
    private fun drawBase(canvas: Canvas, base: String, paint: Paint) {
        when (base) {
            "octopus" -> pathOf(NoticeMarks.OCTOPUS_24)?.let { canvas.save(); canvas.translate(10f, 10f); canvas.scale(7.08f, 7.08f); canvas.drawPath(it, paint); canvas.restore() }
            "todo-logo" -> pathOf(NoticeMarks.TODO_LOGO)?.let { canvas.drawPath(it, paint) }
            "todo-clock-logo" -> pathOf(NoticeMarks.TODO_CLOCK_LOGO)?.let { canvas.drawPath(it, paint) }
            else -> greggStates[base]?.let { drawGregg(canvas, it, paint) } ?: drawNamed(canvas, base, paint)
        }
    }

    /** Gregg's status icon held still: the disc with its glyph cut out and the ring round it (turned parts for cleaning, paused and returning). */
    private fun drawGregg(canvas: Canvas, state: GreggState, paint: Paint) {
        pathOf(glyphFor(state))?.let { canvas.save(); canvas.scale(DISC_SCALE, DISC_SCALE, 100f, 100f); canvas.drawPath(it, paint); canvas.restore() }
        val stroke = Paint(paint).apply { style = Paint.Style.STROKE; strokeWidth = RING_THICKNESS; strokeCap = Paint.Cap.ROUND }
        val oval = RectF(100f - RING_RADIUS, 100f - RING_RADIUS, 100f + RING_RADIUS, 100f + RING_RADIUS)
        val cap = Math.toDegrees((RING_THICKNESS / 2f / RING_RADIUS).toDouble()).toFloat()
        fun arcs(start: Float, sweep: Float) { val s = sweep - 2 * cap; if (s > 0f) canvas.drawArc(oval, start + cap, s, false, stroke) }
        fun parts(count: Int, gap: Float, rotation: Float) { val span = 360f / count; for (i in 0 until count) arcs(rotation + i * span, span - gap) }
        when (state) {
            GreggState.Cleaning -> parts(3, 24f, 0f)
            GreggState.Paused -> parts(3, 24f, 20f)
            GreggState.Returning -> parts(2, 90f, 0f)
            else -> canvas.drawCircle(100f, 100f, RING_RADIUS, stroke)
        }
    }

    /**
     * Draws [spec] on a [size] square with the given [ink]. A badge is the To-Do clock's: a solid circle (a rounded square for the EV plug) 80
     * wide centred on (148, 148) of the 200 grid, the base drawn at 85% above and left of it with a gap cut around the badge, the symbol
     * knocked out of the circle. The off slash is the common one: a 14 wide round-capped bar on the diagonal, with a gap on its upper right.
     */
    internal fun bitmapOf(spec: String, size: Int, margin: Int, ink: Int): Bitmap? {
        val parsed = IconSpec.parse(spec)?.takeIf { drawable(it) } ?: return null
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; style = Paint.Style.FILL }
        val clear = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
        val s = (size - 2 * margin) / 200f
        val layer = canvas.saveLayer(null, null)
        canvas.translate(margin.toFloat(), margin.toFloat())
        canvas.scale(s, s)
        val badge = parsed.badge
        if (badge != null) {
            val ownLogoBase = parsed.base == "todo-logo"  // already cut away around the badge
            canvas.save()
            if (!ownLogoBase) canvas.concat(Matrix().apply { setScale(0.85f, 0.85f, 100f, 100f); postTranslate(-4f, -8f) })
            drawBase(canvas, parsed.base, paint)
            canvas.restore()
            val square = badge == "ev-plug"
            if (!ownLogoBase) {
                if (square) canvas.drawRoundRect(RectF(100f, 100f, 196f, 196f), 28.8f, 28.8f, clear)
                else canvas.drawCircle(148f, 148f, 48f, clear)
            }
            if (badge == "alert-circle-solid") {
                canvas.save(); canvas.translate(48f, 48f); canvas.scale(0.533f, 0.533f, 100f, 100f); drawNamed(canvas, badge, paint); canvas.restore()
            } else {
                if (square) canvas.drawRoundRect(RectF(108f, 108f, 188f, 188f), 24f, 24f, paint) else canvas.drawCircle(148f, 148f, 40f, paint)
                canvas.save()
                canvas.translate(48f, 48f)
                if (badge == "clive") {
                    canvas.scale(0.27f, 0.27f, 100f, 100f)
                    canvas.translate(10f, 10f); canvas.scale(1.73f, 1.73f)
                    pathOf(NoticeMarks.CLIVE_104)?.let { canvas.drawPath(it, clear) }
                } else {
                    canvas.scale(0.38f, 0.38f, 100f, 100f)
                    drawNamed(canvas, badge, clear)
                }
                canvas.restore()
            }
        } else {
            drawBase(canvas, parsed.base, paint)
            if (parsed.off) {
                val gap = Paint(clear).apply { style = Paint.Style.STROKE; strokeWidth = 25f; strokeCap = Paint.Cap.ROUND }
                canvas.drawLine(43.9f, 36.1f, 168.9f, 161.1f, gap)
                val bar = Paint(paint).apply { style = Paint.Style.STROKE; strokeWidth = 14f; strokeCap = Paint.Cap.ROUND }
                canvas.drawLine(40f, 40f, 165f, 165f, bar)
            }
        }
        canvas.restoreToCount(layer)
        return bitmap
    }
}
