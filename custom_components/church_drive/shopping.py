"""Shopping lists: a list is one task, with items you tick off as bought or unavailable.

Each list has a name, the people it is for, an optional date (and time) and optional places where it will
be bought: found with a Google Maps search, so any shop anywhere, kept with their coordinates. It shows on every person's to-do list as a single task ("Shopping: Weekly (3 of 8)", due on its
date) whose description carries a marker, so the cards and the phone app know to open the list instead
of a plain task. The task is ticked off when no item is left open. Items that were unavailable are
remembered and offered when the next list is made, and again if someone arrives at a shop without a list.
A finished list is deleted a day later, together with its tasks, so nothing piles up.

Ticking items off needs no extra to-do entity: lists are kept here and mirrored onto the to-do lists.
The rules are in shopping_logic.py.
"""

from __future__ import annotations

import logging
import uuid
from datetime import timedelta
from typing import Any, Callable

from homeassistant.const import EVENT_STATE_CHANGED
from homeassistant.core import Context, Event, HomeAssistant, callback
from homeassistant.helpers.dispatcher import async_dispatcher_send
from homeassistant.helpers.event import async_track_time_change
from homeassistant.helpers.storage import Store
from homeassistant.util import dt as dt_util

from . import shopping_logic as sl
from .reminder_logic import arrived_in

_LOGGER = logging.getLogger(__name__)

STORE_VERSION = 1
SIGNAL_SHOPPING = "church_drive_shopping"
EVENT_SHOPPING = "church_drive_shopping_changed"
KEEP_DONE = timedelta(days=1)
AGAIN = timedelta(minutes=30)  # one prompt per person per shop per visit
PLACE_AGAIN = timedelta(hours=3)  # and per place a list names
LINK = "/dashboard-tasks/shopping"
HA_SHOPPING = "todo.shopping_list"


def _clean(value: Any, limit: int = 80) -> str:
    return " ".join(str(value or "").split())[:limit]


class Shopping:
    """The household's shopping lists, kept across restarts."""

    def __init__(self, hass: HomeAssistant, people: Any) -> None:
        self.hass = hass
        self._people = people
        self._store: Store = Store(hass, STORE_VERSION, "church_drive.shopping")
        self._lists: list[dict[str, Any]] = []
        self._missed: list[dict[str, Any]] = []
        self._unsub: list[Callable[[], None]] = []
        self._last: dict[tuple[str, str], Any] = {}

    # ---- life ------------------------------------------------------------

    async def async_start(self) -> None:
        data = await self._store.async_load() or {}
        self._lists = list(data.get("lists", []))
        self._missed = list(data.get("missed", []))
        changed = False
        for lst in self._lists:
            # Lists made when places were zones kept zone ids: turn them into places. Every list also gets a time.
            kept = self._places_valid(lst.get("places", []))
            if kept != lst.get("places") or "time" not in lst:
                lst["places"] = kept
                lst.setdefault("time", "")
                changed = True
        if changed:
            await self._async_save()
        self._unsub.append(self.hass.bus.async_listen(EVENT_STATE_CHANGED, self._on_state))
        self._unsub.append(async_track_time_change(self.hass, self._on_morning, hour=8, minute=0, second=0))
        self._unsub.append(async_track_time_change(self.hass, self._on_minute, second=0))
        self._unsub.append(async_track_time_change(self.hass, self._on_housekeeping, minute=15, second=0))
        await self._async_purge()

    @callback
    def async_stop(self) -> None:
        for unsub in self._unsub:
            unsub()
        self._unsub.clear()

    async def _async_save(self) -> None:
        await self._store.async_save({"lists": self._lists, "missed": self._missed})

    def _changed(self, context: Context | None = None) -> None:
        async_dispatcher_send(self.hass, SIGNAL_SHOPPING)
        self.hass.bus.async_fire(EVENT_SHOPPING, {"open": len([l for l in self._lists if not l["done"]])}, context=context)

    # ---- reading ---------------------------------------------------------

    def _firsts(self) -> list[str]:
        return [p["first"] for p in self._people.people()] if self._people else []

    def firsts(self) -> list[str]:
        """First names of the people a list can be for."""
        return self._firsts()

    def _todo_of(self, first: str) -> str | None:
        return next((p["list"] for p in (self._people.people() if self._people else []) if p["first"] == first), None)

    def _find(self, list_id: str) -> dict[str, Any]:
        found = next((l for l in self._lists if l["id"] == list_id), None)
        if found is None:
            raise KeyError(list_id)
        return found

    def lists(self, first: str | None = None) -> list[dict[str, Any]]:
        """Every list (or those a person is on), unfinished first, soonest date first."""
        mine = [l for l in self._lists if first is None or first in l["people"]]
        out = sorted(mine, key=lambda l: (l["done"], l["date"] == "", l["date"], l["created"]))
        return [{**l, "counts": sl.counts(l["items"])} for l in out]

    def missed(self) -> list[dict[str, Any]]:
        return [dict(m) for m in self._missed]

    def default_for(self, first: str | None) -> dict[str, Any] | None:
        """Where a person's shared items go unless they say otherwise."""
        return sl.pick_list(self._lists, first) if first else None

    def zones(self) -> list[dict[str, str]]:
        out = [{"zone": s.entity_id, "name": "Home" if s.entity_id == "zone.home" else s.name} for s in self.hass.states.async_all("zone")]
        return sorted(out, key=lambda z: z["name"].lower())

    # ---- changing --------------------------------------------------------

    def _people_valid(self, who: list[str]) -> list[str]:
        known = self._firsts()
        named = [w for w in dict.fromkeys(_clean(w) for w in who) if w]
        if any(n.lower() == "everyone" for n in named):
            return known
        return [k for k in known if k.lower() in {n.lower() for n in named}]

    def _places_valid(self, places: list[Any]) -> list[dict[str, Any]]:
        """Checked places from a map search. An old zone id becomes the place that zone is."""
        out = []
        for p in places or []:
            if isinstance(p, str):
                st = self.hass.states.get(p) if p.startswith("zone.") else None
                if st is None:
                    continue
                p = {"name": st.name, "lat": st.attributes.get("latitude"), "lon": st.attributes.get("longitude")}
            out.append(p)
        return sl.clean_places(out)

    @staticmethod
    def _when(lst: dict[str, Any], time: str | None) -> None:
        """Keep a list's time only while it has a date (a time of day means nothing without a day)."""
        lst["time"] = sl.valid_time(time) if lst.get("date") else ""

    @staticmethod
    def _due(lst: dict[str, Any]) -> dict[str, str]:
        """The to-do task's due: the date, or the date and time when the list has one."""
        if not lst["date"]:
            return {}
        if lst.get("time"):
            return {"due_datetime": f"{lst['date']} {lst['time']}:00"}
        return {"due_date": lst["date"]}

    async def async_create(
        self,
        name: str,
        people: list[str],
        date: str = "",
        places: list[Any] | None = None,
        items: list[str] | None = None,
        include_missed: bool = True,
        context: Context | None = None,
        time: str = "",
    ) -> dict[str, Any]:
        """Make a list. Items that weren't available last time are added when [include_missed] (and returned in 'offered')."""
        who = self._people_valid(people)
        if not who:
            raise ValueError("A shopping list needs at least one person")
        offered = [m["text"] for m in self._missed] if include_missed else []
        entries, _ = sl.add_items([], [*(items or []), *offered], lambda: uuid.uuid4().hex[:8])
        lst = {
            "id": uuid.uuid4().hex[:12],
            "name": _clean(name),
            "people": who,
            "date": sl.valid_date(date),
            "places": self._places_valid(places or []),
            "items": entries,
            "created": dt_util.utcnow().isoformat(),
            "done": False,
            "done_at": "",
            "tasks": {},
        }
        self._when(lst, time)
        self._lists.append(lst)
        if offered:
            self._missed = sl.drop_missed(self._missed, offered)
        await self._async_after(lst, context)
        if offered:
            await self._async_notify(
                who, "Not available last time",
                f"{', '.join(offered[:8])} {'were' if len(offered) > 1 else 'was'} not available on your last shop. Added to {lst['name'] or 'the new list'}.",
                lst["id"], context,
            )
        return {"list": self._public(lst), "offered": offered}

    async def async_update(
        self, list_id: str, name: str | None, people: list[str] | None, date: str | None, places: list[Any] | None, context: Context | None = None,
        time: str | None = None,
    ) -> dict[str, Any]:
        lst = self._find(list_id)
        if name is not None:
            lst["name"] = _clean(name)
        if people is not None:
            who = self._people_valid(people)
            if not who:
                raise ValueError("A shopping list needs at least one person")
            for first in set(lst["people"]) - set(who):
                await self._async_remove_task(lst, first, context)
            lst["people"] = who
        if date is not None:
            lst["date"] = sl.valid_date(date)
        if places is not None:
            lst["places"] = self._places_valid(places)
        self._when(lst, lst.get("time", "") if time is None else time)
        await self._async_after(lst, context)
        return self._public(lst)

    async def async_add_items(self, list_id: str, texts: list[str], context: Context | None = None) -> dict[str, Any]:
        lst = self._find(list_id)
        lst["items"], added = sl.add_items(lst["items"], texts, lambda: uuid.uuid4().hex[:8])
        self._missed = sl.drop_missed(self._missed, [i["text"] for i in lst["items"]])
        await self._async_after(lst, context)
        return {"list": self._public(lst), "added": added}

    async def async_set_item(self, list_id: str, item_id: str, status: str, context: Context | None = None) -> dict[str, Any]:
        lst = self._find(list_id)
        lst["items"] = sl.set_status(lst["items"], item_id, status)
        await self._async_after(lst, context)
        return self._public(lst)

    async def async_remove_item(self, list_id: str, item_id: str, context: Context | None = None) -> dict[str, Any]:
        lst = self._find(list_id)
        lst["items"] = [i for i in lst["items"] if i["id"] != item_id]
        await self._async_after(lst, context)
        return self._public(lst)

    async def async_delete(self, list_id: str, context: Context | None = None) -> None:
        lst = self._find(list_id)
        for first in list(lst["tasks"]):
            await self._async_remove_task(lst, first, context)
        self._lists.remove(lst)
        await self._async_save()
        self._changed(context)

    async def async_import_ha(self, people: list[str], name: str = "Shopping", context: Context | None = None) -> dict[str, Any]:
        """Move the open items of Home Assistant's own shopping list onto a new list here."""
        result = await self.hass.services.async_call(
            "todo", "get_items", {"entity_id": HA_SHOPPING, "status": ["needs_action"]}, blocking=True, return_response=True, context=context
        )
        found = ((result or {}).get(HA_SHOPPING) or {}).get("items") or []
        made = await self.async_create(name, people, items=[i["summary"] for i in found], include_missed=False, context=context)
        for i in found:
            await self.hass.services.async_call("todo", "remove_item", {"entity_id": HA_SHOPPING, "item": i["uid"]}, blocking=True, context=context)
        return made

    @staticmethod
    def _public(lst: dict[str, Any]) -> dict[str, Any]:
        return {**lst, "counts": sl.counts(lst["items"])}

    # ---- keeping the to-do tasks and the finished state in step -----------------

    async def _async_after(self, lst: dict[str, Any], context: Context | None) -> None:
        was_done = lst["done"]
        lst["done"] = sl.is_done(lst["items"])
        if lst["done"] and not was_done:
            lst["done_at"] = dt_util.utcnow().isoformat()
            self._missed = sl.merge_missed(self._missed, sl.missed_from(lst["items"]), lst["name"] or "Shopping", dt_util.now().date().isoformat())
        elif was_done and not lst["done"]:
            lst["done_at"] = ""
        for first in lst["people"]:
            try:
                await self._async_sync_task(lst, first, context)
            except Exception:  # noqa: BLE001 - the list is kept even if a to-do list can't be written
                _LOGGER.warning("Couldn't update %s's to-do task for the shopping list", first, exc_info=True)
        await self._async_save()
        self._changed(context)

    async def _async_find_uid(self, todo: str, list_id: str) -> str | None:
        result = await self.hass.services.async_call(
            "todo", "get_items", {"entity_id": todo}, blocking=True, return_response=True
        )
        for item in ((result or {}).get(todo) or {}).get("items") or []:
            if sl.parse_marker(item.get("description")) == list_id:
                return item.get("uid")
        return None

    async def _async_sync_task(self, lst: dict[str, Any], first: str, context: Context | None) -> None:
        todo = self._todo_of(first)
        if not todo or self.hass.states.get(todo) is None:
            return
        data: dict[str, Any] = {
            "entity_id": todo,
            "rename": sl.task_summary(lst["name"], lst["items"]),
            "description": sl.task_description(lst["id"], lst["items"]),
            "status": "completed" if lst["done"] else "needs_action",
        }
        data.update(self._due(lst))
        uid = lst["tasks"].get(first)
        if uid:
            data["item"] = uid
            try:
                await self.hass.services.async_call("todo", "update_item", data, blocking=True, context=context)
                return
            except Exception:  # noqa: BLE001 - deleted by hand: make it again below
                lst["tasks"].pop(first, None)
        add = {"entity_id": todo, "item": data["rename"], "description": data["description"]}
        add.update(self._due(lst))
        await self.hass.services.async_call("todo", "add_item", add, blocking=True, context=context)
        uid = await self._async_find_uid(todo, lst["id"])
        if uid:
            lst["tasks"][first] = uid
            if lst["done"]:
                await self.hass.services.async_call(
                    "todo", "update_item", {"entity_id": todo, "item": uid, "status": "completed"}, blocking=True, context=context
                )

    async def _async_remove_task(self, lst: dict[str, Any], first: str, context: Context | None) -> None:
        uid = lst["tasks"].pop(first, None)
        todo = self._todo_of(first)
        if not uid or not todo or self.hass.states.get(todo) is None:
            return
        try:
            await self.hass.services.async_call("todo", "remove_item", {"entity_id": todo, "item": uid}, blocking=True, context=context)
        except Exception:  # noqa: BLE001 - already gone
            _LOGGER.debug("Couldn't remove %s from %s", uid, todo, exc_info=True)

    async def _async_purge(self) -> None:
        """Delete lists finished more than a day ago, with their tasks."""
        cutoff = (dt_util.utcnow() - KEEP_DONE).isoformat()
        old = [l for l in self._lists if l["done"] and l["done_at"] and l["done_at"] < cutoff]
        for lst in old:
            for first in list(lst["tasks"]):
                await self._async_remove_task(lst, first, None)
            self._lists.remove(lst)
        if old:
            await self._async_save()
            self._changed()

    async def _on_housekeeping(self, _now: Any) -> None:
        await self._async_purge()

    # ---- reminding -------------------------------------------------------

    async def _async_notify(self, who: list[str], title: str, message: str, list_id: str, context: Context | None = None, tag: str = "") -> None:
        if self._people is None:
            return
        try:
            await self._people.async_notify(
                None, who, title, message, tag=tag or f"shopping-{list_id}", link=f"{LINK}?list={list_id}" if list_id else LINK, context=context
            )
        except Exception:  # noqa: BLE001 - a courtesy
            _LOGGER.debug("Couldn't send the shopping reminder", exc_info=True)

    async def _on_morning(self, _now: Any) -> None:
        """On a list's date, remind the people it is for."""
        today = dt_util.now().date().isoformat()
        for lst in sl.due_today(self._lists, today):
            if lst.get("time"):
                continue  # a list with a time reminds at that time instead
            c = sl.counts(lst["items"])
            await self._async_notify(
                lst["people"], "Shopping today", f"{lst['name'] or 'Shopping'}: {c[sl.OPEN]} to get. Tap to open the list.", lst["id"], tag=f"shopping-day-{lst['id']}"
            )

    async def _on_minute(self, now: Any) -> None:
        """At a list's time on its date, remind the people it is for, naming the place when it has one."""
        local = dt_util.as_local(now)
        today, hm = local.date().isoformat(), local.strftime("%H:%M")
        for lst in self._lists:
            if lst["done"] or lst["date"] != today or lst.get("time") != hm:
                continue
            where = f" at {lst['places'][0]['name']}" if lst["places"] else ""
            c = sl.counts(lst["items"])
            await self._async_notify(
                lst["people"], "Time to go shopping", f"{lst['name'] or 'Shopping'}{where}: {c[sl.OPEN]} to get. Tap to open the list.",
                lst["id"], tag=f"shopping-time-{lst['id']}",
            )

    async def _async_guard(self, todo: str) -> None:
        """Re-open the shopping tasks on [todo] that were ticked while their list still has items to get."""
        try:
            result = await self.hass.services.async_call(
                "todo", "get_items", {"entity_id": todo, "status": ["completed"]}, blocking=True, return_response=True
            )
        except Exception:  # noqa: BLE001
            return
        ticked = {sl.parse_marker(i.get("description")) for i in ((result or {}).get(todo) or {}).get("items") or []}
        for lst in self._lists:
            if lst["done"] or lst["id"] not in ticked:
                continue
            for first in lst["people"]:
                if self._todo_of(first) == todo:
                    try:
                        await self._async_sync_task(lst, first, None)
                    except Exception:  # noqa: BLE001
                        _LOGGER.debug("Couldn't put %s's shopping task back", first, exc_info=True)

    @callback
    def _on_state(self, event: Event) -> None:
        new = event.data.get("new_state")
        old = event.data.get("old_state")
        if new is not None and new.entity_id.startswith("todo."):
            # A shopping task is only finished by finishing its list: if someone ticks it, put it back.
            if any(not l["done"] and self._todo_of(f) == new.entity_id for l in self._lists for f in l["tasks"]):
                self.hass.async_create_task(self._async_guard(new.entity_id))
            return
        if new is None or not new.entity_id.startswith("person."):
            return
        self._check_near(old, new)
        if old is None or old.state in ("unknown", "unavailable") or old.state == new.state:
            return
        if new.state in ("not_home", "home", "unknown", "unavailable", ""):
            return
        self.hass.async_create_task(self._async_arrived(new.entity_id, new.state))

    def _check_near(self, old: Any, new: Any) -> None:
        """A phone has moved close to a place one of the person's lists will be bought at: bring that list up."""
        if not any(l["places"] and not l["done"] for l in self._lists):
            return
        accuracy = new.attributes.get("gps_accuracy")
        if isinstance(accuracy, (int, float)) and accuracy > 200:
            return  # a vague fix could put them anywhere
        first = next((p["first"] for p in (self._people.people() if self._people else []) if p["entity_id"] == new.entity_id), None)
        if first is None:
            return
        for lst in sl.for_person(self._lists, first):
            hit = sl.near_place(lst["places"], new.attributes.get("latitude"), new.attributes.get("longitude"))
            if hit is None:
                continue
            if old is not None and sl.near_place(lst["places"], old.attributes.get("latitude"), old.attributes.get("longitude")):
                continue  # they were already there
            key = (first, f"{lst['id']}|{hit['lat']},{hit['lon']}")
            now = dt_util.utcnow()
            seen = self._last.get(key)
            if seen and now - seen < PLACE_AGAIN:
                continue
            self._last[key] = now
            said = sl.arrival_message(hit["name"], [lst], [])
            if said:
                self.hass.async_create_task(self._async_notify([first], said[0], said[1], lst["id"], tag=f"shopping-near-{lst['id']}"))

    async def _async_arrived(self, person_entity: str, state: str) -> None:
        first = next((p["first"] for p in (self._people.people() if self._people else []) if p["entity_id"] == person_entity), None)
        if first is None:
            return
        zone = next((z for z in self.hass.states.async_all("zone") if arrived_in(state, z.entity_id, z.name)), None)
        if zone is None:
            return
        # Zones named like a shop bring up lists with no place of their own; a list with places comes up near those instead.
        if not sl.is_shop_zone(zone.name, set(), zone.entity_id):
            return
        now = dt_util.utcnow()
        seen = self._last.get((first, zone.entity_id))
        if seen and now - seen < AGAIN:
            return
        self._last[(first, zone.entity_id)] = now
        mine = [l for l in sl.for_person(self._lists, first) if not l["places"]]
        said = sl.arrival_message(zone.name, mine, self._missed)
        if said is None:
            return
        await self._async_notify([first], said[0], said[1], mine[0]["id"] if mine else "", tag=f"shopping-at-{zone.entity_id}")
