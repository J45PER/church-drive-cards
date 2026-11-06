"""When to tell someone their Church Drive app is out of date.

Each phone tells Home Assistant its app's version when it registers (and again whenever the app opens on a new version),
and Home Assistant keeps it on the phone's mobile app entry. The house's own version is the newest app there is, because
the integration and every app share one number (docs/VERSIONING.md). This file only decides; people.py sends.
"""

from __future__ import annotations

import re
from datetime import datetime, timedelta
from typing import Any

# After the first nudge for a version, a phone that still hasn't updated is told again this often, never more.
REPEAT = timedelta(days=3)
# Nudges go out in the day, not at night: from this hour up to (not including) the end hour, local time.
FROM_HOUR = 9
TO_HOUR = 21


def parse_version(text: Any) -> tuple[int, int, int] | None:
    """The x.y.z at the start of "0.98.0 (123)" or "0.98.0", or None when there isn't one (a build number alone is not a version)."""
    found = re.match(r"\s*v?(\d+)\.(\d+)\.(\d+)", str(text or ""))
    return (int(found.group(1)), int(found.group(2)), int(found.group(3))) if found else None


def is_older(running: Any, newest: Any) -> bool:
    """True when the phone's app is a lower version than the house's. A version that can't be read is never 'older': no guessing."""
    a, b = parse_version(running), parse_version(newest)
    return a is not None and b is not None and a < b


def in_waking_hours(hour: int) -> bool:
    return FROM_HOUR <= hour < TO_HOUR


def should_nudge(running: Any, newest: Any, last: dict[str, Any] | None, now: datetime, hour: int) -> bool:
    """Whether to tell this person now. `last` is what was kept from the previous nudge: {"target": "0.99.0", "at": iso time}."""
    if not is_older(running, newest) or not in_waking_hours(hour):
        return False
    if not last or last.get("target") != str(newest):
        return True  # never told about this version
    try:
        sent = datetime.fromisoformat(str(last.get("at")))
    except ValueError:
        return True
    return now - sent >= REPEAT


def oldest_running(phones: list[dict[str, Any]], newest: Any) -> str | None:
    """The version text of the first of a person's phones that should update (only the Church Drive app's, switched on for notices)."""
    for phone in phones:
        if phone.get("own_app") and phone.get("on") and is_older(phone.get("version"), newest):
            return str(phone.get("version"))
    return None
