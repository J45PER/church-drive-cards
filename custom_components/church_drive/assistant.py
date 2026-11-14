"""What the apps are told about the assistant: done-card motions and misheard names, with the owner's changes kept in Home Assistant's storage.

So a fix to either reaches the apps without an app or integration update (rules in assistant_logic.py).
"""

from __future__ import annotations

import json
from typing import Any

from homeassistant.core import HomeAssistant
from homeassistant.helpers.storage import Store

from .assistant_logic import clean_heard, clean_rules, first_names

STORE_KEY = "church_drive.assistant"


def _shipped(path: str) -> dict[str, Any]:
    try:
        with open(path, encoding="utf-8") as fh:
            cards = json.load(fh)
        return {"motions": clean_rules(cards["done_motions"]["rules"]), "heard": clean_heard(cards["heard"]["corrections"])}
    except (OSError, ValueError, KeyError, TypeError):
        return {"motions": [], "heard": {}}


class AssistantData:
    """The shipped motions and corrections; either is replaced whole by the owner's own, when they have set one."""

    def __init__(self, hass: HomeAssistant, shipped_path: str) -> None:
        self._hass = hass
        self._path = shipped_path
        self._store: Store[dict] = Store(hass, 1, STORE_KEY)
        self._defaults: dict[str, Any] = {"motions": [], "heard": {}}
        self._own: dict[str, Any] = {}
        self.revision = 0

    async def async_load(self) -> None:
        self._defaults = await self._hass.async_add_executor_job(_shipped, self._path)
        saved = await self._store.async_load() or {}
        self._own = {k: v for k, v in (saved.get("own") or {}).items() if k in ("motions", "heard")}
        self.revision = int(saved.get("revision") or 0)

    @property
    def config(self) -> dict[str, Any]:
        people = [s.name for s in self._hass.states.async_all("person")]
        return {
            "motions": self._own.get("motions", self._defaults["motions"]),
            "heard": self._own.get("heard", self._defaults["heard"]),
            "names": first_names(sorted(people)),
            "revision": self.revision,
        }

    async def async_set(self, motions: Any = None, heard: Any = None, reset: bool = False) -> dict[str, Any]:
        """Replace the motions and/or the corrections with these (made safe first); [reset] goes back to the shipped ones."""
        if reset:
            self._own = {}
        if motions is not None:
            self._own["motions"] = clean_rules(motions)
        if heard is not None:
            self._own["heard"] = clean_heard(heard)
        self.revision += 1
        await self._store.async_save({"own": self._own, "revision": self.revision})
        return self.config
