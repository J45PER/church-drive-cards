"""People and notifications: who lives here, their phones, and who gets what.

Everything is worked out from Home Assistant's own people, so nothing names
anyone. A person added in Home Assistant (Settings > People) is picked up by
itself:
- their phones: the companion-app devices on their person, each sending
  unless switched off in Manager;
- their to-do list, "Priorities <first name>" (a Local To-do list), made if
  it doesn't exist;
- a column in Manager's notifications table, starting with whatever is set
  to "everyone";
- their places: the zones they go to, each with their own name for it
  ("Work", "Gym"), so cards can say "At work · Ashfield School".

Assignments are kept here (stored across restarts), per kind of notification
(see kinds.py): either "everyone" (anyone, now or later) or a list of people.
church_drive.notify sends a kind to whoever is assigned, on each of their
phones. Automations and templates read the assignments from
sensor.church_drive_people.

Every new to-do list change also fires `church_drive_todo_changed`
(entity_id, old, new open counts) with the change's context, so automations
can follow every list without naming them.
"""

from __future__ import annotations

import logging
from typing import Any

from homeassistant.const import EVENT_HOMEASSISTANT_STARTED, EVENT_STATE_CHANGED
from homeassistant.core import CoreState, Context, Event, HomeAssistant, callback
from homeassistant.helpers import device_registry as dr
from homeassistant.helpers import entity_registry as er
from homeassistant.helpers.dispatcher import async_dispatcher_send
from homeassistant.helpers.event import async_call_later
from homeassistant.helpers.storage import Store
from homeassistant.util import location, slugify

from .kinds import ARRIVALS, JOB_KINDS, KINDS, arrival_kind

_LOGGER = logging.getLogger(__name__)

STORE_VERSION = 1
SIGNAL_PEOPLE = "church_drive_people"
EVENT_TODO_CHANGED = "church_drive_todo_changed"
PERSON_KEYS = ("friendly_name", "user_id", "device_trackers", "entity_picture")

# One-off: the switches the to-do automations used before this, carried over
# the first time this runs. <first> is the person's first name in lower case.
OLD_SWITCHES = {
    **{k: [f"input_boolean.to_do_{k}_{{first}}"] for k in JOB_KINDS},
    "todo_summary": ["input_boolean.to_do_reminders_{first}", "input_boolean.to_do_summary_{first}"],
    "todo_new": ["input_boolean.to_do_new_task_alerts_{first}"],
}
# Phones that were left out of "Church Drive: notify a person" before this.
OLD_PHONES_OFF = {"person.jamie": ["mobile_app_xitol_j45per_ipad", "mobile_app_xitol_j45per_pw4"]}


def first_name(name: str) -> str:
    return (str(name or "").strip().split(" ") or [""])[0]


class People:
    """The house's people, their phones and their notification assignments."""

    def __init__(self, hass: HomeAssistant) -> None:
        self.hass = hass
        self._store: Store = Store(hass, STORE_VERSION, "church_drive.people")
        self._data: dict[str, Any] = {"assign": {}, "phones_off": {}, "places": {}, "cars": {}, "access": {}, "seeded": False}
        self._admins: dict[str, bool] = {}
        self._owner: str | None = None
        self._unsubs: list = []
        self._unsub_started = None
        self._asked_lists: set[str] = set()
        self._asking = None
        self.rev = 0

    # ---- start / stop -------------------------------------------------

    async def async_start(self) -> None:
        stored = await self._store.async_load()
        if isinstance(stored, dict):
            self._data.update(stored)
        await self._async_users()
        self._unsubs.append(self.hass.bus.async_listen(EVENT_STATE_CHANGED, self._on_state, self._wanted))
        if self.hass.state is CoreState.running:
            await self._async_ready()
        else:
            self._unsub_started = self.hass.bus.async_listen_once(EVENT_HOMEASSISTANT_STARTED, self._started)

    async def _started(self, _event: Event) -> None:
        self._unsub_started = None  # a fired listen_once is gone already
        await self._async_ready()

    async def _async_ready(self) -> None:
        if not self._data.get("seeded"):
            self._seed()
        self._fill_defaults()
        await self._async_save()
        await self._async_make_lists()
        self._changed()

    @callback
    def async_stop(self) -> None:
        for unsub in self._unsubs:
            unsub()
        self._unsubs.clear()
        if self._unsub_started is not None:
            self._unsub_started()
            self._unsub_started = None
        if self._asking is not None:
            self._asking()
            self._asking = None

    async def _async_save(self) -> None:
        await self._store.async_save(self._data)

    async def _async_users(self) -> None:
        users = await self.hass.auth.async_get_users()
        self._admins = {u.id: bool(u.is_admin) for u in users if not u.system_generated}
        self._owner = next((u.id for u in users if u.is_owner), None)

    @callback
    def _changed(self) -> None:
        self.rev += 1
        async_dispatcher_send(self.hass, SIGNAL_PEOPLE)

    # ---- state changes: people and to-do lists -----------------------

    @callback
    def _wanted(self, event_data: Any) -> bool:
        eid = event_data.get("entity_id", "") if hasattr(event_data, "get") else ""
        return eid.startswith(("person.", "todo.", "zone."))

    @callback
    def _on_state(self, event: Event) -> None:
        eid = event.data.get("entity_id", "")
        old, new = event.data.get("old_state"), event.data.get("new_state")
        if eid.startswith("zone."):
            # A zone added or moved: phones only check zones when they send a
            # new location, so ask them for one (a still phone might not for
            # a long time).
            moved = new is not None and (
                old is None or any(old.attributes.get(k) != new.attributes.get(k) for k in ("latitude", "longitude", "radius"))
            )
            if moved and self.hass.state is CoreState.running:
                self._ask_locations()
            return
        if eid.startswith("todo."):
            o, n = _count(old), _count(new)
            if o is not None and n is not None and o != n:
                self.hass.bus.async_fire(
                    EVENT_TODO_CHANGED,
                    {"entity_id": eid, "old": o, "new": n},
                    context=new.context if new is not None else None,
                )
            return
        # A person added, removed, renamed, given a login or a phone (not
        # every location update).
        if old is None or new is None or any(old.attributes.get(k) != new.attributes.get(k) for k in PERSON_KEYS):
            self.hass.async_create_task(self._async_person_changed())
        elif old.state != new.state:
            self._changed()  # home / away, for the cards

    @callback
    def _ask_locations(self) -> None:
        """Ask every phone for its location now (at most once a minute)."""
        if self._asking is not None:
            return

        async def ask(_now: Any = None) -> None:
            self._asking = None
            for p in self.people():
                for phone in p["phones"]:
                    try:
                        await self.hass.services.async_call(
                            "notify", phone["service"], {"message": "request_location_update"}, blocking=False
                        )
                    except Exception as err:  # noqa: BLE001
                        _LOGGER.debug("Couldn't ask %s for a location: %s", phone["name"], err)

        # A short wait, so several zone edits in a row ask once.
        self._asking = async_call_later(self.hass, 10, ask)

    async def _async_person_changed(self) -> None:
        await self._async_users()
        self._fill_defaults()
        await self._async_save()
        await self._async_make_lists()
        self._changed()

    # ---- people and phones -------------------------------------------

    def people(self) -> list[dict[str, Any]]:
        """Everyone with a person entity, with their phones."""
        ent_reg = er.async_get(self.hass)
        dev_reg = dr.async_get(self.hass)
        notify = self.hass.services.async_services().get("notify", {})
        out = []
        for st in sorted(self.hass.states.async_all("person"), key=lambda s: s.name.lower()):
            user = st.attributes.get("user_id")
            first = first_name(st.name)
            phones = []
            for tracker in st.attributes.get("device_trackers") or []:
                entry = ent_reg.async_get(tracker)
                if entry is None or entry.platform != "mobile_app" or not entry.device_id:
                    continue
                device = dev_reg.async_get(entry.device_id)
                service = f"mobile_app_{tracker.split('.', 1)[1]}"
                if service not in notify and device is not None:
                    service = f"mobile_app_{slugify(device.name or '')}"
                if service not in notify:
                    continue
                phones.append(
                    {
                        "service": service,
                        "name": (device.name_by_user or device.name) if device else tracker,
                        "model": (device.model or "") if device else "",
                        "apple": bool(device and (device.manufacturer or "").lower().startswith("apple")),
                        "on": service not in self._data["phones_off"].get(st.entity_id, []),
                    }
                )
            places = list(self._data.get("places", {}).get(st.entity_id, []))
            place, zone = self._place(self._nearest_zone(st), places)
            out.append(
                {
                    "entity_id": st.entity_id,
                    "name": st.name,
                    "first": first,
                    "user_id": user,
                    "admin": bool(user and self._admins.get(user)),
                    "home": st.state,
                    "place": place,
                    "zone": zone,
                    "places": places,
                    "cars": list(self._data.get("cars", {}).get(st.entity_id, [])),
                    "picture": st.attributes.get("entity_picture"),
                    "list": f"todo.priorities_{slugify(first)}",
                    "phones": phones,
                }
            )
        return out

    # ---- places: what each person calls the zones they go to -----------

    def _nearest_zone(self, st: Any) -> str:
        """The person's state, but where they're inside more than one zone (GPS
        accuracy wider than the zones), the zone whose centre is nearest: Home
        Assistant picks the smallest of them instead."""
        a = st.attributes
        zones = [self.hass.states.get(z) for z in a.get("in_zones") or []]
        zones = [z for z in zones if z is not None and z.entity_id != "zone.home"]
        lat, lon = a.get("latitude"), a.get("longitude")
        if len(zones) < 2 or lat is None or lon is None or st.state == "home":
            return st.state
        nearest = min(
            zones,
            key=lambda z: location.distance(lat, lon, z.attributes.get("latitude"), z.attributes.get("longitude")) or 0,
        )
        return nearest.name

    def _place(self, state: str, places: list[dict[str, str]]) -> tuple[str, str]:
        """(label, zone name) for where someone is: "Home", their name for a
        zone ("Work"), the zone's own name, "Away" or "Unknown"."""
        if state == "home":
            return "Home", ""
        if state in ("not_home",):
            return "Away", ""
        if state in ("unknown", "unavailable", ""):
            return "Unknown", ""
        for p in places:
            z = self.hass.states.get(p.get("zone", ""))
            if z is not None and z.name == state:
                return (p.get("name") or state), state
        return state, state

    async def async_set_places(self, person: str, places: list[dict[str, str]]) -> None:
        clean = [
            {"zone": str(p.get("zone", "")), "name": str(p.get("name", "")).strip()[:40]}
            for p in places
            if str(p.get("zone", "")).startswith("zone.") and p.get("zone") != "zone.home"
        ]
        self._data.setdefault("places", {})[person] = clean
        await self._async_save()
        self._changed()

    # ---- cars: whose car is whose (a car nobody has is everyone's) ------

    async def async_set_cars(self, person: str, cars: list[str]) -> None:
        clean = list(dict.fromkeys(str(c) for c in cars if c))
        self._data.setdefault("cars", {})[person] = clean
        await self._async_save()
        self._changed()

    # ---- device access: who can use a device (only the restricted ones) --

    def access(self) -> dict[str, list[str]]:
        """{device id or entity id: [person entity ids]}: the devices only some
        people use. Anything not listed is everyone's."""
        return {k: list(v) for k, v in self._data.get("access", {}).items() if v}

    def access_entities(self) -> dict[str, list[str]]:
        """The same, per entity: a device's entities all take its people."""
        ent_reg = er.async_get(self.hass)
        out: dict[str, list[str]] = {}
        for key, people in self.access().items():
            if "." in key:
                out[key] = sorted(set(out.get(key, [])) | set(people))
                continue
            for entry in er.async_entries_for_device(ent_reg, key):
                out[entry.entity_id] = sorted(set(out.get(entry.entity_id, [])) | set(people))
        return out

    async def async_set_access(self, key: str, people: list[str]) -> None:
        clean = list(dict.fromkeys(str(p) for p in people if str(p).startswith("person.")))
        access = self._data.setdefault("access", {})
        if clean:
            access[key] = clean
        else:
            access.pop(key, None)
        await self._async_save()
        self._changed()

    async def _async_make_lists(self) -> None:
        """Give everyone a "Priorities <first name>" to-do list if they haven't one."""
        if not self.hass.config_entries.async_entries("local_todo"):
            return  # Local To-do isn't in use here
        titles = {e.title.lower() for e in self.hass.config_entries.async_entries("local_todo")}
        for p in self.people():
            title = f"Priorities {p['first']}"
            if self.hass.states.get(p["list"]) is not None or title.lower() in titles or title in self._asked_lists:
                continue
            self._asked_lists.add(title)
            try:
                await self.hass.config_entries.flow.async_init(
                    "local_todo", context={"source": "user"}, data={"todo_list_name": title}
                )
                _LOGGER.info("Made the to-do list %s for %s", title, p["name"])
            except Exception:  # noqa: BLE001
                _LOGGER.exception("Couldn't make the to-do list %s", title)

    # ---- kinds and assignments ---------------------------------------

    def _catalogue(self) -> list[dict[str, Any]]:
        kinds = list(KINDS)
        for st in sorted(self.hass.states.async_all("person"), key=lambda s: s.name.lower()):
            kinds.append(arrival_kind(st.entity_id, first_name(st.name)))
        return kinds

    def _available(self, needs: list) -> bool:
        if not needs:
            return True
        ent_reg = er.async_get(self.hass)
        for need in needs:
            if need[0] == "domain" and self.hass.states.async_entity_ids(need[1]):
                return True
            if need[0] == "class" and any(
                s.attributes.get("device_class") == need[2] for s in self.hass.states.async_all(need[1])
            ):
                return True
            if need[0] == "platform" and any(e.platform == need[1] for e in ent_reg.entities.values()):
                return True
        return False

    def _default(self, kind: dict[str, Any]) -> dict[str, Any]:
        default = kind.get("default", "none")
        if default == "all":
            return {"all": True, "people": []}
        people = self.people()
        if default == "admins":
            return {"all": False, "people": [p["entity_id"] for p in people if p["admin"]]}
        if default == "owner":
            return {"all": False, "people": [p["entity_id"] for p in people if p["user_id"] == self._owner]}
        return {"all": False, "people": []}

    @callback
    def _fill_defaults(self) -> None:
        assign = self._data["assign"]
        for kind in self._catalogue():
            if kind["key"] not in assign:
                assign[kind["key"]] = self._default(kind)

    @callback
    def _seed(self) -> None:
        """First run: carry over the old to-do switches and phone choices."""
        people = self.people()
        assign = self._data["assign"]
        for key, patterns in OLD_SWITCHES.items():
            found, on = False, []
            for p in people:
                for pattern in patterns:
                    st = self.hass.states.get(pattern.format(first=slugify(p["first"])))
                    if st is not None:
                        found = True
                        if st.state == "on":
                            on.append(p["entity_id"])
                        break
            if found:
                assign[key] = {"all": False, "people": on}
        for person, services in OLD_PHONES_OFF.items():
            if self.hass.states.get(person) is not None:
                self._data["phones_off"].setdefault(person, list(services))
        self._data["seeded"] = True

    def kinds(self) -> list[dict[str, Any]]:
        """Every kind for Manager: whether it's available yet, and who's assigned."""
        out = []
        for kind in self._catalogue():
            a = self._data["assign"].get(kind["key"]) or {"all": False, "people": []}
            available = self._available(kind.get("needs", []))
            out.append(
                {
                    "key": kind["key"],
                    "group": kind["group"],
                    "name": kind["name"],
                    "note": kind.get("note", ""),
                    "critical": bool(kind.get("critical")),
                    "admin_only": bool(kind.get("admin_only")),
                    "available": available,
                    "all": bool(a.get("all")),
                    "people": list(a.get("people", [])),
                }
            )
        return out

    def recipients(self, kind: str | None, only: list[str] | None = None) -> list[dict[str, Any]]:
        """The people a kind goes to (optionally only some of them)."""
        people = self.people()
        if kind:
            a = self._data["assign"].get(kind)
            if a is None:
                return []
            spec = next((k for k in self._catalogue() if k["key"] == kind), {})
            chosen = people if a.get("all") else [p for p in people if p["entity_id"] in a.get("people", [])]
            if spec.get("admin_only"):
                chosen = [p for p in chosen if p["admin"]]
        else:
            chosen = people
        if only is not None:
            wanted = {str(x).strip().lower() for x in only}
            chosen = [
                p for p in chosen
                if p["entity_id"].lower() in wanted or p["first"].lower() in wanted or p["name"].lower() in wanted
            ]
        return chosen

    def assigned_names(self) -> dict[str, list[str]]:
        """{kind: [first names]} for templates (house jobs use "Everyone")."""
        return {k["key"]: [p["first"] for p in self.recipients(k["key"])] for k in self._catalogue()}

    def everyone(self) -> dict[str, bool]:
        return {k: bool(v.get("all")) for k, v in self._data["assign"].items()}

    async def async_set(self, kind: str, person: str | None, on: bool) -> None:
        a = self._data["assign"].setdefault(kind, {"all": False, "people": []})
        if person is None:  # the "Everyone" switch
            # Off keeps everyone who has it now ticked; only people added later miss out.
            if not on and a.get("all"):
                a["people"] = [p["entity_id"] for p in self.people()]
            a["all"] = bool(on)
            if on:
                a["people"] = []
        else:
            if a.get("all"):
                # Turning one person off "everyone": everyone else stays on.
                a["all"] = False
                a["people"] = [p["entity_id"] for p in self.people()]
            people = [x for x in a.get("people", []) if x != person]
            if on:
                people.append(person)
            a["people"] = people
        await self._async_save()
        self._changed()

    async def async_set_phone(self, person: str, service: str, on: bool) -> None:
        off = [s for s in self._data["phones_off"].get(person, []) if s != service]
        if not on:
            off.append(service)
        self._data["phones_off"][person] = off
        await self._async_save()
        self._changed()

    # ---- sending -----------------------------------------------------

    async def async_notify(
        self,
        kind: str | None,
        only: list[str] | None,
        title: str,
        message: str,
        admin_message: str = "",
        tag: str = "",
        link: str = "",
        image: str = "",
        critical: bool | None = None,
        extra: dict[str, Any] | None = None,
        context: Context | None = None,
    ) -> dict[str, Any]:
        spec = next((k for k in self._catalogue() if k["key"] == kind), {}) if kind else {}
        loud = bool(spec.get("critical")) if critical is None else bool(critical)
        sent: list[str] = []
        phones = 0
        for p in self.recipients(kind, only):
            text = message + (f"\n{admin_message}" if admin_message and p["admin"] else "")
            for phone in p["phones"]:
                if not phone["on"]:
                    continue
                data: dict[str, Any] = {"group": kind or "church-drive"}
                if tag:
                    data["tag"] = tag
                if link:
                    data["url"] = link
                    data["clickAction"] = link
                if image:
                    data["image"] = image
                if loud:
                    if phone["apple"]:
                        data["push"] = {"sound": {"name": "default", "critical": 1, "volume": 1.0},
                                        "interruption-level": "critical"}
                    else:
                        data.update({"ttl": 0, "priority": "high", "channel": "alarm_stream"})
                if extra:
                    data.update(extra)
                try:
                    await self.hass.services.async_call(
                        "notify", phone["service"], {"title": title, "message": text, "data": data},
                        blocking=True, context=context,
                    )
                    phones += 1
                except Exception as err:  # noqa: BLE001 - one phone failing mustn't stop the rest
                    _LOGGER.warning("Couldn't notify %s on %s: %s", p["name"], phone["name"], err)
            sent.append(p["first"])
        return {"people": sent, "phones": phones}


def _count(state: Any) -> int | None:
    if state is None:
        return None
    try:
        return int(state.state)
    except (TypeError, ValueError):
        return None

