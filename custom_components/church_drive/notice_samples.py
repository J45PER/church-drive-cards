"""What each kind of notification looks like as a sample: the words, whether it carries a camera picture, and which
camera. The app shows these in Account > Settings > Notification styles, and "Send me a test" sends the same words
through the real sending path (with the real buttons, picture and the person's chosen style), so a test looks like
the real one. Pure (no Home Assistant), so it can be tested alone."""

from __future__ import annotations

from typing import Any

# kind -> (title, message, camera base or "")
SAMPLES: dict[str, tuple[str, str, str]] = {
    "smoke": ("🔥 Smoke alarm: Entrance", "The smoke alarm in the Entrance went off at 14:32.", "entrance"),
    "heat": ("🔥 Heat alarm: Kitchen", "The heat alarm in the Kitchen went off at 14:32.", "living_room"),
    "co": ("☠️ Carbon monoxide", "The carbon monoxide alarm went off at 14:32.", ""),
    "alarm_triggered": ("🚨 House alarm going off", "The house alarm was triggered at 14:32.", ""),
    "doorbell": ("🔔 Someone's at the door", "The front doorbell was pressed at 14:32.", "front_door"),
    "door_away": ("🚪 Front Door opened", "Front Door opened at 14:32 while the alarm is set (away).", "front_door"),
    "door_left_open": ("🚪 Back Door left open", "Back Door has been open for 10 minutes.", ""),
    "alarm_mode": ("🛡️ Alarm set: Away", "Set to away by Jamie at 14:32.", ""),
    "night_motion": ("👀 Movement: Garden", "The garden camera saw movement at 14:32.", "garden"),
    "tamper": ("⚠️ Back Door tampered with", "The Ring alarm reports Back Door was tampered with at 14:32.", ""),
    "vacuum": ("🤖 Gregg needs help", "Gregg is stuck in the Living Room.", ""),
    "damp": ("💧 Damp risk: Bathroom", "Humidity has been over 65% for an hour.", ""),
    "frost": ("❄️ Frost tonight", "It is forecast to drop to -2°C tonight.", ""),
    "power_down": ("⚡ Power-down session", "Octopus has asked for less power use from 17:00 to 18:00.", ""),
    "cheap_rate": ("💷 Cheap rate started", "The cheap rate has started: 7p per kWh until 05:30.", ""),
    "energy_cost": ("💷 Yesterday's energy", "Yesterday cost £2.41 for 18.2 kWh.", ""),
    "car_plugged": ("🔌 Car plugged in", "The car was plugged in at 18:05.", ""),
    "car_charging": ("🔋 Charging started", "The car started charging at 00:30.", ""),
    "car_charged": ("🔋 Car charged", "The car is charged to 80%.", ""),
    "car_not_plugged": ("🔌 Not plugged in", "The car isn't plugged in, and tonight's rate is cheap.", ""),
    "internet": ("🌐 Internet down", "The internet went down at 14:32.", ""),
    "backup": ("💾 Backup missing", "There has been no backup for 3 days.", ""),
    "remote_access": ("☁️ Remote access down", "Home Assistant Cloud can't be reached.", ""),
    "left_on": ("💡 Everyone's out", "Everyone's out and the Kitchen lights are on.", ""),
    "device_stale": ("📡 A device is unresponsive", "Living Room Fan hasn't answered for 6 hours.", ""),
    "battery_report": ("🔋 Weekly battery report", "2 batteries are low: Front Door Sensor, Garden Sensor.", ""),
}


def sample_for(kind: str, name: str = "", note: str = "") -> dict[str, Any]:
    """The sample for a kind: {title, message, camera} (a kind with no sample of its own says its name)."""
    found = SAMPLES.get(kind)
    if found:
        return {"title": found[0], "message": found[1], "camera": found[2]}
    if kind.startswith("arrivals:"):
        who = name.split(" gets")[0].split(" arrives")[0] or "Someone"
        return {"title": f"🏠 {who} is home", "message": f"{who} got home at 14:32.", "camera": ""}
    return {"title": name or "Notification", "message": note or "This is how it will look on this phone.", "camera": ""}
