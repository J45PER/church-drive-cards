"""Clive's chats: what was asked, what he answered, and how a chat is marked (the rules only; storage and the Home Assistant hook are in chats.py).

A chat is one turn: a person's words and his answer. It carries the abilities he used (the tools he called) and the card he showed, if any,
and an administrator can mark it good or bad with a note. Kept for 30 days, administrators only.
"""

from __future__ import annotations

from datetime import datetime
from typing import Any

RETENTION_S = 30 * 86400
MAX_ENTRIES = 2000
MAX_TEXT = 1200
MAX_NOTE = 400
RATINGS = ("good", "bad")
MAX_LIMIT = 200
CARD_TOOLS = ("clive_show_card", "clive_show", "show_card")


def _ts(value: Any) -> float | None:
    """A time as seconds: from a number, a datetime or an ISO string."""
    if isinstance(value, (int, float)):
        return float(value)
    if isinstance(value, datetime):
        return value.timestamp()
    if isinstance(value, str):
        try:
            return datetime.fromisoformat(value.replace("Z", "+00:00")).timestamp()
        except ValueError:
            return None
    return None


def _text(value: Any) -> str:
    return " ".join(str(value or "").split())[:MAX_TEXT]


def ability_name(tool: str) -> str:
    """A tool's name as people read it: clive_zappi_charge -> Zappi charge."""
    name = str(tool or "")
    for prefix in ("script.", "clive_"):
        if name.startswith(prefix):
            name = name[len(prefix):]
    return name.replace("_", " ").strip().capitalize() or "Ability"


def turns_from_chat_log(chat_log: Any) -> list[dict[str, Any]]:
    """The turns of one conversation, from Home Assistant's chat log (a dict with `conversation_id` and a `content` list).

    A turn starts at a user line and takes the assistant lines and tool calls up to the next user line. A turn with no answer yet is kept
    (answer ""), so it fills in as the chat log is updated.
    """
    if not isinstance(chat_log, dict):
        return []
    conversation = str(chat_log.get("conversation_id") or "")
    turns: list[dict[str, Any]] = []
    for line in chat_log.get("content") or []:
        if not isinstance(line, dict):
            continue
        role = line.get("role")
        at = _ts(line.get("created"))
        if role == "user":
            turns.append({"conversation": conversation, "ts": at or 0.0, "asked": _text(line.get("content")), "answer": "", "answered": None, "abilities": [], "card": None})
        elif turns and role == "assistant":
            turn = turns[-1]
            if line.get("content"):
                turn["answer"] = _text(line["content"])
                turn["answered"] = at
            for call in line.get("tool_calls") or []:
                tool = str((call or {}).get("tool_name") or "")
                if not tool:
                    continue
                label = ability_name(tool)
                if label not in turn["abilities"]:
                    turn["abilities"].append(label)
                if tool in CARD_TOOLS and turn["card"] is None:
                    args = (call or {}).get("tool_args") or {}
                    turn["card"] = str(args.get("kind") or args.get("type") or "card")[:40]
    out = []
    for turn in turns:
        if not turn["asked"] or not turn["ts"]:
            continue
        turn["id"] = f"{conversation}:{int(turn['ts'])}"
        turn["seconds"] = round(turn["answered"] - turn["ts"], 1) if turn["answered"] and turn["answered"] >= turn["ts"] else None
        out.append(turn)
    return out


def make_entry(turn: dict[str, Any], who: str | None = None, device: str | None = None) -> dict[str, Any]:
    return {
        "id": turn["id"], "ts": turn["ts"], "conversation": turn["conversation"], "who": who or None, "device": device or None,
        "asked": turn["asked"], "answer": turn["answer"], "abilities": list(turn["abilities"])[:12], "card": turn["card"], "seconds": turn["seconds"],
        "rating": None, "note": "", "sent": False,
    }


def upsert(entries: list[dict[str, Any]], turn: dict[str, Any], who: str | None = None, device: str | None = None) -> bool:
    """Add a turn, or fill in an earlier one (its answer, abilities, card and time) keeping its mark and note. True when something changed."""
    existing = next((e for e in entries if e["id"] == turn["id"]), None)
    if existing is None:
        entries.append(make_entry(turn, who, device))
        return True
    before = (existing["answer"], existing["abilities"], existing["card"], existing["seconds"])
    existing["answer"], existing["abilities"], existing["card"], existing["seconds"] = turn["answer"], list(turn["abilities"])[:12], turn["card"], turn["seconds"]
    if who and not existing.get("who"):
        existing["who"] = who
    if device and not existing.get("device"):
        existing["device"] = device
    return before != (existing["answer"], existing["abilities"], existing["card"], existing["seconds"])


def prune(entries: list[dict[str, Any]], now: float) -> list[dict[str, Any]]:
    """Newest MAX_ENTRIES of the last 30 days; a chat that has been marked is kept its full time too (the notes are what matters)."""
    fresh = [e for e in entries if now - float(e.get("ts") or 0) <= RETENTION_S]
    fresh.sort(key=lambda e: e["ts"])
    return fresh[-MAX_ENTRIES:]


def clean(raw: Any) -> list[dict[str, Any]]:
    """Saved chats read back safely."""
    out = []
    for e in raw if isinstance(raw, list) else []:
        if not isinstance(e, dict) or not e.get("id") or not isinstance(e.get("ts"), (int, float)):
            continue
        out.append({
            "id": str(e["id"]), "ts": float(e["ts"]), "conversation": str(e.get("conversation") or ""), "who": e.get("who") or None, "device": e.get("device") or None,
            "asked": _text(e.get("asked")), "answer": _text(e.get("answer")), "abilities": [str(a)[:60] for a in (e.get("abilities") or [])][:12],
            "card": e.get("card") or None, "seconds": e.get("seconds") if isinstance(e.get("seconds"), (int, float)) else None,
            "rating": e.get("rating") if e.get("rating") in RATINGS else None, "note": str(e.get("note") or "")[:MAX_NOTE], "sent": bool(e.get("sent")),
        })
    return out


def rate(entries: list[dict[str, Any]], chat_id: str, rating: Any, note: Any = None) -> dict[str, Any]:
    """Mark a chat: rating good, bad or None (cleared), and a note. Raises ValueError for an unknown chat or rating."""
    entry = next((e for e in entries if e["id"] == chat_id), None)
    if entry is None:
        raise ValueError("That chat is not kept any more")
    if rating not in (*RATINGS, None, ""):
        raise ValueError("A chat is good or bad")
    entry["rating"] = rating or None
    if note is not None:
        entry["note"] = str(note).strip()[:MAX_NOTE]
    entry["sent"] = False   # changed since it was last sent
    return entry


def to_send(entry: dict[str, Any]) -> bool:
    """A chat worth sending to Clive: marked bad or with a note, and not sent since it last changed."""
    return not entry.get("sent") and (entry.get("rating") == "bad" or bool(entry.get("note")))


def mark_sent(entries: list[dict[str, Any]], ids: list[str]) -> int:
    """Remember these chats were sent for checking; gives how many were marked."""
    n = 0
    for e in entries:
        if e["id"] in ids and not e.get("sent"):
            e["sent"] = True
            n += 1
    return n


def select(entries: list[dict[str, Any]], since: float | None = None, until: float | None = None, mark: str = "all", limit: int = 50, offset: int = 0) -> dict[str, Any]:
    """Newest first, filtered by time and by mark (all, unmarked, good, bad, noted, tosend), with the total and a count of each mark."""
    rows = [e for e in entries if (since is None or e["ts"] >= since) and (until is None or e["ts"] < until)]
    counts = {"all": len(rows), "good": sum(e["rating"] == "good" for e in rows), "bad": sum(e["rating"] == "bad" for e in rows),
              "unmarked": sum(not e["rating"] and not e["note"] for e in rows), "noted": sum(bool(e["note"]) for e in rows), "to_send": sum(to_send(e) for e in rows)}
    if mark == "unmarked":
        rows = [e for e in rows if not e["rating"] and not e["note"]]
    elif mark in RATINGS:
        rows = [e for e in rows if e["rating"] == mark]
    elif mark == "noted":
        rows = [e for e in rows if e["note"]]
    elif mark == "tosend":
        rows = [e for e in rows if to_send(e)]
    rows.sort(key=lambda e: e["ts"], reverse=True)
    limit = max(1, min(int(limit), MAX_LIMIT))
    return {"entries": rows[max(0, offset): max(0, offset) + limit], "total": len(rows), "counts": counts}


def normalise_notes_change(change: Any) -> dict[str, Any]:
    """The chat notes of a proposal made safe: {notes: [{id, rating, note, asked, answer}]}. Raises ValueError saying what is wrong."""
    notes = change.get("notes") if isinstance(change, dict) else None
    if not isinstance(notes, list) or not notes:
        raise ValueError("There are no chat notes to send")
    out = []
    for n in notes[:60]:
        if not isinstance(n, dict) or not n.get("id"):
            raise ValueError("A chat note needs the chat it is about")
        rating = n.get("rating") if n.get("rating") in RATINGS else None
        text = str(n.get("note") or "").strip()[:MAX_NOTE]
        if not rating and not text:
            raise ValueError("A chat note needs a mark or some words")
        out.append({"id": str(n["id"])[:80], "rating": rating, "note": text, "asked": _text(n.get("asked"))[:300], "answer": _text(n.get("answer"))[:500]})
    return {"notes": out}
