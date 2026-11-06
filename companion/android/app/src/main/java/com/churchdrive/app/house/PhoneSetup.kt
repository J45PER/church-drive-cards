package com.churchdrive.app.house

import android.content.Context

/** One thing the phone must allow for the house to know who is home and to reach them. In the order they are asked. */
enum class SetupStep(val key: String) {
    Notifications("notifications"),
    Location("location"),
    PreciseLocation("precise_location"),
    AlwaysLocation("always_location"),
    Battery("battery"),
}

/** What the phone allows right now, and what the person has switched on in the app. */
data class SetupState(
    val notifyOn: Boolean,
    val canNotify: Boolean,
    val locationOn: Boolean,
    val canLocate: Boolean,
    val precise: Boolean,
    val always: Boolean,
    val batteryFree: Boolean,
)

/**
 * The phone's set-up check. The house can only tell who is home if the phone keeps reporting where it is, and Android
 * stops that quietly unless location is precise and "all the time" and the app's battery use is unrestricted, so each of those is
 * asked for, again, whenever one is missing.
 */
object PhoneSetup {
    /** What is not yet right, in the order to ask (location before its refinements: Android asks "all the time" only after "while in use"). */
    fun missing(s: SetupState): List<SetupStep> = buildList {
        if (!s.notifyOn || !s.canNotify) add(SetupStep.Notifications)
        if (!s.locationOn || !s.canLocate) add(SetupStep.Location)
        if (!s.precise) add(SetupStep.PreciseLocation)
        if (!s.always) add(SetupStep.AlwaysLocation)
        if (!s.batteryFree) add(SetupStep.Battery)
    }

    /** What the phone allows now. */
    fun read(context: Context, notifyOn: Boolean, locationOn: Boolean) = SetupState(
        notifyOn = notifyOn,
        canNotify = Permissions.canNotify(context),
        locationOn = locationOn,
        canLocate = Permissions.canLocate(context),
        precise = Permissions.canLocatePrecisely(context),
        always = Permissions.canLocateInBackground(context),
        batteryFree = Permissions.batteryUnrestricted(context),
    )

    /** The question opens by itself once for each version of the app that finds something missing; the banner stays until it is put right. */
    fun dialogDue(askedVersion: String?, version: String, missing: List<SetupStep>): Boolean =
        missing.isNotEmpty() && askedVersion != version

    /** The update question opens by itself at most once a day while an update is waiting. */
    fun updateDialogDue(lastAskedMs: Long, nowMs: Long, gapMs: Long = DAY_MS): Boolean = lastAskedMs == 0L || nowMs - lastAskedMs >= gapMs

    /** A step the phone has already been asked once, this time round and still refused: Android won't show its own question again, so send the person to the app's settings. */
    fun needsSettings(asked: Int): Boolean = asked >= 1

    fun title(step: SetupStep): String = when (step) {
        SetupStep.Notifications -> "Allow notifications"
        SetupStep.Location -> "Share your location"
        SetupStep.PreciseLocation -> "Use precise location"
        SetupStep.AlwaysLocation -> "Allow location all the time"
        SetupStep.Battery -> "Don't limit the app's battery use"
    }

    fun why(step: SetupStep): String = when (step) {
        SetupStep.Notifications -> "So the house can reach you: the doorbell, the alarm, and who has got home."
        SetupStep.Location -> "So the house knows when you are home, at work or out. It is also how the heating knows the house is empty."
        SetupStep.PreciseLocation -> "Approximate location can put you streets away from where you are, so the house gets arrivals and departures wrong. Choose Precise."
        SetupStep.AlwaysLocation -> "Without \"Allow all the time\", Android stops your location updating when the app is closed, and the house thinks you are still wherever you last opened it."
        SetupStep.Battery -> "Set the app's battery use to Unrestricted so Android doesn't put it to sleep and stop the updates."
    }

    /** The button that asks for it. */
    fun button(step: SetupStep, needsSettings: Boolean): String = when {
        needsSettings -> "Open settings"
        step == SetupStep.Notifications -> "Allow notifications"
        step == SetupStep.Battery -> "Open battery settings"
        step == SetupStep.AlwaysLocation -> "Allow all the time"
        step == SetupStep.PreciseLocation -> "Allow precise"
        else -> "Allow location"
    }

    /** One line for the banner. */
    fun summary(missing: List<SetupStep>): String = when (missing.size) {
        0 -> "This phone is set up"
        1 -> "One setting needs fixing on this phone: ${short(missing.first())}"
        else -> "${missing.size} settings need fixing on this phone so the house knows where you are"
    }

    private fun short(step: SetupStep): String = when (step) {
        SetupStep.Notifications -> "notifications"
        SetupStep.Location -> "location sharing"
        SetupStep.PreciseLocation -> "precise location"
        SetupStep.AlwaysLocation -> "location all the time"
        SetupStep.Battery -> "battery use"
    }

    const val DAY_MS = 24 * 3_600_000L
}

/** What this install remembers about asking: plain preferences, nothing secret. */
class PhoneSetupStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("church_drive_phone_setup", Context.MODE_PRIVATE)

    /** The app version that last opened the set-up question by itself. */
    var askedVersion: String?
        get() = prefs.getString("asked_version", null)
        set(v) = prefs.edit().putString("asked_version", v).apply()

    /** When the update question last opened by itself (epoch ms). */
    var updateAskedAt: Long
        get() = prefs.getLong("update_asked_at", 0L)
        set(v) = prefs.edit().putLong("update_asked_at", v).apply()

    /** The app version the house was last told this phone is on (so a new version tells it again). */
    var registeredVersion: String?
        get() = prefs.getString("registered_version", null)
        set(v) = prefs.edit().putString("registered_version", v).apply()

    /** The last set-up report sent to the house, and when, so an unchanged one is not sent again for a few hours. */
    var lastReport: String?
        get() = prefs.getString("last_report", null)
        set(v) = prefs.edit().putString("last_report", v).apply()
    var lastReportAt: Long
        get() = prefs.getLong("last_report_at", 0L)
        set(v) = prefs.edit().putLong("last_report_at", v).apply()
}
