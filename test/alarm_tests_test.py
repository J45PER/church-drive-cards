"""The monthly alarm tests end to end, against a pretend Home Assistant."""
import asyncio
import importlib
import pathlib
import sys
import types
import unittest
from datetime import datetime, timezone

ROOT = pathlib.Path(__file__).parent.parent / "custom_components" / "church_drive"


def _install_stubs():
    """Home Assistant's modules, made only where another test hasn't already (they're shared); this module then gets its own
    clock, store and entity registry, so it never changes another test's."""
    def mod(name, **attrs):
        m = sys.modules.get(name)
        if m is None:
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

    class Reg:
        def __init__(self):
            self.entities = {}

        def async_get(self, entity_id):
            return self.entities.get(entity_id)

    reg = Reg()
    now = datetime(2026, 11, 17, 9, 0, tzinfo=timezone.utc)
    mod("homeassistant")
    mod("homeassistant.core", HomeAssistant=object, callback=lambda f: f)
    helpers = mod("homeassistant.helpers")
    helpers.event = mod("homeassistant.helpers.event", async_track_time_change=lambda hass, cb, **k: (lambda: None))
    helpers.storage = mod("homeassistant.helpers.storage", Store=Store)
    helpers.entity_registry = mod("homeassistant.helpers.entity_registry", async_get=lambda hass: reg)
    util = mod("homeassistant.util")
    util.dt = mod("homeassistant.util.dt", now=lambda: now, as_local=lambda d: d, parse_datetime=datetime.fromisoformat)
    if "church_drive" not in sys.modules:
        pkg = types.ModuleType("church_drive")
        pkg.__path__ = [str(ROOT)]
        sys.modules["church_drive"] = pkg
    sys.modules.pop("church_drive.alarm_tests", None)
    m = importlib.import_module("church_drive.alarm_tests")
    m.Store = Store
    m.er = types.SimpleNamespace(async_get=lambda hass: reg)
    m.dt_util = types.SimpleNamespace(now=lambda: now, as_local=lambda d: d)
    return m, reg


at_mod, REG = _install_stubs()


def entry(entity_id, device_id):
    return types.SimpleNamespace(entity_id=entity_id, device_id=device_id, domain=entity_id.split(".")[0], disabled_by=None)


class State:
    def __init__(self, entity_id, state="off", **attrs):
        self.entity_id, self.state, self.attributes = entity_id, state, attrs
        self.name = attrs.get("friendly_name", entity_id)


class Hass:
    def __init__(self, people_home=("person.jamie",)):
        self.states_list = [
            State("binary_sensor.top_floor_smoke_alarm_alarm_status", device_class="smoke"),
            State("binary_sensor.kitchen_heat_alarm_alarm_status", device_class="heat"),
            State("binary_sensor.carbon_monoxide_alarm_alarm_status", device_class="carbon_monoxide"),
            State("siren.downstairs_siren", "unknown"),
            State("button.top_floor_smoke_alarm_test", "unknown", friendly_name="Top Floor Smoke Alarm Device Test"),
        ] + [State(p, "home") for p in people_home] + [State("person.hayley", "not_home")]
        self.states = types.SimpleNamespace(
            get=lambda e: next((s for s in self.states_list if s.entity_id == e), None),
            async_all=lambda domain: [s for s in self.states_list if s.entity_id.startswith(domain + ".")],
        )
        self.calls, self.fired = [], []
        self.services = types.SimpleNamespace(async_call=self._call)
        self.bus = types.SimpleNamespace(async_fire=lambda n, d=None: self.fired.append((n, d)))

    async def _call(self, domain, service, data, blocking=False):
        self.calls.append((domain, service, data["entity_id"]))

    def async_create_task(self, coro):
        return asyncio.ensure_future(coro)


class People:
    def __init__(self):
        self.sent = []

    async def async_notify(self, kind, only, title, message, **kw):
        self.sent.append((only, title))


REG.entities = {
    "binary_sensor.top_floor_smoke_alarm_alarm_status": entry("binary_sensor.top_floor_smoke_alarm_alarm_status", "d1"),
    "binary_sensor.kitchen_heat_alarm_alarm_status": entry("binary_sensor.kitchen_heat_alarm_alarm_status", "d2"),
    "binary_sensor.carbon_monoxide_alarm_alarm_status": entry("binary_sensor.carbon_monoxide_alarm_alarm_status", "d3"),
    "button.top_floor_smoke_alarm_test": entry("button.top_floor_smoke_alarm_test", "d1"),
    "button.top_floor_smoke_alarm_fire_drill": entry("button.top_floor_smoke_alarm_fire_drill", "d1"),
    "button.kitchen_heat_alarm_test": entry("button.kitchen_heat_alarm_test", "d2"),
    "button.carbon_monoxide_alarm_device_test": entry("button.carbon_monoxide_alarm_device_test", "d3"),
}


async def _fast(_s):
    return None


class AlarmTestsTest(unittest.TestCase):
    def test_targets_are_the_test_buttons_of_each_kind(self):
        tests = at_mod.AlarmTests(Hass())
        self.assertEqual(tests.targets("smoke"), ["button.kitchen_heat_alarm_test", "button.top_floor_smoke_alarm_test"])
        self.assertEqual(tests.targets("co"), ["button.carbon_monoxide_alarm_device_test"])
        self.assertEqual(tests.targets("alarm"), ["siren.downstairs_siren"])

    def test_running_presses_every_button_and_sounds_the_siren(self):
        hass = Hass()
        tests = at_mod.AlarmTests(hass)
        real = at_mod.asyncio.sleep
        at_mod.asyncio.sleep = _fast
        try:
            asyncio.run(tests._async_run("smoke"))
            asyncio.run(tests._async_run("alarm"))
        finally:
            at_mod.asyncio.sleep = real
        self.assertEqual(hass.calls, [
            ("button", "press", "button.kitchen_heat_alarm_test"),
            ("button", "press", "button.top_floor_smoke_alarm_test"),
            ("siren", "turn_on", "siren.downstairs_siren"),
            ("siren", "turn_off", "siren.downstairs_siren"),
        ])
        self.assertTrue(tests._last["smoke"]["ok"])

    def test_warnings_go_to_everyone_home(self):
        people = People()
        tests = at_mod.AlarmTests(Hass(), people)
        asyncio.run(tests._async_warn("co", 15))
        self.assertEqual(people.sent, [(["person.jamie"], "CO alarm test in 15 minutes")])

    def test_no_warning_when_nobody_is_home(self):
        people = People()
        tests = at_mod.AlarmTests(Hass(people_home=()), people)
        asyncio.run(tests._async_warn("co", 5))
        self.assertEqual(people.sent, [])

    def test_set_and_status(self):
        tests = at_mod.AlarmTests(Hass())
        out = asyncio.run(tests.async_set("co", {"enabled": True, "day": 1, "time": "11:00"}))
        co = out["tests"]["co"]
        self.assertEqual(co["summary"], "The 1st of each month at 11:00")
        self.assertEqual(co["next"]["text"], "Tue 1 Dec 11:00")
        self.assertEqual(co["targets"], ["button.carbon_monoxide_alarm_device_test"])
        self.assertFalse(out["tests"]["alarm"]["enabled"])
        with self.assertRaises(ValueError):
            asyncio.run(tests.async_set("nope", {}))
