"""The plain rules behind each person's own layout (no Home Assistant in here, so they can be tested alone).

A layout starts as the house's (the dashboard's pages, sections and cards) and a person changes it: on every page they can reorder sections,
hide a house section and hide or reorder its cards; on Home they can also make their own sections of cards from any page. Only the person's
changes are kept (as ids of the house's sections and cards), so anything new in the house shows by default and nothing a person arranged moves.

A layout is {"pages": {page: {"order": [section ids], "hidden": [section ids], "cards": {section id: {"order": [card ids], "hidden": [card ids]}},
"own": [{"id", "title", "icon", "tone", "dynamic", "cards": [{"page", "section", "card"}]}]}}}.
"""

from __future__ import annotations

import re
from typing import Any

MAX_PAGES = 12
MAX_IDS = 120
MAX_ID_LENGTH = 160
MAX_OWN_SECTIONS = 12
MAX_OWN_CARDS = 40
MAX_ADDED = 24
MAX_TITLE = 24
OWN_PAGES = ("home",)
ICONS = (
    "cd:home", "cd:sun", "cd:moon", "cd:flower", "cd:bulb-lit", "cd:thermostat", "cd:room-living", "cd:room-bed-king", "cd:bolt",
    "cd:television", "cd:music-note", "cd:leaf", "cd:palette", "cd:checklist",
)
TONES = ("auto", "dynamic", "lights", "heating", "air", "blue", "green", "amber", "purple", "indigo", "coral", "teal", "orange", "grey")

_PAGE = re.compile(r"^[a-z0-9_-]{1,24}$")
_OWN_ID = re.compile(r"^[A-Za-z0-9_-]{1,32}$")
_ENTITY = re.compile(r"^[a-z0-9_]{1,24}\.[a-z0-9_]{1,80}$")


def _ids(value: Any, limit: int = MAX_IDS) -> list[str]:
    """A list of distinct short strings, in order."""
    out: list[str] = []
    for item in value if isinstance(value, list) else []:
        if isinstance(item, str) and item and len(item) <= MAX_ID_LENGTH and item not in out:
            out.append(item)
        if len(out) >= limit:
            break
    return out


def clean_added(value: Any) -> list[dict[str, Any]]:
    """Things a person added to a house section: a house card (its page, section and card id) or a single entity."""
    out: list[dict[str, Any]] = []
    for item in value if isinstance(value, list) else []:
        if not isinstance(item, dict):
            continue
        if isinstance(item.get("entity"), str) and _ENTITY.match(item["entity"]):
            clean: dict[str, Any] | None = {"entity": item["entity"]}
        else:
            ref = clean_own_card(item.get("card"))
            clean = {"card": ref} if ref else None
        if clean is not None and clean not in out:
            out.append(clean)
        if len(out) >= MAX_ADDED:
            break
    return out


def _card_prefs(value: Any) -> dict[str, dict[str, Any]]:
    out: dict[str, dict[str, Any]] = {}
    for section, prefs in (value.items() if isinstance(value, dict) else []):
        if not isinstance(section, str) or not section or len(section) > MAX_ID_LENGTH or not isinstance(prefs, dict):
            continue
        order, hidden, added = _ids(prefs.get("order")), _ids(prefs.get("hidden")), clean_added(prefs.get("added"))
        if order or hidden or added:
            out[section] = {"order": order, "hidden": hidden, "added": added}
        if len(out) >= MAX_IDS:
            break
    return out


def clean_own_card(value: Any) -> dict[str, str] | None:
    """A reference to a house card (page, section, card id), or None."""
    if not isinstance(value, dict):
        return None
    page, section, card = value.get("page"), value.get("section"), value.get("card")
    if not (isinstance(page, str) and _PAGE.match(page) and isinstance(section, str) and isinstance(card, str)):
        return None
    if not section or not card or len(section) > MAX_ID_LENGTH or len(card) > MAX_ID_LENGTH:
        return None
    return {"page": page, "section": section, "card": card}


def clean_own_section(value: Any) -> dict[str, Any] | None:
    """One of a person's own sections, or None if it isn't one."""
    if not isinstance(value, dict):
        return None
    ident, title = value.get("id"), str(value.get("title") or "").strip()[:MAX_TITLE]
    if not (isinstance(ident, str) and _OWN_ID.match(ident)) or not title:
        return None
    icon, tone = value.get("icon"), value.get("tone")
    cards: list[dict[str, str]] = []
    for raw in value.get("cards") if isinstance(value.get("cards"), list) else []:
        ref = clean_own_card(raw)
        if ref and ref not in cards:
            cards.append(ref)
        if len(cards) >= MAX_OWN_CARDS:
            break
    return {
        "id": ident, "title": title, "icon": icon if icon in ICONS else "cd:palette", "tone": tone if tone in TONES else "auto",
        "dynamic": bool(value.get("dynamic")), "cards": cards,
    }


def clean_page(page: str, value: Any) -> dict[str, Any]:
    """One page's changes. Own sections only count on the pages that allow them."""
    value = value if isinstance(value, dict) else {}
    own: list[dict[str, Any]] = []
    if page in OWN_PAGES:
        for raw in value.get("own") if isinstance(value.get("own"), list) else []:
            section = clean_own_section(raw)
            if section and all(section["id"] != o["id"] for o in own):
                own.append(section)
            if len(own) >= MAX_OWN_SECTIONS:
                break
    return {"order": _ids(value.get("order")), "hidden": _ids(value.get("hidden")), "cards": _card_prefs(value.get("cards")), "own": own}


def is_empty(page: dict[str, Any]) -> bool:
    return not (page["order"] or page["hidden"] or page["cards"] or page["own"])


def clean_layout(value: Any) -> dict[str, Any]:
    """A whole layout from something sent in: pages with changes only, everything checked."""
    pages_in = value.get("pages") if isinstance(value, dict) else None
    pages: dict[str, Any] = {}
    for name, raw in (pages_in.items() if isinstance(pages_in, dict) else []):
        if not isinstance(name, str) or not _PAGE.match(name):
            continue
        page = clean_page(name, raw)
        if not is_empty(page):
            pages[name] = page
        if len(pages) >= MAX_PAGES:
            break
    return {"pages": pages}
