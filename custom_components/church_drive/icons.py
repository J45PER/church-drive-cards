"""The icons the cards use for modes, in one list the dashboard cards and the Android app both read.

Each card has its own idea of which icon a mode gets (a fan's Sleep, a thermostat's Eco, the charger's Stop). They are
kept here, once, so changing one in the Icon Styles card changes it everywhere: on every dashboard and in the app.
Anything not listed here, or if Church Drive isn't running, keeps the card's built-in icon.
"""

from __future__ import annotations

import re
from typing import Any

from homeassistant.core import HomeAssistant
from homeassistant.helpers.storage import Store

STORE_KEY = "church_drive.icons"

# group → key (lower case, as the card looks it up) → built-in icon.
DEFAULTS: dict[str, dict[str, str]] = {
    "fan": {
        "off": "mdi:power",
        "speed_low": "mdi:speedometer-slow",
        "speed_medium": "mdi:speedometer-medium",
        "speed_high": "mdi:speedometer",
        "natural": "mdi:weather-windy",
        "nature": "mdi:weather-windy",
        "breeze": "mdi:weather-windy",
        "sleep": "mdi:power-sleep",
        "auto": "mdi:fan-auto",
        "smart": "mdi:fan-auto",
        "turbo": "mdi:rocket-launch",
        "boost": "mdi:rocket-launch",
        "eco": "mdi:leaf",
        "oscillate": "mdi:arrow-oscillating",
        "other": "mdi:fan",
    },
    "purifier": {
        "off": "mdi:power",
        "auto": "mdi:autorenew",
        "auto (general)": "mdi:autorenew",
        "allergen": "mdi:flower",
        "medium": "mdi:fan",
        "turbo": "mdi:rocket-launch",
        "sleep": "mdi:power-sleep",
        "night": "mdi:power-sleep",
        "low": "mdi:fan-speed-1",
        "high": "mdi:fan-speed-3",
        "other": "mdi:fan",
    },
    "climate_mode": {
        "off": "mdi:power",
        "heat": "mdi:fire",
        "cool": "mdi:snowflake",
        "heat_cool": "mdi:sun-snowflake-variant",
        "auto": "mdi:thermostat-auto",
        "dry": "mdi:water-percent",
        "fan_only": "mdi:fan",
    },
    "climate_preset": {
        "none": "mdi:circle-off-outline",
        "eco": "mdi:leaf",
        "boost": "mdi:rocket-launch",
        "away": "mdi:home-export-outline",
        "sleep": "mdi:power-sleep",
        "comfort": "mdi:sofa",
        "home": "mdi:home",
    },
    "charger": {
        "stopped": "mdi:stop-circle-outline",
        "eco": "mdi:leaf",
        "eco+": "mdi:solar-power",
        "fast": "mdi:lightning-bolt",
    },
    "cover": {
        "open": "mdi:arrow-up",
        "stop": "mdi:stop",
        "close": "mdi:arrow-down",
    },
    "co": {
        "test": "mdi:bell-ring",
        "mute": "mdi:volume-off",
    },
}

# What the editor calls each group.
GROUP_NAMES: dict[str, str] = {
    "fan": "Fan",
    "purifier": "Air purifier",
    "climate_mode": "Thermostat modes",
    "climate_preset": "Thermostat presets",
    "charger": "Car charger",
    "cover": "Blinds and covers",
    "co": "CO alarm",
}

ICON_RE = re.compile(r"^[a-z0-9_-]+:[a-z0-9_-]+$")


class Icons:
    """The built-in icons with the user's changes on top, kept in storage."""

    def __init__(self, hass: HomeAssistant) -> None:
        self._store: Store[dict] = Store(hass, 1, STORE_KEY)
        self.overrides: dict[str, dict[str, str]] = {}

    async def async_load(self) -> None:
        stored = await self._store.async_load() or {}
        # Only keep what's still a known icon slot with a valid icon.
        for group, keys in (stored.get("overrides") or {}).items():
            for key, icon in (keys or {}).items():
                if key in DEFAULTS.get(group, {}) and isinstance(icon, str) and ICON_RE.match(icon):
                    self.overrides.setdefault(group, {})[key] = icon

    def icons(self) -> dict[str, dict[str, str]]:
        """Every slot's icon: the user's, else the built-in one."""
        return {
            group: {key: self.overrides.get(group, {}).get(key, icon) for key, icon in keys.items()}
            for group, keys in DEFAULTS.items()
        }

    def as_dict(self) -> dict[str, Any]:
        return {
            "icons": self.icons(),
            "defaults": DEFAULTS,
            "overrides": self.overrides,
            "groups": GROUP_NAMES,
        }

    async def async_set(self, group: str, key: str, icon: str | None) -> None:
        """Change a slot's icon, or put the built-in one back with None."""
        if key not in DEFAULTS.get(group, {}):
            raise ValueError("That isn't an icon the cards use")
        if icon in (None, "", DEFAULTS[group][key]):
            self.overrides.get(group, {}).pop(key, None)
            if group in self.overrides and not self.overrides[group]:
                del self.overrides[group]
        elif ICON_RE.match(icon):
            self.overrides.setdefault(group, {})[key] = icon
        else:
            raise ValueError("An icon looks like mdi:fan")
        await self._store.async_save({"overrides": self.overrides})
