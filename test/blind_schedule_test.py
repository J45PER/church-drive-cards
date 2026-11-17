"""The blind schedule end to end, against a pretend Home Assistant (real asyncio, a fake blind and services)."""
import asyncio
import importlib
import pathlib
import sys
import types
import unittest
from datetime import datetime, timezone

ROOT = pathlib.Path(__file__).parent.parent / "custom_components" / "church_drive"


def _install_stubs():
    def mod(name, **attrs):
        m = types.ModuleType(name)
        m.__dict__.update(attrs)
        sys.modules[name] = m
        return m

    class Store:
        saved = None
        loaded = None

        def __init__(self, *a, **k):
            pass

        async def async_load(self):
            return Store.loaded

        async def async_save(self, data):
            Store.saved = data

    class Dt:
        @staticmethod
        def now():
            return Dt.current

        @staticmethod
        def as_local(d):
            return d

        @staticmethod
        def parse_datetime(s):
            return datetime.fromisoformat(s)

    Dt.current = datetime(2026, 11, 2, 12, 0, tzinfo=timezone.utc)  # a Monday

    mod("homeassistant")
    mod("homeassistant.core", HomeAssistant=object, callback=lambda f: f)
    helpers = mod("homeassistant.helpers")
    helpers.event = mod("homeassistant.helpers.event", async_track_time_change=lambda hass, cb, **k: (lambda: None))
    helpers.storage = mod("homeassistant.helpers.storage", Store=Store)
    util = mod("homeassistant.util")
    util.dt = mod("homeassistant.util.dt", now=Dt.now, as_local=Dt.as_local, parse_datetime=Dt.parse_datetime)
    pkg = types.ModuleType("church_drive")
    pkg.__path__ = [str(ROOT)]
    sys.modules["church_drive"] = pkg
    return importlib.import_module("church_drive.blind_schedule"), Store, Dt


bsm, Store, Dt = _install_stubs()
real_sleep = asyncio.sleep
BLIND = "cover.roller_blind"
FAN = "fan.bedroom_fan"


class State:
    def __init__(self, state):
        self.state, self.attributes = state, {}


class Hass:
    """A blind that takes the call (or refuses it, `fail` times first)."""

    def __init__(self, state="unknown", fail=0):
        self.cover = State(state)
        self.states = types.SimpleNamespace(get=lambda e: self.cover if e in (BLIND, FAN, "humidifier.bedroom") else None)
        self.services = types.SimpleNamespace(async_call=self._call)
        self.calls, self.fail, self.fired = [], fail, []
        self.bus = types.SimpleNamespace(async_fire=lambda name, data=None: self.fired.append((name, data)))
        self.tasks = []

    def async_create_task(self, coro):
        task = asyncio.ensure_future(coro)
        self.tasks.append(task)
        return task

    async def _call(self, domain, service, data, blocking=False):
        self.calls.append((domain, service, dict(data)))
        if self.fail > 0:
            self.fail -= 1
            raise RuntimeError("no answer")


def run(coro):
    return asyncio.run(coro)


async def fast_sleep(_s):
    await real_sleep(0)


class Applying(unittest.TestCase):
    def setUp(self):
        bsm.asyncio.sleep = fast_sleep

    def tearDown(self):
        bsm.asyncio.sleep = real_sleep

    def test_open_and_close_call_the_cover_services(self):
        hass = Hass()
        sched = bsm.BlindSchedule(hass)
        run(sched._async_apply(BLIND, "open"))
        run(sched._async_apply(BLIND, "close"))
        self.assertEqual(hass.calls, [("cover", "open_cover", {"entity_id": BLIND}), ("cover", "close_cover", {"entity_id": BLIND})])
        self.assertTrue(sched._last_result[BLIND]["ok"])

    def test_a_fan_turns_on_at_its_speed_and_off(self):
        hass = Hass()
        sched = bsm.BlindSchedule(hass)
        run(sched._async_apply(FAN, "open", "sleep"))
        run(sched._async_apply(FAN, "open", "percentage:40"))
        run(sched._async_apply(FAN, "open"))
        run(sched._async_apply(FAN, "close"))
        self.assertEqual(hass.calls, [
            ("fan", "set_preset_mode", {"entity_id": FAN, "preset_mode": "sleep"}),
            ("fan", "set_percentage", {"entity_id": FAN, "percentage": 40}),
            ("fan", "turn_on", {"entity_id": FAN}),
            ("fan", "turn_off", {"entity_id": FAN}),
        ])

    def test_a_humidifier_is_turned_on_before_its_mode_is_set(self):
        hass = Hass()
        sched = bsm.BlindSchedule(hass)
        run(sched._async_apply("humidifier.bedroom", "open", "sleep"))
        self.assertEqual(hass.calls, [
            ("humidifier", "turn_on", {"entity_id": "humidifier.bedroom"}),
            ("humidifier", "set_mode", {"entity_id": "humidifier.bedroom", "mode": "sleep"}),
        ])

    def test_a_refused_call_is_tried_again(self):
        hass = Hass(fail=2)
        sched = bsm.BlindSchedule(hass)
        run(sched._async_apply(BLIND, "open"))
        self.assertEqual(len(hass.calls), 3)
        self.assertEqual(sched._last_result[BLIND]["tries"], 3)
        self.assertTrue(sched._last_result[BLIND]["ok"])

    def test_gives_up_after_three_tries(self):
        hass = Hass(fail=99)
        sched = bsm.BlindSchedule(hass)
        run(sched._async_apply(BLIND, "close"))
        self.assertEqual(len(hass.calls), 3)
        self.assertFalse(sched._last_result[BLIND]["ok"])

    def test_an_unavailable_blind_is_waited_for_not_called(self):
        hass = Hass(state="unavailable")
        sched = bsm.BlindSchedule(hass)
        run(sched._async_apply(BLIND, "open"))
        self.assertEqual(hass.calls, [])
        self.assertFalse(sched._last_result[BLIND]["ok"])

    def test_a_missing_blind_is_skipped(self):
        hass = Hass()
        sched = bsm.BlindSchedule(hass)
        run(sched._async_apply("cover.gone", "open"))
        self.assertEqual(hass.calls, [])
        self.assertEqual(sched._last_result["cover.gone"]["skipped"], "not found")


class Running(unittest.TestCase):
    def setUp(self):
        bsm.asyncio.sleep = fast_sleep
        Store.loaded = None
        Store.saved = None

    def tearDown(self):
        bsm.asyncio.sleep = real_sleep

    def test_set_keeps_status_and_fires_the_event(self):
        hass = Hass()
        sched = bsm.BlindSchedule(hass)
        run(sched.async_start())
        out = run(sched.async_set(BLIND, {"schedules": [{"id": "wk", "days": [0, 1, 2, 3, 4], "open": "07:30", "close": "20:00"}]}))
        self.assertTrue(out["enabled"])
        self.assertEqual(out["schedules"][0]["summary"], "Mon-Fri Open 07:30 · Close 20:00")
        self.assertEqual(out["next"]["text"], "Close Mon 20:00")
        self.assertEqual(Store.saved["blinds"][BLIND]["schedules"][0]["open"], "07:30")
        self.assertEqual(hass.fired[-1], (bsm.EVENT_CHANGED, {"entity_id": BLIND}))
        out = run(sched.async_set(BLIND, {"enabled": False}))
        self.assertFalse(out["enabled"])
        self.assertIsNone(out["next"])
        self.assertEqual(len(out["schedules"]), 1)

    def test_a_fan_schedule_says_on_and_off(self):
        sched = bsm.BlindSchedule(Hass())
        run(sched.async_start())
        out = run(sched.async_set(FAN, {"schedules": [{"id": "n", "days": [0, 1, 2, 3, 4, 5, 6], "open": "22:00", "close": "06:30", "mode": "sleep"}]}))
        self.assertEqual(out["schedules"][0]["summary"], "Every day On 22:00 · Off 06:30")
        self.assertEqual(out["schedules"][0]["mode"], "sleep")
        self.assertTrue(out["next"]["text"].startswith(("On ", "Off ")))

    def test_only_a_cover_can_be_set(self):
        sched = bsm.BlindSchedule(Hass())
        with self.assertRaises(ValueError):
            run(sched.async_set("light.kitchen", {"enabled": False}))

    def test_a_due_minute_sends_the_change_for_that_blind(self):
        async def go():
            hass = Hass()
            sched = bsm.BlindSchedule(hass)
            await sched.async_start()
            await sched.async_set(BLIND, {"schedules": [{"days": [0], "open": "07:30"}]})
            sched._on_minute(datetime(2026, 11, 2, 7, 30, tzinfo=timezone.utc))
            await asyncio.gather(*hass.tasks)
            return hass
        hass = run(go())
        self.assertIn(("cover", "open_cover", {"entity_id": BLIND}), hass.calls)

    def test_a_switched_off_blind_is_left_alone(self):
        async def go():
            hass = Hass()
            sched = bsm.BlindSchedule(hass)
            await sched.async_start()
            await sched.async_set(BLIND, {"enabled": False, "schedules": [{"days": [0], "open": "07:30"}]})
            sched._on_minute(datetime(2026, 11, 2, 7, 30, tzinfo=timezone.utc))
            await asyncio.gather(*hass.tasks)
            return hass
        self.assertEqual(run(go()).calls, [])

    def test_a_change_missed_while_off_is_caught_up(self):
        async def go():
            Store.loaded = {"blinds": {BLIND: {"enabled": True, "schedules": [{"id": "a", "days": [0], "open": "11:45"}]}}, "last_run": "2026-11-02T11:40:00+00:00"}
            hass = Hass()
            sched = bsm.BlindSchedule(hass)
            await sched.async_start()
            await asyncio.gather(*hass.tasks)
            return hass
        hass = run(go())
        self.assertEqual(hass.calls, [("cover", "open_cover", {"entity_id": BLIND})])


if __name__ == "__main__":
    unittest.main()
