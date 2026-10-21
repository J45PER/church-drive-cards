"""What the Pixel Watch companion app shows (no Home Assistant in here, so it's tested on its own).

The manager chooses, in the Watch panel of the Manager dashboard:

- which **pages** the watch has and in what order (Alarm, Climate, Lights, Cleaning, Car charger);
- which **items** each page offers, kept as a list of what is hidden so a new device shows up by itself;
- how the Lights page starts (floors, rooms or zones);
- which **cars** the charger page may take a battery from (a car's own plugged-in and battery sensors);
- the defaults for Clive's toggles on the watch.

The watch reads the result with `church_drive/watch` and falls back to the same defaults when the house has none.
"""

from __future__ import annotations

import re
from typing import Any

PAGES = ("alarm", "climate", "lights", "cleaning", "car")
LIGHTS_TOP = ("floors", "rooms", "zones")
CLIVE_FLAGS = ("voice", "visual", "follow", "buzz")

# An entity id, or an item id the watch made up (a face such as "suction", a light, a thermostat).
_ITEM_RE = re.compile(r"^[A-Za-z0-9_.:@+-]{1,120}$")
_ENTITY_RE = re.compile(r"^[a-z_]+\.[a-z0-9_]+$")
MAX_HIDDEN = 300
MAX_CARS = 6

DEFAULTS: dict[str, Any] = {
    "pages": [{"id": p, "on": True} for p in PAGES],
    "hidden": {},
    "lights_top": "rooms",
    "cars": [],
    "clive": {"voice": True, "visual": True, "follow": False, "buzz": True},
}


def _pages(raw: Any) -> list[dict[str, Any]]:
    """The pages in the order given (a page may be a name or {id, on}); unknown ones dropped, missing ones added at the end, shown."""
    out: list[dict[str, Any]] = []
    seen: set[str] = set()
    if isinstance(raw, list):
        for item in raw:
            page, on = (item, True) if isinstance(item, str) else ((item.get("id"), item.get("on", True)) if isinstance(item, dict) else (None, True))
            if page in PAGES and page not in seen:
                seen.add(page)
                out.append({"id": page, "on": bool(on)})
    for page in PAGES:
        if page not in seen:
            out.append({"id": page, "on": True})
    return out


def _hidden(raw: Any) -> dict[str, list[str]]:
    out: dict[str, list[str]] = {}
    if not isinstance(raw, dict):
        return out
    for page in PAGES:
        items = raw.get(page)
        if not isinstance(items, list):
            continue
        clean: list[str] = []
        for item in items:
            if isinstance(item, str) and _ITEM_RE.match(item) and item not in clean:
                clean.append(item)
            if len(clean) >= MAX_HIDDEN:
                break
        if clean:
            out[page] = clean
    return out


def _cars(raw: Any) -> list[dict[str, str]]:
    out: list[dict[str, str]] = []
    if not isinstance(raw, list):
        return out
    for car in raw:
        if not isinstance(car, dict):
            continue
        name = str(car.get("name") or "").strip()[:40]
        battery = car.get("battery")
        plugged = car.get("plugged")
        if not name or not (isinstance(battery, str) and _ENTITY_RE.match(battery) and isinstance(plugged, str) and _ENTITY_RE.match(plugged)):
            continue
        if not battery.startswith("sensor.") or not plugged.startswith("binary_sensor."):
            continue
        out.append({"name": name, "battery": battery, "plugged": plugged})
        if len(out) >= MAX_CARS:
            break
    return out


def _clive(raw: Any) -> dict[str, bool]:
    base = dict(DEFAULTS["clive"])
    if isinstance(raw, dict):
        for flag in CLIVE_FLAGS:
            if isinstance(raw.get(flag), bool):
                base[flag] = raw[flag]
    return base


def clean_config(raw: Any) -> dict[str, Any]:
    """The watch settings made safe: known pages and flags only, bad values replaced by the defaults."""
    data = raw if isinstance(raw, dict) else {}
    top = data.get("lights_top")
    return {
        "pages": _pages(data.get("pages")),
        "hidden": _hidden(data.get("hidden")),
        "lights_top": top if top in LIGHTS_TOP else DEFAULTS["lights_top"],
        "cars": _cars(data.get("cars")),
        "clive": _clive(data.get("clive")),
    }
