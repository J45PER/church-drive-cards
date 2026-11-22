"""The plain rules behind sensor actions (no Home Assistant in here, so they can be tested alone).

A sensor action says what lights do when a presence or motion sensor sees someone and when the room empties:

    {"id": "a1b2c3d4", "sensor": "binary_sensor.cloakroom_presence", "name": "Cloakroom", "lights": ["light.cloakroom"],
     "on_action": "on", "scene": "", "off_action": "off", "off_delay": 60, "dim_pct": 20,
     "only_dark": false, "dark_lux": 30, "lux_sensor": "", "window": null, "enabled": true}

`on_action` is on, scene (the Church Drive scene named in `scene`) or none; `off_action` is off, dim (to `dim_pct`) or none, `off_delay`
seconds after the sensor last saw anyone. `only_dark` limits the "someone's there" action to when it is dark: below `dark_lux` on
`lux_sensor`, or, with no light-level sensor, while the sun is down. `window` ({"start": "22:00", "end": "06:00"}) limits it to a
time of day. `slots` give the "someone's there" action a different look at different times of day: each is
{"start": "18:00", "end": "23:00", "on_action": "scene", "scene": "relax", "pct": 0}, the first one that covers the time wins, and any
other time uses the sensor's own `on_action`, `scene` and `pct` (a brightness percentage, 0 to leave the light's level alone). Times are the
house's local time.
"""

from __future__ import annotations

import re
import secrets
from typing import Any

MAX_SENSORS = 40
MAX_GROUP = 8
MAX_LIGHTS = 12
MAX_SLOTS = 6
ON_ACTIONS = ("on", "scene", "hybrid", "none")
OFF_ACTIONS = ("off", "dim", "none")
_TIME = re.compile(r"^([01]?\d|2[0-3]):([0-5]\d)$")


def clean_time(value: Any) -> str | None:
    m = _TIME.match(str(value or "").strip())
    return f"{int(m.group(1)):02d}:{m.group(2)}" if m else None


def clean_window(value: Any) -> dict[str, str] | None:
    if not isinstance(value, dict):
        return None
    start, end = clean_time(value.get("start")), clean_time(value.get("end"))
    return {"start": start, "end": end} if start and end and start != end else None


def clean_slots(value: Any) -> list[dict[str, Any]]:
    """The time slots from what was sent: each needs a start and an end that differ; a bad one is dropped, and no more than MAX_SLOTS are kept."""
    out: list[dict[str, Any]] = []
    for raw in value if isinstance(value, (list, tuple)) else []:
        if not isinstance(raw, dict):
            continue
        window = clean_window(raw)
        if window is None:
            continue
        action = raw.get("on_action") if raw.get("on_action") in ON_ACTIONS else "on"
        scene = str(raw.get("scene") or "").strip()[:60]
        if action in ("scene", "hybrid") and not scene:
            continue
        out.append({**window, "on_action": action, "scene": scene if action in ("scene", "hybrid") else "", "pct": _int(raw.get("pct"), 0, 100, 0)})
    return out[:MAX_SLOTS]


def _int(value: Any, low: int, high: int, default: int) -> int:
    try:
        return max(low, min(high, int(float(value))))
    except (TypeError, ValueError):
        return default


def clean_sensor(raw: dict[str, Any], old: dict[str, Any] | None = None) -> dict[str, Any]:
    """A sensor action from what was sent (over `old` when it is a change); ValueError says what's wrong."""
    base = {
        "sensor": "", "name": "", "lights": [], "on_action": "on", "scene": "", "off_action": "off", "off_delay": 60, "dim_pct": 20,
        "only_dark": False, "dark_lux": 30, "lux_sensor": "", "window": None, "enabled": True, "pct": 0, "slots": [],
        "off_if_on": False, "sensors": [], "lux_sensors": [],
    }
    item = {**base, **(old or {}), **{k: v for k, v in raw.items() if k in base}}
    sensor = str(item["sensor"]).strip()
    if not sensor.startswith("binary_sensor."):
        raise ValueError("Choose a motion or presence sensor")
    lights = item["lights"] if isinstance(item["lights"], (list, tuple)) else [item["lights"]]
    lights = [str(x) for x in lights if str(x).startswith("light.")][:MAX_LIGHTS]
    if not lights:
        raise ValueError("Choose a light for it to switch")
    on_action = item["on_action"] if item["on_action"] in ON_ACTIONS else "on"
    scene = str(item["scene"] or "").strip()
    if on_action in ("scene", "hybrid") and not scene:
        raise ValueError("Choose which scene or theme to play")
    lux = str(item["lux_sensor"] or "").strip()
    # A group: the sensor above plus any others, which together count as one (someone seen by any of them is someone there, and the room empties
    # when all have gone). The light level is the brightest of their readings.
    extra = item["sensors"] if isinstance(item["sensors"], (list, tuple)) else []
    sensors = list(dict.fromkeys([sensor] + [str(x).strip() for x in extra if str(x).strip().startswith("binary_sensor.")]))[:MAX_GROUP]
    extra_lux = item["lux_sensors"] if isinstance(item["lux_sensors"], (list, tuple)) else []
    lux_list = list(dict.fromkeys(x for x in [lux] + [str(y).strip() for y in extra_lux] if x.startswith("sensor.")))[:MAX_GROUP]
    return {
        "id": (old or {}).get("id") or secrets.token_hex(4),
        "sensor": sensor,
        "name": str(item["name"] or "").strip()[:60] or sensor.split(".", 1)[1].replace("_", " ").title(),
        "lights": lights,
        "on_action": on_action,
        "scene": scene[:60],
        "off_action": item["off_action"] if item["off_action"] in OFF_ACTIONS else "off",
        "off_delay": _int(item["off_delay"], 10, 3600, 60),
        "dim_pct": _int(item["dim_pct"], 1, 100, 20),
        "only_dark": bool(item["only_dark"]),
        "dark_lux": _int(item["dark_lux"], 1, 2000, 30),
        "lux_sensor": lux_list[0] if lux_list else "",
        "lux_sensors": lux_list,
        "sensors": sensors,
        "window": clean_window(item["window"]),
        "enabled": bool(item["enabled"]),
        "pct": _int(item["pct"], 0, 100, 0),
        "slots": clean_slots(item["slots"]),
        "off_if_on": bool(item["off_if_on"]),
    }


def clean_sensors(raw: Any) -> dict[str, dict[str, Any]]:
    """What was stored, kept if it is still a valid sensor action (a bad one is dropped, not the whole list)."""
    out: dict[str, dict[str, Any]] = {}
    for entry in raw if isinstance(raw, list) else []:
        try:
            item = clean_sensor(entry, {"id": entry.get("id")} if isinstance(entry, dict) and entry.get("id") else None)
        except (ValueError, AttributeError, TypeError):
            continue
        out[item["id"]] = item
    return out


def minutes_of(clock: str) -> int:
    h, m = clock.split(":")
    return int(h) * 60 + int(m)


def in_window(now_minutes: int, window: dict[str, str] | None) -> bool:
    """Whether the time of day (minutes after midnight) is inside the window, which may run past midnight. No window is always."""
    if not window:
        return True
    start, end = minutes_of(window["start"]), minutes_of(window["end"])
    return start <= now_minutes < end if start < end else now_minutes >= start or now_minutes < end


def is_dark(item: dict[str, Any], lux: float | None, sun_down: bool) -> bool:
    """Dark means below the light level on its sensor, or, when there is no reading, the sun being down."""
    if item["lux_sensors"] and lux is not None:
        return lux < item["dark_lux"]
    return sun_down


def pick_look(item: dict[str, Any], now_minutes: int) -> dict[str, Any]:
    """What to do for someone being seen at this time of day: the first time slot that covers it, else the sensor's own look."""
    for slot in item["slots"]:
        if in_window(now_minutes, slot):
            return {"on_action": slot["on_action"], "scene": slot["scene"], "pct": slot["pct"]}
    return {"on_action": item["on_action"], "scene": item["scene"], "pct": item["pct"]}


def should_act_on_presence(item: dict[str, Any], now_minutes: int, lux: float | None, sun_down: bool) -> bool:
    """Whether someone being seen should do the "someone's there" action now."""
    if not item["enabled"] or pick_look(item, now_minutes)["on_action"] == "none":
        return False
    if item["only_dark"] and not is_dark(item, lux, sun_down):
        return False
    return in_window(now_minutes, item["window"])


def delay_text(seconds: int) -> str:
    return f"{seconds} s" if seconds < 60 else f"{seconds // 60} min" if seconds % 60 == 0 else f"{seconds // 60} min {seconds % 60} s"


def summary(item: dict[str, Any]) -> str:
    """One line for the card: "Light follows presence · off after 1 min · when it's dark"."""
    parts = []
    parts.append({"on": "Light follows presence", "scene": "Plays a scene when someone's there", "hybrid": "Plays a theme when someone's there", "none": "Does nothing when someone's there"}[item["on_action"]])
    if item["off_action"] == "off":
        parts.append(f"off after {delay_text(item['off_delay'])}")
    elif item["off_action"] == "dim":
        parts.append(f"dims to {item['dim_pct']}% after {delay_text(item['off_delay'])}")
    if item["slots"]:
        parts.append(f"{len(item['slots'])} time slot{'s' if len(item['slots']) != 1 else ''}")
    if item["only_dark"]:
        parts.append("when it's dark")
    if item["window"]:
        parts.append(f"{item['window']['start']}–{item['window']['end']}")
    if not item["enabled"]:
        parts.insert(0, "Off")
    return " · ".join(parts)
