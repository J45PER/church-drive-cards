"""A short memory of what each person was sent, so a phone that was offline can catch up.

The Church Drive app only hears the house while it is connected (it does not use Google's push service), so anything sent
while a phone is asleep, out of signal or restarting would be lost. The house keeps the last few notices for each person
and the app asks for the ones it missed when it reconnects. Pure logic, so it can be tested without Home Assistant.
"""

from __future__ import annotations

from typing import Any

KEEP = 20          # notices kept per person
MAX_AGE_S = 6 * 3600  # older than this isn't worth showing late
MAX_REPLAY = 8     # most that are replayed at once


class NoticeLog:
    def __init__(self) -> None:
        self._items: dict[str, list[dict[str, Any]]] = {}
        self._last = 0

    def next_seq(self, now_ms: int) -> int:
        """A number that always rises (milliseconds, so it keeps rising across restarts)."""
        self._last = max(self._last + 1, now_ms)
        return self._last

    def record(self, person: str, seq: int, title: str, message: str, data: dict[str, Any]) -> None:
        items = self._items.setdefault(person, [])
        # The same tag replaces its earlier notice, as on the phone.
        tag = data.get("tag")
        if tag:
            items[:] = [i for i in items if i["data"].get("tag") != tag]
        items.append({"seq": seq, "title": title, "message": message, "data": data})
        del items[:-KEEP]

    def missed(self, person: str, since: int, now_ms: int) -> list[dict[str, Any]]:
        """What the person was sent after [since] and recently enough to matter, oldest first."""
        fresh = [i for i in self._items.get(person, []) if i["seq"] > since and now_ms - i["seq"] <= MAX_AGE_S * 1000]
        return fresh[-MAX_REPLAY:]

    def latest(self, person: str) -> int:
        items = self._items.get(person, [])
        return items[-1]["seq"] if items else 0
