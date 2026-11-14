"""His devices: the phones with the Church Drive app and the voice satellites, shaped for the website's Clive page (the rules only; Home Assistant is read in __init__.py)."""

from __future__ import annotations

from datetime import datetime, timezone
from typing import Any

DEAD_STATES = ("unavailable", "unknown")
SATELLITE_STATES = {"idle": "Waiting", "listening": "Listening", "responding": "Speaking", "processing": "Thinking"}


def _seconds_ago(iso: Any, now: float) -> float | None:
    if not isinstance(iso, str):
        return None
    try:
        when = datetime.fromisoformat(iso.replace("Z", "+00:00"))
    except ValueError:
        return None
    if when.tzinfo is None:
        when = when.replace(tzinfo=timezone.utc)
    return max(0.0, now - when.timestamp())


def shape_phones(people: list[dict[str, Any]], stale_hours: float, now: float) -> list[dict[str, Any]]:
    """Every person's phone with the Church Drive app: who, which phone, its app version, when it was last heard and whether it is answering (heard within [stale_hours])."""
    out = []
    for person in people or []:
        for phone in person.get("phones") or []:
            if not phone.get("own_app"):
                continue
            ago = _seconds_ago(phone.get("seen"), now)
            out.append({
                "kind": "phone", "who": person.get("first") or person.get("name") or "", "name": phone.get("name") or "", "model": phone.get("model") or "",
                "version": phone.get("version") or None, "seen": phone.get("seen"), "answering": ago is not None and ago <= stale_hours * 3600, "notifications": bool(phone.get("on", True)),
            })
    return sorted(out, key=lambda p: (not p["answering"], p["who"], p["name"]))


def shape_satellite(entity_id: str, state: str, friendly_name: str, changed: str | None, area: str | None, version: str | None, model: str | None) -> dict[str, Any]:
    """A voice satellite: its state in words, and whether it is answering (not unavailable)."""
    return {
        "kind": "satellite", "name": friendly_name or entity_id, "entity_id": entity_id, "area": area or "", "model": model or "", "version": version or None,
        "state": SATELLITE_STATES.get(state, state if state not in DEAD_STATES else "Not answering"), "answering": state not in DEAD_STATES, "changed": changed,
    }
