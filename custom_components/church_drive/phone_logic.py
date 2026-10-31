"""Finds the leftover registrations of the Church Drive app in Home Assistant's mobile app list.

Home Assistant doesn't recognise a phone that registers again (it makes a new entry every time, the app having signed out,
been reinstalled or been told the old one is gone). The old entries are dead: the app only holds the newest webhook. They
share one notify name, so notices could go to a registration nobody listens on, and the phone showed up several times."""

from __future__ import annotations

from typing import Any, Iterable

OWN_APP_IDS = ("com.churchdrive.manager", "com.churchdrive.app")


def _name(value: Any) -> str:
    return " ".join(str(value or "").split()).casefold()


def leftover_entries(entries: Iterable[dict[str, Any]]) -> list[str]:
    """The entry ids to remove. Each entry has `entry_id`, `app_id`, `user_id` and `device_name`. Only the Church Drive
    app's entries for the same signed-in person and the same phone name are compared, and the newest one is kept
    (entry ids sort by time). Entries with no person or no name are left alone."""
    groups: dict[tuple[str, str], list[str]] = {}
    for e in entries:
        if e.get("app_id") not in OWN_APP_IDS or not e.get("user_id") or not _name(e.get("device_name")):
            continue
        groups.setdefault((str(e["user_id"]), _name(e["device_name"])), []).append(str(e["entry_id"]))
    out: list[str] = []
    for ids in groups.values():
        out.extend(sorted(ids)[:-1])
    return sorted(out)
