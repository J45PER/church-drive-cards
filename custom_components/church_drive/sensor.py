"""Device health sensor: how many watched devices need attention.

Its `devices` attribute holds each watched entity's status for the cards
(the Device Health card and the "not responding" banner on device cards).
"""

from __future__ import annotations

from typing import Any

from homeassistant.components.sensor import SensorEntity
from homeassistant.config_entries import ConfigEntry
from homeassistant.core import HomeAssistant, callback
from homeassistant.helpers.dispatcher import async_dispatcher_connect
from homeassistant.helpers.entity_platform import AddEntitiesCallback

from .const import DOMAIN, SIGNAL_HEALTH


async def async_setup_entry(
    hass: HomeAssistant, entry: ConfigEntry, async_add_entities: AddEntitiesCallback
) -> None:
    if hass.data[DOMAIN].get("health") is not None:
        async_add_entities([DeviceHealthSensor(hass)])


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
