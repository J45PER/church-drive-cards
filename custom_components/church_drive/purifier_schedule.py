"""The air purifier's Auto/Sleep schedules (the rules are in purifier_schedule_logic.py).

Each schedule is a set of days and two times, shown as "Mon-Fri Auto: 07:00-23:30": the purifier goes to Auto at
the first time and to Sleep at the second. Every minute this looks for a change that is due and sends it, then
checks the purifier really changed mode and tries again (up to five times, a minute apart) if it didn't, because
the Philips purifier sometimes drops off the network and misses a command. If Home Assistant was off through a
scheduled minute, the latest missed change (up to 30 minutes old) is sent when it starts again.

A purifier that is switched off is left off: a schedule changes its mode, it never turns it on. What's chosen is
kept in .storage/church_drive.purifier_schedule and set from the Purifier schedule card (web and app).
"""

from __future__ import annotations

import asyncio
import logging
from datetime import timedelta
from typing import Any, Callable

from homeassistant.core import HomeAssistant, callback
from homeassistant.helpers.event import async_track_time_change
from homeassistant.helpers.storage import Store
from homeassistant.util import dt as dt_util

from .purifier_schedule_logic import (
    DEFAULTS,
    clean_config,
    due_between,
    events_at,
    mode_for,
    next_change,
    summary,
)

_LOGGER = logging.getLogger(__name__)

STORE_VERSION = 1
EVENT_CHANGED = "church_drive_purifier_schedule_changed"
TRIES = 5
WAIT_FOR_MODE = 30  # seconds to wait for the purifier to report the new mode
BETWEEN_TRIES = 60
CALL_TIMEOUT = 30
CATCH_UP = 30 * 60  # seconds: a change missed by more than this while HA was off is not sent late


class PurifierSchedule:
    """Applies the purifier schedules and keeps them across restarts."""

    def __init__(self, hass: HomeAssistant) -> None:
        self.hass = hass
        self._store: Store = Store(hass, STORE_VERSION, "church_drive.purifier_schedule")
        self._cfg: dict[str, Any] = clean_config(DEFAULTS)
        self._last_run: str | None = None  # ISO time of the last minute looked at
        self._last_result: dict[str, Any] | None = None  # {"mode", "at", "ok", "tries"}
        self._unsub: list[Callable[[], None]] = []
        self._task: asyncio.Task | None = None

    # ---- life ------------------------------------------------------------

    async def async_start(self) -> None:
        data = await self._store.async_load() or {}
        self._cfg = clean_config(data)
        self._last_run = data.get("last_run")
        self._last_result = data.get("last_result")
        self._unsub.append(async_track_time_change(self.hass, self._on_minute, second=0))
        # Changes missed while Home Assistant was off.
        now = dt_util.now()
        last = dt_util.parse_datetime(self._last_run) if self._last_run else None
        if last is not None and self._cfg["enabled"]:
            last = dt_util.as_local(last)
            mode = due_between(self._cfg["schedules"], max(last, now - timedelta(seconds=CATCH_UP)), now)
            if mode:
                _LOGGER.info("Purifier schedule: catching up a missed change to %s", mode)
                self._start(mode)
        await self._async_save(now)

    @callback
    def async_stop(self) -> None:
        for unsub in self._unsub:
            unsub()
        self._unsub.clear()
        if self._task and not self._task.done():
            self._task.cancel()

    async def _async_save(self, now: Any = None) -> None:
        if now is not None:
            self._last_run = dt_util.as_local(now).isoformat()
        await self._store.async_save({**self._cfg, "last_run": self._last_run, "last_result": self._last_result})

    # ---- what's set --------------------------------------------------------

    def status(self) -> dict[str, Any]:
        schedules = [{**s, "summary": summary(s)} for s in self._cfg["schedules"]]
        state = self.hass.states.get(self._cfg["entity"])
        return {
            "enabled": self._cfg["enabled"],
            "entity": self._cfg["entity"],
            "schedules": schedules,
            "next": next_change(self._cfg["schedules"], dt_util.now()) if self._cfg["enabled"] else None,
            "mode": state.attributes.get("preset_mode") if state else None,
            "state": state.state if state else None,
            "last": self._last_result,
        }

    async def async_set(self, change: dict[str, Any]) -> dict[str, Any]:
        """Change the schedules, whether they run, or the purifier (only what's sent changes)."""
        self._cfg = clean_config(change, self._cfg)
        await self._async_save()
        self.hass.bus.async_fire(EVENT_CHANGED)
        return self.status()

    # ---- running -----------------------------------------------------------

    @callback
    def _on_minute(self, now: Any) -> None:
        local = dt_util.as_local(now)
        self.hass.async_create_task(self._async_save(local))
        if not self._cfg["enabled"]:
            return
        mode = mode_for(events_at(self._cfg["schedules"], local.weekday(), local.strftime("%H:%M")))
        if mode:
            self._start(mode)

    def _start(self, mode: str) -> None:
        # A newer change replaces one still trying (Sleep at night must not keep fighting a morning Auto).
        if self._task and not self._task.done():
            self._task.cancel()
        self._task = self.hass.async_create_task(self._async_apply(mode))

    async def _async_apply(self, mode: str) -> None:
        entity = self._cfg["entity"]
        tries = 0
        ok = False
        skipped = ""
        try:
            for tries in range(1, TRIES + 1):
                state = self.hass.states.get(entity)
                if state is not None and state.state == "off":
                    skipped = "switched off"
                    break
                if state is not None and state.attributes.get("preset_mode") == mode:
                    ok = True
                    break
                if state is not None and state.state not in ("unavailable", "unknown"):
                    try:
                        async with asyncio.timeout(CALL_TIMEOUT):
                            await self.hass.services.async_call(
                                "fan", "set_preset_mode", {"entity_id": entity, "preset_mode": mode}, blocking=True
                            )
                    except Exception as err:  # noqa: BLE001 - a purifier that doesn't answer is tried again
                        _LOGGER.debug("Purifier schedule: %s didn't take %s (try %s): %s", entity, mode, tries, err)
                for _ in range(WAIT_FOR_MODE):
                    await asyncio.sleep(1)
                    now_state = self.hass.states.get(entity)
                    if now_state is not None and now_state.attributes.get("preset_mode") == mode:
                        ok = True
                        break
                if ok:
                    break
                if tries < TRIES:
                    await asyncio.sleep(max(0, BETWEEN_TRIES - WAIT_FOR_MODE))
        except asyncio.CancelledError:
            raise
        if not ok and not skipped:
            _LOGGER.warning("Purifier schedule: %s did not change to %s after %s tries", entity, mode, tries)
        self._last_result = {"mode": mode, "at": dt_util.now().isoformat(), "ok": ok, "tries": tries, **({"skipped": skipped} if skipped else {})}
        await self._async_save()
        self.hass.bus.async_fire(EVENT_CHANGED)
