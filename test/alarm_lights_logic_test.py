import importlib.util
import pathlib
import unittest

spec = importlib.util.spec_from_file_location(
    "alarm_lights_logic", pathlib.Path(__file__).parent.parent / "custom_components" / "church_drive" / "alarm_lights_logic.py"
)
al = importlib.util.module_from_spec(spec)
spec.loader.exec_module(al)


class Config(unittest.TestCase):
    def test_defaults_when_nothing_sent(self):
        self.assertEqual(al.clean_config(None), al.DEFAULTS)
        self.assertEqual(al.clean_config("rubbish"), al.DEFAULTS)

    def test_bad_values_keep_the_old_ones(self):
        cfg = al.clean_config({"lux": "lots", "rule": "most", "green_seconds": None, "enabled": 0, "junk": 1})
        self.assertEqual(cfg["lux"], 50)
        self.assertEqual(cfg["rule"], "half")
        self.assertEqual(cfg["green_seconds"], 60)
        self.assertFalse(cfg["enabled"])
        self.assertNotIn("junk", cfg)

    def test_numbers_are_limited(self):
        cfg = al.clean_config({"lux": 0, "green_seconds": 99999, "blue_seconds": -4})
        self.assertEqual((cfg["lux"], cfg["green_seconds"], cfg["blue_seconds"]), (1, 600, 0))

    def test_only_the_right_kind_of_entity_is_kept(self):
        cfg = al.clean_config({"strobe": ["light.a", "switch.b", "light.a", 5], "sensors": ["sensor.lux", "light.x"]})
        self.assertEqual(cfg["strobe"], ["light.a"])
        self.assertEqual(cfg["sensors"], ["sensor.lux"])

    def test_a_light_cannot_be_strobe_and_constant(self):
        cfg = al.clean_config({"strobe": ["light.a"], "constant": ["light.a", "light.b"]})
        self.assertEqual(cfg["constant"], ["light.b"])
        # also when the strobe list is the part that was already saved
        again = al.clean_config({"constant": ["light.a", "light.c"]}, cfg)
        self.assertEqual(again["constant"], ["light.c"])

    def test_a_partial_change_keeps_the_rest(self):
        cfg = al.clean_config({"strobe": ["light.a"], "lux": 80})
        cfg = al.clean_config({"lux": 20}, cfg)
        self.assertEqual((cfg["strobe"], cfg["lux"]), (["light.a"], 20))


class Daylight(unittest.TestCase):
    def test_no_readings_is_night(self):
        self.assertFalse(al.is_daytime([], 50, "any"))
        self.assertFalse(al.is_daytime([None, None], 50, "any"))

    def test_rules(self):
        r = [100, 0, 0, 100]
        self.assertTrue(al.is_daytime(r, 50, "any"))
        self.assertTrue(al.is_daytime(r, 50, "half"))
        self.assertFalse(al.is_daytime(r, 50, "all"))
        self.assertFalse(al.is_daytime([100, 0, 0], 50, "half"))
        self.assertTrue(al.is_daytime([100, 100, 100], 50, "all"))

    def test_missing_sensors_do_not_count(self):
        self.assertTrue(al.is_daytime([100, None, None], 50, "all"))

    def test_the_threshold_itself_is_day(self):
        self.assertTrue(al.is_daytime([50], 50, "all"))
        self.assertFalse(al.is_daytime([49.9], 50, "any"))


class Mode(unittest.TestCase):
    def test_home_night_and_away(self):
        self.assertEqual(al.security_mode("armed_home"), "home")
        self.assertEqual(al.security_mode("armed_night"), "home")
        self.assertEqual(al.security_mode("armed_away"), "away")

    def test_falls_back_through_pending_to_the_last_mode_then_the_target(self):
        self.assertEqual(al.security_mode("pending", "armed_away"), "away")
        self.assertEqual(al.security_mode("pending", None, "armed_away"), "away")
        self.assertEqual(al.security_mode("pending", "armed_home", "armed_away"), "home")

    def test_unknown_is_home_which_lights_more(self):
        self.assertEqual(al.security_mode(None), "home")
        self.assertEqual(al.security_mode("pending", "disarmed"), "home")


class Profiles(unittest.TestCase):
    def test_fire_by_day_strobes_only(self):
        p = al.profile("fire", "home", True)
        self.assertFalse(p["constant"])
        self.assertEqual(p["cycle"], [("red", 0.5), (None, 0.5), ("amber", 0.5), (None, 0.5)])

    def test_fire_at_night_adds_the_constant_lights(self):
        self.assertTrue(al.profile("fire", "home", False)["constant"])
        self.assertTrue(al.profile("fire", "away", False)["constant"])  # the security mode does not matter

    def test_security_home_is_red_blue_and_constant_by_day_or_night(self):
        for day in (True, False):
            p = al.profile("security", "home", day)
            self.assertTrue(p["constant"])
            self.assertEqual([c for c, _ in p["cycle"] if c], ["red", "blue"])

    def test_security_away_is_red_only_and_leaves_the_constant_lights(self):
        for day in (True, False):
            p = al.profile("security", "away", day)
            self.assertFalse(p["constant"])
            self.assertEqual([c for c, _ in p["cycle"] if c], ["red"])

    def test_every_pulse_is_followed_by_dark(self):
        for args in (("fire", "home", True), ("security", "home", False), ("security", "away", False)):
            cycle = al.profile(*args)["cycle"]
            self.assertEqual(len(cycle) % 2, 0)
            self.assertTrue(all(cycle[i][0] is None for i in range(1, len(cycle), 2)))

    def test_endings(self):
        cfg = al.clean_config({"green_seconds": 90, "blue_seconds": 5})
        self.assertEqual(al.ending("fire", cfg), ("green", 90))
        self.assertEqual(al.ending("security", cfg), ("blue", 5))

    def test_every_colour_used_exists(self):
        used = {c for args in (("fire", "home", True), ("security", "home", False), ("security", "away", False)) for c, _ in al.profile(*args)["cycle"] if c}
        self.assertTrue(used <= set(al.COLOURS))
        self.assertIn("green", al.COLOURS)


class Tests(unittest.TestCase):
    cfg = al.clean_config({})

    def test_a_test_uses_the_same_profile_as_a_real_alarm(self):
        self.assertEqual(al.plan_for("security_away", "night", True, self.cfg)["cycle"], al.profile("security", "away", False)["cycle"])
        self.assertTrue(al.plan_for("fire", "night", True, self.cfg)["constant"])
        self.assertFalse(al.plan_for("fire", "day", False, self.cfg)["constant"])

    def test_auto_uses_the_house_reading(self):
        self.assertTrue(al.plan_for("fire", "auto", False, self.cfg)["constant"])
        self.assertFalse(al.plan_for("fire", "auto", True, self.cfg)["constant"])

    def test_endings_are_tested_too(self):
        self.assertEqual(al.plan_for("fire_ended", "auto", True, self.cfg)["end"], "green")
        self.assertEqual(al.plan_for("disarmed", "auto", True, self.cfg)["end"], "blue")

    def test_unknown_test(self):
        with self.assertRaises(ValueError):
            al.plan_for("earthquake", "auto", True, self.cfg)


class Roles(unittest.TestCase):
    def test_groups_expand_and_strobe_wins(self):
        members = {"light.group": ["light.a", "light.b"], "light.c": ["light.c"]}
        expand = lambda e: members.get(e, [e])  # noqa: E731
        cfg = al.clean_config({"strobe": ["light.group"], "constant": ["light.b", "light.c", "light.c"]})
        self.assertEqual(al.roles(cfg, expand), (["light.a", "light.b"], ["light.c"]))

    def test_a_group_in_both_gives_its_overlap_to_the_strobe(self):
        members = {"light.g1": ["light.a", "light.b"], "light.g2": ["light.b", "light.c"]}
        cfg = al.clean_config({"strobe": ["light.g1"], "constant": ["light.g2"]})
        self.assertEqual(al.roles(cfg, lambda e: members.get(e, [e])), (["light.a", "light.b"], ["light.c"]))


if __name__ == "__main__":
    unittest.main()
