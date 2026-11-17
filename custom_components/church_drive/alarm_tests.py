"""Monthly alarm tests (the rules are in alarm_tests_logic.py).

Every minute this looks at each test's schedule. 15 and 5 minutes before a test, everyone who is home is told it's
coming; at the time, the test runs:

- House alarm: the Ring Chime siren (siren.downstairs_siren) sounds for about 10 seconds. The alarm itself is never armed
  or triggered, so nothing is sent to anyone monitoring it.
- Smoke and heat alarms: each one's Device Test button is pressed (the button ending "_test" on the device of every
  smoke or heat sensor).
- CO alarm: the same, for every carbon monoxide sensor's device.

What's chosen is kept in .storage/church_drive.alarm_tests and set from the app (the alarm's and the smoke and CO
alarms' Details pages). Administrators only.
"""

from __future__ import annotations

import asyncio
import logging
from typing import Any, Callable

from homeassistant.core import HomeAssistant, callback
from homeassistant.helpers import entity_registry as er
from homeassistant.helpers.event import async_track_time_change
from homeassistant.helpers.storage import Store
from homeassistant.util import dt as dt_util

from .alarm_tests_logic import KINDS, NAMES, clean_config, clean_test, due, next_run, summary, warning, when_text

_LOGGER = logging.getLogger(__name__)

STORE_VERSION = 1
EVENT_CHANGED = "church_drive_alarm_tests_changed"
SIREN = "siren.downstairs_siren"
SIREN_SECONDS = 10
SENSOR_CLASSES = {"smoke": ("smoke", "heat"), "co": ("carbon_monoxide",)}


class AlarmTests:
    """Runs the monthly alarm tests and keeps their schedules across restarts."""

    def __init__(self, hass: HomeAssistant, people: Any = None) -> None:
        self.hass = hass
        self._people = people
        self._store: Store = Store(hass, STORE_VERSION, "church_drive.alarm_tests")
        self._cfg: dict[str, dict[str, Any]] = clean_config(None)
        self._last: dict[str, dict[str, Any]] = {}
        self._unsub: list[Callable[[], None]] = []
        self._tasks: set[asyncio.Task] = set()

    async def async_start(self) -> None:
        data = await self._store.async_load() or {}
        self._cfg = clean_config(data)
        last = data.get("last")
        self._last = {k: v for k, v in last.items() if isinstance(v, dict)} if isinstance(last, dict) else {}
        self._unsub.append(async_track_time_change(self.hass, self._on_minute, second=0))

    @callback
    def async_stop(self) -> None:
        for unsub in self._unsub:
            unsub()
        self._unsub.clear()
        for task in self._tasks:
            if not task.done():
                task.cancel()
        self._tasks.clear()

    async def _async_save(self) -> None:
        await self._store.async_save({"tests": self._cfg, "last": self._last})

    # ---- what's set --------------------------------------------------------

    def targets(self, kind: str) -> list[str]:
        """The entities a test uses: the siren, or the test buttons of the smoke and heat (or CO) alarms."""
        if kind == "alarm":
            return [SIREN] if self.hass.states.get(SIREN) is not None else []
        classes = SENSOR_CLASSES[kind]
        reg = er.async_get(self.hass)
        devices = {
            entry.device_id
            for s in self.hass.states.async_all("binary_sensor")
            if s.attributes.get("device_class") in classes and (entry := reg.async_get(s.entity_id)) and entry.device_id
        }
        return sorted(
            e.entity_id for e in reg.entities.values()
            if e.domain == "button" and e.device_id in devices and e.entity_id.endswith("_test") and not e.disabled_by
        )

    def _name(self, entity: str) -> str:
        state = self.hass.states.get(entity)
        name = state.name if state else entity
        return name.replace(" Device Test", "").replace(" Test", "")

    def status(self) -> dict[str, Any]:
        now = dt_util.now()
        out: dict[str, Any] = {}
        for kind in KINDS:
            cfg = self._cfg[kind]
            at = next_run(cfg, now)
            out[kind] = {
                **cfg,
                "name": NAMES[kind],
                "summary": summary(cfg),
                "next": {"at": at.isoformat(), "text": when_text(at)} if at else None,
                "targets": [self._name(e) for e in self.targets(kind)],
                "last": self._last.get(kind),
            }
        return {"tests": out}

    async def async_set(self, kind: str, change: dict[str, Any]) -> dict[str, Any]:
        """Change one test's schedule (only what's sent changes)."""
        if kind not in KINDS:
            raise ValueError("Unknown test")
        self._cfg[kind] = clean_test(change, self._cfg[kind])
        await self._async_save()
        self.hass.bus.async_fire(EVENT_CHANGED, {"kind": kind})
        return self.status()

    # ---- running -----------------------------------------------------------

    @callback
    def _on_minute(self, now: Any) -> None:
        local = dt_util.as_local(now)
        for kind in KINDS:
            what = due(self._cfg[kind], local)
            if what == "run":
                self._spawn(self._async_run(kind))
            elif what:
                self._spawn(self._async_warn(kind, int(what[4:])))

    def _spawn(self, coro: Any) -> None:
        task = self.hass.async_create_task(coro)
        self._tasks.add(task)
        task.add_done_callback(self._tasks.discard)

    def _home(self) -> list[str]:
        return [s.entity_id for s in self.hass.states.async_all("person") if s.state == "home"]

    async def _async_warn(self, kind: str, minutes: int) -> None:
        home = self._home()
        if not home or self._people is None:
            return
        title, message = warning(kind, minutes)
        try:
            await self._people.async_notify(None, home, title, message, tag=f"church-drive-alarm-test-{kind}", link="/dashboard-mobile/security")
        except Exception as err:  # noqa: BLE001 - a notice that can't be sent mustn't stop the test
            _LOGGER.warning("Alarm test: couldn't send the %s warning: %s", kind, err)

    async def _async_run(self, kind: str) -> None:
        targets = self.targets(kind)
        ok: list[str] = []
        for entity in targets:
            try:
                if kind == "alarm":
                    await self.hass.services.async_call("siren", "turn_on", {"entity_id": entity}, blocking=True)
                    await asyncio.sleep(SIREN_SECONDS)
                    await self.hass.services.async_call("siren", "turn_off", {"entity_id": entity}, blocking=True)
                else:
                    await self.hass.services.async_call("button", "press", {"entity_id": entity}, blocking=True)
                ok.append(entity)
            except asyncio.CancelledError:
                raise
            except Exception as err:  # noqa: BLE001 - one alarm that doesn't answer mustn't stop the others
                _LOGGER.warning("Alarm test: %s didn't take the test: %s", entity, err)
        self._last[kind] = {"at": dt_util.now().isoformat(), "ok": len(ok) == len(targets) and bool(targets), "tested": len(ok), "of": len(targets)}
        await self._async_save()
        self.hass.bus.async_fire(EVENT_CHANGED, {"kind": kind})
