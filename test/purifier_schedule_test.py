"""The purifier schedule end to end, against a pretend Home Assistant (real asyncio, a fake purifier and services)."""
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

        def __init__(self, *a, **k):
            pass

        async def async_load(self):
            return None

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

    Dt.current = datetime(2026, 11, 2, 12, 0, tzinfo=timezone.utc)

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
    return importlib.import_module("church_drive.purifier_schedule"), Store, Dt


ps, Store, Dt = _install_stubs()
real_sleep = asyncio.sleep
ENTITY = "fan.hayleys_bedroom_air_purifier"


class State:
    def __init__(self, state, preset=None):
        self.state, self.attributes = state, {"preset_mode": preset}


class Hass:
    """A purifier that takes a mode after `takes_after` calls (never, if None)."""

    def __init__(self, state="on", preset="sleep", takes_after=1):
        self.states = types.SimpleNamespace(get=lambda e: self.fan if e == ENTITY else None)
        self.services = types.SimpleNamespace(async_call=self._call)
        self.fan, self.calls, self.takes_after = State(state, preset), [], takes_after
        self.tasks = []
        self.bus = types.SimpleNamespace(async_fire=lambda *a, **k: None)

    def async_create_task(self, coro):
        task = asyncio.ensure_future(coro)
        self.tasks.append(task)
        return task

    async def _call(self, domain, service, data, blocking=False):
        self.calls.append((domain, service, dict(data)))
        if self.takes_after is not None and len(self.calls) >= self.takes_after:
            self.fan.attributes["preset_mode"] = data["preset_mode"]


def run(coro):
    return asyncio.run(coro)


async def fast_sleep(_s):
    await real_sleep(0)


class Applying(unittest.TestCase):
    def setUp(self):
        ps.asyncio.sleep = fast_sleep

    def tearDown(self):
        ps.asyncio.sleep = real_sleep

    def test_sends_the_mode_and_notes_it_worked(self):
        hass = Hass(preset="sleep", takes_after=1)
        sched = ps.PurifierSchedule(hass)
        run(sched._async_apply("auto"))
        self.assertEqual(hass.calls, [("fan", "set_preset_mode", {"entity_id": ENTITY, "preset_mode": "auto"})])
        self.assertTrue(sched._last_result["ok"])
        self.assertEqual(sched._last_result["mode"], "auto")

    def test_tries_again_when_the_purifier_does_not_take_it(self):
        hass = Hass(preset="sleep", takes_after=3)
        sched = ps.PurifierSchedule(hass)
        run(sched._async_apply("auto"))
        self.assertEqual(len(hass.calls), 3)
        self.assertTrue(sched._last_result["ok"])
        self.assertEqual(sched._last_result["tries"], 3)

    def test_gives_up_after_five_tries(self):
        hass = Hass(preset="sleep", takes_after=None)
        sched = ps.PurifierSchedule(hass)
        run(sched._async_apply("auto"))
        self.assertEqual(len(hass.calls), ps.TRIES)
        self.assertFalse(sched._last_result["ok"])

    def test_leaves_a_switched_off_purifier_off(self):
        hass = Hass(state="off", preset=None)
        sched = ps.PurifierSchedule(hass)
        run(sched._async_apply("sleep"))
        self.assertEqual(hass.calls, [])
        self.assertEqual(sched._last_result["skipped"], "switched off")

    def test_nothing_to_send_when_already_in_the_mode(self):
        hass = Hass(preset="auto")
        sched = ps.PurifierSchedule(hass)
        run(sched._async_apply("auto"))
        self.assertEqual(hass.calls, [])
        self.assertTrue(sched._last_result["ok"])

    def test_waits_for_an_unavailable_purifier_without_calling_it(self):
        hass = Hass(state="unavailable", preset=None, takes_after=None)
        sched = ps.PurifierSchedule(hass)
        run(sched._async_apply("auto"))
        self.assertEqual(hass.calls, [])
        self.assertFalse(sched._last_result["ok"])


class Running(unittest.TestCase):
    def setUp(self):
        ps.asyncio.sleep = fast_sleep

    def tearDown(self):
        ps.asyncio.sleep = real_sleep

    def test_a_due_minute_starts_the_change(self):
        async def go():
            hass = Hass(preset="sleep")
            sched = ps.PurifierSchedule(hass)
            await sched.async_set({"schedules": [{"id": "wk", "days": [0], "auto": "07:00", "sleep": "23:30"}]})
            sched._on_minute(datetime(2026, 11, 2, 7, 0, tzinfo=timezone.utc))  # a Monday
            await asyncio.gather(*hass.tasks)
            return hass

        hass = run(go())
        self.assertEqual([c[2]["preset_mode"] for c in hass.calls], ["auto"])

    def test_nothing_happens_on_a_day_the_schedule_does_not_cover(self):
        async def go():
            hass = Hass(preset="sleep")
            sched = ps.PurifierSchedule(hass)
            await sched.async_set({"schedules": [{"id": "wk", "days": [0], "auto": "07:00", "sleep": "23:30"}]})
            sched._on_minute(datetime(2026, 11, 3, 7, 0, tzinfo=timezone.utc))  # a Tuesday
            await asyncio.gather(*hass.tasks)
            return hass

        self.assertEqual(run(go()).calls, [])

    def test_switched_off_schedules_do_nothing(self):
        async def go():
            hass = Hass(preset="sleep")
            sched = ps.PurifierSchedule(hass)
            await sched.async_set({"enabled": False, "schedules": [{"id": "wk", "days": [0], "auto": "07:00", "sleep": "23:30"}]})
            sched._on_minute(datetime(2026, 11, 2, 7, 0, tzinfo=timezone.utc))
            await asyncio.gather(*hass.tasks)
            return hass

        self.assertEqual(run(go()).calls, [])

    def test_status_has_the_summary_and_the_next_change(self):
        async def go():
            sched = ps.PurifierSchedule(Hass(preset="auto"))
            return await sched.async_set({"schedules": [{"id": "wk", "days": [0, 1, 2, 3, 4], "auto": "07:00", "sleep": "23:30"}]})

        status = run(go())
        self.assertEqual(status["schedules"][0]["summary"], "Mon-Fri Auto: 07:00-23:30")
        self.assertEqual(status["next"]["text"], "Sleep Mon 23:30")
        self.assertEqual(status["mode"], "auto")
        self.assertEqual(Store.saved["schedules"][0]["id"], "wk")


if __name__ == "__main__":
    unittest.main()
