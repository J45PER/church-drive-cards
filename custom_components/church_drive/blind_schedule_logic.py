"""The plain rules behind a blind's or a fan's schedules (no Home Assistant in here, so they can be tested alone).

A fan (or humidifier) uses the same rules: its "open" time turns it on (at the schedule's `mode`, a preset mode or
"percentage:NN", if one is set) and its "close" time turns it off. Its words are On and Off.

A schedule is a set of days and up to two times: the blind **opens** at the first time and **closes** at the
second, on each of those days. "Mon-Fri Open 07:30 · Close 20:00" opens at 07:30 and closes at 20:00, Monday to
Friday. Either time can be left out (a blind that only closes at night). Several schedules can be set (weekdays
one way, the weekend another); a day no schedule covers is left alone. If two schedules want opposite things in
the same minute, Close wins.

Every blind has its own list, kept by its cover entity, so a second blind works the same way.

Days are numbers, Monday = 0 to Sunday = 6 (Python's weekday()). Times are "HH:MM".
"""

from __future__ import annotations

import re
from datetime import datetime, timedelta
from typing import Any

MAX_SCHEDULES = 12
ACTIONS = ("open", "close")
# The kinds of entity that take a schedule, and what their two times are called.
DOMAINS = ("cover", "fan", "humidifier")
WORDS = {"cover": ("Open", "Close"), "fan": ("On", "Off"), "humidifier": ("On", "Off")}


def words_for(entity: str) -> tuple[str, str]:
    """("Open", "Close") for a blind, ("On", "Off") for a fan or humidifier."""
    return WORDS.get(str(entity).split(".", 1)[0], WORDS["cover"])


def schedulable(entity: Any) -> bool:
    """A cover, fan or humidifier entity."""
    return isinstance(entity, str) and entity.split(".", 1)[0] in DOMAINS and "." in entity


def clean_mode(value: Any) -> str | None:
    """What a fan turns on at: a preset mode's name, or "percentage:NN" (1 to 100); None for "as it was"."""
    text = str(value or "").strip()[:40]
    if not text:
        return None
    m = re.match(r"^percentage:(\d{1,3})$", text)
    if m:
        pct = int(m.group(1))
        return f"percentage:{pct}" if 1 <= pct <= 100 else None
    return text if re.match(r"^[\w .+-]+$", text) else None
DAY_NAMES = ("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

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
    """The schedules that are valid (days and at least one time), ids made unique, at most MAX_SCHEDULES. Order is kept.
    A time that is missing or not a time is left out; a schedule with neither time is dropped."""
    if not isinstance(value, (list, tuple)):
        return []
    out: list[dict[str, Any]] = []
    seen: set[str] = set()
    for raw in value:
        if not isinstance(raw, dict):
            continue
        days = clean_days(raw.get("days"))
        opens, closes = clean_time(raw.get("open")), clean_time(raw.get("close"))
        if not days or (opens is None and closes is None):
            continue
        base = re.sub(r"[^a-z0-9_-]", "", str(raw.get("id") or "").lower())[:24] or "s"
        sid, n = base, 1
        while sid in seen:
            n += 1
            sid = f"{base}{n}"
        seen.add(sid)
        item: dict[str, Any] = {"id": sid, "days": days}
        if opens is not None:
            item["open"] = opens
        if closes is not None:
            item["close"] = closes
        mode = clean_mode(raw.get("mode"))
        if mode is not None and opens is not None:
            item["mode"] = mode
        out.append(item)
        if len(out) >= MAX_SCHEDULES:
            break
    return out


def clean_blind(value: Any, current: dict[str, Any] | None = None) -> dict[str, Any]:
    """One blind's settings from something sent in: only what's valid is taken, the rest stays as it was."""
    base = current or {}
    cfg = {"enabled": bool(base.get("enabled", True)), "schedules": [dict(s) for s in base.get("schedules", [])]}
    if not isinstance(value, dict):
        return cfg
    if "enabled" in value:
        cfg["enabled"] = bool(value["enabled"])
    if "schedules" in value and isinstance(value["schedules"], (list, tuple)):
        # An empty list is allowed: it means "no schedule", not "keep the old one".
        cfg["schedules"] = clean_schedules(value["schedules"])
    return cfg


def clean_config(value: Any) -> dict[str, dict[str, Any]]:
    """Everything kept: {"cover.some_blind": {"enabled", "schedules"}, "fan.bedroom": {...}}. Only covers, fans and
    humidifiers are taken (the store's key is still "blinds", from when only blinds had schedules)."""
    out: dict[str, dict[str, Any]] = {}
    blinds = value.get("blinds") if isinstance(value, dict) else None
    if isinstance(blinds, dict):
        for entity, raw in blinds.items():
            if schedulable(entity):
                out[entity] = clean_blind(raw)
    return out


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


def summary(schedule: dict[str, Any], words: tuple[str, str] = WORDS["cover"]) -> str:
    """"Mon-Fri Open 07:30 · Close 20:00", or a fan's "Every day On 22:00 · Off 06:30" (a time that isn't set is left out)."""
    times = []
    if schedule.get("open"):
        times.append(f"{words[0]} {schedule['open']}")
    if schedule.get("close"):
        times.append(f"{words[1]} {schedule['close']}")
    return f"{days_text(schedule.get('days', []))} " + " · ".join(times) if times else days_text(schedule.get("days", []))


def events_at(schedules: list[dict[str, Any]], weekday: int, hhmm: str) -> list[str]:
    """The actions due at this minute of this weekday, one entry per schedule that fires (Open first)."""
    out: list[str] = []
    for s in schedules:
        if weekday not in s.get("days", []):
            continue
        if s.get("open") == hhmm:
            out.append("open")
        if s.get("close") == hhmm:
            out.append("close")
    return sorted(out, key=lambda a: a != "open")


def action_for(actions: list[str]) -> str | None:
    """The one action to take when several are due together: Close wins."""
    if not actions:
        return None
    return "close" if "close" in actions else actions[0]


def mode_at(schedules: list[dict[str, Any]], weekday: int, hhmm: str) -> str | None:
    """The mode a fan turns on at for an "open" due this minute: the first such schedule's, or None."""
    for s in schedules:
        if weekday in s.get("days", []) and s.get("open") == hhmm and s.get("mode"):
            return s["mode"]
    return None


def due_between_at(schedules: list[dict[str, Any]], start: datetime, end: datetime) -> tuple[str, datetime] | None:
    """As due_between, with the minute the change was due (so a fan's catch-up can find its mode)."""
    if end <= start:
        return None
    minute = start.replace(second=0, microsecond=0) + timedelta(minutes=1)
    last: tuple[str, datetime] | None = None
    while minute <= end:
        action = action_for(events_at(schedules, minute.weekday(), minute.strftime("%H:%M")))
        if action:
            last = (action, minute)
        minute += timedelta(minutes=1)
    return last


def due_between(schedules: list[dict[str, Any]], start: datetime, end: datetime) -> str | None:
    """The action to take for the minutes after `start` up to and including `end` (local times): the latest event
    in that window, or None. Used to catch up after Home Assistant was off through a scheduled minute."""
    if end <= start:
        return None
    minute = start.replace(second=0, microsecond=0) + timedelta(minutes=1)
    last: str | None = None
    while minute <= end:
        action = action_for(events_at(schedules, minute.weekday(), minute.strftime("%H:%M")))
        if action:
            last = action
        minute += timedelta(minutes=1)
    return last


def next_change(schedules: list[dict[str, Any]], now: datetime, words: tuple[str, str] = WORDS["cover"]) -> dict[str, str] | None:
    """The next scheduled change after `now` (looking a week ahead): {"action", "at" (ISO), "text" ("Close Fri 20:00",
    or "Off Fri 06:30" for a fan)}."""
    if not schedules:
        return None
    start = now.replace(second=0, microsecond=0)
    for day in range(8):
        date = (start + timedelta(days=day)).date()
        hits: list[tuple[str, str]] = []
        for s in schedules:
            if date.weekday() in s["days"]:
                if s.get("open"):
                    hits.append((s["open"], "open"))
                if s.get("close"):
                    hits.append((s["close"], "close"))
        for hhmm, action in sorted(hits):
            at = datetime.combine(date, datetime.min.time(), tzinfo=now.tzinfo).replace(hour=int(hhmm[:2]), minute=int(hhmm[3:]))
            if at > start:
                word = words[0] if action == "open" else words[1]
                return {"action": action, "at": at.isoformat(), "text": f"{word} {DAY_NAMES[date.weekday()]} {hhmm}"}
    return None
