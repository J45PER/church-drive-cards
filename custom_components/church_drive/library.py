"""The universal scene library.

Each scene is defined once here. Light cards apply these to a room, zone or
light directly (one light.turn_on on the group, so nothing is stored on the
Hue bridge); the bridge sync (hue.py) can also create them as Hue scenes for
chosen rooms. Values are the Hue app's own versions of these scenes, read
from the bridge. `mirek` is colour temperature (1,000,000 / kelvin), `xy` a
colour, `brightness` 0-100.
"""

from __future__ import annotations

from typing import Any

LIBRARY: dict[str, dict] = {
    "bright": {"name": "Bright", "mirek": 370, "brightness": 100.0},
    "cool_bright": {"name": "Cool bright", "mirek": 250, "brightness": 100.0},
    "dimmed": {"name": "Dimmed", "mirek": 370, "brightness": 30.0},
    "read": {"name": "Read", "mirek": 346, "brightness": 100.0},
    "concentrate": {"name": "Concentrate", "mirek": 233, "brightness": 100.0},
    "energise": {"name": "Energise", "mirek": 156, "brightness": 100.0},
    "relax": {"name": "Relax", "mirek": 447, "brightness": 56.25},
    "rest": {"name": "Rest", "mirek": 447, "brightness": 34.0},
    "nightlight": {"name": "Nightlight", "xy": (0.5610, 0.4042), "mirek": 500, "brightness": 0.4},
}


def find(scene: str) -> tuple[str, dict] | None:
    """Look a scene up by key ("cool_bright") or name ("Cool bright")."""
    wanted = str(scene).strip().lower()
    for key, spec in LIBRARY.items():
        if wanted in (key, spec["name"].lower()):
            return key, spec
    return None


def turn_on_data(spec: dict) -> dict[str, Any]:
    """light.turn_on data that applies a library scene."""
    data: dict[str, Any] = {"brightness": max(1, round(spec["brightness"] * 255 / 100))}
    if "xy" in spec:
        data["xy_color"] = list(spec["xy"])
    else:
        data["color_temp_kelvin"] = round(1_000_000 / spec["mirek"])
    return data


def as_list() -> list[dict[str, Any]]:
    """The library for the frontend: key, name and light.turn_on data."""
    return [{"key": key, "name": spec["name"], **turn_on_data(spec)} for key, spec in LIBRARY.items()]
