package com.churchdrive.app.house

/**
 * The sound a kind of notification makes by default, so the doorbell, a movement alert and a to-do reminder don't all sound the same.
 * Each family with its own sound is its own Android channel, so Android's settings can still change it per family.
 * The sounds are in `res/raw` (made by `tools/make_notice_sounds.py`). Safety alarms keep the alarm channel whatever the family.
 */
enum class SoundFamily(val key: String, val label: String, val raw: String?) {
    Default("default", "Pop-ups", null),
    Doorbell("doorbell", "Doorbell", "cd_doorbell"),
    Motion("motion", "Movement", "cd_motion"),
    Energy("energy", "Energy and car", "cd_energy"),
    Todo("todo", "To-dos and reminders", "cd_todo"),
    System("system", "Devices and system", "cd_system");

    /** The channel a pop-up of this family uses. The default family keeps the original pop-up channel. */
    val channel: String get() = if (this == Default) NoticeStyle.Popup.channel else "cd_popup_$key"
}

/** The family a kind of notification belongs to (the kinds are the keys in the house's `kinds.py`). */
fun soundFamilyOf(kind: String?): SoundFamily = when (kind) {
    "doorbell" -> SoundFamily.Doorbell
    "night_motion" -> SoundFamily.Motion
    "power_down", "cheap_rate", "energy_cost", "car_plugged", "car_charging", "car_charged", "car_not_plugged" -> SoundFamily.Energy
    "todo_new", "todo_summary" -> SoundFamily.Todo
    "internet", "backup", "remote_access", "device_stale", "battery_report", "low_batteries", "devices_not_responding",
    "tamper", "filters_due", "safety_alarms", "updates" -> SoundFamily.System
    else -> SoundFamily.Default
}

/**
 * Whether a kind of notification is the assistant's own (what he did in the house, `clive_actions` in the house's `kinds.py`; that key is
 * the house's and never changes here). His come with his mark instead of the Manager's, small and large.
 */
fun isAssistantNotice(kind: String?): Boolean = kind == "clive_actions"

/** The channel a notification in [style] uses: a pop-up gets its family's sound; every other style is as it was. */
fun channelFor(style: NoticeStyle, kind: String?): String =
    if (style == NoticeStyle.Popup) soundFamilyOf(kind).channel else style.channel
