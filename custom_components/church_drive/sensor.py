"""Church Drive sensors.

- Device health: how many watched devices need attention. Its `devices`
  attribute holds each watched entity's status for the cards (the Device
  Health card and the "not responding" banner on device cards).
- People: how many people live here. Its attributes say who they are and who
  gets each kind of notification, for automations and templates:
  `assign` ({kind: [first names]}), `everyone` ({kind: true when set to
  everyone}) and `people` (name, first, entity_id, admin, home, place, zone,
  places, list): `place` is where they are in their own words (Home, Work,
  a zone's name, Away), `zone` the zone's name when it's not Home.
"""

from __future__ import annotations

from typing import Any

from homeassistant.components.sensor import SensorEntity
from homeassistant.config_entries import ConfigEntry
from homeassistant.core import HomeAssistant, callback
from homeassistant.helpers.dispatcher import async_dispatcher_connect
from homeassistant.helpers.entity_platform import AddEntitiesCallback

from .const import DOMAIN, SIGNAL_HEALTH
from .people import SIGNAL_PEOPLE


async def async_setup_entry(
    hass: HomeAssistant, entry: ConfigEntry, async_add_entities: AddEntitiesCallback
) -> None:
    entities: list[SensorEntity] = []
    if hass.data[DOMAIN].get("health") is not None:
        entities.append(DeviceHealthSensor(hass))
    if hass.data[DOMAIN].get("people") is not None:
        entities.append(PeopleSensor(hass))
    async_add_entities(entities)


class DeviceHealthSensor(SensorEntity):
    """Number of watched devices that look stale; details as attributes."""

    _attr_should_poll = False
    _attr_icon = "mdi:heart-pulse"
    _attr_name = "Church Drive device health"
    _attr_unique_id = f"{DOMAIN}_device_health"
    _attr_native_unit_of_measurement = "devices"

    def __init__(self, hass: HomeAssistant) -> None:
        self._health = hass.data[DOMAIN]["health"]

    async def async_added_to_hass(self) -> None:
        self.async_on_remove(async_dispatcher_connect(self.hass, SIGNAL_HEALTH, self._update))

    @callback
    def _update(self) -> None:
        self.async_write_ha_state()

    @property
    def native_value(self) -> int:
        return sum(1 for d in self._health.summary().values() if d["status"] != "ok")

    @property
    def extra_state_attributes(self) -> dict[str, Any]:
        return {"devices": self._health.summary()}


class PeopleSensor(SensorEntity):
    """The house's people and who gets each kind of notification."""

    _attr_should_poll = False
    _attr_icon = "mdi:account-group"
    _attr_name = "Church Drive people"
    _attr_unique_id = f"{DOMAIN}_people"
    _attr_native_unit_of_measurement = "people"

    def __init__(self, hass: HomeAssistant) -> None:
        self._people = hass.data[DOMAIN]["people"]

    async def async_added_to_hass(self) -> None:
        self.async_on_remove(async_dispatcher_connect(self.hass, SIGNAL_PEOPLE, self._update))

    @callback
    def _update(self) -> None:
        self.async_write_ha_state()

    @property
    def native_value(self) -> int:
        return len(self._people.people())

    @property
    def extra_state_attributes(self) -> dict[str, Any]:
        people = [
            {k: p[k] for k in ("name", "first", "entity_id", "admin", "home", "place", "zone", "places", "cars", "picture", "list")}
            for p in self._people.people()
        ]
        return {
            "people": people,
            "assign": self._people.assigned_names(),
            "everyone": self._people.everyone(),
            "access": self._people.access_entities(),
            "rev": self._people.rev,
        }
