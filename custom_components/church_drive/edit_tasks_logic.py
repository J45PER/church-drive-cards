"""The plain rules for the edit tasks (no Home Assistant in here, so they can be tested alone)."""

from __future__ import annotations

import re
from typing import Any

MARKER = "Church Drive edit "
_KINDS = {"palette": "Colours", "cards": "Card colours", "icons": "Icons", "layouts": "Layout", "layout": "Layout", "chats": "Chat notes"}


def kind_words(kind: Any) -> str:
    text = str(kind or "").strip()
    return _KINDS.get(text, text.replace("_", " ").capitalize() or "An")


def task_summary(proposal: dict[str, Any]) -> str:
    """"Colours edit waiting", the words the apps showed before they were tasks."""
    return f"{kind_words(proposal.get('kind'))} edit waiting"


def task_description(proposal_id: str) -> str:
    return f"{MARKER}{proposal_id}"


def marker_of(description: Any) -> str | None:
    """The proposal a task stands for, from its description, or None for any other task."""
    found = re.search(re.escape(MARKER) + r"([A-Za-z0-9_-]+)", str(description or ""))
    return found.group(1) if found else None
