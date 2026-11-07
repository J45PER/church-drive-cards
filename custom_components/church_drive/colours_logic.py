"""Clive's colour palettes kept on Home Assistant: the shipped defaults with the owner's changes on top (the rules only; storage is colours.py)."""

from __future__ import annotations

import re
from typing import Any

HEX = re.compile(r"^#[0-9A-Fa-f]{6}$")
KEY = re.compile(r"^[a-z][a-z0-9_]{0,39}$")
MAX_PALETTES = 200
MAX_NAME = 60

# The glow palette of each kind of Clive card is (Colour, Accent, Light) of one palette (ha/clive-show-card.yaml reads them).
KIND_KEYS = {
    "energy": "energy", "people": "people", "music": "media", "media": "media", "car": "car", "climate": "heating", "heat": "heating",
    "gregg": "gregg", "air": "air", "secure": "alarm", "doors": "doors", "tasks": "tasks", "devices": "warn", "lights": "lights",
    "shop": "shopping", "event": "calendar", "reminder": "calendar",
}


def _colours(raw: Any) -> list[str] | None:
    """Five #rrggbb colours (Colour, Dark, Accent, Light, Deep), upper-cased, or None."""
    if not isinstance(raw, (list, tuple)) or len(raw) != 5:
        return None
    if not all(isinstance(c, str) and HEX.match(c) for c in raw):
        return None
    return [c.upper() for c in raw]


def clean_overrides(raw: Any) -> dict[str, dict[str, Any]]:
    """The changes worth keeping from whatever was sent: {key: {name?, colours?}} for well-formed keys, names and colours only."""
    if not isinstance(raw, dict):
        return {}
    out: dict[str, dict[str, Any]] = {}
    for key, value in raw.items():
        if not isinstance(key, str) or not KEY.match(key) or not isinstance(value, dict):
            continue
        item: dict[str, Any] = {}
        name = value.get("name")
        if isinstance(name, str) and name.strip():
            item["name"] = name.strip()[:MAX_NAME]
        colours = _colours(value.get("colours"))
        if colours:
            item["colours"] = colours
        if item:
            out[key] = item
        if len(out) >= MAX_PALETTES:
            break
    return out


def merge(defaults: dict[str, dict[str, Any]], overrides: dict[str, dict[str, Any]]) -> dict[str, dict[str, Any]]:
    """Every palette as {key: {name, group, colours, custom}}: a default with its changes on top, plus the palettes added."""
    palettes: dict[str, dict[str, Any]] = {}
    for key, d in defaults.items():
        colours = _colours(d.get("colours"))
        if not colours:
            continue
        palettes[key] = {"name": str(d.get("name") or key), "group": str(d.get("group") or "Other"), "colours": colours, "custom": False}
    for key, o in overrides.items():
        if key in palettes:
            palettes[key]["name"] = o.get("name", palettes[key]["name"])
            palettes[key]["colours"] = o.get("colours", palettes[key]["colours"])
        elif "colours" in o:
            palettes[key] = {"name": o.get("name", key), "group": "Your palettes", "colours": o["colours"], "custom": True}
    return palettes


def kind_palettes(palettes: dict[str, dict[str, Any]]) -> dict[str, list[str]]:
    """The glow palette (Colour, Accent, Light) for each kind of Clive card."""
    out: dict[str, list[str]] = {}
    for kind, key in KIND_KEYS.items():
        p = palettes.get(key)
        if p:
            c = p["colours"]
            out[kind] = [c[0], c[2], c[3]]
    return out
