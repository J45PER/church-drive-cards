"""The plain rules behind shopping lists (no Home Assistant in here, so they can be tested alone).

A shopping list has a name, the people it is for, an optional date, optional places (zones) where it
will be bought, and items. Each item is open, bought or unavailable. The list shows on each person's
to-do list as one task that carries a marker in its description, and is ticked off when no item is
left open. Items that were unavailable are remembered ("missed") and offered the next time a list is made.
"""

from __future__ import annotations

import math
import re
from datetime import date, timedelta
from typing import Any

OPEN, BOUGHT, UNAVAILABLE = "open", "bought", "unavailable"
STATUSES = (OPEN, BOUGHT, UNAVAILABLE)
# A list is either shopping (items bought or unavailable) or steps (a checklist: each step open or done, "bought").
SHOPPING, STEPS = "shopping", "steps"
# A project is a longer checklist with its own page in the apps (not a task on anyone's to-do list): steps, a target date and who it is for.
PROJECT = "project"
KINDS = (SHOPPING, STEPS, PROJECT)
MARKER = "church-drive-shopping:"
# A steps task's marker has this word after the id, so the cards and app say "to do" instead of "to get".
STEPS_WORD = " steps"
MAX_ITEMS = 200

# The aisles a shopping item is sorted into, in the order a usual shop walks them. Each has words that say an item belongs there.
CATEGORIES: list[tuple[str, tuple[str, ...]]] = [
    ("Fruit and veg", ("banana", "apple", "orange", "lemon", "lime", "grape", "berries", "strawberr", "blueberr", "raspberr", "melon", "pear", "peach", "plum", "avocado", "tomato", "potato", "onion", "garlic", "carrot", "broccoli", "cauliflower", "cabbage", "lettuce", "salad", "spinach", "rocket", "cucumber", "pepper", "mushroom", "courgette", "aubergine", "celery", "leek", "sprout", "pea", "bean", "corn", "herb", "basil", "parsley", "coriander", "ginger", "fruit", "veg")),
    ("Meat and fish", ("chicken", "beef", "pork", "lamb", "bacon", "sausage", "mince", "steak", "ham", "turkey", "salmon", "fish", "cod", "tuna", "prawn", "haddock", "burger", "gammon")),
    ("Dairy", ("milk", "cheese", "cheddar", "butter", "yoghurt", "yogurt", "cream", "egg", "margarine", "mozzarella", "feta", "halloumi", "custard")),
    ("Bakery", ("bread", "loaf", "roll", "bun", "bagel", "wrap", "pitta", "croissant", "crumpet", "muffin", "cake", "baguette", "sourdough", "tortilla")),
    ("Cupboard", ("pasta", "rice", "noodle", "flour", "sugar", "oil", "sauce", "ketchup", "mayo", "beans", "soup", "tin", "tinned", "cereal", "oats", "porridge", "stock", "spice", "salt", "jam", "honey", "peanut butter", "curry", "lentil", "chickpea", "nuts")),
    ("Snacks", ("crisps", "chocolate", "biscuit", "sweets", "snack", "popcorn", "cookie", "bar", "ice cream", "dessert")),
    ("Drinks", ("tea", "coffee", "juice", "squash", "water", "cola", "lemonade", "beer", "wine", "cider", "gin", "vodka", "whisky", "drink", "fizzy", "cordial")),
    ("Household", ("bin bag", "washing", "dishwasher", "tablet", "kitchen roll", "toilet", "tissue", "foil", "cling", "sponge", "bleach", "cleaner", "spray", "detergent", "softener", "nappies", "shampoo", "soap", "toothpaste", "deodorant", "batteries", "bulb", "candle", "wipes")),
]
OTHER = "Other"
CATEGORY_NAMES = [c for c, _ in CATEGORIES] + [OTHER]

# Words in a zone's name that mean a shop, so arriving there can bring up the list.
SHOP_WORDS = (
    "supermarket", "tesco", "asda", "sainsbury", "morrisons", "aldi", "lidl", "waitrose", "co-op", "coop",
    "m&s", "marks", "iceland", "ocado", "booths", "costco", "farmfoods", "spar", "shop", "store", "market", "pharmacy", "boots",
)

_LEAD = re.compile(r"^\s*(?:[-*••☐☑\[\]x ]+\s*)?(?:(?:please|also|and)\s+)?(?:buy|get|pick up|grab|need|order)\s+(?:some\s+|more\s+)?", re.I)


def checklist(kind: str) -> bool:
    """Whether a kind is a checklist (steps or a project): each item is open or done, never unavailable."""
    return kind in (STEPS, PROJECT)


def norm(text: Any) -> str:
    return " ".join(str(text or "").lower().split())


def clean_item(text: Any, kind: str = SHOPPING) -> str:
    """An item as shown: bullets and 'buy ' removed (not for steps, where "Get a ladder" stays as written), first letter capital, no longer than a task."""
    t = " ".join(str(text or "").split())
    t = re.sub(r"^[-*••☐]+\s*", "", t)
    t = t.strip(" .,;") if checklist(kind) else _LEAD.sub("", t).strip(" .,;")
    return (t[:1].upper() + t[1:])[:120]


def guess_category(text: Any) -> str:
    """The aisle an item most likely belongs in, from its words ("Other" when nothing fits)."""
    n = f" {norm(text)} "
    if any(w in n for w in (" kitchen roll", " toilet roll", " bin bag", " washing ", " dishwasher")):
        return "Household"
    for name, words in CATEGORIES:
        if any(f" {w}" in n or n.strip().startswith(w) for w in words):
            return name
    return OTHER


_QTY_WORD = re.compile(r"^\s*(\d{1,3})\s*(?:x|×)?\s+(?!\d)(.+)$", re.I)
_QTY_TAIL = re.compile(r"^(.+?)\s+(?:x|×)?\s*(\d{1,3})\s*$", re.I)


def parse_item(text: Any, kind: str = SHOPPING) -> tuple[str, int, str]:
    """(name, quantity, note) from what was typed: "milk 2", "2 milk", "2x milk", "milk x2" and "milk (semi-skimmed)" or "milk - semi-skimmed".
    A checklist step is left as written."""
    raw = " ".join(str(text or "").split())
    if checklist(kind):
        return clean_item(raw, kind), 1, ""
    note = ""
    m = re.search(r"\s*[(\[](.{1,60})[)\]]\s*$", raw) or re.search(r"\s+-\s+(.{1,60})$", raw)
    if m:
        note = m.group(1).strip()
        raw = raw[: m.start()].strip()
    qty = 1
    m = _QTY_TAIL.match(raw)
    if m and not re.search(r"\d", m.group(1)):
        raw, qty = m.group(1), int(m.group(2))
    else:
        m = _QTY_WORD.match(raw)
        if m:
            qty, raw = int(m.group(1)), m.group(2)
    return clean_item(raw, kind), max(1, min(qty, 99)), note[:60]


def with_defaults(item: dict[str, Any], kind: str = SHOPPING) -> dict[str, Any]:
    """An item with the fields a shopping item always has (older lists were saved without them)."""
    if checklist(kind):
        return item
    return {**item, "qty": int(item.get("qty") or 1), "note": str(item.get("note") or ""), "cat": item.get("cat") or guess_category(item.get("text"))}


def add_items(items: list[dict[str, Any]], texts: list[Any], new_id: Any, kind: str = SHOPPING) -> tuple[list[dict[str, Any]], int]:
    """[items] with each of [texts] added unless an open item already says the same. Returns (items, how many were added).
    A text may carry a quantity and a note ("milk 2", "bread (seeded)"); an entry may also be a dict with text, qty, note and cat.
    An item that was bought or unavailable and is asked for again is reopened."""
    out = [dict(i) for i in items]
    added = 0
    for raw in texts:
        given = raw if isinstance(raw, dict) else {}
        text, qty, note = parse_item(given.get("text") if given else raw, kind)
        if given:
            qty = max(1, min(int(given.get("qty") or qty), 99))
            note = str(given.get("note") if given.get("note") is not None else note)[:60]
        if not text or len(out) >= MAX_ITEMS:
            continue
        same = next((i for i in out if norm(i["text"]) == norm(text)), None)
        if same is not None:
            if same["status"] != OPEN:
                same["status"] = OPEN
                same.pop("bought_at", None)
                added += 1
            if not checklist(kind) and (qty > 1 or note):
                same["qty"] = qty if qty > 1 else same.get("qty", 1)
                same["note"] = note or same.get("note", "")
            continue
        entry: dict[str, Any] = {"id": new_id(), "text": text, "status": OPEN}
        if not checklist(kind):
            cat = given.get("cat") if given.get("cat") in CATEGORY_NAMES else guess_category(text)
            entry.update({"qty": qty, "note": note, "cat": cat})
        out.append(entry)
        added += 1
    return out, added


def set_fields(items: list[dict[str, Any]], item_id: str, qty: Any = None, note: Any = None, cat: Any = None) -> list[dict[str, Any]]:
    """[items] with one item's quantity, note or aisle changed."""
    if not any(i["id"] == item_id for i in items):
        raise KeyError(item_id)
    if cat is not None and cat not in CATEGORY_NAMES:
        raise ValueError(f"Unknown aisle: {cat}")
    out = []
    for i in items:
        if i["id"] != item_id:
            out.append(dict(i))
            continue
        j = dict(i)
        if qty is not None:
            j["qty"] = max(1, min(int(qty), 99))
        if note is not None:
            j["note"] = str(note)[:60]
        if cat is not None:
            j["cat"] = cat
        out.append(j)
    return out


def set_status(items: list[dict[str, Any]], item_id: str, status: str, kind: str = SHOPPING) -> list[dict[str, Any]]:
    if status not in STATUSES or (checklist(kind) and status == UNAVAILABLE):
        raise ValueError(f"Unknown status: {status}")
    if not any(i["id"] == item_id for i in items):
        raise KeyError(item_id)
    return [{**i, "status": status} if i["id"] == item_id else dict(i) for i in items]


def rename_item(items: list[dict[str, Any]], item_id: str, text: Any, kind: str = SHOPPING) -> list[dict[str, Any]]:
    """[items] with one item's words changed (cleaned like a new one). An empty text is refused, as is a clash with another item."""
    clean = clean_item(text, kind)
    if not clean:
        raise ValueError("An item needs some words")
    if not any(i["id"] == item_id for i in items):
        raise KeyError(item_id)
    if any(i["id"] != item_id and norm(i["text"]) == norm(clean) for i in items):
        raise ValueError("Another item already says that")
    return [{**i, "text": clean} if i["id"] == item_id else dict(i) for i in items]


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
    kind = lst.get("kind")
    return kind if kind in (STEPS, PROJECT) else SHOPPING


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
    return [l for l in lists if l["date"] == today and not l.get("done") and kind_of(l) != PROJECT]


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
            return f"At {place}: steps to do", f"{names}."
        return f"At {place}: shopping list", f"{names}."
    if missed:
        texts = ", ".join(m["text"] for m in missed[:8]) + (" …" if len(missed) > 8 else "")
        return f"At {place}: not available last time", f"You couldn't get: {texts}."
    return None



# ---- what people buy, and when ---------------------------------------------------

MAX_HISTORY = 400
MIN_BUYS = 3  # how many times an item must have been bought before it is suggested


def record_bought(history: dict[str, Any], text: str, cat: str, today: str) -> dict[str, Any]:
    """[history] with one purchase of [text] recorded on [today] (a date): the item's usual aisle and the last dozen days it was bought."""
    key = norm(text)
    if not key:
        return history
    out = {k: dict(v) for k, v in history.items()}
    entry = out.get(key) or {"text": text, "cat": cat, "dates": []}
    entry["text"], entry["cat"] = text, cat or entry.get("cat", OTHER)
    if not entry["dates"] or entry["dates"][-1] != today:
        entry["dates"] = [*entry["dates"], today][-12:]
    out[key] = entry
    if len(out) > MAX_HISTORY:
        oldest = sorted(out, key=lambda k: out[k]["dates"][-1])[: len(out) - MAX_HISTORY]
        for k in oldest:
            out.pop(k, None)
    return out


def _gaps(dates: list[str]) -> list[int]:
    days = [date.fromisoformat(d) for d in dates]
    return [(b - a).days for a, b in zip(days, days[1:]) if (b - a).days > 0]


def usual_gap(dates: list[str]) -> int | None:
    """The usual number of days between purchases (the middle gap), or None when there is too little to say."""
    gaps = sorted(_gaps(dates))
    return gaps[len(gaps) // 2] if len(dates) >= MIN_BUYS and gaps else None


def reason_for(gap: int) -> str:
    if 5 <= gap <= 9:
        return "most weeks"
    if gap >= 10:
        weeks = round(gap / 7)
        return f"every {weeks} weeks"
    return f"every {gap} days"


def suggestions(history: dict[str, Any], today: str, open_texts: list[str], dismissed: dict[str, str], limit: int = 6) -> list[dict[str, Any]]:
    """Things usually bought that are due again and not already on the list, most overdue first, each with its aisle and why.
    Something dismissed stays out until the date kept for it."""
    here = date.fromisoformat(today)
    on_list = {norm(t) for t in open_texts}
    out = []
    for key, entry in history.items():
        if key in on_list or dismissed.get(key, "") > today:
            continue
        gap = usual_gap(entry["dates"])
        if gap is None:
            continue
        since = (here - date.fromisoformat(entry["dates"][-1])).days
        if since < max(1, round(gap * 0.8)):
            continue
        out.append({"text": entry["text"], "cat": entry.get("cat", OTHER), "reason": reason_for(gap), "overdue": since - gap})
    out.sort(key=lambda s: -s["overdue"])
    return out[:limit]


def dismissed_until(today: str, weeks: int = 4) -> str:
    return (date.fromisoformat(today) + timedelta(weeks=weeks)).isoformat()


def clear_old_bought(items: list[dict[str, Any]], now_iso: str, hours: int = 24) -> list[dict[str, Any]]:
    """The main list's items without those bought more than [hours] ago (an item bought with no time kept is left)."""
    from datetime import datetime

    cutoff = datetime.fromisoformat(now_iso) - timedelta(hours=hours)
    keep = []
    for i in items:
        stamp = i.get("bought_at")
        if i["status"] == BOUGHT and stamp and datetime.fromisoformat(stamp) < cutoff:
            continue
        keep.append(i)
    return keep
