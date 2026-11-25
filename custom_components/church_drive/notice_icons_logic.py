"""The icon each kind of notification carries.

An icon is a short spec the Church Drive app draws from its own icon set:

    base                      a house icon (cd: name without the prefix), e.g. "smoke-detector-alert"
    base+badge                the base with a solid circle badge bottom-right, the badge symbol knocked out
    base/off                  the base with the common off slash
    octopus, todo-logo        own marks as a base (todo-logo is the To-Do logo with room for a badge)
    todo-clock-logo           the To-Do logo with its clock
    clive                     Clive's mark, as a badge (marks something Clive did)

`ev-plug` as a badge sits in a rounded square, like the EV tile of car-ev. These are the picks agreed with Jamie (2026-11-25);
Manager can change any of them, and the change is sent with every notice from then on.
"""

from __future__ import annotations

from typing import Any

DEFAULT_ICONS: dict[str, str] = {
    "smoke": "smoke-detector-alert",
    "heat": "fire-alert",
    "co": "co-alert",
    "alarm_triggered": "shield-alert",
    "doorbell": "doorbell",
    "door_away": "shield-alert",
    "door_left_open": "shield-alert",
    "alarm_mode": "shield",  # follows the mode (MODE_ICONS)
    "night_motion": "cctv",
    "tamper": "shield-alert",
    "vacuum": "gregg",
    "vacuum_messages": "gregg",
    "damp": "humidity",
    "frost": "snowflake-alert",
    "clive_actions": "thermostat+clive",
    "power_down": "octopus+power",
    "cheap_rate": "octopus+pound",
    "energy_cost": "octopus+pound",
    "car_plugged": "car+ev-plug",
    "car_charging": "car-ev",
    "car_charged": "car-ev",
    "car_not_plugged": "car+ev-plug",
    "internet": "network-off",
    "backup": "device-server+restore",
    "remote_access": "globe+link",
    "device_stale": "device-health",
    "devices_not_responding": "device-health",
    "battery_report": "battery-low",
    "low_batteries": "battery-low",
    "app_update": "system-update",
    "filters_due": "air-filter",
    "updates": "update",
    "left_on": "home-alert",
    "safety_alarms": "smoke-detector-alert",  # co-alert for a carbon monoxide alarm
    "todo_summary": "todo",
    "todo_new": "todo",
    "todo_due": "todo",
    "todo_overdue": "todo-clock-logo",
    "alarm_test": "shield-check",
}

# The icon of a notice that has no kind of its own, by its tag.
TAG_ICONS: list[tuple[str, str]] = [
    ("inbox-", "todo-logo+alert-circle-solid"),
    ("church-drive-task", "todo-logo+pin"),
    ("church-drive-todo", "todo"),
    ("shopping-", "cart"),
]

MODE_ICONS = {"home": "shield-home", "away": "shield-lock", "night": "shield-moon", "disarmed": "shield-off"}


def _mode_of(title: str, message: str) -> str:
    text = f"{title} {message}".lower()
    if "disarm" in text or "turned off" in text or "off" in text.split():
        return "disarmed"
    for word in ("night", "away", "home"):
        if word in text:
            return word
    return "disarmed"


def gregg_icon(text: str) -> str:
    """Gregg's status icon for what a notice says he is doing or has done (stuck or an error, heading home, paused, cleaning, finished)."""
    t = text.lower()
    if any(w in t for w in ("needs help", "stuck", "error", "fault", "trapped")):
        return "gregg-error"
    if any(w in t for w in ("heading home", "returning", "going home", "back to the dock")):
        return "gregg-returning"
    if "paused" in t:
        return "gregg-paused"
    if any(w in t for w in ("complete", "finished", "done", "docked")):
        return "gregg-docked"
    if any(w in t for w in ("cleaning", "started", "starting")):
        return "gregg-cleaning"
    return "gregg"


def default_icon(kind: str | None, tag: str = "", title: str = "", message: str = "") -> str:
    """The built-in icon for a notice ("" when it has none, so the app keeps the Manager's mark)."""
    k = kind or ""
    if k == "alarm_mode":
        return MODE_ICONS[_mode_of(title, message)]
    if k == "safety_alarms":
        return "co-alert" if "carbon" in f"{title} {message}".lower() or " co " in f" {title.lower()} " else "smoke-detector-alert"
    if k == "clive_actions":
        return "leaf+clive" if "eco" in title.lower() else ("thermostat+clive" if "heating" in title.lower() else "home+clive")
    if k in ("vacuum", "vacuum_messages"):
        return gregg_icon(f"{title} {message}")
    if k.startswith("arrivals:"):
        return "home"
    if k in DEFAULT_ICONS:
        return DEFAULT_ICONS[k]
    if k.startswith("car_"):
        return "car-ev"
    for prefix, icon in TAG_ICONS:
        if tag.startswith(prefix):
            return icon
    return ""


def icon_for(kind: str | None, overrides: dict[str, str] | None, tag: str = "", title: str = "", message: str = "") -> str:
    """What to send with a notice: Manager's choice for the kind if there is one, else the built-in icon.
    A kind whose icon follows the notice (alarm mode, Clive's heating) keeps following it unless Manager has chosen one."""
    chosen = (overrides or {}).get(kind or "")
    return chosen or default_icon(kind, tag, title, message)


def clean_icon(spec: Any) -> str:
    """A spec fit to keep: letters, digits, dashes, plus and slash only, at most 60 characters (an empty one means "back to the built-in")."""
    text = str(spec or "").strip().lower()
    if not text or len(text) > 60 or any(not (c.isalnum() or c in "-+/") for c in text):
        return ""
    return text
