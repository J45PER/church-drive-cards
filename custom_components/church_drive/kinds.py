"""The kinds of notification (and house job) people can be assigned to.

Each kind says where it shows in Manager (group), who starts assigned when it
first appears (`default`: "all" = everyone, including people added later;
"admins"; or "none"), and what it needs to exist (`needs`). A kind whose
devices aren't set up yet (e.g. the Zappi) is listed as waiting and becomes
assignable by itself once they appear.

`needs` is a list of alternatives, any one is enough:
- ("platform", "myenergi"): an entity from that integration;
- ("class", "binary_sensor", "smoke"): an entity of that domain and device class;
- ("domain", "vacuum"): any entity of that domain.

`admin_only` kinds can only go to people whose login is an administrator.
"""

from __future__ import annotations

from typing import Any

GROUPS = ["Safety", "Security", "House", "Energy", "Car", "System", "People", "To-dos", "House jobs"]

KINDS: list[dict[str, Any]] = [
    # Safety: sound even when a phone is on silent.
    {"key": "smoke", "group": "Safety", "name": "Smoke alarm", "critical": True, "default": "all",
     "needs": [("class", "binary_sensor", "smoke")]},
    {"key": "heat", "group": "Safety", "name": "Heat alarm", "critical": True, "default": "all",
     "needs": [("class", "binary_sensor", "heat")]},
    {"key": "co", "group": "Safety", "name": "Carbon monoxide", "critical": True, "default": "all",
     "needs": [("class", "binary_sensor", "carbon_monoxide")]},
    {"key": "alarm_triggered", "group": "Safety", "name": "House alarm going off", "critical": True, "default": "all",
     "needs": [("domain", "alarm_control_panel")]},
    # Security.
    {"key": "doorbell", "group": "Security", "name": "Doorbell pressed", "note": "With a photo", "default": "admins",
     "needs": [("class", "event", "doorbell")]},
    {"key": "door_away", "group": "Security", "name": "Door opened while the alarm's set or nobody's home",
     "default": "admins", "needs": [("class", "binary_sensor", "door")]},
    {"key": "door_left_open", "group": "Security", "name": "Door left open", "note": "10 minutes", "default": "admins",
     "needs": [("class", "binary_sensor", "door")]},
    {"key": "alarm_mode", "group": "Security", "name": "Alarm set or turned off", "default": "admins",
     "needs": [("domain", "alarm_control_panel")]},
    {"key": "night_motion", "group": "Security", "name": "Movement outside at night", "default": "admins",
     "needs": [("class", "event", "motion")]},
    {"key": "tamper", "group": "Security", "name": "Sensor tampered with", "default": "admins",
     "needs": [("class", "binary_sensor", "tamper")]},
    # House.
    {"key": "vacuum", "group": "House", "name": "Vacuum finished or stuck", "default": "admins",
     "needs": [("domain", "vacuum")]},
    {"key": "damp", "group": "House", "name": "Damp risk", "note": "Humidity over 65% for an hour", "default": "admins",
     "needs": [("class", "sensor", "humidity")]},
    {"key": "frost", "group": "House", "name": "Frost tonight", "default": "admins", "needs": [("domain", "weather")]},
    # Energy.
    {"key": "power_down", "group": "Energy", "name": "Octopus power-down session", "default": "all",
     "needs": [("platform", "octopus_energy")]},
    {"key": "cheap_rate", "group": "Energy", "name": "Cheap rate started", "default": "all",
     "needs": [("platform", "octopus_energy")]},
    {"key": "energy_cost", "group": "Energy", "name": "Yesterday's energy cost", "admin_only": True, "default": "admins",
     "needs": [("platform", "octopus_energy")]},
    # Car (the Zappi).
    {"key": "car_plugged", "group": "Car", "name": "Car plugged in", "default": "admins", "needs": [("platform", "myenergi")]},
    {"key": "car_charging", "group": "Car", "name": "Charging started or stopped", "default": "admins",
     "needs": [("platform", "myenergi")]},
    {"key": "car_charged", "group": "Car", "name": "Car charged", "note": "The cost goes to admins only", "default": "admins",
     "needs": [("platform", "myenergi")]},
    {"key": "car_not_plugged", "group": "Car", "name": "Not plugged in before a cheap night", "default": "admins",
     "needs": [("platform", "myenergi")]},
    # System.
    {"key": "internet", "group": "System", "name": "Internet down, and back", "default": "admins",
     "needs": [("platform", "eero"), ("class", "binary_sensor", "connectivity")]},
    {"key": "backup", "group": "System", "name": "Backup failed or missing", "default": "admins",
     "needs": [("platform", "backup")]},
    {"key": "remote_access", "group": "System", "name": "Remote access down", "default": "admins",
     "needs": [("platform", "cloud")]},
    {"key": "device_stale", "group": "System", "name": "A device stays unresponsive", "default": "owner",
     "needs": []},
    {"key": "battery_report", "group": "System", "name": "Weekly battery report", "default": "owner",
     "needs": [("platform", "battery_notes")]},
    # People ("<name> gets home or leaves" kinds are added per person).
    {"key": "left_on", "group": "People", "name": "Everyone's out and something's left on", "default": "admins",
     "needs": []},
    # To-dos.
    {"key": "todo_summary", "group": "To-dos", "name": "To-do summary", "note": "Day and time below", "default": "all",
     "needs": []},
    {"key": "todo_new", "group": "To-dos", "name": "New to-do added", "default": "all", "needs": []},
    # House jobs: these make a to-do for the people ticked (then "New to-do added" tells them).
    {"key": "devices_not_responding", "group": "House jobs", "name": "Devices not responding", "default": "admins",
     "needs": []},
    {"key": "low_batteries", "group": "House jobs", "name": "Low batteries", "default": "admins", "needs": []},
    {"key": "filters_due", "group": "House jobs", "name": "Filters and parts", "default": "admins", "needs": []},
    {"key": "safety_alarms", "group": "House jobs", "name": "Smoke and CO alarms (end of life)", "default": "admins",
     "needs": []},
    {"key": "updates", "group": "House jobs", "name": "Software updates", "default": "admins", "needs": []},
    {"key": "vacuum_messages", "group": "House jobs", "name": "Vacuum messages", "default": "admins", "needs": []},
]

JOB_KINDS = [k["key"] for k in KINDS if k["group"] == "House jobs"]
ARRIVALS = "arrivals:"  # + person entity id, e.g. "arrivals:person.hayley"


def arrival_kind(person_id: str, name: str) -> dict[str, Any]:
    """The "<name> gets home or leaves" kind for one person."""
    return {"key": f"{ARRIVALS}{person_id}", "group": "People", "name": f"{name} gets home or leaves",
            "default": "none", "needs": []}
