"""A website edit waiting to be checked, as a to-do task for each administrator (rules in edit_tasks_logic.py).

The Manager's proposals (a change to the colours, icons or layout made on the website) wait for an administrator to look at them. This keeps one
task for each on every administrator's own list ("Colours edit waiting"), so it shows in the To-Do app and in the apps' task summaries like any task,
and takes it off again when the edit has been applied or turned down. Tasks are matched by a marker in their description, so it is safe to run again.
"""

from __future__ import annotations

import asyncio
import logging
from typing import Any, Callable

from homeassistant.core import HomeAssistant, callback
from homeassistant.helpers.dispatcher import async_dispatcher_connect
from homeassistant.helpers.start import async_at_started

from .const import DOMAIN
from .edit_tasks_logic import marker_of, task_description, task_summary
from .proposals import SIGNAL_PROPOSALS

_LOGGER = logging.getLogger(__name__)
SETTLE_S = 3


class EditTasks:
    def __init__(self, hass: HomeAssistant) -> None:
        self.hass = hass
        self._unsubs: list[Callable[[], None]] = []
        self._pending: asyncio.Task | None = None

    async def async_start(self) -> None:
        self._unsubs.append(async_dispatcher_connect(self.hass, SIGNAL_PROPOSALS, self._changed))
        self._unsubs.append(async_at_started(self.hass, self._changed))

    @callback
    def async_stop(self) -> None:
        for unsub in self._unsubs:
            unsub()
        self._unsubs = []
        if self._pending is not None and not self._pending.done():
            self._pending.cancel()

    @callback
    def _changed(self, *_: Any) -> None:
        """Something about the proposals changed: sync a moment later (a burst of changes is one sync)."""
        if self._pending is not None and not self._pending.done():
            return
        self._pending = self.hass.async_create_task(self._later())

    async def _later(self) -> None:
        await asyncio.sleep(SETTLE_S)
        try:
            await self._sync()
        except Exception:  # noqa: BLE001 - a task that can't be made leaves the website's own list of edits as it was
            _LOGGER.warning("Couldn't keep the edit tasks up to date", exc_info=True)

    async def _sync(self) -> None:
        data = self.hass.data.get(DOMAIN, {})
        proposals, people = data.get("proposals"), data.get("people")
        if proposals is None or people is None:
            return
        pending = {p["id"]: p for p in proposals.pending}
        lists = [p["list"] for p in people.people() if p.get("admin") and p.get("list") and self.hass.states.get(p["list"]) is not None]
        for entity in lists:
            got = await self.hass.services.async_call("todo", "get_items", {"entity_id": entity, "status": ["needs_action"]}, blocking=True, return_response=True)
            items = (got or {}).get(entity, {}).get("items", [])
            have = {pid: item for item in items if (pid := marker_of(item.get("description")))}
            for pid, proposal in pending.items():
                if pid not in have:
                    await self.hass.services.async_call(
                        "todo", "add_item", {"entity_id": entity, "item": task_summary(proposal), "description": task_description(pid)}, blocking=True,
                    )
            for pid, item in have.items():
                if pid not in pending:
                    await self.hass.services.async_call("todo", "remove_item", {"entity_id": entity, "item": item["uid"]}, blocking=True)
