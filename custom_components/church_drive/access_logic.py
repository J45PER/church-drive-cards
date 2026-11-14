"""Who can see which device: the rules only (people.py reads the registries and the store).

A device is hidden from the people it is not assigned to (hiding, not a lock). Administrators see every device. A TV or speaker (a media player) that
nobody has been assigned to is for administrators only, so a new one appears for no one until it is assigned; any other device is everyone's until it
is limited to some people.
"""

from __future__ import annotations

from typing import Iterable

ADMIN_ONLY_DOMAINS = ("media_player",)


def visible_map(assigned: dict[str, list[str]], entities: Iterable[str], admins: Iterable[str]) -> dict[str, list[str]]:
    """{entity id: [person entity ids who can see it]} for the devices that are not everyone's.

    [assigned] is who each entity was given to (a limited device's entities all take its people); [entities] every entity the house has (so unassigned
    media players are found); [admins] the administrators' person entities, who are in every list. An entity that is everyone's is left out.
    """
    admin_list = sorted(set(admins))
    out: dict[str, list[str]] = {}
    for entity, people in assigned.items():
        if people:
            out[entity] = sorted(set(people) | set(admin_list))
    for entity in entities:
        if entity.partition(".")[0] in ADMIN_ONLY_DOMAINS and entity not in out:
            out[entity] = list(admin_list)
    return out


def can_use(access: dict[str, list[str]], entity: str, person: str | None) -> bool:
    """Whether [person] (a person entity, or None when nobody is known) may use [entity]: everyone for an entity not in the map, else those listed."""
    who = access.get(entity)
    return who is None or (person is not None and person in who)


# What Clive may ask of a TV or speaker, as the media_player service and its data.
MEDIA_ACTIONS = {
    "play": ("media_play", {}), "pause": ("media_pause", {}), "play_pause": ("media_play_pause", {}), "stop": ("media_stop", {}),
    "next": ("media_next_track", {}), "previous": ("media_previous_track", {}), "turn_on": ("turn_on", {}), "turn_off": ("turn_off", {}),
    "volume_up": ("volume_up", {}), "volume_down": ("volume_down", {}), "mute": ("volume_mute", {"is_volume_muted": True}), "unmute": ("volume_mute", {"is_volume_muted": False}),
}


def media_call(action: str, value: object = None) -> tuple[str, dict]:
    """The media_player service and data for one of Clive's actions ([value] is a volume from 0 to 100 for volume_set, a source for select_source). Raises ValueError."""
    if action in MEDIA_ACTIONS:
        service, data = MEDIA_ACTIONS[action]
        return service, dict(data)
    if action == "volume_set":
        try:
            level = float(value)  # type: ignore[arg-type]
        except (TypeError, ValueError) as err:
            raise ValueError("A volume is a number from 0 to 100") from err
        return "volume_set", {"volume_level": round(max(0.0, min(100.0, level)) / 100, 2)}
    if action == "select_source":
        if not str(value or "").strip():
            raise ValueError("Say which source")
        return "select_source", {"source": str(value).strip()}
    raise ValueError(f"Unknown action {action}")


def visible_media(access: dict[str, list[str]], players: list[dict], person: str | None, is_admin: bool) -> list[dict]:
    """The media players [person] may see (an administrator, or nobody behind the call, sees all): each {entity_id, name, state, app, title}."""
    return [p for p in players if is_admin or can_use(access, p["entity_id"], person)]
