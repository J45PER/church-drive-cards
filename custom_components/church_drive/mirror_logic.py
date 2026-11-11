"""The pure parts of the Firestore mirror: Home Assistant stays the one writer and the source of truth, and copies the design data (palettes, card
picks, the version history) to Firestore so the apps and the website can read it live. Firestore's own rules forbid every other writer."""

from __future__ import annotations

import json
from typing import Any

KEY_FILE = "church_drive/firebase-writer.json"  # the service account key (role Cloud Datastore User), in Home Assistant's config folder, never in the repo
SCOPE = "https://www.googleapis.com/auth/datastore"
DEBOUNCE_SECONDS = 5


def doc_url(project: str, collection: str, doc_id: str) -> str:
    return f"https://firestore.googleapis.com/v1/projects/{project}/databases/(default)/documents/{collection}/{doc_id}"


def to_value(v: Any) -> dict[str, Any]:
    """A Python value as Firestore's typed JSON value."""
    if v is None:
        return {"nullValue": None}
    if isinstance(v, bool):
        return {"booleanValue": v}
    if isinstance(v, int):
        return {"integerValue": str(v)}
    if isinstance(v, float):
        return {"doubleValue": v}
    if isinstance(v, str):
        return {"stringValue": v}
    if isinstance(v, (list, tuple, set)):
        return {"arrayValue": {"values": [to_value(x) for x in v]}}
    if isinstance(v, dict):
        return {"mapValue": {"fields": {str(k): to_value(x) for k, x in v.items()}}}
    return {"stringValue": str(v)}


def document_body(data: dict[str, Any]) -> dict[str, Any]:
    return {"fields": {str(k): to_value(v) for k, v in data.items()}}


def build_documents(config: dict[str, Any], overrides: dict[str, Any], cards: dict[str, Any], versions: dict[str, list[dict[str, Any]]],
                    app_version: str, now: float, groups: dict[str, str] | None = None) -> list[tuple[str, str, dict[str, Any]]]:
    """Every document the mirror keeps, as (collection, id, data): the live palettes, the card picks, the version history, and the revision."""
    docs: list[tuple[str, str, dict[str, Any]]] = [
        ("palettes", "current", {"revision": config.get("revision", 0), "palettes": config.get("palettes", {}), "overrides": overrides, "kind_palettes": config.get("kind_palettes", {})}),
        ("palettes", "groups", {"groups": groups or {}}),
        ("cards", "picks", {"cards": cards}),
    ]
    for kind, items in sorted(versions.items()):
        for v in items:
            rev = v.get("revision")
            if rev is None:
                continue
            docs.append(("history", f"{kind}-{rev}", {"kind": kind, **{k: x for k, x in v.items()}}))
    docs.append(("meta", "revision", {"colours": config.get("revision", 0), "version": app_version, "updated": now}))
    return docs


def fingerprint(data: dict[str, Any]) -> str:
    """Whether a document changed since it was last written: the same text means nothing to send. (`updated` is left out of meta so a quiet house stays quiet.)"""
    return json.dumps({k: v for k, v in data.items() if k != "updated"}, sort_keys=True, default=str)
