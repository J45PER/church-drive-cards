"""Which role each light has in themes (the rules are in light_roles_logic.py), kept in .storage/church_drive.light_roles for everyone."""

from __future__ import annotations

from homeassistant.core import HomeAssistant
from homeassistant.helpers.storage import Store

from .light_roles_logic import DEFAULT_ROLE, ROLES, clean_roles, role_of

STORE_VERSION = 1
EVENT_CHANGED = "church_drive_hybrids_changed"


class LightRoles:
    def __init__(self, hass: HomeAssistant) -> None:
        self.hass = hass
        self._store: Store = Store(hass, STORE_VERSION, "church_drive.light_roles")
        self._roles: dict[str, str] = {}

    async def async_load(self) -> None:
        data = await self._store.async_load() or {}
        self._roles = clean_roles(data.get("roles"))

    def all(self) -> dict[str, str]:
        return dict(self._roles)

    def role(self, entity: str) -> str:
        return role_of(self._roles, entity)

    async def async_set(self, entity: str, role: str) -> None:
        if not entity.startswith("light.") or self.hass.states.get(entity) is None:
            raise ValueError("I can't find that light")
        if role not in ROLES:
            raise ValueError("A role is Primary, Ambient or Tertiary")
        if role == DEFAULT_ROLE:
            self._roles.pop(entity, None)
        else:
            self._roles[entity] = role
        await self._store.async_save({"roles": self._roles})
        self.hass.bus.async_fire(EVENT_CHANGED, {})
