"""The watch companion's settings, kept in Home Assistant's storage (the rules are in watch_logic.py)."""

from __future__ import annotations

from typing import Any

from homeassistant.core import HomeAssistant
from homeassistant.helpers.storage import Store

from .watch_logic import clean_config

STORE_KEY = "church_drive.watch"


class WatchSettings:
    """Which pages and items the watch shows, set in the Manager dashboard and read by the watch app."""

    def __init__(self, hass: HomeAssistant) -> None:
        self._store: Store[dict] = Store(hass, 1, STORE_KEY)
        self.config: dict[str, Any] = clean_config(None)

    async def async_load(self) -> None:
        self.config = clean_config(await self._store.async_load())

    async def async_set(self, raw: Any) -> dict[str, Any]:
        """Keep the settings (made safe first) and give back what was kept."""
        self.config = clean_config(raw)
        await self._store.async_save(self.config)
        return self.config
