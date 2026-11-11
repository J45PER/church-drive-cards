"""The Manager's proposals: a change sent from the website, checked, then applied or declined, with every applied change kept as a past version (the rules only; storage is proposals.py)."""

from __future__ import annotations

import re
from typing import Any

from .colours_logic import KEY, MAX_PALETTES, clean_overrides

KINDS = ("palette", "cards", "icons", "layouts")
MAX_NOTE = 400
MAX_ITEMS = 200
MAX_VERSIONS = 50
# Text on a palette's Dark colour must be readable: WCAG's minimum for normal text.
MIN_CONTRAST = 4.5


def _luminance(hex_colour: str) -> float:
    channels = [int(hex_colour[i : i + 2], 16) / 255 for i in (1, 3, 5)]
    lin = [c / 12.92 if c <= 0.03928 else ((c + 0.055) / 1.055) ** 2.4 for c in channels]
    return 0.2126 * lin[0] + 0.7152 * lin[1] + 0.0722 * lin[2]


def contrast(a: str, b: str) -> float:
    """WCAG contrast ratio of two #rrggbb colours (1 to 21)."""
    hi, lo = sorted((_luminance(a), _luminance(b)), reverse=True)
    return (hi + 0.05) / (lo + 0.05)


def normalise_palette_change(change: Any) -> dict[str, Any]:
    """A palette change made safe: {mode: set|replace|reset, palettes|keys}. Raises ValueError saying what is wrong."""
    if not isinstance(change, dict):
        raise ValueError("The change must be an object")
    mode = change.get("mode", "set")
    if mode in ("set", "replace"):
        raw = change.get("palettes")
        if not isinstance(raw, dict) or (mode == "set" and not raw):
            raise ValueError("No palettes to change")
        cleaned = clean_overrides(raw)
        if mode == "set" and len(cleaned) != len(raw):
            raise ValueError("A palette name or its five #rrggbb colours is not valid")
        return {"mode": mode, "palettes": cleaned}
    if mode == "reset":
        keys = change.get("keys")
        if not isinstance(keys, list) or not keys or not all(isinstance(k, str) and KEY.match(k) for k in keys):
            raise ValueError("Say which palettes to put back")
        return {"mode": "reset", "keys": list(dict.fromkeys(keys))}
    raise ValueError("Unknown kind of change")


def apply_palette_change(overrides: dict[str, dict[str, Any]], change: dict[str, Any]) -> dict[str, dict[str, Any]]:
    """The overrides after [change] (already normalised): merged in, replaced wholesale, or some put back to their shipped colours."""
    mode = change["mode"]
    if mode == "replace":
        return clean_overrides(change["palettes"])
    if mode == "reset":
        return {k: v for k, v in overrides.items() if k not in change["keys"]}
    merged = {k: dict(v) for k, v in overrides.items()}
    for key, item in change["palettes"].items():
        merged[key] = {**merged.get(key, {}), **item}
    return clean_overrides(dict(list(merged.items())[:MAX_PALETTES]))


def check_palette_change(change: dict[str, Any]) -> list[str]:
    """What the automatic check does not like about a palette change (empty when it is fine): text on the Dark colour must be readable."""
    notes: list[str] = []
    for key, item in (change.get("palettes") or {}).items():
        colours = item.get("colours")
        if not colours:
            continue
        ratio = contrast("#FFFFFF", colours[1])
        if ratio < MIN_CONTRAST:
            notes.append(f"{key}: white text on its Dark colour {colours[1]} is hard to read (contrast {ratio:.1f}, wants {MIN_CONTRAST})")
        if len(set(colours)) < len(colours):
            notes.append(f"{key}: two of its five colours are the same")
    return notes


def new_proposal(pid: str, kind: str, change: dict[str, Any], note: str | None, by: str, now: float, checks: list[str], urgent: bool = False) -> dict[str, Any]:
    return {
        "id": pid, "kind": kind, "change": change, "note": (note or "").strip()[:MAX_NOTE], "by": by, "at": now, "status": "pending",
        "checks": checks, "urgent": bool(urgent), "reason": None, "resolved_at": None, "resolved_by": None,
    }


def trim_items(items: list[dict[str, Any]]) -> list[dict[str, Any]]:
    """Keep every pending proposal and as many of the newest resolved ones as fit."""
    if len(items) <= MAX_ITEMS:
        return items
    pending = [i for i in items if i["status"] == "pending"]
    resolved = [i for i in items if i["status"] != "pending"]
    keep = max(0, MAX_ITEMS - len(pending))
    kept = {i["id"] for i in resolved[-keep:]} if keep else set()
    return [i for i in items if i["status"] == "pending" or i["id"] in kept]


# ---- Cards, icons and layouts ----
# A cards proposal sets the palette a card uses (and a note) in the integration's own record; an icons or layouts proposal names one panel of one
# dashboard view, and Claude changes that dashboard when checking it. Each carries what it replaces (`before`) so it can be put back.

CARD_ID = re.compile(r"^[a-z0-9][a-z0-9-]{0,39}$")
ICON_NAME = re.compile(r"^[a-z]{2,6}:[A-Za-z0-9_ -]{1,60}$")
DASHBOARD = re.compile(r"^[a-z0-9][a-z0-9_-]{0,40}$")
MAX_TITLE = 80
MAX_PANELS = 60


def _title(value: Any) -> str:
    if not isinstance(value, str) or not value.strip() or len(value) > MAX_TITLE:
        raise ValueError("A panel title is missing or too long")
    return value.strip()


def normalise_cards_change(change: Any) -> dict[str, Any]:
    """{cards: {card id: {palette?, comment?}}}: each card needs a palette or a note."""
    raw = change.get("cards") if isinstance(change, dict) else None
    if not isinstance(raw, dict) or not raw or len(raw) > 100:
        raise ValueError("No cards to change")
    out: dict[str, dict[str, str]] = {}
    for cid, item in raw.items():
        if not isinstance(cid, str) or not CARD_ID.match(cid) or not isinstance(item, dict):
            raise ValueError("A card id is not valid")
        clean: dict[str, str] = {}
        if item.get("palette") is not None:
            if not isinstance(item["palette"], str) or not KEY.match(item["palette"]):
                raise ValueError(f"{cid}: the palette is not valid")
            clean["palette"] = item["palette"]
        if isinstance(item.get("comment"), str) and item["comment"].strip():
            clean["comment"] = item["comment"].strip()[:MAX_NOTE]
        if not clean:
            raise ValueError(f"{cid}: nothing to change")
        out[cid] = clean
    return {"cards": out}


def _target(change: dict[str, Any]) -> dict[str, str]:
    dash, view = change.get("dashboard"), change.get("view")
    if not isinstance(dash, str) or not DASHBOARD.match(dash) or not isinstance(view, str) or not DASHBOARD.match(view):
        raise ValueError("Say which dashboard and view")
    return {"dashboard": dash, "view": view}


def normalise_icons_change(change: Any) -> dict[str, Any]:
    """{dashboard, view, panel, icon, before?}: one panel's icon."""
    if not isinstance(change, dict):
        raise ValueError("The change must be an object")
    icon = change.get("icon")
    if not isinstance(icon, str) or not ICON_NAME.match(icon):
        raise ValueError("The icon name is not valid (like cd:bolt)")
    out = {**_target(change), "panel": _title(change.get("panel")), "icon": icon}
    if isinstance(change.get("before"), str) and ICON_NAME.match(change["before"]):
        out["before"] = change["before"]
    return out


def normalise_layouts_change(change: Any) -> dict[str, Any]:
    """{dashboard, view, order: [panel titles], hidden: [panel titles], before?: {order, hidden}}."""
    if not isinstance(change, dict):
        raise ValueError("The change must be an object")
    order = change.get("order")
    if not isinstance(order, list) or not order or len(order) > MAX_PANELS:
        raise ValueError("Give the panels in the order wanted")
    order = [_title(t) for t in order]
    if len(set(order)) != len(order):
        raise ValueError("A panel is listed twice")
    hidden = [_title(t) for t in (change.get("hidden") or [])]
    if not set(hidden) <= set(order):
        raise ValueError("A hidden panel is not in the list")
    out: dict[str, Any] = {**_target(change), "order": order, "hidden": hidden}
    before = change.get("before")
    if isinstance(before, dict) and isinstance(before.get("order"), list):
        out["before"] = {"order": [_title(t) for t in before["order"]][:MAX_PANELS], "hidden": [_title(t) for t in (before.get("hidden") or [])][:MAX_PANELS]}
    return out


def normalise_change(kind: str, change: Any) -> dict[str, Any]:
    if kind == "palette":
        return normalise_palette_change(change)
    if kind == "cards":
        return normalise_cards_change(change)
    if kind == "icons":
        return normalise_icons_change(change)
    if kind == "layouts":
        return normalise_layouts_change(change)
    raise ValueError("Unknown kind of proposal")


def check_change(kind: str, change: dict[str, Any], palette_keys: set[str] | None = None, icon_names: set[str] | None = None) -> list[str]:
    """What the automatic check does not like about a change (empty when it is fine)."""
    if kind == "palette":
        return check_palette_change(change)
    notes: list[str] = []
    if kind == "cards" and palette_keys is not None:
        for cid, item in change["cards"].items():
            if "palette" in item and item["palette"] not in palette_keys:
                notes.append(f"{cid}: there is no palette called {item['palette']}")
    if kind == "icons":
        icon = change["icon"]
        if icon.startswith("mdi:"):
            notes.append("A Material (mdi:) icon: the house's own cd: icons are preferred for anything new")
        elif icon.startswith("cd:") and icon_names is not None and icon not in icon_names:
            notes.append(f"{icon} is not in the house's icon set")
    if kind == "layouts" and len(change["hidden"]) == len(change["order"]):
        notes.append("Every panel would be hidden, so the page would be empty")
    return notes


def version_data_for(kind: str, item: dict[str, Any], current_cards: dict[str, Any]) -> Any:
    """What a past version of this kind holds, so it can be put back: the cards' previous picks, or the panel's / view's previous state."""
    change = item["change"]
    if kind == "cards":
        return {cid: current_cards.get(cid) for cid in change["cards"]}
    if kind == "icons":
        return {"dashboard": change["dashboard"], "view": change["view"], "panel": change["panel"], "icon": change.get("before")}
    if kind == "layouts":
        return {"dashboard": change["dashboard"], "view": change["view"], **(change.get("before") or {})}
    return None


def restore_change(kind: str, data: Any) -> dict[str, Any]:
    """The change that puts a past version back."""
    if kind == "cards":
        cards = {cid: ({"palette": v.get("palette")} if isinstance(v, dict) and v.get("palette") else {"comment": (v or {}).get("comment", "")} if isinstance(v, dict) and v.get("comment") else None) for cid, v in (data or {}).items()}
        cards = {cid: v for cid, v in cards.items() if v}
        if not cards:
            raise ValueError("That version had no picks to put back")
        return {"cards": cards}
    if kind == "icons":
        if not data or not data.get("icon"):
            raise ValueError("That version did not record the previous icon")
        return {"dashboard": data["dashboard"], "view": data["view"], "panel": data["panel"], "icon": data["icon"]}
    if kind == "layouts":
        if not data or not data.get("order"):
            raise ValueError("That version did not record the previous order")
        return {"dashboard": data["dashboard"], "view": data["view"], "order": data["order"], "hidden": data.get("hidden", [])}
    raise ValueError("Unknown kind of proposal")


MAX_GROUPS = 200


def clean_groups(raw: Any) -> dict[str, str]:
    """The palette groupings the owner has chosen: palette key to group name, both trimmed and kept short. Anything else is dropped."""
    out: dict[str, str] = {}
    if not isinstance(raw, dict):
        return out
    for key, name in raw.items():
        k, g = str(key).strip()[:60], str(name).strip()[:40] if isinstance(name, str) else ""
        if k and g and len(out) < MAX_GROUPS:
            out[k] = g
    return out
