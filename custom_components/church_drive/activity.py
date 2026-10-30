"""The activity log: what people and the assistant did in the house, and when people came and went. Administrators only.

Three kinds of line (the rules and wording are in activity_logic.py):

- `user`: a person switched something from the Church Drive app, a dashboard or Home Assistant (a light, the heating, the alarm...).
  Only their own request counts; what an automation or a script they ran did afterwards is not listed again.
- `clive`: something the assistant did. Every script of his that runs is followed (script.clive_*); the changes it makes (and the
  scripts it starts) are listed with who he did it for. A script that changes nothing leaves no line.
- `presence`: a person arrived at or left a place (home, or another zone).

Kept in .storage/church_drive.activity for 30 days.
"""

from __future__ import annotations

import time
from typing import Any, Callable

from homeassistant.const import EVENT_CALL_SERVICE, EVENT_STATE_CHANGED
from homeassistant.core import Context, Event, HomeAssistant, callback
from homeassistant.helpers import area_registry as ar
from homeassistant.helpers.storage import Store
from homeassistant.util import dt as dt_util

from .activity_logic import (
    add,
    clean,
    clive_is_quiet,
    describe,
    entities_in,
    first_name,
    icon_for,
    is_clive,
    make_entry,
    noted,
    presence_text,
    prune,
    select,
    who_list,
)
from .const import EVENT_ACTIVITY

STORE_KEY = "church_drive.activity"
RUN_S = 13 * 3600  # how long a script's context is followed (his timed heating waits up to 12 hours before it puts things back)
SAVE_S = 30


class Activity:
    def __init__(self, hass: HomeAssistant, first_name_for_user: Callable[[str | None], str], assistant_name: Callable[[], str]) -> None:
        self.hass = hass
        self._first_name_for_user = first_name_for_user
        self._assistant_name = assistant_name
        self._store: Store[list] = Store(hass, 1, STORE_KEY)
        self.entries: list[dict[str, Any]] = []
        self._unsubs: list[Any] = []
        self._clive: dict[str, tuple[float, str]] = {}  # context id -> (until, who it is for)
        self._scripts: dict[str, float] = {}  # context id of a person's own script run -> until

    # Home Assistant only accepts event filters marked as callbacks.
    @staticmethod
    @callback
    def _is_person(data: Any) -> bool:
        return str(data.get("entity_id", "")).startswith("person.")

    @staticmethod
    @callback
    def _is_script_run(data: Any) -> bool:
        return str(data.get("entity_id", "")).startswith("script.")

    async def async_start(self) -> None:
        self.entries = prune(clean(await self._store.async_load()), time.time())
        self._unsubs = [
            self.hass.bus.async_listen(EVENT_CALL_SERVICE, self._on_call),
            self.hass.bus.async_listen("script_started", self._on_script, event_filter=self._is_script_run),
            self.hass.bus.async_listen(EVENT_STATE_CHANGED, self._on_person, event_filter=self._is_person),
        ]

    @callback
    def async_stop(self) -> None:
        for unsub in self._unsubs:
            unsub()
        self._unsubs = []
        self._store.async_delay_save(lambda: self.entries, 0)

    # ---- listening ----------------------------------------------------

    def _forget_old(self, now: float) -> None:
        for table in (self._clive, self._scripts):
            for key in [k for k, v in table.items() if (v[0] if isinstance(v, tuple) else v) < now]:
                del table[key]

    def _for_whom(self, context: Context) -> str:
        return self._first_name_for_user(context.user_id) if context.user_id else ""

    @callback
    def _on_script(self, event: Event) -> None:
        """One of the assistant's scripts started: follow it, so what it changes is his."""
        entity_id = str(event.data.get("entity_id", ""))
        if not is_clive(entity_id) or clive_is_quiet(entity_id):
            return
        now = time.time()
        self._forget_old(now)
        ctx = event.context
        # For whom: the person who asked him, or whoever the script it was started from was for.
        for_whom = self._for_whom(ctx) or self._clive.get(ctx.parent_id or "", (0, ""))[1]
        self._clive[ctx.id] = (now + RUN_S, for_whom)

    @callback
    def _on_call(self, event: Event) -> None:
        domain, service = str(event.data.get("domain", "")), str(event.data.get("service", ""))
        data = event.data.get("service_data") or {}
        ctx = event.context
        now = time.time()
        followed = self._clive.get(ctx.id) or self._clive.get(ctx.parent_id or "")
        if followed and followed[0] >= now:
            self._clive[ctx.id] = (now + RUN_S, followed[1])
            if domain not in ("script", "automation") and noted(domain, service):
                self._record("clive", "", domain, service, data, by=followed[1])
            return
        if not ctx.user_id or ctx.parent_id:
            return  # an automation, a timer, the house itself: not a person asking
        if self._scripts.get(ctx.id, 0) >= now:
            return  # a step of a script they ran, already listed as the script
        if domain == "script":
            if is_clive(f"script.{service}") or any(is_clive(e) for e in entities_in(data)):
                return  # the assistant's script, listed under him when it starts
            self._scripts[ctx.id] = now + RUN_S
        if noted(domain, service):
            self._record("user", self._for_whom(ctx), domain, service, data, user_id=ctx.user_id)

    def _record(self, kind: str, who: str, domain: str, service: str, data: dict[str, Any], by: str = "", user_id: str = "") -> None:
        ids = entities_in(data)
        names = {e: (st.name if (st := self.hass.states.get(e)) else "") for e in ids}
        if domain == "script" and service != "turn_on":
            names[f"script.{service}"] = (st.name if (st := self.hass.states.get(f"script.{service}")) else "")
            data = {**data, "entity_id": [f"script.{service}"]}
        areas = []
        if not ids:
            registry = ar.async_get(self.hass)
            for area_id in _as_list(data.get("area_id")):
                area = registry.async_get_area(area_id)
                if area is not None:
                    areas.append(area.name)
        text = describe(domain, service, data, names, areas)
        self._add(make_entry(time.time(), kind, who, text, icon_for(domain), ids[0] if len(ids) == 1 else "", by, user_id))

    @callback
    def _on_person(self, event: Event) -> None:
        old, new = event.data.get("old_state"), event.data.get("new_state")
        if old is None or new is None:
            return
        text = presence_text(old.state, new.state)
        if not text:
            return
        self._add(make_entry(time.time(), "presence", first_name(new.name), text, "mdi:map-marker-account" if new.state != "not_home" else "mdi:exit-run", new.entity_id))

    def _add(self, entry: dict[str, Any]) -> None:
        add(self.entries, entry)
        self.entries = prune(self.entries, entry["ts"])
        self._store.async_delay_save(lambda: self.entries, SAVE_S)
        self.hass.bus.async_fire(EVENT_ACTIVITY)

    # ---- asking -------------------------------------------------------

    async def async_query(self, **filters: Any) -> dict[str, Any]:
        """The lines that match (see activity_logic.select), with logins that have no person named from Home Assistant's users."""
        tz = dt_util.get_time_zone(self.hass.config.time_zone) or dt_util.DEFAULT_TIME_ZONE
        found = select(self.entries, tz=tz, **filters)
        names: dict[str, str] = {}
        shown = []
        for entry in found["entries"]:
            uid = entry.get("user_id")
            if uid and not entry["who"]:
                if uid not in names:
                    user = await self.hass.auth.async_get_user(uid)
                    names[uid] = first_name(user.name) if user else ""
                entry = {**entry, "who": names[uid] or "Someone"}
            shown.append({k: v for k, v in entry.items() if k != "user_id"})
        found["entries"] = shown
        found["people"] = who_list(self.entries)
        found["oldest"] = self.entries[0]["ts"] if self.entries else None
        found["assistant"] = self._assistant_name()
        return found


def _as_list(value: Any) -> list[str]:
    if value is None or value == "":
        return []
    return [value] if isinstance(value, str) else [str(v) for v in value]
