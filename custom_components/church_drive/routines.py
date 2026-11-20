"""Clive's scheduled actions: things done to devices at a time, once or on repeat (rules in routines_logic.py).

Every minute this looks for routines that are due and sends their service calls. A one-off is marked done afterwards (it stays in the
list, greyed, so it can be seen and removed); a repeating one carries on. A call Home Assistant refuses is tried up to three times, a minute
apart. If Home Assistant was off through the time, anything up to 30 minutes late is still done when it starts again.

Kept in .storage/church_drive.routines. Made by Clive (the services church_drive.schedule_action, list_schedules and cancel_schedule) and
looked after in the app's Schedules page (websocket church_drive/routines, routines/set and routines/delete).
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

from .routines_logic import MAX_ROUTINES, call_for, clean_routine, clean_routines, next_run, occurrences, summary

_LOGGER = logging.getLogger(__name__)

STORE_VERSION = 1
EVENT_CHANGED = "church_drive_routines_changed"
TRIES = 3
BETWEEN_TRIES = 60
CALL_TIMEOUT = 30
CATCH_UP = 30 * 60  # seconds


class Routines:
    """Runs the scheduled actions and keeps them across restarts."""

    def __init__(self, hass: HomeAssistant) -> None:
        self.hass = hass
        self._store: Store = Store(hass, STORE_VERSION, "church_drive.routines")
        self._items: dict[str, dict[str, Any]] = {}
        self._last_run: str | None = None
        self._last_result: dict[str, dict[str, Any]] = {}
        self._unsub: list[Callable[[], None]] = []
        self._tasks: dict[str, asyncio.Task] = {}

    async def async_start(self) -> None:
        data = await self._store.async_load() or {}
        now = dt_util.now()
        self._items = clean_routines(data.get("routines"), now)
        self._last_run = data.get("last_run")
        results = data.get("last_result")
        self._last_result = {k: v for k, v in results.items() if isinstance(v, dict)} if isinstance(results, dict) else {}
        self._unsub.append(async_track_time_change(self.hass, self._on_minute, second=0))
        last = dt_util.parse_datetime(self._last_run) if self._last_run else None
        if last is not None:
            start = max(dt_util.as_local(last), now - timedelta(seconds=CATCH_UP))
            for item in list(self._items.values()):
                if item["enabled"] and occurrences(item, start, now):
                    _LOGGER.info("Routines: catching up %s", item["name"])
                    self._start(item["id"])
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
        await self._store.async_save({"routines": self._items, "last_run": self._last_run, "last_result": self._last_result})

    # ---- what's set ----------------------------------------------------------

    def _view(self, item: dict[str, Any]) -> dict[str, Any]:
        now = dt_util.now()
        nxt = next_run(item, now)
        return {**item, "summary": summary(item, now), "next": nxt.isoformat(timespec="minutes") if nxt else None, "last": self._last_result.get(item["id"])}

    def all(self) -> list[dict[str, Any]]:
        views = [self._view(i) for i in self._items.values()]
        # Coming up first (soonest first), then the ones that are off or done.
        return sorted(views, key=lambda v: (v["next"] is None, v["next"] or "", v["name"]))

    async def async_add(self, raw: dict[str, Any]) -> dict[str, Any]:
        if len(self._items) >= MAX_ROUTINES:
            raise ValueError("There are as many scheduled actions as can be kept: remove one first")
        item = clean_routine({k: v for k, v in raw.items() if k != "id"}, dt_util.now())
        if item["when"]["kind"] == "once" and next_run(item, dt_util.now()) is None:
            raise ValueError("That time has already gone")
        self._items[item["id"]] = item
        await self._async_save()
        self.hass.bus.async_fire(EVENT_CHANGED, {"id": item["id"]})
        return self._view(item)

    async def async_update(self, rid: str, change: dict[str, Any]) -> dict[str, Any]:
        old = self._items.get(rid)
        if old is None:
            raise ValueError("No such scheduled action")
        item = clean_routine(change, dt_util.now(), old)
        self._items[rid] = item
        await self._async_save()
        self.hass.bus.async_fire(EVENT_CHANGED, {"id": rid})
        return self._view(item)

    async def async_remove(self, rid: str) -> bool:
        if self._items.pop(rid, None) is None:
            return False
        self._last_result.pop(rid, None)
        task = self._tasks.pop(rid, None)
        if task and not task.done():
            task.cancel()
        await self._async_save()
        self.hass.bus.async_fire(EVENT_CHANGED, {"id": rid})
        return True

    def find(self, text: str) -> list[dict[str, Any]]:
        """Scheduled actions whose id, or name, matches (case aside), for "cancel the blind"."""
        needle = text.strip().lower()
        if not needle:
            return []
        exact = [i for i in self._items.values() if i["id"] == needle or i["name"].lower() == needle]
        return exact or [i for i in self._items.values() if needle in i["name"].lower() or any(needle in e.lower() for e in i["entities"])]

    # ---- running ---------------------------------------------------------------

    @callback
    def _on_minute(self, now: Any) -> None:
        local = dt_util.as_local(now)
        self.hass.async_create_task(self._async_save(local))
        for item in list(self._items.values()):
            if item["enabled"] and occurrences(item, local - timedelta(seconds=1), local):
                self._start(item["id"])

    def _start(self, rid: str) -> None:
        old = self._tasks.get(rid)
        if old and not old.done():
            old.cancel()
        self._tasks[rid] = self.hass.async_create_task(self._async_run(rid))

    async def _async_run(self, rid: str) -> None:
        item = self._items.get(rid)
        if item is None:
            return
        failed: list[str] = []
        pending = list(item["entities"])
        tries = 0
        try:
            for tries in range(1, TRIES + 1):
                still = []
                for entity in pending:
                    state = self.hass.states.get(entity)
                    if state is None:
                        failed.append(entity)
                        continue
                    ok = False
                    if state.state != "unavailable":
                        try:
                            async with asyncio.timeout(CALL_TIMEOUT):
                                domain, service, data = call_for(entity, item["action"], item["value"])
                                await self.hass.services.async_call(domain, service, data, blocking=True)
                            ok = True
                        except Exception as err:  # noqa: BLE001 - a device that doesn't answer is tried again
                            _LOGGER.debug("Routines: %s didn't take %s (try %s): %s", entity, item["action"], tries, err)
                    if not ok:
                        still.append(entity)
                pending = still
                if not pending or tries == TRIES:
                    break
                await asyncio.sleep(BETWEEN_TRIES)
        except asyncio.CancelledError:
            raise
        failed += pending
        if failed:
            _LOGGER.warning("Routines: %s didn't reach %s", item["name"], ", ".join(failed))
        self._last_result[rid] = {"at": dt_util.now().isoformat(timespec="minutes"), "ok": not failed, "failed": failed, "tries": tries}
        current = self._items.get(rid)
        if current is not None and current["when"]["kind"] == "once":
            current["done"] = dt_util.now().isoformat(timespec="minutes")
        await self._async_save()
        self.hass.bus.async_fire(EVENT_CHANGED, {"id": rid})
