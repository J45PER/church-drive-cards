"""The plain rules behind shopping lists (no Home Assistant in here, so they can be tested alone).

A shopping list has a name, the people it is for, an optional date, optional places (zones) where it
will be bought, and items. Each item is open, bought or unavailable. The list shows on each person's
to-do list as one task that carries a marker in its description, and is ticked off when no item is
left open. Items that were unavailable are remembered ("missed") and offered the next time a list is made.
"""

from __future__ import annotations

import math
import re
from datetime import date
from typing import Any

OPEN, BOUGHT, UNAVAILABLE = "open", "bought", "unavailable"
STATUSES = (OPEN, BOUGHT, UNAVAILABLE)
# A list is either shopping (items bought or unavailable) or steps (a checklist: each step open or done, "bought").
SHOPPING, STEPS = "shopping", "steps"
KINDS = (SHOPPING, STEPS)
MARKER = "church-drive-shopping:"
# A steps task's marker has this word after the id, so the cards and app say "to do" instead of "to get".
STEPS_WORD = " steps"
MAX_ITEMS = 200

# Words in a zone's name that mean a shop, so arriving there can bring up the list.
SHOP_WORDS = (
    "supermarket", "tesco", "asda", "sainsbury", "morrisons", "aldi", "lidl", "waitrose", "co-op", "coop",
    "m&s", "marks", "iceland", "ocado", "booths", "costco", "farmfoods", "spar", "shop", "store", "market", "pharmacy", "boots",
)

_LEAD = re.compile(r"^\s*(?:[-*••☐☑\[\]x ]+\s*)?(?:(?:please|also|and)\s+)?(?:buy|get|pick up|grab|need|order)\s+(?:some\s+|more\s+)?", re.I)


def norm(text: Any) -> str:
    return " ".join(str(text or "").lower().split())


def clean_item(text: Any, kind: str = SHOPPING) -> str:
    """An item as shown: bullets and 'buy ' removed (not for steps, where "Get a ladder" stays as written), first letter capital, no longer than a task."""
    t = " ".join(str(text or "").split())
    t = re.sub(r"^[-*••☐]+\s*", "", t)
    t = t.strip(" .,;") if kind == STEPS else _LEAD.sub("", t).strip(" .,;")
    return (t[:1].upper() + t[1:])[:120]


def add_items(items: list[dict[str, Any]], texts: list[str], new_id: Any, kind: str = SHOPPING) -> tuple[list[dict[str, Any]], int]:
    """[items] with each of [texts] added unless an open item already says the same. Returns (items, how many were added).
    An item that was bought or unavailable and is asked for again is reopened."""
    out = [dict(i) for i in items]
    added = 0
    for raw in texts:
        text = clean_item(raw, kind)
        if not text or len(out) >= MAX_ITEMS:
            continue
        same = next((i for i in out if norm(i["text"]) == norm(text)), None)
        if same is not None:
            if same["status"] != OPEN:
                same["status"] = OPEN
                added += 1
            continue
        out.append({"id": new_id(), "text": text, "status": OPEN})
        added += 1
    return out, added


def set_status(items: list[dict[str, Any]], item_id: str, status: str, kind: str = SHOPPING) -> list[dict[str, Any]]:
    if status not in STATUSES or (kind == STEPS and status == UNAVAILABLE):
        raise ValueError(f"Unknown status: {status}")
    if not any(i["id"] == item_id for i in items):
        raise KeyError(item_id)
    return [{**i, "status": status} if i["id"] == item_id else dict(i) for i in items]


def counts(items: list[dict[str, Any]]) -> dict[str, int]:
    c = {OPEN: 0, BOUGHT: 0, UNAVAILABLE: 0}
    for i in items:
        c[i["status"]] = c.get(i["status"], 0) + 1
    c["total"] = len(items)
    return c


def is_done(items: list[dict[str, Any]]) -> bool:
    """A list is done when it has items and none is left open."""
    return bool(items) and all(i["status"] != OPEN for i in items)


def task_summary(name: str, items: list[dict[str, Any]], kind: str = SHOPPING) -> str:
    c = counts(items)
    word = "Steps" if kind == STEPS else "Shopping"
    base = f"{word}: {name}" if name and norm(name) != norm(word) else word
    return f"{base} ({c['total'] - c[OPEN]} of {c['total']})" if c["total"] else base


def task_description(list_id: str, items: list[dict[str, Any]], kind: str = SHOPPING) -> str:
    """The open items, one a line, then the marker the cards and app look for (with STEPS_WORD after it for a steps list)."""
    lines = [f"• {i['text']}" for i in items if i["status"] == OPEN][:20]
    return "\n".join([*lines, f"{MARKER}{list_id}{STEPS_WORD if kind == STEPS else ''}"])


def kind_of(lst: dict[str, Any]) -> str:
    return STEPS if lst.get("kind") == STEPS else SHOPPING


def parse_marker(description: Any) -> str | None:
    """The shopping list id a task's description points at, if it does."""
    m = re.search(re.escape(MARKER) + r"([0-9a-f]{6,32})", str(description or ""))
    return m.group(1) if m else None


def missed_from(items: list[dict[str, Any]]) -> list[str]:
    return [i["text"] for i in items if i["status"] == UNAVAILABLE]


def merge_missed(missed: list[dict[str, Any]], texts: list[str], list_name: str, today: str, limit: int = 60) -> list[dict[str, Any]]:
    """Add the unavailable items of a finished list to what is remembered, without repeats."""
    out = [dict(m) for m in missed]
    for t in texts:
        if not any(norm(m["text"]) == norm(t) for m in out):
            out.append({"text": t, "list": list_name, "date": today})
    return out[-limit:]


def drop_missed(missed: list[dict[str, Any]], texts: list[str]) -> list[dict[str, Any]]:
    """Forget items that are on a list again (or were bought)."""
    gone = {norm(t) for t in texts}
    return [dict(m) for m in missed if norm(m["text"]) not in gone]


def valid_date(value: Any) -> str:
    """YYYY-MM-DD or empty."""
    text = str(value or "").strip()[:10]
    try:
        return date.fromisoformat(text).isoformat() if text else ""
    except ValueError:
        return ""


def for_person(lists: list[dict[str, Any]], first: str) -> list[dict[str, Any]]:
    """Open (not finished) lists the person is on, soonest date first, undated last."""
    mine = [l for l in lists if first in l["people"] and not l.get("done")]
    return sorted(mine, key=lambda l: (l["date"] == "", l["date"], l["created"]))


def pick_list(lists: list[dict[str, Any]], first: str) -> dict[str, Any] | None:
    """Where shared items go by default: the person's soonest open shopping list (never a steps list)."""
    mine = [l for l in for_person(lists, first) if kind_of(l) == SHOPPING]
    return mine[0] if mine else None


def due_today(lists: list[dict[str, Any]], today: str) -> list[dict[str, Any]]:
    return [l for l in lists if l["date"] == today and not l.get("done")]


def is_shop_zone(zone_name: str, list_places: set[str], zone_entity: str) -> bool:
    """Whether a zone is somewhere to shop: one a list says it will be bought at, or named like a shop."""
    if zone_entity in list_places:
        return True
    n = norm(zone_name)
    return any(w in n for w in SHOP_WORDS)


MAX_PLACES = 5
NEAR_METRES = 150  # how close counts as "at the shop"


def clean_place(raw: Any) -> dict[str, Any] | None:
    """A place from a map search ({name, address, lat, lon}), checked, or None if it isn't one."""
    if not isinstance(raw, dict):
        return None
    try:
        lat, lon = float(raw.get("lat")), float(raw.get("lon"))
    except (TypeError, ValueError):
        return None
    if not (-90 <= lat <= 90 and -180 <= lon <= 180) or (lat == 0 and lon == 0):
        return None
    name = " ".join(str(raw.get("name") or "").split())[:80]
    address = " ".join(str(raw.get("address") or "").split())[:160]
    return {"name": name or address or "Shop", "address": address, "lat": round(lat, 6), "lon": round(lon, 6)}


def clean_places(raw: Any) -> list[dict[str, Any]]:
    """Up to MAX_PLACES checked places, without repeats."""
    out: list[dict[str, Any]] = []
    for item in raw or []:
        p = clean_place(item)
        if p and not any(abs(p["lat"] - q["lat"]) < 1e-5 and abs(p["lon"] - q["lon"]) < 1e-5 for q in out):
            out.append(p)
    return out[:MAX_PLACES]


def valid_time(value: Any) -> str:
    """HH:MM or empty."""
    m = re.fullmatch(r"\s*([01]?\d|2[0-3]):([0-5]\d)\s*", str(value or ""))
    return f"{int(m.group(1)):02d}:{m.group(2)}" if m else ""


def distance_m(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    """Metres between two points on the earth (haversine)."""
    r = 6371000.0
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dp, dl = p2 - p1, math.radians(lon2 - lon1)
    a = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * r * math.asin(math.sqrt(a))


def near_place(places: list[dict[str, Any]], lat: Any, lon: Any, metres: float = NEAR_METRES) -> dict[str, Any] | None:
    """The first place within [metres] of a position, or None (also when the position is unknown)."""
    try:
        la, lo = float(lat), float(lon)
    except (TypeError, ValueError):
        return None
    return next((p for p in places if distance_m(la, lo, p["lat"], p["lon"]) <= metres), None)


def maps_url(place: dict[str, Any]) -> str:
    """A Google Maps link that starts directions to the place."""
    return f"https://www.google.com/maps/dir/?api=1&destination={place['lat']},{place['lon']}"


def list_line(l: dict[str, Any]) -> str:
    c = counts(l["items"])
    if kind_of(l) == STEPS:
        return f"{l['name'] or 'Steps'} ({c[OPEN]} to do)"
    return f"{l['name'] or 'Shopping'} ({c[OPEN]} to get)"


def arrival_message(place: str, mine: list[dict[str, Any]], missed: list[dict[str, Any]]) -> tuple[str, str] | None:
    """(title, message) for arriving at a shop, or None when there is nothing to say."""
    open_lists = [l for l in mine if counts(l["items"])[OPEN]]
    if open_lists:
        names = ", ".join(list_line(l) for l in open_lists[:3])
        if all(kind_of(l) == STEPS for l in open_lists):
            return f"At {place}: steps to do", f"{names}. Tap to open the list."
        return f"At {place}: shopping list", f"{names}. Tap to open the list."
    if missed:
        texts = ", ".join(m["text"] for m in missed[:8]) + (" …" if len(missed) > 8 else "")
        return f"At {place}: not available last time", f"You couldn't get: {texts}."
    return None
