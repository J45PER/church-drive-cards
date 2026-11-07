"""The plain rules behind the air purifier's schedules (no Home Assistant in here, so they can be tested alone).

A schedule is a set of days and two times: the purifier goes to **Auto** at the first time and to **Sleep** at
the second, on each of those days. "Mon-Fri Auto: 07:00-23:30" is Auto from 07:00 and Sleep from 23:30, Monday
to Friday. Several schedules can be set (weekdays one way, the weekend another); a day no schedule covers is left
alone. If two schedules want different modes in the same minute, Auto wins.

Days are numbers, Monday = 0 to Sunday = 6 (Python's weekday()). Times are "HH:MM".
"""

from __future__ import annotations

import re
from datetime import datetime, timedelta
from typing import Any

MAX_SCHEDULES = 12
MODES = ("auto", "sleep")
DAY_NAMES = ("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
DEFAULT_ENTITY = "fan.hayleys_bedroom_air_purifier"
DEFAULTS: dict[str, Any] = {
    "enabled": True,
    "entity": DEFAULT_ENTITY,
    "schedules": [{"id": "everyday", "days": [0, 1, 2, 3, 4, 5, 6], "auto": "07:00", "sleep": "22:30"}],
}

_TIME = re.compile(r"^([01]?\d|2[0-3]):([0-5]\d)(?::[0-5]\d)?$")


def clean_time(value: Any) -> str | None:
    """"7:05" or "07:05:00" as "07:05", or None if it isn't a time."""
    m = _TIME.match(str(value or "").strip())
    return f"{int(m.group(1)):02d}:{m.group(2)}" if m else None


def clean_days(value: Any) -> list[int]:
    """The days as a sorted list of 0-6, without repeats or rubbish."""
    if not isinstance(value, (list, tuple, set)):
        return []
    out = {int(d) for d in value if isinstance(d, (int, str)) and str(d).strip().lstrip("-").isdigit() and 0 <= int(d) <= 6}
    return sorted(out)


def clean_schedules(value: Any) -> list[dict[str, Any]]:
    """The schedules that are valid (days, both times), ids made unique, at most MAX_SCHEDULES. Order is kept."""
    if not isinstance(value, (list, tuple)):
        return []
    out: list[dict[str, Any]] = []
    seen: set[str] = set()
    for raw in value:
        if not isinstance(raw, dict):
            continue
        days, auto, sleep = clean_days(raw.get("days")), clean_time(raw.get("auto")), clean_time(raw.get("sleep"))
        if not days or auto is None or sleep is None:
            continue
        base = re.sub(r"[^a-z0-9_-]", "", str(raw.get("id") or "").lower())[:24] or "s"
        sid, n = base, 1
        while sid in seen:
            n += 1
            sid = f"{base}{n}"
        seen.add(sid)
        out.append({"id": sid, "days": days, "auto": auto, "sleep": sleep})
        if len(out) >= MAX_SCHEDULES:
            break
    return out


def clean_config(value: Any, current: dict[str, Any] | None = None) -> dict[str, Any]:
    """Settings from something sent in: only what's valid is taken, the rest stays as it was (or the default)."""
    base = {**DEFAULTS, **(current or {})}
    cfg = {"enabled": bool(base["enabled"]), "entity": base["entity"], "schedules": [dict(s) for s in base["schedules"]]}
    if not isinstance(value, dict):
        return cfg
    if "enabled" in value:
        cfg["enabled"] = bool(value["enabled"])
    entity = value.get("entity")
    if isinstance(entity, str) and entity.startswith("fan."):
        cfg["entity"] = entity
    if "schedules" in value and isinstance(value["schedules"], (list, tuple)):
        # An empty list is allowed: it means "no schedule", not "keep the old one".
        cfg["schedules"] = clean_schedules(value["schedules"])
    return cfg


def days_text(days: list[int]) -> str:
    """"Every day", "Mon-Fri", "Sat & Sun", "Mon, Thu & Sat": runs of three or more days are ranges."""
    ds = clean_days(days)
    if not ds:
        return "No days"
    if len(ds) == 7:
        return "Every day"
    runs: list[list[int]] = []
    for d in ds:
        if runs and d == runs[-1][-1] + 1:
            runs[-1].append(d)
        else:
            runs.append([d])
    parts: list[str] = []
    for run in runs:
        if len(run) >= 3:
            parts.append(f"{DAY_NAMES[run[0]]}-{DAY_NAMES[run[-1]]}")
        else:
            parts.extend(DAY_NAMES[d] for d in run)
    return parts[0] if len(parts) == 1 else ", ".join(parts[:-1]) + " & " + parts[-1]


def summary(schedule: dict[str, Any]) -> str:
    """"Mon-Fri Auto: 07:00-23:30"."""
    return f"{days_text(schedule.get('days', []))} Auto: {schedule.get('auto', '')}-{schedule.get('sleep', '')}"


def events_at(schedules: list[dict[str, Any]], weekday: int, hhmm: str) -> list[str]:
    """The modes due at this minute of this weekday, one entry per schedule that fires (Auto first)."""
    out: list[str] = []
    for s in schedules:
        if weekday not in s.get("days", []):
            continue
        if s.get("auto") == hhmm:
            out.append("auto")
        if s.get("sleep") == hhmm:
            out.append("sleep")
    return sorted(out, key=lambda m: m != "auto")


def mode_for(modes: list[str]) -> str | None:
    """The one mode to apply when several are due together: Auto wins."""
    if not modes:
        return None
    return "auto" if "auto" in modes else modes[-1]


def due_between(schedules: list[dict[str, Any]], start: datetime, end: datetime) -> str | None:
    """The mode to apply for the minutes after `start` up to and including `end` (local times): the latest event
    in that window, or None. Used to catch up after Home Assistant was off through a scheduled minute."""
    if end <= start:
        return None
    minute = start.replace(second=0, microsecond=0) + timedelta(minutes=1)
    last: str | None = None
    while minute <= end:
        mode = mode_for(events_at(schedules, minute.weekday(), minute.strftime("%H:%M")))
        if mode:
            last = mode
        minute += timedelta(minutes=1)
    return last


def next_change(schedules: list[dict[str, Any]], now: datetime) -> dict[str, str] | None:
    """The next scheduled change after `now` (looking a week ahead): {"mode", "at" (ISO), "text" ("Sleep Fri 23:30")}."""
    if not schedules:
        return None
    start = now.replace(second=0, microsecond=0)
    for day in range(8):
        date = (start + timedelta(days=day)).date()
        hits: list[tuple[str, str]] = []
        for s in schedules:
            if date.weekday() in s["days"]:
                hits += [(s["auto"], "auto"), (s["sleep"], "sleep")]
        for hhmm, mode in sorted(hits):
            at = datetime.combine(date, datetime.min.time(), tzinfo=now.tzinfo).replace(hour=int(hhmm[:2]), minute=int(hhmm[3:]))
            if at > start:
                return {"mode": mode, "at": at.isoformat(), "text": f"{mode.capitalize()} {DAY_NAMES[date.weekday()]} {hhmm}"}
    return None
