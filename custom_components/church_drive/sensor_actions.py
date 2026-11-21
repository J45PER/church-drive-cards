"""Sensor actions: what lights do when a motion or presence sensor sees someone and when the room empties (rules in sensor_logic.py).

Watches each sensor that has an action. Seeing someone does the "someone's there" action (if it is dark enough and in the time window it
was limited to); when the sensor has seen nobody for the delay, the "room is empty" action runs. A sensor going unavailable is not
treated as the room emptying, and Home Assistant restarting doesn't switch anything.

Kept in .storage/church_drive.sensor_actions, looked after in the app's Lights page (websocket church_drive/sensor_actions,
sensor_actions/set and sensor_actions/delete). `candidates()` lists the sensors the house has, with the readings and settings of the
device each one belongs to.
"""

from __future__ import annotations

import asyncio
import logging
import time
from typing import Any, Callable

from homeassistant.core import Event, HomeAssistant, callback
from homeassistant.helpers import device_registry as dr, entity_registry as er
from homeassistant.helpers.event import async_call_later, async_track_state_change_event
from homeassistant.helpers.start import async_at_started
from homeassistant.helpers.storage import Store
from homeassistant.util import dt as dt_util

from .sensor_logic import MAX_SENSORS, clean_sensor, clean_sensors, pick_look, should_act_on_presence, summary

_LOGGER = logging.getLogger(__name__)

STORE_VERSION = 1
EVENT_CHANGED = "church_drive_sensor_actions_changed"
CALL_TIMEOUT = 30
SENSOR_CLASSES = {"motion", "occupancy", "presence"}
# Devices that are rooms, zones and bridges rather than sensors, and platforms whose motion is a camera's or the alarm's.
NOT_SENSORS_MODELS = {"Service Group", "Room", "Zone", "Hue Bridge", "Bridge"}
NOT_SENSORS_PLATFORMS = {"ring", "church_drive", "template", "ibeacon"}
NOT_SENSORS_WORDS = ("keypad", "camera", "doorbell", "driveway")
# A device that is a camera or doorbell (it has a camera entity, or is made by Ring) is not a sensor, whatever platform its motion comes through.
NOT_SENSORS_MAKERS = {"Ring"}


class SensorActions:
    """Runs the sensor actions and keeps them across restarts."""

    def __init__(self, hass: HomeAssistant) -> None:
        self.hass = hass
        self._store: Store = Store(hass, STORE_VERSION, "church_drive.sensor_actions")
        self._items: dict[str, dict[str, Any]] = {}
        self._unsub: Callable[[], None] | None = None
        self._timers: dict[str, Callable[[], None]] = {}
        self._due: dict[str, float] = {}
        # Which sensor actions found their light already on when someone came in (kept until the room empties).
        self._was_on: dict[str, bool] = {}
        self._migrated = False
        self._unsub_started: Callable[[], None] | None = None

    async def async_start(self) -> None:
        data = await self._store.async_load() or {}
        self._items = clean_sensors(data.get("sensors"))
        self._migrated = bool(data.get("migrated"))
        self._watch()
        if not self._migrated:
            self._unsub_started = async_at_started(self.hass, self._async_migrate)

    async def _async_migrate(self, _hass: HomeAssistant) -> None:
        """The Cloakroom's own automation (made before this existed) becomes its sensor action, and the automation is switched off so both don't run."""
        old = "automation.cloakroom_light_follows_presence"
        sensor, light, delay = "binary_sensor.cloakroom_presence", "light.cloakroom", "input_number.cloakroom_light_off_delay"
        try:
            if self.hass.states.get(old) is not None and self.hass.states.get(sensor) is not None and self.hass.states.get(light) is not None \
                    and not any(i["sensor"] == sensor for i in self._items.values()):
                state = self.hass.states.get(delay)
                try:
                    seconds = int(float(state.state)) if state is not None else 60
                except ValueError:
                    seconds = 60
                await self.async_set(None, {"sensor": sensor, "name": "Cloakroom", "lights": [light], "off_delay": seconds, "lux_sensor": ""})
                await self.hass.services.async_call("automation", "turn_off", {"entity_id": old}, blocking=True)
                _LOGGER.info("Sensor actions: moved the Cloakroom light automation into its sensor action")
        except Exception:  # noqa: BLE001 - a failed move is tried again next start, and must not stop anything else
            _LOGGER.exception("Sensor actions: couldn't move the Cloakroom automation across")
            return
        self._migrated = True
        await self._async_save()

    @callback
    def async_stop(self) -> None:
        if self._unsub_started:
            self._unsub_started()
            self._unsub_started = None
        if self._unsub:
            self._unsub()
            self._unsub = None
        for cancel in self._timers.values():
            cancel()
        self._timers.clear()

    async def _async_save(self) -> None:
        await self._store.async_save({"sensors": list(self._items.values()), "migrated": self._migrated})

    def _watch(self) -> None:
        if self._unsub:
            self._unsub()
            self._unsub = None
        sensors = sorted({i["sensor"] for i in self._items.values()})
        if sensors:
            self._unsub = async_track_state_change_event(self.hass, sensors, self._on_change)

    def presence(self) -> dict[str, dict[str, Any]]:
        """For each light a sensor action looks after: whether someone is there, and, once the room has emptied, how many seconds until the light goes off."""
        out: dict[str, dict[str, Any]] = {}
        now = time.monotonic()
        for item in self._items.values():
            if not item["enabled"]:
                continue
            state = self.hass.states.get(item["sensor"])
            due = self._due.get(item["id"])
            info = {
                "present": state is not None and state.state == "on",
                "off_in": max(0, round(due - now)) if due is not None else None,
                "delay": item["off_delay"] if item["off_action"] != "none" else 0,
                "sensor": item["name"],
            }
            for light in item["lights"]:
                out[light] = info
        return out

    # ---- what's set ----------------------------------------------------------

    def _view(self, item: dict[str, Any]) -> dict[str, Any]:
        return {**item, "summary": summary(item)}

    def all(self) -> list[dict[str, Any]]:
        return sorted((self._view(i) for i in self._items.values()), key=lambda v: v["name"].lower())

    async def async_set(self, sid: str | None, change: dict[str, Any]) -> dict[str, Any]:
        old = self._items.get(sid) if sid else None
        if sid and old is None:
            raise ValueError("No such sensor action")
        if old is None:
            if len(self._items) >= MAX_SENSORS:
                raise ValueError("There are as many sensor actions as can be kept: remove one first")
            if any(i["sensor"] == change.get("sensor") for i in self._items.values()):
                raise ValueError("That sensor already has an action: change it instead")
        item = clean_sensor(change, old)
        if self.hass.states.get(item["sensor"]) is None:
            raise ValueError("I can't find that sensor")
        for light in item["lights"]:
            if self.hass.states.get(light) is None:
                raise ValueError(f"I can't find the light {light}")
        self._items[item["id"]] = item
        self._cancel(item["id"])
        await self._async_save()
        self._watch()
        self.hass.bus.async_fire(EVENT_CHANGED, {"id": item["id"]})
        return self._view(item)

    async def async_remove(self, sid: str) -> bool:
        if self._items.pop(sid, None) is None:
            return False
        self._cancel(sid)
        await self._async_save()
        self._watch()
        self.hass.bus.async_fire(EVENT_CHANGED, {"id": sid})
        return True

    # ---- running ---------------------------------------------------------------

    def _cancel(self, sid: str) -> None:
        self._due.pop(sid, None)
        cancel = self._timers.pop(sid, None)
        if cancel:
            cancel()

    @callback
    def _on_change(self, event: Event) -> None:
        new = event.data.get("new_state")
        if new is None or new.state in ("unavailable", "unknown"):
            return
        sensor = event.data["entity_id"]
        for item in list(self._items.values()):
            if item["sensor"] != sensor or not item["enabled"]:
                continue
            if new.state == "on":
                self._cancel(item["id"])
                self._was_on.setdefault(item["id"], self._any_on(item))
                self.hass.async_create_task(self._async_present(item))
            elif new.state == "off":
                self._cancel(item["id"])
                if item["off_action"] == "none":
                    self._was_on.pop(item["id"], None)
                else:
                    sid = item["id"]
                    # A @callback, so Home Assistant runs it on the event loop: a plain lambda runs on a worker thread, where creating the off task is refused
                    # and the light would turn on but never off.
                    self._due[sid] = time.monotonic() + item["off_delay"]
                    self._timers[sid] = async_call_later(self.hass, item["off_delay"], callback(lambda _now, sid=sid: self._on_empty(sid)))

    def _any_on(self, item: dict[str, Any]) -> bool:
        states = (self.hass.states.get(light) for light in item["lights"])
        return any(state is not None and state.state == "on" for state in states)

    @callback
    def _on_empty(self, sid: str) -> None:
        self._timers.pop(sid, None)
        self._due.pop(sid, None)
        item = self._items.get(sid)
        state = self.hass.states.get(item["sensor"]) if item else None
        if item and item["enabled"] and state is not None and state.state == "off":
            self.hass.async_create_task(self._async_empty(item))

    def _lux(self, item: dict[str, Any]) -> float | None:
        state = self.hass.states.get(item["lux_sensor"]) if item["lux_sensor"] else None
        try:
            return float(state.state) if state is not None else None
        except (TypeError, ValueError):
            return None

    def _sun_down(self) -> bool:
        sun = self.hass.states.get("sun.sun")
        return sun is not None and sun.state == "below_horizon"

    async def _async_present(self, item: dict[str, Any]) -> None:
        now = dt_util.now()
        minutes = now.hour * 60 + now.minute
        if not should_act_on_presence(item, minutes, self._lux(item), self._sun_down()):
            return
        look = pick_look(item, minutes)
        for light in item["lights"]:
            if look["on_action"] == "hybrid":
                await self._call("church_drive", "apply_hybrid", {"id": look["scene"]}, item)
                return
            if look["on_action"] == "scene":
                await self._call("church_drive", "apply_scene", {"entity_id": light, "scene": look["scene"]}, item)
            else:
                data: dict[str, Any] = {"entity_id": light}
                if look["pct"]:
                    data["brightness_pct"] = look["pct"]
                await self._call("light", "turn_on", data, item)

    async def _async_empty(self, item: dict[str, Any]) -> None:
        # A light that was already on when someone came in is left on, unless this action is set to switch it off anyway.
        if self._was_on.pop(item["id"], False) and not item["off_if_on"]:
            return
        for light in item["lights"]:
            if item["off_action"] == "dim":
                await self._call("light", "turn_on", {"entity_id": light, "brightness_pct": item["dim_pct"]}, item)
            else:
                await self._call("light", "turn_off", {"entity_id": light}, item)

    async def _call(self, domain: str, service: str, data: dict[str, Any], item: dict[str, Any]) -> None:
        try:
            async with asyncio.timeout(CALL_TIMEOUT):
                await self.hass.services.async_call(domain, service, data, blocking=True)
        except Exception as err:  # noqa: BLE001 - one light that doesn't answer must not stop the rest
            _LOGGER.warning("Sensor action %s: %s.%s for %s failed: %s", item["name"], domain, service, data.get("entity_id"), err)

    # ---- the house's sensors -----------------------------------------------------

    def candidates(self) -> list[dict[str, Any]]:
        """Each motion or presence sensor in the house: its device, readings and the settings the device has."""
        ents = er.async_get(self.hass)
        devs = dr.async_get(self.hass)
        by_device: dict[str, list[er.RegistryEntry]] = {}
        for entry in ents.entities.values():
            if entry.device_id:
                by_device.setdefault(entry.device_id, []).append(entry)
        taken = {i["sensor"]: i["id"] for i in self._items.values()}
        out: list[dict[str, Any]] = []
        seen: set[str] = set()
        for entry in ents.entities.values():
            if entry.domain != "binary_sensor" or entry.disabled_by or entry.hidden_by or not entry.device_id:
                continue
            state = self.hass.states.get(entry.entity_id)
            device_class = entry.device_class or entry.original_device_class or (state.attributes.get("device_class") if state else None)
            if device_class not in SENSOR_CLASSES or entry.platform in NOT_SENSORS_PLATFORMS:
                continue
            if any(w in entry.entity_id for w in NOT_SENSORS_WORDS):
                continue
            device = devs.async_get(entry.device_id)
            if device is None or device.model in NOT_SENSORS_MODELS or device.manufacturer in NOT_SENSORS_MAKERS:
                continue
            if any(e.domain == "camera" for e in by_device.get(entry.device_id, [])) or entry.device_id in seen:
                continue
            seen.add(entry.device_id)
            readings: dict[str, Any] = {}
            settings: list[dict[str, Any]] = []
            for other in by_device.get(entry.device_id, []):
                s = self.hass.states.get(other.entity_id)
                if s is None or other.disabled_by:
                    continue
                cls = other.device_class or other.original_device_class
                if other.domain == "sensor" and cls in ("battery", "illuminance", "temperature", "humidity"):
                    readings[cls] = {"entity_id": other.entity_id, "state": s.state, "unit": s.attributes.get("unit_of_measurement")}
                elif other.domain == "number" and other.entity_category != er.EntityCategory.DIAGNOSTIC:
                    settings.append({
                        "entity_id": other.entity_id, "kind": "number", "name": other.name or other.original_name or other.entity_id,
                        "state": s.state, "min": s.attributes.get("min"), "max": s.attributes.get("max"), "step": s.attributes.get("step"),
                        "unit": s.attributes.get("unit_of_measurement"),
                    })
                elif other.domain == "switch" and other.platform == entry.platform and other.entity_category != er.EntityCategory.DIAGNOSTIC:
                    settings.append({"entity_id": other.entity_id, "kind": "switch", "name": other.name or other.original_name or other.entity_id, "state": s.state})
                elif other.domain == "select" and other.entity_category != er.EntityCategory.DIAGNOSTIC:
                    settings.append({
                        "entity_id": other.entity_id, "kind": "select", "name": other.name or other.original_name or other.entity_id,
                        "state": s.state, "options": s.attributes.get("options") or [],
                    })
            out.append({
                "entity_id": entry.entity_id,
                "name": device.name_by_user or device.name or entry.original_name or entry.entity_id,
                "model": device.model,
                "platform": entry.platform,
                "area": device.area_id or entry.area_id,
                "state": state.state if state else "unavailable",
                "readings": readings,
                "settings": settings,
                "action": taken.get(entry.entity_id),
            })
        return sorted(out, key=lambda c: c["name"].lower())
