"""The plain rules behind the monthly alarm tests (no Home Assistant in here, so they can be tested alone).

Three tests, each with its own schedule: the house alarm (sounds the Ring Chime siren briefly), the smoke and heat
alarms (presses each one's Device Test button) and the CO alarm (presses its Device Test button). A test runs once a
month on a day of the month at a time ("the 1st at 11:00"); a day the month doesn't have (the 31st in November) is
taken as that month's last day. Everyone who is home is told 15 minutes and 5 minutes before.
"""

from __future__ import annotations

import calendar
import re
from datetime import datetime, timedelta
from typing import Any

KINDS = ("alarm", "smoke", "co")
NAMES = {"alarm": "House alarm", "smoke": "Smoke and heat alarms", "co": "CO alarm"}
WARN_MINUTES = (15, 5)
DEFAULT = {"enabled": False, "day": 1, "time": "11:00"}

_TIME = re.compile(r"^([01]?\d|2[0-3]):([0-5]\d)(?::[0-5]\d)?$")


def clean_time(value: Any) -> str | None:
    """"7:05" or "07:05:00" as "07:05", or None if it isn't a time."""
    m = _TIME.match(str(value or "").strip())
    return f"{int(m.group(1)):02d}:{m.group(2)}" if m else None


def clean_test(value: Any, current: dict[str, Any] | None = None) -> dict[str, Any]:
    """One test's schedule from something sent in: only what's valid is taken, the rest stays as it was."""
    cfg = {**DEFAULT, **(current or {})}
    if not isinstance(value, dict):
        return cfg
    if "enabled" in value:
        cfg["enabled"] = bool(value["enabled"])
    if "day" in value:
        try:
            day = int(value["day"])
            if 1 <= day <= 31:
                cfg["day"] = day
        except (TypeError, ValueError):
            pass
    if "time" in value and clean_time(value["time"]):
        cfg["time"] = clean_time(value["time"])
    return cfg


def clean_config(value: Any) -> dict[str, dict[str, Any]]:
    """Every test's schedule, each one complete."""
    tests = value.get("tests") if isinstance(value, dict) else None
    tests = tests if isinstance(tests, dict) else {}
    return {kind: clean_test(tests.get(kind)) for kind in KINDS}


def run_at(cfg: dict[str, Any], year: int, month: int, tz: Any = None) -> datetime:
    """When the test runs in that month: its day (or the month's last day if shorter) at its time."""
    day = min(int(cfg["day"]), calendar.monthrange(year, month)[1])
    hh, mm = (int(x) for x in cfg["time"].split(":"))
    return datetime(year, month, day, hh, mm, tzinfo=tz)


def _months_from(now: datetime, count: int = 3) -> list[tuple[int, int]]:
    out, y, m = [], now.year, now.month
    for _ in range(count):
        out.append((y, m))
        y, m = (y + 1, 1) if m == 12 else (y, m + 1)
    return out


def next_run(cfg: dict[str, Any], now: datetime) -> datetime | None:
    """The next time the test runs after `now`, or None while it's switched off."""
    if not cfg.get("enabled"):
        return None
    start = now.replace(second=0, microsecond=0)
    for y, m in _months_from(now):
        at = run_at(cfg, y, m, now.tzinfo)
        if at > start:
            return at
    return None


def due(cfg: dict[str, Any], now: datetime) -> str | None:
    """What's due this minute: "warn15", "warn5", "run", or None."""
    if not cfg.get("enabled"):
        return None
    minute = now.replace(second=0, microsecond=0)
    prev = (now.year - 1, 12) if now.month == 1 else (now.year, now.month - 1)
    for y, m in [prev] + _months_from(now, 2):
        at = run_at(cfg, y, m, now.tzinfo)
        if minute == at:
            return "run"
        for w in WARN_MINUTES:
            if minute == at - timedelta(minutes=w):
                return f"warn{w}"
    return None


def ordinal(day: int) -> str:
    """1 as "1st", 22 as "22nd", 13 as "13th"."""
    suffix = "th" if 11 <= day % 100 <= 13 else {1: "st", 2: "nd", 3: "rd"}.get(day % 10, "th")
    return f"{day}{suffix}"


def summary(cfg: dict[str, Any]) -> str:
    """"The 1st of each month at 11:00", or "Off"."""
    if not cfg.get("enabled"):
        return "Off"
    return f"The {ordinal(int(cfg['day']))} of each month at {cfg['time']}"


def when_text(at: datetime) -> str:
    """"Mon 1 Dec 11:00"."""
    return f"{at.strftime('%a')} {at.day} {at.strftime('%b %H:%M')}"


def warning(kind: str, minutes: int) -> tuple[str, str]:
    """The title and message told to everyone home before a test."""
    what = {
        "alarm": ("House alarm test", "The house alarm's indoor siren will sound for about 10 seconds. It's only a test."),
        "smoke": ("Smoke alarm test", "The smoke and heat alarms will beep for a few seconds. It's only a test."),
        "co": ("CO alarm test", "The CO alarm will beep for a few seconds. It's only a test."),
    }[kind]
    return f"{what[0]} in {minutes} minutes", what[1]
