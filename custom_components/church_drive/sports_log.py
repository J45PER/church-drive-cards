"""A note of the competitions and sports Clive was asked about that have no stored logo or icon of their own.

The scores and fixtures cards show a competition's stored logo when the app has one, our football for football, and one generic
trophy for any other sport. Each time one of those fallbacks is used, Clive records what was asked about here, so the missing logos
can be added later. Pure logic, so it can be tested without Home Assistant.
"""

from __future__ import annotations

from typing import Any

# The competitions whose logo the app stores (the keys of COMPETITION_LOGOS in AssistData.kt, matched as part of the name).
KNOWN_COMPETITIONS = (
    "premier league", "conference league", "champions league", "europa league", "nations league", "world cup", "fa cup",
    "carabao", "efl cup", "league cup", "scottish premiership", "european championship", "championship", "league one",
    "league two", "efl",
)
FOOTBALL = ("", "football", "soccer")  # football has its own icon; any other sport shows the generic trophy
KEEP = 500  # most entries kept


def reasons(competition: str | None, sport: str | None) -> list[str]:
    """Why the card had to fall back to a generic icon (empty when everything asked about has its own)."""
    comp = (competition or "").strip().lower()
    spt = (sport or "").strip().lower()
    out: list[str] = []
    if comp and not any(k in comp for k in KNOWN_COMPETITIONS):
        out.append("competition has no logo")
    if spt not in FOOTBALL:
        out.append("sport has no icon of its own")
    return out


class LogoRequests:
    def __init__(self, items: dict[str, dict[str, Any]] | None = None) -> None:
        self.items: dict[str, dict[str, Any]] = items or {}

    def record(self, competition: str | None, sport: str | None, now_iso: str) -> bool:
        """Note a request; returns whether anything was recorded."""
        why = reasons(competition, sport)
        if not why:
            return False
        comp = (competition or "").strip()
        spt = (sport or "").strip().lower()
        key = f"{comp.lower()}|{spt}"
        item = self.items.get(key)
        if item is None:
            item = {"competition": comp, "sport": spt or "football", "reason": " and ".join(why), "count": 0, "first": now_iso}
            self.items[key] = item
        item["count"] += 1
        item["last"] = now_iso
        if len(self.items) > KEEP:
            oldest = sorted(self.items, key=lambda k: self.items[k]["last"])[: len(self.items) - KEEP]
            for k in oldest:
                del self.items[k]
        return True

    def as_list(self) -> list[dict[str, Any]]:
        """Most asked about first."""
        return sorted(self.items.values(), key=lambda i: (-i["count"], i["competition"].lower()))
