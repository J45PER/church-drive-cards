import importlib.util
import pathlib
import unittest

spec = importlib.util.spec_from_file_location(
    "purifier_display_logic", pathlib.Path(__file__).parent.parent / "custom_components" / "church_drive" / "purifier_display_logic.py"
)
pd = importlib.util.module_from_spec(spec)
spec.loader.exec_module(pd)


class Config(unittest.TestCase):
    def test_defaults_turn_the_display_off_for_sleep_only(self):
        cfg = pd.clean_config(None)
        self.assertEqual(pd.choice_for(cfg, "Sleep"), "off")
        self.assertEqual(pd.choice_for(cfg, "auto"), "keep")
        self.assertEqual(pd.choice_for(cfg, "Turbo"), "keep")
        self.assertEqual(pd.choice_for(cfg, None), "keep")

    def test_changes_keep_the_rest_and_ignore_nonsense(self):
        cfg = pd.clean_config({"modes": {"Auto": "dim", "turbo": "blinding", 5: "off", " ": "off"}, "entity": "light.x"})
        self.assertEqual(cfg["modes"], {"sleep": "off", "auto": "dim"})
        self.assertEqual(cfg["entity"], pd.DEFAULT_ENTITY)
        again = pd.clean_config({"modes": {"sleep": "keep"}, "enabled": False}, cfg)
        self.assertEqual(again["modes"], {"sleep": "keep", "auto": "dim"})
        self.assertFalse(again["enabled"])
        self.assertEqual(pd.clean_config({"entity": "fan.other"})["entity"], "fan.other")

    def test_a_saved_empty_list_stays_empty(self):
        self.assertEqual(pd.clean_config({"modes": {}}, {"enabled": True, "entity": pd.DEFAULT_ENTITY, "modes": {}})["modes"], {})

    def test_the_number_of_modes_is_capped(self):
        cfg = pd.clean_config({"modes": {f"m{i}": "off" for i in range(50)}})
        self.assertEqual(len(cfg["modes"]), pd.MAX_MODES)


class Light(unittest.TestCase):
    def test_finds_the_display_next_to_the_fan(self):
        ids = ["light.hayleys_bedroom_air_purifier", "light.hayleys_bedroom_air_purifier_display_backlight", "light.other_display_backlight"]
        self.assertEqual(pd.display_light(ids, "fan.hayleys_bedroom_air_purifier"), "light.hayleys_bedroom_air_purifier_display_backlight")
        self.assertIsNone(pd.display_light(ids, "fan.nothing"))
        self.assertIsNone(pd.display_light(ids, "bad"))

    def test_calls(self):
        self.assertEqual(pd.call_for("off", "light.d"), ("turn_off", {"entity_id": "light.d"}))
        self.assertEqual(pd.call_for("dim", "light.d"), ("turn_on", {"entity_id": "light.d", "brightness": 128}))
        self.assertEqual(pd.call_for("bright", "light.d"), ("turn_on", {"entity_id": "light.d", "brightness": 255}))
        self.assertIsNone(pd.call_for("keep", "light.d"))


class Changes(unittest.TestCase):
    def test_a_mode_change_applies_but_other_changes_do_not(self):
        self.assertTrue(pd.should_apply("on", "auto", "on", "sleep"))
        self.assertTrue(pd.should_apply("off", "sleep", "on", "sleep"))  # switched on in a mode
        self.assertFalse(pd.should_apply("on", "sleep", "on", "Sleep"))  # same mode, another attribute moved
        self.assertFalse(pd.should_apply("on", "auto", "off", "auto"))
        self.assertFalse(pd.should_apply("unavailable", None, "on", "sleep"))  # coming back after an outage
        self.assertFalse(pd.should_apply(None, None, "on", "sleep"))  # first sight at start-up

    def test_summary(self):
        self.assertEqual(pd.summary(pd.clean_config(None)), "Sleep: Off")
        self.assertEqual(pd.summary(pd.clean_config({"modes": {"auto": "dim", "sleep": "keep"}})), "Auto: Dim")
        self.assertEqual(pd.summary({"modes": {}}), "Left as it is")


if __name__ == "__main__":
    unittest.main()
