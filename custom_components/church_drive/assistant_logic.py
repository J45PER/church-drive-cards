"""What the house tells the apps about the assistant besides his name: how a "done" card's icon moves, and misheard names to put right.

Pure rules (no Home Assistant), tested in test/assistant_logic_test.py. The shipped values are in the served cards.json
(`done_motions.rules`, `heard.corrections`); the owner's changes replace them whole.
"""

from __future__ import annotations

import re
from typing import Any

MOTION_TYPES = {"swap", "wipe", "spin", "cycle"}
_WORD = re.compile(r"^[a-z][a-z' -]{0,30}$")
_ICON = re.compile(r"^[a-z]{2,4}:[a-z0-9-]{1,60}$")
MAX_RULES = 80
MAX_HEARD = 200


def _strings(raw: Any, limit: int = 12) -> list[str]:
    if not isinstance(raw, list):
        return []
    return [s.strip().lower() for s in raw if isinstance(s, str) and 0 < len(s.strip()) <= 40][:limit]


def clean_motion(raw: Any) -> dict[str, Any] | None:
    """A motion the apps can play, or None (a plain tick) when it is missing or not one they know."""
    if not isinstance(raw, dict) or raw.get("type") not in MOTION_TYPES:
        return None
    kind = raw["type"]
    ms = raw.get("ms")
    out: dict[str, Any] = {"type": kind, "ms": int(ms) if isinstance(ms, (int, float)) and 50 <= ms <= 10000 else 600}
    icons = [i for i in _strings(raw.get("icons")) if _ICON.match(i)]
    for key in ("from", "to", "icon"):
        value = raw.get(key)
        if isinstance(value, str) and _ICON.match(value.strip().lower()):
            out[key] = value.strip().lower()
    if kind in ("swap", "wipe") and not ("from" in out and "to" in out):
        return None
    if kind == "wipe":
        out["dir"] = "up" if raw.get("dir") == "up" else "down"
    if kind == "spin":
        if "icon" not in out:
            return None
        turns = raw.get("turns")
        out["turns"] = int(turns) if isinstance(turns, (int, float)) and 1 <= turns <= 20 else 3
    if kind == "cycle":
        if len(icons) < 2:
            return None
        out["icons"] = icons
    return out


def clean_rules(raw: Any) -> list[dict[str, Any]]:
    """The rules that say which motion a done card plays, made safe: a rule must name an icon (`icon` or `is`)."""
    out: list[dict[str, Any]] = []
    if not isinstance(raw, list):
        return out
    for item in raw[:MAX_RULES]:
        if not isinstance(item, dict):
            continue
        rule: dict[str, Any] = {}
        for key in ("icon", "is", "also", "not", "says"):
            values = _strings(item.get(key))
            if values:
                rule[key] = values
        if "icon" not in rule and "is" not in rule:
            continue
        rule["motion"] = clean_motion(item.get("motion"))
        out.append(rule)
    return out


def clean_heard(raw: Any) -> dict[str, str]:
    """Misheard word (lower case) to the name that was said."""
    out: dict[str, str] = {}
    if not isinstance(raw, dict):
        return out
    for wrong, right in list(raw.items())[:MAX_HEARD]:
        if not isinstance(wrong, str) or not isinstance(right, str):
            continue
        w, r = wrong.strip().lower(), right.strip()
        if _WORD.match(w) and 0 < len(r) <= 40 and w != r.lower():
            out[w] = r
    return out


def first_names(names: list[str]) -> list[str]:
    """The household's first names, once each, in order ("Jamie Smith" is Jamie)."""
    out: list[str] = []
    for name in names:
        first = (name or "").strip().split(" ")[0]
        if first and first not in out:
            out.append(first)
    return out
