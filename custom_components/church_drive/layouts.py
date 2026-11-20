"""Each person's own layout, kept in the house against their Home Assistant user (rules in layout_logic.py).

Kept in .storage/church_drive.layouts. A person reads and sets only their own; it follows them between phones.
"""

from __future__ import annotations

from typing import Any

from homeassistant.core import HomeAssistant
from homeassistant.helpers.storage import Store

from .layout_logic import clean_layout

STORE_VERSION = 1
EVENT_CHANGED = "church_drive_layout_changed"


class Layouts:
    def __init__(self, hass: HomeAssistant) -> None:
        self.hass = hass
        self._store: Store = Store(hass, STORE_VERSION, "church_drive.layouts")
        self._by_user: dict[str, dict[str, Any]] = {}

    async def async_load(self) -> None:
        data = await self._store.async_load() or {}
        users = data.get("users")
        self._by_user = {u: clean_layout(layout) for u, layout in users.items() if isinstance(u, str)} if isinstance(users, dict) else {}

    def get(self, user_id: str | None) -> dict[str, Any]:
        return self._by_user.get(user_id or "", {"pages": {}})

    async def async_set(self, user_id: str | None, layout: Any) -> dict[str, Any]:
        """Replace this person's layout (cleaned); an empty one is forgotten."""
        if not user_id:
            raise ValueError("Not signed in")
        cleaned = clean_layout(layout)
        if cleaned["pages"]:
            self._by_user[user_id] = cleaned
        else:
            self._by_user.pop(user_id, None)
        await self._store.async_save({"users": self._by_user})
        self.hass.bus.async_fire(EVENT_CHANGED, {"user_id": user_id})
        return cleaned
