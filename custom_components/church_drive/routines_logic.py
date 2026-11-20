"""The plain rules behind Clive's scheduled actions (no Home Assistant in here, so they can be tested alone).

A routine is one thing done to one or more devices at a time: once ("open the bedroom blind at 8:15 tomorrow") or over and over
("turn the landing light off at 23:00 every day", "weekdays at 07:00"). Each has an id, a name, an `action` (on, off, open, close or
set, with a `value` for set), its devices, and when:

    {"id": "r1", "name": "Open the bedroom blind", "entities": ["cover.hayleys_bedroom_blind"], "action": "open",
     "when": {"kind": "once", "at": "2026-11-21T08:15"}}
    {"kind": "repeat", "time": "07:00", "days": [0, 1, 2, 3, 4]}        (Monday = 0 ... Sunday = 6)

Locks, the alarm and anything else about safety are never scheduled here. Times are the house's local time.
"""

from __future__ import annotations

import re
import secrets
from datetime import datetime, timedelta
from typing import Any

MAX_ROUTINES = 60
MAX_ENTITIES = 12
ACTIONS = ("on", "off", "open", "close", "set")
DAY_NAMES = ("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
# What can be scheduled, and the actions each kind takes ("set" needs a value: cover position %, light brightness %, fan speed %, thermostat degrees).
DOMAINS: dict[str, tuple[str, ...]] = {
    "cover": ("open", "close", "set"),
    "light": ("on", "off", "set"),
    "switch": ("on", "off"),
    "fan": ("on", "off", "set"),
    "humidifier": ("on", "off"),
    "climate": ("on", "off", "set"),
    "scene": ("on",),
    "input_boolean": ("on", "off"),
}
_TIME = re.compile(r"^([01]?\d|2[0-3]):([0-5]\d)(?::[0-5]\d)?$")


def domain_of(entity: Any) -> str:
    return str(entity).split(".", 1)[0] if isinstance(entity, str) and "." in entity else ""


def clean_time(value: Any) -> str | None:
    m = _TIME.match(str(value or "").strip())
    return f"{int(m.group(1)):02d}:{m.group(2)}" if m else None


def clean_days(value: Any) -> list[int]:
    days = sorted({int(d) for d in value if isinstance(d, (int, str)) and str(d).isdigit() and 0 <= int(d) <= 6}) if isinstance(value, (list, tuple)) else []
    return days or list(range(7))


def parse_at(value: Any, now: datetime) -> datetime | None:
    """A one-off time as a local datetime (naive input is taken as the house's local time), or None."""
    try:
        at = datetime.fromisoformat(str(value).strip())
    except ValueError:
        return None
    if at.tzinfo is None:
        return at.replace(tzinfo=now.tzinfo)
    return at.astimezone(now.tzinfo)


def clean_when(value: Any, now: datetime) -> dict[str, Any] | None:
    if not isinstance(value, dict):
        return None
    if value.get("kind") == "repeat":
        time = clean_time(value.get("time"))
        return {"kind": "repeat", "time": time, "days": clean_days(value.get("days"))} if time else None
    at = parse_at(value.get("at"), now)
    return {"kind": "once", "at": at.replace(tzinfo=None).isoformat(timespec="minutes")} if at else None


def clean_routine(raw: Any, now: datetime, keep: dict[str, Any] | None = None) -> dict[str, Any]:
    """A checked routine, or ValueError saying what's wrong (said back to the person)."""
    if not isinstance(raw, dict):
        raise ValueError("A routine is a set of fields")
    old = keep or {}
    entities = raw.get("entities", old.get("entities"))
    ents = [e for e in (entities if isinstance(entities, (list, tuple)) else [entities]) if isinstance(e, str)]
    ents = list(dict.fromkeys(e.strip() for e in ents if e.strip()))[:MAX_ENTITIES]
    if not ents:
        raise ValueError("Say which device")
    action = raw.get("action", old.get("action"))
    if action not in ACTIONS:
        raise ValueError("The action is on, off, open, close or set")
    value = raw.get("value", old.get("value"))
    for e in ents:
        allowed = DOMAINS.get(domain_of(e))
        if allowed is None:
            raise ValueError(f"{e} can't be scheduled (locks, the alarm and similar are left out on purpose)")
        if action not in allowed:
            raise ValueError(f"{e} can't be told to {action}")
    if action == "set":
        try:
            value = float(value)
        except (TypeError, ValueError):
            raise ValueError("Set needs a number") from None
        value = int(value) if value == int(value) else round(value, 1)
    else:
        value = None
    when = clean_when(raw.get("when", old.get("when")), now)
    if when is None:
        raise ValueError("Say when: a time and day, or a time and days to repeat")
    name = str(raw.get("name", old.get("name")) or "").strip()[:80] or default_name(ents, action, value)
    return {
        "id": old.get("id") or str(raw.get("id") or "") or secrets.token_hex(4),
        "name": name,
        "entities": ents,
        "action": action,
        "value": value,
        "when": when,
        "enabled": bool(raw.get("enabled", old.get("enabled", True))),
        "by": str(raw.get("by", old.get("by")) or "")[:60],
        "src": "user" if raw.get("src", old.get("src")) == "user" else "clive",
        "made": old.get("made") or now.replace(tzinfo=None).isoformat(timespec="minutes"),
        "done": old.get("done") if when["kind"] == "once" and "when" not in raw else None,
    }


def default_name(entities: list[str], action: str, value: Any) -> str:
    what = entities[0].split(".", 1)[1].replace("_", " ")
    extra = f" ({len(entities) - 1} more)" if len(entities) > 1 else ""
    verb = {"on": "Turn on", "off": "Turn off", "open": "Open", "close": "Close", "set": "Set"}[action]
    return f"{verb} {what}{extra}" + (f" to {value}" if action == "set" else "")


def clean_routines(raw: Any, now: datetime) -> dict[str, dict[str, Any]]:
    out: dict[str, dict[str, Any]] = {}
    items = raw.values() if isinstance(raw, dict) else raw if isinstance(raw, list) else []
    for item in items:
        try:
            r = clean_routine(item, now, {"id": item.get("id"), "made": item.get("made"), "done": item.get("done")} if isinstance(item, dict) else None)
        except ValueError:
            continue
        out[r["id"]] = r
        if len(out) >= MAX_ROUTINES:
            break
    return out


def call_for(entity: str, action: str, value: Any) -> tuple[str, str, dict[str, Any]]:
    """(domain, service, data) for one device."""
    domain = domain_of(entity)
    data: dict[str, Any] = {"entity_id": entity}
    if domain == "cover":
        if action == "set":
            return "cover", "set_cover_position", {**data, "position": int(value)}
        return "cover", "open_cover" if action == "open" else "close_cover", data
    if action == "set":
        if domain == "light":
            return "light", "turn_on", {**data, "brightness_pct": max(1, min(100, int(value)))}
        if domain == "fan":
            return "fan", "set_percentage", {**data, "percentage": max(0, min(100, int(value)))}
        return "climate", "set_temperature", {**data, "temperature": value}
    return domain, "turn_on" if action == "on" else "turn_off", data


def occurrences(routine: dict[str, Any], start: datetime, end: datetime) -> list[datetime]:
    """When this routine is due in the time after `start` up to and including `end`, oldest first."""
    when = routine["when"]
    if when["kind"] == "once":
        at = datetime.fromisoformat(when["at"]).replace(tzinfo=end.tzinfo)
        return [at] if start < at <= end and not routine.get("done") else []
    hour, minute = (int(x) for x in when["time"].split(":"))
    out = []
    day = start.date()
    while day <= end.date():
        at = datetime(day.year, day.month, day.day, hour, minute, tzinfo=end.tzinfo)
        if day.weekday() in when["days"] and start < at <= end:
            out.append(at)
        day += timedelta(days=1)
    return out


def next_run(routine: dict[str, Any], now: datetime) -> datetime | None:
    """The next time it will happen, or None (a one-off that has been done or has passed, or a routine that's off)."""
    if not routine["enabled"]:
        return None
    when = routine["when"]
    if when["kind"] == "once":
        at = datetime.fromisoformat(when["at"]).replace(tzinfo=now.tzinfo)
        return at if at > now and not routine.get("done") else None
    found = occurrences(routine, now, now + timedelta(days=8))
    return found[0] if found else None


def describe_when(when: dict[str, Any], now: datetime) -> str:
    """"Tomorrow at 08:15", "Friday 20 Nov at 08:15", "Every day at 07:00", "Mon-Fri at 07:00"."""
    if when["kind"] == "once":
        at = datetime.fromisoformat(when["at"])
        gap = (at.date() - now.date()).days
        day = "Today" if gap == 0 else "Tomorrow" if gap == 1 else at.strftime("%A %-d %b")
        return f"{day} at {at.strftime('%H:%M')}"
    return f"{describe_days(when['days'])} at {when['time']}"


def describe_days(days: list[int]) -> str:
    if len(days) == 7:
        return "Every day"
    if days == [0, 1, 2, 3, 4]:
        return "Mon-Fri"
    if days == [5, 6]:
        return "Weekends"
    return ", ".join(DAY_NAMES[d] for d in days)


def summary(routine: dict[str, Any], now: datetime) -> str:
    return f"{routine['name']}: {describe_when(routine['when'], now)}" + ("" if routine["enabled"] else " (off)")


def slug(text: Any) -> str:
    """"Hayley's Bedroom Blind" as "hayleys_bedroom_blind": lower case, apostrophes dropped, everything else not a letter or digit an underscore."""
    return re.sub(r"[^a-z0-9]+", "_", re.sub(r"['\u2019]", "", str(text).lower())).strip("_")


def resolve_entity(entity: str, known: dict[str, str]) -> tuple[str | None, list[str]]:
    """The real entity for what was asked: the same id if the house has it; otherwise the one of a schedulable kind whose name is the same words
    (Clive guesses an id such as cover.hayleys_bedroom_blind from a name, and the real one is cover.roller_blind). `known` maps each schedulable
    entity id to its friendly name. Returns (entity or None, suggestions): suggestions are the names that share a word with what was asked."""
    if entity in known:
        return entity, []
    domain, _, wanted = entity.partition(".")
    wanted = slug(wanted)
    same = [e for e, name in known.items() if e.startswith(domain + ".") and slug(name) == wanted]
    if len(same) == 1:
        return same[0], []
    words = set(wanted.split("_")) - {"the", "a", "my", "room", "light", "blind"} or set(wanted.split("_"))
    near = sorted(name for e, name in known.items() if words & set(slug(name).split("_")))
    if not near:
        near = sorted(name for e, name in known.items() if e.startswith(domain + "."))
    return None, near[:5]
