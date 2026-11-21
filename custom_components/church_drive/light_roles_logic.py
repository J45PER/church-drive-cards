"""The plain rules behind light roles (no Home Assistant in here, so they can be tested alone).

Each light can be given a role in themes: Primary (the main light of a room, and what a light with no role counts as), Ambient (a ring,
strip or lamp that sets the mood) or Tertiary (niche cases, such as the other lights of a Hue Centris fitting). A theme gives each role its own
look, so one theme works in any room.
"""

from __future__ import annotations

from typing import Any

ROLES = ("primary", "ambient", "tertiary")
DEFAULT_ROLE = "primary"
MAX_LIGHTS = 400


def clean_roles(value: Any) -> dict[str, str]:
    """The stored or sent map of light to role, each entry valid; a light set to Primary is the default, so it isn't kept."""
    out: dict[str, str] = {}
    for entity, role in (value.items() if isinstance(value, dict) else []):
        if isinstance(entity, str) and entity.startswith("light.") and role in ROLES and role != DEFAULT_ROLE:
            out[entity] = role
    return dict(list(out.items())[:MAX_LIGHTS])


def role_of(roles: dict[str, str], entity: str) -> str:
    return roles.get(entity, DEFAULT_ROLE)
