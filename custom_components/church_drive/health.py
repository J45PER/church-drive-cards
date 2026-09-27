"""Device health: spot devices whose dashboard state has gone stale, and fix them.

Why: the Philips fan (philips_airpurifier_coap) sometimes reconnects after a
restart with an old status ("off", its runtime counter frozen at an earlier
value) and then stops updating while it's really running. A refresh doesn't
help; sending it a command does.

For each watched entity this keeps:
- the last *real* reading (state and attributes when the device was clearly
  live), stored across restarts;
- when it was last heard from, and its usual gap between updates.

Checks (every minute, and two minutes after Home Assistant starts):
- Old readings: a counter such as `runtime` went backwards (a stale status
  came back after a reconnect). A reset is accepted once the counter moves on
  from the new value twice (e.g. after a power cut).
- Stopped updating: a device with a counter says it's on but hasn't updated
  for 3x its usual gap (at least 10 minutes). Devices without a counter (e.g.
  thermostats) can be quiet for hours, so they're only checked for:
- Unavailable for more than 5 minutes ("unknown" is normal for some, e.g. an
  RF blind, so it doesn't count).

Fixes, all automatic (the user's choice): refresh straight away; after a
minute, re-sync (send the last real state back: fans and thermostats); after
three more minutes, reconnect (reload its integration, at most once every
6 hours per integration, and only while it's loaded).
"""

from __future__ import annotations

import logging
import re
import statistics
import time
from datetime import timedelta
from typing import Any

from homeassistant.config_entries import ConfigEntryState
from homeassistant.const import EVENT_HOMEASSISTANT_STARTED, STATE_UNAVAILABLE, STATE_UNKNOWN
from homeassistant.core import CoreState, Event, HomeAssistant, callback
from homeassistant.helpers import entity_registry as er
from homeassistant.helpers.dispatcher import async_dispatcher_send
from homeassistant.helpers.event import async_track_state_change_event, async_track_time_interval
from homeassistant.helpers.storage import Store
from homeassistant.util import dt as dt_util

from .const import DOMAIN, SIGNAL_HEALTH

_LOGGER = logging.getLogger(__name__)

COUNTER_ATTRS = ("runtime", "uptime")
OFF_STATES = {"off", "idle", "closed", "standby"}
SKIP_ATTRS = {"friendly_name", "icon", "entity_picture", "supported_features", "restored"}
MIN_SILENT = 600  # seconds
UNAVAILABLE_AFTER = 300
RESYNC_AFTER = 0  # re-sync as soon as a device looks stale
RECONNECT_AFTER = 240
RECONNECT_EVERY = 6 * 3600
REAL_MAX_AGE = 24 * 3600  # don't re-sync to a reading older than this

_DURATION = re.compile(r"^(?:(\d+) days?, )?(\d+):(\d{2}):(\d{2})")


def counter_seconds(value: Any) -> float | None:
    """A counter attribute as seconds: '5 days, 6:09:59', '6:09:59' or a number."""
    if value is None:
        return None
    if isinstance(value, (int, float)):
        return float(value)
    m = _DURATION.match(str(value).strip())
    if m:
        days, h, mi, s = (int(x or 0) for x in m.groups())
        return days * 86400 + h * 3600 + mi * 60 + s
    try:
        return float(value)
    except (TypeError, ValueError):
        return None


def usual_gap(gaps: list[float]) -> float | None:
    """The typical time between updates, once there are a few to go on."""
    return statistics.median(gaps) if len(gaps) >= 3 else None


def is_active(state: str) -> bool:
    return state not in OFF_STATES and state not in (STATE_UNAVAILABLE, STATE_UNKNOWN)


class DeviceHealth:
    """Watches entities, keeps their status, and fixes stale ones."""

    def __init__(self, hass: HomeAssistant, entity_ids: list[str]) -> None:
        self.hass = hass
        self.entity_ids = list(dict.fromkeys(entity_ids))
        self._store: Store = Store(hass, 1, f"{DOMAIN}.health")
        self.saved: dict[str, dict] = {}  # per entity: real reading, counter
        self.live: dict[str, dict] = {}  # per entity: last heard, gaps, status
        self._reconnected: dict[str, float] = {}
        self._unsubs: list = []
        self._unsub_started = None

    # ---- lifecycle
    async def async_start(self) -> None:
        self.saved = (await self._store.async_load()) or {}
        now = time.time()
        for entity_id in self.entity_ids:
            self.live[entity_id] = {"heard": None, "gaps": [], "status": "ok", "reason": "", "since": None, "fixes": [], "step": 0}
        self._unsubs.append(async_track_state_change_event(self.hass, self.entity_ids, self._changed))
        self._unsubs.append(async_track_time_interval(self.hass, self._tick, timedelta(minutes=1)))
        # Devices whose state is already in place get checked now; the rest
        # as soon as they report (see _changed), so there's no wait.
        self._startup_check()
        if self.hass.state is not CoreState.running:
            self._unsub_started = self.hass.bus.async_listen_once(EVENT_HOMEASSISTANT_STARTED, self._on_started)
        self._started_at = now

    @callback
    def _on_started(self, _event: Event) -> None:
        self._unsub_started = None
        self._startup_check()

    @callback
    def async_stop(self) -> None:
        if self._unsub_started:
            self._unsub_started()
            self._unsub_started = None
        for unsub in self._unsubs:
            unsub()
        self._unsubs.clear()

    def _save(self) -> None:
        self._store.async_delay_save(lambda: self.saved, 10)

    # ---- readings
    @staticmethod
    def _counter(attrs: dict) -> tuple[str, float] | None:
        for key in COUNTER_ATTRS:
            if key in attrs:
                secs = counter_seconds(attrs[key])
                if secs is not None:
                    return key, secs
        return None

    def _remember_real(self, entity_id: str, state) -> None:
        attrs = {k: v for k, v in state.attributes.items() if k not in SKIP_ATTRS and not isinstance(v, (list, dict))}
        saved = self.saved.setdefault(entity_id, {})
        saved["real"] = {"state": state.state, "attrs": attrs, "at": time.time()}
        counter = self._counter(state.attributes)
        if counter:
            saved["counter"] = counter[1]
        saved.pop("candidate", None)
        saved.pop("candidate_moves", None)
        self._save()

    @callback
    def _changed(self, event: Event) -> None:
        entity_id = event.data["entity_id"]
        new = event.data.get("new_state")
        live = self.live.get(entity_id)
        if new is None or live is None:
            return
        now = time.time()
        if new.state == STATE_UNAVAILABLE:
            live.setdefault("unavailable_since", now)
            self._publish()
            return
        live.pop("unavailable_since", None)
        if live["heard"]:
            gap = now - live["heard"]
            if gap > 5:
                live["gaps"] = (live["gaps"] + [gap])[-20:]
        live["heard"] = now
        saved = self.saved.setdefault(entity_id, {})
        counter = self._counter(new.attributes)
        if counter is None:
            # No counter: any update counts as live.
            self._remember_real(entity_id, new)
            self._set_ok(entity_id)
            return
        prev = saved.get("counter")
        value = counter[1]
        if prev is None or value > prev:
            self._remember_real(entity_id, new)
            self._set_ok(entity_id)
        elif value < prev - 60:
            # Went backwards: old readings, or a real reset. A reset keeps
            # counting up from the new value; stale readings don't.
            candidate = saved.get("candidate")
            if candidate is not None and value > candidate:
                saved["candidate_moves"] = saved.get("candidate_moves", 0) + 1
                if saved["candidate_moves"] >= 2:
                    self._remember_real(entity_id, new)
                    self._set_ok(entity_id)
                    return
            saved["candidate"] = value
            self._set_stale(entity_id, "Came back with old readings")
        self._publish()

    # ---- checks
    def _went_backwards(self, entity_id: str, state) -> bool:
        """Is the device showing a counter below its last real one?"""
        if state is None or state.state == STATE_UNAVAILABLE:
            return False
        counter = self._counter(state.attributes)
        prev = self.saved.get(entity_id, {}).get("counter")
        return bool(counter) and prev is not None and counter[1] < prev - 60

    @callback
    def _startup_check(self, _now=None) -> None:
        for entity_id in self.entity_ids:
            if self._went_backwards(entity_id, self.hass.states.get(entity_id)):
                self._set_stale(entity_id, "Came back with old readings after a restart")
        self._tick()

    @callback
    def _tick(self, _now=None) -> None:
        now = time.time()
        for entity_id in self.entity_ids:
            live = self.live[entity_id]
            state = self.hass.states.get(entity_id)
            if state is None:
                continue
            if state.state == STATE_UNAVAILABLE:
                since = live.setdefault("unavailable_since", now)
                if now - since > UNAVAILABLE_AFTER:
                    self._set_stale(entity_id, "Unavailable")
            else:
                live.pop("unavailable_since", None)
                if live["status"] != "ok" and live["reason"] == "Unavailable":
                    self._set_ok(entity_id)
                if live["status"] == "ok" and self._went_backwards(entity_id, state):
                    self._set_stale(entity_id, "Came back with old readings")
            gap = usual_gap(live["gaps"])
            limit = max(3 * gap, MIN_SILENT) if gap else None
            heard = live["heard"] or dt_util.as_timestamp(state.last_updated)
            # Only devices with a heartbeat counter report regularly; others
            # (e.g. thermostats) can be quiet for hours without a fault.
            if limit and self._counter(state.attributes) and is_active(state.state) and now - heard > limit and live["status"] == "ok":
                self._set_stale(entity_id, "Stopped updating")
            if live["status"] != "ok":
                self._advance_fix(entity_id, now)
        self._publish()

    # ---- status
    def _set_stale(self, entity_id: str, reason: str) -> None:
        live = self.live[entity_id]
        if live["status"] == "ok":
            live.update(status="stale", reason=reason, since=time.time(), step=0, fixes=[])
            _LOGGER.warning("%s looks stale: %s", entity_id, reason)
            self.hass.async_create_task(self._refresh(entity_id))
            self._advance_fix(entity_id, time.time())
            self._publish()
        else:
            live["reason"] = reason

    def _set_ok(self, entity_id: str) -> None:
        live = self.live[entity_id]
        if live["status"] != "ok":
            _LOGGER.info("%s is responding again", entity_id)
        live.update(status="ok", reason="", since=None, step=0)
        self._publish()

    # ---- fixes
    def _advance_fix(self, entity_id: str, now: float) -> None:
        live = self.live[entity_id]
        age = now - (live["since"] or now)
        if live["step"] < 1 and age >= RESYNC_AFTER:
            live["step"] = 1
            self.hass.async_create_task(self.async_resync(entity_id))
        elif live["step"] < 2 and age >= RESYNC_AFTER + RECONNECT_AFTER:
            live["step"] = 2
            self.hass.async_create_task(self.async_reconnect(entity_id))

    def _note(self, entity_id: str, what: str) -> None:
        self.live[entity_id]["fixes"] = (self.live[entity_id]["fixes"] + [f"{dt_util.now().strftime('%H:%M')} {what}"])[-5:]
        self._publish()

    async def _refresh(self, entity_id: str) -> None:
        self._note(entity_id, "Refreshed")
        try:
            await self.hass.services.async_call("homeassistant", "update_entity", {"entity_id": entity_id}, blocking=False)
        except Exception as err:  # noqa: BLE001
            _LOGGER.debug("Refresh of %s failed: %s", entity_id, err)

    async def async_resync(self, entity_id: str) -> bool:
        """Send the last real reading back to the device."""
        real = self.saved.get(entity_id, {}).get("real")
        if not real or time.time() - real.get("at", 0) > REAL_MAX_AGE:
            self._note(entity_id, "No recent real reading to re-sync to")
            return False
        domain = entity_id.split(".")[0]
        state, attrs = real["state"], real.get("attrs", {})
        calls: list[tuple[str, str, dict]] = []
        if domain == "fan":
            if state == "off":
                calls.append(("fan", "turn_off", {}))
            elif attrs.get("preset_mode"):
                calls.append(("fan", "set_preset_mode", {"preset_mode": attrs["preset_mode"]}))
            elif attrs.get("percentage"):
                calls.append(("fan", "set_percentage", {"percentage": attrs["percentage"]}))
            else:
                calls.append(("fan", "turn_on", {}))
            if state != "off" and attrs.get("oscillating") is not None:
                calls.append(("fan", "oscillate", {"oscillating": bool(attrs["oscillating"])}))
        elif domain == "climate":
            calls.append(("climate", "set_hvac_mode", {"hvac_mode": state}))
            if state != "off" and attrs.get("temperature") is not None:
                calls.append(("climate", "set_temperature", {"temperature": attrs["temperature"]}))
            if state != "off" and attrs.get("preset_mode"):
                calls.append(("climate", "set_preset_mode", {"preset_mode": attrs["preset_mode"]}))
        else:
            self._note(entity_id, "Re-sync not possible for this kind of device")
            return False
        when = dt_util.as_local(dt_util.utc_from_timestamp(real["at"])).strftime("%H:%M")
        for dom, service, data in calls:
            try:
                await self.hass.services.async_call(dom, service, {"entity_id": entity_id, **data}, blocking=True)
            except Exception as err:  # noqa: BLE001
                self._note(entity_id, f"Re-sync failed: {err}")
                return False
        self._note(entity_id, f"Re-synced to its {when} reading")
        return True

    def _entry_id(self, entity_id: str) -> str | None:
        entry = er.async_get(self.hass).async_get(entity_id)
        return entry.config_entry_id if entry else None

    async def async_reconnect(self, entity_id: str) -> bool:
        """Reload the device's integration, carefully."""
        entry_id = self._entry_id(entity_id)
        entry = self.hass.config_entries.async_get_entry(entry_id) if entry_id else None
        if not entry or entry.domain == DOMAIN:
            self._note(entity_id, "No integration to reconnect")
            return False
        if entry.state is not ConfigEntryState.LOADED:
            self._note(entity_id, f"Integration is {entry.state.value}; not reconnecting")
            return False
        if time.time() - self._reconnected.get(entry_id, 0) < RECONNECT_EVERY:
            self._note(entity_id, "Reconnected recently; waiting")
            return False
        self._reconnected[entry_id] = time.time()
        self._note(entity_id, f"Reconnecting {entry.title}")
        try:
            await self.hass.config_entries.async_reload(entry_id)
        except Exception as err:  # noqa: BLE001
            self._note(entity_id, f"Reconnect failed: {err}")
            return False
        return True

    async def async_fix(self, entity_id: str, action: str) -> None:
        """A fix asked for from a card's button."""
        if entity_id not in self.live:
            return
        if action == "refresh":
            await self._refresh(entity_id)
        elif action == "reconnect":
            self._reconnected.pop(self._entry_id(entity_id), None)
            await self.async_reconnect(entity_id)
        else:
            await self._refresh(entity_id)
            await self.async_resync(entity_id)

    # ---- output for the sensor and cards
    def summary(self) -> dict[str, Any]:
        devices = {}
        now = time.time()
        for entity_id in self.entity_ids:
            live = self.live.get(entity_id, {})
            state = self.hass.states.get(entity_id)
            real = self.saved.get(entity_id, {}).get("real")
            heard = live.get("heard") or (dt_util.as_timestamp(state.last_updated) if state else None)
            devices[entity_id] = {
                "name": (state.attributes.get("friendly_name") if state else None) or entity_id,
                "status": live.get("status", "ok"),
                "reason": live.get("reason", ""),
                "since": dt_util.utc_from_timestamp(live["since"]).isoformat() if live.get("since") else None,
                "last_heard": dt_util.utc_from_timestamp(heard).isoformat() if heard else None,
                "usual_gap": round(usual_gap(live.get("gaps", [])) or 0) or None,
                "last_real": {
                    "state": real["state"],
                    "preset_mode": real.get("attrs", {}).get("preset_mode"),
                    "temperature": real.get("attrs", {}).get("temperature"),
                    "at": dt_util.utc_from_timestamp(real["at"]).isoformat(),
                } if real else None,
                "fixes": live.get("fixes", []),
            }
        return devices

    def _publish(self) -> None:
        async_dispatcher_send(self.hass, SIGNAL_HEALTH)
