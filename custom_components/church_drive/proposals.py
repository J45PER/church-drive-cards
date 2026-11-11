"""The Manager's proposals and the version history of what was applied (rules in proposals_logic.py)."""

from __future__ import annotations

import time
import uuid
from typing import Any

from homeassistant.core import HomeAssistant
from homeassistant.helpers.dispatcher import async_dispatcher_send
from homeassistant.helpers.storage import Store

from .proposals_logic import (
    KINDS, MAX_VERSIONS, apply_palette_change, check_change, new_proposal, normalise_change, restore_change, trim_items, version_data_for,
)

STORE_KEY = "church_drive.proposals"
SIGNAL_PROPOSALS = "church_drive_proposals_signal"


class Proposals:
    def __init__(self, hass: HomeAssistant) -> None:
        self._hass = hass
        self._store: Store[dict] = Store(hass, 1, STORE_KEY)
        self.items: list[dict[str, Any]] = []
        self.versions: dict[str, list[dict[str, Any]]] = {}
        # What the integration itself records: each card's chosen palette and note.
        self.cards: dict[str, dict[str, str]] = {}

    async def async_load(self) -> None:
        saved = await self._store.async_load() or {}
        self.items = [i for i in saved.get("items", []) if isinstance(i, dict) and i.get("id")]
        self.versions = {k: [v for v in vs if isinstance(v, dict)] for k, vs in (saved.get("versions") or {}).items() if k in KINDS}
        cards = saved.get("cards")
        self.cards = {k: dict(v) for k, v in cards.items() if isinstance(v, dict)} if isinstance(cards, dict) else {}

    async def _save(self) -> None:
        await self._store.async_save({"items": self.items, "versions": self.versions, "cards": self.cards})
        async_dispatcher_send(self._hass, SIGNAL_PROPOSALS)

    @property
    def pending(self) -> list[dict[str, Any]]:
        return [i for i in self.items if i["status"] == "pending"]

    def get(self, pid: str) -> dict[str, Any] | None:
        return next((i for i in self.items if i["id"] == pid), None)

    def listing(self, status: str | None = None) -> list[dict[str, Any]]:
        """Newest first."""
        return [i for i in reversed(self.items) if status in (None, "all") or i["status"] == status]

    def version_list(self, kind: str) -> list[dict[str, Any]]:
        """Past versions of [kind], newest first."""
        return list(reversed(self.versions.get(kind, [])))

    def _next_revision(self, kind: str) -> int:
        return max((v.get("revision", 0) for v in self.versions.get(kind, [])), default=0) + 1

    async def async_propose(
        self, kind: str, change: Any, note: str | None, by: str, urgent: bool = False, restore: int | None = None,
        palette_keys: set[str] | None = None, icon_names: set[str] | None = None,
    ) -> dict[str, Any]:
        """Keep a new proposal. A restore asks for a past version back. Raises ValueError for a change that makes no sense."""
        if kind not in KINDS:
            raise ValueError("Unknown kind of proposal")
        if restore is not None:
            version = next((v for v in self.versions.get(kind, []) if v.get("revision") == restore), None)
            if version is None:
                raise ValueError("That version is not kept any more")
            change = {"mode": "replace", "palettes": version["overrides"]} if kind == "palette" else restore_change(kind, version.get("data"))
            note = note or f"Restore version {restore}"
        clean = normalise_change(kind, change)
        item = new_proposal(uuid.uuid4().hex[:10], kind, clean, note, by, time.time(), check_change(kind, clean, palette_keys, icon_names), urgent)
        self.items = trim_items([*self.items, item])
        await self._save()
        return item

    async def async_resolve(self, pid: str, outcome: str, reason: str | None, by: str) -> dict[str, Any]:
        item = self.get(pid)
        if item is None:
            raise ValueError("No such proposal")
        if item["status"] != "pending":
            raise ValueError(f"That proposal is already {item['status']}")
        item.update(status=outcome, reason=(reason or "").strip()[:400] or None, resolved_at=time.time(), resolved_by=by)
        await self._save()
        return item

    async def async_apply(self, pid: str, by: str, colours: Any) -> dict[str, Any]:
        """Apply a pending proposal: keep the version it replaces, then change it, and mark it applied.

        Palettes and card picks are changed here. A dashboard's icon or layout is changed by Claude (on the dashboard itself) while checking the
        proposal, and this records it as applied with what it replaced, so it can be restored.
        """
        item = self.get(pid)
        if item is None:
            raise ValueError("No such proposal")
        if item["status"] != "pending":
            raise ValueError(f"That proposal is already {item['status']}")
        kind = item["kind"]
        versions = self.versions.setdefault(kind, [])
        if kind == "palette":
            before = {k: dict(v) for k, v in colours.overrides.items()}
            revision = colours.revision
            await colours.async_set(apply_palette_change(before, item["change"]))
            versions.append({"revision": revision, "at": time.time(), "by": by, "proposal": pid, "overrides": before})
        else:
            versions.append({"revision": self._next_revision(kind), "at": time.time(), "by": by, "proposal": pid, "data": version_data_for(kind, item, self.cards)})
            if kind == "cards":
                for cid, picks in item["change"]["cards"].items():
                    self.cards[cid] = {**self.cards.get(cid, {}), **picks}
        del versions[:-MAX_VERSIONS]
        return await self.async_resolve(pid, "applied", None, by)
