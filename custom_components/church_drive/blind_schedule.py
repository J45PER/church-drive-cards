"""Blind schedules: each blind opens and closes at set times on set days (the rules are in blind_schedule_logic.py).

Every minute this looks, for each blind with a schedule, for a change that is due and sends it (cover.open_cover or
cover.close_cover). A radio-controlled blind never says where it is, so there is nothing to check afterwards: the
call is made, and tried again (up to three times, a minute apart) only if the blind is unavailable or Home
Assistant refuses the call. If Home Assistant was off through a scheduled minute, the latest missed change (up to
30 minutes old) is sent when it starts again.

What's chosen is kept in .storage/church_drive.blind_schedule and set from the blind's card in the app (press and
hold the card, then Schedule). Administrators only.
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

from .blind_schedule_logic import (
    action_for,
    clean_blind,
    clean_config,
    due_between,
    events_at,
    next_change,
    summary,
)

_LOGGER = logging.getLogger(__name__)

STORE_VERSION = 1
EVENT_CHANGED = "church_drive_blind_schedule_changed"
TRIES = 3
BETWEEN_TRIES = 60
CALL_TIMEOUT = 30
CATCH_UP = 30 * 60  # seconds: a change missed by more than this while HA was off is not sent late
SERVICES = {"open": "open_cover", "close": "close_cover"}


class BlindSchedule:
    """Applies the blinds' schedules and keeps them across restarts."""

    def __init__(self, hass: HomeAssistant) -> None:
        self.hass = hass
        self._store: Store = Store(hass, STORE_VERSION, "church_drive.blind_schedule")
        self._cfg: dict[str, dict[str, Any]] = {}
        self._last_run: str | None = None  # ISO time of the last minute looked at
        self._last_result: dict[str, dict[str, Any]] = {}  # by entity: {"action", "at", "ok", "tries"}
        self._unsub: list[Callable[[], None]] = []
        self._tasks: dict[str, asyncio.Task] = {}

    # ---- life ------------------------------------------------------------

    async def async_start(self) -> None:
        data = await self._store.async_load() or {}
        self._cfg = clean_config(data)
        self._last_run = data.get("last_run")
        results = data.get("last_result")
        self._last_result = {k: v for k, v in results.items() if isinstance(v, dict)} if isinstance(results, dict) else {}
        self._unsub.append(async_track_time_change(self.hass, self._on_minute, second=0))
        # Changes missed while Home Assistant was off.
        now = dt_util.now()
        last = dt_util.parse_datetime(self._last_run) if self._last_run else None
        if last is not None:
            last = dt_util.as_local(last)
            start = max(last, now - timedelta(seconds=CATCH_UP))
            for entity, blind in self._cfg.items():
                if not blind["enabled"]:
                    continue
                action = due_between(blind["schedules"], start, now)
                if action:
                    _LOGGER.info("Blind schedule: catching up a missed change for %s: %s", entity, action)
                    self._start(entity, action)
        await self._async_save(now)

    @callback
    def async_stop(self) -> None:
        for unsub in self._unsub:
            unsub()
        self._unsub.clear()
        for task in self._tasks.values():
            if not task.done():
                task.cancel()
        self._tasks.clear()

    async def _async_save(self, now: Any = None) -> None:
        if now is not None:
            self._last_run = dt_util.as_local(now).isoformat()
        await self._store.async_save({"blinds": self._cfg, "last_run": self._last_run, "last_result": self._last_result})

    # ---- what's set --------------------------------------------------------

    def status(self, entity: str) -> dict[str, Any]:
        blind = self._cfg.get(entity) or clean_blind(None)
        schedules = [{**s, "summary": summary(s)} for s in blind["schedules"]]
        state = self.hass.states.get(entity)
        return {
            "entity": entity,
            "enabled": blind["enabled"],
            "schedules": schedules,
            "next": next_change(blind["schedules"], dt_util.now()) if blind["enabled"] else None,
            "state": state.state if state else None,
            "last": self._last_result.get(entity),
        }

    async def async_set(self, entity: str, change: dict[str, Any]) -> dict[str, Any]:
        """Change one blind's schedules or whether they run (only what's sent changes; a schedule list replaces the old one)."""
        if not entity.startswith("cover."):
            raise ValueError("A schedule is for a cover entity")
        self._cfg[entity] = clean_blind(change, self._cfg.get(entity))
        await self._async_save()
        self.hass.bus.async_fire(EVENT_CHANGED, {"entity_id": entity})
        return self.status(entity)

    # ---- running -----------------------------------------------------------

    @callback
    def _on_minute(self, now: Any) -> None:
        local = dt_util.as_local(now)
        self.hass.async_create_task(self._async_save(local))
        for entity, blind in self._cfg.items():
            if not blind["enabled"]:
                continue
            action = action_for(events_at(blind["schedules"], local.weekday(), local.strftime("%H:%M")))
            if action:
                self._start(entity, action)

    def _start(self, entity: str, action: str) -> None:
        # A newer change replaces one still trying (a morning Open must not keep fighting an evening Close).
        old = self._tasks.get(entity)
        if old and not old.done():
            old.cancel()
        self._tasks[entity] = self.hass.async_create_task(self._async_apply(entity, action))

    async def _async_apply(self, entity: str, action: str) -> None:
        tries = 0
        ok = False
        skipped = ""
        try:
            for tries in range(1, TRIES + 1):
                state = self.hass.states.get(entity)
                if state is None:
                    skipped = "not found"
                    break
                if state.state not in ("unavailable",):
                    try:
                        async with asyncio.timeout(CALL_TIMEOUT):
                            await self.hass.services.async_call("cover", SERVICES[action], {"entity_id": entity}, blocking=True)
                        ok = True
                        break
                    except Exception as err:  # noqa: BLE001 - a blind that doesn't answer is tried again
                        _LOGGER.debug("Blind schedule: %s didn't take %s (try %s): %s", entity, action, tries, err)
                if tries < TRIES:
                    await asyncio.sleep(BETWEEN_TRIES)
        except asyncio.CancelledError:
            raise
        if not ok and not skipped:
            _LOGGER.warning("Blind schedule: %s did not %s after %s tries", entity, action, tries)
        self._last_result[entity] = {"action": action, "at": dt_util.now().isoformat(), "ok": ok, "tries": tries, **({"skipped": skipped} if skipped else {})}
        await self._async_save()
        self.hass.bus.async_fire(EVENT_CHANGED, {"entity_id": entity})
