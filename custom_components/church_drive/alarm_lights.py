"""What the lights do when an alarm goes off (the rules are in alarm_lights_logic.py).

Watches every smoke, heat and carbon monoxide alarm (binary sensors of those classes) and every alarm control
panel. While one is going off it plays the lights' pattern: the **strobe** lights pulse and, when the rules say
so (night for a fire, home for the house alarm), the **constant** lights come on bright. Fire wins over the house
alarm. When it all stops the strobe lights show green (fire) or blue (alarm turned off) for a while, then every
light goes back to how it was before the first alarm.

Day or night is read once, when the alarm starts, from the chosen light sensors for the whole house. (The
lights themselves would otherwise change the readings halfway through.) What's chosen is kept in
.storage/church_drive.alarm_lights and set from the Alarm lights card in Manager, which also has a light-only
test: it plays the same pattern with no alarm, no notification and nothing else touched.
"""

from __future__ import annotations

import asyncio
import logging
from typing import Any, Callable

from homeassistant.const import EVENT_STATE_CHANGED
from homeassistant.core import Event, HomeAssistant, callback
from homeassistant.helpers.storage import Store

from .alarm_lights_logic import (
    COLOURS,
    CONSTANT_KELVIN,
    DEFAULTS,
    KINDS,
    SCENE_ID,
    WHEN,
    clean_config,
    ending,
    is_daytime,
    plan_for,
    profile,
    roles,
    security_mode,
)

_LOGGER = logging.getLogger(__name__)

STORE_VERSION = 1
EVENT_CHANGED = "church_drive_alarm_lights_changed"
FIRE_CLASSES = ("smoke", "heat", "carbon_monoxide")
PANEL = "alarm_control_panel."
IGNORED = ("unavailable", "unknown", None)
TEST_SECONDS = (5, 120, 20)  # least, most, and the usual length of a light test


class AlarmLights:
    """Alarm patterns on the lights, with a settings store and a light-only test."""

    def __init__(self, hass: HomeAssistant) -> None:
        self.hass = hass
        self._store: Store = Store(hass, STORE_VERSION, "church_drive.alarm_lights")
        self._cfg: dict[str, Any] = dict(DEFAULTS)
        self._unsub: list[Callable[[], None]] = []
        self._fire: set[str] = set()  # fire sensors that are on
        self._panels: set[str] = set()  # panels that are triggered
        self._armed: dict[str, str] = {}  # each panel's last armed mode
        self._mode = "home"  # of the house alarm that is going off
        self._kind: str | None = None  # "fire" or "security": what is going off, else None
        self._last_kind: str | None = None  # what it was when it stopped
        self._disarmed = False
        self._episode = False  # the lights are saved and an alarm is, or just was, going
        self._day = False
        self._playing: tuple[str, str] | None = None
        self._task: asyncio.Task | None = None
        self._test_task: asyncio.Task | None = None
        self._test: dict[str, Any] | None = None
        self._test_saved = False
        self._lock = asyncio.Lock()

    # ---- life ----------------------------------------------------------------

    async def async_start(self) -> None:
        data = await self._store.async_load() or {}
        self._cfg = clean_config(data)
        # What is on already doesn't start anything: only a change does.
        for state in self.hass.states.async_all("alarm_control_panel"):
            if state.state.startswith("armed_"):
                self._armed[state.entity_id] = state.state
        self._unsub.append(self.hass.bus.async_listen(EVENT_STATE_CHANGED, self._on_state))

    @callback
    def async_stop(self) -> None:
        for unsub in self._unsub:
            unsub()
        self._unsub.clear()
        for task in (self._task, self._test_task):
            if task is not None:
                task.cancel()

    async def _async_save(self) -> None:
        await self._store.async_save(self._cfg)

    # ---- settings ------------------------------------------------------------

    def config(self) -> dict[str, Any]:
        return {**self._cfg, "strobe": list(self._cfg["strobe"]), "constant": list(self._cfg["constant"]), "sensors": list(self._cfg["sensors"])}

    async def async_set(self, change: dict[str, Any]) -> dict[str, Any]:
        self._cfg = clean_config(change, self._cfg)
        await self._async_save()
        if not self._cfg["enabled"] and self._episode:
            self._evaluate()  # being switched off mid-alarm puts the lights back
        self._changed()
        return self.status()

    def _light_state(self, entity_id: str) -> dict[str, Any] | None:
        state = self.hass.states.get(entity_id)
        return None if state is None else {"entity_id": entity_id, "name": state.name, "state": state.state}

    def _expand(self, entity_id: str) -> list[str]:
        """A light group as the lights in it; any other light as itself. Missing lights are skipped."""
        state = self.hass.states.get(entity_id)
        if state is None:
            return []
        members = state.attributes.get("entity_id")
        if isinstance(members, (list, tuple)) and members:
            found = [m for m in members if isinstance(m, str) and m.startswith("light.") and self.hass.states.get(m) is not None]
            # A member can itself be a group; one level is what Hue and light groups use.
            return found
        return [entity_id]

    def _lights(self) -> tuple[list[str], list[str], list[str]]:
        """(strobe targets, constant lights, every light to save).

        The strobes are sent to the rooms and zones as chosen (one command each, which is what Hue copes with at
        half-second pulses); the constant lights and the saved lights are single lights."""
        strobe_lights, constant = roles(self._cfg, self._expand)
        targets = [e for e in self._cfg["strobe"] if self.hass.states.get(e) is not None]
        return targets, constant, [*strobe_lights, *constant]

    def _readings(self) -> list[dict[str, Any]]:
        out = []
        for entity_id in self._cfg["sensors"]:
            state = self.hass.states.get(entity_id)
            try:
                lux = float(state.state) if state is not None else None
            except (TypeError, ValueError):
                lux = None
            out.append({"entity_id": entity_id, "name": state.name if state is not None else entity_id, "lux": lux})
        return out

    def daytime(self) -> bool:
        return is_daytime([r["lux"] for r in self._readings()], self._cfg["lux"], self._cfg["rule"])

    def status(self) -> dict[str, Any]:
        state = "idle"
        if self._test is not None:
            state = "test"
        elif self._kind:
            state = self._kind
        elif self._episode:
            state = "ending"
        return {
            "config": self.config(),
            "state": state,
            "test": self._test,
            "mode": self._mode if self._kind == "security" else None,
            "day": self._day if self._episode else self.daytime(),
            "sensors": self._readings(),
            "missing": [e for e in (*self._cfg["strobe"], *self._cfg["constant"]) if self.hass.states.get(e) is None],
        }

    @callback
    def _changed(self) -> None:
        self.hass.bus.async_fire(EVENT_CHANGED)

    # ---- what is going off ----------------------------------------------------

    @callback
    def _on_state(self, event: Event) -> None:
        entity_id = event.data.get("entity_id", "")
        new = event.data.get("new_state")
        old = event.data.get("old_state")
        if new is None:
            return
        if entity_id.startswith(PANEL):
            self._on_panel(entity_id, old.state if old else None, new)
        elif entity_id.startswith("binary_sensor.") and new.attributes.get("device_class") in FIRE_CLASSES:
            if new.state == "on" and (old is None or old.state not in IGNORED):
                self._fire.add(entity_id)
                self._evaluate()
            elif new.state == "off" and entity_id in self._fire:
                self._fire.discard(entity_id)
                self._evaluate()

    def _on_panel(self, entity_id: str, before: str | None, new: Any) -> None:
        if new.state.startswith("armed_"):
            self._armed[entity_id] = new.state
        if new.state == "triggered" and before not in IGNORED and entity_id not in self._panels:
            self._panels.add(entity_id)
            self._mode = security_mode(before, self._armed.get(entity_id), new.attributes.get("targetState"))
            self._evaluate()
        elif new.state != "triggered" and entity_id in self._panels:
            self._panels.discard(entity_id)
            self._disarmed = new.state == "disarmed"
            self._evaluate()

    def _active(self) -> str | None:
        if self._fire:
            return "fire"
        return "security" if self._panels else None

    @callback
    def _evaluate(self) -> None:
        """Something changed: work out what the lights should be doing now."""
        kind = self._active() if self._cfg["enabled"] else None
        if kind:
            self._last_kind = kind
            if self._playing == (kind, self._mode) and self._task is not None and not self._task.done():
                return  # already doing it (a second smoke alarm joined in)
        elif not self._episode and (self._task is None or self._task.done()):
            self._kind = None
            return
        self._kind = kind
        self._playing = (kind, self._mode) if kind else None
        if self._task is not None:
            self._task.cancel()
        self._task = self.hass.async_create_task(self._run(kind))
        self._changed()

    # ---- doing it ---------------------------------------------------------------

    async def _run(self, kind: str | None) -> None:
        try:
            if kind:
                await self._stop_test()  # before the lock: a running test holds it
            async with self._lock:
                if kind:
                    if not self._episode:
                        await self._save_lights()
                        self._day = self.daytime()
                        self._episode = True
                        self._disarmed = False
                    await self._play(profile(kind, self._mode, self._day))
                else:
                    await self._finish()
        except asyncio.CancelledError:
            raise
        except Exception:  # noqa: BLE001
            _LOGGER.exception("The alarm lights hit a problem; carrying on")
        finally:
            self._changed()

    async def _finish(self) -> None:
        """Everything has stopped: the strobes' all-clear colour for a while, then the lights as they were."""
        if not self._episode:
            return
        colour = None
        seconds = 0
        if self._cfg["enabled"] and (self._last_kind == "fire" or (self._last_kind == "security" and self._disarmed)):
            colour, seconds = ending(self._last_kind, self._cfg)
        strobe, _, _ = self._lights()
        if colour and seconds > 0 and strobe:
            await self._colour(strobe, colour)
            await asyncio.sleep(seconds)
        await self._restore()
        self._episode = False
        self._last_kind = None

    async def _play(self, plan: dict[str, Any], seconds: float | None = None) -> None:
        """Put on the constant lights if the plan says so, then repeat the strobes' pattern (for `seconds`, or until cancelled)."""
        strobe, constant, _ = self._lights()
        if plan["constant"] and constant:
            await self._call("light", "turn_on", {"entity_id": constant, "brightness_pct": 100, "color_temp_kelvin": CONSTANT_KELVIN, "transition": 0})
        if not strobe:
            if seconds is None:
                await asyncio.Event().wait()  # nothing to pulse; hold until the alarm stops
            else:
                await asyncio.sleep(seconds)
            return
        loop = asyncio.get_running_loop()
        end = None if seconds is None else loop.time() + seconds
        while end is None or loop.time() < end:
            for colour, length in plan["cycle"]:
                await self._colour(strobe, colour)
                await asyncio.sleep(length)
                if end is not None and loop.time() >= end:
                    break

    async def _colour(self, lights: list[str], colour: str | None) -> None:
        if colour is None:
            await self._call("light", "turn_off", {"entity_id": lights, "transition": 0})
        else:
            await self._call(
                "light", "turn_on", {"entity_id": lights, "rgb_color": list(COLOURS[colour]), "brightness_pct": 100, "transition": 0}
            )

    async def _call(self, domain: str, service: str, data: dict[str, Any], blocking: bool = False) -> None:
        try:
            await self.hass.services.async_call(domain, service, data, blocking=blocking)
        except Exception:  # noqa: BLE001  one light failing mustn't stop the rest
            _LOGGER.warning("Alarm lights: %s.%s failed", domain, service, exc_info=True)

    async def _save_lights(self) -> None:
        _, _, lights = self._lights()
        if lights:
            await self._call("scene", "create", {"scene_id": SCENE_ID, "snapshot_entities": lights}, blocking=True)

    async def _restore(self) -> None:
        if self.hass.states.get(f"scene.{SCENE_ID}") is not None:
            await self._call("scene", "turn_on", {"entity_id": f"scene.{SCENE_ID}", "transition": 2}, blocking=True)

    # ---- the light-only test ---------------------------------------------------------

    async def async_test(self, test: str, when: str = "auto", seconds: int | None = None) -> dict[str, Any]:
        """Play what a real alarm would, on the lights only: no notification, no alarm. Puts the lights back after."""
        if test not in KINDS or when not in WHEN:
            raise ValueError("Unknown test")
        if self._episode or self._active():
            raise ValueError("A real alarm is going off, so no test now")
        low, high, usual = TEST_SECONDS
        length = usual if seconds is None else max(low, min(high, int(seconds)))
        plan = plan_for(test, when, self.daytime(), self._cfg)
        if self._test_task is not None:
            await self._stop_test()
        self._test = {"test": test, "when": "day" if plan["daytime"] else "night", "seconds": length}
        self._test_task = self.hass.async_create_task(self._run_test(plan, length))
        self._changed()
        return self.status()

    async def _run_test(self, plan: dict[str, Any], length: int) -> None:
        try:
            async with self._lock:
                self._test_saved = True
                await self._save_lights()
                if "end" in plan:
                    strobe, _, _ = self._lights()
                    if strobe:
                        await self._colour(strobe, plan["end"])
                    await asyncio.sleep(max(5, min(length, plan["seconds"])))
                else:
                    await self._play(plan, length)
        except asyncio.CancelledError:
            raise
        except Exception:  # noqa: BLE001
            _LOGGER.exception("The alarm lights test hit a problem")
        finally:
            await asyncio.shield(self._finish_test())

    async def _finish_test(self) -> None:
        if self._test_saved:
            self._test_saved = False
            await self._restore()
        self._test = None
        self._test_task = None
        self._changed()

    async def _stop_test(self) -> None:
        """End a test now (lights back as they were). A real alarm and the Stop button both use this."""
        task = self._test_task
        if task is None:
            return
        self._test_task = None
        task.cancel()
        try:
            await task
        except (asyncio.CancelledError, Exception):  # noqa: BLE001
            pass

    async def async_stop_test(self) -> dict[str, Any]:
        await self._stop_test()
        return self.status()
