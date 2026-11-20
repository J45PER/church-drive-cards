"""The saved colours for lights, kept in the house for everyone (rules in saved_colours_logic.py).

Kept in .storage/church_drive.saved_colours. Anyone signed in can add or remove one from a light's settings; the Admin app lists them too.
"""

from __future__ import annotations

from typing import Any

from homeassistant.core import HomeAssistant
from homeassistant.helpers.storage import Store

from .saved_colours_logic import clean

STORE_VERSION = 1
EVENT_CHANGED = "church_drive_saved_colours_changed"


class SavedColours:
    def __init__(self, hass: HomeAssistant) -> None:
        self.hass = hass
        self._store: Store = Store(hass, STORE_VERSION, "church_drive.saved_colours")
        self._colours: list[list[float]] = []

    async def async_load(self) -> None:
        data = await self._store.async_load() or {}
        self._colours = clean(data.get("colours"))

    def listing(self) -> dict[str, Any]:
        return {"colours": [list(c) for c in self._colours]}

    async def async_set(self, colours: Any) -> dict[str, Any]:
        """Replace the list (cleaned: at most eight, none alike)."""
        self._colours = clean(colours)
        await self._store.async_save({"colours": self._colours})
        self.hass.bus.async_fire(EVENT_CHANGED, {})
        return self.listing()
