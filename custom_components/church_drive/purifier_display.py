"""The air purifier's display brightness by mode (the rules are in purifier_display_logic.py).

Each of the purifier's modes can turn its display off, set it dim or bright, or leave it alone; the default is Sleep off and the rest left.
It is set when the mode changes (by the app, a schedule, voice or the purifier's own button), then checked after a short wait and tried
again (up to three times) because the Philips purifier sometimes misses a command. Nothing is done at start-up or when the purifier comes
back from being unavailable, and a display changed by hand stays as it is until the next mode change.

What's chosen is kept in .storage/church_drive.purifier_display and set from the purifier's device page in the app. Administrators only.
"""

from __future__ import annotations

import asyncio
import logging
from typing import Any, Callable

from homeassistant.core import Event, HomeAssistant, callback
from homeassistant.helpers.event import async_track_state_change_event
from homeassistant.helpers.storage import Store

from .purifier_display_logic import (
    BRIGHTNESS,
    CHOICES,
    DEFAULTS,
    call_for,
    choice_for,
    clean_config,
    display_light,
    should_apply,
    summary,
)

_LOGGER = logging.getLogger(__name__)

STORE_VERSION = 1
EVENT_CHANGED = "church_drive_purifier_display_changed"
TRIES = 3
WAIT = 20  # seconds to wait for the display to report the change
CALL_TIMEOUT = 30


class PurifierDisplay:
    """Sets the purifier's display when its mode changes, and keeps the choices across restarts."""

    def __init__(self, hass: HomeAssistant) -> None:
        self.hass = hass
        self._store: Store = Store(hass, STORE_VERSION, "church_drive.purifier_display")
        self._cfg: dict[str, Any] = clean_config(DEFAULTS)
        self._unsub: Callable[[], None] | None = None
        self._task: asyncio.Task | None = None

    async def async_start(self) -> None:
        data = await self._store.async_load()
        self._cfg = clean_config(data, {"enabled": True, "entity": DEFAULTS["entity"], "modes": {}}) if data else clean_config(DEFAULTS)
        self._listen()

    @callback
    def async_stop(self) -> None:
        if self._unsub:
            self._unsub()
            self._unsub = None
        if self._task and not self._task.done():
            self._task.cancel()

    def _listen(self) -> None:
        if self._unsub:
            self._unsub()
        self._unsub = async_track_state_change_event(self.hass, [self._cfg["entity"]], self._on_state)

    # ---- what's set --------------------------------------------------------

    def _light(self) -> str | None:
        return display_light([s.entity_id for s in self.hass.states.async_all("light")], self._cfg["entity"])

    def status(self) -> dict[str, Any]:
        fan = self.hass.states.get(self._cfg["entity"])
        presets = [p for p in (fan.attributes.get("preset_modes") or []) if isinstance(p, str)] if fan else []
        return {
            "enabled": self._cfg["enabled"],
            "entity": self._cfg["entity"],
            "display": self._light(),
            "modes": dict(self._cfg["modes"]),
            "preset_modes": presets,
            "choices": list(CHOICES),
            "summary": summary(self._cfg),
        }

    async def async_set(self, change: dict[str, Any]) -> dict[str, Any]:
        """Change the choices or whether they run (only what's sent changes)."""
        before = self._cfg["entity"]
        self._cfg = clean_config(change, self._cfg)
        if self._cfg["entity"] != before:
            self._listen()
        await self._store.async_save(self._cfg)
        self.hass.bus.async_fire(EVENT_CHANGED)
        return self.status()

    # ---- doing it ----------------------------------------------------------

    @callback
    def _on_state(self, event: Event) -> None:
        if not self._cfg["enabled"]:
            return
        old, new = event.data.get("old_state"), event.data.get("new_state")
        if old is None or new is None:
            return
        if not should_apply(old.state, old.attributes.get("preset_mode"), new.state, new.attributes.get("preset_mode")):
            return
        choice = choice_for(self._cfg, new.attributes.get("preset_mode"))
        if choice == "keep":
            return
        if self._task and not self._task.done():
            self._task.cancel()
        self._task = self.hass.async_create_task(self._async_apply(choice))

    async def _async_apply(self, choice: str) -> None:
        light = self._light()
        if light is None:
            _LOGGER.debug("Purifier display: no display light found for %s", self._cfg["entity"])
            return
        call = call_for(choice, light)
        if call is None:
            return
        service, data = call
        for attempt in range(1, TRIES + 1):
            try:
                async with asyncio.timeout(CALL_TIMEOUT):
                    await self.hass.services.async_call("light", service, data, blocking=True)
            except Exception as err:  # noqa: BLE001
                _LOGGER.debug("Purifier display: %s didn't take %s (try %s): %s", light, choice, attempt, err)
            await asyncio.sleep(WAIT)
            state = self.hass.states.get(light)
            if state is None:
                continue
            if choice == "off" and state.state == "off":
                return
            if choice in BRIGHTNESS and state.state == "on" and _near(state.attributes.get("brightness"), BRIGHTNESS[choice]):
                return
        _LOGGER.warning("Purifier display: %s did not change to %s after %s tries", light, choice, TRIES)


def _near(value: Any, wanted: int) -> bool:
    """Whether a reported brightness is the wanted step (the purifier reports anything from 1 to 254 as 128)."""
    try:
        return abs(int(value) - wanted) <= 1
    except (TypeError, ValueError):
        return False
