"""The plain rules behind sensor actions (no Home Assistant in here, so they can be tested alone).

A sensor action says what lights do when a presence or motion sensor sees someone and when the room empties:

    {"id": "a1b2c3d4", "sensor": "binary_sensor.cloakroom_presence", "name": "Cloakroom", "lights": ["light.cloakroom"],
     "on_action": "on", "scene": "", "off_action": "off", "off_delay": 60, "dim_pct": 20,
     "only_dark": false, "dark_lux": 30, "lux_sensor": "", "window": null, "enabled": true}

`on_action` is on, scene (the Church Drive scene named in `scene`) or none; `off_action` is off, dim (to `dim_pct`) or none, `off_delay`
seconds after the sensor last saw anyone. `only_dark` limits the "someone's there" action to when it is dark: below `dark_lux` on
`lux_sensor`, or, with no light-level sensor, while the sun is down. `window` ({"start": "22:00", "end": "06:00"}) limits it to a
time of day. Times are the house's local time.
"""

from __future__ import annotations

import re
import secrets
from typing import Any

MAX_SENSORS = 40
MAX_LIGHTS = 12
ON_ACTIONS = ("on", "scene", "none")
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


def _int(value: Any, low: int, high: int, default: int) -> int:
    try:
        return max(low, min(high, int(float(value))))
    except (TypeError, ValueError):
        return default


def clean_sensor(raw: dict[str, Any], old: dict[str, Any] | None = None) -> dict[str, Any]:
    """A sensor action from what was sent (over `old` when it is a change); ValueError says what's wrong."""
    base = {
        "sensor": "", "name": "", "lights": [], "on_action": "on", "scene": "", "off_action": "off", "off_delay": 60, "dim_pct": 20,
        "only_dark": False, "dark_lux": 30, "lux_sensor": "", "window": None, "enabled": True,
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
    if on_action == "scene" and not scene:
        raise ValueError("Choose which scene to play")
    lux = str(item["lux_sensor"] or "").strip()
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
        "lux_sensor": lux if lux.startswith("sensor.") else "",
        "window": clean_window(item["window"]),
        "enabled": bool(item["enabled"]),
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
    if item["lux_sensor"] and lux is not None:
        return lux < item["dark_lux"]
    return sun_down


def should_act_on_presence(item: dict[str, Any], now_minutes: int, lux: float | None, sun_down: bool) -> bool:
    """Whether someone being seen should do the "someone's there" action now."""
    if not item["enabled"] or item["on_action"] == "none":
        return False
    if item["only_dark"] and not is_dark(item, lux, sun_down):
        return False
    return in_window(now_minutes, item["window"])


def delay_text(seconds: int) -> str:
    return f"{seconds} s" if seconds < 60 else f"{seconds // 60} min" if seconds % 60 == 0 else f"{seconds // 60} min {seconds % 60} s"


def summary(item: dict[str, Any]) -> str:
    """One line for the card: "Light follows presence · off after 1 min · when it's dark"."""
    parts = []
    parts.append({"on": "Light follows presence", "scene": "Plays a scene when someone's there", "none": "Does nothing when someone's there"}[item["on_action"]])
    if item["off_action"] == "off":
        parts.append(f"off after {delay_text(item['off_delay'])}")
    elif item["off_action"] == "dim":
        parts.append(f"dims to {item['dim_pct']}% after {delay_text(item['off_delay'])}")
    if item["only_dark"]:
        parts.append("when it's dark")
    if item["window"]:
        parts.append(f"{item['window']['start']}–{item['window']['end']}")
    if not item["enabled"]:
        parts.insert(0, "Off")
    return " · ".join(parts)
