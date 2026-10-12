"""The alarm lights' behaviour end to end, against a pretend Home Assistant (real asyncio, fake lights and services)."""
import asyncio
import importlib
import pathlib
import sys
import types
import unittest

ROOT = pathlib.Path(__file__).parent.parent / "custom_components" / "church_drive"


def _install_stubs():
    """Just enough of homeassistant for alarm_lights.py to import, and a church_drive package without its __init__."""
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

    mod("homeassistant")
    mod("homeassistant.const", EVENT_STATE_CHANGED="state_changed")
    mod("homeassistant.core", Event=object, HomeAssistant=object, callback=lambda f: f)
    mod("homeassistant.helpers")
    mod("homeassistant.helpers.storage", Store=Store)
    pkg = types.ModuleType("church_drive")
    pkg.__path__ = [str(ROOT)]
    sys.modules["church_drive"] = pkg
    return importlib.import_module("church_drive.alarm_lights"), Store


al, Store = _install_stubs()
real_sleep = asyncio.sleep


class State:
    def __init__(self, entity_id, state, attrs=None, **more):
        attrs = {**(attrs or {}), **more}
        self.entity_id, self.state, self.attributes = entity_id, state, attrs
        self.name = attrs.get("friendly_name", entity_id)


class Event:
    def __init__(self, data):
        self.data = data


class Bus:
    def __init__(self):
        self.listeners, self.fired = [], []

    def async_listen(self, _name, cb):
        self.listeners.append(cb)
        return lambda: self.listeners.remove(cb)

    def async_fire(self, name, data=None):
        self.fired.append(name)


class States:
    def __init__(self):
        self.all = {}

    def get(self, entity_id):
        return self.all.get(entity_id)

    def async_all(self, domain):
        return [s for e, s in self.all.items() if e.startswith(domain + ".")]


class Services:
    def __init__(self, hass):
        self.hass, self.calls = hass, []

    async def async_call(self, domain, service, data, blocking=False):
        self.calls.append((domain, service, dict(data)))
        if (domain, service) == ("scene", "create"):
            self.hass.states.all["scene." + data["scene_id"]] = State("scene." + data["scene_id"], "unknown")


class Hass:
    def __init__(self):
        self.states, self.bus = States(), Bus()
        self.services = Services(self)
        self.tasks = []

    def async_create_task(self, coro):
        t = asyncio.get_running_loop().create_task(coro)
        self.tasks.append(t)
        return t

    def set(self, entity_id, state, old=None, **attrs):
        """Change an entity and tell the listener, like Home Assistant does."""
        before = self.states.get(entity_id)
        new = State(entity_id, state, attrs or (before.attributes if before else {}))
        self.states.all[entity_id] = new
        for cb in list(self.bus.listeners):
            cb(Event({"entity_id": entity_id, "old_state": before if old is None else (State(entity_id, old) if old != "" else None), "new_state": new}))


def house():
    h = Hass()
    for e, members in (("light.ambience", ["light.a1", "light.a2"]), ("light.hall", None), ("light.lounge", None), ("light.bed", None)):
        h.states.all[e] = State(e, "off", {"entity_id": members} if members else {})
    for e in ("light.a1", "light.a2"):
        h.states.all[e] = State(e, "off")
    h.states.all["binary_sensor.smoke_1"] = State("binary_sensor.smoke_1", "off", device_class="smoke")
    h.states.all["binary_sensor.smoke_2"] = State("binary_sensor.smoke_2", "off", device_class="smoke")
    h.states.all["binary_sensor.door"] = State("binary_sensor.door", "off", device_class="door")
    h.states.all["alarm_control_panel.ring"] = State("alarm_control_panel.ring", "armed_home")
    h.states.all["sensor.lux_a"] = State("sensor.lux_a", "0")
    h.states.all["sensor.lux_b"] = State("sensor.lux_b", "0")
    return h


class AlarmLightsCase(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        # Sleeps 100 times shorter; the logic's own clock is the loop's, so tests use the scaled sleep for waiting.
        self._sleep = al.asyncio.sleep

        async def quick(seconds, *a):
            await real_sleep(seconds / 100)

        al.asyncio.sleep = quick
        al.TEST_SECONDS = (0, 120, 1)
        self.hass = house()
        self.lights = al.AlarmLights(self.hass)
        await self.lights.async_start()
        await self.lights.async_set({"strobe": ["light.ambience"], "constant": ["light.hall", "light.lounge"], "sensors": ["sensor.lux_a", "sensor.lux_b"], "lux": 50, "green_seconds": 60, "blue_seconds": 10})
        self.hass.services.calls.clear()

    async def asyncTearDown(self):
        al.asyncio.sleep = self._sleep
        self.lights.async_stop()

    # helpers
    def calls(self, service=None, domain="light"):
        return [c for c in self.hass.services.calls if c[0] == domain and (service is None or c[1] == service)]

    def constant_on(self):
        return [c for c in self.calls("turn_on") if c[2].get("color_temp_kelvin")]

    def colours_seen(self):
        seen = []
        for _d, s, data in self.calls():
            if s == "turn_on" and "rgb_color" in data and tuple(data["rgb_color"]) not in seen:
                seen.append(tuple(data["rgb_color"]))
        return seen

    async def wait(self, seconds):
        await al.asyncio.sleep(seconds)
        await real_sleep(0.005)

    def fire(self, entity="binary_sensor.smoke_1"):
        self.hass.set(entity, "on", old="off")

    def fire_out(self, entity="binary_sensor.smoke_1"):
        self.hass.set(entity, "off", old="on")

    def siren(self):
        self.hass.set("alarm_control_panel.ring", "triggered", old="armed_home")

    # ---- fire --------------------------------------------------------------

    async def test_fire_by_day_only_pulses_the_strobes(self):
        self.hass.set("sensor.lux_a", "300")
        self.fire()
        await self.wait(4)
        self.assertEqual(self.constant_on(), [])
        self.assertEqual(self.colours_seen(), [al.COLOURS["red"], al.COLOURS["amber"]])
        self.assertTrue(all(c[2]["entity_id"] == ["light.ambience"] for c in self.calls() if "rgb_color" in c[2]))  # the group itself: one command, not one per light
        self.assertTrue(self.calls("turn_off"))  # dark between pulses
        self.assertEqual(self.lights.status()["state"], "fire")

    async def test_fire_at_night_also_lights_the_constant_lights(self):
        self.fire()
        await self.wait(4)
        on = self.constant_on()
        self.assertEqual(len(on), 1)
        self.assertEqual(on[0][2]["entity_id"], ["light.hall", "light.lounge"])
        self.assertEqual(on[0][2]["brightness_pct"], 100)

    async def test_lights_are_saved_before_anything_changes(self):
        self.fire()
        await self.wait(1)
        first = self.hass.services.calls[0]
        self.assertEqual(first[:2], ("scene", "create"))
        self.assertEqual(sorted(first[2]["snapshot_entities"]), ["light.a1", "light.a2", "light.hall", "light.lounge"])

    async def test_day_is_read_once_when_the_alarm_starts(self):
        self.hass.set("sensor.lux_a", "300")  # day: one of two sensors is bright, which is half
        self.fire()
        await self.wait(2)
        self.hass.set("sensor.lux_a", "0")  # the house goes dark while it is going
        self.fire("binary_sensor.smoke_2")
        await self.wait(2)
        self.assertEqual(self.constant_on(), [])

    async def test_fire_ending_shows_green_then_restores(self):
        self.fire()
        await self.wait(2)
        self.hass.services.calls.clear()
        self.fire_out()
        await self.wait(30)
        self.assertEqual(self.colours_seen(), [al.COLOURS["green"]])
        self.assertEqual(self.lights.status()["state"], "ending")
        await self.wait(40)
        self.assertEqual(self.calls("turn_on", "scene")[0][2]["entity_id"], "scene.church_drive_before_alarm")
        self.assertEqual(self.lights.status()["state"], "idle")

    async def test_a_second_alarm_does_not_restart_or_resave(self):
        self.fire()
        await self.wait(2)
        saves = len(self.calls("create", "scene"))
        self.fire("binary_sensor.smoke_2")
        await self.wait(2)
        self.assertEqual(len(self.calls("create", "scene")), saves)
        self.fire_out()
        await self.wait(5)
        self.assertEqual(self.lights.status()["state"], "fire")  # the other one is still going
        self.assertEqual(self.calls("turn_on", "scene"), [])

    async def test_a_non_fire_sensor_or_a_returning_sensor_does_nothing(self):
        self.hass.set("binary_sensor.door", "on", old="off")
        self.hass.set("binary_sensor.smoke_1", "on", old="unavailable")
        await self.wait(3)
        self.assertEqual(self.hass.services.calls, [])
        self.assertEqual(self.lights.status()["state"], "idle")

    # ---- the house alarm ---------------------------------------------------------

    async def test_security_at_home_is_red_blue_with_the_constant_lights(self):
        self.hass.set("sensor.lux_a", "300")  # by day too
        self.siren()
        await self.wait(4)
        self.assertEqual(self.colours_seen(), [al.COLOURS["red"], al.COLOURS["blue"]])
        self.assertEqual(len(self.constant_on()), 1)
        self.assertEqual(self.lights.status()["mode"], "home")

    async def test_security_away_is_red_only_and_leaves_the_constant_lights(self):
        self.hass.set("alarm_control_panel.ring", "armed_away", old="disarmed")
        self.hass.set("alarm_control_panel.ring", "triggered", old="armed_away")
        await self.wait(4)
        self.assertEqual(self.colours_seen(), [al.COLOURS["red"]])
        self.assertEqual(self.constant_on(), [])
        self.assertEqual(self.lights.status()["mode"], "away")

    async def test_the_mode_survives_the_entry_delay(self):
        self.hass.set("alarm_control_panel.ring", "armed_away", old="disarmed")
        self.hass.set("alarm_control_panel.ring", "pending", old="armed_away")
        self.hass.set("alarm_control_panel.ring", "triggered", old="pending")
        await self.wait(3)
        self.assertEqual(self.lights.status()["mode"], "away")

    async def test_turned_off_shows_blue_then_restores(self):
        self.siren()
        await self.wait(2)
        self.hass.services.calls.clear()
        self.hass.set("alarm_control_panel.ring", "disarmed", old="triggered")
        await self.wait(5)
        self.assertEqual(self.colours_seen(), [al.COLOURS["blue"]])
        await self.wait(10)
        self.assertEqual(len(self.calls("turn_on", "scene")), 1)

    async def test_timing_out_back_to_armed_restores_without_the_blue(self):
        self.siren()
        await self.wait(2)
        self.hass.services.calls.clear()
        self.hass.set("alarm_control_panel.ring", "armed_home", old="triggered")
        await self.wait(5)
        self.assertEqual(self.colours_seen(), [])
        self.assertEqual(len(self.calls("turn_on", "scene")), 1)

    # ---- both at once -------------------------------------------------------------

    async def test_fire_takes_over_from_the_house_alarm(self):
        self.siren()
        await self.wait(3)
        self.fire()
        await self.wait(1)
        self.hass.services.calls.clear()
        await self.wait(4)
        self.assertEqual(self.colours_seen(), [al.COLOURS["red"], al.COLOURS["amber"]])
        self.assertEqual(self.lights.status()["state"], "fire")

    async def test_when_the_fire_stops_but_the_siren_goes_on_it_goes_back_to_the_siren(self):
        self.siren()
        await self.wait(2)
        self.fire()
        await self.wait(2)
        saves = len(self.calls("create", "scene"))
        self.hass.services.calls.clear()
        self.fire_out()
        await self.wait(4)
        self.assertEqual(self.colours_seen(), [al.COLOURS["red"], al.COLOURS["blue"]])  # no green: the alarm is still going
        self.assertEqual(self.lights.status()["state"], "security")
        self.assertEqual(saves, 1)  # saved once, at the start

    # ---- switching off, and missing lights ---------------------------------------------

    async def test_switched_off_does_nothing(self):
        await self.lights.async_set({"enabled": False})
        self.hass.services.calls.clear()
        self.fire()
        self.siren()
        await self.wait(3)
        self.assertEqual(self.hass.services.calls, [])

    async def test_switching_off_mid_alarm_puts_the_lights_back_without_the_green(self):
        self.fire()
        await self.wait(2)
        self.hass.services.calls.clear()
        await self.lights.async_set({"enabled": False})
        await self.wait(3)
        self.assertEqual(self.colours_seen(), [])
        self.assertEqual(len(self.calls("turn_on", "scene")), 1)

    async def test_no_lights_chosen_changes_nothing_and_does_not_crash(self):
        await self.lights.async_set({"strobe": [], "constant": []})
        self.hass.services.calls.clear()
        self.fire()
        await self.wait(3)
        self.fire_out()
        await self.wait(3)
        self.assertEqual(self.calls("turn_on"), [])
        self.assertEqual(self.lights.status()["state"], "idle")

    async def test_a_light_that_has_gone_is_skipped(self):
        await self.lights.async_set({"constant": ["light.hall", "light.deleted"]})
        self.hass.services.calls.clear()
        self.fire()
        await self.wait(2)
        self.assertEqual(self.constant_on()[0][2]["entity_id"], ["light.hall"])
        self.assertEqual(self.lights.status()["missing"], ["light.deleted"])

    # ---- the light-only test -----------------------------------------------------------

    async def test_a_test_plays_the_pattern_then_restores_and_touches_nothing_else(self):
        out = await self.lights.async_test("security_home", "night", 1)
        self.assertEqual(out["state"], "test")
        await self.wait(3)
        self.assertEqual(self.colours_seen(), [al.COLOURS["red"], al.COLOURS["blue"]])
        self.assertEqual(len(self.constant_on()), 1)
        await real_sleep(1.3)
        self.assertEqual(len(self.calls("turn_on", "scene")), 1)
        self.assertEqual(self.lights.status()["state"], "idle")
        self.assertTrue({d for d, _s, _x in self.hass.services.calls} <= {"light", "scene"})  # no notify, no alarm panel calls

    async def test_a_test_by_day_matches_a_real_fire_by_day(self):
        await self.lights.async_test("fire", "day", 1)
        await self.wait(3)
        self.assertEqual(self.constant_on(), [])

    async def test_stop_ends_a_test_at_once_and_restores(self):
        await self.lights.async_test("fire", "night", 120)
        await self.wait(2)
        out = await self.lights.async_stop_test()
        self.assertEqual(out["state"], "idle")
        self.assertEqual(len(self.calls("turn_on", "scene")), 1)

    async def test_a_test_ending_colour(self):
        await self.lights.async_test("fire_ended", "auto", 5)
        await self.wait(1)
        self.assertEqual(self.colours_seen(), [al.COLOURS["green"]])
        await self.lights.async_stop_test()

    async def test_no_test_during_a_real_alarm(self):
        self.fire()
        await self.wait(2)
        with self.assertRaises(ValueError):
            await self.lights.async_test("fire", "auto", 5)

    async def test_a_real_alarm_during_a_test_takes_over_cleanly(self):
        await self.lights.async_test("security_away", "night", 120)
        await self.wait(2)
        self.fire()
        await self.wait(5)
        self.assertEqual(self.lights.status()["state"], "fire")
        self.assertIsNone(self.lights.status()["test"])
        creates = self.calls("create", "scene")
        restores = self.calls("turn_on", "scene")
        self.assertEqual(len(creates), 2)  # the test's save, then the alarm's
        self.assertEqual(len(restores), 1)  # the test's lights put back before the real alarm saved them

    async def test_a_bad_test_is_refused(self):
        with self.assertRaises(ValueError):
            await self.lights.async_test("earthquake")

    # ---- settings -------------------------------------------------------------------------

    async def test_settings_are_saved_and_cleaned(self):
        out = await self.lights.async_set({"lux": 9999999, "strobe": ["light.hall", "switch.x"], "constant": ["light.hall", "light.bed"]})
        self.assertEqual(out["config"]["lux"], 100000)
        self.assertEqual(out["config"]["strobe"], ["light.hall"])
        self.assertEqual(out["config"]["constant"], ["light.bed"])
        self.assertEqual(Store.saved["lux"], 100000)

    async def test_status_reports_the_whole_house_reading(self):
        self.hass.set("sensor.lux_a", "300")
        s = self.lights.status()
        self.assertTrue(s["day"])
        self.assertEqual([r["lux"] for r in s["sensors"]], [300.0, 0.0])
        self.hass.set("sensor.lux_a", "unavailable")
        self.hass.set("sensor.lux_b", "unavailable")
        self.assertFalse(self.lights.status()["day"])  # no readings is night


if __name__ == "__main__":
    unittest.main()
