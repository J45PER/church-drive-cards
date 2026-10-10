"""Place reminders and snoozing for to-do tasks.

A task can be tied to a place: when someone it's for arrives in that zone, they're
reminded on their phones, and again on every later arrival until the task is ticked
off. It's an alternative to a due-time reminder (the "Church Drive: repeating tasks"
automation sends those); both kinds of reminder come with **Done** and **Snooze**
buttons, and a snooze can be 5, 10, 20, 30 or 60 minutes.

- A time reminder comes back after the snooze.
- A place reminder comes back after the snooze only if the person is still at that
  place; if they've left, it waits for their next arrival.

The place and who it's for are kept here (a task's description stays as it is),
keyed by the to-do list and the item's uid, so a renamed task keeps its reminder.
A reminder for a task that's been deleted or ticked off is dropped when it next
comes up. The rules are in reminder_logic.py.

The phone sends the pressed button back as the event `church_drive_task_action`
(through its mobile app webhook): {action: done|snooze, list, uid, who, zone,
minutes}.
"""

from __future__ import annotations

import logging
from datetime import timedelta
from typing import Any, Callable

from homeassistant.const import EVENT_STATE_CHANGED
from homeassistant.core import CALLBACK_TYPE, Event, HomeAssistant, callback
from homeassistant.helpers.dispatcher import async_dispatcher_send
from homeassistant.helpers.event import async_call_later
from homeassistant.helpers.storage import Store
from homeassistant.util import dt as dt_util, slugify

from .reminder_logic import SNOOZE_MINUTES, actions_for, arrived_in, key, recipients, snooze_minutes

_LOGGER = logging.getLogger(__name__)

STORE_VERSION = 1
SIGNAL_REMINDERS = "church_drive_reminders"
EVENT_TASK_ACTION = "church_drive_task_action"
AGAIN = timedelta(minutes=2)  # a person's zone flickering shouldn't remind twice
LATE = timedelta(hours=3)  # a snooze this far overdue (HA was off) is dropped, not sent late


class Reminders:
    """Place reminders and snoozes, kept across restarts."""

    def __init__(self, hass: HomeAssistant, people: Any) -> None:
        self.hass = hass
        self._people = people
        self._store: Store = Store(hass, STORE_VERSION, "church_drive.reminders")
        self._reminders: dict[str, dict[str, Any]] = {}
        self._snoozes: list[dict[str, Any]] = []
        self._timers: dict[str, CALLBACK_TYPE] = {}
        self._unsub: list[Callable[[], None]] = []
        self._last: dict[str, Any] = {}

    # ---- life ------------------------------------------------------------

    async def async_start(self) -> None:
        data = await self._store.async_load() or {}
        self._reminders = dict(data.get("reminders", {}))
        self._snoozes = list(data.get("snoozes", []))
        self._unsub.append(self.hass.bus.async_listen(EVENT_STATE_CHANGED, self._on_state))
        self._unsub.append(self.hass.bus.async_listen(EVENT_TASK_ACTION, self._on_action))
        for snooze in list(self._snoozes):
            self._schedule(snooze)

    @callback
    def async_stop(self) -> None:
        for unsub in self._unsub:
            unsub()
        self._unsub.clear()
        for cancel in self._timers.values():
            cancel()
        self._timers.clear()

    async def _async_save(self) -> None:
        await self._store.async_save({"reminders": self._reminders, "snoozes": self._snoozes})

    # ---- what's set --------------------------------------------------------

    def all(self) -> dict[str, dict[str, Any]]:
        return {k: dict(v) for k, v in self._reminders.items()}

    def zones(self) -> list[dict[str, str]]:
        """Every zone a reminder can be tied to, Home first."""
        out = [{"zone": s.entity_id, "name": "Home" if s.entity_id == "zone.home" else s.name} for s in self.hass.states.async_all("zone")]
        return sorted(out, key=lambda z: (z["zone"] != "zone.home", z["name"].lower()))

    def _firsts(self) -> list[str]:
        return [p["first"] for p in self._people.people()] if self._people else []

    def _owner(self, todo: str) -> str | None:
        if not self._people:
            return None
        return next((p["first"] for p in self._people.people() if p["list"] == todo), None)

    async def async_set(self, todo: str, uid: str, zone: str | None, who: list[str]) -> dict[str, Any] | None:
        """Tie a task to a zone (or clear it with None). Returns what was kept."""
        k = key(todo, uid)
        if not zone:
            self._reminders.pop(k, None)
            await self._async_drop_snoozes(todo, uid)
            await self._async_save()
            async_dispatcher_send(self.hass, SIGNAL_REMINDERS)
            return None
        if not zone.startswith("zone.") or self.hass.states.get(zone) is None:
            raise ValueError(f"Unknown place: {zone}")
        if not todo.startswith("todo.") or self.hass.states.get(todo) is None:
            raise ValueError(f"Unknown to-do list: {todo}")
        entry = {"todo": todo, "uid": uid, "zone": zone, "who": recipients(who, self._owner(todo), self._firsts())}
        self._reminders[k] = entry
        await self._async_save()
        async_dispatcher_send(self.hass, SIGNAL_REMINDERS)
        return dict(entry)

    async def _async_drop_snoozes(self, todo: str, uid: str) -> None:
        for s in [s for s in self._snoozes if s["todo"] == todo and s["uid"] == uid]:
            self._cancel(s)
            self._snoozes.remove(s)

    # ---- reading the list --------------------------------------------------

    async def _async_item(self, todo: str, uid: str) -> dict[str, Any] | None:
        """The task if it's still on the list and not ticked off."""
        try:
            result = await self.hass.services.async_call(
                "todo", "get_items", {"entity_id": todo, "status": ["needs_action"]}, blocking=True, return_response=True
            )
        except Exception:  # noqa: BLE001 - a list that can't be read isn't a reason to lose the reminder
            _LOGGER.debug("Couldn't read %s", todo, exc_info=True)
            return {}
        items = ((result or {}).get(todo) or {}).get("items") or []
        return next((i for i in items if i.get("uid") == uid), None)

    # ---- arriving ----------------------------------------------------------

    @callback
    def _on_state(self, event: Event) -> None:
        new = event.data.get("new_state")
        old = event.data.get("old_state")
        if new is None or not new.entity_id.startswith("person."):
            return
        # A real move from somewhere else: not a person appearing (a restart, a new phone) or a state that didn't change.
        if old is None or old.state in ("unknown", "unavailable") or old.state == new.state:
            return
        if new.state in ("not_home", "unknown", "unavailable", ""):
            return
        if not self._reminders:
            return
        self.hass.async_create_task(self._async_arrived(new.entity_id, new.state))

    async def _async_arrived(self, person_entity: str, state: str) -> None:
        first = next((p["first"] for p in (self._people.people() if self._people else []) if p["entity_id"] == person_entity), None)
        if first is None:
            return
        now = dt_util.utcnow()
        for k, r in list(self._reminders.items()):
            zone = self.hass.states.get(r["zone"])
            if first not in r["who"] or not arrived_in(state, r["zone"], zone.name if zone else None):
                continue
            seen = self._last.get((k, first))
            if seen and now - seen < AGAIN:
                continue
            self._last[(k, first)] = now
            item = await self._async_item(r["todo"], r["uid"])
            if item is None:
                # Ticked off or deleted: nothing left to remind about.
                self._reminders.pop(k, None)
                await self._async_save()
                continue
            await self._async_remind(first, r["todo"], r["uid"], (item or {}).get("summary") or "A task", r["zone"])

    async def _async_remind(self, first: str, todo: str, uid: str, summary: str, zone: str | None = None, snoozed: bool = False) -> None:
        z = self.hass.states.get(zone) if zone else None
        place = "Home" if zone == "zone.home" else (z.name if z else "")
        if zone:
            title = f"At {place}: {summary}"
            message = f"You're at {place}. Tick it off on the To-do page when it's done."
        else:
            title = f"To-do: {summary}"
            message = f"{summary} is due. Tick it off on the To-do page when it's done."
        if snoozed:
            message = "Snoozed: " + message
        if self._people is None:
            return
        await self._people.async_notify(
            None, [first], title, message,
            tag=f"church-drive-task-{slugify(summary)}", link="/dashboard-mobile/todo",
            extra={"actions": actions_for(todo, uid, first, zone or "")},
        )

    # ---- buttons -----------------------------------------------------------

    async def _on_action(self, event: Event) -> None:
        d = event.data
        todo, uid, first, zone = str(d.get("list", "")), str(d.get("uid", "")), str(d.get("who", "")), str(d.get("zone", ""))
        if not todo.startswith("todo.") or not uid or self.hass.states.get(todo) is None:
            return
        action = d.get("action")
        if action == "done":
            await self._async_done(todo, uid)
        elif action == "snooze":
            minutes = snooze_minutes(d.get("minutes"))
            if minutes is None:
                _LOGGER.warning("Ignoring a snooze of %r minutes (allowed: %s)", d.get("minutes"), SNOOZE_MINUTES)
                return
            await self._async_snooze(todo, uid, first, zone, minutes)

    async def _async_done(self, todo: str, uid: str) -> None:
        try:
            await self.hass.services.async_call("todo", "update_item", {"entity_id": todo, "item": uid, "status": "completed"}, blocking=True)
        except Exception:  # noqa: BLE001
            _LOGGER.warning("Couldn't tick off %s on %s", uid, todo, exc_info=True)
            return
        self._reminders.pop(key(todo, uid), None)
        await self._async_drop_snoozes(todo, uid)
        await self._async_save()
        async_dispatcher_send(self.hass, SIGNAL_REMINDERS)

    async def _async_snooze(self, todo: str, uid: str, first: str, zone: str, minutes: int) -> None:
        # A second snooze on the same task and person replaces the first.
        for s in [s for s in self._snoozes if s["todo"] == todo and s["uid"] == uid and s["first"] == first]:
            self._cancel(s)
            self._snoozes.remove(s)
        snooze = {
            "id": f"{key(todo, uid)}|{first}",
            "todo": todo, "uid": uid, "first": first, "zone": zone,
            "until": (dt_util.utcnow() + timedelta(minutes=minutes)).isoformat(),
        }
        self._snoozes.append(snooze)
        await self._async_save()
        self._schedule(snooze)

    def _cancel(self, snooze: dict[str, Any]) -> None:
        cancel = self._timers.pop(snooze["id"], None)
        if cancel:
            cancel()

    def _schedule(self, snooze: dict[str, Any]) -> None:
        until = dt_util.parse_datetime(snooze["until"]) or dt_util.utcnow()
        delay = max(0.0, (until - dt_util.utcnow()).total_seconds())

        async def fire(_now: Any) -> None:
            self._timers.pop(snooze["id"], None)
            if snooze in self._snoozes:
                self._snoozes.remove(snooze)
                await self._async_save()
            if dt_util.utcnow() - until > LATE:
                return
            await self._async_snooze_over(snooze)

        self._cancel(snooze)
        self._timers[snooze["id"]] = async_call_later(self.hass, delay, fire)

    async def _async_snooze_over(self, snooze: dict[str, Any]) -> None:
        todo, uid, first, zone = snooze["todo"], snooze["uid"], snooze["first"], snooze["zone"]
        item = await self._async_item(todo, uid)
        if item is None:
            return
        if zone:
            # A place reminder: only if they're still there; otherwise it waits for the next arrival.
            person = next((p for p in (self._people.people() if self._people else []) if p["first"] == first), None)
            st = self.hass.states.get(person["entity_id"]) if person else None
            z = self.hass.states.get(zone)
            if st is None or not arrived_in(st.state, zone, z.name if z else None):
                return
        await self._async_remind(first, todo, uid, (item or {}).get("summary") or "A task", zone or None, snoozed=True)
