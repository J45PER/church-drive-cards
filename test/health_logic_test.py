import importlib.util
import pathlib
import unittest

spec = importlib.util.spec_from_file_location(
    "health_logic",
    pathlib.Path(__file__).parent.parent / "custom_components" / "church_drive" / "health_logic.py",
)
hl = importlib.util.module_from_spec(spec)
spec.loader.exec_module(hl)


class FanChoice(unittest.TestCase):
    def test_a_mode_wins_over_a_speed(self):
        self.assertEqual(hl.fan_choice({"preset_mode": "sleep", "percentage": 33}), ("set_preset_mode", {"preset_mode": "sleep"}))
        self.assertEqual(hl.fan_choice({"preset_mode": None, "percentage": 66}), ("set_percentage", {"percentage": 66}))
        self.assertIsNone(hl.fan_choice({"preset_mode": None, "percentage": None}))

    def test_a_silent_fan_keeps_its_last_mode_or_speed(self):
        silent = {"preset_mode": None, "percentage": None, "runtime": "1:00:00"}
        kept = hl.keep_fan_choice("on", silent, {"preset_mode": "speed_2", "percentage": 66})
        self.assertEqual(kept["preset_mode"], "speed_2")
        self.assertEqual(kept["percentage"], 66)
        self.assertEqual(kept["runtime"], "1:00:00")

    def test_what_the_fan_reports_or_off_is_left_alone(self):
        spoken = {"preset_mode": "sleep", "percentage": None}
        self.assertEqual(hl.keep_fan_choice("on", spoken, {"preset_mode": "speed_2"}), spoken)
        silent = {"preset_mode": None, "percentage": None}
        self.assertEqual(hl.keep_fan_choice("off", silent, {"preset_mode": "speed_2"}), silent)
        self.assertEqual(hl.keep_fan_choice("on", silent, None), silent)


if __name__ == "__main__":
    unittest.main()
