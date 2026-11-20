"""The plain rules behind the Upcoming page: everything the house does at a set time, as a list of what happens next (no Home Assistant in
here, so it can be tested alone).

Each item is one thing that will happen: {"at" (local ISO, to the minute), "date", "time", "title", "kind", "src", "by", "card", "tap"}.
`kind` says what colour and icon it has (blind, light, fan, plug, heat, task, alarm), `src` who set it (clive, user, or device for a schedule
set from a device's own card), and `tap` where tapping goes: {"to": "oneoff" | "routine" | "device" | "page", "id" | "entity" | "page"}.
"""

from __future__ import annotations

import logging
from datetime import datetime, timedelta
from typing import Any

from .routines_logic import domain_of, occurrences

_LOGGER = logging.getLogger(__name__)

MAX_DAYS = 31
KIND_OF_DOMAIN = {"cover": "blind", "light": "light", "scene": "light", "fan": "fan", "humidifier": "fan", "switch": "plug", "input_boolean": "plug", "climate": "heat"}


def kind_of(entity: str) -> str:
    return KIND_OF_DOMAIN.get(domain_of(entity), "light")


def _item(at: datetime, title: str, kind: str, src: str, tap: dict[str, Any], *, by: str = "", card: str = "") -> dict[str, Any]:
    return {"at": at.replace(tzinfo=None).isoformat(timespec="minutes"), "date": at.date().isoformat(), "time": at.strftime("%H:%M"), "title": title, "kind": kind, "src": src, "by": by, "card": card, "tap": tap}


def _dt(value: Any) -> datetime:
    """A time that arrives as text (a websocket answer) or as a datetime (a state's attribute inside Home Assistant)."""
    return value if isinstance(value, datetime) else datetime.fromisoformat(str(value))


def _at(day: Any, hhmm: str, tz: Any) -> datetime:
    return datetime(day.year, day.month, day.day, int(hhmm[:2]), int(hhmm[3:5]), tzinfo=tz)


def _days(now: datetime, days: int) -> list[Any]:
    return [(now + timedelta(days=i)).date() for i in range(days + 1)]


def from_routines(now: datetime, end: datetime, routines: list[dict[str, Any]], names: dict[str, str]) -> list[dict[str, Any]]:
    out = []
    for r in routines:
        if not r["enabled"]:
            continue
        kind = kind_of(r["entities"][0])
        once = r["when"]["kind"] == "once"
        for at in occurrences(r, now, end):
            out.append(_item(at, r["name"], kind, "user" if r.get("src") == "user" else "clive", {"to": "oneoff" if once else "routine", "id": r["id"]}, by=r.get("by", ""), card=""))
    return out


def from_blinds(now: datetime, end: datetime, blinds: dict[str, dict[str, Any]], words: Any) -> list[dict[str, Any]]:
    """Blind, fan and humidifier schedules: each Open/On and Close/Off time on its days."""
    out = []
    for entity, cfg in blinds.items():
        if not cfg.get("enabled", True):
            continue
        first, second = words(entity)
        kind = kind_of(entity)
        for day in _days(now, (end.date() - now.date()).days):
            for s in cfg.get("schedules", []):
                if day.weekday() not in s["days"]:
                    continue
                for hhmm, word in ((s.get("open"), first), (s.get("close"), second)):
                    if not hhmm:
                        continue
                    at = _at(day, hhmm, now.tzinfo)
                    if now < at <= end:
                        out.append(_item(at, f"{word} {str(cfg.get('name', entity)).strip()}", kind, "device", {"to": "device", "entity": entity}, card="Blind card" if kind == "blind" else "Device card"))
    return out


def from_purifier(now: datetime, end: datetime, purifier: dict[str, Any] | None) -> list[dict[str, Any]]:
    if not purifier or not purifier.get("enabled", True):
        return []
    out = []
    for day in _days(now, (end.date() - now.date()).days):
        for s in purifier.get("schedules", []):
            if day.weekday() not in s["days"]:
                continue
            for hhmm, mode in ((s.get("auto"), "Auto"), (s.get("sleep"), "Sleep")):
                if hhmm:
                    at = _at(day, hhmm, now.tzinfo)
                    if now < at <= end:
                        out.append(_item(at, f"{str(purifier.get('name', 'Air purifier')).strip()} to {mode}", "fan", "device", {"to": "device", "entity": purifier["entity"]}, card="Purifier card"))
    return out


def from_tests(now: datetime, end: datetime, tests: list[dict[str, Any]]) -> list[dict[str, Any]]:
    out = []
    for t in tests:
        at = t.get("at")
        if not at:
            continue
        when = _dt(at)
        if when.tzinfo is None:
            when = when.replace(tzinfo=now.tzinfo)
        if now < when <= end:
            out.append(_item(when, f"{t['name']} test", "alarm", "device", {"to": "page", "page": "security"}, card="Alarm tests"))
    return out


def from_overnight(now: datetime, end: datetime, overnight: dict[str, Any] | None) -> list[dict[str, Any]]:
    """Eco or Off overnight: it begins each night at the start time (Smart leaves the heating to Clive, so nothing is set)."""
    if not overnight or overnight.get("mode") not in ("Eco", "Off"):
        return []
    out = []
    start, stop = overnight.get("start"), overnight.get("end")
    if not start or not stop:
        return []
    for day in _days(now, (end.date() - now.date()).days):
        at = _at(day, start, now.tzinfo)
        if now < at <= end:
            out.append(_item(at, f"Heating {overnight['mode'].lower() if overnight['mode'] == 'Off' else 'to Eco'} overnight", "heat", "device", {"to": "page", "page": "climate"}, card="Overnight heating"))
    return out


def from_tasks(now: datetime, end: datetime, tasks: list[dict[str, Any]]) -> list[dict[str, Any]]:
    """To-do tasks due at a time in the window (a task with only a date is due that day, shown at the end of the day)."""
    out = []
    for t in tasks:
        due = t.get("due")
        if not due:
            continue
        try:
            when = datetime.fromisoformat(due)
        except ValueError:
            continue
        if len(due) <= 10:
            when = when.replace(hour=23, minute=59)
        if when.tzinfo is None:
            when = when.replace(tzinfo=now.tzinfo)
        if now < when <= end:
            who = ", ".join(t.get("for") or [])
            out.append(_item(when, t["title"], "task", "device", {"to": "page", "page": "todo"}, card="To-do" + (f" · {who}" if who else "")))
    return out


def from_charging(now: datetime, end: datetime, charging: dict[str, Any] | None) -> list[dict[str, Any]]:
    """Car charging: the slots Octopus has planned for the car (`dispatches`), and, for a car plugged in with smart charge on that Octopus doesn't
    schedule (not the Primary one), the start of each night's cheap-rate window (`window_start`, `window_end`: the next one, as ISO times)."""
    if not charging:
        return []
    out = []
    tap = {"to": "device", "entity": charging.get("entity", "")}
    for d in charging.get("dispatches", []):
        start, stop = _dt(d["start"]), _dt(d["end"])
        if now < start <= end:
            out.append(_item(start.astimezone(now.tzinfo), f"Car charging until {stop.astimezone(now.tzinfo).strftime('%H:%M')}", "car", "device", tap, card="Octopus"))
    if charging.get("plugged") and charging.get("smart") and charging.get("role") != "Primary" and charging.get("window_start"):
        first = _dt(charging["window_start"]).astimezone(now.tzinfo)
        stop = _dt(charging["window_end"]).astimezone(now.tzinfo) if charging.get("window_end") else None
        for i in range(MAX_DAYS + 1):
            start = first + timedelta(days=i)
            if start > end:
                break
            if start > now:
                until = f" until {(stop + timedelta(days=i)).strftime('%H:%M')}" if stop else ""
                out.append(_item(start, f"Car charging at the cheap rate{until}", "car", "device", tap, card="Zappi"))
    return out


def time_automation_runs(config: dict[str, Any], now: datetime, end: datetime) -> list[datetime]:
    """When an automation with a plain time trigger ("at": "18:00:00") and, optionally, a weekday condition runs in the window. Triggers that name
    a helper or anything else are not understood and give nothing."""
    triggers = config.get("triggers", config.get("trigger", []))
    triggers = triggers if isinstance(triggers, list) else [triggers]
    times = [str(t["at"])[:5] for t in triggers if isinstance(t, dict) and (t.get("trigger") or t.get("platform")) == "time" and isinstance(t.get("at"), str) and len(t["at"]) >= 5 and t["at"][2] == ":"]
    conditions = config.get("conditions", config.get("condition", []))
    conditions = conditions if isinstance(conditions, list) else [conditions]
    weekdays = None
    names = ["mon", "tue", "wed", "thu", "fri", "sat", "sun"]
    for c in conditions:
        if isinstance(c, dict) and (c.get("condition") == "time") and c.get("weekday"):
            wd = c["weekday"] if isinstance(c["weekday"], list) else [c["weekday"]]
            weekdays = {names.index(str(w).lower()[:3]) for w in wd if str(w).lower()[:3] in names}
    out = []
    for day in _days(now, (end.date() - now.date()).days):
        if weekdays is not None and day.weekday() not in weekdays:
            continue
        for hhmm in times:
            at = _at(day, hhmm, now.tzinfo)
            if now < at <= end:
                out.append(at)
    return sorted(out)


def from_automations(now: datetime, end: datetime, automations: list[dict[str, Any]], known: dict[str, tuple[str, str, str]]) -> list[dict[str, Any]]:
    """House automations that run at a set time and that Upcoming knows by name (`known`: alias -> (title, kind, card))."""
    out = []
    for cfg in automations:
        meta = known.get(str(cfg.get("alias", "")))
        if not meta:
            continue
        title, kind, card = meta
        for at in time_automation_runs(cfg, now, end):
            out.append(_item(at, title, kind, "device", {"to": "none"}, card=card))
    return out


def build(now: datetime, days: int, *, routines: list[dict[str, Any]], names: dict[str, str], blinds: dict[str, dict[str, Any]], words: Any,
          purifier: dict[str, Any] | None, tests: list[dict[str, Any]], overnight: dict[str, Any] | None, tasks: list[dict[str, Any]],
          charging: dict[str, Any] | None = None, automations: list[dict[str, Any]] | None = None, known_automations: dict[str, tuple[str, str, str]] | None = None) -> list[dict[str, Any]]:
    """Everything due in the next `days` days (1 to 31), soonest first."""
    days = max(1, min(int(days), MAX_DAYS))
    end = now + timedelta(days=days)
    def safely(source: Any, *args: Any) -> list[dict[str, Any]]:
        """One source going wrong (an odd value from one integration) leaves its items out and the rest of the list intact."""
        try:
            return source(*args)
        except Exception:  # noqa: BLE001
            _LOGGER.exception("Upcoming: %s failed, leaving it out", getattr(source, "__name__", "a source"))
            return []

    items = (
        safely(from_routines, now, end, routines, names) + safely(from_blinds, now, end, blinds, words) + safely(from_purifier, now, end, purifier)
        + safely(from_tests, now, end, tests) + safely(from_overnight, now, end, overnight) + safely(from_tasks, now, end, tasks)
        + safely(from_charging, now, end, charging) + safely(from_automations, now, end, automations or [], known_automations or {})
    )
    return sorted(items, key=lambda i: (i["at"], i["title"]))
