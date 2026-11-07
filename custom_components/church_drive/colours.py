"""Clive's colour palettes, kept in Home Assistant's storage so a change shows in the apps and on the dashboards at once (rules in colours_logic.py)."""

from __future__ import annotations

import json
from typing import Any

from homeassistant.core import HomeAssistant
from homeassistant.helpers.dispatcher import async_dispatcher_send
from homeassistant.helpers.storage import Store

from .colours_logic import clean_overrides, kind_palettes, merge

STORE_KEY = "church_drive.colours"
SIGNAL_COLOURS = "church_drive_colours_signal"


def _shipped(path: str) -> dict[str, Any]:
    """The palettes the integration ships (the served cards.json), as {key: {name, group, colours}}."""
    try:
        with open(path, encoding="utf-8") as fh:
            return json.load(fh)["entity_colours"]["types"]
    except (OSError, ValueError, KeyError, TypeError):
        return {}


class ColourSettings:
    """The palettes: shipped defaults, with the owner's changes and additions on top."""

    def __init__(self, hass: HomeAssistant, shipped_path: str) -> None:
        self._hass = hass
        self._path = shipped_path
        self._store: Store[dict] = Store(hass, 1, STORE_KEY)
        self._defaults: dict[str, Any] = {}
        self.overrides: dict[str, dict[str, Any]] = {}
        self.revision = 0

    async def async_load(self) -> None:
        self._defaults = await self._hass.async_add_executor_job(_shipped, self._path)
        saved = await self._store.async_load() or {}
        self.overrides = clean_overrides(saved.get("overrides"))
        self.revision = int(saved.get("revision") or 0)

    @property
    def config(self) -> dict[str, Any]:
        palettes = merge(self._defaults, self.overrides)
        return {"revision": self.revision, "palettes": palettes, "kind_palettes": kind_palettes(palettes)}

    async def _save(self) -> dict[str, Any]:
        self.revision += 1
        await self._store.async_save({"overrides": self.overrides, "revision": self.revision})
        async_dispatcher_send(self._hass, SIGNAL_COLOURS)
        return self.config

    async def async_set(self, raw: Any) -> dict[str, Any]:
        """Replace the changes with these (made safe first); a palette not listed goes back to its shipped colours."""
        self.overrides = clean_overrides(raw)
        return await self._save()

    async def async_set_one(self, key: str, name: str | None, colours: list[str] | None) -> dict[str, Any]:
        """Change or add one palette, leaving the others as they are."""
        item: dict[str, Any] = {}
        if name:
            item["name"] = name
        if colours:
            item["colours"] = colours
        merged = dict(self.overrides)
        merged[key] = {**merged.get(key, {}), **item}
        self.overrides = clean_overrides(merged)
        return await self._save()
