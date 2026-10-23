"""Does what a notification's buttons ask in the house: lights on, vacuum to its dock, everything off, charging."""

from __future__ import annotations

import logging
import re
from typing import Any

from homeassistant.core import Event, HomeAssistant, callback

from .notice_actions_logic import HOUSE_VERBS, split_action

_LOGGER = logging.getLogger(__name__)

EVENT_NOTICE_ACTION = "church_drive_notice_action"  # fired by the Church Drive app
EVENT_APP_ACTION = "mobile_app_notification_action"  # fired by the Home Assistant app
ZAPPI_MODE = "select.zappi_charge_mode"
ZAPPI_LOCKED = "binary_sensor.zappi_locked"
NOT_REAL = re.compile(r"backlight|display|indicator|status")


class NoticeActions:
    """Listens for a notification button being pressed and does it (only the few things listed in `HOUSE_VERBS`)."""

    def __init__(self, hass: HomeAssistant) -> None:
        self.hass = hass
        self._unsubs: list[Any] = []

    def async_start(self) -> None:
        self._unsubs = [
            self.hass.bus.async_listen(EVENT_NOTICE_ACTION, self._on_event),
            self.hass.bus.async_listen(EVENT_APP_ACTION, self._on_event),
        ]

    @callback
    def async_stop(self) -> None:
        for unsub in self._unsubs:
            unsub()
        self._unsubs = []

    async def _on_event(self, event: Event) -> None:
        parsed = split_action(str(event.data.get("action") or ""))
        if parsed is None or parsed[0] not in HOUSE_VERBS:
            return  # not ours, or one the phone does for itself
        name = parsed[0]
        try:
            await getattr(self, f"_do_{name}")()
        except Exception as err:  # noqa: BLE001 - a button that fails must not break the listener
            _LOGGER.warning("Notification button %s failed: %s", name, err)

    def _on(self, domain: str) -> list[str]:
        return [
            s.entity_id for s in self.hass.states.async_all(domain)
            if s.state in ("on", "playing", "paused") and not NOT_REAL.search(s.entity_id)
        ]

    async def _call(self, domain: str, service: str, data: dict[str, Any]) -> None:
        await self.hass.services.async_call(domain, service, data, blocking=True)

    async def _do_lights_on(self) -> None:
        off = [s.entity_id for s in self.hass.states.async_all("light") if s.state == "off" and not NOT_REAL.search(s.entity_id)]
        if off:
            await self._call("light", "turn_on", {"entity_id": off, "brightness_pct": 100})

    async def _do_dock(self) -> None:
        vacuums = [s.entity_id for s in self.hass.states.async_all("vacuum")]
        if vacuums:
            await self._call("vacuum", "return_to_base", {"entity_id": vacuums})

    async def _do_all_off(self) -> None:
        for domain in ("light", "media_player", "fan"):
            on = self._on(domain)
            if on:
                await self._call(domain, "turn_off", {"entity_id": on})

    async def _do_car_now(self) -> None:
        locked = self.hass.states.get(ZAPPI_LOCKED)
        if locked is not None and locked.state == "on":
            await self._call("myenergi", "myenergi_unlock", {"entity_id": ZAPPI_MODE})
        await self._call("select", "select_option", {"entity_id": ZAPPI_MODE, "option": "Fast"})

    async def _do_car_pause(self) -> None:
        await self._call("select", "select_option", {"entity_id": ZAPPI_MODE, "option": "Stopped"})

    async def _do_backup(self) -> None:
        await self._call("backup", "create_automatic", {})

    async def _do_clive_resume(self) -> None:
        await self._call("script", "clive_climate_control", {"command": "resume"})
