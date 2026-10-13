"""The buttons each kind of notification carries, as pure choices (no Home Assistant needed).

Two sorts of button, both in Home Assistant's own `actions` format so the Home Assistant app understands the first sort too:

- opening: `{"action": "URI", "title": ..., "uri": ...}`. `uri` is `church-drive-camera:camera.x` (live view; `?answer` to
  answer the doorbell) or `/dashboard-mobile/<page>`;
- doing: `{"action": "CD_<verb>[:<arg>]", "title": ...}`. `mute` and `snooze` are handled by the Church Drive app on the phone
  itself; the rest change something in the house and are done by `notice_actions.py` (the app asks for the phone to be
  unlocked first). Nothing here ever disarms the alarm or dials a number.
"""

from __future__ import annotations

import re
from typing import Any, Callable

PREFIX = "CD_"

# Buttons that change something in the house.
HOUSE_VERBS = ("lights_on", "dock", "all_off", "car_now", "car_pause", "backup")
# Buttons the Church Drive app handles on the phone: hide this kind for a while, or bring this one back later.
PHONE_VERBS = ("mute", "snooze")

# This house's live-view cameras.
FRONT_DOOR = "camera.front_door_live_view"
ENTRANCE = "camera.entrance_live_view"
GARDEN = "camera.garden_live_view"
LIVING_ROOM = "camera.living_room_live_view"

MAX_BUTTONS = 3  # Android shows three

# Where tapping the notification itself goes when the sender gave no link (the page's name in the app).
KIND_PAGES = {
    "smoke": "security", "heat": "security", "co": "security", "alarm_triggered": "security", "door_away": "security",
    "door_left_open": "security", "alarm_mode": "security", "tamper": "security", "doorbell": "security",
    "night_motion": "security", "vacuum": "cleaning", "damp": "climate", "frost": "climate", "left_on": "lighting",
    "todo_new": "todo", "todo_summary": "todo",
}
# Kinds about a device's own health: tapping one opens the app's Device Health page (the device's page when it is known).
HEALTH_KINDS = ("battery_report", "device_stale", "low_batteries", "devices_not_responding")
HEALTH_URI = "church-drive-health"
DEVICE_PREFIX = "church-drive-device:"
BATTERY_TAG = "church-drive-battery-"


def page_uri(page: str) -> str:
    return f"/dashboard-mobile/{page}"


def camera_uri(camera: str, answer: bool = False) -> str:
    return f"church-drive-camera:{camera}" + ("?answer" if answer else "")


def link(title: str, uri: str) -> dict[str, str]:
    return {"action": "URI", "title": title, "uri": uri}


def verb(name: str, title: str, arg: int | None = None) -> dict[str, str]:
    return {"action": f"{PREFIX}{name}" + (f":{arg}" if arg is not None else ""), "title": title}


def is_phone_action(action_id: str) -> bool:
    """A button only the Church Drive app can do (the Home Assistant app is not given these)."""
    return any(action_id.startswith(f"{PREFIX}{v}") for v in PHONE_VERBS)


def split_action(action_id: str) -> tuple[str, int | None] | None:
    """`CD_snooze:10` -> ("snooze", 10); None for anything that is not one of ours."""
    if not action_id.startswith(PREFIX):
        return None
    name, _, arg = action_id[len(PREFIX):].partition(":")
    if name not in HOUSE_VERBS + PHONE_VERBS:
        return None
    return name, (int(arg) if arg.isdigit() else None)


def camera_for(kind: str | None, tag: str = "", image: str = "", exists: Callable[[str], bool] = lambda _e: True) -> str | None:
    """The live camera that goes with a notification, or None."""
    t = (tag or "").lower()
    found: str | None = None
    m = re.search(r"camera\.([a-z0-9_]+?)_snapshot", image or "")
    if m:
        found = f"camera.{m.group(1)}_live_view"
    elif kind == "doorbell":
        found = FRONT_DOOR
    elif kind in ("door_away", "door_left_open"):
        found = GARDEN if "back_door" in t else FRONT_DOOR
    elif kind in ("smoke", "heat"):
        if "entrance" in t:
            found = ENTRANCE
        elif "kitchen" in t or "living" in t:
            found = LIVING_ROOM
    return found if found and exists(found) else None


def actions_for(
    kind: str | None, tag: str = "", image: str = "", title: str = "",
    exists: Callable[[str], bool] = lambda _e: True,
) -> list[dict[str, Any]]:
    """The buttons for a notification of this kind (at most three), or none."""
    if not kind:
        return []
    cam = camera_for(kind, tag, image, exists)
    out: list[dict[str, Any]] = []

    def view(label: str = "View camera", answer: bool = False) -> None:
        if cam:
            out.append(link(label, camera_uri(cam, answer)))

    if kind in ("smoke", "heat"):
        view()
        out.append(verb("lights_on", "Lights on"))
    elif kind in ("co", "alarm_mode", "tamper"):
        out.append(link("Open Security", page_uri("security")))
    elif kind == "alarm_triggered":
        out.append(link("View cameras", page_uri("security")))
    elif kind == "doorbell":
        view("Answer", answer=True)
        view("View")
        out.append(verb("mute", "Mute 1 hour", 60))
    elif kind == "door_away":
        view()
        out.append(link("Open Security", page_uri("security")))
    elif kind == "door_left_open":
        out.append(verb("snooze", "Snooze 10 min", 10))
        view()
    elif kind == "night_motion":
        view("View live")
        out.append(verb("mute", "Mute 1 hour", 60))
    elif kind == "vacuum":
        if "help" in (title or "").lower():  # "Gregg needs help": stuck, not finished
            out.append(verb("dock", "Send to dock"))
        out.append(link("Open Cleaning", page_uri("cleaning")))
    elif kind == "damp":
        out.append(verb("snooze", "Snooze 1 hour", 60))
        out.append(link("Open Climate", page_uri("climate")))
    elif kind == "left_on":
        out.append(verb("all_off", "Turn them off"))
        out.append(link("Open Lighting", page_uri("lighting")))
    elif kind == "cheap_rate":
        out.append(verb("car_now", "Charge car now"))
        out.append(link("Open Energy", page_uri("energy")))
    elif kind == "power_down":
        out.append(verb("car_pause", "Pause car charging"))
        out.append(link("Open Energy", page_uri("energy")))
    elif kind in ("car_plugged", "car_charged"):
        out.append(verb("car_now", "Charge now"))
    elif kind == "car_not_plugged":
        out.append(verb("snooze", "Snooze 30 min", 30))
    elif kind == "backup":
        out.append(verb("backup", "Run backup now"))
    return out[:MAX_BUTTONS]


def device_uri(entity_id: str) -> str:
    return f"{DEVICE_PREFIX}{entity_id}"


def entity_from_slug(slug: str, entity_ids: list[str]) -> str | None:
    """The entity whose id, slugified, is `slug` (a tag such as `church-drive-battery-binary_sensor_back_door_battery_plus_low`)."""
    return next((e for e in entity_ids if e.replace(".", "_") == slug), None)


def tap_link_for(kind: str | None, tag: str = "", link_given: str = "", entity_ids: list[str] | None = None) -> str:
    """Where tapping the notification goes: the sender's own link when it is a good one, else the right place for the kind.

    - A battery or device-health kind opens the device's page (the battery alert's tag names its sensor) or, failing that,
      the Device Health list; an old `/battery-status/...` link or a page link on such a kind is replaced by that.
    - With no link at all, a kind opens its page (a smoke alarm opens Security, the vacuum opens Cleaning, ...).
    """
    given = link_given or ""
    if given.startswith(DEVICE_PREFIX) or given == HEALTH_URI:
        return given
    if kind in HEALTH_KINDS:
        slug = tag[len(BATTERY_TAG):] if tag.startswith(BATTERY_TAG) else ""
        found = entity_from_slug(slug, entity_ids or []) if slug else None
        return device_uri(found) if found else HEALTH_URI
    if given.startswith("/battery-status"):
        return HEALTH_URI
    if given:
        return given
    page = KIND_PAGES.get(kind or "")
    return page_uri(page) if page else ""


def is_app_only_link(link: str) -> bool:
    """A link only the Church Drive app understands (a device or Device Health page)."""
    return link.startswith(DEVICE_PREFIX) or link == HEALTH_URI
