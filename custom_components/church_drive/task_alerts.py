"""Reminders ahead of a task's due time and notices for overdue tasks, sent to the people's phones.

A task with a due time can be given a lead (how many minutes before it to be reminded), set from the To-Do app. Every task on a person's
own list or the shared list that has gone overdue is announced once and then each morning until it is done. The lead is kept here with
the list and the task's uid; what has been sent is kept so a restart doesn't repeat it. The rules are in task_alerts_logic.py.

Each notice carries Done and Snooze (the same buttons and handling as place reminders in reminders.py) and opens the task in the To-Do
app when it is on the phone.
"""

from __future__ import annotations

import logging
from datetime import timedelta
from typing import Any, Callable

from homeassistant.core import CALLBACK_TYPE, HomeAssistant, callback
from homeassistant.helpers.dispatcher import async_dispatcher_send
from homeassistant.helpers.event import async_track_time_interval
from homeassistant.helpers.storage import Store
from homeassistant.util import dt as dt_util, slugify

from . import task_alerts_logic as tl
from .reminder_logic import actions_for

_LOGGER = logging.getLogger(__name__)

STORE_VERSION = 1
SIGNAL_TASK_ALERTS = "church_drive_task_alerts"
SHARED_LIST = "todo.priorities_everyone"


class TaskAlerts:
    def __init__(self, hass: HomeAssistant, people: Any) -> None:
        self.hass = hass
        self._people = people
        self._store: Store = Store(hass, STORE_VERSION, "church_drive.task_alerts")
        self._leads: dict[str, int] = {}
        self._sent: dict[str, dict[str, str]] = {}
        self._unsub: list[Callable[[], None]] = []

    async def async_start(self) -> None:
        data = await self._store.async_load() or {}
        self._leads = {k: int(v) for k, v in (data.get("leads") or {}).items()}
        self._sent = dict(data.get("sent") or {})
        self._unsub.append(async_track_time_interval(self.hass, self._on_minute, timedelta(minutes=1)))

    @callback
    def async_stop(self) -> None:
        for unsub in self._unsub:
            unsub()
        self._unsub.clear()

    async def _async_save(self) -> None:
        await self._store.async_save({"leads": self._leads, "sent": self._sent})

    def leads(self) -> dict[str, int]:
        return dict(self._leads)

    async def async_set_lead(self, todo: str, uid: str, lead: Any) -> int | None:
        """Keep a task's lead (None clears it). Returns what was kept."""
        k = tl.key(todo, uid)
        if lead is None:
            self._leads.pop(k, None)
            self._sent.pop(k, None)
            kept = None
        else:
            kept = tl.valid_lead(lead)
            if kept is None:
                raise ValueError(f"Unknown lead: {lead}")
            if not todo.startswith("todo.") or self.hass.states.get(todo) is None:
                raise ValueError(f"Unknown to-do list: {todo}")
            if self._leads.get(k) != kept:
                self._sent.get(k, {}).pop("lead", None)
            self._leads[k] = kept
        await self._async_save()
        async_dispatcher_send(self.hass, SIGNAL_TASK_ALERTS)
        return kept

    # ---- reading the lists ------------------------------------------------

    def _lists(self) -> list[tuple[str, str | None]]:
        """The lists looked at: each person's own (with its owner) and the shared one (no owner)."""
        if not self._people:
            return []
        out: list[tuple[str, str | None]] = [(p["list"], p["first"]) for p in self._people.people() if p.get("list")]
        out.append((SHARED_LIST, None))
        return [(t, o) for t, o in out if self.hass.states.get(t) is not None]

    async def _async_open_tasks(self) -> list[dict[str, Any]]:
        todos = [t for t, _ in self._lists()]
        if not todos:
            return []
        try:
            result = await self.hass.services.async_call(
                "todo", "get_items", {"entity_id": todos, "status": ["needs_action"]}, blocking=True, return_response=True
            )
        except Exception:  # noqa: BLE001 - a list that can't be read isn't a reason to stop the rest
            _LOGGER.debug("Couldn't read the to-do lists for alerts", exc_info=True)
            return []
        owners = dict(self._lists())
        out = []
        for todo in todos:
            for i in ((result or {}).get(todo) or {}).get("items") or []:
                if i.get("due") and i.get("uid"):
                    out.append({"todo": todo, "uid": i["uid"], "summary": i.get("summary", ""), "due": i["due"], "description": i.get("description", ""), "owner": owners.get(todo)})
        return out

    # ---- sending -----------------------------------------------------------

    def _who(self, task: dict[str, Any]) -> list[str]:
        if task["owner"]:
            return [task["owner"]]
        return [p["first"] for p in self._people.people()] if self._people else []

    async def _async_send(self, kind: str, task: dict[str, Any], title: str, message: str) -> None:
        for first in self._who(task):
            try:
                await self._people.async_notify(
                    kind, [first], title, message,
                    tag=f"church-drive-task-{slugify(task['summary'])}",
                    link=f"/dashboard-mobile/todo?list={task['todo']}&uid={task['uid']}",
                    extra={"actions": actions_for(task["todo"], task["uid"], first, "")},
                )
            except Exception:  # noqa: BLE001 - one phone failing mustn't stop the others
                _LOGGER.warning("Couldn't send a task notice to %s", first, exc_info=True)

    async def _on_minute(self, _now: Any) -> None:
        if self._people is None:
            return
        now = dt_util.now().replace(tzinfo=None)
        tasks = await self._async_open_tasks()
        # A task that is no longer open (or has no due) loses its sent marks and its lead.
        open_keys = {tl.key(t["todo"], t["uid"]) for t in tasks}
        changed = False
        pruned = tl.prune(self._sent, open_keys)
        if pruned != self._sent:
            self._sent = pruned
            changed = True
        for t in tl.reminders_due(tasks, self._leads, self._sent, now):
            summary = t["summary"]
            lead = t["lead"]
            title = f"Due {tl.lead_text(lead)}: {summary}" if lead else f"Due now: {summary}"
            await self._async_send("todo_due", t, title, f"At {t['due_at'].strftime('%H:%M')}.")
            tl.mark_lead(self._sent, t["todo"], t["uid"], t["due_at"])
            changed = True
        for t in tl.overdue_now(tasks, self._sent, now):
            words = tl.overdue_words(t["due_at"], now)
            await self._async_send("todo_overdue", t, f"Overdue: {t['summary']}", words[:1].upper() + words[1:] + ".")
            tl.mark_overdue(self._sent, t["todo"], t["uid"], t["due_at"], now.date().isoformat())
            changed = True
        if changed:
            await self._async_save()
