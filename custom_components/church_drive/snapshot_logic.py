"""When a camera needs a new snapshot (pure rules; events.py presses the camera's Take Snapshot button).

Ring's snapshots are taken on motion and on a ring, so a busy camera always has a fresh one. A quiet one goes stale, so Home Assistant asks for a
new picture once the newest thing known about the camera (its last picture, its last activity, the last time its button was pressed, from
anywhere) is older than the interval. Counting from the last event is what keeps a busy camera from being asked at all. Battery cameras are
asked less often, because every snapshot wakes them.
"""

from __future__ import annotations

from typing import Iterable

DEFAULT_MINUTES = 60          # a camera on mains power
DEFAULT_BATTERY_MINUTES = 180  # a camera with a battery
CHECK_MINUTES = 5             # how often the cameras are looked at


def interval_s(has_battery: bool, minutes: int | float | None, battery_minutes: int | float | None) -> float:
    """Seconds between snapshots for a camera; 0 means never (the setting is 0)."""
    chosen = battery_minutes if has_battery else minutes
    default = DEFAULT_BATTERY_MINUTES if has_battery else DEFAULT_MINUTES
    try:
        value = default if chosen is None else float(chosen)
    except (TypeError, ValueError):
        value = default
    return max(0.0, value) * 60


def newest(times: Iterable[float | None]) -> float:
    """The latest of the times that are known (0 if none are)."""
    return max([t for t in times if t], default=0.0)


def snapshot_due(now: float, last_known: float, asked: float, interval: float, available: bool = True) -> bool:
    """Whether to press the button now: not never, the camera is there, nothing newer than [interval] ago (a picture, an event, a press from
    anywhere), and not already asked within [interval] (the press may not show up as a new picture for a while)."""
    if interval <= 0 or not available:
        return False
    return now - max(last_known, asked) >= interval
