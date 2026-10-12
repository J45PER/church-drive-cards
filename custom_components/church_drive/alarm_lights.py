"""What the lights do when an alarm goes off (the rules are in alarm_lights_logic.py).

Watches every smoke, heat and carbon monoxide alarm (binary sensors of those classes) and every alarm control
panel. While one is going off it plays the lights' pattern: the **strobe** lights pulse and, when the rules say
so (night for a fire, home for the house alarm), the **constant** lights come on bright. Fire wins over the house
alarm. When it all stops the strobe lights show green (fire) or blue (alarm turned off) for a while, then every
light goes back to how it was before the first alarm.

How the strobes flash: Hue lights are flashed by the Hue bridge itself (Hue's "signaling": one command per light
and the bridge repeats it on its own), because a stream of commands from Home Assistant is throttled by the bridge
and doesn't strobe. Lights that aren't Hue (or all of them, if the method is "pulses") are pulsed by Home
Assistant, one command at a time so they never queue up.

Day or night is read once, when the alarm starts, from the chosen light sensors for the whole house. (The
lights themselves would otherwise change the readings halfway through.) What's chosen is kept in
.storage/church_drive.alarm_lights and set from the Alarm lights card in Manager, which also has a light-only
test: it plays the same pattern with no alarm, no notification and nothing else touched.
"""

from __future__ import annotations

import asyncio
import logging
from dataclasses import dataclass
from typing import Any, Callable

from homeassistant.const import EVENT_STATE_CHANGED
from homeassistant.core import Event, HomeAssistant, callback
from homeassistant.helpers import entity_registry as er
from homeassistant.helpers.storage import Store

from .alarm_lights_logic import (
    COLOURS,
    CONSTANT_KELVIN,
    DEFAULTS,
    KINDS,
    SCENE_ID,
    WHEN,
    XY,
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
SIGNAL_SECONDS = 600  # a real alarm's Hue flashing runs this long, and is renewed halfway (HA stopping can't leave it on for long)
SETTLE = 1.5  # seconds for lights to stop flashing before they're put back
AGAIN = 2.5  # the lights are put back a second time this long after the first, in case a slow one missed it
SEND_AT_ONCE = 6  # Hue commands in flight together; the bridge copes with about ten a second


@dataclass
class Lights:
    """The lights an alarm uses."""

    targets: list[str]  # strobe rooms and zones as chosen (for the all-clear colour)
    strobe: list[str]  # the single lights in them
    constant: list[str]
    save: list[str]  # every single light to save and put back


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
        self._signalled: list[tuple[Any, str]] = []  # (Hue bridge, light id) flashing on the bridge now
        self._used_signal = False
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
        """A light group as the lights in it; any other light as itself. Missing lights are skipped.

        Hue puts the lights of a room or zone in the group's `entity_id` attribute as a *set*, other groups as a
        list, so anything that isn't text is read as a collection."""
        state = self.hass.states.get(entity_id)
        if state is None:
            return []
        members = state.attributes.get("entity_id")
        if members and not isinstance(members, (str, bytes)):
            try:
                found = sorted(m for m in members if isinstance(m, str) and m.startswith("light.") and self.hass.states.get(m) is not None)
            except TypeError:
                found = []
            if found:
                return found
        return [entity_id]

    def _lights(self) -> Lights:
        """The lights, by role. Strobes are sent to rooms and zones as chosen; everything saved is a single light."""
        strobe, constant = roles(self._cfg, self._expand)
        targets = [e for e in self._cfg["strobe"] if self.hass.states.get(e) is not None]
        return Lights(targets, strobe, constant, [*strobe, *constant])

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
                    await self._play(profile(kind, self._mode, self._day, self._cfg["pulse_ms"] / 1000))
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
        lights = self._lights()
        if colour and seconds > 0 and lights.targets:
            await self._colour(lights.targets, colour)
            await asyncio.sleep(seconds)
        await self._restore()
        self._episode = False
        self._last_kind = None

    def _bridges(self) -> dict[str, Any]:
        """Each running Hue (v2) bridge by config entry, to flash its lights from the bridge itself."""
        found = {}
        for entry in self.hass.config_entries.async_entries("hue"):
            bridge = getattr(entry, "runtime_data", None)
            if bridge is not None and getattr(bridge, "api_version", None) == 2:
                found[entry.entry_id] = bridge
        return found

    def _split(self, lights: list[str]) -> tuple[list[tuple[Any, str, str]], list[str]]:
        """(Hue lights as (bridge, light id, entity), the rest). The rest are pulsed by Home Assistant."""
        if self._cfg["method"] != "hue":
            return [], list(lights)
        registry = er.async_get(self.hass)
        bridges = self._bridges()
        hue: list[tuple[Any, str, str]] = []
        rest: list[str] = []
        for entity_id in lights:
            entry = registry.async_get(entity_id)
            bridge = bridges.get(entry.config_entry_id) if entry is not None and entry.platform == "hue" else None
            if bridge is None:
                rest.append(entity_id)
            else:
                hue.append((bridge, entry.unique_id, entity_id))
        return hue, rest

    async def _hue_put(self, bridge: Any, light_id: str, signaling: dict[str, Any]) -> bool:
        try:
            await bridge.api.request("put", f"clip/v2/resource/light/{light_id}", json={"signaling": signaling})
            return True
        except Exception:  # noqa: BLE001  that light just gets pulsed from Home Assistant instead
            _LOGGER.warning("Alarm lights: the Hue bridge wouldn't flash light %s", light_id, exc_info=True)
            return False

    async def _hue_all(self, jobs: list[tuple[Any, str, dict[str, Any]]]) -> list[bool]:
        gate = asyncio.Semaphore(SEND_AT_ONCE)

        async def one(job: tuple[Any, str, dict[str, Any]]) -> bool:
            async with gate:
                return await self._hue_put(*job)

        return list(await asyncio.gather(*(one(j) for j in jobs)))

    async def _signal_start(self, hue: list[tuple[Any, str, str]], signal: tuple[str, list[str]], seconds: float) -> list[str]:
        """Start the bridge flashing these Hue lights. Returns the lights it wouldn't flash (to be pulsed instead)."""
        kind, colours = signal
        body = {"signal": kind, "duration": int(max(1, seconds)) * 1000, "colors": [{"xy": {"x": XY[c][0], "y": XY[c][1]}} for c in colours]}
        done = await self._hue_all([(bridge, light_id, body) for bridge, light_id, _ in hue])
        self._signalled = [(b, i) for (b, i, _), ok in zip(hue, done) if ok] + [x for x in self._signalled if x not in [(b, i) for b, i, _ in hue]]
        self._used_signal = self._used_signal or any(done)
        return [entity for (_, _, entity), ok in zip(hue, done) if not ok]

    async def _signal_stop(self) -> None:
        """Stop the bridge flashing, and give the lights a moment to settle."""
        flashing, self._signalled = self._signalled, []
        if not flashing:
            return
        # The bridge ignores the duration of no_signal but its rules still insist it is there.
        stop = {"signal": "no_signal", "duration": 1000}
        done = await self._hue_all([(bridge, light_id, stop) for bridge, light_id in flashing])
        if not all(done):  # one more go for any it missed, so nothing is left flashing
            await asyncio.sleep(0.5)
            await self._hue_all([(bridge, light_id, stop) for (bridge, light_id), ok in zip(flashing, done) if not ok])
        await asyncio.sleep(SETTLE)

    async def _play(self, plan: dict[str, Any], seconds: float | None = None) -> None:
        """Constant lights on if the plan says so, then flash the strobes (for `seconds`, or until cancelled)."""
        lights = self._lights()
        if plan["constant"] and lights.constant:
            await self._call("light", "turn_on", {"entity_id": lights.constant, "brightness_pct": 100, "color_temp_kelvin": CONSTANT_KELVIN, "transition": 0})
        loop = asyncio.get_running_loop()
        end = None if seconds is None else loop.time() + seconds
        try:
            hue, rest = self._split(lights.strobe)
            if not hue:
                rest = list(lights.targets)  # nothing flashed by Hue: pulse each room or zone with one command, not one per light
            length = SIGNAL_SECONDS if seconds is None else seconds + 2
            if hue:
                rest += await self._signal_start(hue, plan["signal"], length)
            renew = loop.time() + SIGNAL_SECONDS / 2
            while end is None or loop.time() < end:
                if rest:
                    for colour, step in plan["cycle"]:
                        await self._colour(rest, colour)
                        await asyncio.sleep(step)
                        if end is not None and loop.time() >= end:
                            break
                else:
                    await asyncio.sleep(1 if end is None else max(0.0, min(1, end - loop.time())))
                if hue and end is None and loop.time() >= renew:
                    rest = [e for e in rest if e not in [x for _, _, x in hue]]
                    rest += await self._signal_start(hue, plan["signal"], length)
                    renew = loop.time() + SIGNAL_SECONDS / 2
        finally:
            await asyncio.shield(self._signal_stop())

    async def _colour(self, lights: list[str], colour: str | None) -> None:
        """Every light to one colour (or dark), and wait for it, so pulses can never queue up behind a slow bridge."""
        if colour is None:
            await self._call("light", "turn_off", {"entity_id": lights, "transition": 0}, blocking=True)
        else:
            await self._call(
                "light", "turn_on", {"entity_id": lights, "rgb_color": list(COLOURS[colour]), "brightness_pct": 100, "transition": 0}, blocking=True
            )

    async def _call(self, domain: str, service: str, data: dict[str, Any], blocking: bool = False) -> None:
        try:
            if blocking:
                await asyncio.wait_for(self.hass.services.async_call(domain, service, data, blocking=True), 5)
            else:
                await self.hass.services.async_call(domain, service, data, blocking=False)
        except Exception:  # noqa: BLE001  one light failing mustn't stop the rest
            _LOGGER.warning("Alarm lights: %s.%s failed", domain, service, exc_info=True)

    async def _save_lights(self) -> None:
        lights = self._lights().save
        if lights:
            await self._call("scene", "create", {"scene_id": SCENE_ID, "snapshot_entities": lights}, blocking=True)

    async def _restore(self) -> None:
        """Put every light back as it was. Done twice if the bridge was flashing them, as a slow one can miss the first."""
        if self.hass.states.get(f"scene.{SCENE_ID}") is None:
            return
        for again in (False, True):
            if again:
                if not self._used_signal:
                    break
                await asyncio.sleep(AGAIN)
            await self._call("scene", "turn_on", {"entity_id": f"scene.{SCENE_ID}", "transition": 1 if again else 2}, blocking=True)
        self._used_signal = False

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
                    targets = self._lights().targets
                    if targets:
                        await self._colour(targets, plan["end"])
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
