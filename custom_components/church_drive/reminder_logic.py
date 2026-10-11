"""The plain rules behind place reminders and snoozing (no Home Assistant in here, so they can be tested alone).

A place reminder is kept for one task (a to-do list and the item's uid): the zone to be reminded at, and who. When
someone arrives in that zone, they're reminded on their phones with two buttons: Done and Snooze. The same buttons
come with a time reminder. The phone sends the button pressed back as an event carrying an *action id*, which is
built and read here.
"""

from __future__ import annotations

SNOOZE_MINUTES = (5, 10, 20, 30, 60)
SEP = "|"


def action_id(kind: str, todo: str, uid: str, first: str, zone: str = "") -> str:
    """What a notification button carries: kind (done/snooze), the list, the item, who it is for and, for a place reminder, its zone."""
    return SEP.join(["CD", kind, todo, uid, first, zone])


def parse_action_id(value: str) -> dict[str, str] | None:
    """The reverse of action_id, or None if it isn't one of ours."""
    parts = str(value or "").split(SEP)
    if len(parts) != 6 or parts[0] != "CD" or parts[1] not in ("done", "snooze") or not parts[2].startswith("todo."):
        return None
    return {"kind": parts[1], "todo": parts[2], "uid": parts[3], "first": parts[4], "zone": parts[5]}


def actions_for(todo: str, uid: str, first: str, zone: str = "") -> list[dict[str, str]]:
    """The buttons on a reminder. Snooze opens the app to choose how long (five lengths don't fit as buttons)."""
    return [
        {"action": action_id("done", todo, uid, first, zone), "title": "Done"},
        {"action": action_id("snooze", todo, uid, first, zone), "title": "Snooze…"},
    ]


def snooze_minutes(value: object) -> int | None:
    """The snooze length if it's one of the allowed ones, else None."""
    try:
        minutes = int(value)  # type: ignore[call-overload]
    except (TypeError, ValueError):
        return None
    return minutes if minutes in SNOOZE_MINUTES else None


def zone_name(zone_entity: str, zone_friendly_name: str | None) -> str:
    """The text a person's state shows while they're in that zone ("home" for the home zone)."""
    return "home" if zone_entity == "zone.home" else (zone_friendly_name or "")


def arrived_in(state: str, zone_entity: str, zone_friendly_name: str | None) -> bool:
    """Whether a person whose state is [state] is in that zone."""
    name = zone_name(zone_entity, zone_friendly_name)
    return bool(name) and state.strip().lower() == name.strip().lower()


def recipients(who: list[str], owner: str | None, everyone: list[str]) -> list[str]:
    """Who a reminder is for: the people named, else the list's owner, else everyone."""
    named = [w for w in dict.fromkeys(str(w).strip() for w in who) if w]
    if "everyone" in [n.lower() for n in named]:
        return list(everyone)
    return named or ([owner] if owner else list(everyone))


def key(todo: str, uid: str) -> str:
    return f"{todo}{SEP}{uid}"
