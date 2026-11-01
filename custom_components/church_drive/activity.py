"""The activity log: what people and the assistant did in the house, and when people came and went. Administrators only.

Three kinds of line (the rules and wording are in activity_logic.py):

- `user`: a person switched something from the Church Drive app, a dashboard or Home Assistant (a light, the heating, the alarm...).
  Only their own request counts; what an automation or a script they ran did afterwards is not listed again.
- `clive` / `clive_auto`: something the assistant did. Every script of his that runs is followed (script.clive_*); the changes it
  makes (and the scripts it starts) are listed. `clive` = someone asked him (a conversation, or a person running his script), with who
  he did it for; `clive_auto` = an automation ran his script (the heating rules...), with that automation's name. A script that
  changes nothing leaves no line.
- `presence`: a person arrived at or left a place (home, or another zone).
- `overruled`: something the assistant set (a thermostat's mode or target, a light, switch or fan on or off) was changed again from
  outside the Church Drive app within 6 hours, with nothing in Home Assistant asking: the thermostat's own dial or app, a wall switch,
  a remote, a schedule in the device. A change counts as Home Assistant's own when its context belongs to a service call (from anyone:
  a person, an automation, a script, the assistant, Church Drive itself), or follows such a call in the same kind of device a moment
  later (a Hue room's lights follow the room). A device going back to what he set, or through unavailable, never counts. Devices he
  hasn't touched are not listed, whatever changes them. One line per override: it is not listed again until he sets the device again.

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
    OVERRULE_S,
    WATCH_DOMAINS,
    WATCH_DUPLICATE_S,
    WATCH_PROPAGATE_S,
    WATCH_SETTLE_S,
    add,
    clean,
    clive_is_quiet,
    describe,
    entities_in,
    expected_from_call,
    first_name,
    icon_for,
    is_clive,
    make_entry,
    worth_a_line,
    overrule_text,
    presence_text,
    prune,
    related,
    rule_name,
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
        self._clive: dict[str, tuple[float, str, str]] = {}  # context id -> (until, who it is for, the automation that ran it or "")
        self._autos: dict[str, tuple[float, str]] = {}  # context id of an automation run -> (until, its name)
        self._scripts: dict[str, float] = {}  # context id of a person's own script run -> until
        self._called: dict[str, float] = {}  # context id of any service call -> until: a change with one of these is Home Assistant's own
        self._last_call: dict[str, float] = {}  # domain -> when something last asked for it
        self._clive_set: dict[str, tuple[float, dict[str, Any]]] = {}  # entity -> (when he last set it, what he set: state, target)
        self._recent_overrules: list[tuple[float, str, set[str]]] = []
        self._started = time.time()

    # Home Assistant only accepts event filters marked as callbacks.
    @staticmethod
    @callback
    def _is_person(data: Any) -> bool:
        return str(data.get("entity_id", "")).startswith("person.")

    @staticmethod
    @callback
    def _is_script_run(data: Any) -> bool:
        return str(data.get("entity_id", "")).startswith("script.")

    @staticmethod
    @callback
    def _is_device(data: Any) -> bool:
        return str(data.get("entity_id", "")).partition(".")[0] in WATCH_DOMAINS

    async def async_start(self) -> None:
        self.entries = prune(clean(await self._store.async_load()), time.time())
        self._unsubs = [
            self.hass.bus.async_listen(EVENT_CALL_SERVICE, self._on_call),
            self.hass.bus.async_listen("automation_triggered", self._on_automation),
            self.hass.bus.async_listen("script_started", self._on_script, event_filter=self._is_script_run),
            self.hass.bus.async_listen(EVENT_STATE_CHANGED, self._on_person, event_filter=self._is_person),
            self.hass.bus.async_listen(EVENT_STATE_CHANGED, self._on_device, event_filter=self._is_device),
        ]

    @callback
    def async_stop(self) -> None:
        for unsub in self._unsubs:
            unsub()
        self._unsubs = []
        self._store.async_delay_save(lambda: self.entries, 0)

    # ---- listening ----------------------------------------------------

    def _forget_old(self, now: float) -> None:
        for table in (self._clive, self._scripts, self._autos, self._called):
            for key in [k for k, v in table.items() if (v[0] if isinstance(v, tuple) else v) < now]:
                del table[key]

    def _for_whom(self, context: Context) -> str:
        return self._first_name_for_user(context.user_id) if context.user_id else ""

    @callback
    def _on_automation(self, event: Event) -> None:
        """An automation ran: a script it starts shares this context, so that script was started by the house, not asked for."""
        now = time.time()
        self._forget_old(now)
        self._autos[event.context.id] = (now + RUN_S, rule_name(str(event.data.get("name") or event.data.get("entity_id") or "")))

    @callback
    def _on_script(self, event: Event) -> None:
        """One of the assistant's scripts started: follow it, so what it changes is his."""
        entity_id = str(event.data.get("entity_id", ""))
        if not is_clive(entity_id) or clive_is_quiet(entity_id):
            return
        now = time.time()
        self._forget_old(now)
        ctx = event.context
        # A script he started inherits from the one that started it; otherwise it is an automation's, or someone asked.
        parent = self._clive.get(ctx.id) or self._clive.get(ctx.parent_id or "")
        auto = self._autos.get(ctx.id) or self._autos.get(ctx.parent_id or "")
        if parent:
            who, rule = parent[1], parent[2]
        elif auto and auto[0] >= now:
            who, rule = "", auto[1] or "an automation"
        else:
            who, rule = self._for_whom(ctx), ""
        self._clive[ctx.id] = (now + RUN_S, who, rule)

    @callback
    def _on_call(self, event: Event) -> None:
        domain, service = str(event.data.get("domain", "")), str(event.data.get("service", ""))
        data = event.data.get("service_data") or {}
        ctx = event.context
        now = time.time()
        # Every request counts here, whoever made it: what follows from it is Home Assistant's doing, not a device acting alone.
        self._called[ctx.id] = now + 60
        self._last_call[domain] = now
        if len(self._called) > 300:
            self._forget_old(now)
        followed = self._clive.get(ctx.id) or self._clive.get(ctx.parent_id or "")
        if followed and followed[0] >= now:
            self._clive[ctx.id] = (now + RUN_S, followed[1], followed[2])
            if domain not in ("script", "automation"):
                self._remember_clive_set(now, domain, service, data)
                if worth_a_line(domain, service, entities_in(data)):
                    self._record("clive_auto" if followed[2] else "clive", "", domain, service, data, by=followed[1], rule=followed[2])
            return
        if domain in WATCH_DOMAINS:
            # Someone or something else has set these devices since: they are no longer his to be overruled.
            for entity_id in entities_in(data):
                self._clive_set.pop(entity_id, None)
        if not ctx.user_id or ctx.parent_id:
            return  # an automation, a timer, the house itself: not a person asking
        if self._scripts.get(ctx.id, 0) >= now:
            return  # a step of a script they ran, already listed as the script
        if domain == "script":
            if is_clive(f"script.{service}") or any(is_clive(e) for e in entities_in(data)):
                return  # the assistant's script, listed under him when it starts
            self._scripts[ctx.id] = now + RUN_S
        if worth_a_line(domain, service, entities_in(data)):
            self._record("user", self._for_whom(ctx), domain, service, data, user_id=ctx.user_id)

    def _record(self, kind: str, who: str, domain: str, service: str, data: dict[str, Any], by: str = "", user_id: str = "", rule: str = "") -> None:
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
        self._add(make_entry(time.time(), kind, who, text, icon_for(domain), ids[0] if len(ids) == 1 else "", by, user_id, rule))

    @callback
    def _on_person(self, event: Event) -> None:
        old, new = event.data.get("old_state"), event.data.get("new_state")
        if old is None or new is None:
            return
        text = presence_text(old.state, new.state)
        if not text:
            return
        self._add(make_entry(time.time(), "presence", first_name(new.name), text, "mdi:map-marker-account" if new.state != "not_home" else "mdi:exit-run", new.entity_id))

    def _remember_clive_set(self, now: float, domain: str, service: str, data: dict[str, Any]) -> None:
        """He asked for these devices to be like this: a later change away from it, from outside, is an override."""
        expected = expected_from_call(domain, service, data)
        if not expected:
            return
        if len(self._clive_set) > 200:
            self._clive_set = {k: v for k, v in self._clive_set.items() if now - v[0] <= OVERRULE_S}
        for entity_id in entities_in(data):
            before = self._clive_set.get(entity_id, (0, {}))[1]
            self._clive_set[entity_id] = (now, {**before, **expected})

    @callback
    def _on_device(self, event: Event) -> None:
        """A device changed: listed when it overrules what the assistant set, from outside Home Assistant (its own dial, app or a switch)."""
        ctx = event.context
        if ctx.user_id or ctx.parent_id:
            return
        old, new = event.data.get("old_state"), event.data.get("new_state")
        if old is None or new is None:
            return
        touched = self._clive_set.get(new.entity_id)
        now = time.time()
        if touched is None or now - touched[0] > OVERRULE_S or now - self._started < WATCH_SETTLE_S:
            return
        if self._called.get(ctx.id, 0) >= now or now - self._last_call.get(new.domain, 0) < WATCH_PROPAGATE_S:
            return
        text = overrule_text(new.domain, old.state, old.attributes, new.state, new.attributes, touched[1])
        if not text:
            return
        members = set(_as_list(new.attributes.get("entity_id")))
        self._recent_overrules = [r for r in self._recent_overrules if now - r[0] < WATCH_DUPLICATE_S]
        if any(related(new.entity_id, members, r[1], r[2]) for r in self._recent_overrules):
            return
        self._recent_overrules.append((now, new.entity_id, members))
        del self._clive_set[new.entity_id]  # one line per override
        self._add(make_entry(now, "overruled", new.name, text, icon_for(new.domain), new.entity_id, clive_ts=touched[0]))

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
