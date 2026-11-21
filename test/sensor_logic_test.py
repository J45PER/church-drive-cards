import importlib.util
import pathlib
import unittest

spec = importlib.util.spec_from_file_location(
    "sensor_logic", pathlib.Path(__file__).parent.parent / "custom_components" / "church_drive" / "sensor_logic.py"
)
sl = importlib.util.module_from_spec(spec)
spec.loader.exec_module(sl)

CLOAK = {"sensor": "binary_sensor.cloakroom_presence", "lights": ["light.cloakroom"], "name": "Cloakroom"}


class Clean(unittest.TestCase):
    def test_a_plain_one_has_the_cloakrooms_defaults(self):
        item = sl.clean_sensor(CLOAK)
        self.assertEqual((item["on_action"], item["off_action"], item["off_delay"]), ("on", "off", 60))
        self.assertTrue(item["enabled"])
        self.assertEqual(len(item["id"]), 8)

    def test_it_needs_a_sensor_and_a_light(self):
        with self.assertRaises(ValueError):
            sl.clean_sensor({"sensor": "light.x", "lights": ["light.a"]})
        with self.assertRaises(ValueError):
            sl.clean_sensor({"sensor": "binary_sensor.a", "lights": ["switch.a"]})

    def test_a_scene_needs_its_name(self):
        with self.assertRaises(ValueError):
            sl.clean_sensor({**CLOAK, "on_action": "scene"})
        self.assertEqual(sl.clean_sensor({**CLOAK, "on_action": "scene", "scene": "relax"})["scene"], "relax")

    def test_numbers_are_kept_in_range(self):
        item = sl.clean_sensor({**CLOAK, "off_delay": 1, "dim_pct": 500, "dark_lux": "40"})
        self.assertEqual((item["off_delay"], item["dim_pct"], item["dark_lux"]), (10, 100, 40))

    def test_a_change_keeps_what_wasnt_sent_and_the_id(self):
        old = sl.clean_sensor({**CLOAK, "off_delay": 120})
        new = sl.clean_sensor({"only_dark": True}, old)
        self.assertEqual((new["id"], new["off_delay"], new["only_dark"], new["lights"]), (old["id"], 120, True, ["light.cloakroom"]))

    def test_a_bad_stored_one_is_dropped_alone(self):
        good = sl.clean_sensor(CLOAK)
        kept = sl.clean_sensors([good, {"sensor": "nope"}, "junk"])
        self.assertEqual(list(kept), [good["id"]])


class Window(unittest.TestCase):
    def test_no_window_is_always(self):
        self.assertTrue(sl.in_window(3 * 60, None))

    def test_a_window_past_midnight(self):
        w = {"start": "22:00", "end": "06:00"}
        for minutes, expected in ((23 * 60, True), (2 * 60, True), (6 * 60, False), (12 * 60, False), (22 * 60, True)):
            self.assertEqual(sl.in_window(minutes, w), expected, minutes)

    def test_a_window_in_a_day(self):
        w = {"start": "08:00", "end": "18:00"}
        self.assertTrue(sl.in_window(9 * 60, w))
        self.assertFalse(sl.in_window(19 * 60, w))

    def test_bad_windows_are_ignored(self):
        self.assertIsNone(sl.clean_window({"start": "25:00", "end": "06:00"}))
        self.assertIsNone(sl.clean_window({"start": "06:00", "end": "06:00"}))


class Dark(unittest.TestCase):
    def test_the_light_level_decides_when_there_is_a_reading(self):
        item = sl.clean_sensor({**CLOAK, "only_dark": True, "lux_sensor": "sensor.l", "dark_lux": 30})
        self.assertTrue(sl.is_dark(item, 10, sun_down=False))
        self.assertFalse(sl.is_dark(item, 200, sun_down=True))

    def test_the_sun_decides_without_a_reading(self):
        item = sl.clean_sensor({**CLOAK, "only_dark": True})
        self.assertTrue(sl.is_dark(item, None, sun_down=True))
        self.assertFalse(sl.is_dark(item, 5, sun_down=False))

    def test_someone_there_acts_only_when_allowed(self):
        item = sl.clean_sensor({**CLOAK, "only_dark": True, "window": {"start": "22:00", "end": "06:00"}})
        self.assertTrue(sl.should_act_on_presence(item, 23 * 60, None, True))
        self.assertFalse(sl.should_act_on_presence(item, 23 * 60, None, False))
        self.assertFalse(sl.should_act_on_presence(item, 12 * 60, None, True))
        self.assertFalse(sl.should_act_on_presence({**item, "enabled": False}, 23 * 60, None, True))
        self.assertFalse(sl.should_act_on_presence({**item, "on_action": "none"}, 23 * 60, None, True))


class Words(unittest.TestCase):
    def test_the_summary(self):
        item = sl.clean_sensor(CLOAK)
        self.assertEqual(sl.summary(item), "Light follows presence · off after 1 min")
        dark = sl.clean_sensor({**CLOAK, "only_dark": True, "off_action": "dim", "off_delay": 90})
        self.assertEqual(sl.summary(dark), "Light follows presence · dims to 20% after 1 min 30 s · when it's dark")
        self.assertTrue(sl.summary({**item, "enabled": False}).startswith("Off · "))


if __name__ == "__main__":
    unittest.main()
