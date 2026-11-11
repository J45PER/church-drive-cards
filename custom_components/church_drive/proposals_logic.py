"""The Manager's proposals: a change sent from the website, checked, then applied or declined, with every applied change kept as a past version (the rules only; storage is proposals.py)."""

from __future__ import annotations

from typing import Any

from .colours_logic import KEY, MAX_PALETTES, clean_overrides

KINDS = ("palette",)
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
