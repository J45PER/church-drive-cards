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

from .const import DOMAIN
from .sensor_logic import LIT_HOLD_OFF_S, LIT_HOLD_ON_S, MAX_SENSORS, clean_sensor, clean_sensors, lux_for_decision, pick_look, should_act_on_presence, summary

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
        # Until when (monotonic seconds) the room counts as dark whatever its sensor reads: the light this action switched on raises that reading.
        self._dark_hold: dict[str, float] = {}
        # The scene that holds how an action's lights were before someone came in (action id -> scene entity), for the ones that go back to it.
        self._snap: dict[str, str] = {}
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
        sensors = sorted({s for i in self._items.values() for s in i["sensors"]})
        if sensors:
            self._unsub = async_track_state_change_event(self.hass, sensors, self._on_change)

    def presence(self) -> dict[str, dict[str, Any]]:
        """For each light a sensor action looks after: whether someone is there, and, once the room has emptied, how many seconds until the light goes off."""
        out: dict[str, dict[str, Any]] = {}
        now = time.monotonic()
        for item in self._items.values():
            if not item["enabled"]:
                continue
            due = self._due.get(item["id"])
            info = {
                "present": self._any_sensor_on(item),
                "off_in": max(0, round(due - now)) if due is not None else None,
                "delay": item["off_delay"] if item["off_action"] != "none" else 0,
                "sensor": item["name"],
                # A motion detector (the Hue ones) only sees movement, so its card says "Motion detected" where a presence sensor says "Someone's in".
                "motion": all((s := self.hass.states.get(e)) is not None and s.attributes.get("device_class") == "motion" for e in item["sensors"]),
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
            if any(change.get("sensor") in i["sensors"] for i in self._items.values()):
                raise ValueError("That sensor already has an action: change it instead")
        item = clean_sensor(change, old)
        for sensor in item["sensors"]:
            if self.hass.states.get(sensor) is None:
                raise ValueError("I can't find that sensor")
        if any(other["id"] != item["id"] and set(other["sensors"]) & set(item["sensors"]) for other in self._items.values()):
            raise ValueError("One of those sensors already has an action: change that one instead")
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
            if sensor not in item["sensors"] or not item["enabled"]:
                continue
            if new.state == "on":
                self._cancel(item["id"])
                self._was_on.setdefault(item["id"], self._any_on(item))
                self.hass.async_create_task(self._async_arrive(item))
            elif new.state == "off":
                if self._any_sensor_on(item):  # another sensor of the group still sees someone
                    continue
                self._cancel(item["id"])
                if item["off_action"] == "none":
                    self._was_on.pop(item["id"], None)
                else:
                    sid = item["id"]
                    # A @callback, so Home Assistant runs it on the event loop: a plain lambda runs on a worker thread, where creating the off task is refused
                    # and the light would turn on but never off.
                    self._due[sid] = time.monotonic() + item["off_delay"]
                    cancels = [async_call_later(self.hass, item["off_delay"], callback(lambda _now, sid=sid: self._on_empty(sid)))]
                    # Halfway through the countdown a light that is about to go off dims to half, as the warning.
                    if item["off_action"] == "off" and item["dim_warning"] and item["off_delay"] >= 2:
                        cancels.append(async_call_later(self.hass, item["off_delay"] / 2, callback(lambda _now, sid=sid: self._on_halfway(sid))))
                    self._timers[sid] = lambda cancels=cancels: [cancel() for cancel in cancels]

    def holding(self, light: str) -> bool:
        """Whether a sensor action is looking after this light right now: someone is there, or its countdown to put the light back is running."""
        for item in self._items.values():
            if item["enabled"] and light in item["lights"] and (self._any_sensor_on(item) or item["id"] in self._due):
                return True
        return False

    def _any_sensor_on(self, item: dict[str, Any]) -> bool:
        states = (self.hass.states.get(sensor) for sensor in item["sensors"])
        return any(state is not None and state.state == "on" for state in states)

    def _any_on(self, item: dict[str, Any]) -> bool:
        states = (self.hass.states.get(light) for light in item["lights"])
        return any(state is not None and state.state == "on" for state in states)

    @callback
    def _on_empty(self, sid: str) -> None:
        self._timers.pop(sid, None)
        self._due.pop(sid, None)
        item = self._items.get(sid)
        if item and item["enabled"] and not self._any_sensor_on(item):
            self.hass.async_create_task(self._async_empty(item))

    @callback
    def _on_halfway(self, sid: str) -> None:
        item = self._items.get(sid)
        # Only when the light really is going to be switched off at the end (one that was on already is left alone unless the action says otherwise).
        if item and item["enabled"] and not self._any_sensor_on(item) and (item["off_if_on"] or not self._was_on.get(sid, False)):
            self.hass.async_create_task(self._async_halve(item))

    async def _async_halve(self, item: dict[str, Any]) -> None:
        for light in item["lights"]:
            state = self.hass.states.get(light)
            brightness = state.attributes.get("brightness") if state is not None and state.state == "on" else None
            if not brightness:  # an on/off light has no brightness to halve
                continue
            await self._call("light", "turn_on", {"entity_id": light, "brightness_pct": max(1, round(brightness / 255 * 50))}, item)

    def _lux(self, item: dict[str, Any]) -> float | None:
        readings: list[float] = []
        for entity in item["lux_sensors"]:
            state = self.hass.states.get(entity)
            try:
                readings.append(float(state.state))
            except (AttributeError, TypeError, ValueError):
                continue
        return max(readings) if readings else None  # the brightest reading of a group

    def _lux_now(self, item: dict[str, Any]) -> float | None:
        """The light level to decide "is it dark" from, ignoring what the action's own light has done to the sensor lately."""
        return lux_for_decision(self._lux(item), time.monotonic() < self._dark_hold.get(item["id"], 0.0))

    def _sun_down(self) -> bool:
        sun = self.hass.states.get("sun.sun")
        return sun is not None and sun.state == "below_horizon"

    def _note(self, item: dict[str, Any], text: str, entity: str = "") -> None:
        """Say in the activity log what this sensor action did."""
        activity = self.hass.data.get(DOMAIN, {}).get("activity")
        if activity is not None:
            activity.note_sensor(f"{item['name']} sensor", text, entity)

    def _light_names(self, item: dict[str, Any]) -> str:
        names = [(s.name if (s := self.hass.states.get(light)) is not None else light) for light in item["lights"]]
        return ", ".join(names[:-2] + [" and ".join(names[-2:])]) if len(names) > 1 else (names[0] if names else "")

    async def _async_arrive(self, item: dict[str, Any]) -> None:
        """Someone came in: note how the lights are (when they are to go back to it), then do the "someone's there" action."""
        now = dt_util.now()
        if item["off_action"] == "restore" and item["id"] not in self._snap and should_act_on_presence(item, now.hour * 60 + now.minute, self._lux_now(item), self._sun_down()):
            await self._async_snapshot(item)
        await self._async_present(item)

    async def _async_snapshot(self, item: dict[str, Any]) -> None:
        """Keep each light's state, and the Hue scene its room or zone is showing, as a scene to go back to."""
        entities = list(item["lights"])
        for light in item["lights"]:
            select = f"select.{light.split('.', 1)[1]}_scene"
            if self.hass.states.get(select) is not None:
                entities.append(select)
        scene_id = f"church_drive_sensor_{item['id']}"
        try:
            async with asyncio.timeout(CALL_TIMEOUT):
                await self.hass.services.async_call("scene", "create", {"scene_id": scene_id, "snapshot_entities": entities}, blocking=True)
            self._snap[item["id"]] = f"scene.{scene_id}"
        except Exception as err:  # noqa: BLE001 - without a snapshot the lights are simply left as they are
            _LOGGER.warning("Sensor action %s: couldn't note how the lights were: %s", item["name"], err)

    async def _async_present(self, item: dict[str, Any]) -> None:
        now = dt_util.now()
        minutes = now.hour * 60 + now.minute
        if not should_act_on_presence(item, minutes, self._lux_now(item), self._sun_down()):
            return
        # It is dark enough to act: the light about to come on will lift the sensor's reading, so keep calling the room dark while it is lit.
        self._dark_hold[item["id"]] = time.monotonic() + LIT_HOLD_ON_S
        look = pick_look(item, minutes)
        for light in item["lights"]:
            if look["on_action"] == "hybrid":
                await self._call("church_drive", "apply_hybrid", {"id": look["scene"]}, item)
                self._note(item, f"played a theme on {self._light_names(item)}", item["lights"][0] if len(item["lights"]) == 1 else "")
                return
            if look["on_action"] == "scene":
                await self._call("church_drive", "apply_scene", {"entity_id": light, "scene": look["scene"]}, item)
            else:
                data: dict[str, Any] = {"entity_id": light}
                if look["pct"]:
                    data["brightness_pct"] = look["pct"]
                await self._call("light", "turn_on", data, item)
        scene = look["scene"].replace("_", " ") if look["on_action"] == "scene" else ""
        self._note(item, f"switched on {self._light_names(item)}" + (f" with {scene}" if scene else ""), item["lights"][0] if len(item["lights"]) == 1 else "")

    async def _async_empty(self, item: dict[str, Any]) -> None:
        # The sensor is slow to read the light going out, so the next person in a moment is still coming into the dark.
        if item["id"] in self._dark_hold:
            self._dark_hold[item["id"]] = time.monotonic() + LIT_HOLD_OFF_S
        # A light that was already on when someone came in is left on, unless this action is set to switch it off anyway.
        if item["off_action"] == "restore":
            self._was_on.pop(item["id"], None)
            scene = self._snap.pop(item["id"], None)
            if scene is not None:  # no snapshot means the "someone's there" action never ran, so there is nothing to put back
                await self._call("scene", "turn_on", {"entity_id": scene}, item)
                self._note(item, f"put {self._light_names(item)} back as it was", item["lights"][0] if len(item["lights"]) == 1 else "")
            return
        if self._was_on.pop(item["id"], False) and not item["off_if_on"]:
            return
        for light in item["lights"]:
            if item["off_action"] == "dim":
                await self._call("light", "turn_on", {"entity_id": light, "brightness_pct": item["dim_pct"]}, item)
            else:
                await self._call("light", "turn_off", {"entity_id": light}, item)
        self._note(item, f"switched off {self._light_names(item)}" if item["off_action"] != "dim" else f"dimmed {self._light_names(item)} to {item['dim_pct']}%",
                   item["lights"][0] if len(item["lights"]) == 1 else "")

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
        taken = {s: i["id"] for i in self._items.values() for s in i["sensors"]}
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
                "device_class": device_class,
                "area": device.area_id or entry.area_id,
                "state": state.state if state else "unavailable",
                "readings": readings,
                "settings": settings,
                "action": taken.get(entry.entity_id),
            })
        return sorted(out, key=lambda c: c["name"].lower())
