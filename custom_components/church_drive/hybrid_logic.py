"""The plain rules behind hybrid scenes (no Home Assistant in here, so they can be tested alone).

A hybrid scene gives each light in a room (or each zone of a multi-zone light) its own look in one tap: leave it, switch it off, play a
white scene, a colour, a gradient or a dynamic scene. A hybrid belongs to a target (the room's group light) and is kept in the house for
everyone. Its icon is one of a short list: the scene icons and the ones other sections' scenes use.
"""

from __future__ import annotations

import re
import time
from typing import Any

LOOKS = ("leave", "off", "white", "colour", "gradient", "dynamic")
ICONS = (
    "cd:sun", "cd:sun-half", "cd:flower", "cd:moon", "cd:bolt", "cd:palette", "cd:animation-play", "cd:bulb-lit", "cd:room-bed-double",
    "cd:weather-sunset", "cd:television", "cd:music-note", "cd:leaf", "cd:fire", "cd:snowflake", "cd:home", "cd:home-away", "cd:shield-moon",
)
DEFAULT_ICON = "cd:palette"
MAX_NAME = 20
MAX_LIGHTS = 16
MAX_HYBRIDS_PER_TARGET = 12

_HEX = re.compile(r"^#[0-9a-fA-F]{6}$")
_KEY = re.compile(r"^[a-z0-9_]{1,40}$")


def clean_look(value: Any) -> dict[str, Any] | None:
    """One light's look from something sent in, or None if it isn't a look."""
    if not isinstance(value, dict):
        return None
    kind = value.get("t")
    if kind in ("leave", "off"):
        return {"t": kind}
    v = value.get("v")
    if kind in ("white", "dynamic"):
        return {"t": kind, "v": v} if isinstance(v, str) and _KEY.match(v) else None
    if kind == "colour":
        return {"t": kind, "v": v.lower()} if isinstance(v, str) and _HEX.match(v) else None
    if kind == "gradient":
        if isinstance(v, list) and 2 <= len(v) <= 5 and all(isinstance(c, str) and _HEX.match(c) for c in v):
            return {"t": kind, "v": [c.lower() for c in v]}
    return None


def clean_hybrid(value: Any, existing: dict[str, Any] | None = None) -> dict[str, Any]:
    """A hybrid from something sent in; raises ValueError saying what is wrong."""
    if not isinstance(value, dict):
        raise ValueError("A hybrid scene needs a name, a target and looks")
    name = str(value.get("name") or "").strip()[:MAX_NAME]
    if not name:
        raise ValueError("A hybrid scene needs a name")
    target = value.get("target") if value.get("target") is not None else (existing or {}).get("target")
    if not isinstance(target, str) or not target.startswith("light."):
        raise ValueError("A hybrid scene belongs to a room or light")
    looks_in = value.get("looks")
    if not isinstance(looks_in, dict) or not looks_in:
        raise ValueError("A hybrid scene needs a look for at least one light")
    looks: dict[str, dict[str, Any]] = {}
    for entity, raw in list(looks_in.items())[:MAX_LIGHTS]:
        look = clean_look(raw)
        if look is None or not isinstance(entity, str) or not entity.startswith("light."):
            raise ValueError(f"Not a look for {entity}")
        looks[entity] = look
    if not any(look["t"] != "leave" for look in looks.values()):
        raise ValueError("Every light is set to Leave, so this scene would do nothing")
    icon = value.get("icon")
    return {
        "id": (existing or {}).get("id") or str(value.get("id") or "") or f"h{int(time.time() * 1000)}",
        "name": name,
        "icon": icon if icon in ICONS else DEFAULT_ICON,
        "target": target,
        "looks": looks,
    }


def clean_all(value: Any) -> list[dict[str, Any]]:
    """The stored list, each one valid; anything that isn't is dropped."""
    out: list[dict[str, Any]] = []
    for raw in value if isinstance(value, list) else []:
        try:
            out.append(clean_hybrid(raw, raw if isinstance(raw, dict) else None))
        except ValueError:
            continue
    return out


def for_target(hybrids: list[dict[str, Any]], target: str | None) -> list[dict[str, Any]]:
    """The hybrids of one room or light (all of them with no target)."""
    return [h for h in hybrids if target is None or h["target"] == target]


def gradient_spec(colours_xy: list[tuple[float, float]], name: str = "Hybrid") -> dict[str, Any]:
    """A colour scene spec (as the scene library keeps them) for a hybrid's gradient: still, at full brightness, the colours in order."""
    return {"name": name, "kind": "colour", "brightness": 100.0, "colors": [tuple(c) for c in colours_xy], "dynamic": False, "speed": 0.5}


def rgb_of(hex_colour: str) -> list[int]:
    """"#ff9a3c" as [255, 154, 60]."""
    v = hex_colour.lstrip("#")
    return [int(v[i : i + 2], 16) for i in (0, 2, 4)]
